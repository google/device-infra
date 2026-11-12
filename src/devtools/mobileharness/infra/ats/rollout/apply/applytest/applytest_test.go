package applytest

import (
	"context"
	"errors"
	"fmt"
	"runtime"
	"strings"
	"testing"

	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/apply"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/store/fake"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/store"
)

var (
	parentKey = store.NewKey("parents", "p")
	markKeys  = []store.Key{store.NewKey("marks", "m0"), store.NewKey("marks", "m1")}
)

func childKey(n int) store.Key {
	return parentKey.Child("children", fmt.Sprint(n))
}

// seedStore returns a store with a parent that is not done and two records
// that are not marked.
func seedStore(t *testing.T) *fake.Store {
	t.Helper()
	ctx := context.Background()
	s := fake.New()
	for _, key := range append([]store.Key{parentKey}, markKeys...) {
		if _, err := s.Create(ctx, key, store.Record{}); err != nil {
			t.Fatalf("Create(%q) failed: %v", key, err)
		}
	}
	return s
}

// ensureStep is a reconciler that follows the contract. Its row creates three
// children, then, on a later run, sets done on the parent (children before
// the parent). Its bookkeeping marks every unmarked record.
func ensureStep(ctx context.Context, s store.Store) ([]apply.Decision, error) {
	var d apply.Decision
	parent, version, err := s.Get(ctx, parentKey)
	if err != nil {
		return nil, err
	}
	children, err := s.List(ctx, parentKey, "children")
	if err != nil {
		return nil, err
	}
	exists := map[store.Key]bool{}
	for _, c := range children {
		exists[c.Key] = true
	}
	for n := range 3 {
		if !exists[childKey(n)] {
			d.Row = append(d.Row, apply.Sequence{{Op: apply.Create, Key: childKey(n), Record: store.Record{}}})
		}
	}
	if len(d.Row) == 0 && parent["done"] != true {
		d.Row = []apply.Sequence{{{Op: apply.Update, Key: parentKey, Record: store.Record{"done": true}, Expected: version}}}
	}
	marks, err := s.List(ctx, store.Key{}, "marks")
	if err != nil {
		return nil, err
	}
	for _, key := range markKeys { // List order is random; the decision must not be.
		for _, m := range marks {
			if m.Key == key && m.Record["marked"] != true {
				d.Bookkeeping = append(d.Bookkeeping, apply.Sequence{{Op: apply.Update, Key: key, Record: store.Record{"marked": true}, Expected: m.Version}})
			}
		}
	}
	return []apply.Decision{d}, nil
}

// doStep is a reconciler that breaks the contract: what it writes to "b"
// depends on whether the run that created "a" also got to create "b".
func doStep(ctx context.Context, s store.Store) ([]apply.Decision, error) {
	a, b := store.NewKey("things", "a"), store.NewKey("things", "b")
	_, _, errA := s.Get(ctx, a)
	_, _, errB := s.Get(ctx, b)
	switch {
	case errors.Is(errA, store.ErrNotFound):
		return []apply.Decision{{Row: []apply.Sequence{{
			{Op: apply.Create, Key: a, Record: store.Record{}},
			{Op: apply.Create, Key: b, Record: store.Record{"by": "first run"}},
		}}}}, nil
	case errors.Is(errB, store.ErrNotFound):
		return []apply.Decision{{Row: []apply.Sequence{{
			{Op: apply.Create, Key: b, Record: store.Record{"by": "repair"}},
		}}}}, nil
	}
	return nil, errors.Join(errA, errB)
}

// endlessStep is a reconciler that never reaches a fixpoint: every run
// creates one more record.
func endlessStep(ctx context.Context, s store.Store) ([]apply.Decision, error) {
	things, err := s.List(ctx, store.Key{}, "things")
	if err != nil {
		return nil, err
	}
	key := store.NewKey("things", fmt.Sprint(len(things)))
	return []apply.Decision{{Row: []apply.Sequence{{{Op: apply.Create, Key: key, Record: store.Record{}}}}}}, nil
}

// recordingTB records failures instead of failing the test, so that a test
// can check that the harness reports a broken reconciler.
type recordingTB struct {
	testing.TB
	failures []string
}

func (r *recordingTB) Helper() {}

func (r *recordingTB) Errorf(format string, args ...any) {
	r.failures = append(r.failures, fmt.Sprintf(format, args...))
}

func (r *recordingTB) Fatalf(format string, args ...any) {
	r.Errorf(format, args...)
	runtime.Goexit()
}

// failuresOf runs f with a recordingTB, in a goroutine of its own because
// Fatalf ends the calling goroutine, and returns the failures f reported.
func failuresOf(t *testing.T, f func(tb testing.TB)) []string {
	t.Helper()
	rec := &recordingTB{TB: t}
	done := make(chan struct{})
	go func() {
		defer close(done)
		f(rec)
	}()
	<-done
	return rec.failures
}

