// Package acceptor implements the acceptor service for dual conduit.
package acceptor

import (
	"context"
	"fmt"
	"log"
	"net"
	"time"

	"github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/conduit"
	rsockettransport "github.com/rsocket/rsocket-go/core/transport"
	"github.com/rsocket/rsocket-go/payload"
	"github.com/rsocket/rsocket-go"
	"github.com/rsocket/rsocket-go/rx/flux"
	"github.com/rsocket/rsocket-go/rx/mono"
	"google.golang.org/protobuf/proto"

	"github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/mesh"
	dconpb "github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/proto/dconpb"
)

// Service represents the acceptor service for Dual Conduit.
type Service struct {
	Manager               *conduit.Manager
	serverTransporter     rsockettransport.ServerTransporter
	meshServer            *mesh.Server
	reverseForwardAddress string
}

// New creates a new Service with a initialized conduit.Manager and ServerTransporter.
func New(newTransporter func() (rsockettransport.ServerTransporter, error), meshServer *mesh.Server, reverseForwardAddress string) (*Service, error) {
	transporter, err := newTransporter()
	if err != nil {
		return nil, fmt.Errorf("failed to create server transport: %v", err)
	}
	return &Service{
		Manager:               conduit.NewManager(),
		serverTransporter:     transporter,
		meshServer:            meshServer,
		reverseForwardAddress: reverseForwardAddress,
	}, nil
}

// RegisterService registers service discovery with mesh server.
func (s *Service) RegisterService(ctx context.Context, req *dconpb.EstablishConduitRequest, dynamicPort int) (string, error) {
	physicalAddress := fmt.Sprintf("%s:%d", s.reverseForwardAddress, dynamicPort)
	protocolStr := "tcp"
	switch req.Protocol {
	case dconpb.EstablishConduitRequest_PROTOCOL_GRPC:
		protocolStr = "grpc"
	case dconpb.EstablishConduitRequest_PROTOCOL_HTTP:
		protocolStr = "http"
	}

	return s.meshServer.RegisterService(ctx, req.ServiceName, req.ClientHostname, physicalAddress, protocolStr)
}

// DeregisterService deregisters service discovery with mesh server.
func (s *Service) DeregisterService(ctx context.Context, req *dconpb.EstablishConduitRequest) error {
	return s.meshServer.DeregisterService(ctx, req.ServiceName, req.ClientHostname)
}

// Run starts the Acceptor service, listening for incoming RSocket connections.
func (s *Service) Run(ctx context.Context) error {
	log.Println("Acceptor starting up...")

	defer s.Manager.Shutdown()

	handler := func(ctx context.Context, setup payload.SetupPayload, rs rsocket.CloseableRSocket) (rsocket.RSocket, error) {
		conduitID := setup.DataUTF8()

		if conduitID == "PROBE" {
			return s.handleProbe(), nil
		}

		req, err := s.parseSetupMetadata(setup)
		if err != nil {
			return nil, err
		}

		log.Printf("RSocket Connection incoming. Conduit ID: %s, Metadata: %+v", conduitID, req)

		con, err := s.Manager.Add(ctx, conduitID, req, rs, nil)
		if err != nil {
			log.Printf("Failed to add conduit: %v", err)
			return nil, err
		}

		if req.Type == dconpb.EstablishConduitRequest_CONDUIT_TYPE_REVERSE {
			return s.handleReverseConduit(ctx, conduitID, req, rs, con)
		}

		return s.handleForwardConduit(con, req), nil
	}

	return rsocket.Receive().
		Resume(rsocket.WithServerResumeSessionDuration(1 * time.Minute)).
		Acceptor(handler).
		Transport(s.serverTransporter).
		Serve(ctx)
}

func (s *Service) handleProbe() rsocket.RSocket {
	log.Printf("Received check connection, acknowledging.")
	return rsocket.NewAbstractSocket(
		rsocket.RequestResponse(func(msg payload.Payload) mono.Mono {
			clientHostname := msg.DataUTF8()
			log.Printf("Probe from client hostname: %s", clientHostname)
			return mono.Just(payload.New([]byte("ACK "+clientHostname), nil))
		}),
	)
}

