// Package storetest checks that an implementation of store.Store satisfies
// the contract documented in package store.
package storetest

import (
	"context"
	"errors"
	"slices"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/store"
	"github.com/google/go-cmp/cmp"
)

// Run runs the contract suite as subtests of t. newStore must return an
// empty store on every call.
func Run(t *testing.T, newStore func(t *testing.T) store.Store) {
	tests := []struct {
		name string
		test func(t *testing.T, s store.Store)
	}{
		{"GetMissing", testGetMissing},
		{"CreateThenGet", testCreateThenGet},
		{"CreateExisting", testCreateExisting},
		{"Update", testUpdate},
		{"UpdateNoOp", testUpdateNoOp},
		{"UpdateMissing", testUpdateMissing},
		{"UpdateAfterRecreate", testUpdateAfterRecreate},
		{"DeleteConditional", testDeleteConditional},
		{"DeleteMissing", testDeleteMissing},
		{"ListDirectChildren", testListDirectChildren},
		{"QueryFilter", testQueryFilter},
		{"InvalidArguments", testInvalidArguments},
		{"DoneContext", testDoneContext},
		{"ReturnedRecordsAreCopies", testReturnedRecordsAreCopies},
		{"ConcurrentWritesOneWinner", testConcurrentWritesOneWinner},
		{"ConcurrentReadWrite", testConcurrentReadWrite},
		{"SnapshotIsolation", testSnapshotIsolation},
		{"SnapshotInvalidReadDoesNotPin", testSnapshotInvalidReadDoesNotPin},
		{"SnapshotVersions", testSnapshotVersions},
		{"SnapshotClose", testSnapshotClose},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			tc.test(t, newStore(t))
		})
	}
}

var (
	domain = store.NewKey("domains", "d1")
	groupA = domain.Child("groups", "a")
	groupB = domain.Child("groups", "b")
)

func testGetMissing(t *testing.T, s store.Store) {
	if _, _, err := s.Get(t.Context(), groupA); !errors.Is(err, store.ErrNotFound) {
		t.Errorf("Get(%q) on an empty store returned error %v, want %v", groupA, err, store.ErrNotFound)
	}
}

func testCreateThenGet(t *testing.T, s store.Store) {
	at := time.Date(2026, 11, 5, 19, 0, 0, 123456789, time.FixedZone("CST", 8*60*60))
	r := store.Record{
		"s":     "value",
		"b":     true,
		"i":     int64(-42),
		"min":   int64(-1 << 63),
		"max":   int64(1<<63 - 1),
		"at":    at,
		"list":  []any{"x", int64(1), map[string]any{"inner": []any{false}}},
		"map":   map[string]any{"host.example.com": "DONE", "at": at, "nested": map[string]any{"k": "v"}},
		"empty": map[string]any{},
		"none":  []any{},
	}
	if _, err := s.Create(t.Context(), groupA, r); err != nil {
		t.Fatalf("Create(%q) failed: %v", groupA, err)
	}

	got, _, err := s.Get(t.Context(), groupA)
	if err != nil {
		t.Fatalf("Get(%q) failed: %v", groupA, err)
	}
	wantAt := time.Date(2026, 11, 5, 11, 0, 0, 123456000, time.UTC)
	want := store.Record{
		"s":     "value",
		"b":     true,
		"i":     int64(-42),
		"min":   int64(-1 << 63),
		"max":   int64(1<<63 - 1),
		"at":    wantAt,
		"list":  []any{"x", int64(1), map[string]any{"inner": []any{false}}},
		"map":   map[string]any{"host.example.com": "DONE", "at": wantAt, "nested": map[string]any{"k": "v"}},
		"empty": map[string]any{},
		"none":  []any{},
	}
	if diff := cmp.Diff(want, got); diff != "" {
		t.Errorf("Get(%q) after Create returned diff (-want +got):\n%s", groupA, diff)
	}
	if gotAt, ok := got["at"].(time.Time); !ok || gotAt.Location() != time.UTC {
		t.Errorf("Get(%q) field \"at\" = %v, want a time.Time in UTC", groupA, got["at"])
	}

	mustCreate(t, s, groupB, store.Record{})
	if got, _ := mustGet(t, s, groupB); got == nil {
		t.Errorf("Get(%q) of an empty record = nil, want a non-nil empty Record", groupB)
	}
}

func testCreateExisting(t *testing.T, s store.Store) {
	mustCreate(t, s, groupA, store.Record{"n": int64(1)})

	if _, err := s.Create(t.Context(), groupA, store.Record{"n": int64(2)}); !errors.Is(err, store.ErrAlreadyExists) {
		t.Errorf("Create(%q) on an existing record returned error %v, want %v", groupA, err, store.ErrAlreadyExists)
	}
	if got, _ := mustGet(t, s, groupA); got["n"] != int64(1) {
		t.Errorf("Get(%q) after a failed Create = %v, want the original record", groupA, got)
	}
}

