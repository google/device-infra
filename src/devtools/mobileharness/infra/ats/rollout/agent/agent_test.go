package agent

import (
	"context"
	"errors"
	"io"
	"log/slog"
	"strings"
	"testing"
	"time"

	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/imageref"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/job"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/record"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/store/fake"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/store"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/view"
	"github.com/google/go-cmp/cmp"
)

const (
	domainID = "mtt.example.com"
	groupID  = "private_usage"
	me       = "host1"
	other    = "host2"
)

var (
	now     = time.Date(2026, 11, 18, 8, 0, 0, 0, time.UTC)
	earlier = now.Add(-time.Hour)

	digestOld = "sha256:" + strings.Repeat("a", 64)
	digestNew = "sha256:" + strings.Repeat("b", 64)

	// revOld is what the host runs before the rollout, revNew what the
	// rollout brings.
	revOld = record.Revision{Build: "asia-docker.pkg.dev/p/r/img@" + digestOld, TenantConfigVersion: "t1", PoolConfigVersion: "p1"}
	revNew = record.Revision{Build: "asia-docker.pkg.dev/p/r/img@" + digestNew, TenantConfigVersion: "t1", PoolConfigVersion: "p1"}

	groupKey   = record.GroupKey(domainID, groupID)
	memberKey  = record.MemberKey(groupKey, me)
	rolloutKey = record.RolloutKey(groupKey, 1)

	errStore = errors.New("store unavailable")
	errNode  = errors.New("node failed")
)

// fakeNode is a Node that reports a fixed status and records the upgrades
// it is asked to start.
type fakeNode struct {
	status     Status
	statusErr  error
	upgradeErr error

	upgrades []string
}

func (n *fakeNode) Status(context.Context) (Status, error) {
	return n.status, n.statusErr
}

func (n *fakeNode) StartUpgrade(_ context.Context, build string) error {
	n.upgrades = append(n.upgrades, build)
	return n.upgradeErr
}

// hooked is a store that runs a function before every Update. If the
// function returns an error, Update returns it and writes nothing.
type hooked struct {
	store.Store
	beforeUpdate func(key store.Key) error
}

func (h *hooked) Update(ctx context.Context, key store.Key, r store.Record, v store.Version) (store.Version, error) {
	if err := h.beforeUpdate(key); err != nil {
		return store.Version{}, err
	}
	return h.Store.Update(ctx, key, r, v)
}

// running returns the status of a node whose core service runs rev, with no
// command running.
func running(rev record.Revision) Status {
	return Status{Running: true, Active: rev}
}

// member returns a member that last wrote its heartbeat an hour ago, when
// it ran revOld.
func member(status record.MemberStatus) *record.Member {
	return &record.Member{Status: status, HeartbeatAt: earlier, RunningRevision: revOld}
}

// beat returns the member as the heartbeat of this poll leaves it.
func beat(status record.MemberStatus, rev record.Revision) *record.Member {
	return &record.Member{Status: status, HeartbeatAt: now, RunningRevision: rev}
}

// rollout returns an IN_PROGRESS rollout of revNew.
func rollout() *record.Rollout {
	return &record.Rollout{Revision: revNew, Waves: []int64{1}, CreatedAt: earlier}
}

var (
	open   = record.Slot{State: record.SlotOpen}
	closed = record.Slot{State: record.SlotClosed}
)

// task returns a slot that agent claimed an hour ago, with the given result.
func task(agent string, result record.TaskResult) record.Slot {
	return record.Slot{State: record.SlotClaimed, AgentID: agent, ClaimedAt: earlier, RenewedAt: earlier, Result: result}
}

// claimed returns a slot as the claim of this poll leaves it.
func claimed(result record.TaskResult) record.Slot {
	return record.Slot{State: record.SlotClaimed, AgentID: me, ClaimedAt: now, RenewedAt: now, Result: result}
}

func put(t *testing.T, s store.Store, key store.Key, r store.Record) {
	t.Helper()
	if _, err := s.Create(context.Background(), key, r); err != nil {
		t.Fatalf("Create(%q) failed: %v", key, err)
	}
}

