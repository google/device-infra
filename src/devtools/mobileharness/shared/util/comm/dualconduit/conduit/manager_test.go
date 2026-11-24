package conduit

import (
	"context"
	"errors"
	"fmt"
	"maps"
	"sync"
	"testing"
	"time"

	dconpb "github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/proto/dconpb"
	"github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/version"
	"github.com/rsocket/rsocket-go"
	sdkmetric "go.opentelemetry.io/otel/sdk/metric"
	"go.opentelemetry.io/otel/sdk/metric/metricdata"
	"go.opentelemetry.io/otel/trace"
)

func TestManagerAddAndConduit(t *testing.T) {
	m := NewManager()

	var mockRS rsocket.CloseableRSocket
	c, err := m.Add(t.Context(), "test-conduit", nil, mockRS, nil, trace.SpanContext{})
	if err != nil {
		t.Fatalf("Add() err = %v, want nil", err)
	}
	if c.ID != "test-conduit" {
		t.Errorf("Add() ID = %q, want %q", c.ID, "test-conduit")
	}

	fetched, err := m.Conduit("test-conduit")
	if err != nil {
		t.Fatalf("Conduit() err = %v, want nil", err)
	}
	if fetched != c {
		t.Errorf("Conduit() fetched = %v, want %v", fetched, c)
	}

	_, err = m.Conduit("non-existent")
	if err != ErrNotFound {
		t.Errorf("Conduit() err = %v, want %v", err, ErrNotFound)
	}
}

func TestManagerAddDuplicate(t *testing.T) {
	m := NewManager()

	var mockRS rsocket.CloseableRSocket
	c1, err := m.Add(t.Context(), "test-conduit", nil, mockRS, nil, trace.SpanContext{})
	if err != nil {
		t.Fatalf("Add() err = %v, want nil", err)
	}

	// Adding another conduit with the same ID should fail.
	c2, err := m.Add(t.Context(), "test-conduit", nil, mockRS, nil, trace.SpanContext{})
	if err != ErrAlreadyExists {
		t.Errorf("Add() err = %v, want %v", err, ErrAlreadyExists)
	}
	if c2 != nil {
		t.Errorf("Add() created conduit = %v, want nil", c2)
	}

	// The original conduit should NOT be closed.
	select {
	case <-c1.Context().Done():
		t.Errorf("Add() context = done, want open")
	default:
		// Success
	}

	fetched, err := m.Conduit("test-conduit")
	if err != nil {
		t.Fatalf("Conduit() err = %v, want nil", err)
	}
	if fetched != c1 {
		t.Errorf("Conduit() fetched = %v, want %v", fetched, c1)
	}
}

func TestManagerRemove(t *testing.T) {
	m := NewManager()

	var mockRS rsocket.CloseableRSocket
	c, err := m.Add(t.Context(), "test-conduit", nil, mockRS, nil, trace.SpanContext{})
	if err != nil {
		t.Fatalf("Add() failed err = %v", err)
	}
	m.Remove("test-conduit")

	_, err = m.Conduit("test-conduit")
	if err != ErrNotFound {
		t.Errorf("Conduit() after Remove() err = %v, want %v", err, ErrNotFound)
	}

	select {
	case <-c.Context().Done():
		// Success, removing it should cancel its context
	case <-time.After(1 * time.Second):
		t.Errorf("Remove() context active = %v, want done", true)
	}
}

func TestManagerShutdown(t *testing.T) {
	m := NewManager()

	var mockRS rsocket.CloseableRSocket
	c1, err := m.Add(t.Context(), "1", nil, mockRS, nil, trace.SpanContext{})
	if err != nil {
		t.Fatalf("Add() for c1 failed err = %v", err)
	}
	c2, err := m.Add(t.Context(), "2", nil, mockRS, nil, trace.SpanContext{})
	if err != nil {
		t.Fatalf("Add() for c2 failed err = %v", err)
	}

	m.Shutdown()

	select {
	case <-c1.Context().Done():
	default:
		t.Errorf("Shutdown() c1 context active = %v, want done", true)
	}

	select {
	case <-c2.Context().Done():
	default:
		t.Errorf("Shutdown() c2 context active = %v, want done", true)
	}
}

