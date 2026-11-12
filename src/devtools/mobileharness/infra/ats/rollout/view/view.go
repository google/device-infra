// Package view reads the records of one rollout domain into the typed,
// validated, sorted form that the decision functions of the job take.
//
// Reading is the only step of a run that validates stored data. A group whose
// records do not match the schema is kept in the view with Group.Err set:
// both passes write nothing for it and report it (see "Invalid data fails
// static for the whole group" in node_auto_rollout_concepts.md).
package view

import (
	"cmp"
	"context"
	"fmt"
	"slices"
	"time"

	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/record"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/store"
)

// Domain is one rollout domain as of one snapshot.
type Domain struct {
	ID  string
	Key store.Key
	// Paused is the domain's paused flag: nothing new starts while it is set.
	Paused bool
	// Groups holds every group of the domain, sorted by ID.
	Groups []Group
}

// Group is one rollout group with everything under it.
type Group struct {
	ID  string
	Key store.Key
	// Err is not nil when the group's document, its rollout, a slot, or a
	// member does not match the schema, when the group has more than one
	// rollout document, when its rollout is finished but still has slots, or
	// when its rollout's sequence number is impossible.
	// It wraps record.ErrInvalid. The fields below Err are then unset, and no
	// component writes anything for the group.
	Err error

	Record  record.Group
	Version store.Version
	// Members is sorted by agent ID.
	Members []Member
	// Rollout is the group's current rollout, or nil if it has none.
	Rollout *Rollout

	// Upstream is the revision the group should run, and UpstreamErr is why
	// it could not be resolved. Read sets neither: the caller resolves the
	// upstream, once per run, and fills them in. See Resolved.
	Upstream    record.Revision
	UpstreamErr error
}

// Resolved reports whether the group's upstream revision is known for this
// run.
func (g *Group) Resolved() bool {
	return g.UpstreamErr == nil && !g.Upstream.IsZero()
}

// Online returns the number of the group's members that are online at now.
func (g *Group) Online(now time.Time) int {
	n := 0
	for _, m := range g.Members {
		if m.Record.Online(now) {
			n++
		}
	}
	return n
}

// Member is one member row of a group.
type Member struct {
	// ID is the agent ID.
	ID      string
	Key     store.Key
	Record  record.Member
	Version store.Version
}

// Rollout is a group's current rollout and its slots.
type Rollout struct {
	// Seq is the sequence number encoded in Key.
	Seq     int64
	Key     store.Key
	Record  record.Rollout
	Version store.Version
	// Slots is sorted by index.
	Slots []Slot
}

// Slot is one slot of a rollout.
type Slot struct {
	// Index is the slot index encoded in Key.
	Index   int
	Key     store.Key
	Record  record.Slot
	Version store.Version
}

// Read returns the domain with the given ID as r shows it. To get a view
// that is consistent across records, r must be a store.Snapshot.
//
// It returns an error, and no view, when the domain document is missing or
// does not match the schema (a malformed paused flag never reads as false),
// and when a read fails. Invalid data below the domain is not an error: it
// is reported in Group.Err.
func Read(ctx context.Context, r store.Reader, domainID string) (Domain, error) {
	d := Domain{ID: domainID, Key: record.DomainKey(domainID)}
	rec, _, err := r.Get(ctx, d.Key)
	if err != nil {
		return Domain{}, fmt.Errorf("reading domain %q: %w", domainID, err)
	}
	doc, err := record.ParseDomain(rec)
	if err != nil {
		return Domain{}, fmt.Errorf("domain %q: %w", domainID, err)
	}
	d.Paused = doc.Paused

	groups, err := r.List(ctx, d.Key, record.KindGroup)
	if err != nil {
		return Domain{}, fmt.Errorf("listing the groups of domain %q: %w", domainID, err)
	}
	for _, g := range groups {
		group, err := readGroup(ctx, r, g)
		if err != nil {
			return Domain{}, err
		}
		d.Groups = append(d.Groups, group)
	}
	slices.SortFunc(d.Groups, func(a, b Group) int { return cmp.Compare(a.ID, b.ID) })
	return d, nil
}