// newStore returns a store that holds the agent's member, if any, the
// rollout, if any, and the slots of the rollout, numbered from 0.
func newStore(t *testing.T, m *record.Member, ro *record.Rollout, slots []record.Slot) *fake.Store {
	t.Helper()
	s := fake.New()
	if m != nil {
		put(t, s, memberKey, m.Record())
	}
	if ro != nil {
		put(t, s, rolloutKey, ro.Record())
	}
	for i, slot := range slots {
		put(t, s, record.SlotKey(rolloutKey, i), slot.Record())
	}
	return s
}

func newAgent(t *testing.T, s store.Store, n Node, drainTimeout time.Duration) *Agent {
	t.Helper()
	a, err := New(Config{
		DomainID:     domainID,
		GroupID:      groupID,
		AgentID:      me,
		Store:        s,
		Node:         n,
		DrainTimeout: drainTimeout,
	})
	if err != nil {
		t.Fatalf("New failed: %v", err)
	}
	return a
}

// checkMember fails the test unless the stored member is want, or there is
// none when want is nil.
func checkMember(t *testing.T, s store.Store, want *record.Member) {
	t.Helper()
	got, _, err := s.Get(context.Background(), memberKey)
	if want == nil {
		if !errors.Is(err, store.ErrNotFound) {
			t.Errorf("Get(member) = %v, %v, want no member", got, err)
		}
		return
	}
	if err != nil {
		t.Fatalf("Get(member) failed: %v", err)
	}
	if diff := cmp.Diff(want.Record(), got); diff != "" {
		t.Errorf("member differs (-want +got):\n%s", diff)
	}
}

// checkSlots fails the test unless the stored slots 0, 1, ... are want.
func checkSlots(t *testing.T, s store.Store, want []record.Slot) {
	t.Helper()
	for i, slot := range want {
		key := record.SlotKey(rolloutKey, i)
		got, _, err := s.Get(context.Background(), key)
		if err != nil {
			t.Fatalf("Get(%q) failed: %v", key, err)
		}
		if diff := cmp.Diff(slot.Record(), got); diff != "" {
			t.Errorf("slot %d differs (-want +got):\n%s", i, diff)
		}
	}
}

// checkFailed fails the test unless a poll returned an error. If want is not
// nil, the error must be want.
func checkFailed(t *testing.T, err, want error) {
	t.Helper()
	if err == nil || (want != nil && !errors.Is(err, want)) {
		t.Errorf("Poll = %v, want an error (%v)", err, want)
	}
}

// checkNoUpgrade fails the test if the node was asked to start an upgrade.
func checkNoUpgrade(t *testing.T, n *fakeNode) {
	t.Helper()
	if len(n.upgrades) != 0 {
		t.Errorf("upgrades started = %v, want none", n.upgrades)
	}
}

// claimFor claims the slot at key for agent, as another agent's poll would.
func claimFor(t *testing.T, s store.Store, key store.Key, agent string) {
	t.Helper()
	_, v, err := s.Get(context.Background(), key)
	if err != nil {
		t.Fatalf("Get(%q) failed: %v", key, err)
	}
	if _, err := s.Update(context.Background(), key, task(agent, record.TaskRunning).Record(), v); err != nil {
		t.Fatalf("Update(%q) failed: %v", key, err)
	}
}

