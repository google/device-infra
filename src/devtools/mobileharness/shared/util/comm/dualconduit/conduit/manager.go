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
	"go.opentelemetry.io/otel/attribute"
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
	brokenCounter     metric.Int64Counter
}

// NewManager creates an empty manager for Conduits.
func NewManager() *Manager {
	return newManager(otel.Meter("dualconduit/conduit"))
}

// newManager creates an empty manager that reports its metrics to meter.
func newManager(meter metric.Meter) *Manager {
	m := &Manager{
		conduits: make(map[string]*Conduit),
	}
	m.registerMetrics(meter)
	return m
}

func (m *Manager) registerMetrics(meter metric.Meter) {

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

	brokenCounter, err := meter.Int64Counter(
		"conduit.broken_count",
		metric.WithDescription("Number of broken conduits"),
		metric.WithUnit("1"),
	)
	if err != nil {
		slog.ErrorContext(context.Background(), "Failed to create conduit.broken_count counter", "error", err)
	} else {
		m.brokenCounter = brokenCounter
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
		metric.WithDescription("Number of active conduits, by dialer and by the DualConduit protocol version of the other side"),
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
			id       string
			dialerID string
			age      float64
		}
		// activeGroup is the attribute set of one series of
		// conduit.active_count.
		type activeGroup struct {
			dialerID string
			// peerProtocol is the DualConduit protocol version of the other
			// side. On an acceptor it tells which dialers still speak an old
			// protocol. A dialer does not learn the protocol of its
			// acceptor, so there it is the dialer's own.
			peerProtocol int32
		}
		var active []conduitInfo
		counts := make(map[activeGroup]int64)

		m.mu.RLock()
		for _, c := range m.conduits {
			// A conduit stays in the manager while it closes.
			if c.State() != StateActive {
				continue
			}
			var dialerID string
			if c.metadata != nil {
				dialerID = c.metadata.GetInstanceId()
			}
			counts[activeGroup{dialerID: dialerID, peerProtocol: c.peerProtocol.Load()}]++
			active = append(active, conduitInfo{
				id:       c.ID,
				dialerID: dialerID,
				age:      time.Since(c.startTime).Seconds(),
			})
		}
		m.mu.RUnlock()

		for group, count := range counts {
			obs.ObserveInt64(activeCounter, count, metric.WithAttributes(
				attribute.String("conduit.dialer_id", group.dialerID),
				attribute.Int("conduit.peer_protocol", int(group.peerProtocol)),
			))
		}

		for _, info := range active {
			obs.ObserveFloat64(ageGauge, info.age, metric.WithAttributes(attribute.String("conduit.dialer_id", info.dialerID)))
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

	// onRemove only untracks the conduit from the map. It is the last step
	// of closing the conduit, so it must not call Close().
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

	c := New(ctx, id, meta, rs, onRemove, beforeClose, openSpanContext, m.durationHistogram, m.brokenCounter)
	m.conduits[id] = c
	return c, nil
}

// Conduit retrieves a conduit by its unique ID. The conduit is ACTIVE or
// CLOSING, or CLOSED for the moment in which it leaves the manager.
func (m *Manager) Conduit(id string) (*Conduit, error) {
	m.mu.RLock()
	defer m.mu.RUnlock()

	c, ok := m.conduits[id]
	if !ok {
		return nil, ErrNotFound
	}
	return c, nil
}

// Remove closes the conduit and returns when it is CLOSED. The conduit stays
// in the manager while it closes and leaves it as the last step of closing.
func (m *Manager) Remove(id string) {
	m.mu.RLock()
	c, ok := m.conduits[id]
	m.mu.RUnlock()

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
// c.Close() returns when the conduit is CLOSED and out of the map, so when
// Shutdown returns, none of the conduits that it found is left in the manager.
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
