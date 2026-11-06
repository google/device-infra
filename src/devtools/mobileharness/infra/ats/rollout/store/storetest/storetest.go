// Package storetest checks that an implementation of store.Store satisfies
// the contract documented in package store.
package storetest

import (
	"context"
	"errors"
	"slices"
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
		{"UpdateMissing", testUpdateMissing},
		{"UpdateAfterRecreate", testUpdateAfterRecreate},
		{"DeleteConditional", testDeleteConditional},
		{"DeleteMissing", testDeleteMissing},
		{"ListDirectChildren", testListDirectChildren},
		{"QueryFilter", testQueryFilter},
		{"InvalidArguments", testInvalidArguments},
		{"ReturnedRecordsAreCopies", testReturnedRecordsAreCopies},
		{"SnapshotIsolation", testSnapshotIsolation},
		{"SnapshotVersions", testSnapshotVersions},
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
		"s":    "value",
		"b":    true,
		"i":    int64(-42),
		"at":   at,
		"list": []any{"x", int64(1), []any{false}},
		"map":  map[string]any{"host.example.com": "DONE", "at": at, "nested": map[string]any{"k": "v"}},
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
		"s":    "value",
		"b":    true,
		"i":    int64(-42),
		"at":   wantAt,
		"list": []any{"x", int64(1), []any{false}},
		"map":  map[string]any{"host.example.com": "DONE", "at": wantAt, "nested": map[string]any{"k": "v"}},
	}
	if diff := cmp.Diff(want, got); diff != "" {
		t.Errorf("Get(%q) after Create returned diff (-want +got):\n%s", groupA, diff)
	}
	if gotAt, ok := got["at"].(time.Time); !ok || gotAt.Location() != time.UTC {
		t.Errorf("Get(%q) field \"at\" = %v, want a time.Time in UTC", groupA, got["at"])
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
	if err := s.Delete(t.Context(), groupA, nil); err != nil {
		t.Fatalf("Delete(%q) failed: %v", groupA, err)
	}
	mustCreate(t, s, groupA, store.Record{"n": int64(1)})

	if _, err := s.Update(t.Context(), groupA, store.Record{"n": int64(2)}, old); !errors.Is(err, store.ErrVersionMismatch) {
		t.Errorf("Update(%q) with a version from before Delete and Create returned error %v, want %v", groupA, err, store.ErrVersionMismatch)
	}
}

func testDeleteConditional(t *testing.T, s store.Store) {
	v1 := mustCreate(t, s, groupA, store.Record{"n": int64(1)})
	v2 := mustUpdate(t, s, groupA, store.Record{"n": int64(2)}, v1)

	if err := s.Delete(t.Context(), groupA, &v1); !errors.Is(err, store.ErrVersionMismatch) {
		t.Errorf("Delete(%q) with a superseded version returned error %v, want %v", groupA, err, store.ErrVersionMismatch)
	}
	mustGet(t, s, groupA)
	if err := s.Delete(t.Context(), groupA, &v2); err != nil {
		t.Errorf("Delete(%q) with the current version failed: %v", groupA, err)
	}
	if _, _, err := s.Get(t.Context(), groupA); !errors.Is(err, store.ErrNotFound) {
		t.Errorf("Get(%q) after Delete returned error %v, want %v", groupA, err, store.ErrNotFound)
	}
}

func testDeleteMissing(t *testing.T, s store.Store) {
	v := mustCreate(t, s, groupA, store.Record{})

	if err := s.Delete(t.Context(), groupB, nil); err != nil {
		t.Errorf("Delete(%q, nil) on a missing record returned error %v, want nil", groupB, err)
	}
	if err := s.Delete(t.Context(), groupB, &v); err != nil {
		t.Errorf("Delete(%q, &v) on a missing record returned error %v, want nil", groupB, err)
	}
}