func TestManagerConcurrent(t *testing.T) {
	m := NewManager()
	var wg sync.WaitGroup
	var mockRS rsocket.CloseableRSocket

	// Concurrently add, conduit, and remove conduits to test for races/deadlocks.
	numWorkers := 10
	iterations := 100

	for i := 0; i < numWorkers; i++ {
		wg.Add(1)
		go func(workerID int) {
			defer wg.Done()
			for j := 0; j < iterations; j++ {
				id := fmt.Sprintf("conduit-%d-%d", workerID, j)

				// Add
				ctx := t.Context()
				c, err := m.Add(ctx, id, nil, mockRS, nil, trace.SpanContext{})
				if err != nil {
					t.Errorf("Add(%q) failed: %v", id, err)
					continue
				}

				// Get
				_, err = m.Conduit(id)
				if err != nil {
					t.Errorf("Conduit(%q) failed: %v", id, err)
				}

				// Close the conduit (triggers onRemove which calls m.Remove).
				c.Close()

				// Remove again directly (should be safe and a no-op if already removed).
				m.Remove(id)
			}
		}(i)
	}

	wg.Wait()
}

func TestManagerSubscribeToRemove_Remove(t *testing.T) {
	m := NewManager()

	var called1, called2 bool
	var gotID1, gotID2 string
	var gotMeta1, gotMeta2 *dconpb.EstablishConduitRequest

	handler1 := func(id string, meta *dconpb.EstablishConduitRequest) {
		called1 = true
		gotID1 = id
		gotMeta1 = meta
	}
	handler2 := func(id string, meta *dconpb.EstablishConduitRequest) {
		called2 = true
		gotID2 = id
		gotMeta2 = meta
	}

	// 1. SubscribeToRemove before Add.
	m.SubscribeToRemove(handler1)

	var mockRS rsocket.CloseableRSocket
	meta := &dconpb.EstablishConduitRequest{
		ServerName: "test-server",
	}

	_, err := m.Add(t.Context(), "conduit-1", meta, mockRS, nil, trace.SpanContext{})
	if err != nil {
		t.Fatalf("Add() err = %v, want nil", err)
	}

	// 2. SubscribeToRemove after Add.
	// Since remove handlers are dynamically copied at removal time from the Manager instance,
	// handlers registered after a conduit is added but before it is removed also get invoked.
	m.SubscribeToRemove(handler2)

	m.Remove("conduit-1")

	if !called1 {
		t.Errorf("handler1 (registered before Add) was not called on Remove()")
	}
	if gotID1 != "conduit-1" {
		t.Errorf("handler1 got id %q, want %q", gotID1, "conduit-1")
	}
	if gotMeta1 != meta {
		t.Errorf("handler1 got meta %v, want %v", gotMeta1, meta)
	}

	if !called2 {
		t.Errorf("handler2 (registered after Add) was not called on Remove()")
	}
	if gotID2 != "conduit-1" {
		t.Errorf("handler2 got id %q, want %q", gotID2, "conduit-1")
	}
	if gotMeta2 != meta {
		t.Errorf("handler2 got meta %v, want %v", gotMeta2, meta)
	}
}

func TestManagerSubscribeToRemove_Close(t *testing.T) {
	m := NewManager()

	var called bool
	var gotID string
	var gotMeta *dconpb.EstablishConduitRequest

	m.SubscribeToRemove(func(id string, meta *dconpb.EstablishConduitRequest) {
		called = true
		gotID = id
		gotMeta = meta
	})

	var mockRS rsocket.CloseableRSocket
	meta := &dconpb.EstablishConduitRequest{
		ServerName: "test-server",
	}

	c, err := m.Add(t.Context(), "conduit-2", meta, mockRS, nil, trace.SpanContext{})
	if err != nil {
		t.Fatalf("Add() err = %v, want nil", err)
	}

	c.Close()

	if !called {
		t.Errorf("handler was not called on c.Close()")
	}
	if gotID != "conduit-2" {
		t.Errorf("handler got id %q, want %q", gotID, "conduit-2")
	}
	if gotMeta != meta {
		t.Errorf("handler got meta %v, want %v", gotMeta, meta)
	}
}

