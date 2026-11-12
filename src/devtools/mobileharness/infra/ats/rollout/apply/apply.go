// Package apply executes the store writes that the decision functions of the
// job return. It is the only place where the manager and the orchestrator
// touch the store for writing, and the only one that deals with deadlines:
// the decision functions are pure.
//
// A run's decision is a set of independent write sequences (see "Reconciler
// contract" in node_auto_rollout_concepts.md). The matched row of a decision
// table contributes one sequence for each set of writes that must happen in
// order, and each bookkeeping write and each cleanup write is a sequence of
// its own. A failed write stops its own sequence only, so a write that loses
// a race with an agent never starves the rest of the run.
package apply

import (
	"context"
	"errors"
	"fmt"
	"time"

	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/store"
)

// Op is the kind of a Write.
type Op int

// The store operations a Write can ask for.
const (
	// Create stores Record at Key if no record exists there. A record that
	// exists already counts as done: IDs are deterministic, so it is the
	// record an earlier run created.
	Create Op = iota + 1
	// Update replaces the record at Key with Record if it still has the
	// Expected version.
	Update
	// Delete removes the record at Key if it still has the Expected version.
	// A record that is gone already counts as done.
	Delete
)

// String returns the name of op.
func (op Op) String() string {
	switch op {
	case Create:
		return "create"
	case Update:
		return "update"
	case Delete:
		return "delete"
	}
	return fmt.Sprintf("Op(%d)", int(op))
}

// Write is one conditional store write.
type Write struct {
	Op  Op
	Key store.Key
	// Record is the record to store; unused by Delete.
	Record store.Record
	// Expected is the version the decision was made on; unused by Create.
	Expected store.Version
}

// Sequence is an ordered list of writes. A write is attempted only if every
// write before it succeeded.
type Sequence []Write

// Decision is what a decision function returns for one group or one rollout.
type Decision struct {
	// Row holds the sequences of the matched row. It is empty when the row's
	// action is to do nothing. Writes that must happen in order share a
	// sequence (archive: update the group, then delete the rollout). Writes
	// that do not depend on each other are sequences of their own (closing
	// each OPEN slot), so that one that loses a race with an agent does not
	// stop the others.
	Row []Sequence
	// Bookkeeping holds one sequence per mark that is made on every run,
	// whatever the row decides (OFFLINE, LOST, down_after_done_at).
	Bookkeeping []Sequence
	// Cleanup holds one sequence per record to remove after the work is
	// over (each slot of a concluded rollout). Nothing waits for a cleanup
	// write except the end of the rollout it belongs to, so cleanup has the
	// lowest priority.
	Cleanup []Sequence
}

// Writer is the write half of store.Store.
type Writer interface {
	Create(ctx context.Context, key store.Key, r store.Record) (store.Version, error)
	Update(ctx context.Context, key store.Key, r store.Record, v store.Version) (store.Version, error)
	Delete(ctx context.Context, key store.Key, v store.Version) error
}

// Options bounds the time Apply spends.
type Options struct {
	// Deadline is when the run stops issuing writes. No write starts at or
	// after it, and a write in progress is cancelled at it. The zero value
	// sets no deadline beyond the context's own.
	Deadline time.Time
	// WriteTimeout bounds each write. The zero value sets no bound beyond
	// Deadline.
	WriteTimeout time.Duration
}

// Result reports what Apply did.
type Result struct {
	// Attempted is the number of writes sent to the store, whether or not
	// they succeeded. If it is not zero the store may have changed, and a
	// snapshot read before Apply is out of date.
	Attempted int
	// Errs holds the error that stopped each sequence that did not complete,
	// in the order the sequences were applied. A version mismatch is the
	// normal result of losing a race; the next run decides again.
	Errs []error
	// Exhausted reports that the deadline passed, or the context was
	// cancelled, before every sequence was applied. The next run does the
	// rest.
	Exhausted bool
}

// Apply applies the decisions to w and reports what it did. The decisions are
// in the order their groups are visited.
//
// The Row sequences are applied first, then the Bookkeeping sequences, then
// the Cleanup sequences, each kind round-robin across decisions: the first
// sequence of each decision, then the second of each, and so on. So a burst
// of marks delays only marks and cleanup, deleting the slots of a large
// rollout delays nothing but the end of that rollout, and one group with
// many slots to close does not hold back another group's action. The time
// budget is spent in that order.
//
// A failed write stops its sequence and no other. Apply returns no error:
// every failure is left to the next run, which reads the store again.
func Apply(ctx context.Context, w Writer, decisions []Decision, opts Options) Result {
	if !opts.Deadline.IsZero() {
		var cancel context.CancelFunc
		ctx, cancel = context.WithDeadline(ctx, opts.Deadline)
		defer cancel()
	}
	a := applier{w: w, writeTimeout: opts.WriteTimeout}
	a.roundRobin(ctx, decisions, func(d Decision) []Sequence { return d.Row })
	a.roundRobin(ctx, decisions, func(d Decision) []Sequence { return d.Bookkeeping })
	a.roundRobin(ctx, decisions, func(d Decision) []Sequence { return d.Cleanup })
	return a.res
}

// applier accumulates the Result of one Apply call.
type applier struct {
	w            Writer
	writeTimeout time.Duration
	res          Result
}

// roundRobin applies the sequences that of selects from each decision: the
// first of every decision, then the second of every decision, and so on.
func (a *applier) roundRobin(ctx context.Context, decisions []Decision, of func(Decision) []Sequence) {
	for i, more := 0, true; more; i++ {
		more = false
		for _, d := range decisions {
			if seqs := of(d); i < len(seqs) {
				more = true
				a.sequence(ctx, seqs[i])
			}
		}
	}
}

// sequence applies seq until a write fails or the run's time is up.
func (a *applier) sequence(ctx context.Context, seq Sequence) {
	for _, wr := range seq {
		if ctx.Err() != nil {
			a.res.Exhausted = true
			return
		}
		if wr.Op != Create && wr.Op != Update && wr.Op != Delete {
			a.res.Errs = append(a.res.Errs, fmt.Errorf("write to %q: unknown operation %v", wr.Key, wr.Op))
			return
		}
		a.res.Attempted++
		if err := a.write(ctx, wr); err != nil {
			if ctx.Err() != nil {
				a.res.Exhausted = true
			}
			a.res.Errs = append(a.res.Errs, fmt.Errorf("%v %q: %w", wr.Op, wr.Key, err))
			return
		}
	}
}

// write sends one write to the store under the per-write timeout.
func (a *applier) write(ctx context.Context, wr Write) error {
	if a.writeTimeout > 0 {
		var cancel context.CancelFunc
		ctx, cancel = context.WithTimeout(ctx, a.writeTimeout)
		defer cancel()
	}
	switch wr.Op {
	case Create:
		_, err := a.w.Create(ctx, wr.Key, wr.Record)
		if errors.Is(err, store.ErrAlreadyExists) {
			return nil
		}
		return err
	case Update:
		_, err := a.w.Update(ctx, wr.Key, wr.Record, wr.Expected)
		return err
	default:
		return a.w.Delete(ctx, wr.Key, wr.Expected)
	}
}
