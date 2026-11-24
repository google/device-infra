package conduit

import (
	"bytes"
	"context"
	"errors"
	"log/slog"
	"slices"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	dconpb "github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/proto/dconpb"
	"github.com/rsocket/rsocket-go/payload"
	"github.com/rsocket/rsocket-go"
	"github.com/rsocket/rsocket-go/rx/flux"
	"github.com/rsocket/rsocket-go/rx/mono"
	"go.opentelemetry.io/otel/trace"
	"go.opentelemetry.io/otel"
)

func TestConduitCloseViaPumpCtx(t *testing.T) {
	ctx, cancel := context.WithCancel(t.Context())
	defer cancel()

	removed := make(chan struct{})
	mrs := newMockRSocket()

	c := New(ctx, "test-id", nil, mrs, func() {
		close(removed)
	}, nil, trace.SpanContext{}, nil, nil)

	if c.ID != "test-id" {
		t.Errorf("New() ID = %v, want %v", c.ID, "test-id")
	}

	// Terminate parent context to trigger watcher closure.
	cancel()

	// Wait for conduit close via context.
	select {
	case <-removed:
		// Success
	case <-time.After(1 * time.Second):
		t.Errorf("after parent context cancel, onRemove called = %v, want %v", false, true)
	}

	select {
	case <-c.Context().Done():
		// Success
	case <-time.After(1 * time.Second):
		t.Errorf("after parent context cancel, c.Context().Done() closed = %v, want %v", false, true)
	}

	select {
	case <-mrs.CloseChan:
		// RSocket was successfully closed.
	case <-time.After(1 * time.Second):
		t.Errorf("after parent context cancel, underlying RSocket closed = %v, want %v", false, true)
	}
}

func TestConduitCloseViaRSocket(t *testing.T) {
	ctx, cancel := context.WithCancel(t.Context())
	defer cancel()

	removed := make(chan struct{})
	mrs := newMockRSocket()

	c := New(ctx, "test-id2", nil, mrs, func() {
		close(removed)
	}, nil, trace.SpanContext{}, nil, nil)

	// Simulate RSocket connection drop.
	mrs.Close()

	// Wait for conduit close via RSocket watcher.
	select {
	case <-removed:
		// Success
	case <-time.After(1 * time.Second):
		t.Errorf("after RSocket close, onRemove called = %v, want %v", false, true)
	}

	select {
	case <-c.Context().Done():
		// Conduit context is triggered internally via c.cancel().
	case <-time.After(1 * time.Second):
		t.Errorf("after RSocket close, c.Context().Done() closed = %v, want %v", false, true)
	}
}

func TestConduitExplicitClose(t *testing.T) {
	ctx, cancel := context.WithCancel(t.Context())
	defer cancel()

	removed := false
	mrs := newMockRSocket()

	c := New(ctx, "test-id3", nil, mrs, func() {
		removed = true
	}, nil, trace.SpanContext{}, nil, nil)

	// Explicitly close the Conduit.
	if err := c.Close(); err != nil {
		t.Errorf("Close() err = %v, want %v", err, nil)
	}

	// Verify Context (Pump) is cancelled.
	select {
	case <-c.Context().Done():
		// pumpCtx was successfully cancelled.
	default:
		t.Errorf("after explicit Close(), c.Context().Done() closed = %v, want %v", false, true)
	}

	// Verify RSocket is closed.
	select {
	case <-mrs.CloseChan:
		// RSocket was successfully closed.
	default:
		t.Errorf("after explicit Close(), underlying RSocket closed = %v, want %v", false, true)
	}

	// Verify onRemove was called.
	if !removed {
		t.Errorf("after explicit Close(), onRemove called = %v, want %v", removed, true)
	}
}

func TestConduitCloseIdempotent(t *testing.T) {
	ctx, cancel := context.WithCancel(t.Context())
	defer cancel()

	removedCount := 0
	mrs := newMockRSocket()

	c := New(ctx, "test-id4", nil, mrs, func() {
		removedCount++
	}, nil, trace.SpanContext{}, nil, nil)

	// Explicitly close the Conduit multiple times.
	if err := c.Close(); err != nil {
		t.Errorf("Close() err = %v, want %v", err, nil)
	}
	if err := c.Close(); err != nil {
		t.Errorf("second Close() err = %v, want %v", err, nil)
	}

	// Verify onRemove was called exactly once.
	if removedCount != 1 {
		t.Errorf("after multiple Close(), onRemove call count = %v, want %v", removedCount, 1)
	}
}

