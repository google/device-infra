package orchestrator

import (
	"context"
	"errors"
	"slices"
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
	domainID = "mtt.example.com"
	pool     = "dda"

	fresh = time.Minute // age of a heartbeat or a renewal that is still good
	// stale is the age at which a heartbeat is stale and a lease is expired.
	stale = record.HeartbeatTimeout
)

var (
	now     = time.Date(2026, 11, 12, 8, 0, 0, 0, time.UTC)
	earlier = now.Add(-time.Hour)
	rev     = record.Revision{Build: "repo/img@sha256:aaa", TenantConfigVersion: "t1", PoolConfigVersion: "p1"}

	poolKey    = record.GroupKey(domainID, pool)
	rolloutKey = record.RolloutKey(poolKey, 1)
)

// Summary entries, under the names the summary uses.
const (
	done       = record.SummaryEntry(record.TaskDone)
	failed     = record.SummaryEntry(record.TaskFailed)
	skipped    = record.SummaryEntry(record.TaskSkipped)
	lost       = record.SummaryEntry(record.TaskLost)
	offline    = record.SummaryOffline
	notStarted = record.SummaryNotStarted
)

type summary = map[string]record.SummaryEntry

// rolloutOpt changes one field of a rollout built by rollout.
type rolloutOpt func(*record.Rollout)

func stop(request record.StopRequest) rolloutOpt {
	return func(ro *record.Rollout) { ro.StopRequest = request }
}

func maxFailed(n int64) rolloutOpt { return func(ro *record.Rollout) { ro.MaxFailed = n } }

func maxInFlight(n int64) rolloutOpt { return func(ro *record.Rollout) { ro.MaxInFlight = n } }

func wave(index int64) rolloutOpt { return func(ro *record.Rollout) { ro.WaveIndex = index } }

// concluded sets the fields that the conclude writes together.
func concluded(outcome record.Outcome, at time.Time, s summary) rolloutOpt {
	return func(ro *record.Rollout) { ro.Outcome, ro.FinishedAt, ro.Summary = outcome, at, s }
}

func finished() rolloutOpt { return func(ro *record.Rollout) { ro.Finished = true } }

// rollout returns an IN_PROGRESS rollout on its first wave, with max_failed 0
// and no max_in_flight, changed by opts.
func rollout(waves []int64, opts ...rolloutOpt) record.Rollout {
	ro := record.Rollout{Revision: rev, Waves: waves, CreatedAt: earlier}
	for _, opt := range opts {
		opt(&ro)
	}
	return ro
}

// member returns a member whose last heartbeat is age old.
func member(status record.MemberStatus, age time.Duration) record.Member {
	return record.Member{Status: status, HeartbeatAt: now.Add(-age), RunningRevision: rev}
}

func openSlot() record.Slot { return record.Slot{State: record.SlotOpen} }

func closedSlot() record.Slot { return record.Slot{State: record.SlotClosed} }

// task returns a slot claimed by agent, with the given result and a lease
// that was last renewed age ago.
func task(agent string, result record.TaskResult, age time.Duration) record.Slot {
	s := record.Slot{State: record.SlotClaimed, AgentID: agent, ClaimedAt: earlier, RenewedAt: now.Add(-age), Result: result}
	if result == record.TaskFailed {
		s.Reason = record.ReasonNotConverged
	}
	return s
}

// downAfterDone returns a DONE task of agent with down_after_done_at set at
// the given time.
func downAfterDone(agent string, at time.Time) record.Slot {
	s := task(agent, record.TaskDone, fresh)
	s.DownAfterDoneAt = at
	return s
}

// scenario is the stored state of one group with a rollout.
type scenario struct {
	rollout record.Rollout
	slots   map[int]record.Slot
	members map[string]record.Member
}

func put(t *testing.T, s *fake.Store, key store.Key, r store.Record) {
	t.Helper()
	if _, err := s.Create(context.Background(), key, r); err != nil {
		t.Fatalf("Create(%q) failed: %v", key, err)
	}
}

// newStore returns a store that holds the domain document.
func newStore(t *testing.T, paused bool) *fake.Store {
	t.Helper()
	s := fake.New()
	put(t, s, record.DomainKey(domainID), record.Domain{Paused: paused}.Record())
	return s
}

// putIn stores the scenario as the group with the given ID: the group, its
// rollout with sequence number 1, the slots, and the members.
func (sc scenario) putIn(t *testing.T, s *fake.Store, groupID string) {
	t.Helper()
	group := record.GroupKey(domainID, groupID)
	put(t, s, group, record.Group{
		Image:  "repo/img",
		Tag:    "latest",
		Policy: record.Policy{Waves: []record.Wave{{Value: 100, Percent: true}}},
	}.Record())
	ro := record.RolloutKey(group, 1)
	put(t, s, ro, sc.rollout.Record())
	for n, slot := range sc.slots {
		put(t, s, record.SlotKey(ro, n), slot.Record())
	}
	for id, m := range sc.members {
		put(t, s, record.MemberKey(group, id), m.Record())
	}
}

// read returns the view of the domain through one snapshot.
func read(ctx context.Context, s store.Store) (view.Domain, error) {
	snap, err := s.Snapshot(ctx)
	if err != nil {
		return view.Domain{}, err
	}
	defer snap.Close(ctx)
	return view.Read(ctx, snap, domainID)
}

