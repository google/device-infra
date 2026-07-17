// Package dialer implements the dialer service for dual conduit.
package dialer

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net"
	"os"
	"sync"
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
	"github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/session"
	"go.opentelemetry.io/otel/trace"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/propagation"
)

const (
	defaultKeepAliveTickPeriod = 60 * time.Second
	keepAliveAckTimeout        = 15 * time.Second
	keepAliveMissedAcks        = 3
	ipv4Loopback               = "127.0.0.1"

	reverseConduitCloseSignalDelay = 100 * time.Millisecond
	checkConnectionRequestTimeout  = 10 * time.Second
	probeSignal                    = "PROBE"
	closeSignal                    = "CLOSE"
)

// Service is the implementation of the DualConduitService.
type Service struct {
	dconsvcpb.UnimplementedDualConduitServiceServer
	ServiceCtx          context.Context
	Manager             *conduit.Manager
	Sessions            *session.Manager
	NewTransporter      func() (rsockettransport.ClientTransporter, error)
	Hostname            string
	ForwardAddress      string
	DefaultRetryPolicy  RetryPolicy
	KeepAliveTickPeriod time.Duration
}

// New creates a new Service with an initialized conduit.Manager, session.Manager, and default retry policy.
func New(ctx context.Context, hostname string, forwardAddress string, newTransporter func() (rsockettransport.ClientTransporter, error), policy RetryPolicy, keepAliveTickPeriod time.Duration) *Service {
	cm := conduit.NewManager()
	sm := session.NewManager()

	if policy.Strategy == nil {
		policy = RetryPolicy{
			Strategy: &ConstantBackoff{
				Interval: 1 * time.Second,
			},
			MaxAttempts: 0, // Infinite
		}
	}

	tickPeriod := keepAliveTickPeriod
	if tickPeriod == 0 {
		tickPeriod = defaultKeepAliveTickPeriod
	}

	s := &Service{
		ServiceCtx:          ctx,
		Manager:             cm,
		Sessions:            sm,
		NewTransporter:      newTransporter,
		Hostname:            hostname,
		ForwardAddress:      forwardAddress,
		DefaultRetryPolicy:  policy,
		KeepAliveTickPeriod: tickPeriod,
	}
	cm.SubscribeToRemove(s.handleConduitRemoved)
	return s
}

// EstablishConduit handles the gRPC request to establish a forward/reverse conduit.
func (s *Service) EstablishConduit(ctx context.Context, req *dconpb.EstablishConduitRequest) (*dconpb.EstablishConduitResponse, error) {
	return s.establishConduit(ctx, req)
}