func testListDirectChildren(t *testing.T, s store.Store) {
	other := store.NewKey("domains", "d2") // Parent record that does not exist.
	for _, k := range []store.Key{
		domain,
		groupA,
		groupB,
		groupA.Child("members", "m1"), // Grandchild of domain.
		domain.Child("others", "x"),   // Other type.
		other.Child("groups", "c"),    // Other parent.
	} {
		mustCreate(t, s, k, store.Record{"id": k.ID()})
	}

	tests := []struct {
		parent store.Key
		typ    string
		want   []string
	}{
		{parent: domain, typ: "groups", want: []string{"domains/d1/groups/a", "domains/d1/groups/b"}},
		{parent: store.Key{}, typ: "domains", want: []string{"domains/d1"}},
		{parent: other, typ: "groups", want: []string{"domains/d2/groups/c"}},
		{parent: groupB, typ: "members", want: nil},
	}
	for _, tc := range tests {
		items, err := s.List(t.Context(), tc.parent, tc.typ)
		if err != nil {
			t.Errorf("List(%q, %q) failed: %v", tc.parent, tc.typ, err)
			continue
		}
		if diff := cmp.Diff(tc.want, itemKeys(items)); diff != "" {
			t.Errorf("List(%q, %q) returned keys diff (-want +got):\n%s", tc.parent, tc.typ, diff)
		}
		for _, it := range items {
			if it.Record["id"] != it.Key.ID() {
				t.Errorf("List(%q, %q) returned record %v for key %q, want the record stored there", tc.parent, tc.typ, it.Record, it.Key)
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

	items, err := s.Query(t.Context(), groupA, "slots", "agent_id", "h1")
	if err != nil {
		t.Fatalf("Query(%q, slots, agent_id, h1) failed: %v", groupA, err)
	}
	want := []string{"domains/d1/groups/a/slots/also_match", "domains/d1/groups/a/slots/match"}
	if diff := cmp.Diff(want, itemKeys(items)); diff != "" {
		t.Errorf("Query(%q, slots, agent_id, h1) returned keys diff (-want +got):\n%s", groupA, diff)
	}
	if items, err := s.Query(t.Context(), groupA, "slots", "agent_id", "1"); err != nil || len(items) != 0 {
		t.Errorf("Query(%q, slots, agent_id, 1) = %v, %v, want no items: an int64 never equals a string", groupA, itemKeys(items), err)
	}
}

func testInvalidArguments(t *testing.T, s store.Store) {
	ctx := t.Context()
	bad := map[string]func() error{
		"get_root":           func() error { _, _, err := s.Get(ctx, store.Key{}); return err },
		"get_empty_id":       func() error { _, _, err := s.Get(ctx, store.NewKey("domains", "")); return err },
		"get_slash_id":       func() error { _, _, err := s.Get(ctx, store.NewKey("domains", "a/b")); return err },
		"get_dot_id":         func() error { _, _, err := s.Get(ctx, store.NewKey("domains", "..")); return err },
		"get_reserved_id":    func() error { _, _, err := s.Get(ctx, store.NewKey("domains", "__x__")); return err },
		"delete_root":        func() error { return s.Delete(ctx, store.Key{}, nil) },
		"list_empty_type":    func() error { _, err := s.List(ctx, domain, ""); return err },
		"query_empty_field":  func() error { _, err := s.Query(ctx, domain, "groups", "", "a"); return err },
		"query_empty_value":  func() error { _, err := s.Query(ctx, domain, "groups", "state", ""); return err },
		"create_int":         func() error { _, err := s.Create(ctx, groupA, store.Record{"n": 1}); return err },
		"create_float":       func() error { _, err := s.Create(ctx, groupA, store.Record{"n": 1.5}); return err },
		"create_nil":         func() error { _, err := s.Create(ctx, groupA, store.Record{"n": nil}); return err },
		"create_bad_utf8":    func() error { _, err := s.Create(ctx, groupA, store.Record{"s": "\xff"}); return err },
		"create_empty_field": func() error { _, err := s.Create(ctx, groupA, store.Record{"": "x"}); return err },
		"create_nested_bad": func() error {
			_, err := s.Create(ctx, groupA, store.Record{"m": map[string]any{"__k__": "x"}})
			return err
		},
		"create_list_bad": func() error { _, err := s.Create(ctx, groupA, store.Record{"l": []any{int32(1)}}); return err },
		"update_root":     func() error { _, err := s.Update(ctx, store.Key{}, store.Record{}, store.Version{}); return err },
	}
	for name, call := range bad {
		if err := call(); !errors.Is(err, store.ErrInvalidArgument) {
			t.Errorf("%s: returned error %v, want %v", name, err, store.ErrInvalidArgument)
		}
	}
	if _, _, err := s.Get(ctx, groupA); !errors.Is(err, store.ErrNotFound) {
		t.Errorf("Get(%q) after invalid Creates returned error %v, want %v", groupA, err, store.ErrNotFound)
	}
}

func testReturnedRecordsAreCopies(t *testing.T, s store.Store) {
	r := store.Record{"m": map[string]any{"k": "v"}, "l": []any{"x"}}
	mustCreate(t, s, groupA, r)
	r["m"].(map[string]any)["k"] = "changed after Create"

	got, _ := mustGet(t, s, groupA)
	got["m"].(map[string]any)["k"] = "changed after Get"
	got["l"].([]any)[0] = "changed after Get"

	want := store.Record{"m": map[string]any{"k": "v"}, "l": []any{"x"}}
	if got, _ := mustGet(t, s, groupA); !cmp.Equal(got, want) {
		t.Errorf("Get(%q) after modifying earlier records = %v, want %v", groupA, got, want)
	}
}

func testSnapshotIsolation(t *testing.T, s store.Store) {
	a, b, c := groupA.Child("slots", "a"), groupA.Child("slots", "b"), groupA.Child("slots", "c")
	va := mustCreate(t, s, a, store.Record{"state": "OPEN"})
	mustCreate(t, s, b, store.Record{"state": "OPEN"})

	snap, err := s.Snapshot(t.Context())
	if err != nil {
		t.Fatalf("Snapshot() failed: %v", err)
	}
	defer snap.Close(context.WithoutCancel(t.Context()))

	mustUpdate(t, s, a, store.Record{"state": "CLAIMED"}, va)
	if err := s.Delete(t.Context(), b, nil); err != nil {
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
	if items, err := snap.Query(t.Context(), groupA, "slots", "state", "OPEN"); err != nil || !cmp.Equal(itemKeys(items), wantKeys) {
		t.Errorf("snapshot Query(%q, slots, state, OPEN) = %v, %v, want %v", groupA, itemKeys(items), err, wantKeys)
	}
	wantLatest := []string{c.String()}
	if items, err := s.Query(t.Context(), groupA, "slots", "state", "OPEN"); err != nil || !cmp.Equal(itemKeys(items), wantLatest) {
		t.Errorf("Query(%q, slots, state, OPEN) = %v, %v, want %v", groupA, itemKeys(items), err, wantLatest)
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
	if err := s.Delete(t.Context(), groupB, &snapB); !errors.Is(err, store.ErrVersionMismatch) {
		t.Errorf("Delete(%q) with a snapshot version of a changed record returned error %v, want %v", groupB, err, store.ErrVersionMismatch)
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