func TestManagerSubscribeToRemove_Shutdown(t *testing.T) {
	m := NewManager()

	var mu sync.Mutex
	removedIDs := make(map[string]bool)

	m.SubscribeToRemove(func(id string, meta *dconpb.EstablishConduitRequest) {
		mu.Lock()
		defer mu.Unlock()
		removedIDs[id] = true
	})

	var mockRS rsocket.CloseableRSocket
	_, err := m.Add(t.Context(), "conduit-a", nil, mockRS, nil, trace.SpanContext{})
	if err != nil {
		t.Fatalf("Add() err = %v, want nil", err)
	}
	_, err = m.Add(t.Context(), "conduit-b", nil, mockRS, nil, trace.SpanContext{})
	if err != nil {
		t.Fatalf("Add() err = %v, want nil", err)
	}

	m.Shutdown()

	mu.Lock()
	defer mu.Unlock()
	if !removedIDs["conduit-a"] {
		t.Errorf("conduit-a was not reported as removed after Shutdown()")
	}
	if !removedIDs["conduit-b"] {
		t.Errorf("conduit-b was not reported as removed after Shutdown()")
	}
}

func TestManagerSubscribeToRemove_ContextCancel(t *testing.T) {
	m := NewManager()

	removedChan := make(chan string, 1)
	m.SubscribeToRemove(func(id string, meta *dconpb.EstablishConduitRequest) {
		removedChan <- id
	})

	ctx, cancel := context.WithCancel(t.Context())
	var mockRS rsocket.CloseableRSocket
	_, err := m.Add(ctx, "conduit-cancel", nil, mockRS, nil, trace.SpanContext{})
	if err != nil {
		cancel()
		t.Fatalf("Add() err = %v, want nil", err)
	}

	cancel()

	select {
	case id := <-removedChan:
		if id != "conduit-cancel" {
			t.Errorf("handler got id %q, want %q", id, "conduit-cancel")
		}
	case <-time.After(1 * time.Second):
		t.Errorf("handler was not called within 1s after context cancellation")
	}
}

func TestManagerActiveCounterWithDialerID(t *testing.T) {
	m := NewManager()

	var mockRS rsocket.CloseableRSocket
	meta1 := &dconpb.EstablishConduitRequest{InstanceId: "dialer-1"}
	meta2 := &dconpb.EstablishConduitRequest{InstanceId: "dialer-2"}

	c1, err := m.Add(t.Context(), "conduit-1", meta1, mockRS, nil, trace.SpanContext{})
	if err != nil {
		t.Fatalf("Add() for conduit-1 failed: %v", err)
	}
	defer c1.Close()

	c2, err := m.Add(t.Context(), "conduit-2", meta2, mockRS, nil, trace.SpanContext{})
	if err != nil {
		t.Fatalf("Add() for conduit-2 failed: %v", err)
	}
	defer c2.Close()

	fetched1, err := m.Conduit("conduit-1")
	if err != nil || fetched1 != c1 {
		t.Errorf("Conduit(conduit-1) = (%v, %v), want (%v, nil)", fetched1, err, c1)
	}

	fetched2, err := m.Conduit("conduit-2")
	if err != nil || fetched2 != c2 {
		t.Errorf("Conduit(conduit-2) = (%v, %v), want (%v, nil)", fetched2, err, c2)
	}
}