func TestPoll(t *testing.T) {
	// The same build as revNew, as another spelling of the repository.
	revNewRespelled := revNew
	revNewRespelled.Build = "asia.gcr.io/p/img@" + digestNew
	busyUpgrading := Status{Busy: true}
	withDeadline := claimed(record.TaskRunning)
	withDeadline.Deadline = now.Add(10 * time.Minute)
	renewed := task(me, record.TaskRunning)
	renewed.RenewedAt = now
	failed := func(reason string) record.Slot {
		s := task(me, record.TaskFailed)
		s.Reason = reason
		return s
	}
	stopping := rollout()
	stopping.StopRequest = record.StopCancelled

	tests := []struct {
		name         string
		member       *record.Member // nil: the agent has no member
		status       Status
		rollout      *record.Rollout // nil: the group has no rollout
		slots        []record.Slot
		drainTimeout time.Duration

		wantMember   *record.Member // nil: as it was
		wantSlots    []record.Slot  // nil: as they were
		wantUpgrades []string
	}{
		// The heartbeat.
		{
			name:   "no_member_writes_nothing",
			status: running(revOld),
		},
		{
			name:   "opted_out_writes_nothing",
			member: member(record.MemberOptedOut),
			status: running(revOld),
		},
		{
			name:       "core_running_is_active",
			member:     member(record.MemberActive),
			status:     running(revNew),
			wantMember: beat(record.MemberActive, revNew),
		},
		{
			name:       "core_down_is_down",
			member:     member(record.MemberActive),
			status:     Status{},
			wantMember: beat(record.MemberDown, record.Revision{}),
		},
		{
			name:       "busy_with_core_down_is_active_and_keeps_the_revision",
			member:     member(record.MemberActive),
			status:     busyUpgrading,
			wantMember: beat(record.MemberActive, revOld),
		},
		{
			name:       "busy_with_a_revision_reports_it",
			member:     member(record.MemberActive),
			status:     Status{Busy: true, Running: true, Active: revNew},
			wantMember: beat(record.MemberActive, revNew),
		},
		{
			name:       "offline_becomes_active",
			member:     member(record.MemberOffline),
			status:     running(revOld),
			wantMember: beat(record.MemberActive, revOld),
		},

		// The agent's task.
		{
			name:       "running_task_of_a_busy_node_is_renewed",
			member:     member(record.MemberActive),
			status:     busyUpgrading,
			rollout:    rollout(),
			slots:      []record.Slot{task(me, record.TaskRunning)},
			wantMember: beat(record.MemberActive, revOld),
			wantSlots:  []record.Slot{renewed},
		},
		{
			name:       "running_task_on_the_revision_is_done",
			member:     member(record.MemberActive),
			status:     running(revNew),
			rollout:    rollout(),
			slots:      []record.Slot{task(me, record.TaskRunning)},
			wantMember: beat(record.MemberActive, revNew),
			wantSlots:  []record.Slot{task(me, record.TaskDone)},
		},
		{
			name:       "running_task_on_another_revision_is_failed",
			member:     member(record.MemberActive),
			status:     running(revOld),
			rollout:    rollout(),
			slots:      []record.Slot{task(me, record.TaskRunning)},
			wantMember: beat(record.MemberActive, revOld),
			wantSlots:  []record.Slot{failed(record.ReasonNotConverged)},
		},
		{
			name:       "running_task_with_core_down_is_failed",
			member:     member(record.MemberActive),
			status:     Status{},
			rollout:    rollout(),
			slots:      []record.Slot{task(me, record.TaskRunning)},
			wantMember: beat(record.MemberDown, record.Revision{}),
			wantSlots:  []record.Slot{failed(record.ReasonCoreDown)},
		},
		{
			name:       "final_task_stays_and_no_other_slot_is_claimed",
			member:     member(record.MemberActive),
			status:     running(revOld),
			rollout:    rollout(),
			slots:      []record.Slot{failed(record.ReasonNotConverged), open},
			wantMember: beat(record.MemberActive, revOld),
		},
		{
			name:         "lost_slot_is_not_the_task",
			member:       member(record.MemberActive),
			status:       running(revOld),
			rollout:      rollout(),
			slots:        []record.Slot{task(me, record.TaskLost), open},
			wantMember:   beat(record.MemberActive, revOld),
			wantSlots:    []record.Slot{task(me, record.TaskLost), claimed(record.TaskRunning)},
			wantUpgrades: []string{revNew.Build},
		},

		// The claim.
		{
			name:       "concluded_rollout_is_not_claimed",
			member:     member(record.MemberActive),
			status:     running(revOld),
			rollout:    &record.Rollout{Revision: revNew, Waves: []int64{1}, CreatedAt: earlier, Outcome: record.OutcomeCancelled, FinishedAt: earlier},
			slots:      []record.Slot{open},
			wantMember: beat(record.MemberActive, revOld),
		},
		{
			name:       "stopping_rollout_is_not_claimed",
			member:     member(record.MemberActive),
			status:     running(revOld),
			rollout:    stopping,
			slots:      []record.Slot{open},
			wantMember: beat(record.MemberActive, revOld),
		},
		{
			name:       "busy_node_does_not_claim",
			member:     member(record.MemberActive),
			status:     Status{Busy: true, Running: true, Active: revOld},
			rollout:    rollout(),
			slots:      []record.Slot{open},
			wantMember: beat(record.MemberActive, revOld),
		},
		{
			name:       "no_open_slot",
			member:     member(record.MemberActive),
			status:     running(revOld),
			rollout:    rollout(),
			slots:      []record.Slot{closed, task(other, record.TaskRunning)},
			wantMember: beat(record.MemberActive, revOld),
		},
		{
			name:       "core_down_claims_as_skipped",
			member:     member(record.MemberActive),
			status:     Status{},
			rollout:    rollout(),
			slots:      []record.Slot{open},
			wantMember: beat(record.MemberDown, record.Revision{}),
			wantSlots:  []record.Slot{claimed(record.TaskSkipped)},
		},
		{
			name:       "same_digest_in_another_repository_spelling_is_the_revision",
			member:     member(record.MemberActive),
			status:     running(revNewRespelled),
			rollout:    rollout(),
			slots:      []record.Slot{open},
			wantMember: beat(record.MemberActive, revNewRespelled),
			wantSlots:  []record.Slot{claimed(record.TaskDone)},
		},
		{
			name:         "on_another_revision_claims_and_starts_the_upgrade",
			member:       member(record.MemberActive),
			status:       running(revOld),
			rollout:      rollout(),
			slots:        []record.Slot{open},
			wantMember:   beat(record.MemberActive, revOld),
			wantSlots:    []record.Slot{claimed(record.TaskRunning)},
			wantUpgrades: []string{revNew.Build},
		},
		{
			name:         "drain_timeout_is_the_deadline_of_the_claim",
			member:       member(record.MemberActive),
			status:       running(revOld),
			rollout:      rollout(),
			slots:        []record.Slot{open},
			drainTimeout: 10 * time.Minute,
			wantMember:   beat(record.MemberActive, revOld),
			wantSlots:    []record.Slot{withDeadline},
			wantUpgrades: []string{revNew.Build},
		},
		{
			name:         "on_the_revision_claims_as_done_without_a_deadline",
			member:       member(record.MemberActive),
			status:       running(revNew),
			rollout:      rollout(),
			slots:        []record.Slot{open},
			drainTimeout: 10 * time.Minute,
			wantMember:   beat(record.MemberActive, revNew),
			wantSlots:    []record.Slot{claimed(record.TaskDone)},
		},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			s := newStore(t, tc.member, tc.rollout, tc.slots)
			node := &fakeNode{status: tc.status}

			if _, err := newAgent(t, s, node, tc.drainTimeout).Poll(context.Background(), now); err != nil {
				t.Fatalf("Poll failed: %v", err)
			}

			wantMember, wantSlots := tc.wantMember, tc.wantSlots
			if wantMember == nil {
				wantMember = tc.member
			}
			if wantSlots == nil {
				wantSlots = tc.slots
			}
			checkMember(t, s, wantMember)
			checkSlots(t, s, wantSlots)
			if diff := cmp.Diff(tc.wantUpgrades, node.upgrades); diff != "" {
				t.Errorf("upgrades started differ (-want +got):\n%s", diff)
			}
		})
	}
}

