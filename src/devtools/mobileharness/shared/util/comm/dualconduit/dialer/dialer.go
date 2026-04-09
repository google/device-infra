// Package dialer implements the dialer service for dual conduit.
package dialer

import (
	"context"
	"fmt"
	"log"
	"net"
	"os"
	"time"

	rsockettransport "github.com/rsocket/rsocket-go/core/transport"

	"github.com/rsocket/rsocket-go/payload"
	"github.com/rsocket/rsocket-go"
	"github.com/rsocket/rsocket-go/rx/flux"
	"github.com/google/uuid"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/proto"

	"github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/conduit"
	dconpb "github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/proto/dconpb"
	dconsvcpb "github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/proto/dconsvcpb"
)

const (
	keepAliveTickPeriod = 20 * time.Second
	keepAliveAckTimeout = 10 * time.Second
	keepAliveMissedAcks = 3
	ipv4Loopback        = "127.0.0.1"

	reverseConduitCloseSignalDelay = 100 * time.Millisecond
	checkConnectionRequestTimeout  = 10 * time.Second
	probeSignal                    = "PROBE"
	closeSignal                    = "CLOSE"
)

// Service is the implementation of the DualConduitService.
type Service struct {
	dconsvcpb.UnimplementedDualConduitServiceServer
	ServiceCtx     context.Context
	Manager        *conduit.Manager
	NewTransporter func() (rsockettransport.ClientTransporter, error)
	Hostname       string
	ForwardAddress string
}

// New creates a new Service with a initialized conduit.Manager.
func New(ctx context.Context, hostname string, newTransporter func() (rsockettransport.ClientTransporter, error)) *Service {
	return &Service{
		ServiceCtx:     ctx,
		Manager:        conduit.NewManager(),
		NewTransporter: newTransporter,
		Hostname:       hostname,
	}
}

// EstablishConduit handles the gRPC request to establish a forward/reverse conduit.
func (s *Service) EstablishConduit(ctx context.Context, req *dconpb.EstablishConduitRequest) (*dconpb.EstablishConduitResponse, error) {
	log.Printf("Received EstablishConduit request: %+v", req)

	if req.Type == dconpb.EstablishConduitRequest_CONDUIT_TYPE_FORWARD && req.EntryPort == 0 {
		return nil, status.Errorf(codes.InvalidArgument, "entry port must be set for forward conduit")
	}

	// 1. Generate unique conduit ID
	id := uuid.NewString()

	// 2. Marshal protobuf metadata for the SETUP frame
	metadata, err := proto.Marshal(req)
	if err != nil {
		return nil, status.Errorf(codes.Internal, "failed to marshal request: %v", err)
	}
	setup := payload.New([]byte(id), metadata)

	// 3. Connect to the Acceptor via RSocket
	condReady := make(chan struct{})
	metadataPush := make(chan payload.Payload, 1)

	rsClient, err := s.startRSocketClient(id, req, setup, condReady, metadataPush)
	if err != nil {
		return nil, err
	}

	errChan := make(chan error, 1)
	rsClient.OnClose(func(err error) {
		errChan <- err
	})

	var beforeClose func()
	if req.Type == dconpb.EstablishConduitRequest_CONDUIT_TYPE_REVERSE {
		beforeClose = func() {
			log.Printf("Sending CLOSE signal to Acceptor for conduit %s", id)
			rsClient.MetadataPush(payload.New(nil, []byte(closeSignal)))
			time.Sleep(reverseConduitCloseSignalDelay)
		}
	}

	// 4. Create Conduit in the Manager
	con, err := s.Manager.Add(s.ServiceCtx, id, req, rsClient, beforeClose)
	if err != nil {
		rsClient.Close()
		return nil, status.Errorf(codes.Internal, "failed to add conduit to manager: %v", err)
	}
	close(condReady)

	var serviceLocator *dconpb.ServiceLocator
	if req.Type == dconpb.EstablishConduitRequest_CONDUIT_TYPE_FORWARD {
		serviceLocator, err = s.startForwardConduit(con, uint32(req.EntryPort))
		if err != nil {
			con.Close()
			return nil, err
		}
	} else {
		serviceLocator, err = s.waitForReverseConduitLocator(ctx, metadataPush, errChan)
		if err != nil {
			con.Close()
			return nil, err
		}
	}

	// 6. Return response
	return &dconpb.EstablishConduitResponse{
		ConduitId:      id,
		ServiceLocator: serviceLocator,
	}, nil
}

