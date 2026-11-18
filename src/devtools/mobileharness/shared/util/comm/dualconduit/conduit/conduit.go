// Package conduit provides the control and data planes for Conduit.
package conduit

import (
	"context"
	"log/slog"
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

// Conduit represents a managed multiplexed tunnel. It bridges the IO pump (Go context)
// and the RSocket connection. Closing either the IO pump or the RSocket connection
// should close the entire Conduit.
type Conduit struct {
	ID          string
	metadata    *dconpb.EstablishConduitRequest
	rsocket     rsocket.CloseableRSocket
	pumpCtx     context.Context    // Represents the active IO pump lifecycle. While Go style generally discourages storing context in structs, Conduit is a connection object where the context models its lifetime. External streams bind to this to know when the tunnel drops.
	cancel      context.CancelFunc // Cancels the IO pump lifecycle.
	closeOnce   sync.Once
	onRemove    func() // Callback to tell the Manager to drop this Conduit
	beforeClose func() // Callback to handle signaling before close

	startTime         time.Time
	telemetry         *Telemetry
	durationHistogram metric.Float64Histogram
	brokenCounter     metric.Int64Counter

	// Tracks active logical connections in this conduit under one lock so
	// Close never races with a newly starting stream.
	streamsMu     sync.Mutex
	streamsClosed bool
	activeStreams int
	streamsZero   chan struct{}
	// streamWait is how long Close waits for the streams to end.
	streamWait time.Duration

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

// streamStarted records a new logical connection in this conduit. It reports
// false once Close has begun and no new streams may start.
func (c *Conduit) streamStarted() bool {
	c.streamsMu.Lock()
	defer c.streamsMu.Unlock()
	if c.streamsClosed {
		return false
	}
	c.activeStreams++
	return true
}

// streamFinished records that a logical connection has fully ended.
func (c *Conduit) streamFinished() {
	c.streamsMu.Lock()
	c.activeStreams--
	done := c.streamsClosed && c.activeStreams == 0
	c.streamsMu.Unlock()
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

// waitStreams marks the conduit closed to new streams and waits until all
// counted streams have finished, for at most c.streamWait. It returns the
// number of streams that had not finished by then. It must be called at most
// once, from Close.
func (c *Conduit) waitStreams() int {
	c.streamsMu.Lock()
	c.streamsClosed = true
	done := c.activeStreams == 0
	c.streamsMu.Unlock()
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
	c.streamsMu.Lock()
	defer c.streamsMu.Unlock()
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
		streamsZero:       make(chan struct{}),
		streamWait:        defaultStreamWait,
	}
	// Protocol 0 support.
	c.peerProtocol.Store(version.ProtocolVersion)

	// 1. Context -> RSocket: If the IO pump context is canceled (e.g., when the parent
	// context is canceled or someone calls cancel), we must ensure the RSocket is closed.
	// This background watcher is necessary to bridge context cancellation to network closure.
	go func() {
		<-pumpCtx.Done()
		c.Close()
	}()

	if rs != nil {
		// 2. RSocket -> Context: If the RSocket connection is closed by the peer or network,
		// we must cancel the IO pump context in a goroutine to prevent deadlocks during synchronous Close().
		rs.OnClose(func(err error) {
			slog.InfoContext(pumpCtx, "Conduit underlying socket closed", "id", id, "error", err)
			if err != nil {
				c.telemetry.RecordError(err)
			}
			go c.Close()
		})
	}

	return c
}

// Close deterministically tears down the Conduit, no matter who triggers it.
// sync.Once guarantees this teardown logic only ever runs exactly one time.
func (c *Conduit) Close() error {
	slog.InfoContext(c.pumpCtx, "Closing Conduit", "id", c.ID)
	c.closeOnce.Do(func() {
		slog.DebugContext(c.pumpCtx, "Conduit teardown started", "id", c.ID)

		// 1. Record Metrics (Duration & Broken Counter)
		duration := time.Since(c.startTime)
		c.recordDurationMetric(duration)
		c.recordBrokenMetric()

		// 2. Record Close Trace
		c.recordCloseTrace()

		// 3. Call beforeClose callback
		if c.beforeClose != nil {
			c.beforeClose()
		}
		// 4. Cancel the Go context (notifies all IO pumps to stop)
		c.cancel()

		// Wait for the streams to finish, but not without limit: closing the
		// transport below releases a stream that is blocked in it, and the
		// conduit has to be removed for its session to replace it.
		if left := c.waitStreams(); left > 0 {
			slog.WarnContext(c.pumpCtx, "Conduit closes although some of its streams have not ended", "id", c.ID, "streams", left, "waited", c.streamWait)
		} else {
			slog.DebugContext(c.pumpCtx, "Conduit active connections finished", "id", c.ID)
		}

		// 5. Close the physical transport synchronously
		if c.rsocket != nil {
			slog.DebugContext(c.pumpCtx, "Conduit closing underlying RSocket", "id", c.ID)
			if err := c.rsocket.Close(); err != nil {
				slog.ErrorContext(c.pumpCtx, "Conduit failed to close underlying RSocket", "id", c.ID, "error", err)
			}
		}

		// 6. Notify the Manager to remove this Conduit from its map
		if c.onRemove != nil {
			c.onRemove()
		}
		slog.InfoContext(c.pumpCtx, "Conduit teardown completed", "id", c.ID)
	})
	return nil
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
