package conduit

import (
	"fmt"
	"sync"
	"testing"
	"time"

	"github.com/rsocket/rsocket-go"
)

func TestManagerAddAndConduit(t *testing.T) {
	m := NewManager()

	var mockRS rsocket.CloseableRSocket
	c, err := m.Add(t.Context(), "test-conduit", nil, mockRS)
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
	c1, err := m.Add(t.Context(), "test-conduit", nil, mockRS)
	if err != nil {
		t.Fatalf("Add() err = %v, want nil", err)
	}

	// Adding another conduit with the same ID should fail.
	c2, err := m.Add(t.Context(), "test-conduit", nil, mockRS)
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
	c, err := m.Add(t.Context(), "test-conduit", nil, mockRS)
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
	c1, err := m.Add(t.Context(), "1", nil, mockRS)
	if err != nil {
		t.Fatalf("Add() for c1 failed err = %v", err)
	}
	c2, err := m.Add(t.Context(), "2", nil, mockRS)
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
				c, err := m.Add(ctx, id, nil, mockRS)
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