func testUpdate(t *testing.T, s store.Store) {
	v1 := mustCreate(t, s, groupA, store.Record{"n": int64(1)})

	v2, err := s.Update(t.Context(), groupA, store.Record{"m": int64(2)}, v1)
	if err != nil {
		t.Fatalf("Update(%q) with the current version failed: %v", groupA, err)
	}
	if got, _ := mustGet(t, s, groupA); !cmp.Equal(got, store.Record{"m": int64(2)}) {
		t.Errorf("Get(%q) after Update = %v, want the whole record replaced", groupA, got)
	}
	if _, err := s.Update(t.Context(), groupA, store.Record{"m": int64(3)}, v1); !errors.Is(err, store.ErrVersionMismatch) {
		t.Errorf("Update(%q) with a superseded version returned error %v, want %v", groupA, err, store.ErrVersionMismatch)
	}
	if _, err := s.Update(t.Context(), groupA, store.Record{"m": int64(3)}, v2); err != nil {
		t.Errorf("Update(%q) with the version returned by the last Update failed: %v", groupA, err)
	}
}

func testUpdateMissing(t *testing.T, s store.Store) {
	v := mustCreate(t, s, groupA, store.Record{})

	if _, err := s.Update(t.Context(), groupB, store.Record{}, v); !errors.Is(err, store.ErrVersionMismatch) {
		t.Errorf("Update(%q) on a missing record returned error %v, want %v", groupB, err, store.ErrVersionMismatch)
	}
	if _, _, err := s.Get(t.Context(), groupB); !errors.Is(err, store.ErrNotFound) {
		t.Errorf("Get(%q) after a failed Update returned error %v, want %v", groupB, err, store.ErrNotFound)
	}
}

func testUpdateAfterRecreate(t *testing.T, s store.Store) {
	old := mustCreate(t, s, groupA, store.Record{"n": int64(1)})
	if err := s.Delete(t.Context(), groupA, old); err != nil {
		t.Fatalf("Delete(%q) failed: %v", groupA, err)
	}
	mustCreate(t, s, groupA, store.Record{"n": int64(1)})

	if _, err := s.Update(t.Context(), groupA, store.Record{"n": int64(2)}, old); !errors.Is(err, store.ErrVersionMismatch) {
		t.Errorf("Update(%q) with a version from before Delete and Create returned error %v, want %v", groupA, err, store.ErrVersionMismatch)
	}
	if err := s.Delete(t.Context(), groupA, old); !errors.Is(err, store.ErrVersionMismatch) {
		t.Errorf("Delete(%q) with a version from before Delete and Create returned error %v, want %v", groupA, err, store.ErrVersionMismatch)
	}
	mustGet(t, s, groupA)
}

func testDeleteConditional(t *testing.T, s store.Store) {
	v1 := mustCreate(t, s, groupA, store.Record{"n": int64(1)})
	v2 := mustUpdate(t, s, groupA, store.Record{"n": int64(2)}, v1)

	if err := s.Delete(t.Context(), groupA, v1); !errors.Is(err, store.ErrVersionMismatch) {
		t.Errorf("Delete(%q) with a superseded version returned error %v, want %v", groupA, err, store.ErrVersionMismatch)
	}
	mustGet(t, s, groupA)
	if err := s.Delete(t.Context(), groupA, v2); err != nil {
		t.Errorf("Delete(%q) with the current version failed: %v", groupA, err)
	}
	if _, _, err := s.Get(t.Context(), groupA); !errors.Is(err, store.ErrNotFound) {
		t.Errorf("Get(%q) after Delete returned error %v, want %v", groupA, err, store.ErrNotFound)
	}
}

func testDeleteMissing(t *testing.T, s store.Store) {
	v := mustCreate(t, s, groupA, store.Record{})

	if err := s.Delete(t.Context(), groupB, v); err != nil {
		t.Errorf("Delete(%q) on a missing record returned error %v, want nil", groupB, err)
	}
}

