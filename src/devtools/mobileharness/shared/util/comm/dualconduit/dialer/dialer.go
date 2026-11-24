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

	"github.com/jjeffcaii/reactor-go/scheduler"
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
	"github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/rxconn"
	"github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/session"
	dcontransport "github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/transport"
	"github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/version"
	"go.opentelemetry.io/otel/metric"
	"go.opentelemetry.io/otel/trace"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/propagation"
)

const (
	// defaultKeepAliveTickPeriod is the keep-alive interval of a dialer that
	// is not given one.
	defaultKeepAliveTickPeriod = 20 * time.Second
	keepAliveAckTimeout        = 15 * time.Second
	keepAliveMissedAcks        = 3
	// keepAliveLifetime is how long a side keeps a connection on which it
	// has received nothing. The dialer sends it in SETUP, and the acceptor
	// applies it too.
	keepAliveLifetime = keepAliveMissedAcks * keepAliveAckTimeout
	ipv4Loopback      = "127.0.0.1"
	// defaultConnectTimeout bounds how long one attempt to connect to the
	// acceptor may take. It is shorter than the keep-alive interval: rsocket-go
	// starts the keep-alive timer before it dials, so a slower dial would have a
	// keep-alive frame due before the SETUP frame has been sent.
	defaultConnectTimeout = 10 * time.Second
	// rsocketDefaultKeepAliveTickPeriod is the keep-alive interval rsocket-go
	// uses when a client does not set one.
	rsocketDefaultKeepAliveTickPeriod = 20 * time.Second

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
	rttHistogram        metric.Float64Histogram
	connectTimeout      time.Duration
	// keepAliveLifetime is the keep-alive lifetime of the conduits: the
	// constant of the same name, or a shorter time in tests.
	keepAliveLifetime time.Duration
	// beforeHandOver, if not nil, is called with the ID of a conduit that
	// is established and about to be handed to its owner. Tests use it.
	beforeHandOver func(conduitID string)
	// fatal receives the error after which the dialer cannot work with its
	// acceptor. See Fatal.
	fatal chan error
}

// Fatal returns a channel that receives an error when the acceptor has
// refused this dialer because of its protocol version. Trying again cannot
// succeed, so the process should log the error and exit.
func (s *Service) Fatal() <-chan error {
	return s.fatal
}

// reportIfMismatch reports err on the Fatal channel if it says that the
// acceptor refused the protocol version of this dialer.
func (s *Service) reportIfMismatch(err error) {
	if !version.IsMismatch(err) {
		return
	}
	select {
	case s.fatal <- err:
	default:
		// Already reported, or nobody set up the channel.
	}
}

// connectTimeoutFor returns the connect timeout for a client with the given
// keep-alive interval, keeping it below that interval.
func (s *Service) connectTimeoutFor(keepAliveTickPeriod time.Duration) time.Duration {
	timeout := s.connectTimeout
	if timeout <= 0 {
		timeout = defaultConnectTimeout
	}
	if half := keepAliveTickPeriod / 2; half > 0 && half < timeout {
		timeout = half
	}
	return timeout
}

// requestScheduler returns the scheduler on which rsocket-go runs the
// subscriptions of the streams of every connection of this process.
//
// Left to itself, rsocket-go creates a pool of goroutines for each client
// before it dials, and releases the pool only when the connection closes. A
// dial that fails never has a connection to close, so each failed dial leaves
// the scavenger goroutine of its pool behind for good. rsocket-go does not
// release a scheduler that it is given, so all connections share this one. It
// is the scheduler that rsocket-go already uses, process-wide, for responses.
func requestScheduler() scheduler.Scheduler {
	return scheduler.Elastic()
}

// checkKeepAlive reports an error unless a connection with the keep-alive
// interval tickPeriod survives the loss of one keep-alive frame: two
// intervals must fit into the lifetime.
func checkKeepAlive(tickPeriod, lifetime time.Duration) error {
	if tickPeriod <= 0 {
		return fmt.Errorf("keep-alive interval %v is not positive", tickPeriod)
	}
	if 2*tickPeriod >= lifetime {
		return fmt.Errorf("keep-alive interval %v is too long for the keep-alive lifetime of %v: it must be less than half of it (%v), or an idle conduit is closed when one keep-alive frame is late", tickPeriod, lifetime, lifetime/2)
	}
	return nil
}

