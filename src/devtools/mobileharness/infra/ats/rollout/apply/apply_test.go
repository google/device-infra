package apply

import (
	"context"
	"errors"
	"testing"
	"time"

	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/store/fake"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/store"
	"github.com/google/go-cmp/cmp"
)

var errWrite = errors.New("write failed")

// key returns a test key with the given ID.
func key(id string) store.Key {
	return store.NewKey("things", id)
}

// create returns a Create of an empty record at key(id).
func create(id string) Write {
	return Write{Op: Create, Key: key(id), Record: store.Record{}}
}

// recorder is a Writer that records the IDs it is asked to write, in order,
// and fails the writes named in fail. A write named in block does not return
// until its context is done.
type recorder struct {
	fail  map[string]error
	block map[string]bool
	ids   []string
}

func (r *recorder) do(ctx context.Context, k store.Key) error {
	r.ids = append(r.ids, k.ID())
	if r.block[k.ID()] {
		<-ctx.Done()
		return ctx.Err()
	}
	return r.fail[k.ID()]
}

func (r *recorder) Create(ctx context.Context, k store.Key, _ store.Record) (store.Version, error) {
	return store.Version{}, r.do(ctx, k)
}

func (r *recorder) Update(ctx context.Context, k store.Key, _ store.Record, _ store.Version) (store.Version, error) {
	return store.Version{}, r.do(ctx, k)
}

func (r *recorder) Delete(ctx context.Context, k store.Key, _ store.Version) error {
	return r.do(ctx, k)
}

func TestApplyOrderAndErrors(t *testing.T) {
	tests := []struct {
		name          string
		decisions     []Decision
		fail          map[string]error
		wantIDs       []string
		wantErrs      int
		wantErrTarget error
	}{
		{
			name: "no decisions",
		},
		{
			name: "row sequences round-robin across decisions, before any bookkeeping",
			decisions: []Decision{
				{Row: []Sequence{{create("a-close1")}, {create("a-close2")}}, Bookkeeping: []Sequence{{create("a-mark1")}}},
				{Row: []Sequence{{create("b-stop")}}},
			},
			wantIDs: []string{"a-close1", "b-stop", "a-close2", "a-mark1"},
		},
		{
			name: "a failed row sequence does not stop the row's other sequences",
			decisions: []Decision{
				{Row: []Sequence{{create("close1")}, {create("close2")}, {create("close3")}}},
			},
			fail:          map[string]error{"close1": store.ErrVersionMismatch},
			wantIDs:       []string{"close1", "close2", "close3"},
			wantErrs:      1,
			wantErrTarget: store.ErrVersionMismatch,
		},
		{
			name: "rows first, then bookkeeping round-robin across decisions",
			decisions: []Decision{
				{Row: []Sequence{{create("a-row1"), create("a-row2")}}, Bookkeeping: []Sequence{{create("a-mark1")}, {create("a-mark2")}, {create("a-mark3")}}},
				{Bookkeeping: []Sequence{{create("b-mark1")}}},
				{Row: []Sequence{{create("c-row1")}}, Bookkeeping: []Sequence{{create("c-mark1")}, {create("c-mark2")}}},
			},
			wantIDs: []string{"a-row1", "a-row2", "c-row1", "a-mark1", "b-mark1", "c-mark1", "a-mark2", "c-mark2", "a-mark3"},
		},
		{
			name: "an error stops its own sequence only",
			decisions: []Decision{
				{Row: []Sequence{{create("a-row1"), create("a-row2")}}, Bookkeeping: []Sequence{{create("a-mark1")}}},
				{Row: []Sequence{{create("b-row1")}}},
			},
			fail:          map[string]error{"a-row1": errWrite},
			wantIDs:       []string{"a-row1", "b-row1", "a-mark1"},
			wantErrs:      1,
			wantErrTarget: errWrite,
		},
		{
			name: "a failed mark does not stop the next mark",
			decisions: []Decision{
				{Bookkeeping: []Sequence{{create("mark1")}, {create("mark2")}}},
			},
			fail:          map[string]error{"mark1": store.ErrVersionMismatch},
			wantIDs:       []string{"mark1", "mark2"},
			wantErrs:      1,
			wantErrTarget: store.ErrVersionMismatch,
		},
		{
			name: "a create that finds the record counts as done",
			decisions: []Decision{
				{Row: []Sequence{{create("row1"), create("row2")}}},
			},
			fail:    map[string]error{"row1": store.ErrAlreadyExists},
			wantIDs: []string{"row1", "row2"},
		},
		{
			name: "an update that finds the record is an error",
			decisions: []Decision{
				{Row: []Sequence{{{Op: Update, Key: key("row1")}, create("row2")}}},
			},
			fail:          map[string]error{"row1": store.ErrAlreadyExists},
			wantIDs:       []string{"row1"},
			wantErrs:      1,
			wantErrTarget: store.ErrAlreadyExists,
		},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			rec := &recorder{fail: tc.fail}

			got := Apply(context.Background(), rec, tc.decisions, Options{})

			if diff := cmp.Diff(tc.wantIDs, rec.ids); diff != "" {
				t.Errorf("Apply() wrote in an unexpected order (-want +got):\n%s", diff)
			}
			if got.Attempted != len(tc.wantIDs) {
				t.Errorf("Apply() Attempted = %d, want %d", got.Attempted, len(tc.wantIDs))
			}
			if got.Exhausted {
				t.Errorf("Apply() Exhausted = true, want false")
			}
			if len(got.Errs) != tc.wantErrs {
				t.Fatalf("Apply() returned %d errors %v, want %d", len(got.Errs), got.Errs, tc.wantErrs)
			}
			if tc.wantErrs > 0 && !errors.Is(got.Errs[0], tc.wantErrTarget) {
				t.Errorf("Apply() error = %v, want it to wrap %v", got.Errs[0], tc.wantErrTarget)
			}
		})
	}
}