func TestFailAfter(t *testing.T) {
	ctx := context.Background()
	s := fake.New()
	f := FailAfter(s, 2)
	key := store.NewKey("things", "a")

	v, err := f.Create(ctx, key, store.Record{})
	if err != nil {
		t.Fatalf("first write: Create() failed: %v", err)
	}
	if v, err = f.Update(ctx, key, store.Record{"n": int64(1)}, v); err != nil {
		t.Fatalf("second write: Update() failed: %v", err)
	}
	if _, err := f.Create(ctx, store.NewKey("things", "b"), store.Record{}); !errors.Is(err, ErrInjected) {
		t.Errorf("third write: Create() error = %v, want %v", err, ErrInjected)
	}
	if _, err := f.Update(ctx, key, store.Record{"n": int64(2)}, v); !errors.Is(err, ErrInjected) {
		t.Errorf("fourth write: Update() error = %v, want %v", err, ErrInjected)
	}
	if err := f.Delete(ctx, key, v); !errors.Is(err, ErrInjected) {
		t.Errorf("fifth write: Delete() error = %v, want %v", err, ErrInjected)
	}

	if f.Passed() != 2 || f.Refused() != 3 {
		t.Errorf("Passed() = %d, Refused() = %d, want 2 and 3", f.Passed(), f.Refused())
	}
	if got := len(s.All()); got != 1 {
		t.Errorf("store holds %d records after the refused writes, want 1", got)
	}
}

func TestFailAfterNegative(t *testing.T) {
	ctx := context.Background()
	s := fake.New()
	f := FailAfter(s, -1)

	for _, id := range []string{"a", "b"} {
		if _, err := f.Create(ctx, store.NewKey("things", id), store.Record{}); !errors.Is(err, ErrInjected) {
			t.Errorf("Create(%q) error = %v, want %v", id, err, ErrInjected)
		}
	}

	if f.Passed() != 0 || f.Refused() != 2 {
		t.Errorf("Passed() = %d, Refused() = %d, want 0 and 2", f.Passed(), f.Refused())
	}
	if got := len(s.All()); got != 0 {
		t.Errorf("store holds %d records after the refused writes, want 0", got)
	}
}

func TestRunToFixpoint(t *testing.T) {
	ctx := context.Background()
	s := seedStore(t)

	got := RunToFixpoint(ctx, t, s, ensureStep, 3)

	// Three children and two marks in the first run, the parent in the second.
	if want := 6; got != want {
		t.Errorf("RunToFixpoint() = %d writes, want %d", got, want)
	}
	parent, _, err := s.Get(ctx, parentKey)
	if err != nil {
		t.Fatalf("Get(%q) failed: %v", parentKey, err)
	}
	if parent["done"] != true {
		t.Errorf("parent at the fixpoint = %v, want done", parent)
	}
}

func TestRunToFixpointReportsNoFixpoint(t *testing.T) {
	tests := []struct {
		name    string
		step    Step
		maxRuns int
		want    string
	}{
		{name: "never converges", step: endlessStep, maxRuns: 5, want: "no fixpoint within 5 runs"},
		{name: "needs more runs than allowed", step: ensureStep, maxRuns: 1, want: "no fixpoint within 1 runs"},
		{
			name: "step fails",
			step: func(context.Context, store.Store) ([]apply.Decision, error) {
				return nil, errors.New("cannot read")
			},
			maxRuns: 5,
			want:    "step failed: cannot read",
		},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			s := seedStore(t)

			got := failuresOf(t, func(tb testing.TB) {
				RunToFixpoint(context.Background(), tb, s, tc.step, tc.maxRuns)
			})

			if len(got) != 1 || !strings.Contains(got[0], tc.want) {
				t.Errorf("RunToFixpoint() reported %q, want one failure containing %q", got, tc.want)
			}
		})
	}
}

func TestCheckCrashes(t *testing.T) {
	CheckCrashes(context.Background(), t, func() *fake.Store { return seedStore(t) }, ensureStep, 3)
}

func TestCheckCrashesReportsDifferentFixpoint(t *testing.T) {
	got := failuresOf(t, func(tb testing.TB) {
		CheckCrashes(context.Background(), tb, fake.New, doStep, 3)
	})

	// Of the two crash points, only a crash after the first write (between
	// the creates of "a" and "b") changes the fixpoint.
	if len(got) != 1 || !strings.Contains(got[0], "crash 1 writes into 2") {
		t.Errorf("CheckCrashes() reported %q, want one failure for a crash 1 writes into 2", got)
	}
}

func TestCheckCrashesReportsWriteErrorBeforeCrash(t *testing.T) {
	// The step follows the contract until the harness starts the run that it
	// will crash after one write. In that run the step's first write carries
	// a version that the store refuses, so the write fails before the crash
	// point.
	seeds, armed := 0, false
	seed := func() *fake.Store {
		seeds++
		// Seed 1 is the uninterrupted run, seed 2 the crash after no write,
		// seed 3 the crash after one write.
		armed = seeds == 3
		return seedStore(t)
	}
	step := func(ctx context.Context, s store.Store) ([]apply.Decision, error) {
		decisions, err := ensureStep(ctx, s)
		if err != nil || !armed {
			return decisions, err
		}
		armed = false
		stale := apply.Sequence{{Op: apply.Update, Key: parentKey, Record: store.Record{}, Expected: store.NewVersion("stale")}}
		decisions[0].Row = append([]apply.Sequence{stale}, decisions[0].Row...)
		return decisions, nil
	}

	got := failuresOf(t, func(tb testing.TB) {
		CheckCrashes(context.Background(), tb, seed, step, 3)
	})

	if len(got) != 1 || !strings.Contains(got[0], "a write failed before the crash point") || !strings.Contains(got[0], store.ErrVersionMismatch.Error()) {
		t.Errorf("CheckCrashes() reported %q, want one failure for a write that failed before the crash point with %v", got, store.ErrVersionMismatch)
	}
}