// readGroup reads what is under one group. It returns an error only when a
// read fails.
func readGroup(ctx context.Context, r store.Reader, group store.Item) (Group, error) {
	members, err := r.List(ctx, group.Key, record.KindMember)
	if err != nil {
		return Group{}, fmt.Errorf("listing the members of %q: %w", group.Key, err)
	}
	rollouts, err := r.List(ctx, group.Key, record.KindRollout)
	if err != nil {
		return Group{}, fmt.Errorf("listing the rollouts of %q: %w", group.Key, err)
	}
	var slots []store.Item
	if len(rollouts) == 1 {
		if slots, err = r.List(ctx, rollouts[0].Key, record.KindSlot); err != nil {
			return Group{}, fmt.Errorf("listing the slots of %q: %w", rollouts[0].Key, err)
		}
	}
	g, err := newGroup(group, members, rollouts, slots)
	if err != nil {
		return Group{ID: group.Key.ID(), Key: group.Key, Err: err}, nil
	}
	return g, nil
}

// newGroup builds a group from its records. It returns an error wrapping
// record.ErrInvalid if any of them is invalid.
func newGroup(group store.Item, members, rollouts, slots []store.Item) (Group, error) {
	g := Group{ID: group.Key.ID(), Key: group.Key, Version: group.Version}
	var err error
	if g.Record, err = record.ParseGroup(group.Record); err != nil {
		return Group{}, fmt.Errorf("group %q: %w", group.Key, err)
	}
	for _, m := range members {
		doc, err := record.ParseMember(m.Record)
		if err != nil {
			return Group{}, fmt.Errorf("member %q: %w", m.Key, err)
		}
		g.Members = append(g.Members, Member{ID: m.Key.ID(), Key: m.Key, Record: doc, Version: m.Version})
	}
	slices.SortFunc(g.Members, func(a, b Member) int { return cmp.Compare(a.ID, b.ID) })

	switch len(rollouts) {
	case 0:
		return g, nil
	case 1:
	default:
		keys := make([]string, len(rollouts))
		for i, ro := range rollouts {
			keys[i] = ro.Key.ID()
		}
		slices.Sort(keys)
		return Group{}, fmt.Errorf("%w: group %q has %d rollout documents %q, want at most one", record.ErrInvalid, group.Key, len(rollouts), keys)
	}
	if g.Rollout, err = newRollout(rollouts[0], slots); err != nil {
		return Group{}, err
	}
	if err := checkSeq(g.Record.LastRollout, g.Rollout); err != nil {
		return Group{}, fmt.Errorf("rollout %q: %w", g.Rollout.Key, err)
	}
	return g, nil
}

// newRollout builds a rollout from its record and its slots.
func newRollout(rollout store.Item, slots []store.Item) (*Rollout, error) {
	ro := &Rollout{Key: rollout.Key, Version: rollout.Version}
	var err error
	if ro.Seq, err = record.RolloutSeq(rollout.Key); err != nil {
		return nil, err
	}
	if ro.Record, err = record.ParseRollout(rollout.Record); err != nil {
		return nil, fmt.Errorf("rollout %q: %w", rollout.Key, err)
	}
	for _, s := range slots {
		index, err := record.SlotIndex(s.Key)
		if err != nil {
			return nil, err
		}
		doc, err := record.ParseSlot(s.Record)
		if err != nil {
			return nil, fmt.Errorf("slot %q: %w", s.Key, err)
		}
		ro.Slots = append(ro.Slots, Slot{Index: index, Key: s.Key, Record: doc, Version: s.Version})
	}
	slices.SortFunc(ro.Slots, func(a, b Slot) int { return cmp.Compare(a.Index, b.Index) })
	if ro.Record.Finished && len(ro.Slots) > 0 {
		// The orchestrator deletes every slot before it sets finished, so no
		// writer produces this state. Archiving the rollout would delete it
		// and leave its slots behind.
		return nil, fmt.Errorf("%w: rollout %q is finished but still has %d slots", record.ErrInvalid, rollout.Key, len(ro.Slots))
	}
	return ro, nil
}

// checkSeq returns an error wrapping record.ErrInvalid unless the current
// rollout's sequence number is possible given the group's last rollout: one
// more than the last (1 when there is none), or equal to the last when the
// rollout is finished, which is the state a crash between the two steps of
// an archive leaves. Any other number would let a rollout conclude without
// updating last_rollout, or move last_rollout.seq backward.
func checkSeq(last *record.LastRollout, ro *Rollout) error {
	var lastSeq int64
	if last != nil {
		lastSeq = last.Seq
	}
	if ro.Seq == lastSeq+1 || (ro.Seq == lastSeq && ro.Record.Finished) {
		return nil
	}
	if ro.Seq == lastSeq {
		return fmt.Errorf("%w: sequence number %d equals last_rollout.seq, but the rollout is not finished", record.ErrInvalid, ro.Seq)
	}
	return fmt.Errorf("%w: sequence number %d, want %d after last_rollout.seq %d", record.ErrInvalid, ro.Seq, lastSeq+1, lastSeq)
}
