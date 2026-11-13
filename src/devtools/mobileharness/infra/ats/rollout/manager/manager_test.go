package manager

import (
	"context"
	"errors"
	"strings"
	"testing"
	"time"

	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/apply"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/apply/applytest"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/record"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/store/fake"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/store"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/view"
	"github.com/google/go-cmp/cmp"
)

const (
	domainID   = "mtt.example.com"
	controller = record.GroupController
	pool       = "dda"

	fresh = time.Minute             // age of a heartbeat that is online
	stale = record.HeartbeatTimeout // age of a heartbeat that is just stale
)

var (
	now      = time.Date(2026, 11, 11, 8, 0, 0, 0, time.UTC)
	earlier  = now.Add(-time.Hour)
	revA     = record.Revision{Build: "repo/img@sha256:aaa", TenantConfigVersion: "t1", PoolConfigVersion: "p1"}
	revB     = record.Revision{Build: "repo/img@sha256:bbb", TenantConfigVersion: "t1", PoolConfigVersion: "p1"}
	ctrlRevA = record.Revision{Build: "repo/img@sha256:aaa"}
	ctrlRevB = record.Revision{Build: "repo/img@sha256:bbb"}

	controllerKey = record.GroupKey(domainID, controller)
	poolKey       = record.GroupKey(domainID, pool)
)

// fixture builds the stored state of one domain.
type fixture struct {
	t *testing.T
	s *fake.Store
}

// newFixture returns a store that holds the domain document.
func newFixture(t *testing.T, paused bool) *fixture {
	t.Helper()
	f := &fixture{t: t, s: fake.New()}
	f.put(record.DomainKey(domainID), record.Domain{Paused: paused}.Record())
	return f
}

func (f *fixture) put(key store.Key, r store.Record) {
	f.t.Helper()
	if _, err := f.s.Create(context.Background(), key, r); err != nil {
		f.t.Fatalf("Create(%q) failed: %v", key, err)
	}
}

func (f *fixture) group(key store.Key, g record.Group) {
	f.t.Helper()
	f.put(key, g.Record())
}

// member stores a member of group whose last heartbeat is age old.
func (f *fixture) member(group store.Key, agentID string, status record.MemberStatus, age time.Duration) {
	f.t.Helper()
	f.put(record.MemberKey(group, agentID), memberDoc(status, age).Record())
}

func (f *fixture) rollout(group store.Key, seq int64, ro record.Rollout) {
	f.t.Helper()
	f.put(record.RolloutKey(group, seq), ro.Record())
}

func memberDoc(status record.MemberStatus, age time.Duration) record.Member {
	return record.Member{Status: status, HeartbeatAt: now.Add(-age), RunningRevision: revA}
}

// groupDoc returns a group that follows a tag, with a policy of three waves
// and both limits.
func groupDoc(last *record.LastRollout) record.Group {
	return record.Group{
		Image: "repo/img",
		Tag:   "latest",
		Policy: record.Policy{
			Waves:       []record.Wave{{Value: 1}, {Value: 50, Percent: true}, {Value: 100, Percent: true}},
			MaxInFlight: 2,
			MaxFailed:   1,
		},
		LastRollout: last,
	}
}

func lastDoc(seq int64, rev record.Revision, outcome record.Outcome) *record.LastRollout {
	return &record.LastRollout{Seq: seq, Revision: rev, Outcome: outcome, FinishedAt: earlier}
}

// rolloutDoc returns an IN_PROGRESS rollout of rev.
func rolloutDoc(rev record.Revision) record.Rollout {
	return record.Rollout{Revision: rev, Waves: []int64{1, 2}, MaxFailed: 1, CreatedAt: earlier}
}

// concluded returns ro with an outcome: CONCLUDED, or FINISHED if finished.
func concluded(ro record.Rollout, outcome record.Outcome, finished bool) record.Rollout {
	ro.Outcome, ro.FinishedAt, ro.Finished = outcome, earlier, finished
	ro.Summary = map[string]record.SummaryEntry{"host-a": record.SummaryEntry(record.TaskDone), "host-b": record.SummaryOffline}
	return ro
}

// upstreams maps a group ID to its resolved upstream. A group that is not in
// the map is unresolved in this run.
type upstreams map[string]record.Revision

var errUnresolved = errors.New("registry unreachable")

// read returns the view of the fixture's domain through one snapshot, with
// the upstreams filled in.
func read(ctx context.Context, s store.Store, up upstreams) (view.Domain, error) {
	snap, err := s.Snapshot(ctx)
	if err != nil {
		return view.Domain{}, err
	}
	defer snap.Close(ctx)
	d, err := view.Read(ctx, snap, domainID)
	if err != nil {
		return view.Domain{}, err
	}
	for i := range d.Groups {
		g := &d.Groups[i]
		if rev, ok := up[g.ID]; ok {
			g.Upstream = rev
		} else {
			g.UpstreamErr = errUnresolved
		}
	}
	return d, nil
}