func TestConduitBrokenCounter(t *testing.T) {
	ctx, cancel := context.WithCancel(t.Context())
	defer cancel()

	mrs := newMockRSocket()
	meter := otel.Meter("test-conduit")
	counter, _ := meter.Int64Counter("test.broken_count")

	meta := &dconpb.EstablishConduitRequest{
		InstanceId:          "dialer-123",
		DestinationEndpoint: "localhost:8080",
		EntryPort:           9000,
		Type:                dconpb.EstablishConduitRequest_CONDUIT_TYPE_FORWARD,
	}

	c := New(ctx, "test-id-broken", meta, mrs, nil, nil, trace.SpanContext{}, nil, counter)
	if err := c.Close(); err != nil {
		t.Errorf("Close() err = %v, want %v", err, nil)
	}
}

var _ rsocket.CloseableRSocket = (*mockRSocket)(nil)

// mockRSocket implements rsocket.CloseableRSocket but does practically nothing and exposes a close chan.
type mockRSocket struct {
	CloseChan chan struct{}
}

func newMockRSocket() *mockRSocket {
	return &mockRSocket{CloseChan: make(chan struct{})}
}

func (m *mockRSocket) Close() error {
	select {
	case <-m.CloseChan:
	default:
		close(m.CloseChan)
	}
	return nil
}

func (m *mockRSocket) OnClose(cb func(error)) {
	go func() {
		<-m.CloseChan
		cb(nil)
	}()
}

func (m *mockRSocket) FireAndForget(msg payload.Payload)             {}
func (m *mockRSocket) MetadataPush(msg payload.Payload)              {}
func (m *mockRSocket) RequestResponse(msg payload.Payload) mono.Mono { return mono.Empty() }
func (m *mockRSocket) RequestStream(msg payload.Payload) flux.Flux   { return flux.Empty() }
func (m *mockRSocket) RequestChannel(msgs flux.Flux) flux.Flux {
	return flux.Empty()
}

// TestConduitClose_DoesNotWaitForStuckStream covers a stream that never ends,
// as one that is blocked in rsocket-go does. Close must give up waiting for
// it, so that the conduit is removed and its session can replace it.
func TestConduitClose_DoesNotWaitForStuckStream(t *testing.T) {
	removed := make(chan struct{})
	c := New(t.Context(), "stuck-stream", nil, mockCloseable{rsocket.NewAbstractSocket()}, func() { close(removed) }, nil, trace.SpanContext{}, nil, nil)
	c.streamWait = 50 * time.Millisecond
	if !c.streamStarted() {
		t.Fatalf("streamStarted() = false on an open conduit, want true")
	}

	start := time.Now()
	closed := make(chan struct{})
	go func() {
		c.Close()
		close(closed)
	}()

	select {
	case <-closed:
	case <-time.After(5 * time.Second):
		t.Fatalf("Close() did not return within 5s with one stream that never ends")
	}
	if waited := time.Since(start); waited < c.streamWait {
		t.Errorf("Close() returned after %v, want it to wait %v for the stream", waited, c.streamWait)
	}
	select {
	case <-removed:
	default:
		t.Errorf("Close() returned without removing the conduit")
	}
	if got := c.ActiveStreams(); got != 1 {
		t.Errorf("ActiveStreams() after Close() = %d, want 1", got)
	}
	if c.streamStarted() {
		t.Errorf("streamStarted() = true after Close(), want false")
	}

	// The stream may still end later.
	c.streamFinished()
	if got := c.ActiveStreams(); got != 0 {
		t.Errorf("ActiveStreams() after the stream ended = %d, want 0", got)
	}
}

// closeHookRSocket calls onClose when the conduit closes the connection.
type closeHookRSocket struct {
	*mockRSocket
	onClose func()
}

func (r closeHookRSocket) Close() error {
	r.onClose()
	return r.mockRSocket.Close()
}

