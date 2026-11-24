package session

import (
	"context"
	"errors"
	"testing"

	dconpb "github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/proto/dconpb"
)

func TestManager_AddAndSession(t *testing.T) {
	m := NewManager()
	s := New(context.Background(), "session-1", &dconpb.EstablishSessionRequest{}, []string{"c1"})

	if err := m.Add(s); err != nil {
		t.Fatalf("failed to add session: %v", err)
	}

	// Try to add duplicate
	if err := m.Add(s); !errors.Is(err, ErrAlreadyExists) {
		t.Errorf("Add(%v) got %v, want ErrAlreadyExists", s.ID, err)
	}

	retrieved, err := m.Session("session-1")
	if err != nil {
		t.Fatalf("failed to retrieve session: %v", err)
	}
	if retrieved != s {
		t.Errorf("Session(\"session-1\") got %v, want %v", retrieved, s)
	}

	// Retrieve non-existent
	if retrieved, err := m.Session("non-existent"); !errors.Is(err, ErrNotFound) {
		t.Errorf("Session(\"non-existent\") got (%v, %v), want (nil, ErrNotFound)", retrieved, err)
	}
}

func TestManager_FindByConduitID(t *testing.T) {
	m := NewManager()
	s1 := New(context.Background(), "session-1", &dconpb.EstablishSessionRequest{}, []string{"c1", "c2"})
	s2 := New(context.Background(), "session-2", &dconpb.EstablishSessionRequest{}, []string{"c3"})

	_ = m.Add(s1)
	_ = m.Add(s2)

	if retrieved, ok := m.FindByConduitID("c1"); !ok || retrieved != s1 {
		t.Errorf("FindByConduitID(\"c1\") got (%v, %t), want (%v, true)", retrieved, ok, s1)
	}
	if retrieved, ok := m.FindByConduitID("c3"); !ok || retrieved != s2 {
		t.Errorf("FindByConduitID(\"c3\") got (%v, %t), want (%v, true)", retrieved, ok, s2)
	}
	if retrieved, ok := m.FindByConduitID("non-existent"); ok || retrieved != nil {
		t.Errorf("FindByConduitID(\"non-existent\") got (%v, %t), want (nil, false)", retrieved, ok)
	}
}

func TestManager_AutoUpdateMappingOnReplace(t *testing.T) {
	m := NewManager()
	s := New(context.Background(), "session-1", &dconpb.EstablishSessionRequest{}, []string{"c1"})
	_ = m.Add(s)

	// Verify initial mapping
	if retrieved, ok := m.FindByConduitID("c1"); !ok || retrieved != s {
		t.Errorf("FindByConduitID(\"c1\") got (%v, %t), want (%v, true)", retrieved, ok, s)
	}

	// Replace conduit in session
	s.ReplaceConduit("c1", "c2")

	// Verify mapping updated
	if retrieved, ok := m.FindByConduitID("c1"); ok || retrieved != nil {
		t.Errorf("FindByConduitID(\"c1\") got (%v, %t), want (nil, false)", retrieved, ok)
	}
	if retrieved, ok := m.FindByConduitID("c2"); !ok || retrieved != s {
		t.Errorf("FindByConduitID(\"c2\") got (%v, %t), want (%v, true)", retrieved, ok, s)
	}
}

func TestManager_Remove(t *testing.T) {
	m := NewManager()
	s := New(context.Background(), "session-1", &dconpb.EstablishSessionRequest{}, []string{"c1", "c2"})
	_ = m.Add(s)

	// Verify mappings exist
	if _, ok := m.FindByConduitID("c1"); !ok {
		t.Error("expected mapping for c1 to exist")
	}

	// Remove session
	if err := m.Remove("session-1"); err != nil {
		t.Fatalf("failed to remove session: %v", err)
	}

	// Verify session is gone
	if retrieved, err := m.Session("session-1"); !errors.Is(err, ErrNotFound) {
		t.Errorf("Session(\"session-1\") got (%v, %v), want (nil, ErrNotFound)", retrieved, err)
	}

	// Verify mappings are gone
	if _, ok := m.FindByConduitID("c1"); ok {
		t.Error("expected mapping for c1 to be removed")
	}
	if _, ok := m.FindByConduitID("c2"); ok {
		t.Error("expected mapping for c2 to be removed")
	}

	// Try to remove again
	if err := m.Remove("session-1"); !errors.Is(err, ErrNotFound) {
		t.Errorf("Remove(\"session-1\") got %v, want ErrNotFound", err)
	}
}

// TestManager_ConduitAddedLater checks that a conduit that joins a session
// after the session was added to the manager is found by its ID. A session is
// added before its conduits are established, so that it is found when one of
// them is removed.
func TestManager_ConduitAddedLater(t *testing.T) {
	m := NewManager()
	s := New(context.Background(), "session-1", &dconpb.EstablishSessionRequest{}, nil)
	if err := m.Add(s); err != nil {
		t.Fatalf("Add() got unexpected error: %v", err)
	}
	if got := m.Sessions(); len(got) != 1 || got[0] != s {
		t.Errorf("Sessions() = %v, want the one session that was added", got)
	}

	if !s.AddConduit("c1") {
		t.Fatalf("AddConduit() = false, want true")
	}

	if got, ok := m.FindByConduitID("c1"); !ok || got != s {
		t.Errorf("FindByConduitID(%q) = (%v, %t), want the session", "c1", got, ok)
	}

	// A conduit that joins after the session was removed is not indexed.
	if err := m.Remove("session-1"); err != nil {
		t.Fatalf("Remove() got unexpected error: %v", err)
	}
	s.AddConduit("c2")
	if _, ok := m.FindByConduitID("c2"); ok {
		t.Errorf("FindByConduitID(%q) found a session that was removed", "c2")
	}
}

// TestManager_ReplaceAfterRemoveLeavesNoIndexEntry covers a session that is
// removed between the moment it replaces a conduit and the moment the
// manager updates its index. The index must not keep the conduit that was
// replaced.
func TestManager_ReplaceAfterRemoveLeavesNoIndexEntry(t *testing.T) {
	m := NewManager()
	s := New(context.Background(), "session-1", &dconpb.EstablishSessionRequest{}, []string{"c1"})
	if err := m.Add(s); err != nil {
		t.Fatalf("Add() got unexpected error: %v", err)
	}
	// The session is removed after it has replaced the conduit and before
	// the manager is told.
	updateIndex := s.onReplace
	s.onReplace = func(oldID, newID string) {
		if err := m.Remove(s.ID); err != nil {
			t.Errorf("Remove() got unexpected error: %v", err)
		}
		updateIndex(oldID, newID)
	}

	s.ReplaceConduit("c1", "c2")

	for _, id := range []string{"c1", "c2"} {
		if _, ok := m.FindByConduitID(id); ok {
			t.Errorf("FindByConduitID(%q) found a session that was removed", id)
		}
	}
	if got := len(m.conduitToSession); got != 0 {
		t.Errorf("the index of the manager has %d entries after its only session was removed, want 0: %v", got, m.conduitToSession)
	}
}
