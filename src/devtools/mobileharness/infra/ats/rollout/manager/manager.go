// Package manager is domain-manager: it decides, for each rollout group of a
// domain, whether to start, stop, or archive a rollout.
//
// Decide implements the rollout group decision table of
// node_auto_rollout_concepts.md ("Rollout group"). It is a pure function of
// the view and the time: it reads no clock and does no I/O, and returns the
// store writes for package apply to execute.
package manager

import (
	"fmt"
	"time"

	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/apply"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/record"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/view"
)

// Row names the row of the decision table that a group matched.
type Row string

// The rows of the rollout group decision table, in the order they are
// checked. Row 0 (mark stale members OFFLINE) is not among them: it is
// bookkeeping, done on every run alongside whichever row matches.
const (
	// RowInvalid is not a row of the table: the group's records are invalid,
	// and nothing is written for it.
	RowInvalid Row = "invalid"

	RowArchive       Row = "1"  // rollout FINISHED: archive it
	RowConcluded     Row = "1a" // rollout CONCLUDED: the orchestrator is cleaning up
	RowStopping      Row = "2"  // stop_request set: wait for FINISHED
	RowSupersede     Row = "3"  // upstream moved: stop_request SUPERSEDED
	RowCancel        Row = "4"  // cancel requested: stop_request CANCELLED
	RowRollingOut    Row = "5"  // rollout in progress: nothing
	RowClearCancel   Row = "5a" // cancel with no rollout: clear it
	RowPaused        Row = "5b" // domain paused: no new rollout
	RowUnresolved    Row = "6"  // upstream unknown this run: nothing
	RowUpToDate      Row = "7"  // upstream is the last rollout's revision: nothing
	RowWaiting       Row = "8"  // a dependency blocks the rollout: wait
	RowNoMember      Row = "8a" // nobody online to run a rollout: nothing
	RowCreateRollout Row = "9"  // create the rollout
)

// Decision is what Decide returns for one group.
type Decision struct {
	GroupID string
	// Row is the row the group matched.
	Row Row
	// Writes holds the row's sequence, and one bookkeeping sequence per
	// stale member to mark OFFLINE.
	Writes apply.Decision
	// Anomalies describe states that only a human can repair: invalid
	// records, and a dependency that is stuck. They are reported on every
	// run until the state is repaired.
	Anomalies []string
}

// Decide returns one decision per group of d, in the order of d.Groups. now
// is the single time of the run; every heartbeat is judged against it.
func Decide(d view.Domain, now time.Time) []Decision {
	decisions := make([]Decision, len(d.Groups))
	for i := range d.Groups {
		decisions[i] = decideGroup(d, &d.Groups[i], now)
	}
	return decisions
}

// decideGroup evaluates the decision table for g; the first matching row
// wins.
func decideGroup(d view.Domain, g *view.Group, now time.Time) Decision {
	dec := Decision{GroupID: g.ID}
	if g.Err != nil {
		dec.Row = RowInvalid
		dec.Anomalies = []string{fmt.Sprintf("group %q has invalid records, nothing is written for it: %v", g.ID, g.Err)}
		return dec
	}
	dec.Writes.Bookkeeping = markOffline(g, now)

	// Every row of this table writes at most one sequence: its writes, if
	// more than one, must happen in order.
	var row apply.Sequence
	ro, last := g.Rollout, g.Record.LastRollout
	switch {
	case ro != nil && ro.Record.Finished:
		dec.Row, row = RowArchive, archive(g)
	case ro != nil && ro.Record.Outcome != "":
		dec.Row = RowConcluded
	case ro != nil && ro.Record.StopRequest != "":
		dec.Row = RowStopping
	case ro != nil && g.Resolved() && !g.Upstream.Equal(ro.Record.Revision) && !d.Paused:
		dec.Row, row = RowSupersede, stop(ro, record.StopSuperseded)
	case ro != nil && g.Record.Cancel:
		dec.Row, row = RowCancel, stop(ro, record.StopCancelled)
	case ro != nil:
		dec.Row = RowRollingOut
	case g.Record.Cancel:
		dec.Row, row = RowClearCancel, clearCancel(g)
	case d.Paused:
		dec.Row = RowPaused
	case !g.Resolved():
		dec.Row = RowUnresolved
	case last != nil && g.Upstream.Equal(last.Revision) && !g.Record.Rerun:
		dec.Row = RowUpToDate
	case blocked(d, g):
		dec.Row = RowWaiting
		if dep := dependency(d, g); dep != nil && stuck(dep) {
			dec.Anomalies = []string{fmt.Sprintf("group %q waits for group %q, which is stuck: %s", g.ID, dep.ID, whyStuck(dep))}
		}
	case g.Online(now) == 0:
		dec.Row = RowNoMember
	default:
		dec.Row, row = RowCreateRollout, createRollout(g, now)
	}
	if row != nil {
		dec.Writes.Row = []apply.Sequence{row}
	}
	return dec
}

// markOffline returns one sequence per stale member (row 0). OPTED_OUT and
// OFFLINE members are never stale, so they are never rewritten.
func markOffline(g *view.Group, now time.Time) []apply.Sequence {
	var seqs []apply.Sequence
	for _, m := range g.Members {
		if !m.Record.Stale(now) {
			continue
		}
		doc := m.Record
		doc.Status = record.MemberOffline
		seqs = append(seqs, apply.Sequence{{Op: apply.Update, Key: m.Key, Record: doc.Record(), Expected: m.Version}})
	}
	return seqs
}