func (s *Service) establishConduit(ctx context.Context, req *dconpb.EstablishConduitRequest) (*dconpb.EstablishConduitResponse, error) {
	slog.Info("Received EstablishConduit request", "request", req)

	if req.Type == dconpb.EstablishConduitRequest_CONDUIT_TYPE_FORWARD && req.EntryPort == 0 {
		return nil, status.Errorf(codes.InvalidArgument, "entry port must be set for forward conduit")
	}

	// 1. Generate unique conduit ID
	id := uuid.NewString()

	// Start OpenTelemetry span for the conduit lifecycle on Dialer
	tracer := otel.Tracer("dualconduit/conduit")
	ctx, dialerSpan := tracer.Start(ctx, "conduit.lifecycle",
		trace.WithAttributes(
			attribute.String("conduit.id", id),
			attribute.String("conduit.destination", req.DestinationEndpoint),
			attribute.Int("conduit.entry_port", int(req.EntryPort)),
			attribute.String("conduit.type", req.Type.String()),
		),
	)

	// Inject W3C Trace Context headers into map carrier
	traceContext := make(map[string]string)
	otel.GetTextMapPropagator().Inject(ctx, propagation.MapCarrier(traceContext))

	// 2. Marshal protobuf metadata envelope for the SETUP frame
	setupMeta := &dconpb.ConduitSetupMetadata{
		Request:      req,
		TraceContext: traceContext,
	}
	metadata, err := proto.Marshal(setupMeta)
	if err != nil {
		dialerSpan.End()
		return nil, status.Errorf(codes.Internal, "failed to marshal request: %v", err)
	}
	setup := payload.New([]byte(id), metadata)

	// 3. Connect to the Acceptor via RSocket
	condReady := make(chan struct{})
	metadataPush := make(chan payload.Payload, 1)

	rsClient, err := s.startRSocketClient(id, req, setup, condReady, metadataPush)
	if err != nil {
		dialerSpan.End()
		return nil, err
	}

	errChan := make(chan error, 1)
	rsClient.OnClose(func(err error) {
		errChan <- err
	})

	var beforeClose func()
	if req.Type == dconpb.EstablishConduitRequest_CONDUIT_TYPE_REVERSE {
		beforeClose = func() {
			slog.Info("Sending CLOSE signal to Acceptor", "id", id)
			rsClient.MetadataPush(payload.New(nil, []byte(closeSignal)))
			time.Sleep(reverseConduitCloseSignalDelay)
		}
	}

	// 4. Create Conduit in the Manager (passes ownership of dialerSpan)
	con, err := s.Manager.Add(s.ServiceCtx, id, req, rsClient, beforeClose, dialerSpan)
	if err != nil {
		rsClient.Close()
		dialerSpan.End()
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
		KeepAlive(s.KeepAliveTickPeriod, keepAliveAckTimeout, keepAliveMissedAcks)

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
			slog.Error("StartListeningLoop error", "error", err)
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
	slog.Info("Received TeardownConduit request", "request", req)
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
// It uses exponential backoff retry with max attempts 3.
func (s *Service) CheckConnection(ctx context.Context) error {
	policy := RetryPolicy{
		Strategy: &ExponentialBackoff{
			InitialInterval: 1 * time.Second,
			MaxInterval:     10 * time.Second,
			Multiplier:      2.0,
		},
		MaxAttempts: 3,
	}

	return Retry(ctx, policy, func(ctx context.Context) error {
		trans, err := s.NewTransporter()
		if err != nil {
			return err
		}

		slog.Info("Performing pre-flight check to acceptor")
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

		slog.Info("Pre-flight check successful")
		return nil
	})
}

// establishConduitWithRetry wraps establishConduit with retry logic.
func (s *Service) establishConduitWithRetry(ctx context.Context, req *dconpb.EstablishConduitRequest, policy RetryPolicy) (*dconpb.EstablishConduitResponse, error) {
	var resp *dconpb.EstablishConduitResponse
	err := Retry(ctx, policy, func(ctx context.Context) error {
		var err error
		resp, err = s.establishConduit(ctx, req)
		return err
	})
	if err != nil {
		return nil, err
	}
	return resp, nil
}

// EstablishSession handles the gRPC request to establish a persistent session of conduits.
func (s *Service) EstablishSession(ctx context.Context, req *dconpb.EstablishSessionRequest) (*dconpb.EstablishSessionResponse, error) {
	slog.Info("Received EstablishSession request", "request", req)

	if req.GetEstablishConduitRequest() == nil {
		return nil, status.Errorf(codes.InvalidArgument, "establish_conduit_request must be set")
	}

	// 1. Generate Session ID
	sessionID := uuid.NewString()

	// 2. Establish initial conduits
	count := 1
	if req.EstablishConduitRequest.Type == dconpb.EstablishConduitRequest_CONDUIT_TYPE_REVERSE && req.ReverseConduitCount > 0 {
		count = int(req.ReverseConduitCount)
	}

	conduitResponses := make([]*dconpb.EstablishConduitResponse, count)
	conduitIDs := make([]string, count)

	// Establish conduits in parallel. If any fails, we fail fast and cancel others.
	ctx, cancel := context.WithCancel(ctx)
	defer cancel()

	var wg sync.WaitGroup
	var mu sync.Mutex
	var firstErr error

	for i := 0; i < count; i++ {
		wg.Add(1)
		go func(index int) {
			defer wg.Done()
			resp, err := s.establishConduit(ctx, req.EstablishConduitRequest)
			mu.Lock()
			defer mu.Unlock()
			if err != nil {
				if firstErr == nil {
					firstErr = err
					cancel() // Cancel other ongoing establishments
				}
				return
			}
			conduitResponses[index] = resp
			conduitIDs[index] = resp.ConduitId
		}(i)
	}
	wg.Wait()

	if firstErr != nil {
		// Cleanup successfully established conduits
		for _, cid := range conduitIDs {
			if cid != "" {
				s.Manager.Remove(cid)
			}
		}
		return nil, status.Errorf(codes.Unavailable, "failed to establish initial conduits: %v", firstErr)
	}

	// 3. Create and track Session
	sess := session.New(s.ServiceCtx, sessionID, req, conduitIDs)
	if err := s.Sessions.Add(sess); err != nil {
		// Cleanup established conduits
		for _, cid := range conduitIDs {
			s.Manager.Remove(cid)
		}
		return nil, status.Errorf(codes.Internal, "failed to add session to manager: %v", err)
	}

	return &dconpb.EstablishSessionResponse{
		SessionId:                 sessionID,
		EstablishConduitResponses: conduitResponses,
	}, nil
}

// TeardownSession handles the gRPC request to tear down a session.
func (s *Service) TeardownSession(ctx context.Context, req *dconpb.TeardownSessionRequest) (*dconpb.TeardownSessionResponse, error) {
	slog.Info("Received TeardownSession request", "request", req)

	// 1. Retrieve the session to get its conduits before removing it.
	sess, err := s.Sessions.Session(req.SessionId)
	if err != nil {
		if errors.Is(err, session.ErrNotFound) {
			return nil, status.Errorf(codes.NotFound, "session %q not found", req.SessionId)
		}
		return nil, status.Errorf(codes.Internal, "failed to retrieve session: %v", err)
	}

	// 2. Remove from SessionManager first so handleConduitRemoved won't find it
	// and try to reconnect when we remove the conduits.
	if err := s.Sessions.Remove(req.SessionId); err != nil {
		return nil, status.Errorf(codes.Internal, "failed to remove session: %v", err)
	}

	// 3. Close the session (cancels its context, stopping any active reconnect loops).
	sess.Close()

	// 4. Tear down all associated conduits.
	// We do this by removing them from the low-level conduit manager.
	for _, cid := range sess.Conduits() {
		slog.Info("Tearing down conduit as part of session teardown", "conduitID", cid, "sessionID", req.SessionId)
		s.Manager.Remove(cid)
	}

	return &dconpb.TeardownSessionResponse{}, nil
}

func (s *Service) handleConduitRemoved(id string, meta *dconpb.EstablishConduitRequest) {
	slog.Info("handleConduitRemoved", "conduitID", id, "meta", meta)
	select {
	case <-s.ServiceCtx.Done():
		slog.Info("Service is shutting down, ignoring conduit removal", "conduitID", id)
		return
	default:
	}

	// If the session was explicitly torn down, it will have been removed from
	// the SessionManager, so ok will be false and we skip reconnection.
	sess, ok := s.Sessions.FindByConduitID(id)
	if !ok {
		slog.Warn("Conduit not found in session manager", "conduitID", id)
		return // Not a persistent session conduit
	}
	if !sess.Meta.AutoReconnect {
		slog.Info("Conduit removed from persistent session, auto-reconnect is disabled", "conduitID", id, "sessionID", sess.ID)
		return
	}
	slog.Info("Conduit removed from persistent session, triggering reconnect", "conduitID", id, "sessionID", sess.ID)
	go s.reconnect(sess, id, meta)
}

func (s *Service) reconnect(sess *session.Session, brokenConduitID string, meta *dconpb.EstablishConduitRequest) {
	// Reconnect runs in the background. We use ServiceCtx to ensure it survives the RPC context.
	// We also bind it to the session context so if the session is closed, reconnection stops.
	reconCtx, cancel := context.WithCancel(sess.Context())
	defer cancel()

	slog.Info("Reconnecting conduit", "brokenConduitID", brokenConduitID, "sessionID", sess.ID)
	resp, err := s.establishConduitWithRetry(reconCtx, meta, s.DefaultRetryPolicy)
	if err != nil {
		slog.Error("Failed to reconnect conduit after retry policy exhausted", "brokenConduitID", brokenConduitID, "sessionID", sess.ID, "error", err)
		// What to do if reconnect fails?
		// If MaxAttempts was reached, we might want to mark the session as degraded or close it.
		// For now, with infinite retries, we shouldn't get here unless context is canceled.
		return
	}

	slog.Info("Reconnected conduit successfully", "oldID", brokenConduitID, "newID", resp.ConduitId, "sessionID", sess.ID)
	sess.ReplaceConduit(brokenConduitID, resp.ConduitId)
}