func TestPoll_StatusFails_WritesNothing(t *testing.T) {
	slots := []record.Slot{open}
	s := newStore(t, member(record.MemberActive), rollout(), slots)
	node := &fakeNode{statusErr: errNode}

	_, err := newAgent(t, s, node, 0).Poll(context.Background(), now)

	checkFailed(t, err, errNode)
	checkMember(t, s, member(record.MemberActive))
	checkSlots(t, s, slots)
}

func TestPoll_RevisionWithoutABuild_WritesNothing(t *testing.T) {
	slots := []record.Slot{open}
	s := newStore(t, member(record.MemberActive), rollout(), slots)
	node := &fakeNode{status: Status{Running: true, Active: record.Revision{TenantConfigVersion: "t1"}}}

	_, err := newAgent(t, s, node, 0).Poll(context.Background(), now)

	checkFailed(t, err, nil)
	checkMember(t, s, member(record.MemberActive))
	checkSlots(t, s, slots)
	checkNoUpgrade(t, node)
}

func TestPoll_HeartbeatLosesItsRace_EndsThePoll(t *testing.T) {
	slots := []record.Slot{open}
	s := newStore(t, member(record.MemberActive), rollout(), slots)
	node := &fakeNode{status: running(revOld)}
	// The manager marks the member OFFLINE between the agent's read and its
	// write.
	offline := member(record.MemberOffline)
	h := &hooked{Store: s, beforeUpdate: func(key store.Key) error {
		_, v, err := s.Get(context.Background(), key)
		if err != nil {
			t.Fatalf("Get(%q) failed: %v", key, err)
		}
		if _, err := s.Update(context.Background(), key, offline.Record(), v); err != nil {
			t.Fatalf("Update(%q) failed: %v", key, err)
		}
		return nil
	}}

	_, err := newAgent(t, h, node, 0).Poll(context.Background(), now)

	checkFailed(t, err, store.ErrVersionMismatch)
	checkMember(t, s, offline)
	checkSlots(t, s, slots)
	checkNoUpgrade(t, node)
}

