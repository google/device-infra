// Package agent is the poll of rollout-agent: what one agent, on one host,
// does every poll interval. It writes its heartbeat, renews or ends the task
// it holds, and otherwise claims a slot of its group's rollout and starts the
// upgrade.
//
// The steps are those of "Agent" in node_auto_rollout_design.md. The agent
// keeps no state between polls: each poll reads the store and the node. It
// never retries: an error ends the poll, and the next poll is the retry. The
// one exception is a claim that loses its race, after which it tries another
// slot.
package agent

import (
	"context"
	"errors"
	"fmt"
	"math/rand/v2"
	"time"

	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/imageref"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/record"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/store"
)

// Status is what the agent reads of the node's status.
type Status struct {
	// Running reports whether the core service is running.
	Running bool
	// Busy reports whether a command of the node is running, an upgrade for
	// one: the node cannot start another, and what it runs may be about to
	// change.
	Busy bool
	// Active is the revision that the core service runs. It is the zero
	// revision when the node reports none; any other revision must have a
	// build, or the poll fails: a member cannot record a revision without
	// one.
	Active record.Revision
}

// Node is the host that the agent manages.
type Node interface {
	// Status returns what the node runs now.
	Status(ctx context.Context) (Status, error)
	// StartUpgrade starts the upgrade of the core service to build, and
	// returns without waiting for it to end. The node is busy until it does.
	// ctx bounds the start only: the upgrade outlives ctx, and the process
	// of the agent.
	StartUpgrade(ctx context.Context, build string) error
}

// Config is what an Agent is made of. Every field but DrainTimeout is
// required.
type Config struct {
	// DomainID and GroupID name the group that the host belongs to. AgentID
	// is the ID of the host in it, in the form that record.AgentID returns.
	DomainID string
	GroupID  string
	AgentID  string
	Store    store.Store
	Node     Node
	// DrainTimeout is the longest that an upgrade of this host waits for its
	// tests to end: the timeout that the Node gives its upgrades. A claim
	// declares it as the deadline of the task, for people to read. Zero
	// means that the claim declares none.
	DrainTimeout time.Duration
}

// Agent polls for one host. It keeps no state between polls.
type Agent struct {
	cfg    Config
	group  store.Key
	member store.Key
}

// New returns the agent that cfg describes.
func New(cfg Config) (*Agent, error) {
	group := record.GroupKey(cfg.DomainID, cfg.GroupID)
	member := record.MemberKey(group, cfg.AgentID)
	if err := member.Validate(); err != nil {
		return nil, fmt.Errorf("agent: the domain, group, and agent IDs: %w", err)
	}
	return &Agent{cfg: cfg, group: group, member: member}, nil
}