// write is an apply.Write in a form that cmp can compare: without the
// expected version, which is opaque.
type write struct {
	Op     string
	Key    string
	Record store.Record
}

func update(key store.Key, r store.Record) write {
	return write{Op: "update", Key: key.String(), Record: r}
}

func writesOf(seq apply.Sequence) []write {
	var ws []write
	for _, w := range seq {
		ws = append(ws, write{Op: w.Op.String(), Key: w.Key.String(), Record: w.Record})
	}
	return ws
}

func TestDecide_Rows(t *testing.T) {
	tests := []struct {
		name string
		// group is the ID of the group whose decision is checked; pool if
		// empty.
		group     string
		paused    bool
		setup     func(f *fixture)
		upstreams upstreams
		wantRow   Row
		wantRow0  []write // bookkeeping, one write per sequence
		wantSeq   []write // the row's sequence
		// wantAnomaly is a substring of the single anomaly expected; empty
		// for none.
		wantAnomaly string
	}{
		{
			// Row 0: stale ACTIVE and DOWN members are marked OFFLINE, others are left alone.
			name: "row0_stale_members_marked_offline",
			setup: func(f *fixture) {
				f.group(poolKey, groupDoc(lastDoc(1, revA, record.OutcomeSucceeded)))
				f.member(poolKey, "host-a", record.MemberActive, fresh)
				f.member(poolKey, "host-b", record.MemberActive, stale)
				f.member(poolKey, "host-c", record.MemberDown, 2*stale)
				f.member(poolKey, "host-d", record.MemberDown, stale-time.Second)
				f.member(poolKey, "host-e", record.MemberOptedOut, 2*stale)
				f.member(poolKey, "host-f", record.MemberOffline, 2*stale)
			},
			upstreams: upstreams{pool: revA},
			wantRow:   RowUpToDate,
			wantRow0: []write{
				update(record.MemberKey(poolKey, "host-b"), memberDoc(record.MemberOffline, stale).Record()),
				update(record.MemberKey(poolKey, "host-c"), memberDoc(record.MemberOffline, 2*stale).Record()),
			},
		},
		{
			// Row 1: a FINISHED rollout is archived and the requests are cleared.
			name: "row1_finished_rollout_archived",
			setup: func(f *fixture) {
				g := groupDoc(lastDoc(1, revA, record.OutcomeSucceeded))
				g.Rerun, g.Cancel = true, true
				f.group(poolKey, g)
				ro := rolloutDoc(revB)
				ro.StopRequest = record.StopCancelled
				f.rollout(poolKey, 2, concluded(ro, record.OutcomeCancelled, true))
			},
			upstreams: upstreams{pool: revA},
			wantRow:   RowArchive,
			wantSeq: []write{
				update(poolKey, groupDoc(&record.LastRollout{
					Seq:        2,
					Revision:   revB,
					Outcome:    record.OutcomeCancelled,
					Summary:    map[string]record.SummaryEntry{"host-a": record.SummaryEntry(record.TaskDone), "host-b": record.SummaryOffline},
					FinishedAt: earlier,
				}).Record()),
				{Op: "delete", Key: record.RolloutKey(poolKey, 2).String()},
			},
		},
		{
			// Row 1: the group write is skipped when the group already records the rollout, keeping a rerun.
			name: "row1_group_write_skipped_after_crash",
			setup: func(f *fixture) {
				g := groupDoc(lastDoc(2, revB, record.OutcomeIncomplete))
				g.Rerun = true
				f.group(poolKey, g)
				f.rollout(poolKey, 2, concluded(rolloutDoc(revB), record.OutcomeIncomplete, true))
			},
			upstreams: upstreams{pool: revB},
			wantRow:   RowArchive,
			wantSeq:   []write{{Op: "delete", Key: record.RolloutKey(poolKey, 2).String()}},
		},
		{
			name: "row1_needs_no_upstream",
			setup: func(f *fixture) {
				f.group(poolKey, groupDoc(nil))
				f.rollout(poolKey, 1, concluded(rolloutDoc(revA), record.OutcomeSucceeded, true))
			},
			wantRow: RowArchive,
			wantSeq: []write{
				update(poolKey, groupDoc(&record.LastRollout{
					Seq:        1,
					Revision:   revA,
					Outcome:    record.OutcomeSucceeded,
					Summary:    map[string]record.SummaryEntry{"host-a": record.SummaryEntry(record.TaskDone), "host-b": record.SummaryOffline},
					FinishedAt: earlier,
				}).Record()),
				{Op: "delete", Key: record.RolloutKey(poolKey, 1).String()},
			},
		},
		{
			// Row 1a: a CONCLUDED rollout is left to the orchestrator.
			name: "row1a_concluded_left_to_orchestrator",
			setup: func(f *fixture) {
				g := groupDoc(nil)
				g.Cancel = true
				f.group(poolKey, g)
				f.rollout(poolKey, 1, concluded(rolloutDoc(revA), record.OutcomeSucceeded, false))
			},
			upstreams: upstreams{pool: revB},
			wantRow:   RowConcluded,
		},
		{
			// Row 2: a rollout that is stopping is not stopped again.
			name: "row2_stopping_not_stopped_again",
			setup: func(f *fixture) {
				g := groupDoc(nil)
				g.Cancel = true
				f.group(poolKey, g)
				ro := rolloutDoc(revA)
				ro.StopRequest = record.StopSuperseded
				f.rollout(poolKey, 1, ro)
			},
			upstreams: upstreams{pool: revB},
			wantRow:   RowStopping,
		},
		{
			// Row 3: a rollout of an old revision is superseded, before a cancel is looked at.
			name: "row3_superseded_before_cancel",
			setup: func(f *fixture) {
				g := groupDoc(nil)
				g.Cancel = true
				f.group(poolKey, g)
				f.rollout(poolKey, 1, rolloutDoc(revA))
			},
			upstreams: upstreams{pool: revB},
			wantRow:   RowSupersede,
			wantSeq: []write{update(record.RolloutKey(poolKey, 1), func() store.Record {
				ro := rolloutDoc(revA)
				ro.StopRequest = record.StopSuperseded
				return ro.Record()
			}())},
		},
		{
			// Row 3 does not apply while the domain is paused.
			name:   "row3_not_while_paused",
			paused: true,
			setup: func(f *fixture) {
				f.group(poolKey, groupDoc(nil))
				f.rollout(poolKey, 1, rolloutDoc(revA))
			},
			upstreams: upstreams{pool: revB},
			wantRow:   RowRollingOut,
		},
		{
			// Row 3 does not apply while the upstream is unresolved.
			name: "row3_not_while_unresolved",
			setup: func(f *fixture) {
				f.group(poolKey, groupDoc(nil))
				f.rollout(poolKey, 1, rolloutDoc(revA))
			},
			wantRow: RowRollingOut,
		},
		{
			// Row 4: a cancel stops the rollout, also while paused and with the upstream moved.
			name:   "row4_cancel_stops_rollout",
			paused: true,
			setup: func(f *fixture) {
				g := groupDoc(nil)
				g.Cancel = true
				f.group(poolKey, g)
				f.rollout(poolKey, 1, rolloutDoc(revA))
			},
			upstreams: upstreams{pool: revB},
			wantRow:   RowCancel,
			wantSeq: []write{update(record.RolloutKey(poolKey, 1), func() store.Record {
				ro := rolloutDoc(revA)
				ro.StopRequest = record.StopCancelled
				return ro.Record()
			}())},
		},
		{
			name: "row4_needs_no_upstream",
			setup: func(f *fixture) {
				g := groupDoc(nil)
				g.Cancel = true
				f.group(poolKey, g)
				f.rollout(poolKey, 1, rolloutDoc(revA))
			},
			wantRow: RowCancel,
			wantSeq: []write{update(record.RolloutKey(poolKey, 1), func() store.Record {
				ro := rolloutDoc(revA)
				ro.StopRequest = record.StopCancelled
				return ro.Record()
			}())},
		},
		{
			// Row 5: a rollout of the current upstream is left alone.
			name: "row5_current_upstream_left_alone",
			setup: func(f *fixture) {
				g := groupDoc(nil)
				g.Rerun = true
				f.group(poolKey, g)
				f.rollout(poolKey, 1, rolloutDoc(revA))
			},
			upstreams: upstreams{pool: revA},
			wantRow:   RowRollingOut,
		},
		{
			// Row 5a: a cancel without a rollout is cleared, also while paused.
			name:   "row5a_cancel_without_rollout_cleared",
			paused: true,
			setup: func(f *fixture) {
				g := groupDoc(lastDoc(1, revA, record.OutcomeSucceeded))
				g.Cancel, g.Rerun = true, true
				f.group(poolKey, g)
				f.member(poolKey, "host-a", record.MemberActive, fresh)
			},
			upstreams: upstreams{pool: revB},
			wantRow:   RowClearCancel,
			wantSeq: []write{update(poolKey, func() store.Record {
				g := groupDoc(lastDoc(1, revA, record.OutcomeSucceeded))
				g.Rerun = true
				return g.Record()
			}())},
		},
		{
			// Row 5b: no new rollout while the domain is paused.
			name:   "row5b_paused_starts_nothing",
			paused: true,
			setup: func(f *fixture) {
				f.group(poolKey, groupDoc(nil))
				f.member(poolKey, "host-a", record.MemberActive, fresh)
			},
			upstreams: upstreams{pool: revB},
			wantRow:   RowPaused,
		},
		{
			// Row 6: an unresolved upstream starts nothing and is not an anomaly.
			name: "row6_unresolved_starts_nothing",
			setup: func(f *fixture) {
				f.group(poolKey, groupDoc(nil))
				f.member(poolKey, "host-a", record.MemberActive, fresh)
			},
			wantRow: RowUnresolved,
		},
		{
			// Row 7: the upstream was rolled out last, whatever the outcome.
			name: "row7_upstream_already_rolled_out",
			setup: func(f *fixture) {
				f.group(poolKey, groupDoc(lastDoc(1, revA, record.OutcomeFailed)))
				f.member(poolKey, "host-a", record.MemberActive, fresh)
			},
			// The same digest under another repository spelling is the same
			// revision.
			upstreams: upstreams{pool: {Build: "mirror/img@sha256:aaa", TenantConfigVersion: "t1", PoolConfigVersion: "p1"}},
			wantRow:   RowUpToDate,
		},
		{
			// Row 8: a pool waits while the controller is rolling out.
			name: "row8_pool_waits_controller_rolling_out",
			setup: func(f *fixture) {
				f.group(controllerKey, groupDoc(lastDoc(1, ctrlRevA, record.OutcomeSucceeded)))
				f.rollout(controllerKey, 2, rolloutDoc(ctrlRevB))
				f.group(poolKey, groupDoc(nil))
				f.member(poolKey, "host-a", record.MemberActive, fresh)
			},
			upstreams: upstreams{controller: ctrlRevB, pool: revB},
			wantRow:   RowWaiting,
		},
		{
			// Row 8: a pool waits for a controller that has a new revision pending.
			name: "row8_pool_waits_controller_pending",
			setup: func(f *fixture) {
				f.group(controllerKey, groupDoc(lastDoc(1, ctrlRevA, record.OutcomeSucceeded)))
				f.group(poolKey, groupDoc(nil))
				f.member(poolKey, "host-a", record.MemberActive, fresh)
			},
			upstreams: upstreams{controller: ctrlRevB, pool: revB},
			wantRow:   RowWaiting,
		},
		{
			// Row 8: a pool waits for a controller that never rolled out.
			name: "row8_pool_waits_controller_never_rolled_out",
			setup: func(f *fixture) {
				f.group(controllerKey, groupDoc(nil))
				f.group(poolKey, groupDoc(nil))
				f.member(poolKey, "host-a", record.MemberActive, fresh)
			},
			upstreams: upstreams{controller: ctrlRevA, pool: revB},
			wantRow:   RowWaiting,
		},
		{
			// Row 8: a pool waits while the controller's upstream is unresolved.
			name: "row8_pool_waits_controller_unresolved",
			setup: func(f *fixture) {
				f.group(controllerKey, groupDoc(lastDoc(1, ctrlRevA, record.OutcomeSucceeded)))
				f.group(poolKey, groupDoc(nil))
				f.member(poolKey, "host-a", record.MemberActive, fresh)
			},
			upstreams: upstreams{pool: revB},
			wantRow:   RowWaiting,
		},
		{
			// Row 8: a controller rollout that did not succeed is stuck, which is an anomaly.
			name: "row8_failed_controller_is_stuck",
			setup: func(f *fixture) {
				f.group(controllerKey, groupDoc(lastDoc(1, ctrlRevA, record.OutcomeIncomplete)))
				f.group(poolKey, groupDoc(nil))
				f.member(poolKey, "host-a", record.MemberActive, fresh)
			},
			upstreams:   upstreams{controller: ctrlRevA, pool: revB},
			wantRow:     RowWaiting,
			wantAnomaly: `waits for group "controller", which is stuck: its rollout 1 of the current revision ended INCOMPLETE`,
		},
		{
			// Row 8: a failed controller with rerun set is not stuck.
			name: "row8_failed_controller_with_rerun_not_stuck",
			setup: func(f *fixture) {
				g := groupDoc(lastDoc(1, ctrlRevA, record.OutcomeFailed))
				g.Rerun = true
				f.group(controllerKey, g)
				f.group(poolKey, groupDoc(nil))
				f.member(poolKey, "host-a", record.MemberActive, fresh)
			},
			upstreams: upstreams{controller: ctrlRevA, pool: revB},
			wantRow:   RowWaiting,
		},
		{
			// Row 8: an invalid controller is stuck.
			name: "row8_invalid_controller_is_stuck",
			setup: func(f *fixture) {
				f.put(controllerKey, store.Record{"tag": "latest"})
				f.group(poolKey, groupDoc(nil))
				f.member(poolKey, "host-a", record.MemberActive, fresh)
			},
			upstreams:   upstreams{controller: ctrlRevA, pool: revB},
			wantRow:     RowWaiting,
			wantAnomaly: `waits for group "controller", which is stuck: its records are invalid`,
		},
		{
			// Row 8: the controller waits while a pool has a rollout.
			name:  "row8_controller_waits_pool_has_rollout",
			group: controller,
			setup: func(f *fixture) {
				f.group(controllerKey, groupDoc(lastDoc(1, ctrlRevA, record.OutcomeSucceeded)))
				f.member(controllerKey, "ctrl-a", record.MemberActive, fresh)
				f.group(poolKey, groupDoc(nil))
				f.rollout(poolKey, 1, rolloutDoc(revA))
			},
			upstreams: upstreams{controller: ctrlRevB, pool: revA},
			wantRow:   RowWaiting,
		},
		{
			// Row 8: the controller waits while a pool is invalid.
			name:  "row8_controller_waits_pool_invalid",
			group: controller,
			setup: func(f *fixture) {
				f.group(controllerKey, groupDoc(nil))
				f.member(controllerKey, "ctrl-a", record.MemberActive, fresh)
				f.put(poolKey, store.Record{"tag": "latest"})
			},
			upstreams: upstreams{controller: ctrlRevB, pool: revA},
			wantRow:   RowWaiting,
		},
		{
			// Row 8a: a group with nobody online starts no rollout.
			name: "row8a_nobody_online_starts_nothing",
			setup: func(f *fixture) {
				f.group(poolKey, groupDoc(nil))
				f.member(poolKey, "host-a", record.MemberActive, stale)
				f.member(poolKey, "host-b", record.MemberOptedOut, fresh)
			},
			upstreams: upstreams{pool: revB},
			wantRow:   RowNoMember,
			wantRow0:  []write{update(record.MemberKey(poolKey, "host-a"), memberDoc(record.MemberOffline, stale).Record())},
		},
		{
			// Row 9: the first rollout, in a domain without a controller group.
			name: "row9_first_rollout_no_controller_group",
			setup: func(f *fixture) {
				f.group(poolKey, groupDoc(nil))
				f.member(poolKey, "host-a", record.MemberActive, fresh)
				f.member(poolKey, "host-b", record.MemberDown, fresh)
				f.member(poolKey, "host-c", record.MemberActive, fresh)
				f.member(poolKey, "host-d", record.MemberActive, stale)
				f.member(poolKey, "host-e", record.MemberOptedOut, fresh)
			},
			upstreams: upstreams{pool: revB},
			wantRow:   RowCreateRollout,
			wantRow0:  []write{update(record.MemberKey(poolKey, "host-d"), memberDoc(record.MemberOffline, stale).Record())},
			// Three members are online: 50% is 2, 100% is 3.
			wantSeq: []write{{
				Op:     "create",
				Key:    record.RolloutKey(poolKey, 1).String(),
				Record: record.Rollout{Revision: revB, Waves: []int64{1, 2, 3}, MaxInFlight: 2, MaxFailed: 1, CreatedAt: now}.Record(),
			}},
		},
		{
			// Row 9: a pool starts once the controller is settled.
			name: "row9_pool_starts_controller_settled",
			setup: func(f *fixture) {
				f.group(controllerKey, groupDoc(lastDoc(4, ctrlRevB, record.OutcomeSucceeded)))
				f.group(poolKey, groupDoc(lastDoc(7, revA, record.OutcomeSucceeded)))
				f.member(poolKey, "host-a", record.MemberActive, fresh)
			},
			upstreams: upstreams{controller: ctrlRevB, pool: revB},
			wantRow:   RowCreateRollout,
			wantSeq: []write{{
				Op:     "create",
				Key:    record.RolloutKey(poolKey, 8).String(),
				Record: record.Rollout{Revision: revB, Waves: []int64{1}, MaxInFlight: 2, MaxFailed: 1, CreatedAt: now}.Record(),
			}},
		},
		{
			// Row 9: rerun rolls out the last revision again.
			name: "row9_rerun_last_revision",
			setup: func(f *fixture) {
				g := groupDoc(lastDoc(1, revA, record.OutcomeFailed))
				g.Rerun = true
				f.group(poolKey, g)
				f.member(poolKey, "host-a", record.MemberActive, fresh)
			},
			upstreams: upstreams{pool: revA},
			wantRow:   RowCreateRollout,
			wantSeq: []write{{
				Op:     "create",
				Key:    record.RolloutKey(poolKey, 2).String(),
				Record: record.Rollout{Revision: revA, Waves: []int64{1}, MaxInFlight: 2, MaxFailed: 1, CreatedAt: now}.Record(),
			}},
		},
		{
			// Row 9: the controller starts when no pool has a rollout.
			name:  "row9_controller_starts_no_pool_rollout",
			group: controller,
			setup: func(f *fixture) {
				f.group(controllerKey, groupDoc(lastDoc(1, ctrlRevA, record.OutcomeSucceeded)))
				f.member(controllerKey, "ctrl-a", record.MemberActive, fresh)
				f.group(poolKey, groupDoc(nil))
			},
			upstreams: upstreams{controller: ctrlRevB},
			wantRow:   RowCreateRollout,
			wantSeq: []write{{
				Op:     "create",
				Key:    record.RolloutKey(controllerKey, 2).String(),
				Record: record.Rollout{Revision: ctrlRevB, Waves: []int64{1}, MaxInFlight: 2, MaxFailed: 1, CreatedAt: now}.Record(),
			}},
		},
		{
			// Invalid group: nothing is written, not even OFFLINE marks.
			name: "invalid_group_nothing_written",
			setup: func(f *fixture) {
				g := groupDoc(nil)
				g.Cancel = true
				f.group(poolKey, g)
				f.member(poolKey, "host-a", record.MemberActive, stale)
				f.put(record.MemberKey(poolKey, "host-b"), store.Record{"status": "SLEEPING"})
			},
			upstreams:   upstreams{pool: revB},
			wantRow:     RowInvalid,
			wantAnomaly: `group "dda" has invalid records`,
		},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			f := newFixture(t, tc.paused)
			tc.setup(f)
			d, err := read(context.Background(), f.s, tc.upstreams)
			if err != nil {
				t.Fatalf("reading the fixture failed: %v", err)
			}
			groupID := tc.group
			if groupID == "" {
				groupID = pool
			}

			decisions := Decide(d, now)

			if len(decisions) != len(d.Groups) {
				t.Fatalf("Decide() returned %d decisions for %d groups", len(decisions), len(d.Groups))
			}
			var got Decision
			for i, dec := range decisions {
				if dec.GroupID != d.Groups[i].ID {
					t.Errorf("Decide() decision %d is for group %q, want %q", i, dec.GroupID, d.Groups[i].ID)
				}
				if dec.GroupID == groupID {
					got = dec
				}
			}
			if got.Row != tc.wantRow {
				t.Errorf("Decide() matched row %q for group %q, want %q", got.Row, groupID, tc.wantRow)
			}
			if len(got.Writes.Row) > 1 {
				t.Errorf("Decide() returned %d row sequences for group %q, want at most 1", len(got.Writes.Row), groupID)
			}
			var gotSeq []write
			for _, seq := range got.Writes.Row {
				gotSeq = append(gotSeq, writesOf(seq)...)
			}
			if diff := cmp.Diff(tc.wantSeq, gotSeq); diff != "" {
				t.Errorf("Decide() row sequence for group %q (-want +got):\n%s", groupID, diff)
			}
			var gotRow0 []write
			for _, seq := range got.Writes.Bookkeeping {
				if len(seq) != 1 {
					t.Errorf("Decide() returned a bookkeeping sequence of %d writes, want 1 write per sequence", len(seq))
				}
				gotRow0 = append(gotRow0, writesOf(seq)...)
			}
			if diff := cmp.Diff(tc.wantRow0, gotRow0); diff != "" {
				t.Errorf("Decide() bookkeeping for group %q (-want +got):\n%s", groupID, diff)
			}
			switch {
			case tc.wantAnomaly == "" && len(got.Anomalies) != 0:
				t.Errorf("Decide() anomalies for group %q = %q, want none", groupID, got.Anomalies)
			case tc.wantAnomaly != "" && (len(got.Anomalies) != 1 || !strings.Contains(got.Anomalies[0], tc.wantAnomaly)):
				t.Errorf("Decide() anomalies for group %q = %q, want one containing %q", groupID, got.Anomalies, tc.wantAnomaly)
			}
		})
	}
}

