// Package orchestrator is rollout-orchestrator: it drives one rollout from
// its creation to FINISHED by opening and closing slots, advancing waves,
// revoking expired leases, and recording the outcome.
//
// Decide implements the rollout decision table of
// node_auto_rollout_concepts.md ("Rollout"). It is a pure function of the
// view and the time: it reads no clock and does no I/O, and returns the
// store writes for package apply to execute.
package orchestrator

import (
	"fmt"
	"time"

	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/apply"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/record"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/store"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/view"
)

// RolloutView is everything the rollout table reads: the rollout with its
// slots, the group's members, and the domain's paused flag. It leaves out the
// group record on purpose: the policy a rollout follows is the copy frozen
// into it at creation, never the group's.
type RolloutView struct {
	GroupID string
	// Paused is the domain's paused flag.
	Paused bool
	// Rollout is the rollout with its slots. No decision depends on the
	// order of the slots; the writes for them are returned in that order.
	// Views passes them sorted by index.
	Rollout view.Rollout
	// Members holds the group's members, in any order. Views passes them
	// sorted by agent ID.
	Members []view.Member
}

// Views returns the view of every current rollout of d, in the order of
// d.Groups. A group with invalid records has none: nothing is written for
// it, and the manager pass reports it.
func Views(d view.Domain) []RolloutView {
	var views []RolloutView
	for _, g := range d.Groups {
		if g.Err != nil || g.Rollout == nil {
			continue
		}
		views = append(views, RolloutView{GroupID: g.ID, Paused: d.Paused, Rollout: *g.Rollout, Members: g.Members})
	}
	return views
}

// Row names the row of the decision table that a rollout matched.
type Row string

// The rows of the rollout decision table, in the order they are checked. Row
// 1 (mark expired tasks LOST, record a DONE task whose host went DOWN) is not
// among them: it is bookkeeping, done on every run of an IN_PROGRESS rollout
// alongside whichever row matches.
const (
	// RowInvalid is not a row of the table: a slot is claimed by an agent
	// without a member row, or an agent holds two tasks. Nothing is written
	// for the rollout.
	RowInvalid Row = "invalid"
	// RowFinished is not a row of the table: the rollout is FINISHED, and
	// archiving it is the manager's work.
	RowFinished Row = "finished"

	RowCleanup  Row = "0"  // rollout CONCLUDED: delete its slots, then set finished
	RowStop     Row = "2"  // stop requested or too many failures: close slots, conclude
	RowComplete Row = "3"  // every online agent has a result: close slots, conclude
	RowPaused   Row = "3a" // domain paused: close slots, open none
	RowAdvance  Row = "4"  // the wave is done: advance to the next
	RowOpen     Row = "5"  // open slots up to the target
)

// Decision is what Decide returns for one rollout.
type Decision struct {
	GroupID string
	// Row is the row the rollout matched.
	Row Row
	// Writes holds the row's sequences (one per slot to close or create, or
	// the one write on the rollout), one bookkeeping sequence per slot to
	// mark, and one cleanup sequence per slot to delete.
	Writes apply.Decision
	// Anomalies describe states that only a human can repair. They are
	// reported on every run until the state is repaired.
	Anomalies []string
}