// viewOf stores sc as the only group of a domain and returns its view.
func viewOf(t *testing.T, sc scenario, paused bool) (*fake.Store, RolloutView) {
	t.Helper()
	s := newStore(t, paused)
	sc.putIn(t, s, pool)
	d, err := read(context.Background(), s)
	if err != nil {
		t.Fatalf("reading the scenario failed: %v", err)
	}
	views := Views(d)
	if len(views) != 1 {
		t.Fatalf("Views() returned %d views, want 1 (group error: %v)", len(views), d.Groups[0].Err)
	}
	return s, views[0]
}

// write is an apply.Write in a form that cmp can compare: without the
// expected version, which is opaque.
type write struct {
	Op     string
	Key    string
	Record store.Record
}

// writesOf flattens sequences that must hold one write each.
func writesOf(t *testing.T, kind string, seqs []apply.Sequence) []write {
	t.Helper()
	var ws []write
	for _, seq := range seqs {
		if len(seq) != 1 {
			t.Errorf("Decide() returned a %s sequence of %d writes, want 1 write per sequence", kind, len(seq))
		}
		for _, w := range seq {
			ws = append(ws, write{Op: w.Op.String(), Key: w.Key.String(), Record: w.Record})
		}
	}
	return ws
}

func slotKey(n int) store.Key { return record.SlotKey(rolloutKey, n) }

// slotUpdate is the expected update of slot n to the given content.
func slotUpdate(n int, s record.Slot) write {
	return write{Op: "update", Key: slotKey(n).String(), Record: s.Record()}
}

// rolloutUpdate is the expected update of the rollout to the given content.
func rolloutUpdate(ro record.Rollout) write {
	return write{Op: "update", Key: rolloutKey.String(), Record: ro.Record()}
}

func slotCreate(n int) write {
	return write{Op: "create", Key: slotKey(n).String(), Record: openSlot().Record()}
}

func slotDelete(n int) write {
	return write{Op: "delete", Key: slotKey(n).String()}
}