func testListDirectChildren(t *testing.T, s store.Store) {
	other := store.NewKey("domains", "d2") // Parent record that does not exist.
	for _, k := range []store.Key{
		domain,
		groupA,
		groupB,
		groupA.Child("members", "m1"), // Grandchild of domain.
		domain.Child("others", "x"),   // Other kind.
		other.Child("groups", "c"),    // Other parent.
	} {
		mustCreate(t, s, k, store.Record{"id": k.ID()})
	}

	tests := []struct {
		parent store.Key
		kind   string
		want   []string
	}{
		{parent: domain, kind: "groups", want: []string{"domains/d1/groups/a", "domains/d1/groups/b"}},
		{parent: store.Key{}, kind: "domains", want: []string{"domains/d1"}},
		{parent: other, kind: "groups", want: []string{"domains/d2/groups/c"}},
		{parent: groupB, kind: "members", want: nil},
	}
	for _, tc := range tests {
		items, err := s.List(t.Context(), tc.parent, tc.kind)
		if err != nil {
			t.Errorf("List(%q, %q) failed: %v", tc.parent, tc.kind, err)
			continue
		}
		if diff := cmp.Diff(tc.want, itemKeys(items)); diff != "" {
			t.Errorf("List(%q, %q) returned keys diff (-want +got):\n%s", tc.parent, tc.kind, diff)
		}
		for _, it := range items {
			if it.Record["id"] != it.Key.ID() {
				t.Errorf("List(%q, %q) returned record %v for key %q, want the record stored there", tc.parent, tc.kind, it.Record, it.Key)
			}
		}
	}
}

func testQueryFilter(t *testing.T, s store.Store) {
	records := map[string]store.Record{
		"match":      {"agent_id": "h1", "n": int64(1)},
		"other":      {"agent_id": "h2"},
		"no_field":   {"name": "h1"},
		"int":        {"agent_id": int64(1)},
		"list":       {"agent_id": []any{"h1"}},
		"nested":     {"m": map[string]any{"agent_id": "h1"}},
		"also_match": {"agent_id": "h1"},
	}
	for id, r := range records {
		mustCreate(t, s, groupA.Child("slots", id), r)
	}
	mustCreate(t, s, groupA.Child("slots", "match").Child("slots", "deep"), store.Record{"agent_id": "h1"})

	eq := store.Eq{Field: "agent_id", Value: "h1"}
	items, err := s.Query(t.Context(), groupA, "slots", eq)
	if err != nil {
		t.Fatalf("Query(%q, slots, %v) failed: %v", groupA, eq, err)
	}
	want := []string{"domains/d1/groups/a/slots/also_match", "domains/d1/groups/a/slots/match"}
	if diff := cmp.Diff(want, itemKeys(items)); diff != "" {
		t.Errorf("Query(%q, slots, %v) returned keys diff (-want +got):\n%s", groupA, eq, diff)
	}
	eq = store.Eq{Field: "agent_id", Value: "1"}
	if items, err := s.Query(t.Context(), groupA, "slots", eq); err != nil || len(items) != 0 {
		t.Errorf("Query(%q, slots, %v) = %v, %v, want no items: an int64 never equals a string", groupA, eq, itemKeys(items), err)
	}
}

func testInvalidArguments(t *testing.T, s store.Store) {
	ctx := t.Context()
	v := mustCreate(t, s, groupB, store.Record{})
	longName := strings.Repeat("a", 1501)
	badKeys := map[string]store.Key{
		"root":        {},
		"empty_id":    store.NewKey("domains", ""),
		"slash_id":    store.NewKey("domains", "a/b"),
		"dot_id":      store.NewKey("domains", ".."),
		"reserved_id": store.NewKey("domains", "__x__"),
		"empty_kind":  store.NewKey("", "d1"),
	}
	badRecords := map[string]store.Record{
		"int":          {"n": 1},
		"float":        {"n": 1.5},
		"nil":          {"n": nil},
		"bad_utf8":     {"s": "\xff"},
		"empty_field":  {"": "x"},
		"nested_bad":   {"m": map[string]any{"__k__": "x"}},
		"list_bad":     {"l": []any{int32(1)}},
		"nested_list":  {"l": []any{[]any{"x"}}},
		"year_0":       {"t": time.Date(0, 12, 31, 0, 0, 0, 0, time.UTC)},
		"year_10000":   {"t": time.Date(10000, 1, 1, 0, 0, 0, 0, time.UTC)},
		"year_1_local": {"t": time.Date(1, 1, 1, 0, 0, 0, 0, time.FixedZone("E", 3600))},
	}
	badQueries := map[string]store.Eq{
		"empty_field":    {Value: "a"},
		"dotted_field":   {Field: "a.b", Value: "a"},
		"upper_field":    {Field: "State", Value: "a"},
		"reserved_field": {Field: "__name__", Value: "a"},
		"long_field":     {Field: longName, Value: "a"},
		"empty_value":    {Field: "state"},
		"bad_utf8_value": {Field: "state", Value: "\xff"},
	}

	bad := map[string]func() error{
		"delete_zero":        func() error { return s.Delete(ctx, groupB, store.Version{}) },
		"delete_zero_absent": func() error { return s.Delete(ctx, groupA, store.Version{}) },
		"update_zero":        func() error { _, err := s.Update(ctx, groupB, store.Record{}, store.Version{}); return err },
		"list_empty_kind":    func() error { _, err := s.List(ctx, domain, ""); return err },
		"list_bad_parent":    func() error { _, err := s.List(ctx, store.NewKey("domains", ""), "groups"); return err },
	}
	for name, key := range badKeys {
		bad["get_"+name] = func() error { _, _, err := s.Get(ctx, key); return err }
		bad["delete_"+name] = func() error { return s.Delete(ctx, key, v) }
		bad["update_"+name] = func() error { _, err := s.Update(ctx, key, store.Record{}, v); return err }
	}
	for name, r := range badRecords {
		bad["create_"+name] = func() error { _, err := s.Create(ctx, groupA, r); return err }
		bad["update_"+name] = func() error { _, err := s.Update(ctx, groupB, r, v); return err }
	}
	for name, eq := range badQueries {
		bad["query_"+name] = func() error { _, err := s.Query(ctx, domain, "groups", eq); return err }
	}
	for name, call := range bad {
		if err := call(); !errors.Is(err, store.ErrInvalidArgument) {
			t.Errorf("%s: returned error %v, want %v", name, err, store.ErrInvalidArgument)
		}
	}
	if _, _, err := s.Get(ctx, groupA); !errors.Is(err, store.ErrNotFound) {
		t.Errorf("Get(%q) after invalid Creates returned error %v, want %v", groupA, err, store.ErrNotFound)
	}
	if got, gotV := mustGet(t, s, groupB); len(got) != 0 || gotV != v {
		t.Errorf("Get(%q) after invalid Updates = %v, %v, want the empty record at version %v", groupB, got, gotV, v)
	}
}