// Poll does one poll, and returns what it did in a few words, for the caller
// to log. now is the time of every timestamp it writes.
//
// It returns an error when it could not do a step: the store or the node
// failed, a record that it read is not valid, or a write lost its race. What
// it wrote before the error stays written.
//
// The polls of one agent must not run at the same time: each could claim a
// slot.
func (a *Agent) Poll(ctx context.Context, now time.Time) (did string, err error) {
	s := a.cfg.Store

	// Only enabling the agent enrolls it: a poll never creates the member,
	// and never opts it in again.
	rec, version, err := s.Get(ctx, a.member)
	if errors.Is(err, store.ErrNotFound) {
		return "nothing: the agent is not enrolled", nil
	}
	if err != nil {
		return "", fmt.Errorf("reading the member: %w", err)
	}
	m, err := record.ParseMember(rec)
	if err != nil {
		return "", fmt.Errorf("member %q: %w", a.member, err)
	}
	if m.Status == record.MemberOptedOut {
		return "nothing: the agent is opted out", nil
	}

	st, err := a.cfg.Node.Status(ctx)
	if err != nil {
		return "", fmt.Errorf("reading the status of the node: %w", err)
	}
	if !st.Active.IsZero() && st.Active.Build == "" {
		// Written as it is, the member would no longer parse, for this agent
		// or for the job, which then writes nothing for the whole group.
		return "", fmt.Errorf("the node reports a revision without a build: %+v", st.Active)
	}

	// The heartbeat. A busy node is ACTIVE whatever its core service does:
	// an upgrade stops the service on the way. While the upgrade swaps the
	// service the node reports no revision, and the member keeps the one it
	// has.
	m.Status = record.MemberActive
	if !st.Running && !st.Busy {
		m.Status = record.MemberDown
	}
	m.HeartbeatAt = now
	if !st.Busy || !st.Active.IsZero() {
		m.RunningRevision = st.Active
	}
	if _, err := s.Update(ctx, a.member, m.Record(), version); err != nil {
		return "", fmt.Errorf("writing the heartbeat: %w", err)
	}

	rollouts, err := s.List(ctx, a.group, record.KindRollout)
	switch {
	case err != nil:
		return "", fmt.Errorf("reading the rollout: %w", err)
	case len(rollouts) == 0:
		return "heartbeat", nil
	case len(rollouts) > 1:
		return "", fmt.Errorf("group %q has %d rollouts, want at most one", a.group, len(rollouts))
	}
	rollout := rollouts[0].Key
	ro, err := record.ParseRollout(rollouts[0].Record)
	if err != nil {
		return "", fmt.Errorf("rollout %q: %w", rollout, err)
	}
	// The node logs in to the registry of the build that it is given, with
	// the credentials of the host. The job writes only builds on Google's
	// registries, but a rollout is data in the store, which every enrolled
	// host can write.
	if _, _, err := imageref.ParseBuild(ro.Revision.Build); err != nil {
		return "", fmt.Errorf("rollout %q: %w", rollout, err)
	}

	// The agent's task is its slot that is not LOST: a lost lease is the
	// orchestrator's record of a task that it gave up on.
	mine, err := s.Query(ctx, rollout, record.KindSlot, store.Eq{Field: record.FieldAgentID, Value: a.cfg.AgentID})
	if err != nil {
		return "", fmt.Errorf("reading the slots of the agent: %w", err)
	}
	var task *record.Slot
	var taskItem store.Item
	for _, it := range mine {
		slot, err := record.ParseSlot(it.Record)
		switch {
		case err != nil:
			return "", fmt.Errorf("slot %q: %w", it.Key, err)
		case slot.Result == record.TaskLost:
			continue
		case task != nil:
			return "", fmt.Errorf("the agent holds slots %q and %q, want at most one that is not LOST", taskItem.Key, it.Key)
		}
		task, taskItem = &slot, it
	}

	if task != nil {
		if task.Final() {
			return "heartbeat", nil
		}
		// The verdict comes from what the node runs now, not from a record of
		// the upgrade.
		switch {
		case st.Busy:
			task.RenewedAt = now
			did = "renewed the lease"
		case st.Running && st.Active.Equal(ro.Revision):
			task.Result = record.TaskDone
			did = "task DONE"
		case st.Running:
			task.Result, task.Reason = record.TaskFailed, record.ReasonNotConverged
			did = "task FAILED: " + record.ReasonNotConverged
		default:
			task.Result, task.Reason = record.TaskFailed, record.ReasonCoreDown
			did = "task FAILED: " + record.ReasonCoreDown
		}
		if _, err := s.Update(ctx, taskItem.Key, task.Record(), taskItem.Version); err != nil {
			return "", fmt.Errorf("writing slot %q: %w", taskItem.Key, err)
		}
		return did, nil
	}

	// A rollout that is asked to stop still has open slots until the
	// orchestrator's next run closes them. The agent starts nothing in it.
	if ro.Outcome != "" || ro.StopRequest != "" || st.Busy {
		return "heartbeat", nil
	}

	// The claim carries the first result of the task, decided before the
	// claim, so that the two are one write.
	result := record.TaskRunning
	var deadline time.Time
	switch {
	case !st.Running:
		result = record.TaskSkipped
	case st.Active.Equal(ro.Revision):
		result = record.TaskDone
	case a.cfg.DrainTimeout > 0:
		deadline = now.Add(a.cfg.DrainTimeout)
	}

	open, err := s.Query(ctx, rollout, record.KindSlot, store.Eq{Field: record.FieldState, Value: string(record.SlotOpen)})
	if err != nil {
		return "", fmt.Errorf("reading the open slots: %w", err)
	}
	rand.Shuffle(len(open), func(i, j int) { open[i], open[j] = open[j], open[i] })
	for _, it := range open {
		slot, err := record.ParseSlot(it.Record)
		if err != nil {
			return "", fmt.Errorf("slot %q: %w", it.Key, err)
		}
		slot.State, slot.AgentID = record.SlotClaimed, a.cfg.AgentID
		slot.ClaimedAt, slot.RenewedAt = now, now
		slot.Result, slot.Deadline = result, deadline
		_, err = s.Update(ctx, it.Key, slot.Record(), it.Version)
		if errors.Is(err, store.ErrVersionMismatch) {
			continue // Another agent took it, or the orchestrator closed it.
		}
		if err != nil {
			// The claim may have landed. The next poll finds the slot if it
			// did; trying another slot now could leave the agent with two.
			return "", fmt.Errorf("claiming slot %q: %w", it.Key, err)
		}
		if result != record.TaskRunning {
			return "claimed a slot: task " + string(result), nil
		}
		if err := a.cfg.Node.StartUpgrade(ctx, ro.Revision.Build); err != nil {
			// The slot stays claimed. The next poll finds a task with no
			// upgrade running, and records it as failed.
			return "", fmt.Errorf("claimed slot %q, starting the upgrade to %q: %w", it.Key, ro.Revision.Build, err)
		}
		return "claimed a slot and started the upgrade to " + ro.Revision.Build, nil
	}
	return "heartbeat", nil
}