// TestDecide_WritesExpectVersionsRead checks that every conditional write
// carries the version of the record as it was read, by applying a decision
// after the records changed: every write must be refused.
func TestDecide_WritesExpectVersionsRead(t *testing.T) {
	ctx := context.Background()
	f := newFixture(t, false)
	g := groupDoc(lastDoc(1, revA, record.OutcomeSucceeded))
	f.group(poolKey, g)
	f.member(poolKey, "host-a", record.MemberActive, stale)
	f.rollout(poolKey, 2, concluded(rolloutDoc(revB), record.OutcomeSucceeded, true))
	d, err := read(ctx, f.s, upstreams{pool: revB})
	if err != nil {
		t.Fatalf("reading the fixture failed: %v", err)
	}
	dec := Decide(d, now)[0]
	// Another writer changes the group and the member after the snapshot: a
	// human requests a rerun, and the agent heartbeats.
	g.Rerun = true
	touch(t, f.s, poolKey, g.Record())
	touch(t, f.s, record.MemberKey(poolKey, "host-a"), memberDoc(record.MemberActive, 0).Record())

	res := apply.Apply(ctx, f.s, []apply.Decision{dec.Writes}, apply.Options{})

	// The archive stops at its first step, so the rollout is not deleted, and
	// the OFFLINE mark loses to the heartbeat.
	if res.Attempted != 2 || len(res.Errs) != 2 {
		t.Fatalf("apply.Apply() = %+v, want 2 writes attempted and both refused", res)
	}
	for _, err := range res.Errs {
		if !errors.Is(err, store.ErrVersionMismatch) {
			t.Errorf("apply.Apply() error = %v, want it to wrap %v", err, store.ErrVersionMismatch)
		}
	}
	if _, _, err := f.s.Get(ctx, record.RolloutKey(poolKey, 2)); err != nil {
		t.Errorf("Get() of the rollout after a refused archive failed: %v", err)
	}
}