// archive returns the two steps of row 1: record the finished rollout as the
// group's last rollout and clear the requests, then delete the rollout. The
// group write is skipped when the group already records this rollout: a
// crash landed between the two steps, and a rerun requested since then must
// be kept.
func archive(g *view.Group) apply.Sequence {
	ro := g.Rollout
	var seq apply.Sequence
	if last := g.Record.LastRollout; last == nil || last.Seq != ro.Seq {
		doc := g.Record
		doc.LastRollout = &record.LastRollout{
			Seq:        ro.Seq,
			Revision:   ro.Record.Revision,
			Outcome:    ro.Record.Outcome,
			Summary:    ro.Record.Summary,
			FinishedAt: ro.Record.FinishedAt,
		}
		doc.Rerun, doc.Cancel = false, false
		seq = append(seq, apply.Write{Op: apply.Update, Key: g.Key, Record: doc.Record(), Expected: g.Version})
	}
	return append(seq, apply.Write{Op: apply.Delete, Key: ro.Key, Expected: ro.Version})
}

// stop returns the write of rows 3 and 4: set stop_request on the rollout.
func stop(ro *view.Rollout, request record.StopRequest) apply.Sequence {
	doc := ro.Record
	doc.StopRequest = request
	return apply.Sequence{{Op: apply.Update, Key: ro.Key, Record: doc.Record(), Expected: ro.Version}}
}

// clearCancel returns the write of row 5a: a cancel applies only to a
// current rollout, so one requested while there is none is removed.
func clearCancel(g *view.Group) apply.Sequence {
	doc := g.Record
	doc.Cancel = false
	return apply.Sequence{{Op: apply.Update, Key: g.Key, Record: doc.Record(), Expected: g.Version}}
}

// createRollout returns the write of row 9: create the rollout of the
// upstream revision, with the group's policy frozen into it. The key is
// derived from the last rollout's sequence number, so a second create of the
// same rollout finds the first.
func createRollout(g *view.Group, now time.Time) apply.Sequence {
	seq := int64(1)
	if last := g.Record.LastRollout; last != nil {
		seq = last.Seq + 1
	}
	policy := g.Record.Policy
	doc := record.Rollout{
		Revision:    g.Upstream,
		Waves:       resolveWaves(policy.Waves, int64(g.Online(now))),
		MaxInFlight: policy.MaxInFlight,
		MaxFailed:   policy.MaxFailed,
		CreatedAt:   now,
	}
	return apply.Sequence{{Op: apply.Create, Key: record.RolloutKey(g.Key, seq), Record: doc.Record()}}
}

// resolveWaves turns a policy's waves into the strictly increasing counts a
// rollout stores, for a group with n online members: a count stays as it is,
// a percentage becomes ceil(p × n / 100), every entry is at least 1 and at
// least the one before it, and an entry equal to the one before it is
// dropped.
func resolveWaves(waves []record.Wave, n int64) []int64 {
	var resolved []int64
	prev := int64(0)
	for _, w := range waves {
		v := w.Value
		if w.Percent {
			v = (w.Value*n + 99) / 100
		}
		v = max(v, prev, 1)
		if v == prev {
			continue
		}
		resolved = append(resolved, v)
		prev = v
	}
	return resolved
}

// dependency returns the group that g depends on, or nil if it has none.
// Every group other than the controller depends on the controller; in a
// domain without a controller group no group depends on anything.
func dependency(d view.Domain, g *view.Group) *view.Group {
	if g.ID == record.GroupController {
		return nil
	}
	for i := range d.Groups {
		if d.Groups[i].ID == record.GroupController {
			return &d.Groups[i]
		}
	}
	return nil
}

// blocked reports whether row 8 holds g back: the group it depends on is not
// settled, or a group that depends on it has a current rollout. So workers
// never upgrade against a controller that is not fully rolled out, and a new
// controller build waits for the running pool rollouts rather than
// superseding them.
func blocked(d view.Domain, g *view.Group) bool {
	if dep := dependency(d, g); dep != nil {
		return !settled(dep)
	}
	for i := range d.Groups {
		other := &d.Groups[i]
		if dep := dependency(d, other); dep != nil && dep.ID == g.ID && hasRollout(other) {
			return true
		}
	}
	return false
}

// hasRollout reports whether other groups must treat g as having a current
// rollout. An invalid group counts as having one.
func hasRollout(g *view.Group) bool {
	return g.Err != nil || g.Rollout != nil
}

// settled reports whether dep has no current rollout and its last rollout
// succeeded with dep's current upstream revision. It is defined on stored
// records and the upstream only, never on writes of this run, so the order
// in which groups are visited does not matter. An invalid group, and one
// whose upstream is unknown this run, is not settled.
func settled(dep *view.Group) bool {
	if hasRollout(dep) || !dep.Resolved() {
		return false
	}
	last := dep.Record.LastRollout
	return last != nil && last.Outcome == record.OutcomeSucceeded && last.Revision.Equal(dep.Upstream)
}

// stuck reports whether dep is not settled and nothing but a human can
// settle it: its records are invalid, or it has rolled out its current
// upstream with an outcome other than SUCCEEDED, has no rollout, and nobody
// asked for a rerun. A dependency that is rolling out, that waits for its
// first member, or whose upstream is unknown this run is not stuck.
func stuck(dep *view.Group) bool {
	if dep.Err != nil {
		return true
	}
	last := dep.Record.LastRollout
	return dep.Rollout == nil && !dep.Record.Rerun && dep.Resolved() &&
		last != nil && last.Revision.Equal(dep.Upstream) && last.Outcome != record.OutcomeSucceeded
}

// whyStuck describes a stuck dependency for the anomaly message.
func whyStuck(dep *view.Group) string {
	if dep.Err != nil {
		return "its records are invalid"
	}
	last := dep.Record.LastRollout
	return fmt.Sprintf("its rollout %d of the current revision ended %s; set rerun on it or push a new build", last.Seq, last.Outcome)
}