// New creates a new Service with an initialized conduit.Manager, session.Manager, and default retry policy.
// A keepAliveTickPeriod of 0 selects the default interval. New returns an
// error if the interval is too long for the keep-alive lifetime.
func New(ctx context.Context, hostname string, forwardAddress string, newTransporter func() (rsockettransport.ClientTransporter, error), policy RetryPolicy, keepAliveTickPeriod time.Duration) (*Service, error) {
	tickPeriod := keepAliveTickPeriod
	if tickPeriod == 0 {
		tickPeriod = defaultKeepAliveTickPeriod
	}
	if err := checkKeepAlive(tickPeriod, keepAliveLifetime); err != nil {
		return nil, err
	}

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

	meter := otel.Meter("dualconduit/conduit")
	boundaries := []float64{1, 5, 10, 25, 50, 100, 250, 500, 1000, 2500, 5000}
	rttHistogram, err := meter.Float64Histogram(
		"conduit.probe_rtt",
		metric.WithDescription("Round-trip time (RTT) of data transfer during dialer pre-flight check to acceptor"),
		metric.WithUnit("ms"),
		metric.WithExplicitBucketBoundaries(boundaries...),
	)
	if err != nil {
		slog.ErrorContext(ctx, "Failed to create conduit.probe_rtt histogram", "error", err)
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
		rttHistogram:        rttHistogram,
		connectTimeout:      defaultConnectTimeout,
		keepAliveLifetime:   keepAliveLifetime,
		fatal:               make(chan error, 1),
	}
	cm.SubscribeToRemove(s.handleConduitRemoved)
	return s, nil
}

// EstablishConduit handles the gRPC request to establish a forward/reverse conduit.
func (s *Service) EstablishConduit(ctx context.Context, req *dconpb.EstablishConduitRequest) (*dconpb.EstablishConduitResponse, error) {
	return s.establishConduit(ctx, req, conduitOwner{ctx: s.ServiceCtx})
}

// conduitOwner says for whom a new conduit is established and who records
// it.
type conduitOwner struct {
	// ctx is the context of the owner. When it ends, an attempt to connect
	// the conduit that is in flight ends too.
	ctx context.Context
	// claim, if not nil, records the conduit with its owner. It is called
	// once, while the conduit is ACTIVE and cannot begin to close, so that
	// the owner knows the conduit before the conduit can be removed. It
	// reports false if the owner does not take the conduit, as an owner
	// does that is gone. The conduit is then closed.
	claim func(conduitID string) bool
}

// sendCloseSignal tells the acceptor of a reverse conduit that the dialer is
// closing it, and pauses so that the signal leaves before the streams are
// ended. The entry port of a reverse conduit is on the acceptor: without the
// signal, clients would be sent into a conduit that is closing.
func sendCloseSignal(ctx context.Context, rs interface{ MetadataPush(payload.Payload) }, id string) {
	slog.InfoContext(ctx, "Sending CLOSE signal to Acceptor", "id", id)
	// MetadataPush waits while the send queue of the connection is full, as
	// it is on a connection that has stalled, and closing must not wait for
	// the peer. The call returns at the latest when the connection closes.
	queued := make(chan struct{})
	go func() {
		defer close(queued)
		rs.MetadataPush(payload.New(nil, []byte(closeSignal)))
	}()
	time.Sleep(reverseConduitCloseSignalDelay)
	select {
	case <-queued:
	default:
		slog.WarnContext(ctx, "CLOSE signal is still waiting to be sent, the connection has stalled; closing goes on", "id", id)
	}
}

// endDialWith returns a ClientTransporter whose dial also ends when ctx ends.
// Only the dial is bound to ctx, not the connection that it opens.
func endDialWith(ctx context.Context, dial rsockettransport.ClientTransporter) rsockettransport.ClientTransporter {
	return func(dialCtx context.Context) (*rsockettransport.Transport, error) {
		dialCtx, cancel := context.WithCancel(dialCtx)
		defer cancel()
		stop := context.AfterFunc(ctx, cancel)
		defer stop()
		return dial(dialCtx)
	}
}

