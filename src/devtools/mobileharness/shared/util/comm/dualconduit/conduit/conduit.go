// Package conduit provides the control and data planes for Conduit.
package conduit

import (
	"context"
	"log/slog"
	"sync"
	"time"

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

	// Tracks active logical connections in this conduit. For each accepted TCP
	// connection or incoming RSocket channel, one connection is added.
	activeConnections sync.WaitGroup
}

// Context exposes the Conduit's lifecycle so streams can bind to it.
func (c *Conduit) Context() context.Context {
	return c.pumpCtx
}

// New creates the tunnel and sets up the bidirectional lifecycle bridge.
func New(ctx context.Context, id string, meta *dconpb.EstablishConduitRequest, rs rsocket.CloseableRSocket, onRemove func(), beforeClose func(), openSpanContext trace.SpanContext, durationHistogram metric.Float64Histogram) *Conduit {
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
	}

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

		// 1. Record Metrics (Duration)
		duration := time.Since(c.startTime)
		c.recordDurationMetric(duration)

		// 2. Record Close Trace
		c.recordCloseTrace()

		// 3. Call beforeClose callback
		if c.beforeClose != nil {
			c.beforeClose()
		}
		// 4. Cancel the Go context (notifies all IO pumps to stop)
		c.cancel()

		// Wait for all registered stream goroutines to finish.
		c.activeConnections.Wait()
		slog.DebugContext(c.pumpCtx, "Conduit active connections finished", "id", c.ID)

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