// touch replaces the record at key, giving it a new version.
func touch(t *testing.T, s *fake.Store, key store.Key, r store.Record) {
	t.Helper()
	ctx := context.Background()
	_, v, err := s.Get(ctx, key)
	if err != nil {
		t.Fatalf("Get(%q) failed: %v", key, err)
	}
	if _, err := s.Update(ctx, key, r, v); err != nil {
		t.Fatalf("Update(%q) failed: %v", key, err)
	}
}

func TestResolveWaves(t *testing.T) {
	count := func(n int64) record.Wave { return record.Wave{Value: n} }
	percent := func(p int64) record.Wave { return record.Wave{Value: p, Percent: true} }
	tests := []struct {
		name  string
		waves []record.Wave
		n     int64
		want  []int64
	}{
		{
			name:  "concepts_brief_example",
			waves: []record.Wave{count(1), count(3), percent(5), percent(15), percent(50), percent(100)},
			n:     40,
			want:  []int64{1, 3, 6, 20, 40},
		},
		{
			name:  "no_gating",
			waves: []record.Wave{percent(100)},
			n:     7,
			want:  []int64{7},
		},
		{
			name:  "percentage_rounds_up",
			waves: []record.Wave{percent(1), percent(34), percent(100)},
			n:     3,
			want:  []int64{1, 2, 3},
		},
		{
			name:  "small_pool_skips_redundant_waves",
			waves: []record.Wave{count(1), count(3), percent(5), percent(15), percent(50), percent(100)},
			n:     1,
			want:  []int64{1, 3},
		},
		{
			// A count larger than the group stays as it is.
			name:  "count_larger_than_group_kept",
			waves: []record.Wave{count(1), count(3), count(10)},
			n:     2,
			want:  []int64{1, 3, 10},
		},
		{
			// An entry below the one before it is raised to it and dropped.
			name:  "entry_below_previous_raised_and_dropped",
			waves: []record.Wave{count(5), count(2), percent(100)},
			n:     8,
			want:  []int64{5, 8},
		},
		{
			name:  "equal_entries_are_dropped",
			waves: []record.Wave{count(2), count(2), percent(100), percent(100)},
			n:     2,
			want:  []int64{2},
		},
		{
			name:  "large_group",
			waves: []record.Wave{percent(1), percent(100)},
			n:     1000,
			want:  []int64{10, 1000},
		},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			got := resolveWaves(tc.waves, tc.n)

			if diff := cmp.Diff(tc.want, got); diff != "" {
				t.Errorf("resolveWaves(%v, %d) (-want +got):\n%s", tc.waves, tc.n, diff)
			}
		})
	}
}

