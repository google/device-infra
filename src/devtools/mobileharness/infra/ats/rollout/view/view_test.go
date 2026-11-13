package view

import (
	"context"
	"errors"
	"testing"
	"time"

	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/record"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/store/fake"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/store"
	"github.com/google/go-cmp/cmp"
)

const domainID = "mtt.example.com"

var (
	now  = time.Date(2026, 11, 11, 8, 0, 0, 0, time.UTC)
	revA = record.Revision{Build: "repo/img@sha256:aaa"}
)

func put(t *testing.T, s *fake.Store, key store.Key, r store.Record) {
	t.Helper()
	if _, err := s.Create(context.Background(), key, r); err != nil {
		t.Fatalf("Create(%q) failed: %v", key, err)
	}
}

func groupRecord(last *record.LastRollout) store.Record {
	return record.Group{
		Image:       "repo/img",
		Tag:         "latest",
		Policy:      record.Policy{Waves: []record.Wave{{Value: 100, Percent: true}}},
		LastRollout: last,
	}.Record()
}

func lastRollout(seq int64) *record.LastRollout {
	return &record.LastRollout{Seq: seq, Revision: revA, Outcome: record.OutcomeSucceeded, FinishedAt: now}
}

func rolloutRecord(finished bool) store.Record {
	ro := record.Rollout{Revision: revA, Waves: []int64{1}, CreatedAt: now}
	if finished {
		ro.Outcome, ro.FinishedAt, ro.Finished = record.OutcomeSucceeded, now, true
	}
	return ro.Record()
}

func memberRecord() store.Record {
	return record.Member{Status: record.MemberActive, HeartbeatAt: now}.Record()
}

func openSlot() store.Record {
	return record.Slot{State: record.SlotOpen}.Record()
}

// newStore returns a store with the domain document and a valid group
// "controller" that has one member.
func newStore(t *testing.T) *fake.Store {
	t.Helper()
	s := fake.New()
	put(t, s, record.DomainKey(domainID), record.Domain{}.Record())
	controller := record.GroupKey(domainID, record.GroupController)
	put(t, s, controller, groupRecord(nil))
	put(t, s, record.MemberKey(controller, "host-c"), memberRecord())
	return s
}

func TestRead(t *testing.T) {
	ctx := context.Background()
	s := fake.New()
	put(t, s, record.DomainKey(domainID), record.Domain{Paused: true}.Record())
	// A group of another domain must not appear.
	put(t, s, record.DomainKey("other.example.com"), record.Domain{}.Record())
	put(t, s, record.GroupKey("other.example.com", "dda"), groupRecord(nil))
	for _, id := range []string{"public_testing", record.GroupController, "dda"} {
		put(t, s, record.GroupKey(domainID, id), groupRecord(nil))
	}
	dda := record.GroupKey(domainID, "dda")
	for _, id := range []string{"host-b", "host-c", "host-a"} {
		put(t, s, record.MemberKey(dda, id), memberRecord())
	}
	rollout := record.RolloutKey(dda, 1)
	put(t, s, rollout, rolloutRecord(false))
	for _, n := range []int{10, 0, 2} {
		put(t, s, record.SlotKey(rollout, n), openSlot())
	}
	snap, err := s.Snapshot(ctx)
	if err != nil {
		t.Fatalf("Snapshot() failed: %v", err)
	}
	defer snap.Close(ctx)

	got, err := Read(ctx, snap, domainID)
	if err != nil {
		t.Fatalf("Read() failed: %v", err)
	}

	if got.ID != domainID || got.Key != record.DomainKey(domainID) || !got.Paused {
		t.Errorf("Read() domain = {ID: %q, Key: %q, Paused: %t}, want %q, its key, and paused", got.ID, got.Key, got.Paused, domainID)
	}
	var groupIDs []string
	for _, g := range got.Groups {
		groupIDs = append(groupIDs, g.ID)
		if g.Err != nil {
			t.Errorf("group %q: Err = %v, want nil", g.ID, g.Err)
		}
		if g.Key != record.GroupKey(domainID, g.ID) || g.Version.IsZero() || g.Record.Image != "repo/img" {
			t.Errorf("group %q: Key = %q, zero version = %t, image = %q; want its key, a version, and the stored image", g.ID, g.Key, g.Version.IsZero(), g.Record.Image)
		}
	}
	if diff := cmp.Diff([]string{record.GroupController, "dda", "public_testing"}, groupIDs); diff != "" {
		t.Fatalf("Read() groups (-want +got):\n%s", diff)
	}
	if g := got.Groups[0]; g.Rollout != nil || len(g.Members) != 0 {
		t.Errorf("group %q: has rollout = %t and %d members, want neither", g.ID, g.Rollout != nil, len(g.Members))
	}

	g := got.Groups[1]
	var memberIDs []string
	for _, m := range g.Members {
		memberIDs = append(memberIDs, m.ID)
		if m.Key != record.MemberKey(dda, m.ID) || m.Version.IsZero() || m.Record.Status != record.MemberActive {
			t.Errorf("member %q: Key = %q, zero version = %t, status = %q; want its key, a version, and %q", m.ID, m.Key, m.Version.IsZero(), m.Record.Status, record.MemberActive)
		}
	}
	if diff := cmp.Diff([]string{"host-a", "host-b", "host-c"}, memberIDs); diff != "" {
		t.Errorf("group %q members (-want +got):\n%s", g.ID, diff)
	}
	if g.Rollout == nil {
		t.Fatalf("group %q: Rollout = nil, want rollout 1", g.ID)
	}
	if ro := g.Rollout; ro.Seq != 1 || ro.Key != rollout || ro.Version.IsZero() || !ro.Record.Revision.Equal(revA) {
		t.Errorf("rollout: Seq = %d, Key = %q, zero version = %t, revision = %v; want 1, %q, a version, and %v", ro.Seq, ro.Key, ro.Version.IsZero(), ro.Record.Revision, rollout, revA)
	}
	var slotIndexes []int
	for _, sl := range g.Rollout.Slots {
		slotIndexes = append(slotIndexes, sl.Index)
		if sl.Key != record.SlotKey(rollout, sl.Index) || sl.Version.IsZero() || sl.Record.State != record.SlotOpen {
			t.Errorf("slot %d: Key = %q, zero version = %t, state = %q; want its key, a version, and %q", sl.Index, sl.Key, sl.Version.IsZero(), sl.Record.State, record.SlotOpen)
		}
	}
	// Indexes are ordered as integers: 2 before 10.
	if diff := cmp.Diff([]int{0, 2, 10}, slotIndexes); diff != "" {
		t.Errorf("rollout slots (-want +got):\n%s", diff)
	}
}