func (s *Service) establishConduit(ctx context.Context, req *dconpb.EstablishConduitRequest, owner conduitOwner) (*dconpb.EstablishConduitResponse, error) {
	slog.InfoContext(ctx, "Received EstablishConduit request", "request", req)

	if req.Type == dconpb.EstablishConduitRequest_CONDUIT_TYPE_FORWARD && req.EntryPort == 0 {
		return nil, status.Errorf(codes.InvalidArgument, "entry port must be set for forward conduit")
	}

	// 1. Generate unique conduit ID
	id := uuid.NewString()

	// Start OpenTelemetry span for the conduit lifecycle on Dialer
	tracer := otel.Tracer("dualconduit/conduit")
	ctx, dialerSpan := tracer.Start(ctx, "conduit.open",
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
		Request:         req,
		TraceContext:    traceContext,
		ProtocolVersion: version.ProtocolVersion,
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
	errChan := make(chan error, 1)

	rsClient, err := s.startRSocketClient(owner.ctx, id, req, setup, condReady, metadataPush, errChan)
	if err != nil {
		dialerSpan.End()
		return nil, err
	}

	var beforeClose func()
	if req.Type == dconpb.EstablishConduitRequest_CONDUIT_TYPE_REVERSE {
		// The conduit outlives ctx, which is the context of the request
		// that establishes it and carries its trace.
		beforeClose = func() { sendCloseSignal(s.ServiceCtx, rsClient, id) }
	}

	// 4. Create Conduit in the Manager. It lives under the context of the
	// service, not of its owner: the end of the context of a conduit ends
	// its streams at once, and an owner that gives a conduit up closes it
	// through the steps of Conduit.Close, which signal the peer first.
	// End the setup span after adding to the manager, so failures in Add are captured.
	dialerSpan.End()
	openSpanContext := dialerSpan.SpanContext()
	con, err := s.Manager.Add(s.ServiceCtx, id, req, rsClient, beforeClose, openSpanContext)
	if err != nil {
		rsClient.Close()
		return nil, status.Errorf(codes.Internal, "failed to add conduit to manager: %v", err)
	}
	close(condReady)

	var serviceLocator *dconpb.ServiceLocator
	if req.Type == dconpb.EstablishConduitRequest_CONDUIT_TYPE_FORWARD {
		serviceLocator, err = s.startForwardConduit(ctx, con, uint32(req.EntryPort))
		if err != nil {
			con.Close()
			return nil, err
		}
		select {
		case closeErr := <-errChan:
			con.Close()
			if closeErr != nil {
				return nil, status.Errorf(codes.Unavailable, "rsocket connection closed with error: %v", closeErr)
			}
			return nil, status.Error(codes.Unavailable, "rsocket connection closed unexpectedly")
		default:
		}
	} else {
		serviceLocator, err = s.waitForReverseConduitLocator(ctx, metadataPush, errChan)
		if err != nil {
			con.Close()
			return nil, err
		}
	}

	// 6. Hand the conduit to its owner. From here on the owner is told when
	// the conduit is removed. A conduit that has begun to close before this
	// point was never handed over, so the attempt has failed.
	if s.beforeHandOver != nil {
		s.beforeHandOver(id)
	}
	// A caller that has given up never learns the ID of the conduit and
	// can never tear it down, so it gets none.
	if err := ctx.Err(); err != nil {
		con.Close()
		return nil, status.Errorf(codes.Canceled, "the caller gave up while the conduit was being established: %v", err)
	}
	claimed := true
	active := con.IfActive(func() {
		if owner.claim != nil {
			claimed = owner.claim(id)
		}
	})
	if !active || !claimed {
		con.Close()
		return nil, status.Error(codes.Unavailable, "conduit closed while it was being established")
	}

	// 7. Return response
	return &dconpb.EstablishConduitResponse{
		ConduitId:      id,
		ServiceLocator: serviceLocator,
	}, nil
}

// closeTrackingRSocket registers a single OnClose callback on the RSocket
// ClientBuilder before Start is called. rsocket-go's BaseSocket.OnClose
// appends to an unlocked slice and does not invoke callbacks registered after
// the socket has already closed (for example on a fast REJECTED_SETUP).
type closeTrackingRSocket struct {
	rsocket.Client
	mu      sync.Mutex
	closed  bool
	err     error
	closers []func(error)
}

func (r *closeTrackingRSocket) handleClose(err error) {
	r.mu.Lock()
	r.closed = true
	r.err = err
	closers := r.closers
	r.closers = nil
	r.mu.Unlock()
	for _, fn := range closers {
		fn(err)
	}
}

func (r *closeTrackingRSocket) OnClose(fn func(error)) {
	if fn == nil {
		return
	}
	r.mu.Lock()
	if r.closed {
		err := r.err
		r.mu.Unlock()
		go fn(err)
		return
	}
	r.closers = append(r.closers, fn)
	r.mu.Unlock()
}

func (s *Service) reverseConduitAcceptor(connCtx context.Context, id string, destinationEndpoint string, condReady <-chan struct{}, metadataPush chan<- payload.Payload) func(context.Context, rsocket.RSocket) rsocket.RSocket {
	return func(_ context.Context, _ rsocket.RSocket) rsocket.RSocket {
		return rsocket.NewAbstractSocket(
			rsocket.RequestChannel(func(incoming flux.Flux) flux.Flux {
				select {
				case <-condReady:
					c, err := s.Manager.Conduit(id)
					if err != nil {
						// No Flux is failed: an ERROR frame is not safe on a
						// stream. The stream is refused with the RST marker.
						slog.WarnContext(connCtx, "Reverse conduit is gone, refusing the stream", "id", id, "error", err)
						return rxconn.Refuse(connCtx, incoming, nil)
					}
					return conduit.AcceptStream(c, destinationEndpoint, incoming)
				case <-connCtx.Done():
					slog.WarnContext(connCtx, "Connection closed before the conduit was ready, refusing the stream", "id", id)
					return rxconn.Refuse(connCtx, incoming, nil)
				}
			}),
			rsocket.MetadataPush(func(msg payload.Payload) {
				metadataPush <- payload.Clone(msg)
			}),
		)
	}
}

// startRSocketClient connects to the acceptor. The connection lives under the
// context of the service; only the attempt to open it also ends with
// ownerCtx. A connection must not be closed under its streams, so a conduit
// whose owner is gone is closed in the steps of Conduit.Close, not by ending
// the context of its connection.
func (s *Service) startRSocketClient(ownerCtx context.Context, id string, req *dconpb.EstablishConduitRequest, setup payload.Payload, condReady <-chan struct{}, metadataPush chan<- payload.Payload, errChan chan<- error) (rsocket.Client, error) {
	if err := ownerCtx.Err(); err != nil {
		return nil, status.Errorf(codes.Canceled, "the owner of the conduit is gone: %v", err)
	}
	connCtx, connCancel := context.WithCancel(s.ServiceCtx)
	tracked := &closeTrackingRSocket{
		closers: []func(error){
			func(err error) {
				select {
				case errChan <- err:
				default:
				}
			},
		},
	}
	builder := rsocket.Connect().
		Scheduler(requestScheduler(), nil).
		OnClose(func(err error) {
			connCancel()
			s.reportIfMismatch(err)
			tracked.handleClose(err)
		}).
		SetupPayload(setup).
		ConnectTimeout(s.connectTimeoutFor(s.KeepAliveTickPeriod)).
		KeepAlive(s.KeepAliveTickPeriod, s.keepAliveLifetime/keepAliveMissedAcks, keepAliveMissedAcks)

	var starter rsocket.ToClientStarter = builder
	if req.Type == dconpb.EstablishConduitRequest_CONDUIT_TYPE_REVERSE {
		starter = builder.Acceptor(s.reverseConduitAcceptor(connCtx, id, req.DestinationEndpoint, condReady, metadataPush))
	}

	trans, err := s.NewTransporter()
	if err != nil {
		connCancel()
		return nil, status.Errorf(codes.Internal, "failed to create transport: %v", err)
	}

	// The lifetime runs from the moment the connection opens, so that a
	// connection that dies before its first frame arrives is given up too.
	// When the connection is given up, the client is closed: rsocket-go
	// does not always get there by itself. onDead runs after the connection
	// has been closed, which makes Start return if it has not yet, so
	// onDead waits for Start to learn the client.
	started := make(chan struct{})
	var rsClient rsocket.Client
	onDead := func() {
		<-started
		if rsClient != nil {
			_ = rsClient.Close()
		}
	}
	rsClient, err = starter.
		Transport(endDialWith(ownerCtx, dcontransport.WithLifetimeFromOpen(trans, s.keepAliveLifetime, onDead))).
		Start(s.ServiceCtx)
	close(started)
	if err != nil {
		connCancel()
		s.reportIfMismatch(err)
		return nil, status.Errorf(codes.Unavailable, "failed to connect to acceptor: %v", err)
	}
	tracked.Client = rsClient
	return tracked, nil
}

func (s *Service) startForwardConduit(ctx context.Context, con *conduit.Conduit, entryPort uint32) (*dconpb.ServiceLocator, error) {
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
	// The entry port closes in the step of closing that ends the context of
	// the conduit. Its close is registered here and not left to the
	// listening loop, which registers it on its own goroutine: a conduit
	// that closes before that goroutine has run would return from Close with
	// the port still bound, and the conduit that replaces it could not
	// listen.
	con.AtEntryClose(func() { lis.Close() })
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
	// The goroutine lives as long as the Conduit object con. When con closes,
	// the step of closing that ends the context of the conduit also closes
	// the listener. lis.Accept() then returns an error, and StartListeningLoop
	// returns. It also returns when a client connects after closing has begun.
	// The result of StartListeningLoop is an error which is logged for debugging
	// purposes if it's non-nil.
	go func() {
		if err := conduit.StartListeningLoop(con, func() (net.Listener, error) {
			return lis, nil
		}); err != nil {
			slog.ErrorContext(con.Context(), "StartListeningLoop error", "error", err)
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
	slog.InfoContext(ctx, "Received TeardownConduit request", "request", req)
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

		slog.InfoContext(ctx, "Performing pre-flight check to acceptor")
		client, err := rsocket.Connect().
			Scheduler(requestScheduler(), nil).
			SetupPayload(payload.New([]byte(probeSignal), nil)).
			ConnectTimeout(s.connectTimeoutFor(rsocketDefaultKeepAliveTickPeriod)).
			Transport(trans).
			Start(ctx)
		if err != nil {
			return fmt.Errorf("failed to connect to acceptor during pre-flight check: %v", err)
		}
		defer client.Close()

		hostname := s.hostname()

		reqCtx, cancel := context.WithTimeout(ctx, checkConnectionRequestTimeout)
		defer cancel()
		startTime := time.Now()
		resp, err := client.RequestResponse(payload.New([]byte(hostname), nil)).Block(reqCtx)
		rtt := time.Since(startTime)
		if err != nil {
			return fmt.Errorf("pre-flight check request failed: %v", err)
		}
		if s.rttHistogram != nil {
			s.rttHistogram.Record(ctx, float64(rtt.Microseconds())/1000.0, metric.WithAttributes(
				attribute.String("acceptor.address", s.ForwardAddress),
			))
		}

		expectedAck := "ACK " + hostname
		if string(resp.Data()) != expectedAck {
			return fmt.Errorf("unexpected pre-flight check response: %q, want: %q", string(resp.Data()), expectedAck)
		}

		slog.InfoContext(ctx, "Pre-flight check successful")
		return nil
	})
}

// establishConduitWithRetry wraps establishConduit with retry logic.
func (s *Service) establishConduitWithRetry(ctx context.Context, req *dconpb.EstablishConduitRequest, policy RetryPolicy, owner conduitOwner) (*dconpb.EstablishConduitResponse, error) {
	var resp *dconpb.EstablishConduitResponse
	err := Retry(ctx, policy, func(ctx context.Context) error {
		var err error
		resp, err = s.establishConduit(ctx, req, owner)
		return err
	})
	if err != nil {
		return nil, err
	}
	return resp, nil
}

// EstablishSession handles the gRPC request to establish a persistent session of conduits.
func (s *Service) EstablishSession(ctx context.Context, req *dconpb.EstablishSessionRequest) (*dconpb.EstablishSessionResponse, error) {
	slog.InfoContext(ctx, "Received EstablishSession request", "request", req)

	if req.GetEstablishConduitRequest() == nil {
		return nil, status.Errorf(codes.InvalidArgument, "establish_conduit_request must be set")
	}

	// 1. Create and track the Session, before its conduits: a conduit is
	// recorded in its session before it can be removed, and the session has
	// to be found then.
	sessionID := uuid.NewString()
	sess := session.New(s.ServiceCtx, sessionID, req, nil)
	if err := s.Sessions.Add(sess); err != nil {
		sess.Close()
		return nil, status.Errorf(codes.Internal, "failed to add session to manager: %v", err)
	}
	// The conduits of a session live under the context of the session.
	owner := conduitOwner{ctx: sess.Context(), claim: sess.AddConduit}

	// 2. Establish initial conduits
	count := 1
	if req.EstablishConduitRequest.Type == dconpb.EstablishConduitRequest_CONDUIT_TYPE_REVERSE && req.ReverseConduitCount > 0 {
		count = int(req.ReverseConduitCount)
	}

	conduitResponses := make([]*dconpb.EstablishConduitResponse, count)

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
			resp, err := s.establishConduit(ctx, req.EstablishConduitRequest, owner)
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
		}(i)
	}
	wg.Wait()

	if firstErr != nil {
		// Give the session up, with the conduits that were established.
		s.teardownSession(ctx, sess)
		return nil, status.Errorf(codes.Unavailable, "failed to establish initial conduits: %v", firstErr)
	}

	return &dconpb.EstablishSessionResponse{
		SessionId:                 sessionID,
		EstablishConduitResponses: conduitResponses,
	}, nil
}