func (s *Service) reverseConduitAcceptor(id string, destinationEndpoint string, condReady <-chan struct{}, metadataPush chan<- payload.Payload) func(context.Context, rsocket.RSocket) rsocket.RSocket {
	return func(ctx context.Context, rs rsocket.RSocket) rsocket.RSocket {
		return rsocket.NewAbstractSocket(
			rsocket.RequestChannel(func(incoming flux.Flux) flux.Flux {
				select {
				case <-condReady:
					c, err := s.Manager.Conduit(id)
					if err != nil {
						return flux.Error(status.Errorf(codes.Internal, "failed to get conduit: %v", err))
					}
					return conduit.AcceptStream(c, destinationEndpoint, incoming)
				case <-ctx.Done():
					return flux.Error(status.Errorf(codes.Canceled, "connection closed before conduit was ready: %v", ctx.Err()))
				}
			}),
			rsocket.MetadataPush(func(msg payload.Payload) {
				metadataPush <- payload.Clone(msg)
			}),
		)
	}
}

func (s *Service) startRSocketClient(id string, req *dconpb.EstablishConduitRequest, setup payload.Payload, condReady chan struct{}, metadataPush chan payload.Payload) (rsocket.Client, error) {
	builder := rsocket.Connect().
		SetupPayload(setup).
		KeepAlive(keepAliveTickPeriod, keepAliveAckTimeout, keepAliveMissedAcks)

	if req.AutoReconnect {
		builder = builder.Resume(rsocket.WithClientResumeToken(func() []byte {
			return []byte(id)
		}))
	}

	var starter rsocket.ToClientStarter = builder
	if req.Type == dconpb.EstablishConduitRequest_CONDUIT_TYPE_REVERSE {
		starter = builder.Acceptor(s.reverseConduitAcceptor(id, req.DestinationEndpoint, condReady, metadataPush))
	}

	trans, err := s.NewTransporter()
	if err != nil {
		return nil, status.Errorf(codes.Internal, "failed to create transport: %v", err)
	}

	rsClient, err := starter.
		Transport(trans).
		Start(s.ServiceCtx)
	if err != nil {
		return nil, status.Errorf(codes.Unavailable, "failed to connect to acceptor: %v", err)
	}
	return rsClient, nil
}

func (s *Service) startForwardConduit(con *conduit.Conduit, entryPort uint32) (*dconpb.ServiceLocator, error) {
	// 5. If it is a forward conduit, start the local listening loop.
	port := int(entryPort)
	addr := s.ForwardAddress
	if addr == "" {
		addr = ipv4Loopback
	}
	lis, err := net.Listen("tcp", fmt.Sprintf("%s:%d", addr, port))
	if err != nil {
		return nil, status.Errorf(codes.Internal, "failed to listen on port %d: %v", port, err)
	}
	serviceLocator := &dconpb.ServiceLocator{
		Locator: &dconpb.ServiceLocator_HostnamePort{
			HostnamePort: lis.Addr().String(),
		},
	}

	// This goroutine runs conduit.StartListeningLoop to accept incoming TCP connections
	// for forward conduits. It runs in a separate goroutine because StartListeningLoop
	// is a blocking call that only returns if an error occurs during connection
	// acceptance, or if the listener is closed.
	// The listener setup happens in EstablishConduit before this goroutine is
	// started: if net.Listen fails, EstablishConduit returns an error and
	// this goroutine is not started.
	// The lifecycle of this goroutine is equivalent to the lifecycle of the Conduit object con,
	// which is monitored by conduit.Manager via TeardownConduit.
	// The goroutine terminates when con.Close() is called, which cancels the
	// conduit context, triggering lis.Close() inside StartListeningLoop.
	// This causes lis.Accept() to return an error, and StartListeningLoop to exit.
	// The result of StartListeningLoop is an error which is logged for debugging
	// purposes if it's non-nil.
	go func() {
		if err := conduit.StartListeningLoop(con, func() (net.Listener, error) {
			return lis, nil
		}); err != nil {
			log.Printf("StartListeningLoop error: %v", err)
		}
	}()
	return serviceLocator, nil
}