// TestManagerRemove_ConduitLeavesManagerLast checks that a conduit stays in
// the manager while it closes and leaves it as the last step: until then its
// connection and its entry port still exist.
func TestManagerRemove_ConduitLeavesManagerLast(t *testing.T) {
	m := NewManager()
	inSignal, release := make(chan struct{}), make(chan struct{})
	beforeClose := func() {
		close(inSignal)
		<-release
	}
	if _, err := m.Add(t.Context(), "c1", nil, newMockRSocket(), beforeClose, trace.SpanContext{}); err != nil {
		t.Fatalf("Add() got unexpected error: %v", err)
	}
	handled := make(chan error, 1)
	m.SubscribeToRemove(func(id string, _ *dconpb.EstablishConduitRequest) {
		_, err := m.Conduit(id)
		handled <- err
	})

	removed := make(chan struct{})
	go func() {
		m.Remove("c1")
		close(removed)
	}()
	select {
	case <-inSignal:
	case <-time.After(5 * time.Second):
		t.Fatalf("Remove() did not start closing the conduit within 5s")
	}
	c, err := m.Conduit("c1")
	if err != nil {
		t.Fatalf("Conduit() of a conduit that is closing got error %v, want the conduit", err)
	}
	if got := c.State(); got != StateClosing {
		t.Errorf("State() of a conduit that is closing = %v, want %v", got, StateClosing)
	}
	select {
	case <-removed:
		t.Errorf("Remove() returned while the conduit was still closing")
	default:
	}

	close(release)
	select {
	case <-removed:
	case <-time.After(5 * time.Second):
		t.Fatalf("Remove() did not return within 5s after closing went on")
	}
	if _, err := m.Conduit("c1"); !errors.Is(err, ErrNotFound) {
		t.Errorf("Conduit() after Remove() got error %v, want %v", err, ErrNotFound)
	}
	select {
	case err := <-handled:
		if !errors.Is(err, ErrNotFound) {
			t.Errorf("Conduit() in the removal handler got error %v, want %v", err, ErrNotFound)
		}
	case <-time.After(5 * time.Second):
		t.Errorf("removal handler was not called within 5s")
	}
}

// TestManagerActiveCount_ByDialerAndPeerProtocol checks the metric that tells
// which dialers still speak an old protocol: conduit.active_count has one
// series per dialer and protocol version of the other side, and counts only
// the conduits that are ACTIVE.
func TestManagerActiveCount_ByDialerAndPeerProtocol(t *testing.T) {
	reader := sdkmetric.NewManualReader()
	provider := sdkmetric.NewMeterProvider(sdkmetric.WithReader(reader))
	defer provider.Shutdown(t.Context())
	m := newManager(provider.Meter("test"))

	add := func(id, dialerID string, peerProtocol int32, beforeClose func()) *Conduit {
		t.Helper()
		c, err := m.Add(t.Context(), id, &dconpb.EstablishConduitRequest{InstanceId: dialerID}, newMockRSocket(), beforeClose, trace.SpanContext{})
		if err != nil {
			t.Fatalf("Add(%q) got unexpected error: %v", id, err)
		}
		c.SetPeerProtocol(peerProtocol)
		return c
	}
	add("c1", "worker-1", version.ProtocolVersion, nil)
	add("c2", "worker-1", version.ProtocolVersion, nil)
	add("c3", "worker-2", 0, nil)
	add("c4", "worker-2", 0, nil)
	add("c5", "worker-2", 0, nil)
	// A conduit that is closing is still in the manager and must not count.
	inSignal, release := make(chan struct{}), make(chan struct{})
	closing := add("c6", "worker-3", version.ProtocolVersion, func() {
		close(inSignal)
		<-release
	})
	defer close(release)
	closing.BeginClose()
	waitClosed(t, inSignal, "the closing step of the conduit")

	var got metricdata.ResourceMetrics
	if err := reader.Collect(t.Context(), &got); err != nil {
		t.Fatalf("reader.Collect() got unexpected error: %v", err)
	}
	type series struct {
		dialerID     string
		peerProtocol int64
	}
	counts := make(map[series]int64)
	for _, sm := range got.ScopeMetrics {
		for _, metric := range sm.Metrics {
			if metric.Name != "conduit.active_count" {
				continue
			}
			sum, ok := metric.Data.(metricdata.Sum[int64])
			if !ok {
				t.Fatalf("conduit.active_count has data of type %T, want metricdata.Sum[int64]", metric.Data)
			}
			for _, point := range sum.DataPoints {
				dialerID, _ := point.Attributes.Value("conduit.dialer_id")
				peerProtocol, ok := point.Attributes.Value("conduit.peer_protocol")
				if !ok {
					t.Errorf("conduit.active_count series of dialer %q has no attribute conduit.peer_protocol", dialerID.AsString())
				}
				counts[series{dialerID.AsString(), peerProtocol.AsInt64()}] += point.Value
			}
		}
	}
	want := map[series]int64{
		{"worker-1", int64(version.ProtocolVersion)}: 2,
		{"worker-2", 0}: 3,
	}
	if !maps.Equal(counts, want) {
		t.Errorf("conduit.active_count by dialer and peer protocol = %v, want %v", counts, want)
	}
}