func testUpdateNoOp(t *testing.T, s store.Store) {
	r := store.Record{"n": int64(1), "at": time.Date(2026, 11, 9, 0, 0, 0, 5, time.UTC)}
	v1 := mustCreate(t, s, groupA, r.Canonical())

	// Writing a record equal to the stored one: the backend may keep the
	// version or assign a new one. Either way the returned version is current.
	v2, err := s.Update(t.Context(), groupA, r.Canonical(), v1)
	if err != nil {
		t.Fatalf("no-op Update(%q) failed: %v", groupA, err)
	}
	if _, err := s.Update(t.Context(), groupA, store.Record{"n": int64(2)}, v2); err != nil {
		t.Errorf("Update(%q) with the version returned by a no-op Update failed: %v", groupA, err)
	}
}

func testDoneContext(t *testing.T, s store.Store) {
	v := mustCreate(t, s, groupA, store.Record{})
	ctx, cancel := context.WithCancel(t.Context())
	cancel()

	calls := map[string]func() error{
		"Get":      func() error { _, _, err := s.Get(ctx, groupA); return err },
		"List":     func() error { _, err := s.List(ctx, domain, "groups"); return err },
		"Query":    func() error { _, err := s.Query(ctx, domain, "groups", store.Eq{Field: "s", Value: "x"}); return err },
		"Snapshot": func() error { _, err := s.Snapshot(ctx); return err },
		"Create":   func() error { _, err := s.Create(ctx, groupB, store.Record{}); return err },
		"Update":   func() error { _, err := s.Update(ctx, groupA, store.Record{"n": int64(1)}, v); return err },
		"Delete":   func() error { return s.Delete(ctx, groupA, v) },
	}
	for name, call := range calls {
		if err := call(); !errors.Is(err, context.Canceled) {
			t.Errorf("%s with a canceled context returned error %v, want %v", name, err, context.Canceled)
		}
	}
	// Invalid arguments are reported before the context is checked.
	if _, _, err := s.Get(ctx, store.Key{}); !errors.Is(err, store.ErrInvalidArgument) {
		t.Errorf("Get(root) with a canceled context returned error %v, want %v", err, store.ErrInvalidArgument)
	}
	if got, gotV := mustGet(t, s, groupA); len(got) != 0 || gotV != v {
		t.Errorf("Get(%q) after canceled writes = %v, %v, want the empty record at version %v", groupA, got, gotV, v)
	}
	if _, _, err := s.Get(t.Context(), groupB); !errors.Is(err, store.ErrNotFound) {
		t.Errorf("Get(%q) after a canceled Create returned error %v, want %v", groupB, err, store.ErrNotFound)
	}

	snap, err := s.Snapshot(t.Context())
	if err != nil {
		t.Fatalf("Snapshot() failed: %v", err)
	}
	if _, _, err := snap.Get(t.Context(), groupA); err != nil {
		t.Fatalf("snapshot Get(%q) failed: %v", groupA, err)
	}
	if _, _, err := snap.Get(ctx, groupA); !errors.Is(err, context.Canceled) {
		t.Errorf("snapshot Get with a canceled context returned error %v, want %v", err, context.Canceled)
	}
	// Close works on an active snapshot even when the context is done.
	if err := snap.Close(ctx); err != nil {
		t.Errorf("snapshot Close with a canceled context returned error %v, want nil", err)
	}
	if _, _, err := snap.Get(t.Context(), groupA); !errors.Is(err, store.ErrClosed) {
		t.Errorf("snapshot Get after Close returned error %v, want %v", err, store.ErrClosed)
	}
}