func TestDecide_Rows(t *testing.T) {
	tests := []struct {
		name    string
		paused  bool
		rollout record.Rollout
		slots   map[int]record.Slot
		members map[string]record.Member

		wantRow     Row
		wantWrites  []write // the row's sequences, one write each
		wantMarks   []write // bookkeeping, one write each
		wantCleanup []write // cleanup, one write each
		// wantAnomaly is a substring of the single anomaly expected; empty
		// for none.
		wantAnomaly string
	}{
		{
			// Row 0: the slots of a CONCLUDED rollout are deleted as cleanup, and nothing else is evaluated.
			name:    "row0_concluded_slots_deleted",
			rollout: rollout([]int64{2}, stop(record.StopCancelled), concluded(record.OutcomeCancelled, earlier, nil)),
			slots: map[int]record.Slot{
				0: closedSlot(),
				1: task("host-a", record.TaskDone, fresh),     // its host is down: no down_after_done_at mark
				3: task("ghost", record.TaskRunning, 2*stale), // no member row, expired: no anomaly, no LOST mark
			},
			members:     map[string]record.Member{"host-a": member(record.MemberDown, fresh)},
			wantRow:     RowCleanup,
			wantCleanup: []write{slotDelete(0), slotDelete(1), slotDelete(3)},
		},
		{
			// Row 0: a CONCLUDED rollout without slots is finished.
			name:    "row0_concluded_without_slots_finished",
			rollout: rollout([]int64{2}, concluded(record.OutcomeSucceeded, earlier, summary{"host-a": done})),
			members: map[string]record.Member{"host-a": member(record.MemberActive, fresh)},
			wantRow: RowCleanup,
			wantWrites: []write{
				rolloutUpdate(rollout([]int64{2}, concluded(record.OutcomeSucceeded, earlier, summary{"host-a": done}), finished())),
			},
		},
		{
			// A FINISHED rollout is left to the manager.
			name:    "finished_left_to_manager",
			rollout: rollout([]int64{2}, concluded(record.OutcomeSucceeded, earlier, nil), finished()),
			members: map[string]record.Member{"host-a": member(record.MemberActive, stale)},
			wantRow: RowFinished,
		},
		{
			// Row 1: an expired task is marked LOST and a DONE task whose host is down is recorded.
			name:    "row1_expired_lost_and_done_host_down",
			rollout: rollout([]int64{5}, maxFailed(2)),
			slots: map[int]record.Slot{
				0: task("host-a", record.TaskRunning, stale),             // expired
				1: task("host-b", record.TaskRunning, stale-time.Second), // not expired
				2: task("host-c", record.TaskDone, fresh),                // host down
				3: downAfterDone("host-d", earlier),                      // recorded already
				4: task("host-e", record.TaskDone, fresh),                // DOWN but silent: not down
			},
			members: map[string]record.Member{
				"host-a": member(record.MemberActive, stale),
				"host-b": member(record.MemberActive, fresh),
				"host-c": member(record.MemberDown, fresh),
				"host-d": member(record.MemberActive, fresh),
				"host-e": member(record.MemberDown, stale),
			},
			// host-b is still running and nobody is unclaimed: row 5 has
			// nothing to open.
			wantRow: RowOpen,
			wantMarks: []write{
				slotUpdate(0, task("host-a", record.TaskLost, stale)),
				slotUpdate(2, downAfterDone("host-c", now)),
			},
		},
		{
			// Row 2: a stop request closes every OPEN slot, also while paused.
			name:    "row2_stop_request_closes_open_slots",
			paused:  true,
			rollout: rollout([]int64{5}, stop(record.StopCancelled)),
			slots: map[int]record.Slot{
				0: openSlot(),
				1: task("host-a", record.TaskRunning, fresh),
				2: openSlot(),
				3: closedSlot(),
			},
			members: map[string]record.Member{
				"host-a": member(record.MemberActive, fresh),
				"host-b": member(record.MemberActive, fresh),
			},
			wantRow:    RowStop,
			wantWrites: []write{slotUpdate(0, closedSlot()), slotUpdate(2, closedSlot())},
		},
		{
			// Row 2: a stopped rollout waits for the task in flight.
			name:    "row2_stopped_waits_for_in_flight",
			rollout: rollout([]int64{5}, stop(record.StopSuperseded)),
			slots: map[int]record.Slot{
				0: closedSlot(),
				1: task("host-a", record.TaskRunning, fresh),
			},
			members: map[string]record.Member{"host-a": member(record.MemberActive, fresh)},
			wantRow: RowStop,
		},
		{
			// Row 2: an expired task is in flight until its LOST mark has landed.
			name:    "row2_expired_task_still_in_flight",
			rollout: rollout([]int64{5}, stop(record.StopSuperseded)),
			slots:   map[int]record.Slot{0: task("host-a", record.TaskRunning, 2*stale)},
			members: map[string]record.Member{"host-a": member(record.MemberActive, 2*stale)},
			wantRow: RowStop,
			wantMarks: []write{
				slotUpdate(0, task("host-a", record.TaskLost, 2*stale)),
			},
		},
		{
			// Row 2: with no OPEN slot and no task in flight the outcome and the summary are written.
			name:    "row2_outcome_and_summary_written",
			rollout: rollout([]int64{20}, stop(record.StopCancelled)),
			slots: map[int]record.Slot{
				0: task("done", record.TaskDone, fresh),
				1: task("failed", record.TaskFailed, fresh),
				2: task("down", record.TaskDone, fresh),
				3: downAfterDone("recorded", earlier),
				4: task("lost", record.TaskLost, 2*stale),
				5: task("skipped", record.TaskSkipped, fresh),
				6: task("retried", record.TaskLost, 2*stale),
				7: task("retried", record.TaskDone, fresh),
				8: task("left", record.TaskDone, fresh),
				9: closedSlot(),
			},
			members: map[string]record.Member{
				"done":     member(record.MemberActive, fresh),
				"failed":   member(record.MemberActive, fresh),
				"down":     member(record.MemberDown, fresh),   // DONE, then DOWN: failed
				"recorded": member(record.MemberActive, fresh), // back up, but recorded as down after done
				"lost":     member(record.MemberOffline, 2*stale),
				"skipped":  member(record.MemberDown, fresh),
				"retried":  member(record.MemberActive, fresh),   // a LOST slot, then a new task
				"left":     member(record.MemberOptedOut, fresh), // claimed, then opted out
				"idle":     member(record.MemberActive, fresh),   // never had its turn
				"silent":   member(record.MemberActive, stale),   // not yet marked OFFLINE
				"marked":   member(record.MemberOffline, 2*stale),
				"out":      member(record.MemberOptedOut, fresh), // never claimed: no entry
			},
			wantRow: RowStop,
			// A stop request names the outcome even though failures also
			// exceed max_failed.
			wantWrites: []write{
				rolloutUpdate(rollout([]int64{20}, stop(record.StopCancelled), concluded(record.OutcomeCancelled, now, summary{
					"done":     done,
					"failed":   failed,
					"down":     failed,
					"recorded": failed,
					"lost":     lost,
					"skipped":  skipped,
					"retried":  done,
					"left":     done,
					"idle":     notStarted,
					"silent":   offline,
					"marked":   offline,
				}))),
			},
			wantMarks: []write{slotUpdate(2, downAfterDone("down", now))},
		},
		{
			// Row 2: more failures than max_failed close every OPEN slot.
			name:    "row2_max_failed_closes_open_slots",
			rollout: rollout([]int64{5}),
			slots: map[int]record.Slot{
				0: task("host-a", record.TaskFailed, fresh),
				1: openSlot(),
			},
			members: map[string]record.Member{
				"host-a": member(record.MemberActive, fresh),
				"host-b": member(record.MemberActive, fresh),
			},
			wantRow:    RowStop,
			wantWrites: []write{slotUpdate(1, closedSlot())},
		},
		{
			// Row 2: more failures than max_failed conclude FAILED.
			name:    "row2_max_failed_concludes_failed",
			rollout: rollout([]int64{5}, maxFailed(1)),
			slots: map[int]record.Slot{
				0: task("host-a", record.TaskFailed, fresh),
				1: task("host-b", record.TaskDone, fresh),
			},
			members: map[string]record.Member{
				"host-a": member(record.MemberActive, fresh),
				"host-b": member(record.MemberDown, fresh),
				"host-c": member(record.MemberActive, fresh),
			},
			wantRow: RowStop,
			wantWrites: []write{
				rolloutUpdate(rollout([]int64{5}, maxFailed(1), concluded(record.OutcomeFailed, now, summary{
					"host-a": failed,
					"host-b": failed,
					"host-c": notStarted,
				}))),
			},
			wantMarks: []write{slotUpdate(1, downAfterDone("host-b", now))},
		},
		{
			// Row 2 does not apply while failures are within max_failed.
			name:    "row2_not_within_max_failed",
			rollout: rollout([]int64{5}, maxFailed(1)),
			slots:   map[int]record.Slot{0: task("host-a", record.TaskFailed, fresh)},
			members: map[string]record.Member{
				"host-a": member(record.MemberActive, fresh),
				"host-b": member(record.MemberActive, fresh),
			},
			wantRow:    RowOpen,
			wantWrites: []write{slotCreate(1)},
		},
		{
			// Row 3: every online agent has a result, so the leftover OPEN slot is closed.
			name:    "row3_all_reported_closes_leftover_slot",
			rollout: rollout([]int64{5}),
			slots: map[int]record.Slot{
				0: task("host-a", record.TaskDone, fresh),
				1: openSlot(),
			},
			members: map[string]record.Member{
				"host-a": member(record.MemberActive, fresh),
				"host-b": member(record.MemberActive, stale),
			},
			wantRow:    RowComplete,
			wantWrites: []write{slotUpdate(1, closedSlot())},
		},
		{
			// Row 3: the running task of an agent that opted out is waited for.
			name:    "row3_waits_for_opted_out_running_task",
			rollout: rollout([]int64{5}),
			slots: map[int]record.Slot{
				0: task("host-a", record.TaskDone, fresh),
				1: task("host-b", record.TaskRunning, fresh),
			},
			members: map[string]record.Member{
				"host-a": member(record.MemberActive, fresh),
				"host-b": member(record.MemberOptedOut, fresh),
			},
			wantRow: RowComplete,
		},
		{
			// Row 3: SUCCEEDED when every agent attempted the upgrade and reported a result, a failure within max_failed included.
			name:    "row3_succeeded_all_attempted",
			rollout: rollout([]int64{5}, maxFailed(1)),
			slots: map[int]record.Slot{
				0: task("host-a", record.TaskDone, fresh),
				1: task("host-b", record.TaskFailed, fresh),
				2: closedSlot(),
			},
			members: map[string]record.Member{
				"host-a": member(record.MemberActive, fresh),
				"host-b": member(record.MemberActive, fresh),
				"host-c": member(record.MemberOptedOut, fresh),
			},
			wantRow: RowComplete,
			wantWrites: []write{
				rolloutUpdate(rollout([]int64{5}, maxFailed(1), concluded(record.OutcomeSucceeded, now, summary{
					"host-a": done,
					"host-b": failed,
				}))),
			},
		},
		{
			// Row 3: INCOMPLETE when an agent is offline.
			name:    "row3_incomplete_agent_offline",
			rollout: rollout([]int64{5}),
			slots:   map[int]record.Slot{0: task("host-a", record.TaskDone, fresh)},
			members: map[string]record.Member{
				"host-a": member(record.MemberActive, fresh),
				"host-b": member(record.MemberActive, stale),
			},
			wantRow: RowComplete,
			wantWrites: []write{
				rolloutUpdate(rollout([]int64{5}, concluded(record.OutcomeIncomplete, now, summary{"host-a": done, "host-b": offline}))),
			},
		},
		{
			// Row 3: INCOMPLETE when an agent lost its lease and did not come back.
			name:    "row3_incomplete_agent_lost",
			rollout: rollout([]int64{5}),
			slots: map[int]record.Slot{
				0: task("host-a", record.TaskDone, fresh),
				1: task("host-b", record.TaskLost, 2*stale),
			},
			members: map[string]record.Member{
				"host-a": member(record.MemberActive, fresh),
				"host-b": member(record.MemberOffline, 2*stale),
			},
			wantRow: RowComplete,
			wantWrites: []write{
				rolloutUpdate(rollout([]int64{5}, concluded(record.OutcomeIncomplete, now, summary{"host-a": done, "host-b": lost}))),
			},
		},
		{
			// Row 3: INCOMPLETE when an agent skipped because its core service was not running.
			name:    "row3_incomplete_agent_skipped",
			rollout: rollout([]int64{1}),
			slots:   map[int]record.Slot{0: task("host-a", record.TaskSkipped, fresh)},
			members: map[string]record.Member{"host-a": member(record.MemberDown, fresh)},
			wantRow: RowComplete,
			wantWrites: []write{
				rolloutUpdate(rollout([]int64{1}, concluded(record.OutcomeIncomplete, now, summary{"host-a": skipped}))),
			},
		},
		{
			// Row 3 does not apply with no agent online: the rollout does not conclude.
			name:    "row3_not_with_nobody_online",
			rollout: rollout([]int64{5}),
			slots:   map[int]record.Slot{0: task("host-a", record.TaskDone, fresh)},
			members: map[string]record.Member{
				"host-a": member(record.MemberActive, stale),
				"host-b": member(record.MemberOffline, 2*stale),
			},
			wantRow: RowOpen,
		},
		{
			// Row 3a: while paused every OPEN slot is closed and the wave does not advance.
			name:    "row3a_paused_closes_open_slots",
			paused:  true,
			rollout: rollout([]int64{1, 5}),
			slots: map[int]record.Slot{
				0: task("host-a", record.TaskDone, fresh),
				1: openSlot(),
				2: task("host-b", record.TaskRunning, fresh),
			},
			members: map[string]record.Member{
				"host-a": member(record.MemberActive, fresh),
				"host-b": member(record.MemberActive, fresh),
				"host-c": member(record.MemberActive, fresh),
			},
			wantRow:    RowPaused,
			wantWrites: []write{slotUpdate(1, closedSlot())},
		},
		{
			// Row 4: the wave advances on the results agents reported, and no slot is opened in the same run.
			name:    "row4_wave_advances_opens_nothing",
			rollout: rollout([]int64{2, 5}),
			slots: map[int]record.Slot{
				0: task("host-a", record.TaskDone, fresh),
				1: task("host-b", record.TaskSkipped, fresh),
				2: task("host-c", record.TaskLost, 2*stale),
			},
			members: map[string]record.Member{
				"host-a": member(record.MemberActive, fresh),
				"host-b": member(record.MemberDown, fresh),
				"host-c": member(record.MemberActive, stale),
				"host-d": member(record.MemberActive, fresh),
				"host-e": member(record.MemberActive, fresh),
			},
			wantRow:    RowAdvance,
			wantWrites: []write{rolloutUpdate(rollout([]int64{2, 5}, wave(1)))},
		},
		{
			// Row 5: a LOST task does not advance the wave, and its agent gets a new slot.
			name:    "row5_lost_task_gets_new_slot",
			rollout: rollout([]int64{2, 5}),
			slots: map[int]record.Slot{
				0: task("host-a", record.TaskDone, fresh),
				1: task("host-b", record.TaskLost, 2*stale),
			},
			members: map[string]record.Member{
				"host-a": member(record.MemberActive, fresh),
				"host-b": member(record.MemberActive, fresh),
				"host-c": member(record.MemberActive, fresh),
			},
			// Two agents are unclaimed, but the wave has one place left.
			wantRow:    RowOpen,
			wantWrites: []write{slotCreate(2)},
		},
		{
			// Row 5: the first wave opens its slots from index 0.
			name:    "row5_first_wave_from_index_0",
			rollout: rollout([]int64{2, 3}),
			members: map[string]record.Member{
				"host-a": member(record.MemberActive, fresh),
				"host-b": member(record.MemberDown, fresh),
				"host-c": member(record.MemberActive, fresh),
				"host-d": member(record.MemberOptedOut, fresh),
			},
			wantRow:    RowOpen,
			wantWrites: []write{slotCreate(0), slotCreate(1)},
		},
		{
			// Row 5: new slots follow the highest index, within max_in_flight.
			name:    "row5_new_slots_after_highest_index",
			rollout: rollout([]int64{10}, maxInFlight(3)),
			slots: map[int]record.Slot{
				0: closedSlot(),
				4: task("host-a", record.TaskRunning, fresh),
				7: openSlot(),
			},
			members: map[string]record.Member{
				"host-a": member(record.MemberActive, fresh),
				"host-b": member(record.MemberActive, fresh),
				"host-c": member(record.MemberActive, fresh),
				"host-d": member(record.MemberActive, fresh),
				"host-e": member(record.MemberActive, fresh),
			},
			// max_in_flight leaves room for two OPEN slots; one exists.
			wantRow:    RowOpen,
			wantWrites: []write{slotCreate(8)},
		},
		{
			// Row 5 never closes a slot, even with more OPEN slots than the target.
			name:    "row5_never_closes_slot",
			rollout: rollout([]int64{10}, maxInFlight(1)),
			slots:   map[int]record.Slot{0: openSlot(), 1: openSlot()},
			members: map[string]record.Member{"host-a": member(record.MemberActive, fresh)},
			wantRow: RowOpen,
		},
		{
			// Anomaly: a slot claimed by an agent without a member row, even a LOST one, stops every write.
			name:    "anomaly_claimant_without_member",
			rollout: rollout([]int64{5}, stop(record.StopCancelled)),
			slots: map[int]record.Slot{
				0: task("ghost", record.TaskLost, 2*stale),
				1: openSlot(),
				2: task("host-a", record.TaskRunning, 2*stale),
			},
			members:     map[string]record.Member{"host-a": member(record.MemberActive, fresh)},
			wantRow:     RowInvalid,
			wantAnomaly: `slot 0 is claimed by agent "ghost", which has no member row`,
		},
		{
			// Anomaly: an agent with two slots that are not LOST stops every write.
			name:    "anomaly_agent_with_two_slots",
			rollout: rollout([]int64{5}),
			slots: map[int]record.Slot{
				0: task("host-a", record.TaskDone, fresh),
				1: task("host-a", record.TaskRunning, 2*stale),
				2: openSlot(),
			},
			members:     map[string]record.Member{"host-a": member(record.MemberDown, fresh)},
			wantRow:     RowInvalid,
			wantAnomaly: `agent "host-a" holds slots 0 and 1`,
		},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			_, rv := viewOf(t, scenario{rollout: tc.rollout, slots: tc.slots, members: tc.members}, tc.paused)

			got := Decide(rv, now)

			if got.GroupID != pool {
				t.Errorf("Decide() GroupID = %q, want %q", got.GroupID, pool)
			}
			if got.Row != tc.wantRow {
				t.Errorf("Decide() matched row %q, want %q", got.Row, tc.wantRow)
			}
			if diff := cmp.Diff(tc.wantWrites, writesOf(t, "row", got.Writes.Row)); diff != "" {
				t.Errorf("Decide() row writes (-want +got):\n%s", diff)
			}
			if diff := cmp.Diff(tc.wantMarks, writesOf(t, "bookkeeping", got.Writes.Bookkeeping)); diff != "" {
				t.Errorf("Decide() bookkeeping (-want +got):\n%s", diff)
			}
			if diff := cmp.Diff(tc.wantCleanup, writesOf(t, "cleanup", got.Writes.Cleanup)); diff != "" {
				t.Errorf("Decide() cleanup (-want +got):\n%s", diff)
			}
			switch {
			case tc.wantAnomaly == "" && len(got.Anomalies) != 0:
				t.Errorf("Decide() anomalies = %q, want none", got.Anomalies)
			case tc.wantAnomaly != "" && (len(got.Anomalies) != 1 || !strings.Contains(got.Anomalies[0], tc.wantAnomaly)):
				t.Errorf("Decide() anomalies = %q, want one containing %q", got.Anomalies, tc.wantAnomaly)
			}
		})
	}
}