// step returns the manager pass as an applytest.Step: read one snapshot,
// decide at the fixed now.
func step(up upstreams) applytest.Step {
	return func(ctx context.Context, s store.Store) ([]apply.Decision, error) {
		d, err := read(ctx, s, up)
		if err != nil {
			return nil, err
		}
		var writes []apply.Decision
		for _, dec := range Decide(d, now) {
			writes = append(writes, dec.Writes)
		}
		return writes, nil
	}
}

// reconcilerCases are stored states from which the manager pass, on its own,
// has several writes to make over more than one run.
var reconcilerCases = []struct {
	name      string
	setup     func(f *fixture)
	upstreams upstreams
	// check inspects the fixpoint.
	check func(t *testing.T, d view.Domain)
}{
	{
		name: "archive_then_next_rollout",
		setup: func(f *fixture) {
			g := groupDoc(lastDoc(1, ctrlRevA, record.OutcomeSucceeded))
			g.Rerun, g.Cancel = true, true
			f.group(controllerKey, g)
			f.rollout(controllerKey, 2, concluded(rolloutDoc(ctrlRevA), record.OutcomeIncomplete, true))
			f.member(controllerKey, "ctrl-a", record.MemberActive, fresh)
			f.member(controllerKey, "ctrl-b", record.MemberActive, stale)
			f.member(controllerKey, "ctrl-c", record.MemberDown, stale)
			p := groupDoc(nil)
			p.Cancel = true
			f.group(poolKey, p)
			f.member(poolKey, "host-a", record.MemberActive, fresh)
			f.member(poolKey, "host-b", record.MemberActive, stale)
		},
		upstreams: upstreams{controller: ctrlRevB, pool: revB},
		check: func(t *testing.T, d view.Domain) {
			c, p := d.Groups[0], d.Groups[1]
			if last := c.Record.LastRollout; last == nil || last.Seq != 2 || last.Outcome != record.OutcomeIncomplete || c.Record.Rerun || c.Record.Cancel {
				t.Errorf("controller group at the fixpoint = %+v, want rollout 2 archived as INCOMPLETE and both requests cleared", c.Record)
			}
			if c.Rollout == nil || c.Rollout.Seq != 3 || !c.Rollout.Record.Revision.Equal(ctrlRevB) {
				t.Errorf("controller rollout at the fixpoint = %+v, want rollout 3 of %v", c.Rollout, ctrlRevB)
			}
			if p.Rollout != nil || p.Record.Cancel {
				t.Errorf("pool at the fixpoint: has rollout = %t, cancel = %t; want it waiting for the controller with the cancel cleared", p.Rollout != nil, p.Record.Cancel)
			}
			for _, g := range d.Groups {
				for _, m := range g.Members {
					if want := m.ID == "ctrl-a" || m.ID == "host-a"; (m.Record.Status != record.MemberOffline) != want {
						t.Errorf("member %q at the fixpoint has status %q, want OFFLINE only for the stale members", m.ID, m.Record.Status)
					}
				}
			}
		},
	},
	{
		// Archive interrupted between its steps, with a rerun requested since.
		name: "archive_interrupted_with_rerun",
		setup: func(f *fixture) {
			g := groupDoc(lastDoc(2, revA, record.OutcomeFailed))
			g.Rerun = true
			f.group(poolKey, g)
			f.rollout(poolKey, 2, concluded(rolloutDoc(revA), record.OutcomeFailed, true))
			f.member(poolKey, "host-a", record.MemberActive, fresh)
		},
		upstreams: upstreams{pool: revA},
		check: func(t *testing.T, d view.Domain) {
			p := d.Groups[0]
			if !p.Record.Rerun || p.Rollout == nil || p.Rollout.Seq != 3 || !p.Rollout.Record.Revision.Equal(revA) {
				t.Errorf("pool at the fixpoint: rerun = %t, rollout = %+v; want the rerun kept and rollout 3 of %v", p.Record.Rerun, p.Rollout, revA)
			}
		},
	},
	{
		// Supersede, and a cancel that is cleared in another group.
		name: "supersede_and_cancel_cleared_elsewhere",
		setup: func(f *fixture) {
			f.group(poolKey, groupDoc(nil))
			f.rollout(poolKey, 1, rolloutDoc(revA))
			f.member(poolKey, "host-a", record.MemberDown, 3*stale)
			other := groupDoc(lastDoc(5, revA, record.OutcomeSucceeded))
			other.Cancel = true
			f.group(record.GroupKey(domainID, "public_testing"), other)
		},
		upstreams: upstreams{pool: revB, "public_testing": revA},
		check: func(t *testing.T, d view.Domain) {
			p, other := d.Groups[0], d.Groups[1]
			if p.Rollout == nil || p.Rollout.Record.StopRequest != record.StopSuperseded {
				t.Errorf("pool rollout at the fixpoint = %+v, want stop_request SUPERSEDED", p.Rollout)
			}
			if other.Record.Cancel {
				t.Errorf("group %q at the fixpoint still has cancel set", other.ID)
			}
		},
	},
}

func TestDecide_RunsToFixpoint(t *testing.T) {
	for _, tc := range reconcilerCases {
		t.Run(tc.name, func(t *testing.T) {
			ctx := context.Background()
			f := newFixture(t, false)
			tc.setup(f)

			applytest.RunToFixpoint(ctx, t, f.s, step(tc.upstreams), 4)

			d, err := read(ctx, f.s, tc.upstreams)
			if err != nil {
				t.Fatalf("reading the fixpoint failed: %v", err)
			}
			for _, g := range d.Groups {
				if g.Err != nil {
					t.Fatalf("group %q is invalid at the fixpoint: %v", g.ID, g.Err)
				}
			}
			tc.check(t, d)
		})
	}
}

func TestDecide_SurvivesCrashes(t *testing.T) {
	for _, tc := range reconcilerCases {
		t.Run(tc.name, func(t *testing.T) {
			seed := func() *fake.Store {
				f := newFixture(t, false)
				tc.setup(f)
				return f.s
			}

			applytest.CheckCrashes(context.Background(), t, seed, step(tc.upstreams), 4)
		})
	}
}