// Decide evaluates the decision table for one rollout; the first matching
// row wins. now is the single time of the run; every heartbeat and lease is
// judged against it.
func Decide(rv RolloutView, now time.Time) Decision {
	dec := Decision{GroupID: rv.GroupID}
	ro := rv.Rollout.Record
	switch {
	case ro.Finished:
		dec.Row = RowFinished
		return dec
	case ro.Outcome != "":
		// Row 0 ends the evaluation: bookkeeping and the anomaly checks apply
		// only while the rollout is IN_PROGRESS, so neither can block the
		// cleanup.
		dec.Row = RowCleanup
		if len(rv.Rollout.Slots) > 0 {
			dec.Writes.Cleanup = deleteSlots(rv)
		} else {
			dec.Writes.Row = []apply.Sequence{finish(rv)}
		}
		return dec
	}

	t, anomalies := count(rv, now)
	if len(anomalies) > 0 {
		dec.Row, dec.Anomalies = RowInvalid, anomalies
		return dec
	}
	dec.Writes.Bookkeeping = bookkeeping(rv, t, now)

	lastWave := int64(len(ro.Waves)) - 1
	switch {
	case ro.StopRequest != "" || t.failed > ro.MaxFailed:
		dec.Row = RowStop
		dec.Writes.Row = windDown(rv, t, now, func(map[string]record.SummaryEntry) record.Outcome {
			return stopOutcome(ro.StopRequest)
		})
	case t.online > 0 && t.waiting == 0:
		// Without the first clause, a lab partition that takes every agent
		// offline would conclude the rollout with no work done.
		dec.Row = RowComplete
		dec.Writes.Row = windDown(rv, t, now, completeOutcome)
	case rv.Paused:
		// Agents never read the paused flag, so an OPEN slot would be claimed
		// and start an upgrade.
		dec.Row = RowPaused
		dec.Writes.Row = closeSlots(rv)
	case ro.WaveIndex < lastWave && t.agentFinished >= ro.Waves[ro.WaveIndex]:
		// The next run opens the new wave's slots.
		dec.Row = RowAdvance
		dec.Writes.Row = []apply.Sequence{advance(rv)}
	default:
		dec.Row = RowOpen
		dec.Writes.Row = openSlots(rv, t)
	}
	return dec
}

// tally holds the counts of the rollout table; see "Counting" in the concepts
// brief. Every count comes from stored timestamps and the run's now, never
// from a label this run is about to write: an expired task is in flight
// until its LOST mark has landed, so a mark that loses its race with a
// renewal is never followed, in the same run, by a conclude or by a slot
// that replaces a live task.
type tally struct {
	members map[string]view.Member
	// tasks maps an agent to its task: its one slot whose result is not LOST.
	tasks map[string]view.Slot
	// lost holds the agents that own a LOST slot.
	lost map[string]bool

	open          int64 // OPEN slots
	inFlight      int64 // RUNNING tasks, expired or not
	agentFinished int64 // tasks that ended DONE, FAILED, or SKIPPED
	failed        int64 // tasks that count toward max_failed
	online        int64 // online members
	waiting       int64 // online members without an agent-finished task
	unclaimed     int64 // online members without a task
}

// count tallies the slots and members of an IN_PROGRESS rollout. It returns
// anomalies, and a tally that must not be used, when a claimed slot belongs
// to an agent without a member row or an agent holds two tasks: both are
// states the protocol never produces, and any count made over them could
// conclude the rollout around a task that is still running.
func count(rv RolloutView, now time.Time) (tally, []string) {
	t := tally{
		members: make(map[string]view.Member, len(rv.Members)),
		tasks:   make(map[string]view.Slot),
		lost:    make(map[string]bool),
	}
	for _, m := range rv.Members {
		t.members[m.ID] = m
	}
	var anomalies []string
	for _, s := range rv.Rollout.Slots {
		switch s.Record.State {
		case record.SlotOpen:
			t.open++
		case record.SlotClaimed:
			agent := s.Record.AgentID
			m, ok := t.members[agent]
			if !ok {
				anomalies = append(anomalies, fmt.Sprintf("rollout %q: slot %d is claimed by agent %q, which has no member row; nothing is written for the rollout", rv.Rollout.Key, s.Index, agent))
				continue
			}
			if s.Record.Result == record.TaskLost {
				t.lost[agent] = true
				continue
			}
			if other, ok := t.tasks[agent]; ok {
				anomalies = append(anomalies, fmt.Sprintf("rollout %q: agent %q holds slots %d and %d, want at most one slot that is not LOST; nothing is written for the rollout", rv.Rollout.Key, agent, other.Index, s.Index))
				continue
			}
			t.tasks[agent] = s
			switch {
			case s.Record.InFlight():
				t.inFlight++
			case s.Record.AgentFinished():
				t.agentFinished++
			}
			if s.Record.Failed(m.Record, now) {
				t.failed++
			}
		}
	}
	for _, m := range rv.Members {
		if !m.Record.Online(now) {
			continue
		}
		t.online++
		task, ok := t.tasks[m.ID]
		if !ok {
			t.unclaimed++
		}
		if !ok || !task.Record.AgentFinished() {
			t.waiting++
		}
	}
	return t, anomalies
}