func testReturnedRecordsAreCopies(t *testing.T, s store.Store) {
	want := store.Record{"m": map[string]any{"k": "v"}, "l": []any{"x"}, "state": "OPEN"}
	fresh := func() store.Record { return want.Canonical() }
	mutate := func(r store.Record) {
		r["m"].(map[string]any)["k"] = "changed"
		r["l"].([]any)[0] = "changed"
		r["state"] = "changed"
	}

	v := mustCreate(t, s, groupA, fresh())
	in := fresh()
	mustUpdate(t, s, groupA, in, v)
	mutate(in)

	got, _ := mustGet(t, s, groupA)
	mutate(got)
	items, err := s.List(t.Context(), domain, "groups")
	if err != nil || len(items) != 1 {
		t.Fatalf("List(%q, groups) = %v, %v, want one item", domain, itemKeys(items), err)
	}
	mutate(items[0].Record)
	items, err = s.Query(t.Context(), domain, "groups", store.Eq{Field: "state", Value: "OPEN"})
	if err != nil || len(items) != 1 {
		t.Fatalf("Query(%q, groups, state=OPEN) = %v, %v, want one item", domain, itemKeys(items), err)
	}
	mutate(items[0].Record)

	if got, _ := mustGet(t, s, groupA); !cmp.Equal(got, want) {
		t.Errorf("Get(%q) after modifying earlier records = %v, want %v", groupA, got, want)
	}
}

func testConcurrentWritesOneWinner(t *testing.T, s store.Store) {
	const n = 16
	race := func(name string, write func() error) {
		var wg sync.WaitGroup
		errs := make([]error, n)
		for i := range n {
			wg.Go(func() { errs[i] = write() })
		}
		wg.Wait()
		wins := 0
		for i, err := range errs {
			if err == nil {
				wins++
			} else if !errors.Is(err, store.ErrAlreadyExists) && !errors.Is(err, store.ErrVersionMismatch) {
				t.Errorf("%s: writer %d returned error %v, want nil, %v, or %v", name, i, err, store.ErrAlreadyExists, store.ErrVersionMismatch)
			}
		}
		if wins != 1 {
			t.Errorf("%s: %d writers succeeded, want exactly 1", name, wins)
		}
	}

	race("Create", func() error {
		_, err := s.Create(context.WithoutCancel(t.Context()), groupA, store.Record{})
		return err
	})
	_, v := mustGet(t, s, groupA)
	race("Update", func() error {
		_, err := s.Update(context.WithoutCancel(t.Context()), groupA, store.Record{"n": int64(1)}, v)
		return err
	})

	// Update against Delete on the same version: one wins, the other sees a
	// version mismatch, and the record's final state is the winner's.
	ctx := context.WithoutCancel(t.Context())
	for round := range 16 {
		r, v, err := s.Get(ctx, groupA)
		if errors.Is(err, store.ErrNotFound) {
			v = mustCreate(t, s, groupA, store.Record{"n": int64(round)})
			r = store.Record{"n": int64(round)}
		} else if err != nil {
			t.Fatalf("round %d: Get(%q) failed: %v", round, groupA, err)
		}
		updated := store.Record{"n": r["n"], "round": int64(round)}

		var wg sync.WaitGroup
		var updateErr, deleteErr error
		var updateV store.Version
		wg.Go(func() { updateV, updateErr = s.Update(ctx, groupA, updated, v) })
		wg.Go(func() { deleteErr = s.Delete(ctx, groupA, v) })
		wg.Wait()

		got, gotV, getErr := s.Get(ctx, groupA)
		switch {
		case updateErr == nil && errors.Is(deleteErr, store.ErrVersionMismatch):
			if getErr != nil || !cmp.Equal(got, updated) || gotV != updateV {
				t.Errorf("round %d: Update won the race, but Get(%q) = %v, %v, %v, want %v at version %v", round, groupA, got, gotV, getErr, updated, updateV)
			}
		case deleteErr == nil && errors.Is(updateErr, store.ErrVersionMismatch):
			if !errors.Is(getErr, store.ErrNotFound) {
				t.Errorf("round %d: Delete won the race, but Get(%q) = %v, %v, %v, want %v", round, groupA, got, gotV, getErr, store.ErrNotFound)
			}
		default:
			t.Errorf("round %d: racing Update and Delete returned %v and %v, want exactly one nil and one %v", round, updateErr, deleteErr, store.ErrVersionMismatch)
		}
	}
}