func TestApplyUnknownOp(t *testing.T) {
	rec := &recorder{}
	decisions := []Decision{
		{Row: []Sequence{{{Key: key("bad")}, create("after-bad")}}},
		{Row: []Sequence{{create("other")}}},
	}

	got := Apply(context.Background(), rec, decisions, Options{})

	if diff := cmp.Diff([]string{"other"}, rec.ids); diff != "" {
		t.Errorf("Apply() wrote unexpected records (-want +got):\n%s", diff)
	}
	if got.Attempted != 1 || len(got.Errs) != 1 {
		t.Errorf("Apply() = %+v, want 1 write attempted and 1 error", got)
	}
}

func TestApplyStopsAtDeadline(t *testing.T) {
	tests := []struct {
		name string
		ctx  func() (context.Context, context.CancelFunc)
		opts Options
	}{
		{
			name: "deadline passed",
			ctx:  func() (context.Context, context.CancelFunc) { return context.WithCancel(context.Background()) },
			opts: Options{Deadline: time.Now().Add(-time.Second)},
		},
		{
			name: "context cancelled",
			ctx: func() (context.Context, context.CancelFunc) {
				ctx, cancel := context.WithCancel(context.Background())
				cancel()
				return ctx, cancel
			},
		},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			ctx, cancel := tc.ctx()
			defer cancel()
			rec := &recorder{}
			decisions := []Decision{{Row: []Sequence{{create("row")}}, Bookkeeping: []Sequence{{create("mark")}}}}

			got := Apply(ctx, rec, decisions, tc.opts)

			if len(rec.ids) != 0 {
				t.Errorf("Apply() wrote %v, want no writes", rec.ids)
			}
			if got.Attempted != 0 || !got.Exhausted || len(got.Errs) != 0 {
				t.Errorf("Apply() = %+v, want no write attempted, Exhausted, and no error", got)
			}
		})
	}
}

