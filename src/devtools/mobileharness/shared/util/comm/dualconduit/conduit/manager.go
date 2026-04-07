package conduit

import (
	"context"
	"errors"
	"sync"

	"github.com/rsocket/rsocket-go"

	dconpb "github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/proto/dconpb"
)

var (
	// ErrNotFound is returned when a requested conduit ID is not present in the manager.
	ErrNotFound = errors.New("conduit not found")

	// ErrAlreadyExists is returned when trying to Add a conduit with an ID that already exists.
	ErrAlreadyExists = errors.New("conduit already exists")
)

// Manager is responsible for storing active conduits and handling their lifecycle across the system.
type Manager struct {
	mu       sync.RWMutex
	conduits map[string]*Conduit
}

// NewManager creates an empty manager for Conduits.
func NewManager() *Manager {
	return &Manager{
		conduits: make(map[string]*Conduit),
	}
}

// Add creates a new Conduit with the provided RSocket session and tracks it.
// The ctx acts as the parent context for the conduit. If this context is canceled,
// the conduit will automatically shut down and remove itself from the manager.
//
// Returns ErrAlreadyExists if a Conduit with the same ID already exists.
func (m *Manager) Add(ctx context.Context, id string, meta *dconpb.EstablishConduitRequest, rs rsocket.CloseableRSocket, beforeClose func()) (*Conduit, error) {
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
		m.mu.Unlock()
	}

	c := New(ctx, id, meta, rs, onRemove, beforeClose)
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