func TestRead_DomainErrors(t *testing.T) {
	tests := []struct {
		name   string
		domain store.Record // nil: no domain document
		want   error
	}{
		{name: "missing_domain", want: store.ErrNotFound},
		{name: "malformed_paused", domain: store.Record{"paused": "no"}, want: record.ErrInvalid},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			s := fake.New()
			if tc.domain != nil {
				put(t, s, record.DomainKey(domainID), tc.domain)
			}
			put(t, s, record.GroupKey(domainID, "dda"), groupRecord(nil))

			_, err := Read(context.Background(), s, domainID)

			if !errors.Is(err, tc.want) {
				t.Errorf("Read() error = %v, want it to wrap %v", err, tc.want)
			}
		})
	}
}

// failingReader fails every List of one record kind.
type failingReader struct {
	store.Reader
	kind string
}

var errRead = errors.New("store unreachable")

func (r failingReader) List(ctx context.Context, parent store.Key, kind string) ([]store.Item, error) {
	if kind == r.kind {
		return nil, errRead
	}
	return r.Reader.List(ctx, parent, kind)
}

func TestRead_ReadFailure(t *testing.T) {
	for _, kind := range []string{record.KindGroup, record.KindMember, record.KindRollout, record.KindSlot} {
		t.Run(kind, func(t *testing.T) {
			s := newStore(t)
			rollout := record.RolloutKey(record.GroupKey(domainID, record.GroupController), 1)
			put(t, s, rollout, rolloutRecord(false))

			_, err := Read(context.Background(), failingReader{Reader: s, kind: kind}, domainID)

			if !errors.Is(err, errRead) {
				t.Errorf("Read() error = %v, want it to wrap %v", err, errRead)
			}
		})
	}
}

// tainted is a reader that returns one record with a value of a type the
// store does not allow, in a field the schema does not know. The Firestore
// adapter returns a null typed in the admin tool this way; the fake store
// cannot hold one.
type tainted struct {
	store.Reader
	key store.Key
}

func (r tainted) taint(key store.Key, rec store.Record) {
	if key == r.key {
		rec["note"] = nil
	}
}

func (r tainted) Get(ctx context.Context, key store.Key) (store.Record, store.Version, error) {
	rec, v, err := r.Reader.Get(ctx, key)
	if err == nil {
		r.taint(key, rec)
	}
	return rec, v, err
}