// TeardownSession handles the gRPC request to tear down a session.
func (s *Service) TeardownSession(ctx context.Context, req *dconpb.TeardownSessionRequest) (*dconpb.TeardownSessionResponse, error) {
	slog.InfoContext(ctx, "Received TeardownSession request", "request", req)

	// 1. Retrieve the session to get its conduits before removing it.
	sess, err := s.Sessions.Session(req.SessionId)
	if err != nil {
		if errors.Is(err, session.ErrNotFound) {
			return nil, status.Errorf(codes.NotFound, "session %q not found", req.SessionId)
		}
		return nil, status.Errorf(codes.Internal, "failed to retrieve session: %v", err)
	}

	s.teardownSession(ctx, sess)
	return &dconpb.TeardownSessionResponse{}, nil
}

// teardownSession gives a session up and closes its conduits.
func (s *Service) teardownSession(ctx context.Context, sess *session.Session) {
	// 1. Remove from SessionManager first so handleConduitRemoved won't find it
	// and try to reconnect when the conduits close.
	if err := s.Sessions.Remove(sess.ID); err != nil {
		slog.WarnContext(ctx, "Session was already removed", "sessionID", sess.ID, "error", err)
	}

	// 2. Close the session. That ends its context, and with it an attempt to
	// reconnect that is in flight. From here on the session takes no
	// conduit: one that becomes ready only now is refused and closed.
	sess.Close()

	// 3. Close the conduits of the session, each through the steps of
	// Conduit.Close.
	for _, cid := range sess.Conduits() {
		slog.InfoContext(ctx, "Tearing down conduit as part of session teardown", "conduitID", cid, "sessionID", sess.ID)
		s.Manager.Remove(cid)
	}
}