// TestDecide_WritesExpectVersionsRead checks that every update and delete
// carries the version of the record as it was read, by applying a decision
// after every record changed: every write must be refused.
func TestDecide_WritesExpectVersionsRead(t *testing.T) {
	tests := []struct {
		name       string
		scenario   scenario
		wantWrites int
	}{
		{
			name: "close_and_lost_mark",
			scenario: scenario{
				rollout: rollout([]int64{5}, stop(record.StopCancelled)),
				slots: map[int]record.Slot{
					0: openSlot(),
					1: task("host-a", record.TaskRunning, 2*stale),
				},
				members: map[string]record.Member{"host-a": member(record.MemberActive, fresh)},
			},
			wantWrites: 2,
		},
		{
			name: "conclude_and_down_after_done_mark",
			scenario: scenario{
				rollout: rollout([]int64{5}),
				slots:   map[int]record.Slot{0: task("host-a", record.TaskDone, fresh)},
				members: map[string]record.Member{"host-a": member(record.MemberDown, fresh)},
			},
			wantWrites: 2,
		},
		{
			name: "wave_advance",
			scenario: scenario{
				rollout: rollout([]int64{1, 5}),
				slots:   map[int]record.Slot{0: task("host-a", record.TaskDone, fresh)},
				members: map[string]record.Member{
					"host-a": member(record.MemberActive, fresh),
					"host-b": member(record.MemberActive, fresh),
				},
			},
			wantWrites: 1,
		},
		{
			name: "cleanup",
			scenario: scenario{
				rollout: rollout([]int64{5}, concluded(record.OutcomeSucceeded, earlier, nil)),
				slots:   map[int]record.Slot{0: closedSlot(), 1: closedSlot()},
			},
			wantWrites: 2,
		},
		{
			name: "finish",
			scenario: scenario{
				rollout: rollout([]int64{5}, concluded(record.OutcomeSucceeded, earlier, nil)),
			},
			wantWrites: 1,
		},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			ctx := context.Background()
			s, rv := viewOf(t, tc.scenario, false)
			dec := Decide(rv, now)
			// Every record changes after the snapshot.
			for _, it := range s.All() {
				it.Record["touched"] = true
				if _, err := s.Update(ctx, it.Key, it.Record, it.Version); err != nil {
					t.Fatalf("Update(%q) failed: %v", it.Key, err)
				}
			}

			res := apply.Apply(ctx, s, []apply.Decision{dec.Writes}, apply.Options{})

			if res.Attempted != tc.wantWrites || len(res.Errs) != tc.wantWrites {
				t.Fatalf("apply.Apply() = %+v, want %d writes attempted and all refused", res, tc.wantWrites)
			}
			for _, err := range res.Errs {
				if !errors.Is(err, store.ErrVersionMismatch) {
					t.Errorf("apply.Apply() error = %v, want it to wrap %v", err, store.ErrVersionMismatch)
				}
			}
		})
	}
}