func (r tainted) List(ctx context.Context, parent store.Key, kind string) ([]store.Item, error) {
	items, err := r.Reader.List(ctx, parent, kind)
	for _, it := range items {
		r.taint(it.Key, it.Record)
	}
	return items, err
}

func TestRead_DisallowedValueStaysWithGroup(t *testing.T) {
	dda := record.GroupKey(domainID, "dda")
	rollout := record.RolloutKey(dda, 1)
	for name, key := range map[string]store.Key{
		"group":   dda,
		"member":  record.MemberKey(dda, "host-a"),
		"rollout": rollout,
		"slot":    record.SlotKey(rollout, 0),
	} {
		t.Run(name, func(t *testing.T) {
			s := newStore(t)
			put(t, s, dda, groupRecord(nil))
			put(t, s, record.MemberKey(dda, "host-a"), memberRecord())
			put(t, s, rollout, rolloutRecord(false))
			put(t, s, record.SlotKey(rollout, 0), openSlot())

			got, err := Read(context.Background(), tainted{Reader: s, key: key}, domainID)
			if err != nil {
				t.Fatalf("Read() failed, want the bad value to stay with its group: %v", err)
			}

			if len(got.Groups) != 2 {
				t.Fatalf("Read() returned %d groups, want controller and dda", len(got.Groups))
			}
			if c := got.Groups[0]; c.Err != nil {
				t.Errorf("group %q: Err = %v, want nil: the bad value is in another group", c.ID, c.Err)
			}
			if g := got.Groups[1]; !errors.Is(g.Err, record.ErrInvalid) {
				t.Errorf("group %q: Err = %v, want it to wrap %v", g.ID, g.Err, record.ErrInvalid)
			}
		})
	}

	// In the domain document it stops the run, like any invalid domain.
	s := newStore(t)
	if _, err := Read(context.Background(), tainted{Reader: s, key: record.DomainKey(domainID)}, domainID); !errors.Is(err, record.ErrInvalid) {
		t.Errorf("Read() with a bad value in the domain document returned error %v, want it to wrap %v", err, record.ErrInvalid)
	}
}

