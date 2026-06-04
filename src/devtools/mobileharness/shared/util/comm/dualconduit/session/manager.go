package session

import (
	"errors"
	"sync"
)

var (
	// ErrNotFound is returned when a session is not found.
	ErrNotFound = errors.New("session not found")
	// ErrAlreadyExists is returned when trying to Add a session that already exists.
	ErrAlreadyExists = errors.New("session already exists")
)

// Manager stores and retrieves sessions, maintaining an index for fast lookup by conduit ID.
type Manager struct {
	mu               sync.RWMutex
	sessions         map[string]*Session
	conduitToSession map[string]string // Fast lookup: conduitID -> sessionID
}

// NewManager creates a new Manager.
func NewManager() *Manager {
	return &Manager{
		sessions:         make(map[string]*Session),
		conduitToSession: make(map[string]string),
	}
}

// Add tracks a new session and registers its onReplace callback.
func (m *Manager) Add(s *Session) error {
	// Query conduits before locking manager to avoid lock order inversion.
	cids := s.Conduits()

	m.mu.Lock()
	defer m.mu.Unlock()
	if _, ok := m.sessions[s.ID]; ok {
		return ErrAlreadyExists
	}
	m.sessions[s.ID] = s
	for _, cid := range cids {
		m.conduitToSession[cid] = s.ID
	}

	// Register the update callback to automatically keep index in sync.
	s.onReplace = func(oldID, newID string) {
		m.mu.Lock()
		defer m.mu.Unlock()
		// If the session has been removed from the manager, do not update the conduit index.
		if _, ok := m.sessions[s.ID]; !ok {
			return
		}
		delete(m.conduitToSession, oldID)
		m.conduitToSession[newID] = s.ID
	}

	return nil
}

// Session retrieves a session by its unique ID.
func (m *Manager) Session(id string) (*Session, error) {
	m.mu.RLock()
	defer m.mu.RUnlock()
	s, ok := m.sessions[id]
	if !ok {
		return nil, ErrNotFound
	}
	return s, nil
}

// FindByConduitID performs an O(1) lookup to find the session owning the given conduit ID.
func (m *Manager) FindByConduitID(conduitID string) (*Session, bool) {
	m.mu.RLock()
	defer m.mu.RUnlock()
	sessionID, ok := m.conduitToSession[conduitID]
	if !ok {
		return nil, false
	}
	s, ok := m.sessions[sessionID]
	return s, ok
}

// Remove deletes the session from the manager and clears the index.
func (m *Manager) Remove(id string) error {
	m.mu.Lock()
	defer m.mu.Unlock()

	s, ok := m.sessions[id]
	if !ok {
		return ErrNotFound
	}

	delete(m.sessions, id)
	for _, cid := range s.Conduits() {
		delete(m.conduitToSession, cid)
	}
	return nil
}