func (s *Service) waitForReverseConduitLocator(ctx context.Context, metadataPush <-chan payload.Payload, errChan <-chan error) (*dconpb.ServiceLocator, error) {
	// 5. If it is a reverse conduit, wait for metadata push from acceptor.
	select {
	case msg := <-metadataPush:
		metadata, ok := msg.Metadata()
		if !ok {
			return nil, status.Errorf(codes.Internal, "metadata push did not contain metadata")
		}
		var pushResp dconpb.EstablishConduitResponse
		if err := proto.Unmarshal(metadata, &pushResp); err != nil {
			return nil, status.Errorf(codes.Internal, "failed to unmarshal metadata push: %v", err)
		}
		return pushResp.ServiceLocator, nil
	case <-ctx.Done():
		return nil, status.Errorf(codes.DeadlineExceeded, "timed out waiting for metadata push from acceptor")
	case err := <-errChan:
		if err != nil {
			return nil, status.Errorf(codes.Unavailable, "rsocket connection closed with error: %v", err)
		}
		return nil, status.Errorf(codes.Unavailable, "rsocket connection closed unexpectedly")
	}
}

// TeardownConduit handles the gRPC request to tear down a conduit.
func (s *Service) TeardownConduit(ctx context.Context, req *dconpb.TeardownConduitRequest) (*dconpb.TeardownConduitResponse, error) {
	log.Printf("Received TeardownConduit request: %+v", req)
	s.Manager.Remove(req.ConduitId)
	return &dconpb.TeardownConduitResponse{}, nil
}

func (s *Service) hostname() string {
	hostname := s.Hostname
	if hostname == "" {
		hostname = os.Getenv("HOST_HOSTNAME")
	}
	if hostname == "" {
		var err error
		hostname, err = os.Hostname()
		if err != nil {
			hostname = "unknown"
		}
	}
	return hostname
}

// CheckConnection performs a pre-flight check to the acceptor.
func (s *Service) CheckConnection(ctx context.Context) error {
	trans, err := s.NewTransporter()
	if err != nil {
		return err
	}

	log.Println("Performing pre-flight check to acceptor...")
	client, err := rsocket.Connect().
		SetupPayload(payload.New([]byte(probeSignal), nil)).
		Transport(trans).
		Start(ctx)
	if err != nil {
		return fmt.Errorf("failed to connect to acceptor during pre-flight check: %v", err)
	}
	defer client.Close()

	hostname := s.hostname()

	reqCtx, cancel := context.WithTimeout(ctx, checkConnectionRequestTimeout)
	defer cancel()
	resp, err := client.RequestResponse(payload.New([]byte(hostname), nil)).Block(reqCtx)
	if err != nil {
		return fmt.Errorf("pre-flight check request failed: %v", err)
	}

	expectedAck := "ACK " + hostname
	if string(resp.Data()) != expectedAck {
		return fmt.Errorf("unexpected pre-flight check response: %q, want: %q", string(resp.Data()), expectedAck)
	}

	log.Println("Pre-flight check successful.")
	return nil
}
