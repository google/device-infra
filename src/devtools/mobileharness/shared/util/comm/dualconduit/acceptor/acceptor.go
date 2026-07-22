// Package acceptor implements the acceptor service for dual conduit.
package acceptor

import (
	"context"
	"fmt"
	"log/slog"
	"net"

	"github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/conduit"
	rsockettransport "github.com/rsocket/rsocket-go/core/transport"
	"github.com/rsocket/rsocket-go/payload"
	"github.com/rsocket/rsocket-go"
	"github.com/rsocket/rsocket-go/rx/flux"
	"github.com/rsocket/rsocket-go/rx/mono"
	"google.golang.org/protobuf/proto"

	"github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/mesh"
	dconpb "github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/proto/dconpb"
	"go.opentelemetry.io/otel/trace"
	"go.opentelemetry.io/otel/attribute"
	otelcodes "go.opentelemetry.io/otel/codes"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/propagation"
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

// RegisterEndpoint registers service discovery with mesh server.
func (s *Service) RegisterEndpoint(ctx context.Context, req *dconpb.EstablishConduitRequest, dynamicPort int) (string, error) {
	physicalAddress := fmt.Sprintf("%s:%d", s.reverseForwardAddress, dynamicPort)
	protocolStr := "tcp"
	switch req.Protocol {
	case dconpb.EstablishConduitRequest_PROTOCOL_GRPC:
		protocolStr = "grpc"
	case dconpb.EstablishConduitRequest_PROTOCOL_HTTP:
		protocolStr = "http"
	}

	return s.meshServer.RegisterEndpoint(ctx, req.ServerName, req.InstanceId, physicalAddress, protocolStr)
}

// DeregisterEndpoint deregisters service discovery with mesh server.
func (s *Service) DeregisterEndpoint(ctx context.Context, req *dconpb.EstablishConduitRequest, dynamicPort int) error {
	physicalAddress := fmt.Sprintf("%s:%d", s.reverseForwardAddress, dynamicPort)
	return s.meshServer.DeregisterEndpoint(ctx, req.ServerName, req.InstanceId, physicalAddress)
}

// Run starts the Acceptor service, listening for incoming RSocket connections.
func (s *Service) Run(ctx context.Context) error {
	slog.InfoContext(ctx, "Acceptor starting up")

	defer s.Manager.Shutdown()

	handler := func(ctx context.Context, setup payload.SetupPayload, rs rsocket.CloseableRSocket) (rsocket.RSocket, error) {
		conduitID := setup.DataUTF8()

		if conduitID == "PROBE" {
			return s.handleProbe(), nil
		}

		req, traceContext, err := s.parseSetupMetadata(setup)
		if err != nil {
			return nil, err
		}

		// Extract parent context from W3C headers in traceContext
		extractedCtx := otel.GetTextMapPropagator().Extract(ctx, propagation.MapCarrier(traceContext))

		slog.InfoContext(extractedCtx, "RSocket Connection incoming", "id", conduitID, "metadata", req)

		// Start OpenTelemetry span for acceptor side conduit lifecycle
		tracer := otel.Tracer("dualconduit/conduit")
		_, acceptorSpan := tracer.Start(extractedCtx, "conduit.open",
			trace.WithAttributes(
				attribute.String("conduit.id", conduitID),
				attribute.String("conduit.destination", req.DestinationEndpoint),
				attribute.Int("conduit.entry_port", int(req.EntryPort)),
				attribute.String("conduit.type", req.Type.String()),
			),
		)

		con, err := s.Manager.Add(ctx, conduitID, req, rs, nil, acceptorSpan.SpanContext())
		if err != nil {
			slog.ErrorContext(extractedCtx, "Failed to add conduit", "id", conduitID, "error", err)
			acceptorSpan.RecordError(err)
			acceptorSpan.SetStatus(otelcodes.Error, err.Error())
			acceptorSpan.End()
			return nil, err
		}
		acceptorSpan.End()

		if req.Type == dconpb.EstablishConduitRequest_CONDUIT_TYPE_REVERSE {
			return s.handleReverseConduit(ctx, conduitID, req, rs, con)
		}

		return s.handleForwardConduit(con, req), nil
	}

	return rsocket.Receive().
		Acceptor(handler).
		Transport(s.serverTransporter).
		Serve(ctx)
}

func (s *Service) handleProbe() rsocket.RSocket {
	slog.InfoContext(context.Background(), "Received check connection, acknowledging")
	return rsocket.NewAbstractSocket(
		rsocket.RequestResponse(func(msg payload.Payload) mono.Mono {
			clientHostname := msg.DataUTF8()
			slog.InfoContext(context.Background(), "Probe from client", "hostname", clientHostname)
			return mono.Just(payload.New([]byte("ACK "+clientHostname), nil))
		}),
	)
}

func (s *Service) parseSetupMetadata(setup payload.SetupPayload) (*dconpb.EstablishConduitRequest, map[string]string, error) {
	metadata, ok := setup.Metadata()
	if !ok {
		slog.ErrorContext(context.Background(), "Setup payload is missing metadata")
		return nil, nil, fmt.Errorf("setup payload is missing metadata")
	}
	setupMeta := &dconpb.ConduitSetupMetadata{}
	if err := proto.Unmarshal(metadata, setupMeta); err == nil && setupMeta.Request != nil {
		return setupMeta.Request, setupMeta.TraceContext, nil
	}

	// Fallback to unmarshaling directly as EstablishConduitRequest
	req := &dconpb.EstablishConduitRequest{}
	if err := proto.Unmarshal(metadata, req); err != nil {
		slog.ErrorContext(context.Background(), "Invalid setup metadata", "error", err)
		return nil, nil, err
	}
	return req, nil, nil
}

func (s *Service) handleReverseConduit(ctx context.Context, conduitID string, req *dconpb.EstablishConduitRequest, rs rsocket.CloseableRSocket, con *conduit.Conduit) (rsocket.RSocket, error) {
	addr := ":0" // ":0" tells the OS to pick a free port
	if req.EntryPort != 0 {
		addr = fmt.Sprintf(":%d", req.EntryPort)
	}
	lis, err := net.Listen("tcp", addr)
	if err != nil {
		slog.ErrorContext(ctx, "Failed to allocate port for reverse conduit", "id", conduitID, "error", err)
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
		logicalName, err := s.RegisterEndpoint(ctx, req, dynamicPort)
		if err != nil {
			slog.ErrorContext(ctx, "Service register failed", "id", conduitID, "error", err)
			lis.Close()
			con.Close()
			return
		}

		go func() {
			<-con.Context().Done()
			if err := s.DeregisterEndpoint(ctx, req, dynamicPort); err != nil {
				slog.ErrorContext(ctx, "Service deregister failed", "id", conduitID, "error", err)
			}
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
			slog.ErrorContext(ctx, "Unsupported protocol for reverse conduit", "id", conduitID, "protocol", req.Protocol)
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
			slog.ErrorContext(ctx, "Failed to marshal registration metadata", "id", conduitID, "error", err)
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
			slog.ErrorContext(ctx, "StartListeningLoop error", "id", conduitID, "error", err)
		}
	}()

	// For Reverse conduits, the Acceptor is the Requester, but we listen for CLOSE signal.
	return rsocket.NewAbstractSocket(
		rsocket.MetadataPush(func(msg payload.Payload) {
			metadata, ok := msg.Metadata()
			if ok && string(metadata) == "CLOSE" {
				slog.InfoContext(ctx, "Received CLOSE signal from Dialer", "id", conduitID)
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