func TestApplyDeadlineCancelsWriteInProgress(t *testing.T) {
	rec := &recorder{block: map[string]bool{"slow": true}}
	decisions := []Decision{
		{Row: []Sequence{{create("slow"), create("after-slow")}}},
		{Row: []Sequence{{create("other")}}},
	}

	got := Apply(context.Background(), rec, decisions, Options{Deadline: time.Now().Add(20 * time.Millisecond)})

	if diff := cmp.Diff([]string{"slow"}, rec.ids); diff != "" {
		t.Errorf("Apply() wrote unexpected records (-want +got):\n%s", diff)
	}
	if got.Attempted != 1 || !got.Exhausted {
		t.Errorf("Apply() = %+v, want 1 write attempted and Exhausted", got)
	}
	if len(got.Errs) != 1 || !errors.Is(got.Errs[0], context.DeadlineExceeded) {
		t.Errorf("Apply() errors = %v, want one wrapping %v", got.Errs, context.DeadlineExceeded)
	}
}

func TestApplyWriteTimeout(t *testing.T) {
	rec := &recorder{block: map[string]bool{"slow": true}}
	decisions := []Decision{
		{Row: []Sequence{{create("slow"), create("after-slow")}}},
		{Row: []Sequence{{create("other")}}},
	}

	got := Apply(context.Background(), rec, decisions, Options{WriteTimeout: 20 * time.Millisecond})

	// The slow write times out on its own; the run goes on.
	if diff := cmp.Diff([]string{"slow", "other"}, rec.ids); diff != "" {
		t.Errorf("Apply() wrote unexpected records (-want +got):\n%s", diff)
	}
	if got.Attempted != 2 || got.Exhausted {
		t.Errorf("Apply() = %+v, want 2 writes attempted and not Exhausted", got)
	}
	if len(got.Errs) != 1 || !errors.Is(got.Errs[0], context.DeadlineExceeded) {
		t.Errorf("Apply() errors = %v, want one wrapping %v", got.Errs, context.DeadlineExceeded)
	}
}

// TestApplyOnStore checks each operation against a real store: the write
// lands when the version matches, and is refused when it does not.
func TestApplyOnStore(t *testing.T) {
	ctx := context.Background()
	s := fake.New()
	seed := func(id string) store.Version {
		t.Helper()
		v, err := s.Create(ctx, key(id), store.Record{"n": int64(1)})
		if err != nil {
			t.Fatalf("Create(%q) failed: %v", key(id), err)
		}
		return v
	}
	updated, deleted, existing := seed("updated"), seed("deleted"), seed("existing")
	stale := seed("stale")
	if _, err := s.Update(ctx, key("stale"), store.Record{"n": int64(2)}, stale); err != nil {
		t.Fatalf("Update(%q) failed: %v", key("stale"), err)
	}
	decisions := []Decision{
		{Row: []Sequence{{
			{Op: Create, Key: key("created"), Record: store.Record{"n": int64(10)}},
			{Op: Create, Key: key("existing"), Record: store.Record{"n": int64(11)}},
			{Op: Update, Key: key("updated"), Record: store.Record{"n": int64(12)}, Expected: updated},
			{Op: Delete, Key: key("deleted"), Expected: deleted},
			{Op: Delete, Key: key("missing"), Expected: existing},
		}}},
		{Row: []Sequence{{
			{Op: Update, Key: key("stale"), Record: store.Record{"n": int64(13)}, Expected: stale},
			{Op: Create, Key: key("after-stale"), Record: store.Record{}},
		}}},
		{Row: []Sequence{{{Op: Delete, Key: key("stale"), Expected: stale}}}},
	}

	got := Apply(ctx, s, decisions, Options{WriteTimeout: time.Minute, Deadline: time.Now().Add(time.Minute)})

	if got.Attempted != 7 || got.Exhausted {
		t.Errorf("Apply() = %+v, want 7 writes attempted and not Exhausted", got)
	}
	if len(got.Errs) != 2 || !errors.Is(got.Errs[0], store.ErrVersionMismatch) || !errors.Is(got.Errs[1], store.ErrVersionMismatch) {
		t.Errorf("Apply() errors = %v, want two wrapping %v", got.Errs, store.ErrVersionMismatch)
	}
	stored := map[string]int64{}
	for _, it := range s.All() {
		stored[it.Key.ID()], _ = it.Record["n"].(int64)
	}
	want := map[string]int64{"created": 10, "existing": 1, "updated": 12, "stale": 2}
	if diff := cmp.Diff(want, stored); diff != "" {
		t.Errorf("store content after Apply() (-want +got):\n%s", diff)
	}
}
