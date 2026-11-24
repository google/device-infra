// Package conduit provides the control and data planes for Conduit.
package conduit

import (
	"context"
	"log/slog"
	"net"
	"sync"
	"sync/atomic"
	"time"

	"github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/version"
	"github.com/rsocket/rsocket-go"

	dconpb "github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/proto/dconpb"
	"go.opentelemetry.io/otel/metric"
	"go.opentelemetry.io/otel/trace"
	"go.opentelemetry.io/otel/attribute"
	otelcodes "go.opentelemetry.io/otel/codes"
	"go.opentelemetry.io/otel"
)

// Telemetry wraps OpenTelemetry trace context and close error.
type Telemetry struct {
	openSpanContext trace.SpanContext
	mu              sync.Mutex
	closeErr        error
}

// NewTelemetry creates a new telemetry wrapper.
func NewTelemetry(openSpanContext trace.SpanContext) *Telemetry {
	return &Telemetry{
		openSpanContext: openSpanContext,
	}
}

// RecordError records the error that caused the conduit to close.
func (t *Telemetry) RecordError(err error) {
	t.mu.Lock()
	defer t.mu.Unlock()
	t.closeErr = err
}

// Error returns the recorded close error, if any.
func (t *Telemetry) Error() error {
	t.mu.Lock()
	defer t.mu.Unlock()
	return t.closeErr
}

// State is the state of a Conduit.
type State int32

const (
	// StateActive means that the connection is up and the conduit is in the
	// manager. Streams may start.
	StateActive State = iota
	// StateClosing means that closing has begun. No stream may start, and
	// the open ones are being ended.
	StateClosing
	// StateClosed means that the connection is closed and the conduit is
	// out of the manager.
	StateClosed
)

func (s State) String() string {
	switch s {
	case StateActive:
		return "ACTIVE"
	case StateClosing:
		return "CLOSING"
	case StateClosed:
		return "CLOSED"
	}
	return "UNKNOWN"
}

// Conduit represents a managed multiplexed tunnel: one RSocket connection
// together with its entry point and its streams. It is bound to that
// connection for life. Whatever goes wrong with it, it closes, once, and a
// session establishes a new one.
type Conduit struct {
	ID          string
	metadata    *dconpb.EstablishConduitRequest
	rsocket     rsocket.CloseableRSocket
	pumpCtx     context.Context    // Represents the active IO pump lifecycle. While Go style generally discourages storing context in structs, Conduit is a connection object where the context models its lifetime. External streams bind to this to know when the tunnel drops.
	cancel      context.CancelFunc // Cancels the IO pump lifecycle.
	onRemove    func()             // Callback to tell the Manager to drop this Conduit
	beforeClose func()             // Callback to handle signaling before close

	startTime         time.Time
	telemetry         *Telemetry
	durationHistogram metric.Float64Histogram
	brokenCounter     metric.Int64Counter

	// connLost: the RSocket connection closed under the conduit. The close
	// of the connection in the last step of closing does not set it.
	connLost atomic.Bool
	// closingSocket: closing has reached the step that closes the RSocket
	// connection, so a close of the connection from here on is the
	// conduit's own.
	closingSocket atomic.Bool
	// closed is closed when the conduit is CLOSED.
	closed chan struct{}

	// mu guards the state, the number of streams and the fields below, so
	// that a stream is either counted or refused when closing begins.
	mu    sync.Mutex
	state State
	// atEntryClose are the functions that close the entry point.
	// entryClosed: they have been run.
	atEntryClose []func()
	entryClosed  bool
	// waitingForStreams: closing waits for the last stream to end.
	waitingForStreams bool
	activeStreams     int
	streamsZero       chan struct{}
	// streamWait is how long closing waits for the streams to end.
	streamWait time.Duration

	// dial connects to the destination of a stream, and dialTimeout limits
	// how long it may take.
	dial        func(ctx context.Context, network, address string) (net.Conn, error)
	dialTimeout time.Duration

	// Protocol 0 support: the DualConduit protocol the other side of the
	// conduit speaks.
	peerProtocol atomic.Int32
}

// SetPeerProtocol records the DualConduit protocol the other side of the
// conduit speaks, when it is not the one of this side. It must be called
// before the conduit carries its first stream.
//
// Protocol 0 support: only the acceptor calls it, for a dialer that is older
// than itself.
func (c *Conduit) SetPeerProtocol(protocol int32) {
	c.peerProtocol.Store(protocol)
}

// State returns the state of the conduit.
func (c *Conduit) State() State {
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.state
}

// IfActive runs fn if the conduit is ACTIVE and reports whether it did.
// Closing cannot begin while fn runs, so what fn records about the conduit is
// in place before the conduit can close. fn must not call the conduit.
func (c *Conduit) IfActive(fn func()) bool {
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.state != StateActive {
		return false
	}
	fn()
	return true
}