// TestDecide_NewSlotsAfterHighestIndex checks that a view built
// without Views, whose slots are in no particular order, still gets new slots
// that follow the highest index.
func TestDecide_NewSlotsAfterHighestIndex(t *testing.T) {
	_, rv := viewOf(t, scenario{
		rollout: rollout([]int64{10}),
		slots: map[int]record.Slot{
			2: closedSlot(),
			5: task("host-a", record.TaskDone, fresh),
			9: closedSlot(),
		},
		members: map[string]record.Member{
			"host-a": member(record.MemberActive, fresh),
			"host-b": member(record.MemberActive, fresh),
			"host-c": member(record.MemberActive, fresh),
		},
	}, false)
	// The slot with the highest index comes first.
	slices.Reverse(rv.Rollout.Slots)

	got := Decide(rv, now)

	want := []write{slotCreate(10), slotCreate(11)}
	if diff := cmp.Diff(want, writesOf(t, "row", got.Writes.Row)); diff != "" {
		t.Errorf("Decide() row writes for slots in reverse order (-want +got):\n%s", diff)
	}
}

func TestTargetOpen(t *testing.T) {
	tests := []struct {
		name    string
		rollout record.Rollout
		tally   tally
		want    int64
	}{
		{
			// The wave bounds finished tasks plus tasks in flight.
			name:    "wave_bounds_finished_plus_in_flight",
			rollout: rollout([]int64{3, 10}),
			tally:   tally{agentFinished: 1, inFlight: 1, unclaimed: 5},
			want:    1,
		},
		{
			name:    "full_wave_opens_nothing",
			rollout: rollout([]int64{3, 10}),
			tally:   tally{agentFinished: 2, inFlight: 1, unclaimed: 5},
			want:    0,
		},
		{
			name:    "overfull_wave_opens_nothing",
			rollout: rollout([]int64{3, 10}),
			tally:   tally{agentFinished: 1, inFlight: 3, unclaimed: 5},
			want:    0,
		},
		{
			name:    "last_wave_has_no_bound",
			rollout: rollout([]int64{3, 10}, wave(1)),
			tally:   tally{agentFinished: 10, inFlight: 2, unclaimed: 4},
			want:    4,
		},
		{
			// Max_in_flight bounds tasks in flight.
			name:    "max_in_flight_bounds_in_flight",
			rollout: rollout([]int64{10}, maxInFlight(3)),
			tally:   tally{inFlight: 1, unclaimed: 5},
			want:    2,
		},
		{
			name:    "max_in_flight_reached",
			rollout: rollout([]int64{10}, maxInFlight(3)),
			tally:   tally{inFlight: 3, unclaimed: 5},
			want:    0,
		},
		{
			name:    "max_in_flight_exceeded",
			rollout: rollout([]int64{10}, maxInFlight(3)),
			tally:   tally{inFlight: 4, unclaimed: 5},
			want:    0,
		},
		{
			// The smallest bound wins: the wave.
			name:    "smallest_bound_wave",
			rollout: rollout([]int64{2, 10}, maxInFlight(5)),
			tally:   tally{unclaimed: 8},
			want:    2,
		},
		{
			// The smallest bound wins: unclaimed agents.
			name:    "smallest_bound_unclaimed",
			rollout: rollout([]int64{3, 10}, maxInFlight(5)),
			tally:   tally{unclaimed: 1},
			want:    1,
		},
		{
			name:    "nobody_unclaimed",
			rollout: rollout([]int64{10}),
			tally:   tally{agentFinished: 3},
			want:    0,
		},
		{
			// One wave and no limit: a slot for every unclaimed agent.
			name:    "one_wave_no_limit",
			rollout: rollout([]int64{10}),
			tally:   tally{agentFinished: 3, inFlight: 20, unclaimed: 1000},
			want:    1000,
		},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			if got := targetOpen(tc.rollout, tc.tally); got != tc.want {
				t.Errorf("targetOpen() = %d, want %d", got, tc.want)
			}
		})
	}
}