// update returns the one-write sequence that replaces the record at key if
// it still has the version v.
func update(key store.Key, r store.Record, v store.Version) apply.Sequence {
	return apply.Sequence{{Op: apply.Update, Key: key, Record: r, Expected: v}}
}

// bookkeeping returns one sequence per slot to mark (row 1): an expired task
// becomes LOST, and a DONE task whose host is down gets down_after_done_at,
// which keeps it failed even if the host comes back.
func bookkeeping(rv RolloutView, t tally, now time.Time) []apply.Sequence {
	var seqs []apply.Sequence
	for _, s := range rv.Rollout.Slots {
		doc := s.Record
		switch {
		case doc.Expired(now):
			doc.Result = record.TaskLost
		case doc.Result == record.TaskDone && doc.DownAfterDoneAt.IsZero() && t.members[doc.AgentID].Record.Down(now):
			doc.DownAfterDoneAt = now
		default:
			continue
		}
		seqs = append(seqs, update(s.Key, doc.Record(), s.Version))
	}
	return seqs
}

// windDown returns the writes of rows 2 and 3, which end the rollout: close
// every OPEN slot, and once the snapshot shows no OPEN slot and no task in
// flight, record the outcome. Waiting for that snapshot is what keeps an
// agent from claiming a slot after the outcome is decided: a claim that
// races a close either fails or makes the slot a task in flight.
func windDown(rv RolloutView, t tally, now time.Time, outcomeOf func(map[string]record.SummaryEntry) record.Outcome) []apply.Sequence {
	switch {
	case t.open > 0:
		return closeSlots(rv)
	case t.inFlight > 0:
		return nil
	}
	doc := rv.Rollout.Record
	doc.Summary = summarize(rv, t, now)
	doc.Outcome = outcomeOf(doc.Summary)
	doc.FinishedAt = now
	return []apply.Sequence{update(rv.Rollout.Key, doc.Record(), rv.Rollout.Version)}
}

// stopOutcome returns the outcome of a rollout that row 2 ends. A stop
// request names it, even if failures also exceeded max_failed.
func stopOutcome(request record.StopRequest) record.Outcome {
	switch request {
	case record.StopSuperseded:
		return record.OutcomeSuperseded
	case record.StopCancelled:
		return record.OutcomeCancelled
	}
	return record.OutcomeFailed
}

// completeOutcome returns the outcome of a rollout that row 3 ends:
// SUCCEEDED only if every agent in the summary attempted the upgrade and
// reported what happened, that is, no entry is OFFLINE, NOT_STARTED, LOST, or
// SKIPPED. Any such entry makes the rollout INCOMPLETE, so a group that
// depends on this one does not roll out against it.
//
// A FAILED entry does not: failures are judged against max_failed by row 2,
// and a rollout that reaches row 3 has no more of them than its policy
// tolerates. It can therefore end SUCCEEDED while the hosts that failed are
// still on the old revision.
func completeOutcome(summary map[string]record.SummaryEntry) record.Outcome {
	for _, entry := range summary {
		switch entry {
		case record.SummaryOffline, record.SummaryNotStarted, record.SummaryEntry(record.TaskLost), record.SummaryEntry(record.TaskSkipped):
			return record.OutcomeIncomplete
		}
	}
	return record.OutcomeSucceeded
}