// TestConduitClose_StepsRunInOrder checks the order of the closing steps and
// the state in each of them: the peer is signalled while the context is still
// alive; then the context ends and the entry point closes; the connection is
// closed only when the streams have ended; and the conduit leaves the manager
// last, when it is CLOSED.
func TestConduitClose_StepsRunInOrder(t *testing.T) {
	var (
		mu    sync.Mutex
		steps []string
	)
	record := func(step string) {
		mu.Lock()
		defer mu.Unlock()
		steps = append(steps, step)
	}
	rs := closeHookRSocket{mockRSocket: newMockRSocket(), onClose: func() { record("connection closed") }}
	var c *Conduit
	onRemove := func() {
		if got := c.State(); got != StateClosed {
			t.Errorf("State() when the conduit leaves the manager = %v, want %v", got, StateClosed)
		}
		record("removed")
	}
	beforeClose := func() {
		if got := c.State(); got != StateClosing {
			t.Errorf("State() when the peer is signalled = %v, want %v", got, StateClosing)
		}
		if c.streamStarted() {
			t.Errorf("streamStarted() = true once closing has begun, want false")
		}
		if err := c.Context().Err(); err != nil {
			t.Errorf("Context() ended before the peer was signalled: %v", err)
		}
		record("peer signalled")
	}
	c = New(t.Context(), "steps", nil, rs, onRemove, beforeClose, trace.SpanContext{}, nil, nil)
	entryClosed := make(chan struct{})
	c.AtEntryClose(func() {
		if c.Context().Err() == nil {
			t.Errorf("the entry point closed before Context() ended")
		}
		record("entry closed")
		close(entryClosed)
	})
	// A stream that ends when the context of the conduit has ended.
	if !c.streamStarted() {
		t.Fatalf("streamStarted() = false on an open conduit, want true")
	}
	go func() {
		<-c.Context().Done()
		<-entryClosed
		record("stream ended")
		c.streamFinished()
	}()
	if got := c.State(); got != StateActive {
		t.Errorf("State() before Close() = %v, want %v", got, StateActive)
	}

	c.Close()

	want := []string{"peer signalled", "entry closed", "stream ended", "connection closed", "removed"}
	mu.Lock()
	defer mu.Unlock()
	if !slices.Equal(steps, want) {
		t.Errorf("Close() ran the steps %q, want %q", steps, want)
	}
	// What registers after the entry point has closed is closed at once.
	late := false
	c.AtEntryClose(func() { late = true })
	if !late {
		t.Errorf("AtEntryClose() on a closed conduit did not run the function")
	}
}

// TestConduitClose_LostConnectionSkipsPeerSignal covers a conduit whose
// connection is lost. There is nobody to signal, so closing must not spend
// the pause of the signal: the session waits for this conduit to be removed
// before it reconnects.
func TestConduitClose_LostConnectionSkipsPeerSignal(t *testing.T) {
	var signalled atomic.Bool
	mrs := newMockRSocket()
	c := New(t.Context(), "lost", nil, mrs, nil, func() { signalled.Store(true) }, trace.SpanContext{}, nil, nil)

	// The connection closes: rsocket-go calls the OnClose callback.
	mrs.Close()

	select {
	case <-c.Done():
	case <-time.After(5 * time.Second):
		t.Fatalf("conduit is %v 5s after its connection was lost, want %v", c.State(), StateClosed)
	}
	if signalled.Load() {
		t.Errorf("the peer was signalled over a connection that was already lost")
	}
}

// TestConduitBeginClose_ReturnsAtOnce covers a close that is triggered on a
// goroutine of rsocket-go, which must not wait for the steps of closing.
func TestConduitBeginClose_ReturnsAtOnce(t *testing.T) {
	release := make(chan struct{})
	c := New(t.Context(), "begin", nil, newMockRSocket(), nil, func() { <-release }, trace.SpanContext{}, nil, nil)

	returned := make(chan struct{})
	go func() {
		c.BeginClose()
		c.BeginClose()
		close(returned)
	}()
	select {
	case <-returned:
	case <-time.After(5 * time.Second):
		t.Fatalf("BeginClose() did not return within 5s while a closing step was blocked")
	}
	if got := c.State(); got != StateClosing {
		t.Errorf("State() after BeginClose() = %v, want %v", got, StateClosing)
	}

	close(release)
	select {
	case <-c.Done():
	case <-time.After(5 * time.Second):
		t.Fatalf("conduit is %v 5s after the closing step was released, want %v", c.State(), StateClosed)
	}
}

// TestConduitIfActive checks that what IfActive runs is in place before the
// conduit can begin to close, and that it runs nothing once closing has
// begun.
func TestConduitIfActive(t *testing.T) {
	c := New(t.Context(), "claim", nil, newMockRSocket(), nil, nil, trace.SpanContext{}, nil, nil)
	closing := make(chan struct{})
	ran := c.IfActive(func() {
		go func() {
			c.BeginClose()
			close(closing)
		}()
		// BeginClose cannot enter CLOSING while this function runs.
		select {
		case <-closing:
			t.Errorf("BeginClose() returned while the function of IfActive was running")
		case <-time.After(100 * time.Millisecond):
		}
	})
	if !ran {
		t.Errorf("IfActive() = false on an ACTIVE conduit, want true")
	}
	waitClosed(t, c.Done(), "the close of the conduit")
	if c.IfActive(func() { t.Errorf("IfActive() ran its function on a closed conduit") }) {
		t.Errorf("IfActive() = true on a closed conduit, want false")
	}
}

// waitClosed fails the test if ch is not closed within 5 s.
func waitClosed(t *testing.T, ch <-chan struct{}, what string) {
	t.Helper()
	select {
	case <-ch:
	case <-time.After(5 * time.Second):
		t.Fatalf("%s did not happen within 5s", what)
	}
}

