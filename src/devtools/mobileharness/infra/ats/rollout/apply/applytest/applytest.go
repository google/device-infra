// Package applytest checks a decision function against the reconciler
// contract (see "Reconciler contract" in node_auto_rollout_concepts.md): on a
// store nobody else writes to, repeated runs reach a fixpoint, and a run that
// crashes after any of its writes is repaired by the runs that follow.
package applytest

import (
	"context"
	"errors"
	"testing"

	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/apply"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/store/fake"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/store"
	"github.com/google/go-cmp/cmp"
)

// Step is the read-and-decide half of one run: it reads s and returns the
// decisions for apply.Apply. A test passes the same now to every call, so
// that the runs differ only in what the store holds.
type Step func(ctx context.Context, s store.Store) ([]apply.Decision, error)

// ErrInjected is the error of every write that a Faulty writer refuses.
var ErrInjected = errors.New("injected write failure")

// Faulty is an apply.Writer that passes a number of writes through and
// refuses every write after them, the way a process that crashed makes no
// further writes.
type Faulty struct {
	w       apply.Writer
	left    int
	passed  int
	refused int
}

// FailAfter returns a writer that passes the first k writes to w and refuses
// the rest with ErrInjected. With k of zero or less it refuses every write.
func FailAfter(w apply.Writer, k int) *Faulty {
	return &Faulty{w: w, left: k}
}

// Passed returns the number of writes passed through to the store.
func (f *Faulty) Passed() int { return f.passed }

// Refused returns the number of writes refused.
func (f *Faulty) Refused() int { return f.refused }

// pass reports whether the next write goes through, and counts it.
func (f *Faulty) pass() bool {
	if f.left <= 0 {
		f.refused++
		return false
	}
	f.left--
	f.passed++
	return true
}

// Create implements apply.Writer.
func (f *Faulty) Create(ctx context.Context, key store.Key, r store.Record) (store.Version, error) {
	if !f.pass() {
		return store.Version{}, ErrInjected
	}
	return f.w.Create(ctx, key, r)
}

// Update implements apply.Writer.
func (f *Faulty) Update(ctx context.Context, key store.Key, r store.Record, v store.Version) (store.Version, error) {
	if !f.pass() {
		return store.Version{}, ErrInjected
	}
	return f.w.Update(ctx, key, r, v)
}

// Delete implements apply.Writer.
func (f *Faulty) Delete(ctx context.Context, key store.Key, v store.Version) error {
	if !f.pass() {
		return ErrInjected
	}
	return f.w.Delete(ctx, key, v)
}

// RunToFixpoint runs step and applies its decisions until a run makes no
// write, and returns the number of writes made on the way. It fails the test
// if a step or a write fails, if no fixpoint is reached within maxRuns runs,
// or if one more run after the fixpoint makes a write.
func RunToFixpoint(ctx context.Context, t testing.TB, s *fake.Store, step Step, maxRuns int) int {
	t.Helper()
	total := 0
	for run := 1; run <= maxRuns; run++ {
		n := runOnce(ctx, t, s, step)
		if n > 0 {
			total += n
			continue
		}
		if again := runOnce(ctx, t, s, step); again != 0 {
			t.Fatalf("run %d made no write, but the run after it made %d: the fixpoint does not hold", run, again)
		}
		return total
	}
	t.Fatalf("no fixpoint within %d runs (%d writes so far)", maxRuns, total)
	return total
}

// runOnce does one uninterrupted run and returns the number of its writes.
func runOnce(ctx context.Context, t testing.TB, s *fake.Store, step Step) int {
	t.Helper()
	decisions, err := step(ctx, s)
	if err != nil {
		t.Fatalf("step failed: %v", err)
	}
	res := apply.Apply(ctx, s, decisions, apply.Options{})
	if len(res.Errs) > 0 || res.Exhausted {
		t.Fatalf("apply.Apply() = %+v, want no error on a store with no other writer", res)
	}
	return res.Attempted
}

// CheckCrashes checks that a crash after any single write is repaired. It
// first runs step to its fixpoint on a store from seed. Then, for every write
// made on the way, it starts again from a new seed store, lets the runs
// proceed until that many writes have landed, refuses the rest of the writes
// of the run that was in progress, and runs to the fixpoint. It reports an
// error for every crash point whose fixpoint differs from the uninterrupted
// one; record versions are not compared.
//
// seed returns a new store with the same content on every call.
func CheckCrashes(ctx context.Context, t testing.TB, seed func() *fake.Store, step Step, maxRuns int) {
	t.Helper()
	uninterrupted := seed()
	total := RunToFixpoint(ctx, t, uninterrupted, step, maxRuns)
	want := content(uninterrupted)
	for k := 0; k < total; k++ {
		s := seed()
		crashAfter(ctx, t, s, step, k)
		RunToFixpoint(ctx, t, s, step, maxRuns)
		if diff := cmp.Diff(want, content(s)); diff != "" {
			t.Errorf("fixpoint after a crash %d writes into %d differs from the uninterrupted fixpoint (-want +got):\n%s", k, total, diff)
		}
	}
}

// crashAfter runs step until k writes have landed in s, and refuses the
// remaining writes of the run in which the k-th write landed.
func crashAfter(ctx context.Context, t testing.TB, s *fake.Store, step Step, k int) {
	t.Helper()
	for {
		decisions, err := step(ctx, s)
		if err != nil {
			t.Fatalf("step failed: %v", err)
		}
		f := FailAfter(s, k)
		res := apply.Apply(ctx, f, decisions, apply.Options{})
		if res.Exhausted {
			t.Fatalf("apply.Apply() ran out of time before the crash point: %+v", res)
		}
		for _, err := range res.Errs {
			// The store has no other writer, so the only writes that may
			// fail are the ones refused after the crash point.
			if !errors.Is(err, ErrInjected) {
				t.Fatalf("a write failed before the crash point: %v", err)
			}
		}
		if f.Refused() > 0 || f.Passed() == 0 {
			return
		}
		k -= f.Passed()
	}
}

// content returns the records of s by key path, without versions.
func content(s *fake.Store) map[string]store.Record {
	records := map[string]store.Record{}
	for _, it := range s.All() {
		records[it.Key.String()] = it.Record
	}
	return records
}