func (s *Service) parseSetupMetadata(setup payload.SetupPayload) (*dconpb.EstablishConduitRequest, error) {
	metadata, ok := setup.Metadata()
	if !ok {
		log.Printf("Setup payload is missing metadata.")
		return nil, fmt.Errorf("setup payload is missing metadata")
	}
	req := &dconpb.EstablishConduitRequest{}
	if err := proto.Unmarshal(metadata, req); err != nil {
		log.Printf("Invalid setup metadata: %v", err)
		return nil, err
	}
	return req, nil
}

func (s *Service) handleReverseConduit(ctx context.Context, conduitID string, req *dconpb.EstablishConduitRequest, rs rsocket.CloseableRSocket, con *conduit.Conduit) (rsocket.RSocket, error) {
	addr := ":0" // ":0" tells the OS to pick a free port
	if req.EntryPort != 0 {
		addr = fmt.Sprintf(":%d", req.EntryPort)
	}
	lis, err := net.Listen("tcp", addr)
	if err != nil {
		log.Printf("Failed to allocate port for reverse conduit: %v", err)
		return nil, err
	}
	tcpAddr, ok := lis.Addr().(*net.TCPAddr)
	if !ok {
		lis.Close()
		return nil, fmt.Errorf("listener address is not a TCP address: %v", lis.Addr())
	}
	dynamicPort := tcpAddr.Port

	// Async register and PUSHMETA_DATA to dialer
	go func() {
		logicalName, regErr := s.RegisterService(ctx, req, dynamicPort)
		if regErr != nil {
			log.Printf("Service register failed: %v", regErr)
			lis.Close()
			con.Close()
			return
		}

		go func() {
			<-con.Context().Done()
			s.DeregisterService(ctx, req)
		}()

		var locator *dconpb.ServiceLocator
		switch req.Protocol {
		case dconpb.EstablishConduitRequest_PROTOCOL_GRPC:
			locator = &dconpb.ServiceLocator{
				Locator: &dconpb.ServiceLocator_XdsAddress{
					XdsAddress: "xds:///" + logicalName,
				},
			}
		case dconpb.EstablishConduitRequest_PROTOCOL_HTTP:
			locator = &dconpb.ServiceLocator{
				Locator: &dconpb.ServiceLocator_VirtualHost{
					VirtualHost: logicalName,
				},
			}
		case dconpb.EstablishConduitRequest_PROTOCOL_TCP:
			locator = &dconpb.ServiceLocator{
				Locator: &dconpb.ServiceLocator_Sni{
					Sni: logicalName,
				},
			}
		default:
			log.Printf("Unsupported protocol for reverse conduit: %v", req.Protocol)
			lis.Close()
			con.Close()
			return
		}

		resp := &dconpb.EstablishConduitResponse{
			ConduitId:      conduitID,
			ServiceLocator: locator,
		}
		metadata, err := proto.Marshal(resp)
		if err != nil {
			log.Printf("Failed to marshal registration metadata: %v", err)
			lis.Close()
			con.Close()
			return
		}
		rs.MetadataPush(payload.New(nil, metadata))
	}()

	go func() {
		if err := conduit.StartListeningLoop(con, func() (net.Listener, error) {
			return lis, nil
		}); err != nil {
			log.Printf("StartListeningLoop error: %v", err)
		}
	}()

	// For Reverse conduits, the Acceptor is the Requester, but we listen for CLOSE signal.
	return rsocket.NewAbstractSocket(
		rsocket.MetadataPush(func(msg payload.Payload) {
			metadata, ok := msg.Metadata()
			if ok && string(metadata) == "CLOSE" {
				log.Printf("Received CLOSE signal from Dialer for conduit %s", conduitID)
				con.Close()
			}
		}),
	), nil
}

func (s *Service) handleForwardConduit(con *conduit.Conduit, req *dconpb.EstablishConduitRequest) rsocket.RSocket {
	return rsocket.NewAbstractSocket(
		rsocket.RequestChannel(func(incoming flux.Flux) flux.Flux {
			return conduit.AcceptStream(con, req.DestinationEndpoint, incoming)
		}),
	)
}