// testConcurrentReadWrite checks that readers never see a torn record and
// that a snapshot's reads agree with each other while the store changes.
func testConcurrentReadWrite(t *testing.T, s store.Store) {
	const steps = 25
	ctx := context.WithoutCancel(t.Context())
	consistent := func(r store.Record) bool {
		if r["step"] != r["mirror"] {
			return false
		}
		if nested, ok := r["nested"].(map[string]any); ok && nested["step"] != r["step"] {
			return false
		}
		return true
	}
	recordAt := func(step int) store.Record {
		return store.Record{"step": int64(step), "mirror": int64(step), "state": "ACTIVE", "nested": map[string]any{"step": int64(step)}}
	}
	active := store.Eq{Field: "state", Value: "ACTIVE"}

	v := mustCreate(t, s, groupA, recordAt(0))
	done := make(chan struct{})
	var wg sync.WaitGroup

	// Writer: advance groupA, and create then delete groupB, every step.
	wg.Go(func() {
		defer close(done)
		for i := 1; i <= steps; i++ {
			nv, err := s.Update(ctx, groupA, recordAt(i), v)
			if err != nil {
				t.Errorf("writer: Update(%q) at step %d failed: %v", groupA, i, err)
				return
			}
			v = nv
			bv, err := s.Create(ctx, groupB, store.Record{"step": int64(i), "mirror": int64(i), "state": "ACTIVE"})
			if err != nil {
				t.Errorf("writer: Create(%q) at step %d failed: %v", groupB, i, err)
				return
			}
			if err := s.Delete(ctx, groupB, bv); err != nil {
				t.Errorf("writer: Delete(%q) at step %d failed: %v", groupB, i, err)
				return
			}
		}
	})

	running := func() bool {
		select {
		case <-done:
			return false
		default:
			return true
		}
	}

	// Get reader. Mutating the returned records must not affect the store.
	wg.Go(func() {
		for running() {
			r, gv, err := s.Get(ctx, groupA)
			if err != nil {
				t.Errorf("reader: Get(%q) failed: %v", groupA, err)
				return
			}
			if !consistent(r) || gv.IsZero() {
				t.Errorf("reader: Get(%q) = %v at version %v, want a consistent record with a non-zero version", groupA, r, gv)
				return
			}
			r["step"] = int64(-1)
			r["nested"].(map[string]any)["step"] = int64(-1)
			if rb, _, err := s.Get(ctx, groupB); err == nil {
				if !consistent(rb) {
					t.Errorf("reader: Get(%q) = %v, want step == mirror", groupB, rb)
					return
				}
				rb["step"] = int64(-1)
			} else if !errors.Is(err, store.ErrNotFound) {
				t.Errorf("reader: Get(%q) failed: %v", groupB, err)
				return
			}
		}
	})

	// List and Query readers.
	wg.Go(func() {
		for running() {
			items, err := s.List(ctx, domain, "groups")
			if err != nil {
				t.Errorf("reader: List(%q, groups) failed: %v", domain, err)
				return
			}
			queried, err := s.Query(ctx, domain, "groups", active)
			if err != nil {
				t.Errorf("reader: Query(%q, groups, %v) failed: %v", domain, active, err)
				return
			}
			for _, it := range slices.Concat(items, queried) {
				if !consistent(it.Record) || it.Version.IsZero() {
					t.Errorf("reader: List/Query returned %q = %v at version %v, want a consistent record with a non-zero version", it.Key, it.Record, it.Version)
					return
				}
			}
		}
	})

	// Snapshot reader: every read through one snapshot agrees with the others.
	wg.Go(func() {
		for running() {
			snap, err := s.Snapshot(ctx)
			if err != nil {
				t.Errorf("reader: Snapshot() failed: %v", err)
				return
			}
			listed, err := snap.List(ctx, domain, "groups")
			if err != nil {
				t.Errorf("reader: snapshot List(%q, groups) failed: %v", domain, err)
				return
			}
			byKey := make(map[store.Key]store.Item, len(listed))
			for _, it := range listed {
				byKey[it.Key] = it
			}
			r, gv, err := snap.Get(ctx, groupA)
			if err != nil {
				t.Errorf("reader: snapshot Get(%q) failed: %v", groupA, err)
				return
			}
			if la, ok := byKey[groupA]; !ok || la.Version != gv || la.Record["step"] != r["step"] {
				t.Errorf("reader: snapshot Get(%q) = step %v at version %v, but the same snapshot's List returned %v", groupA, r["step"], gv, la)
				return
			}
			queried, err := snap.Query(ctx, domain, "groups", active)
			if err != nil {
				t.Errorf("reader: snapshot Query(%q, groups, %v) failed: %v", domain, active, err)
				return
			}
			if diff := cmp.Diff(itemKeys(listed), itemKeys(queried)); diff != "" {
				t.Errorf("reader: snapshot Query returned different keys from the same snapshot's List (-list +query):\n%s", diff)
				return
			}
			for _, it := range queried {
				if la := byKey[it.Key]; la.Version != it.Version || la.Record["step"] != it.Record["step"] {
					t.Errorf("reader: snapshot Query returned %q = step %v at version %v, but the same snapshot's List returned %v", it.Key, it.Record["step"], it.Version, la)
					return
				}
			}
			if err := snap.Close(ctx); err != nil {
				t.Errorf("reader: snapshot Close() failed: %v", err)
				return
			}
		}
	})

	wg.Wait()
	if got, _ := mustGet(t, s, groupA); !cmp.Equal(got, recordAt(steps)) {
		t.Errorf("Get(%q) after the writer finished = %v, want %v", groupA, got, recordAt(steps))
	}
}

