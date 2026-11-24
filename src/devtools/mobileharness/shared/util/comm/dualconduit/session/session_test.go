package session

import (
	"context"
	"slices"
	"testing"

	dconpb "github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/proto/dconpb"
)

func TestNewSession(t *testing.T) {
	ctx := context.Background()
	req := &dconpb.EstablishSessionRequest{}
	cids := []string{"c1", "c2"}
	s := New(ctx, "session-1", req, cids)

	if s.ID != "session-1" {
		t.Errorf("s.ID = %q, want 'session-1'", s.ID)
	}
	if s.Meta != req {
		t.Errorf("s.Meta = %v, want %v", s.Meta, req)
	}

	activeCids := s.Conduits()
	if len(activeCids) != 2 {
		t.Errorf("len(s.Conduits()) = %d, want 2", len(activeCids))
	}

	cidMap := make(map[string]bool)
	for _, cid := range activeCids {
		cidMap[cid] = true
	}
	if !cidMap["c1"] || !cidMap["c2"] {
		t.Errorf("s.Conduits() = %v, want ['c1', 'c2']", activeCids)
	}
}

func TestReplaceConduit(t *testing.T) {
	ctx := context.Background()
	s := New(ctx, "session-1", &dconpb.EstablishSessionRequest{}, []string{"c1"})

	callbackCalled := false
	var gotOld, gotNew string
	s.onReplace = func(oldID, newID string) {
		callbackCalled = true
		gotOld = oldID
		gotNew = newID
	}

	// Replace existing
	s.ReplaceConduit("c1", "c2")

	activeCids := s.Conduits()
	if len(activeCids) != 1 || activeCids[0] != "c2" {
		t.Errorf("s.Conduits() = %v, want ['c2']", activeCids)
	}

	if !callbackCalled {
		t.Error("expected onReplace callback to be called")
	}
	if gotOld != "c1" || gotNew != "c2" {
		t.Errorf("onReplace called with (%q, %q), want ('c1', 'c2')", gotOld, gotNew)
	}
}

func TestReplaceConduit_NotFound(t *testing.T) {
	ctx := context.Background()
	s := New(ctx, "session-1", &dconpb.EstablishSessionRequest{}, []string{"c1"})

	callbackCalled := false
	s.onReplace = func(oldID, newID string) {
		callbackCalled = true
	}

	// Try to replace non-existing
	s.ReplaceConduit("non-existent", "c2")

	activeCids := s.Conduits()
	if len(activeCids) != 1 || activeCids[0] != "c1" {
		t.Errorf("s.Conduits() = %v, want ['c1']", activeCids)
	}

	if callbackCalled {
		t.Error("expected onReplace callback NOT to be called")
	}
}

// TestSession_ConduitsOfClosedSession checks that a closed session takes no
// conduit: a conduit that is established after its session was torn down
// belongs to nobody and must be closed by the caller.
func TestSession_ConduitsOfClosedSession(t *testing.T) {
	s := New(t.Context(), "session-1", &dconpb.EstablishSessionRequest{}, []string{"c1"})
	if !s.AddConduit("c2") {
		t.Errorf("AddConduit() on an open session = false, want true")
	}
	if !s.ReplaceConduit("c1", "c3") {
		t.Errorf("ReplaceConduit() of a conduit of an open session = false, want true")
	}
	if s.ReplaceConduit("c1", "c4") {
		t.Errorf("ReplaceConduit() of a conduit that the session does not have = true, want false")
	}

	s.Close()

	if s.AddConduit("c5") {
		t.Errorf("AddConduit() on a closed session = true, want false")
	}
	if s.ReplaceConduit("c2", "c6") {
		t.Errorf("ReplaceConduit() on a closed session = true, want false")
	}
	got := s.Conduits()
	slices.Sort(got)
	if want := []string{"c2", "c3"}; !slices.Equal(got, want) {
		t.Errorf("Conduits() = %v, want %v", got, want)
	}
}