func TestViews(t *testing.T) {
	s := newStore(t, true)
	// An invalid group, a group without a rollout, and a group with one.
	put(t, s, record.GroupKey(domainID, record.GroupController), store.Record{"tag": "latest"})
	put(t, s, record.RolloutKey(record.GroupKey(domainID, record.GroupController), 1), rollout([]int64{1}).Record())
	put(t, s, record.GroupKey(domainID, "public_testing"), record.Group{
		Image:  "repo/img",
		Tag:    "latest",
		Policy: record.Policy{Waves: []record.Wave{{Value: 1}}},
	}.Record())
	scenario{
		rollout: rollout([]int64{1, 2}),
		slots:   map[int]record.Slot{0: openSlot(), 1: closedSlot()},
		members: map[string]record.Member{"host-a": member(record.MemberActive, fresh)},
	}.putIn(t, s, pool)
	d, err := read(context.Background(), s)
	if err != nil {
		t.Fatalf("reading the domain failed: %v", err)
	}

	got := Views(d)

	if len(got) != 1 {
		t.Fatalf("Views() returned %d views, want 1", len(got))
	}
	rv := got[0]
	if rv.GroupID != pool || !rv.Paused {
		t.Errorf("Views()[0] = {GroupID: %q, Paused: %t}, want %q and paused", rv.GroupID, rv.Paused, pool)
	}
	if rv.Rollout.Key != rolloutKey || len(rv.Rollout.Slots) != 2 || len(rv.Members) != 1 {
		t.Errorf("Views()[0] has rollout %q with %d slots and %d members, want %q with 2 slots and 1 member", rv.Rollout.Key, len(rv.Rollout.Slots), len(rv.Members), rolloutKey)
	}
}