func testSnapshotIsolation(t *testing.T, s store.Store) {
	a, b, c := groupA.Child("slots", "a"), groupA.Child("slots", "b"), groupA.Child("slots", "c")
	va := mustCreate(t, s, a, store.Record{"state": "OPEN"})

	snap, err := s.Snapshot(t.Context())
	if err != nil {
		t.Fatalf("Snapshot() failed: %v", err)
	}
	defer snap.Close(context.WithoutCancel(t.Context()))
	// The snapshot's view is fixed at its first read, not at Snapshot(): a
	// record created in between is visible.
	vb := mustCreate(t, s, b, store.Record{"state": "OPEN"})
	if _, _, err := snap.Get(t.Context(), a); err != nil {
		t.Fatalf("first snapshot Get(%q) failed: %v", a, err)
	}

	mustUpdate(t, s, a, store.Record{"state": "CLAIMED"}, va)
	if err := s.Delete(t.Context(), b, vb); err != nil {
		t.Fatalf("Delete(%q) failed: %v", b, err)
	}
	mustCreate(t, s, c, store.Record{"state": "OPEN"})

	if got, _, err := snap.Get(t.Context(), a); err != nil || got["state"] != "OPEN" {
		t.Errorf("snapshot Get(%q) = %v, %v, want the record as of the snapshot", a, got, err)
	}
	if _, _, err := snap.Get(t.Context(), c); !errors.Is(err, store.ErrNotFound) {
		t.Errorf("snapshot Get(%q) of a record created later returned error %v, want %v", c, err, store.ErrNotFound)
	}
	wantKeys := []string{a.String(), b.String()}
	if items, err := snap.List(t.Context(), groupA, "slots"); err != nil || !cmp.Equal(itemKeys(items), wantKeys) {
		t.Errorf("snapshot List(%q, slots) = %v, %v, want %v", groupA, itemKeys(items), err, wantKeys)
	}
	open := store.Eq{Field: "state", Value: "OPEN"}
	if items, err := snap.Query(t.Context(), groupA, "slots", open); err != nil || !cmp.Equal(itemKeys(items), wantKeys) {
		t.Errorf("snapshot Query(%q, slots, %v) = %v, %v, want %v", groupA, open, itemKeys(items), err, wantKeys)
	}
	wantLatest := []string{c.String()}
	if items, err := s.Query(t.Context(), groupA, "slots", open); err != nil || !cmp.Equal(itemKeys(items), wantLatest) {
		t.Errorf("Query(%q, slots, %v) = %v, %v, want %v", groupA, open, itemKeys(items), err, wantLatest)
	}
}

func testSnapshotInvalidReadDoesNotPin(t *testing.T, s store.Store) {
	snap, err := s.Snapshot(t.Context())
	if err != nil {
		t.Fatalf("Snapshot() failed: %v", err)
	}
	defer snap.Close(context.WithoutCancel(t.Context()))

	// Invalid reads fail before the snapshot's view is fixed.
	if _, _, err := snap.Get(t.Context(), store.Key{}); !errors.Is(err, store.ErrInvalidArgument) {
		t.Errorf("snapshot Get(root) returned error %v, want %v", err, store.ErrInvalidArgument)
	}
	if _, err := snap.List(t.Context(), domain, ""); !errors.Is(err, store.ErrInvalidArgument) {
		t.Errorf("snapshot List(%q, \"\") returned error %v, want %v", domain, err, store.ErrInvalidArgument)
	}
	if _, err := snap.Query(t.Context(), domain, "groups", store.Eq{Field: "a.b", Value: "x"}); !errors.Is(err, store.ErrInvalidArgument) {
		t.Errorf("snapshot Query(%q, groups, a.b=x) returned error %v, want %v", domain, err, store.ErrInvalidArgument)
	}
	mustCreate(t, s, groupA, store.Record{})

	// The first valid read sees the record created after the invalid reads.
	if _, _, err := snap.Get(t.Context(), groupA); err != nil {
		t.Errorf("snapshot Get(%q) after invalid reads returned error %v, want the record created before the first valid read", groupA, err)
	}
}