func TestPoll_BuildOnAnotherRegistry_ClaimsNone(t *testing.T) {
	ro := rollout()
	ro.Revision.Build = "registry.example.com/p/img@" + digestNew
	slots := []record.Slot{open}
	s := newStore(t, member(record.MemberActive), ro, slots)
	node := &fakeNode{status: running(revOld)}

	_, err := newAgent(t, s, node, 0).Poll(context.Background(), now)

	checkFailed(t, err, imageref.ErrInvalid)
	// The host stays online: the heartbeat comes before the rollout is read.
	checkMember(t, s, beat(record.MemberActive, revOld))
	checkSlots(t, s, slots)
	checkNoUpgrade(t, node)
}

func TestPoll_SlotTakenByAnotherAgent_ClaimsTheNext(t *testing.T) {
	s := newStore(t, member(record.MemberActive), rollout(), []record.Slot{open, open})
	node := &fakeNode{status: running(revOld)}
	// Another agent takes the first slot that this one tries.
	var taken store.Key
	h := &hooked{Store: s, beforeUpdate: func(key store.Key) error {
		if key.Kind() == record.KindSlot && taken.IsRoot() {
			taken = key
			claimFor(t, s, key, other)
		}
		return nil
	}}

	if _, err := newAgent(t, h, node, 0).Poll(context.Background(), now); err != nil {
		t.Fatalf("Poll failed: %v", err)
	}

	want := []record.Slot{claimed(record.TaskRunning), claimed(record.TaskRunning)}
	n, err := record.SlotIndex(taken)
	if err != nil {
		t.Fatalf("no slot was tried: %v", err)
	}
	want[n] = task(other, record.TaskRunning)
	checkSlots(t, s, want)
	if diff := cmp.Diff([]string{revNew.Build}, node.upgrades); diff != "" {
		t.Errorf("upgrades started differ (-want +got):\n%s", diff)
	}
}