func (s *Service) handleConduitRemoved(id string, meta *dconpb.EstablishConduitRequest) {
	slog.InfoContext(s.ServiceCtx, "handleConduitRemoved", "conduitID", id, "meta", meta)
	select {
	case <-s.ServiceCtx.Done():
		slog.InfoContext(s.ServiceCtx, "Service is shutting down, ignoring conduit removal", "conduitID", id)
		return
	default:
	}

	// If the session was explicitly torn down, it will have been removed from
	// the SessionManager, so ok will be false and we skip reconnection.
	sess, ok := s.Sessions.FindByConduitID(id)
	if !ok {
		slog.WarnContext(s.ServiceCtx, "Conduit not found in session manager", "conduitID", id)
		return // Not a persistent session conduit
	}
	if !sess.Meta.AutoReconnect {
		slog.InfoContext(sess.Context(), "Conduit removed from persistent session, auto-reconnect is disabled", "conduitID", id, "sessionID", sess.ID)
		return
	}
	slog.InfoContext(sess.Context(), "Conduit removed from persistent session, triggering reconnect", "conduitID", id, "sessionID", sess.ID)
	go s.reconnect(sess, id, meta)
}

func (s *Service) reconnect(sess *session.Session, brokenConduitID string, meta *dconpb.EstablishConduitRequest) {
	// Reconnect runs in the background, under the context of the session:
	// if the session is closed, reconnection stops.
	reconCtx, cancel := context.WithCancel(sess.Context())
	defer cancel()

	slog.InfoContext(reconCtx, "Reconnecting conduit", "brokenConduitID", brokenConduitID, "sessionID", sess.ID)
	// The new conduit takes the place of the broken one in the session while
	// it cannot close yet. If it closes later, however soon, the session is
	// told and replaces it in turn.
	owner := conduitOwner{
		ctx:   sess.Context(),
		claim: func(newID string) bool { return sess.ReplaceConduit(brokenConduitID, newID) },
	}
	resp, err := s.establishConduitWithRetry(reconCtx, meta, s.DefaultRetryPolicy, owner)
	if err != nil {
		slog.ErrorContext(reconCtx, "Failed to reconnect conduit after retry policy exhausted", "brokenConduitID", brokenConduitID, "sessionID", sess.ID, "error", err)
		// What to do if reconnect fails?
		// If MaxAttempts was reached, we might want to mark the session as degraded or close it.
		// For now, with infinite retries, we shouldn't get here unless context is canceled.
		return
	}

	slog.InfoContext(reconCtx, "Reconnected conduit successfully", "oldID", brokenConduitID, "newID", resp.ConduitId, "sessionID", sess.ID)
}