func testSnapshotVersions(t *testing.T, s store.Store) {
	mustCreate(t, s, groupA, store.Record{"n": int64(1)})
	vb := mustCreate(t, s, groupB, store.Record{"n": int64(1)})

	snap, err := s.Snapshot(t.Context())
	if err != nil {
		t.Fatalf("Snapshot() failed: %v", err)
	}
	defer snap.Close(context.WithoutCancel(t.Context()))
	_, snapA, err := snap.Get(t.Context(), groupA)
	if err != nil {
		t.Fatalf("snapshot Get(%q) failed: %v", groupA, err)
	}
	_, snapB, err := snap.Get(t.Context(), groupB)
	if err != nil {
		t.Fatalf("snapshot Get(%q) failed: %v", groupB, err)
	}
	mustUpdate(t, s, groupB, store.Record{"n": int64(2)}, vb)

	if _, err := s.Update(t.Context(), groupA, store.Record{"n": int64(2)}, snapA); err != nil {
		t.Errorf("Update(%q) with a snapshot version of an unchanged record failed: %v", groupA, err)
	}
	if _, err := s.Update(t.Context(), groupB, store.Record{"n": int64(3)}, snapB); !errors.Is(err, store.ErrVersionMismatch) {
		t.Errorf("Update(%q) with a snapshot version of a changed record returned error %v, want %v", groupB, err, store.ErrVersionMismatch)
	}
	if err := s.Delete(t.Context(), groupB, snapB); !errors.Is(err, store.ErrVersionMismatch) {
		t.Errorf("Delete(%q) with a snapshot version of a changed record returned error %v, want %v", groupB, err, store.ErrVersionMismatch)
	}
}

func testSnapshotClose(t *testing.T, s store.Store) {
	mustCreate(t, s, groupA, store.Record{"state": "OPEN"})
	snap, err := s.Snapshot(t.Context())
	if err != nil {
		t.Fatalf("Snapshot() failed: %v", err)
	}
	if _, _, err := snap.Get(t.Context(), groupA); err != nil {
		t.Fatalf("snapshot Get(%q) failed: %v", groupA, err)
	}
	if err := snap.Close(t.Context()); err != nil {
		t.Fatalf("snapshot Close() failed: %v", err)
	}

	if _, _, err := snap.Get(t.Context(), groupA); !errors.Is(err, store.ErrClosed) {
		t.Errorf("snapshot Get(%q) after Close returned error %v, want %v", groupA, err, store.ErrClosed)
	}
	if _, err := snap.List(t.Context(), domain, "groups"); !errors.Is(err, store.ErrClosed) {
		t.Errorf("snapshot List(%q, groups) after Close returned error %v, want %v", domain, err, store.ErrClosed)
	}
	if _, err := snap.Query(t.Context(), domain, "groups", store.Eq{Field: "state", Value: "OPEN"}); !errors.Is(err, store.ErrClosed) {
		t.Errorf("snapshot Query(%q, groups, state=OPEN) after Close returned error %v, want %v", domain, err, store.ErrClosed)
	}
	if err := snap.Close(t.Context()); err != nil {
		t.Errorf("second snapshot Close() returned error %v, want nil", err)
	}
}

func mustCreate(t *testing.T, s store.Store, key store.Key, r store.Record) store.Version {
	t.Helper()
	v, err := s.Create(t.Context(), key, r)
	if err != nil {
		t.Fatalf("Create(%q) failed: %v", key, err)
	}
	return v
}

func mustUpdate(t *testing.T, s store.Store, key store.Key, r store.Record, v store.Version) store.Version {
	t.Helper()
	v, err := s.Update(t.Context(), key, r, v)
	if err != nil {
		t.Fatalf("Update(%q) failed: %v", key, err)
	}
	return v
}

func mustGet(t *testing.T, s store.Store, key store.Key) (store.Record, store.Version) {
	t.Helper()
	r, v, err := s.Get(t.Context(), key)
	if err != nil {
		t.Fatalf("Get(%q) failed: %v", key, err)
	}
	return r, v
}

// itemKeys returns the sorted keys of items, so that results in unspecified
// order compare equal.
func itemKeys(items []store.Item) []string {
	var keys []string
	for _, it := range items {
		keys = append(keys, it.Key.String())
	}
	slices.Sort(keys)
	return keys
}
