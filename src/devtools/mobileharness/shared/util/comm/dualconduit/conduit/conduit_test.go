package conduit

import (
	"context"
	"testing"
	"time"

	"github.com/rsocket/rsocket-go/payload"
	"github.com/rsocket/rsocket-go"
	"github.com/rsocket/rsocket-go/rx/flux"
	"github.com/rsocket/rsocket-go/rx/mono"
)

func TestConduitCloseViaPumpCtx(t *testing.T) {
	ctx, cancel := context.WithCancel(t.Context())
	defer cancel()

	removed := make(chan struct{})
	mrs := newMockRSocket()

	c := New(ctx, "test-id", nil, mrs, func() {
		close(removed)
	})

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
	})

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
	})

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
	})

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