func TestPoll_EverySlotTaken_ClaimsNone(t *testing.T) {
	s := newStore(t, member(record.MemberActive), rollout(), []record.Slot{open, open})
	node := &fakeNode{status: running(revOld)}
	h := &hooked{Store: s, beforeUpdate: func(key store.Key) error {
		if key.Kind() == record.KindSlot {
			claimFor(t, s, key, other)
		}
		return nil
	}}

	if _, err := newAgent(t, h, node, 0).Poll(context.Background(), now); err != nil {
		t.Fatalf("Poll failed: %v", err)
	}

	checkSlots(t, s, []record.Slot{task(other, record.TaskRunning), task(other, record.TaskRunning)})
	checkNoUpgrade(t, node)
}

func TestPoll_ClaimFails_TriesNoOtherSlot(t *testing.T) {
	slots := []record.Slot{open, open}
	s := newStore(t, member(record.MemberActive), rollout(), slots)
	node := &fakeNode{status: running(revOld)}
	tried := 0
	h := &hooked{Store: s, beforeUpdate: func(key store.Key) error {
		if key.Kind() != record.KindSlot {
			return nil
		}
		tried++
		return errStore
	}}

	_, err := newAgent(t, h, node, 0).Poll(context.Background(), now)

	checkFailed(t, err, errStore)
	if tried != 1 {
		t.Errorf("the agent tried %d slots, want 1", tried)
	}
	checkSlots(t, s, slots)
	checkNoUpgrade(t, node)
}

func TestPoll_UpgradeDoesNotStart_SlotStaysClaimed(t *testing.T) {
	s := newStore(t, member(record.MemberActive), rollout(), []record.Slot{open})
	node := &fakeNode{status: running(revOld), upgradeErr: errNode}

	_, err := newAgent(t, s, node, 0).Poll(context.Background(), now)

	checkFailed(t, err, errNode)
	if key := record.SlotKey(rolloutKey, 0).String(); err != nil && !strings.Contains(err.Error(), key) {
		t.Errorf("Poll = %v, want an error that names slot %q", err, key)
	}
	checkSlots(t, s, []record.Slot{claimed(record.TaskRunning)})
}

func TestPoll_ResultLosesToALostMark_EndsThePoll(t *testing.T) {
	s := newStore(t, member(record.MemberActive), rollout(), []record.Slot{task(me, record.TaskRunning)})
	node := &fakeNode{status: running(revNew)}
	// The orchestrator marks the task LOST between the agent's read and its
	// write.
	lost := task(me, record.TaskLost)
	h := &hooked{Store: s, beforeUpdate: func(key store.Key) error {
		if key.Kind() != record.KindSlot {
			return nil
		}
		_, v, err := s.Get(context.Background(), key)
		if err != nil {
			t.Fatalf("Get(%q) failed: %v", key, err)
		}
		if _, err := s.Update(context.Background(), key, lost.Record(), v); err != nil {
			t.Fatalf("Update(%q) failed: %v", key, err)
		}
		return nil
	}}

	_, err := newAgent(t, h, node, 0).Poll(context.Background(), now)

	checkFailed(t, err, store.ErrVersionMismatch)
	checkSlots(t, s, []record.Slot{lost})
}

// TestPoll_RecordsThatNoWriterLeaves_EndThePoll covers the two states that
// neither the job nor the agent writes, and that the agent does not act on.
func TestPoll_RecordsThatNoWriterLeaves_EndThePoll(t *testing.T) {
	tests := []struct {
		name          string
		slots         []record.Slot
		secondRollout bool
	}{
		{
			name:          "two_rollouts",
			slots:         []record.Slot{open},
			secondRollout: true,
		},
		{
			name:  "two_slots_of_the_agent_that_are_not_lost",
			slots: []record.Slot{task(me, record.TaskRunning), task(me, record.TaskDone), open},
		},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			s := newStore(t, member(record.MemberActive), rollout(), tc.slots)
			if tc.secondRollout {
				put(t, s, record.RolloutKey(groupKey, 2), rollout().Record())
			}
			node := &fakeNode{status: running(revOld)}

			_, err := newAgent(t, s, node, 0).Poll(context.Background(), now)

			checkFailed(t, err, nil)
			checkSlots(t, s, tc.slots)
			checkNoUpgrade(t, node)
		})
	}
}