// CheckSessions reports an error if a forward session has no ACTIVE conduit:
// its entry port is then closed, or open without a connection behind it.
func (s *Service) CheckSessions() error {
	for _, sess := range s.Sessions.Sessions() {
		req := sess.Meta.GetEstablishConduitRequest()
		if req.GetType() != dconpb.EstablishConduitRequest_CONDUIT_TYPE_FORWARD {
			continue
		}
		cids := sess.Conduits()
		if len(cids) == 0 {
			// The session is being established: it has had no conduit yet.
			// A session that has lost its conduit keeps the ID of that
			// conduit until it has replaced it.
			continue
		}
		active := false
		for _, cid := range cids {
			if c, err := s.Manager.Conduit(cid); err == nil && c.State() == conduit.StateActive {
				active = true
				break
			}
		}
		if !active {
			return fmt.Errorf("forward port %d to %s has no active conduit (session %s)", req.GetEntryPort(), req.GetDestinationEndpoint(), sess.ID)
		}
	}
	return nil
}

// CheckHealth reports whether the dialer serves: the acceptor answers a probe
// connection, and every forward port has an active conduit. A probe alone
// does not show a forward port that is dead while the acceptor can be
// reached.
func (s *Service) CheckHealth(ctx context.Context) error {
	if err := s.CheckConnection(ctx); err != nil {
		return err
	}
	return s.CheckSessions()
}

// WatchHealth runs CheckHealth once every period and passes each result to
// report: nil if the dialer serves. It returns when ctx ends.
func (s *Service) WatchHealth(ctx context.Context, period time.Duration, report func(error)) {
	ticker := time.NewTicker(period)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			report(s.CheckHealth(ctx))
		}
	}
}
