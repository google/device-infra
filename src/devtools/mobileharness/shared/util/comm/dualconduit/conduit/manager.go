package conduit

import (
	"context"
	"errors"
	"log/slog"
	"sync"
	"time"

	"github.com/rsocket/rsocket-go"

	dconpb "github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/proto/dconpb"
	"go.opentelemetry.io/otel/metric"
	"go.opentelemetry.io/otel/trace"
	"go.opentelemetry.io/otel"
)

var (
	// ErrNotFound is returned when a requested conduit ID is not present in the manager.
	ErrNotFound = errors.New("conduit not found")

	// ErrAlreadyExists is returned when trying to Add a conduit with an ID that already exists.
	ErrAlreadyExists = errors.New("conduit already exists")
)

// RemoveHandler is a function that is called when a conduit is removed from the manager.
// We use this to subscribe to the removal events.
type RemoveHandler func(id string, meta *dconpb.EstablishConduitRequest)

// Manager is responsible for storing active conduits and handling their lifecycle across the system.
type Manager struct {
	mu                sync.RWMutex
	conduits          map[string]*Conduit
	removeHandlers    []RemoveHandler // List of removal subscribers.
	durationHistogram metric.Float64Histogram
}

// NewManager creates an empty manager for Conduits.
func NewManager() *Manager {
	m := &Manager{
		conduits: make(map[string]*Conduit),
	}
	m.registerMetrics()
	return m
}

func (m *Manager) registerMetrics() {
	meter := otel.Meter("dualconduit/conduit")

	boundaries := []float64{1, 10, 60, 600, 3600, 86400, 604800}
	durationHistogram, err := meter.Float64Histogram(
		"conduit.duration",
		metric.WithDescription("Duration of the conduit connection"),
		metric.WithUnit("s"),
		metric.WithExplicitBucketBoundaries(boundaries...),
	)
	if err != nil {
		slog.ErrorContext(context.Background(), "Failed to create conduit.duration histogram", "error", err)
	} else {
		m.durationHistogram = durationHistogram
	}

	ageGauge, err := meter.Float64ObservableGauge(
		"conduit.age",
		metric.WithDescription("Current age of active conduits"),
		metric.WithUnit("s"),
	)
	if err != nil {
		slog.ErrorContext(context.Background(), "Failed to create conduit.age gauge", "error", err)
	}

	activeCounter, err := meter.Int64ObservableUpDownCounter(
		"conduit.active_count",
		metric.WithDescription("Number of active conduits"),
		metric.WithUnit("1"),
	)
	if err != nil {
		slog.ErrorContext(context.Background(), "Failed to create conduit.active_count counter", "error", err)
	}

	if ageGauge == nil || activeCounter == nil {
		return
	}

	_, err = meter.RegisterCallback(func(ctx context.Context, obs metric.Observer) error {
		type conduitInfo struct {
			id  string
			age float64
		}
		var active []conduitInfo

		m.mu.RLock()
		obs.ObserveInt64(activeCounter, int64(len(m.conduits)))
		for _, c := range m.conduits {
			active = append(active, conduitInfo{
				id:  c.ID,
				age: time.Since(c.startTime).Seconds(),
			})
		}
		m.mu.RUnlock()

		for _, info := range active {
			obs.ObserveFloat64(ageGauge, info.age)
		}
		return nil
	}, ageGauge, activeCounter)
	if err != nil {
		slog.ErrorContext(context.Background(), "Failed to register metric callback", "error", err)
	}
}

// Add creates a new Conduit with the provided RSocket session and tracks it.
// The ctx acts as the parent context for the conduit. If this context is canceled,
// the conduit will automatically shut down and remove itself from the manager.
//
// Returns ErrAlreadyExists if a Conduit with the same ID already exists.
func (m *Manager) Add(ctx context.Context, id string, meta *dconpb.EstablishConduitRequest, rs rsocket.CloseableRSocket, beforeClose func(), openSpanContext trace.SpanContext) (*Conduit, error) {
	m.mu.Lock()
	defer m.mu.Unlock()

	if _, ok := m.conduits[id]; ok {
		return nil, ErrAlreadyExists
	}

	// onRemove only untracks the conduit from the map. It does NOT call Close()
	// to avoid recursive sync.Once deadlocks when Close() triggers onRemove.
	onRemove := func() {
		m.mu.Lock()
		delete(m.conduits, id)
		subs := make([]RemoveHandler, len(m.removeHandlers))
		copy(subs, m.removeHandlers)
		m.mu.Unlock()
		for _, sub := range subs {
			sub(id, meta)
		}
	}

	c := New(ctx, id, meta, rs, onRemove, beforeClose, openSpanContext, m.durationHistogram)
	m.conduits[id] = c
	return c, nil
}

// Conduit retrieves an active conduit by its unique ID.
func (m *Manager) Conduit(id string) (*Conduit, error) {
	m.mu.RLock()
	defer m.mu.RUnlock()

	c, ok := m.conduits[id]
	if !ok {
		return nil, ErrNotFound
	}
	return c, nil
}

// Remove shuts down the conduit via standard Close() and scrubs it from the active map.
func (m *Manager) Remove(id string) {
	m.mu.Lock()
	c, ok := m.conduits[id]
	if ok {
		delete(m.conduits, id)
	}
	m.mu.Unlock()

	if ok {
		c.Close()
	}
}

// SubscribeToRemove registers a handler that is called whenever a conduit is removed from the manager.
func (m *Manager) SubscribeToRemove(handler RemoveHandler) {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.removeHandlers = append(m.removeHandlers, handler)
}

// Shutdown gracefully terminates all tracked conduits.
// Since c.Close() synchronously calls onRemove (which removes the conduit from the map),
// when Shutdown returns, the manager's conduit map is guaranteed to be empty
func (m *Manager) Shutdown() {
	m.mu.Lock()
	// Create a copy to prevent deadlock while iterating and closing.
	toClose := make([]*Conduit, 0, len(m.conduits))
	for _, c := range m.conduits {
		toClose = append(toClose, c)
	}
	m.mu.Unlock()

	for _, c := range toClose {
		c.Close()
	}
}