// step is the orchestrator pass as an applytest.Step: read one snapshot,
// decide every rollout at the fixed now.
func step(ctx context.Context, s store.Store) ([]apply.Decision, error) {
	d, err := read(ctx, s)
	if err != nil {
		return nil, err
	}
	var writes []apply.Decision
	for _, rv := range Views(d) {
		writes = append(writes, Decide(rv, now).Writes)
	}
	return writes, nil
}

// reconcilerCases are stored states from which the orchestrator pass, with
// no agent writing, has several writes to make over more than one run.
var reconcilerCases = []struct {
	name string
	// groups maps a group ID to its stored state.
	groups map[string]scenario
	// check inspects the fixpoint; groups are sorted by ID.
	check func(t *testing.T, d view.Domain)
}{
	{
		// Stop: close and mark, conclude, clean up, finish.
		name: "stop_to_finish",
		groups: map[string]scenario{pool: {
			rollout: rollout([]int64{2, 6}, stop(record.StopCancelled)),
			slots: map[int]record.Slot{
				0: task("host-a", record.TaskDone, fresh),
				1: openSlot(),
				2: openSlot(),
				3: task("host-b", record.TaskRunning, 2*stale),
				4: task("host-c", record.TaskDone, fresh),
			},
			members: map[string]record.Member{
				"host-a": member(record.MemberActive, fresh),
				"host-b": member(record.MemberActive, 2*stale),
				"host-c": member(record.MemberDown, fresh),
				"host-d": member(record.MemberActive, fresh),
			},
		}},
		check: func(t *testing.T, d view.Domain) {
			ro := d.Groups[0].Rollout
			want := rollout([]int64{2, 6}, stop(record.StopCancelled), finished(), concluded(record.OutcomeCancelled, now, summary{
				"host-a": done,
				"host-b": lost,
				"host-c": failed,
				"host-d": notStarted,
			}))
			if diff := cmp.Diff(want.Record(), ro.Record.Record()); diff != "" {
				t.Errorf("rollout at the fixpoint (-want +got):\n%s", diff)
			}
			if len(ro.Slots) != 0 {
				t.Errorf("rollout at the fixpoint has %d slots, want none", len(ro.Slots))
			}
		},
	},
	{
		// Two groups: one opens slots while the other cleans up.
		name: "two_groups",
		groups: map[string]scenario{
			pool: {
				rollout: rollout([]int64{3}),
				members: map[string]record.Member{
					"host-a": member(record.MemberActive, fresh),
					"host-b": member(record.MemberActive, fresh),
					"host-c": member(record.MemberActive, fresh),
					"host-d": member(record.MemberActive, fresh),
				},
			},
			"public_testing": {
				rollout: rollout([]int64{1}, concluded(record.OutcomeSucceeded, earlier, summary{"host-x": done})),
				slots: map[int]record.Slot{
					0: task("host-x", record.TaskDone, fresh),
					1: closedSlot(),
					2: closedSlot(),
				},
				members: map[string]record.Member{"host-x": member(record.MemberActive, fresh)},
			},
		},
		check: func(t *testing.T, d view.Domain) {
			opening, cleaned := d.Groups[0].Rollout, d.Groups[1].Rollout
			var open []int
			for _, s := range opening.Slots {
				if s.Record.State == record.SlotOpen {
					open = append(open, s.Index)
				}
			}
			// The only wave is the last: every unclaimed agent gets a slot.
			if diff := cmp.Diff([]int{0, 1, 2, 3}, open); diff != "" {
				t.Errorf("OPEN slots of group %q at the fixpoint (-want +got):\n%s", d.Groups[0].ID, diff)
			}
			if !cleaned.Record.Finished || len(cleaned.Slots) != 0 {
				t.Errorf("rollout of group %q at the fixpoint: finished = %t with %d slots, want finished with none", d.Groups[1].ID, cleaned.Record.Finished, len(cleaned.Slots))
			}
		},
	},
	{
		// Wave: advance, then open the next wave's slots.
		name: "wave_advance_then_open",
		groups: map[string]scenario{pool: {
			rollout: rollout([]int64{1, 3, 6}),
			slots:   map[int]record.Slot{0: task("host-a", record.TaskDone, fresh)},
			members: map[string]record.Member{
				"host-a": member(record.MemberActive, fresh),
				"host-b": member(record.MemberActive, fresh),
				"host-c": member(record.MemberActive, fresh),
				"host-d": member(record.MemberActive, fresh),
				"host-e": member(record.MemberActive, fresh),
			},
		}},
		check: func(t *testing.T, d view.Domain) {
			ro := d.Groups[0].Rollout
			if ro.Record.WaveIndex != 1 {
				t.Errorf("wave index at the fixpoint = %d, want 1", ro.Record.WaveIndex)
			}
			var open []int
			for _, s := range ro.Slots {
				if s.Record.State == record.SlotOpen {
					open = append(open, s.Index)
				}
			}
			// Four agents are unclaimed, but the second wave has two places
			// left after the first wave's task.
			if diff := cmp.Diff([]int{1, 2}, open); diff != "" {
				t.Errorf("OPEN slots at the fixpoint (-want +got):\n%s", diff)
			}
		},
	},
	{
		// Complete: close the leftover slot, conclude INCOMPLETE, clean up, finish.
		name: "complete_incomplete_to_finish",
		groups: map[string]scenario{pool: {
			rollout: rollout([]int64{1, 4}, wave(1)),
			slots: map[int]record.Slot{
				0: task("host-a", record.TaskDone, fresh),
				1: task("host-b", record.TaskSkipped, fresh),
				2: openSlot(),
			},
			members: map[string]record.Member{
				"host-a": member(record.MemberActive, fresh),
				"host-b": member(record.MemberDown, fresh),
				"host-c": member(record.MemberActive, stale),
			},
		}},
		check: func(t *testing.T, d view.Domain) {
			ro := d.Groups[0].Rollout
			want := rollout([]int64{1, 4}, wave(1), finished(), concluded(record.OutcomeIncomplete, now, summary{
				"host-a": done,
				"host-b": skipped,
				"host-c": offline,
			}))
			if diff := cmp.Diff(want.Record(), ro.Record.Record()); diff != "" {
				t.Errorf("rollout at the fixpoint (-want +got):\n%s", diff)
			}
		},
	},
}

