// Package session defines the state of a persistent conduit session.
package session

import (
	"context"
	"sync"

	dconpb "github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/proto/dconpb"
)

// Session represents the state of a persistent conduit session.
type Session struct {
	ID   string
	Meta *dconpb.EstablishSessionRequest

	mu         sync.RWMutex
	conduitIDs map[string]bool // Set of active conduit IDs in this session
	ctx        context.Context
	cancel     context.CancelFunc

	// Callback triggered when a conduit is replaced.
	// Set by the Manager when the session is added.
	onReplace func(oldID, newID string)
}

// New creates a new Session with initial conduit IDs.
func New(ctx context.Context, id string, meta *dconpb.EstablishSessionRequest, conduitIDs []string) *Session {
	sCtx, sCancel := context.WithCancel(ctx)
	conduits := make(map[string]bool)
	for _, cid := range conduitIDs {
		conduits[cid] = true
	}
	return &Session{
		ID:         id,
		Meta:       meta,
		conduitIDs: conduits,
		ctx:        sCtx,
		cancel:     sCancel,
	}
}

// ReplaceConduit replaces a broken conduit ID with a new one.
// Triggers the onReplace callback if set.
func (s *Session) ReplaceConduit(oldID, newID string) {
	s.mu.Lock()
	replaced := false
	if s.conduitIDs[oldID] {
		delete(s.conduitIDs, oldID)
		s.conduitIDs[newID] = true
		replaced = true
	}
	s.mu.Unlock()

	// Call callback outside the lock to prevent lock order inversion / deadlocks.
	if replaced && s.onReplace != nil {
		s.onReplace(oldID, newID)
	}
}

// Conduits returns a slice of active conduit IDs in this session.
func (s *Session) Conduits() []string {
	s.mu.RLock()
	defer s.mu.RUnlock()
	ids := make([]string, 0, len(s.conduitIDs))
	for id := range s.conduitIDs {
		ids = append(ids, id)
	}
	return ids
}

// Context returns the session's context.
func (s *Session) Context() context.Context {
	return s.ctx
}

// Close closes the session and cancels its context.
func (s *Session) Close() {
	s.cancel()
}