// streamStarted records a new logical connection in this conduit. It reports
// false once closing has begun and no new streams may start.
func (c *Conduit) streamStarted() bool {
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.state != StateActive {
		return false
	}
	c.activeStreams++
	return true
}

// streamFinished records that a logical connection has fully ended.
func (c *Conduit) streamFinished() {
	c.mu.Lock()
	c.activeStreams--
	done := c.waitingForStreams && c.activeStreams == 0
	c.mu.Unlock()
	if done {
		close(c.streamsZero)
	}
}

// defaultStreamWait is how long a closing conduit waits for its streams to
// end. Every stream ends when the context of its conduit ends, so this limit
// is reached only by a stream that is stuck in a call into rsocket-go. Without
// a limit, one such stream would keep the conduit from being removed, and a
// session from replacing it, for good.
const defaultStreamWait = 5 * time.Second

// waitStreams waits until all counted streams have finished, for at most
// c.streamWait. It returns the number of streams that had not finished by
// then. It is called once, while the conduit is CLOSING.
func (c *Conduit) waitStreams() int {
	c.mu.Lock()
	c.waitingForStreams = true
	done := c.activeStreams == 0
	c.mu.Unlock()
	if done {
		close(c.streamsZero)
		return 0
	}
	timer := time.NewTimer(c.streamWait)
	defer timer.Stop()
	select {
	case <-c.streamsZero:
		return 0
	case <-timer.C:
		return c.ActiveStreams()
	}
}

// ActiveStreams returns the number of logical connections in this conduit
// that have not fully ended.
func (c *Conduit) ActiveStreams() int {
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.activeStreams
}

// Context exposes the Conduit's lifecycle so streams can bind to it.
func (c *Conduit) Context() context.Context {
	return c.pumpCtx
}

// New creates the tunnel and sets up the bidirectional lifecycle bridge.
func New(ctx context.Context, id string, meta *dconpb.EstablishConduitRequest, rs rsocket.CloseableRSocket, onRemove func(), beforeClose func(), openSpanContext trace.SpanContext, durationHistogram metric.Float64Histogram, brokenCounter metric.Int64Counter) *Conduit {
	if meta != nil {
		slog.InfoContext(ctx, "Creating Conduit", "id", id, "type", meta.Type, "destination", meta.DestinationEndpoint, "entry_port", meta.EntryPort)
	} else {
		slog.InfoContext(ctx, "Creating Conduit with nil metadata", "id", id)
	}
	pumpCtx, cancel := context.WithCancel(ctx)
	c := &Conduit{
		ID:                id,
		metadata:          meta,
		rsocket:           rs,
		pumpCtx:           pumpCtx,
		cancel:            cancel,
		onRemove:          onRemove,
		beforeClose:       beforeClose,
		startTime:         time.Now(),
		telemetry:         NewTelemetry(openSpanContext),
		durationHistogram: durationHistogram,
		brokenCounter:     brokenCounter,
		closed:            make(chan struct{}),
		streamsZero:       make(chan struct{}),
		streamWait:        defaultStreamWait,
		dial:              (&net.Dialer{}).DialContext,
		dialTimeout:       destinationDialTimeout,
	}
	// Protocol 0 support.
	c.peerProtocol.Store(version.ProtocolVersion)

	// The end of the parent context is a request to close: pumpCtx ends
	// with it. pumpCtx also ends when the conduit ends it itself while it
	// closes; BeginClose then finds the conduit CLOSING and does nothing.
	context.AfterFunc(pumpCtx, c.BeginClose)

	if rs != nil {
		// The connection closed: the peer closed it, the network failed, or
		// the keep-alive lifetime ran out. This runs on a goroutine of
		// rsocket-go, so it only starts closing.
		rs.OnClose(func(err error) {
			if c.closingSocket.Load() {
				// The conduit closed the connection itself, in the last
				// step of closing: nothing was lost.
				return
			}
			slog.InfoContext(pumpCtx, "Conduit underlying socket closed", "id", id, "error", err)
			if err != nil {
				c.telemetry.RecordError(err)
			}
			c.connLost.Store(true)
			c.BeginClose()
		})
	}

	return c
}

// AtEntryClose registers fn to close a part of the entry point of the
// conduit, such as the listener of its entry port. Closing runs fn in the
// step that ends the context of the conduit, before it waits for the streams.
// The functions run in the reverse order of their registration, so that what
// was set up last is taken down first. If that step has already run,
// AtEntryClose runs fn at once.
func (c *Conduit) AtEntryClose(fn func()) {
	c.mu.Lock()
	if !c.entryClosed {
		c.atEntryClose = append(c.atEntryClose, fn)
		c.mu.Unlock()
		return
	}
	c.mu.Unlock()
	fn()
}