func TestRead_GroupValidation(t *testing.T) {
	dda := record.GroupKey(domainID, "dda")
	tests := []struct {
		name string
		// records are the records under and including group "dda".
		records map[store.Key]store.Record
		// wantValid is whether the group is valid; an invalid one must wrap
		// record.ErrInvalid.
		wantValid bool
	}{
		{
			name:      "group_without_rollout",
			records:   map[store.Key]store.Record{dda: groupRecord(lastRollout(3))},
			wantValid: true,
		},
		{
			name: "first_rollout",
			records: map[store.Key]store.Record{
				dda:                       groupRecord(nil),
				record.RolloutKey(dda, 1): rolloutRecord(false),
			},
			wantValid: true,
		},
		{
			name: "rollout_after_last_one",
			records: map[store.Key]store.Record{
				dda:                       groupRecord(lastRollout(3)),
				record.RolloutKey(dda, 4): rolloutRecord(false),
			},
			wantValid: true,
		},
		{
			// Archive crash window: finished rollout already recorded as last.
			name: "archive_crash_window",
			records: map[store.Key]store.Record{
				dda:                       groupRecord(lastRollout(3)),
				record.RolloutKey(dda, 3): rolloutRecord(true),
			},
			wantValid: true,
		},
		{
			name:    "group_without_image",
			records: map[store.Key]store.Record{dda: {"tag": "latest"}},
		},
		{
			name: "member_without_status",
			records: map[store.Key]store.Record{
				dda:                             groupRecord(nil),
				record.MemberKey(dda, "host-a"): {"heartbeat_at": now},
			},
		},
		{
			name: "rollout_without_waves",
			records: map[store.Key]store.Record{
				dda:                       groupRecord(nil),
				record.RolloutKey(dda, 1): record.Rollout{Revision: revA}.Record(),
			},
		},
		{
			name: "rollout_key_with_padding",
			records: map[store.Key]store.Record{
				dda:                                 groupRecord(nil),
				dda.Child(record.KindRollout, "01"): rolloutRecord(false),
			},
		},
		{
			name: "two_rollout_documents",
			records: map[store.Key]store.Record{
				dda:                       groupRecord(lastRollout(1)),
				record.RolloutKey(dda, 1): rolloutRecord(true),
				record.RolloutKey(dda, 2): rolloutRecord(false),
			},
		},
		{
			name: "slot_with_task_fields_not_claimed",
			records: map[store.Key]store.Record{
				dda:                       groupRecord(nil),
				record.RolloutKey(dda, 1): rolloutRecord(false),
				record.SlotKey(record.RolloutKey(dda, 1), 0): {"state": "OPEN", "agent_id": "host-a"},
			},
		},
		{
			name: "slot_key_not_a_number",
			records: map[store.Key]store.Record{
				dda:                       groupRecord(nil),
				record.RolloutKey(dda, 1): rolloutRecord(false),
				record.RolloutKey(dda, 1).Child(record.KindSlot, "first"): openSlot(),
			},
		},
		{
			name: "finished_rollout_with_slot",
			records: map[store.Key]store.Record{
				dda:                       groupRecord(nil),
				record.RolloutKey(dda, 1): rolloutRecord(true),
				record.SlotKey(record.RolloutKey(dda, 1), 0): openSlot(),
			},
		},
		{
			// First rollout with a sequence number other than 1.
			name: "first_rollout_seq_not_1",
			records: map[store.Key]store.Record{
				dda:                       groupRecord(nil),
				record.RolloutKey(dda, 2): rolloutRecord(false),
			},
		},
		{
			// Rollout that skips a sequence number.
			name: "rollout_skips_seq",
			records: map[store.Key]store.Record{
				dda:                       groupRecord(lastRollout(3)),
				record.RolloutKey(dda, 5): rolloutRecord(false),
			},
		},
		{
			// Unfinished rollout at last_rollout.seq.
			name: "unfinished_rollout_at_last_seq",
			records: map[store.Key]store.Record{
				dda:                       groupRecord(lastRollout(3)),
				record.RolloutKey(dda, 3): rolloutRecord(false),
			},
		},
		{
			// Rollout below last_rollout.seq.
			name: "rollout_below_last_seq",
			records: map[store.Key]store.Record{
				dda:                       groupRecord(lastRollout(3)),
				record.RolloutKey(dda, 2): rolloutRecord(true),
			},
		},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			s := newStore(t)
			for key, r := range tc.records {
				put(t, s, key, r)
			}

			got, err := Read(context.Background(), s, domainID)
			if err != nil {
				t.Fatalf("Read() failed: %v", err)
			}

			if len(got.Groups) != 2 || got.Groups[0].ID != record.GroupController || got.Groups[1].ID != "dda" {
				t.Fatalf("Read() returned %d groups, want controller and dda", len(got.Groups))
			}
			// The other group is not affected by this one.
			if c := got.Groups[0]; c.Err != nil || len(c.Members) != 1 {
				t.Errorf("group %q: Err = %v with %d members, want a valid group with 1 member", c.ID, c.Err, len(c.Members))
			}
			g := got.Groups[1]
			if tc.wantValid {
				if g.Err != nil {
					t.Errorf("group %q: Err = %v, want nil", g.ID, g.Err)
				}
				return
			}
			if !errors.Is(g.Err, record.ErrInvalid) {
				t.Errorf("group %q: Err = %v, want it to wrap %v", g.ID, g.Err, record.ErrInvalid)
			}
			if g.Key != dda {
				t.Errorf("invalid group: Key = %q, want %q", g.Key, dda)
			}
			if g.Rollout != nil || g.Members != nil || !g.Version.IsZero() {
				t.Errorf("invalid group %q carries records (rollout: %t, members: %d, zero version: %t), want none", g.ID, g.Rollout != nil, len(g.Members), g.Version.IsZero())
			}
		})
	}
}

func TestGroup_Resolved(t *testing.T) {
	tests := []struct {
		name  string
		group Group
		want  bool
	}{
		{name: "upstream_set", group: Group{Upstream: revA}, want: true},
		{name: "nothing_set", group: Group{}, want: false},
		{name: "error", group: Group{UpstreamErr: errRead}, want: false},
		{name: "partial_result_with_error", group: Group{Upstream: revA, UpstreamErr: errRead}, want: false},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			if got := tc.group.Resolved(); got != tc.want {
				t.Errorf("Resolved() = %t, want %t", got, tc.want)
			}
		})
	}
}

func TestGroup_Online(t *testing.T) {
	member := func(status record.MemberStatus, age time.Duration) Member {
		return Member{Record: record.Member{Status: status, HeartbeatAt: now.Add(-age)}}
	}
	g := Group{Members: []Member{
		member(record.MemberActive, time.Minute),
		member(record.MemberDown, time.Minute),
		member(record.MemberActive, record.HeartbeatTimeout),
		member(record.MemberOptedOut, time.Minute),
		member(record.MemberOffline, time.Minute),
	}}

	if got, want := g.Online(now), 2; got != want {
		t.Errorf("Online() = %d, want %d", got, want)
	}
}