// summarize returns the per-agent summary of a rollout with no task in
// flight: one entry per member that has not opted out, and one per agent
// that claimed a slot and then opted out. A task that counts as failed is
// written as FAILED whether or not down_after_done_at has been recorded.
func summarize(rv RolloutView, t tally, now time.Time) map[string]record.SummaryEntry {
	summary := make(map[string]record.SummaryEntry)
	for _, m := range rv.Members {
		task, hasTask := t.tasks[m.ID]
		switch {
		case hasTask && task.Record.Failed(m.Record, now):
			summary[m.ID] = record.SummaryEntry(record.TaskFailed)
		case hasTask:
			summary[m.ID] = record.SummaryEntry(task.Record.Result)
		case t.lost[m.ID]:
			summary[m.ID] = record.SummaryEntry(record.TaskLost)
		case m.Record.Status == record.MemberOptedOut:
			// Never claimed a slot: not part of this rollout.
		case m.Record.Online(now):
			summary[m.ID] = record.SummaryNotStarted
		default:
			summary[m.ID] = record.SummaryOffline
		}
	}
	return summary
}

// closeSlots returns one sequence per OPEN slot to close. Each close is a
// sequence of its own: one that loses its race with a claim must not leave
// the other slots open.
func closeSlots(rv RolloutView) []apply.Sequence {
	var seqs []apply.Sequence
	for _, s := range rv.Rollout.Slots {
		if s.Record.State != record.SlotOpen {
			continue
		}
		doc := s.Record
		doc.State = record.SlotClosed
		seqs = append(seqs, update(s.Key, doc.Record(), s.Version))
	}
	return seqs
}

// advance returns the write of row 4: move to the next wave.
func advance(rv RolloutView) apply.Sequence {
	doc := rv.Rollout.Record
	doc.WaveIndex++
	return update(rv.Rollout.Key, doc.Record(), rv.Rollout.Version)
}

// targetOpen returns the number of OPEN slots row 5 wants: one per unclaimed
// agent, within what the current wave and max_in_flight still allow. The
// last wave has no bound, so that an agent that comes online late, or whose
// task was LOST, can still get a slot.
func targetOpen(ro record.Rollout, t tally) int64 {
	target := t.unclaimed
	if lastWave := int64(len(ro.Waves)) - 1; ro.WaveIndex < lastWave {
		// A claimed slot is no longer OPEN but still has its place in the
		// wave, so tasks in flight count as well as finished ones.
		target = min(target, ro.Waves[ro.WaveIndex]-t.agentFinished-t.inFlight)
	}
	if ro.MaxInFlight > 0 {
		target = min(target, ro.MaxInFlight-t.inFlight)
	}
	return max(target, 0)
}

// openSlots returns one sequence per slot to create (row 5), so that at
// least targetOpen slots are OPEN. It never closes a slot. New slots are
// numbered from one more than the highest existing index, so a run that
// crashed partway is completed by the next, and a slot an operator deleted
// is not created again.
func openSlots(rv RolloutView, t tally) []apply.Sequence {
	next := 0
	for _, s := range rv.Rollout.Slots {
		next = max(next, s.Index+1)
	}
	var seqs []apply.Sequence
	for i := range max(targetOpen(rv.Rollout.Record, t)-t.open, 0) {
		key := record.SlotKey(rv.Rollout.Key, next+int(i))
		seqs = append(seqs, apply.Sequence{{Op: apply.Create, Key: key, Record: record.Slot{State: record.SlotOpen}.Record()}})
	}
	return seqs
}

// deleteSlots returns one cleanup sequence per slot of a CONCLUDED rollout.
func deleteSlots(rv RolloutView) []apply.Sequence {
	seqs := make([]apply.Sequence, len(rv.Rollout.Slots))
	for i, s := range rv.Rollout.Slots {
		seqs[i] = apply.Sequence{{Op: apply.Delete, Key: s.Key, Expected: s.Version}}
	}
	return seqs
}

// finish returns the write that ends row 0: set finished on a CONCLUDED
// rollout whose snapshot shows no slot. The manager then archives it.
func finish(rv RolloutView) apply.Sequence {
	doc := rv.Rollout.Record
	doc.Finished = true
	return update(rv.Rollout.Key, doc.Record(), rv.Rollout.Version)
}