// seedOf returns a new store that holds the groups.
func seedOf(t *testing.T, groups map[string]scenario) *fake.Store {
	t.Helper()
	s := newStore(t, false)
	for id, sc := range groups {
		sc.putIn(t, s, id)
	}
	return s
}

func TestDecide_RunsToFixpoint(t *testing.T) {
	for _, tc := range reconcilerCases {
		t.Run(tc.name, func(t *testing.T) {
			ctx := context.Background()
			s := seedOf(t, tc.groups)

			applytest.RunToFixpoint(ctx, t, s, step, 6)

			d, err := read(ctx, s)
			if err != nil {
				t.Fatalf("reading the fixpoint failed: %v", err)
			}
			for _, g := range d.Groups {
				if g.Err != nil || g.Rollout == nil {
					t.Fatalf("group %q at the fixpoint: Err = %v, has rollout = %t; want a valid group with its rollout", g.ID, g.Err, g.Rollout != nil)
				}
			}
			tc.check(t, d)
		})
	}
}

func TestDecide_SurvivesCrashes(t *testing.T) {
	for _, tc := range reconcilerCases {
		t.Run(tc.name, func(t *testing.T) {
			seed := func() *fake.Store { return seedOf(t, tc.groups) }

			applytest.CheckCrashes(context.Background(), t, seed, step, 6)
		})
	}
}