// BeginClose starts closing the conduit and returns at once. It does nothing
// if closing has already begun. It is safe on a goroutine of rsocket-go.
func (c *Conduit) BeginClose() {
	c.mu.Lock()
	if c.state != StateActive {
		c.mu.Unlock()
		return
	}
	// 1. Enter CLOSING. From here on no stream starts.
	c.state = StateClosing
	c.mu.Unlock()
	go c.runClose()
}

// Close closes the conduit, whatever triggers it, and returns when the
// conduit is CLOSED. The steps of closing run once, on a goroutine of their
// own; a second caller only waits. Close must not be called on a goroutine of
// rsocket-go: use BeginClose there.
func (c *Conduit) Close() error {
	c.BeginClose()
	<-c.closed
	return nil
}

// Done returns a channel that is closed when the conduit is CLOSED.
func (c *Conduit) Done() <-chan struct{} {
	return c.closed
}

// runClose runs the steps of closing that follow the entry into CLOSING.
// Every wait in it has a time limit or ends with the conduit's context: none
// depends on the peer.
func (c *Conduit) runClose() {
	slog.InfoContext(c.pumpCtx, "Closing Conduit", "id", c.ID)

	// 2. Record the duration and the closing trace.
	duration := time.Since(c.startTime)
	c.recordDurationMetric(duration)
	c.recordBrokenMetric()
	c.recordCloseTrace()

	// 3. Signal the peer before the streams are ended. There is nobody to
	// signal when the connection is already lost.
	if c.beforeClose != nil && !c.connLost.Load() {
		c.beforeClose()
	}

	// 4. End the context, which ends every stream, and close the entry
	// point, so that no client is sent into a conduit that is closing.
	c.cancel()
	c.mu.Lock()
	c.entryClosed = true
	atEntryClose := c.atEntryClose
	c.atEntryClose = nil
	c.mu.Unlock()
	for i := len(atEntryClose) - 1; i >= 0; i-- {
		atEntryClose[i]()
	}

	// 5. Wait for the streams to end, but not without limit: closing the
	// connection below releases a stream that is blocked in it, and the
	// conduit has to be removed for its session to replace it.
	if left := c.waitStreams(); left > 0 {
		slog.WarnContext(c.pumpCtx, "Conduit closes although some of its streams have not ended", "id", c.ID, "streams", left, "waited", c.streamWait)
	} else {
		slog.DebugContext(c.pumpCtx, "Conduit active connections finished", "id", c.ID)
	}

	// 6. Close the RSocket connection.
	if c.rsocket != nil {
		slog.DebugContext(c.pumpCtx, "Conduit closing underlying RSocket", "id", c.ID)
		c.closingSocket.Store(true)
		if err := c.rsocket.Close(); err != nil {
			slog.ErrorContext(c.pumpCtx, "Conduit failed to close underlying RSocket", "id", c.ID, "error", err)
		}
	}

	// 7. Enter CLOSED: the conduit leaves the manager, and the removal
	// handlers run. A session reconnects from there.
	c.mu.Lock()
	c.state = StateClosed
	c.mu.Unlock()
	if c.onRemove != nil {
		c.onRemove()
	}
	slog.InfoContext(c.pumpCtx, "Conduit teardown completed", "id", c.ID)
	close(c.closed)
}

func (c *Conduit) recordCloseTrace() {
	tracer := otel.Tracer("dualconduit/conduit")
	_, closeSpan := tracer.Start(
		context.Background(), // Independent trace
		"conduit.close",
		trace.WithLinks(trace.Link{SpanContext: c.telemetry.openSpanContext}),
		trace.WithAttributes(
			attribute.String("conduit.id", c.ID),
		),
	)
	if c.metadata != nil {
		closeSpan.SetAttributes(
			attribute.String("conduit.destination", c.metadata.DestinationEndpoint),
			attribute.Int("conduit.entry_port", int(c.metadata.EntryPort)),
			attribute.String("conduit.type", c.metadata.Type.String()),
		)
	}
	if closeErr := c.telemetry.Error(); closeErr != nil {
		closeSpan.RecordError(closeErr)
		closeSpan.SetStatus(otelcodes.Error, closeErr.Error())
	}
	closeSpan.End()
}

func (c *Conduit) recordDurationMetric(duration time.Duration) {
	if c.durationHistogram != nil {
		c.durationHistogram.Record(context.Background(), duration.Seconds())
	}
}

func (c *Conduit) recordBrokenMetric() {
	if c.brokenCounter == nil {
		return
	}
	var attrs []attribute.KeyValue
	if c.metadata != nil {
		attrs = append(attrs,
			attribute.String("conduit.destination", c.metadata.DestinationEndpoint),
			attribute.Int("conduit.entry_port", int(c.metadata.EntryPort)),
			attribute.String("conduit.type", c.metadata.Type.String()),
			attribute.String("conduit.dialer_id", c.metadata.GetInstanceId()),
		)
	}
	c.brokenCounter.Add(context.Background(), 1, metric.WithAttributes(attrs...))
}