// upstream is a job.Resolver that resolves every group to one revision.
type upstream record.Revision

func (u upstream) Resolve(_ context.Context, d *view.Domain) {
	for i := range d.Groups {
		if d.Groups[i].Err == nil {
			d.Groups[i].Upstream = record.Revision(u)
		}
	}
}

// TestPoll_WithTheJob_ControllerRolloutSucceeds takes one controller rollout
// from the agent's first heartbeat to the group's record of it, with the
// job and the agent taking turns on one store: the agent writes what the
// job reads, and the job writes what the agent reads.
func TestPoll_WithTheJob_ControllerRolloutSucceeds(t *testing.T) {
	ctx := context.Background()
	// A controller's revision is a build alone.
	oldRev := record.Revision{Build: revOld.Build}
	newRev := record.Revision{Build: revNew.Build}
	group := record.GroupKey(domainID, record.GroupController)
	logger := slog.New(slog.NewTextHandler(io.Discard, nil))

	s := fake.New()
	put(t, s, record.DomainKey(domainID), record.Domain{}.Record())
	put(t, s, group, record.Group{
		Image:  "asia-docker.pkg.dev/p/r/img",
		Tag:    "latest",
		Policy: record.Policy{Waves: []record.Wave{{Value: 100, Percent: true}}},
	}.Record())
	// The member as enabling the agent left it, long enough ago to be stale.
	put(t, s, record.MemberKey(group, me), record.Member{Status: record.MemberActive, HeartbeatAt: earlier, RunningRevision: oldRev}.Record())

	node := &fakeNode{status: running(oldRev)}
	a, err := New(Config{DomainID: domainID, GroupID: record.GroupController, AgentID: me, Store: s, Node: node})
	if err != nil {
		t.Fatalf("New failed: %v", err)
	}
	j, err := job.New(job.Config{DomainID: domainID, Store: s, Resolver: upstream(newRev), Timeout: time.Minute, Log: logger})
	if err != nil {
		t.Fatalf("job.New failed: %v", err)
	}
	poll := func() {
		t.Helper()
		if _, err := a.Poll(ctx, now); err != nil {
			t.Fatalf("Poll failed: %v", err)
		}
	}
	run := func() {
		t.Helper()
		if _, err := j.Run(ctx, now); err != nil {
			t.Fatalf("job.Run failed: %v", err)
		}
	}

	poll() // The heartbeat brings the member online.
	run()  // The job creates the rollout and opens its slot.
	poll() // The agent claims the slot and starts the upgrade.
	if diff := cmp.Diff([]string{newRev.Build}, node.upgrades); diff != "" {
		t.Fatalf("upgrades started differ (-want +got):\n%s", diff)
	}
	node.status = running(newRev) // The upgrade ends.
	poll()                        // The agent reports the task DONE.
	// The job concludes the rollout, deletes the slot, finishes the rollout,
	// and archives it: four runs. Two more change nothing.
	for range 6 {
		run()
	}

	rec, _, err := s.Get(ctx, group)
	if err != nil {
		t.Fatalf("Get(group) failed: %v", err)
	}
	g, err := record.ParseGroup(rec)
	if err != nil {
		t.Fatalf("ParseGroup failed: %v", err)
	}
	want := &record.LastRollout{
		Seq:        1,
		Revision:   newRev,
		Outcome:    record.OutcomeSucceeded,
		Summary:    map[string]record.SummaryEntry{me: record.SummaryEntry(record.TaskDone)},
		FinishedAt: now,
	}
	if diff := cmp.Diff(want, g.LastRollout); diff != "" {
		t.Errorf("last rollout of the group differs (-want +got):\n%s", diff)
	}
	rollouts, err := s.List(ctx, group, record.KindRollout)
	if err != nil {
		t.Fatalf("List(rollouts) failed: %v", err)
	}
	if len(rollouts) != 0 {
		t.Errorf("the group has %d rollouts left, want none", len(rollouts))
	}
	if len(node.upgrades) != 1 {
		t.Errorf("upgrades started = %v, want one", node.upgrades)
	}
}