// TestConduitClose_EntryPointClosesInReverseOrder checks that the parts of
// the entry point are taken down in the reverse order of their set-up: the
// acceptor relies on it to take the endpoint of a reverse conduit out of the
// mesh before the entry port closes.
func TestConduitClose_EntryPointClosesInReverseOrder(t *testing.T) {
	c := New(t.Context(), "order", nil, newMockRSocket(), nil, nil, trace.SpanContext{}, nil, nil)
	var order []string
	c.AtEntryClose(func() { order = append(order, "listener") })
	c.AtEntryClose(func() { order = append(order, "mesh endpoint") })

	c.Close()

	if want := []string{"mesh endpoint", "listener"}; !slices.Equal(order, want) {
		t.Errorf("Close() closed the entry point in the order %q, want %q", order, want)
	}
}

// closeReportingRSocket is an RSocket that calls its OnClose callbacks from
// Close, with the error that the test gives it. rsocket-go calls them the
// same way whether the connection was closed from this side or was lost.
type closeReportingRSocket struct {
	*mockRSocket
	closeErr error

	mu        sync.Mutex
	callbacks []func(error)
	closed    bool
}

func (r *closeReportingRSocket) OnClose(cb func(error)) {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.callbacks = append(r.callbacks, cb)
}

func (r *closeReportingRSocket) Close() error {
	r.mu.Lock()
	if r.closed {
		r.mu.Unlock()
		return nil
	}
	r.closed = true
	callbacks := r.callbacks
	r.mu.Unlock()
	for _, cb := range callbacks {
		cb(r.closeErr)
	}
	return nil
}

// logBuffer collects log output from several goroutines.
type logBuffer struct {
	mu  sync.Mutex
	buf bytes.Buffer
}

func (b *logBuffer) Write(p []byte) (int, error) {
	b.mu.Lock()
	defer b.mu.Unlock()
	return b.buf.Write(p)
}

func (b *logBuffer) String() string {
	b.mu.Lock()
	defer b.mu.Unlock()
	return b.buf.String()
}

// captureLogs sends the log output of the test to the returned buffer.
func captureLogs(t *testing.T) *logBuffer {
	t.Helper()
	logs := &logBuffer{}
	previous := slog.Default()
	slog.SetDefault(slog.New(slog.NewTextHandler(logs, nil)))
	t.Cleanup(func() { slog.SetDefault(previous) })
	return logs
}

// socketClosedLog is what the conduit logs when its connection closed under
// it. Operators read it as "the connection was lost".
const socketClosedLog = "Conduit underlying socket closed"

// TestConduitClose_OwnCloseOfConnectionIsNotLostConnection covers the last
// step of closing, in which the conduit closes its connection itself.
// rsocket-go reports that close like any other, but nothing was lost: the
// conduit must not log a lost connection or record it.
func TestConduitClose_OwnCloseOfConnectionIsNotLostConnection(t *testing.T) {
	logs := captureLogs(t)
	rs := &closeReportingRSocket{mockRSocket: newMockRSocket(), closeErr: errors.New("use of closed network connection")}
	c := New(t.Context(), "clean", nil, rs, nil, nil, trace.SpanContext{}, nil, nil)

	if err := c.Close(); err != nil {
		t.Fatalf("Close() got unexpected error: %v", err)
	}
	if c.connLost.Load() {
		t.Errorf("connLost = true after Close(), want false: the conduit closed the connection itself")
	}
	if err := c.telemetry.Error(); err != nil {
		t.Errorf("telemetry.Error() after Close() = %v, want nil", err)
	}
	if got := logs.String(); strings.Contains(got, socketClosedLog) {
		t.Errorf("Close() logged %q, want that only for a connection that closed under the conduit; log:\n%s", socketClosedLog, got)
	}
}

// TestConduitClose_LostConnectionIsLoggedAndRecorded is the other case: the
// connection closes while the conduit is ACTIVE.
func TestConduitClose_LostConnectionIsLoggedAndRecorded(t *testing.T) {
	logs := captureLogs(t)
	wantErr := errors.New("i/o timeout")
	rs := &closeReportingRSocket{mockRSocket: newMockRSocket(), closeErr: wantErr}
	c := New(t.Context(), "lost", nil, rs, nil, nil, trace.SpanContext{}, nil, nil)

	// The connection closes: rsocket-go calls the OnClose callbacks.
	rs.Close()

	select {
	case <-c.Done():
	case <-time.After(5 * time.Second):
		t.Fatalf("conduit is %v 5s after its connection was lost, want %v", c.State(), StateClosed)
	}
	if !c.connLost.Load() {
		t.Errorf("connLost = false after the connection was lost, want true")
	}
	if err := c.telemetry.Error(); !errors.Is(err, wantErr) {
		t.Errorf("telemetry.Error() after the connection was lost = %v, want %v", err, wantErr)
	}
	if got := logs.String(); !strings.Contains(got, socketClosedLog) {
		t.Errorf("a lost connection was not logged as %q; log:\n%s", socketClosedLog, got)
	}
}
