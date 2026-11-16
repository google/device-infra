package job

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"slices"
	"sync"
	"testing"
	"time"

	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/manager"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/orchestrator"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/record"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/store/fake"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/store"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/view"
	"github.com/google/go-cmp/cmp"
)

const (
	domainID   = "mtt.example.com"
	controller = record.GroupController

	fresh = time.Minute             // age of a heartbeat that is online
	stale = record.HeartbeatTimeout // age of a heartbeat that is just stale

	// runTimeout is the timeout of a job that is not meant to run out of
	// time.
	runTimeout = time.Minute
)

var (
	now      = time.Date(2026, 11, 16, 8, 0, 0, 0, time.UTC)
	earlier  = now.Add(-time.Hour)
	revA     = record.Revision{Build: "repo/img@sha256:aaa", TenantConfigVersion: "t1", PoolConfigVersion: "p1"}
	revB     = record.Revision{Build: "repo/img@sha256:bbb", TenantConfigVersion: "t1", PoolConfigVersion: "p1"}
	ctrlRevA = record.Revision{Build: "repo/img@sha256:aaa"}
	ctrlRevB = record.Revision{Build: "repo/img@sha256:bbb"}

	errStore       = errors.New("store unavailable")
	errRegistry    = errors.New("registry unreachable")
	errNoImage     = errors.New("no such image")
	errNotResolved = errors.New("the test resolves no upstream for this group")
)

// fixture builds the stored state of one domain.
type fixture struct {
	t *testing.T
	s *fake.Store
}

// newFixture returns a store that holds the domain document.
func newFixture(t *testing.T) *fixture {
	t.Helper()
	f := &fixture{t: t, s: fake.New()}
	f.put(record.DomainKey(domainID), record.Domain{}.Record())
	return f
}

func (f *fixture) put(key store.Key, r store.Record) {
	f.t.Helper()
	if _, err := f.s.Create(context.Background(), key, r); err != nil {
		f.t.Fatalf("Create(%q) failed: %v", key, err)
	}
}

// group stores a group that follows a tag, with a policy of one wave of
// every member and no limits, and returns its key.
func (f *fixture) group(id string, last *record.LastRollout) store.Key {
	f.t.Helper()
	key := record.GroupKey(domainID, id)
	f.put(key, record.Group{
		Image:       "repo/img",
		Tag:         "latest",
		Policy:      record.Policy{Waves: []record.Wave{{Value: 100, Percent: true}}},
		LastRollout: last,
	}.Record())
	return key
}

// member stores an ACTIVE member of group whose last heartbeat is age old.
func (f *fixture) member(group store.Key, agentID string, age time.Duration) {
	f.t.Helper()
	f.put(record.MemberKey(group, agentID), record.Member{Status: record.MemberActive, HeartbeatAt: now.Add(-age), RunningRevision: revA}.Record())
}

// rollout stores the rollout of group with the given sequence number, and
// returns its key.
func (f *fixture) rollout(group store.Key, seq int64, ro record.Rollout) store.Key {
	f.t.Helper()
	key := record.RolloutKey(group, seq)
	f.put(key, ro.Record())
	return key
}

func (f *fixture) slot(rollout store.Key, n int, s record.Slot) {
	f.t.Helper()
	f.put(record.SlotKey(rollout, n), s.Record())
}

// succeeded returns the last rollout of a group that rolled out rev.
func succeeded(seq int64, rev record.Revision) *record.LastRollout {
	return &record.LastRollout{Seq: seq, Revision: rev, Outcome: record.OutcomeSucceeded, FinishedAt: earlier}
}

// inProgress returns an IN_PROGRESS rollout of rev on its first wave, with no
// limits.
func inProgress(rev record.Revision, waves ...int64) record.Rollout {
	return record.Rollout{Revision: rev, Waves: waves, CreatedAt: earlier}
}

// task returns a slot claimed by agent, with the given result and a lease
// that was last renewed age ago.
func task(agent string, result record.TaskResult, age time.Duration) record.Slot {
	return record.Slot{State: record.SlotClaimed, AgentID: agent, ClaimedAt: earlier, RenewedAt: now.Add(-age), Result: result}
}

// upstreams is a Resolver that knows the answer for each group by its ID:
// the group's upstream, a record.Revision, or the error that leaves it
// unresolved. A group that is not in the map is unresolved.
type upstreams map[string]any

func (u upstreams) Resolve(_ context.Context, d *view.Domain) {
	for i := range d.Groups {
		g := &d.Groups[i]
		if g.Err != nil {
			continue
		}
		switch answer := u[g.ID].(type) {
		case record.Revision:
			g.Upstream = answer
		case error:
			g.UpstreamErr = answer
		default:
			g.UpstreamErr = errNotResolved
		}
	}
}

// logLine is one line that a job logged. An attribute that is an error is
// kept as its text.
type logLine struct {
	Level slog.Level
	Msg   string
	Attrs map[string]any
}

// logs holds what a job logged.
type logs struct {
	mu    sync.Mutex
	lines []logLine
}

// with returns the lines with the given message.
func (l *logs) with(msg string) []logLine {
	l.mu.Lock()
	defer l.mu.Unlock()
	var lines []logLine
	for _, line := range l.lines {
		if line.Msg == msg {
			lines = append(lines, line)
		}
	}
	return lines
}

// handler is a slog.Handler that records into logs.
type handler struct {
	to    *logs
	attrs []slog.Attr
}

func (h handler) Enabled(context.Context, slog.Level) bool { return true }

func (h handler) Handle(_ context.Context, r slog.Record) error {
	line := logLine{Level: r.Level, Msg: r.Message, Attrs: map[string]any{}}
	add := func(a slog.Attr) bool {
		v := a.Value.Resolve().Any()
		if err, ok := v.(error); ok {
			v = err.Error()
		}
		line.Attrs[a.Key] = v
		return true
	}
	for _, a := range h.attrs {
		add(a)
	}
	r.Attrs(add)
	h.to.mu.Lock()
	defer h.to.mu.Unlock()
	h.to.lines = append(h.to.lines, line)
	return nil
}

func (h handler) WithAttrs(attrs []slog.Attr) slog.Handler {
	return handler{to: h.to, attrs: append(slices.Clip(h.attrs), attrs...)}
}

func (h handler) WithGroup(string) slog.Handler { return h }

// config returns the configuration of a job on s that resolves upstreams
// with r, and what a job made from it logs.
func config(s store.Store, r Resolver) (Config, *logs) {
	l := &logs{}
	return Config{DomainID: domainID, Store: s, Resolver: r, Timeout: runTimeout, Log: slog.New(handler{to: l})}, l
}

func mustNew(t *testing.T, cfg Config) *Job {
	t.Helper()
	j, err := New(cfg)
	if err != nil {
		t.Fatalf("New() failed: %v", err)
	}
	return j
}

// settle repeats run until a run attempts no write. It fails the test if a
// run fails, if a write fails for another reason than the run being out of
// time, or if the runs still write after maxRuns of them.
func settle(t *testing.T, maxRuns int, run func() (Report, error)) {
	t.Helper()
	for range maxRuns {
		rep, err := run()
		if err != nil {
			t.Fatalf("Run() failed: %v", err)
		}
		if len(rep.Writes.Errs) > 0 && !rep.Writes.Exhausted {
			t.Fatalf("Run() write errors = %v, want none on a store with no other writer", rep.Writes.Errs)
		}
		if rep.Writes.Attempted == 0 {
			return
		}
	}
	t.Fatalf("the runs still write after %d runs", maxRuns)
}

// read returns the domain as s holds it.
func read(t *testing.T, s store.Store) view.Domain {
	t.Helper()
	ctx := context.Background()
	snap, err := s.Snapshot(ctx)
	if err != nil {
		t.Fatalf("Snapshot() failed: %v", err)
	}
	defer snap.Close(ctx)
	d, err := view.Read(ctx, snap, domainID)
	if err != nil {
		t.Fatalf("view.Read() failed: %v", err)
	}
	return d
}

// groupIn returns the group of d with the given ID, which must be valid.
func groupIn(t *testing.T, d view.Domain, id string) view.Group {
	t.Helper()
	for _, g := range d.Groups {
		if g.ID == id {
			if g.Err != nil {
				t.Fatalf("group %q is invalid: %v", id, g.Err)
			}
			return g
		}
	}
	t.Fatalf("the domain has no group %q", id)
	return view.Group{}
}

// slotStates returns the states of the slots of g's rollout, by index.
func slotStates(g view.Group) []record.SlotState {
	var states []record.SlotState
	if g.Rollout != nil {
		for _, s := range g.Rollout.Slots {
			states = append(states, s.Record.State)
		}
	}
	return states
}

// decided is the row that a pass matched for a group.
type decided struct {
	Group, Row string
}

func managerRows(rep Report) []decided {
	var rows []decided
	for _, dec := range rep.Manager {
		rows = append(rows, decided{Group: dec.GroupID, Row: string(dec.Row)})
	}
	return rows
}

func orchestratorRows(rep Report) []decided {
	var rows []decided
	for _, dec := range rep.Orchestrator {
		rows = append(rows, decided{Group: dec.GroupID, Row: string(dec.Row)})
	}
	return rows
}

// newRevision stores the pool "dda" as it is when its upstream has moved: it
// rolled out revA, has one online member, and no rollout.
func newRevision(f *fixture) {
	g := f.group("dda", succeeded(1, revA))
	f.member(g, "host-a", fresh)
}

// staleMember stores the pool "dda" as it is when it runs its upstream,
// revA, and its one member has gone silent: all there is to do is to mark
// the member OFFLINE.
func staleMember(f *fixture) store.Key {
	g := f.group("dda", succeeded(1, revA))
	f.member(g, "host-a", stale)
	return record.MemberKey(g, "host-a")
}

func TestNew(t *testing.T) {
	cfg, _ := config(fake.New(), upstreams{})

	if _, err := New(cfg); err != nil {
		t.Errorf("New(%+v) failed: %v", cfg, err)
	}
}

func TestNew_InvalidConfig(t *testing.T) {
	tests := []struct {
		name   string
		change func(*Config)
	}{
		{name: "no_domain", change: func(c *Config) { c.DomainID = "" }},
		// Hosts derive the lower-case name, so they would use another domain.
		{name: "domain_in_upper_case", change: func(c *Config) { c.DomainID = "MTT.example.com" }},
		{name: "domain_as_url", change: func(c *Config) { c.DomainID = "wss://mtt.example.com:443" }},
		{name: "domain_with_trailing_dot", change: func(c *Config) { c.DomainID = "mtt.example.com." }},
		{name: "no_store", change: func(c *Config) { c.Store = nil }},
		{name: "no_resolver", change: func(c *Config) { c.Resolver = nil }},
		{name: "no_timeout", change: func(c *Config) { c.Timeout = 0 }},
		{name: "negative_timeout", change: func(c *Config) { c.Timeout = -time.Minute }},
		{name: "no_logger", change: func(c *Config) { c.Log = nil }},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			cfg, _ := config(fake.New(), upstreams{})
			tc.change(&cfg)

			if j, err := New(cfg); err == nil {
				t.Errorf("New(%+v) = %v, want an error", cfg, j)
			}
		})
	}
}

func TestRun_DrivesNewRolloutInSameRun(t *testing.T) {
	f := newFixture(t)
	newRevision(f)
	cfg, log := config(f.s, upstreams{"dda": revB})

	rep, err := mustNew(t, cfg).Run(context.Background(), now)

	if err != nil {
		t.Fatalf("Run() failed: %v", err)
	}
	if diff := cmp.Diff([]decided{{Group: "dda", Row: string(manager.RowCreateRollout)}}, managerRows(rep)); diff != "" {
		t.Errorf("Run() manager rows (-want +got):\n%s", diff)
	}
	if diff := cmp.Diff([]decided{{Group: "dda", Row: string(orchestrator.RowOpen)}}, orchestratorRows(rep)); diff != "" {
		t.Errorf("Run() orchestrator rows (-want +got):\n%s", diff)
	}
	if rep.Writes.Attempted != 2 || len(rep.Writes.Errs) != 0 || rep.Writes.Exhausted {
		t.Errorf("Run() writes = %+v, want 2 attempted, no error, not exhausted", rep.Writes)
	}

	// The rollout that the manager created has its slot already.
	g := groupIn(t, read(t, f.s), "dda")
	if g.Rollout == nil || g.Rollout.Seq != 2 || !g.Rollout.Record.Revision.Equal(revB) {
		t.Fatalf("rollout after the run = %+v, want rollout 2 of %+v", g.Rollout, revB)
	}
	if diff := cmp.Diff([]record.SlotState{record.SlotOpen}, slotStates(g)); diff != "" {
		t.Errorf("slots after the run (-want +got):\n%s", diff)
	}

	wantLog := []logLine{
		{Level: slog.LevelInfo, Msg: "manager decided", Attrs: map[string]any{
			"domain": domainID, "group": "dda", "row": "9",
			"upstream_build": revB.Build, "upstream_tenant_config_version": "t1", "upstream_pool_config_version": "p1",
		}},
		{Level: slog.LevelInfo, Msg: "orchestrator decided", Attrs: map[string]any{"domain": domainID, "group": "dda", "row": "5"}},
		{Level: slog.LevelInfo, Msg: "run finished", Attrs: map[string]any{
			"domain": domainID, "writes": int64(2), "write_errors": int64(0), "out_of_time": false,
		}},
	}
	// How long the run took is not for a test to say.
	ignoreDurations := cmp.FilterPath(func(p cmp.Path) bool {
		key, ok := p.Last().(cmp.MapIndex)
		return ok && slices.Contains([]string{"read_seconds", "resolve_seconds", "total_seconds"}, key.Key().String())
	}, cmp.Ignore())
	if diff := cmp.Diff(wantLog, log.lines, ignoreDurations); diff != "" {
		t.Errorf("Run() logged (-want +got):\n%s", diff)
	}
}

// recorder is a store that records the snapshots asked for and the writes
// made through it, in order, whether or not they succeed.
type recorder struct {
	store.Store
	events []string
}

func (r *recorder) Snapshot(ctx context.Context) (store.Snapshot, error) {
	r.events = append(r.events, "snapshot")
	snap, err := r.Store.Snapshot(ctx)
	if err != nil {
		return nil, err
	}
	return &recordedSnapshot{Snapshot: snap, r: r}, nil
}

func (r *recorder) Create(ctx context.Context, key store.Key, rec store.Record) (store.Version, error) {
	r.events = append(r.events, "create "+key.String())
	return r.Store.Create(ctx, key, rec)
}

func (r *recorder) Update(ctx context.Context, key store.Key, rec store.Record, v store.Version) (store.Version, error) {
	r.events = append(r.events, "update "+key.String())
	return r.Store.Update(ctx, key, rec, v)
}

func (r *recorder) Delete(ctx context.Context, key store.Key, v store.Version) error {
	r.events = append(r.events, "delete "+key.String())
	return r.Store.Delete(ctx, key, v)
}

type recordedSnapshot struct {
	store.Snapshot
	r *recorder
}

func (s *recordedSnapshot) Close(ctx context.Context) error {
	s.r.events = append(s.r.events, "close")
	return s.Snapshot.Close(ctx)
}

func TestRun_AppliesWritesInStages(t *testing.T) {
	// Three pools, each with work for another stage.
	f := newFixture(t)
	// "dda" gets a rollout from the manager and its slot from the
	// orchestrator, and has a stale member for the manager to mark.
	a := f.group("dda", succeeded(1, revA))
	f.member(a, "host-a1", fresh)
	f.member(a, "host-a2", stale)
	// "ddb" is rolling out: one member needs a slot, and the task of the
	// other has expired, for the orchestrator to mark.
	b := f.group("ddb", nil)
	f.member(b, "host-b1", fresh)
	f.member(b, "host-b2", fresh)
	rolloutB := f.rollout(b, 1, inProgress(revB, 2))
	f.slot(rolloutB, 0, task("host-b1", record.TaskRunning, record.LeaseTimeout))
	// "ddc" has concluded and still has a slot to clean up.
	c := f.group("ddc", nil)
	f.member(c, "host-c1", fresh)
	done := inProgress(revB, 1)
	done.Outcome, done.FinishedAt = record.OutcomeSucceeded, earlier
	done.Summary = map[string]record.SummaryEntry{"host-c1": record.SummaryEntry(record.TaskDone)}
	rolloutC := f.rollout(c, 1, done)
	f.slot(rolloutC, 0, task("host-c1", record.TaskDone, fresh))
	rec := &recorder{Store: f.s}
	cfg, _ := config(rec, upstreams{"dda": revB, "ddb": revB, "ddc": revB})

	rep, err := mustNew(t, cfg).Run(context.Background(), now)

	if err != nil || len(rep.Writes.Errs) != 0 {
		t.Fatalf("Run() = %+v, %v, want no error and no failed write", rep.Writes, err)
	}
	rolloutA := record.RolloutKey(a, 2)
	want := []string{
		"snapshot", "close",
		// The manager's rows.
		"create " + rolloutA.String(),
		// The manager wrote, so the orchestrator gets a snapshot of its own.
		"snapshot", "close",
		// The orchestrator's rows, group by group.
		"create " + record.SlotKey(rolloutA, 0).String(),
		"create " + record.SlotKey(rolloutB, 1).String(),
		// Bookkeeping: the orchestrator's LOST, then the manager's OFFLINE.
		"update " + record.SlotKey(rolloutB, 0).String(),
		"update " + record.MemberKey(a, "host-a2").String(),
		// Cleanup.
		"delete " + record.SlotKey(rolloutC, 0).String(),
	}
	if diff := cmp.Diff(want, rec.events); diff != "" {
		t.Errorf("Run() snapshots and writes (-want +got):\n%s", diff)
	}
}

func TestRun_ReadsOnceWhenManagerRowsWriteNothing(t *testing.T) {
	f := newFixture(t)
	member := staleMember(f)
	rec := &recorder{Store: f.s}
	cfg, _ := config(rec, upstreams{"dda": revA})

	if _, err := mustNew(t, cfg).Run(context.Background(), now); err != nil {
		t.Fatalf("Run() failed: %v", err)
	}

	// The manager's mark is bookkeeping, not a row: it does not make the
	// first snapshot out of date for the orchestrator.
	want := []string{"snapshot", "close", "update " + member.String()}
	if diff := cmp.Diff(want, rec.events); diff != "" {
		t.Errorf("Run() snapshots and writes (-want +got):\n%s", diff)
	}
}

// settlingCases are states that take more than one run, and both passes, to
// settle. No agent acts between the runs.
var settlingCases = []struct {
	name      string
	setup     func(f *fixture)
	upstreams upstreams
	// maxRuns bounds the runs until one writes nothing.
	maxRuns int
	check   func(t *testing.T, d view.Domain)
}{
	{
		// Create the rollout, open its slots, mark the stale member.
		name: "new_revision",
		setup: func(f *fixture) {
			g := f.group("dda", succeeded(1, revA))
			f.member(g, "host-a", fresh)
			f.member(g, "host-b", fresh)
			f.member(g, "host-c", stale)
		},
		upstreams: upstreams{"dda": revB},
		maxRuns:   3,
		check: func(t *testing.T, d view.Domain) {
			g := groupIn(t, d, "dda")
			if g.Rollout == nil || g.Rollout.Seq != 2 || !g.Rollout.Record.Revision.Equal(revB) {
				t.Fatalf("rollout at the fixpoint = %+v, want rollout 2 of %+v", g.Rollout, revB)
			}
			if diff := cmp.Diff([]record.SlotState{record.SlotOpen, record.SlotOpen}, slotStates(g)); diff != "" {
				t.Errorf("slots at the fixpoint (-want +got):\n%s", diff)
			}
			for _, m := range g.Members {
				if m.ID == "host-c" && m.Record.Status != record.MemberOffline {
					t.Errorf("member %q at the fixpoint has status %s, want %s", m.ID, m.Record.Status, record.MemberOffline)
				}
			}
		},
	},
	{
		// Conclude, delete the slot, set finished, archive.
		name: "every_task_done",
		setup: func(f *fixture) {
			g := f.group("dda", nil)
			f.member(g, "host-a", fresh)
			ro := f.rollout(g, 1, inProgress(revB, 1))
			f.slot(ro, 0, task("host-a", record.TaskDone, fresh))
		},
		upstreams: upstreams{"dda": revB},
		maxRuns:   6,
		check: func(t *testing.T, d view.Domain) {
			g := groupIn(t, d, "dda")
			last := g.Record.LastRollout
			if g.Rollout != nil || last == nil || last.Seq != 1 || last.Outcome != record.OutcomeSucceeded || !last.Revision.Equal(revB) {
				t.Errorf("group at the fixpoint has rollout %+v and last rollout %+v, want no rollout and rollout 1 of %+v SUCCEEDED as the last", g.Rollout, last, revB)
			}
		},
	},
	{
		// Stop the rollout of revA, close its slot, conclude, clean up,
		// archive, then start over with revB.
		name: "superseded",
		setup: func(f *fixture) {
			g := f.group("dda", nil)
			f.member(g, "host-a", fresh)
			ro := f.rollout(g, 1, inProgress(revA, 1))
			f.slot(ro, 0, record.Slot{State: record.SlotOpen})
		},
		upstreams: upstreams{"dda": revB},
		maxRuns:   8,
		check: func(t *testing.T, d view.Domain) {
			g := groupIn(t, d, "dda")
			last := g.Record.LastRollout
			if last == nil || last.Seq != 1 || last.Outcome != record.OutcomeSuperseded {
				t.Errorf("last rollout at the fixpoint = %+v, want rollout 1 SUPERSEDED", last)
			}
			if g.Rollout == nil || g.Rollout.Seq != 2 || !g.Rollout.Record.Revision.Equal(revB) {
				t.Fatalf("rollout at the fixpoint = %+v, want rollout 2 of %+v", g.Rollout, revB)
			}
			if diff := cmp.Diff([]record.SlotState{record.SlotOpen}, slotStates(g)); diff != "" {
				t.Errorf("slots at the fixpoint (-want +got):\n%s", diff)
			}
		},
	},
	{
		// The controller starts its rollout; the pool waits for it.
		name: "pool_waits_for_controller",
		setup: func(f *fixture) {
			c := f.group(controller, succeeded(1, ctrlRevA))
			f.member(c, "host-c", fresh)
			g := f.group("dda", succeeded(1, revA))
			f.member(g, "host-a", fresh)
		},
		upstreams: upstreams{controller: ctrlRevB, "dda": revB},
		maxRuns:   3,
		check: func(t *testing.T, d view.Domain) {
			c := groupIn(t, d, controller)
			if c.Rollout == nil || !c.Rollout.Record.Revision.Equal(ctrlRevB) {
				t.Fatalf("controller rollout at the fixpoint = %+v, want one of %+v", c.Rollout, ctrlRevB)
			}
			if diff := cmp.Diff([]record.SlotState{record.SlotOpen}, slotStates(c)); diff != "" {
				t.Errorf("controller slots at the fixpoint (-want +got):\n%s", diff)
			}
			if g := groupIn(t, d, "dda"); g.Rollout != nil {
				t.Errorf("pool rollout at the fixpoint = %+v, want none while the controller rolls out", g.Rollout)
			}
		},
	},
}

func TestRun_SettlesOverRuns(t *testing.T) {
	for _, tc := range settlingCases {
		t.Run(tc.name, func(t *testing.T) {
			f := newFixture(t)
			tc.setup(f)
			cfg, _ := config(f.s, tc.upstreams)
			j := mustNew(t, cfg)

			settle(t, tc.maxRuns, func() (Report, error) { return j.Run(context.Background(), now) })

			tc.check(t, read(t, f.s))
		})
	}
}

// slow is a store whose writes take time, as those of a store across a
// network do. A write that is still on its way when its context is done
// does not land.
type slow struct {
	store.Store
	latency time.Duration
}

func (s slow) wait(ctx context.Context) error {
	select {
	case <-time.After(s.latency):
		return nil
	case <-ctx.Done():
		return ctx.Err()
	}
}

func (s slow) Create(ctx context.Context, key store.Key, r store.Record) (store.Version, error) {
	if err := s.wait(ctx); err != nil {
		return store.Version{}, err
	}
	return s.Store.Create(ctx, key, r)
}

func (s slow) Update(ctx context.Context, key store.Key, r store.Record, v store.Version) (store.Version, error) {
	if err := s.wait(ctx); err != nil {
		return store.Version{}, err
	}
	return s.Store.Update(ctx, key, r, v)
}

func (s slow) Delete(ctx context.Context, key store.Key, v store.Version) error {
	if err := s.wait(ctx); err != nil {
		return err
	}
	return s.Store.Delete(ctx, key, v)
}

func TestRun_StopsAtItsBudget(t *testing.T) {
	// A pool with more slots to create than one run has time for, and a
	// second pool whose members all need the OFFLINE mark.
	const (
		members = 1000
		silent  = 50
	)
	f := newFixture(t)
	big := f.group("dda", succeeded(1, revA))
	for i := range members {
		f.member(big, fmt.Sprintf("host-%04d", i), fresh)
	}
	other := f.group("ddb", succeeded(1, revA))
	for i := range silent {
		f.member(other, fmt.Sprintf("host-%04d", i), stale)
	}
	// run does one run with the given timeout, each write taking latency.
	run := func(latency, timeout time.Duration) (Report, error) {
		cfg, _ := config(slow{Store: f.s, latency: latency}, upstreams{"dda": revB, "ddb": revA})
		cfg.Timeout = timeout
		return mustNew(t, cfg).Run(context.Background(), now)
	}
	offline := func(d view.Domain) int {
		n := 0
		for _, m := range groupIn(t, d, "ddb").Members {
			if m.Record.Status == record.MemberOffline {
				n++
			}
		}
		return n
	}

	// The first run has 300 ms, which is 30 writes at 10 ms a write. Left
	// to finish, its 1001 row writes alone would take 10 seconds, so the
	// bound on its time is far from both: it tells a run that stops at its
	// budget from one that does not, however slow the machine.
	const (
		latency = 10 * time.Millisecond
		timeout = 400 * time.Millisecond
		tooLong = 5 * time.Second
	)
	start := time.Now()

	rep, err := run(latency, timeout)

	if elapsed := time.Since(start); elapsed > tooLong {
		t.Errorf("Run() with a timeout of %v took %v, want it to stop at its budget", timeout, elapsed)
	}
	if err != nil || !rep.Writes.Exhausted {
		t.Errorf("Run() = %+v, %v, want no error and writes exhausted", rep.Writes, err)
	}
	d := read(t, f.s)
	if slots := len(slotStates(groupIn(t, d, "dda"))); slots == 0 || slots >= members {
		t.Errorf("the first run created %d slots, want some but not all %d", slots, members)
	}
	// Rows come before bookkeeping, and the rows were not done.
	if got := offline(d); got != 0 {
		t.Errorf("the first run marked %d members OFFLINE, want none before every slot is created", got)
	}

	// The runs after it do the rest. Their writes are faster, to keep the
	// test short, but still too many for one run: at a millisecond a write,
	// a run with 450 ms makes fewer than 450 of them. Three such runs are
	// enough; the bound allows for a machine on which a write takes longer.
	settle(t, 30, func() (Report, error) { return run(time.Millisecond, 600*time.Millisecond) })

	d = read(t, f.s)
	if slots := len(slotStates(groupIn(t, d, "dda"))); slots != members {
		t.Errorf("the runs created %d slots in all, want %d", slots, members)
	}
	if got := offline(d); got != silent {
		t.Errorf("the runs marked %d members OFFLINE in all, want %d", got, silent)
	}
}

// snapshotFailer is a store whose snapshots fail once ok of them have been
// taken.
type snapshotFailer struct {
	store.Store
	ok int
}

func (s *snapshotFailer) Snapshot(ctx context.Context) (store.Snapshot, error) {
	if s.ok <= 0 {
		return nil, errStore
	}
	s.ok--
	return s.Store.Snapshot(ctx)
}

func TestRun_DomainCannotBeRead(t *testing.T) {
	tests := []struct {
		name  string
		store func(t *testing.T) store.Store
		want  error
	}{
		{
			name: "no_snapshot",
			store: func(t *testing.T) store.Store {
				f := newFixture(t)
				newRevision(f)
				return &snapshotFailer{Store: f.s}
			},
			want: errStore,
		},
		{
			name:  "no_domain_document",
			store: func(*testing.T) store.Store { return fake.New() },
			want:  store.ErrNotFound,
		},
		{
			// A paused flag that cannot be read must not count as false.
			name: "invalid_domain_document",
			store: func(t *testing.T) store.Store {
				f := &fixture{t: t, s: fake.New()}
				f.put(record.DomainKey(domainID), store.Record{"paused": "no"})
				g := f.group("dda", succeeded(1, revA))
				f.member(g, "host-a", fresh)
				return f.s
			},
			want: record.ErrInvalid,
		},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			rec := &recorder{Store: tc.store(t)}
			cfg, _ := config(rec, upstreams{"dda": revB})

			rep, err := mustNew(t, cfg).Run(context.Background(), now)

			if !errors.Is(err, tc.want) {
				t.Errorf("Run() error = %v, want one wrapping %v", err, tc.want)
			}
			if rep.Writes.Attempted != 0 || slices.ContainsFunc(rec.events, func(e string) bool { return e != "snapshot" && e != "close" }) {
				t.Errorf("Run() attempted %d writes, store saw %q, want no write", rep.Writes.Attempted, rec.events)
			}
		})
	}
}

func TestRun_SecondReadFails(t *testing.T) {
	f := newFixture(t)
	newRevision(f)
	cfg, _ := config(&snapshotFailer{Store: f.s, ok: 1}, upstreams{"dda": revB})

	rep, err := mustNew(t, cfg).Run(context.Background(), now)

	if !errors.Is(err, errStore) {
		t.Errorf("Run() error = %v, want one wrapping %v", err, errStore)
	}
	// The manager's write landed before the read that failed.
	if rep.Writes.Attempted != 1 || len(rep.Orchestrator) != 0 {
		t.Errorf("Run() attempted %d writes and made %d orchestrator decisions, want 1 and 0", rep.Writes.Attempted, len(rep.Orchestrator))
	}
	if g := groupIn(t, read(t, f.s), "dda"); g.Rollout == nil || len(g.Rollout.Slots) != 0 {
		t.Errorf("rollout after the run = %+v, want one without slots", g.Rollout)
	}
}

// blocking is a store whose creates never return before their context is
// done.
type blocking struct {
	store.Store
}

func (blocking) Create(ctx context.Context, _ store.Key, _ store.Record) (store.Version, error) {
	<-ctx.Done()
	return store.Version{}, ctx.Err()
}

func TestRun_OutOfTimeBeforeSecondRead(t *testing.T) {
	f := newFixture(t)
	newRevision(f)
	rec := &recorder{Store: blocking{Store: f.s}}
	cfg, log := config(rec, upstreams{"dda": revB})
	cfg.Timeout = 40 * time.Millisecond

	rep, err := mustNew(t, cfg).Run(context.Background(), now)

	// Running out of time is not a failure of the run: the manager's write
	// is the next run's to make.
	if err != nil {
		t.Fatalf("Run() failed: %v", err)
	}
	if rep.Writes.Attempted != 1 || !rep.Writes.Exhausted || len(rep.Orchestrator) != 0 {
		t.Errorf("Run() = writes %+v and %d orchestrator decisions, want 1 write attempted, exhausted, and no decision", rep.Writes, len(rep.Orchestrator))
	}
	// The run does not ask for a second snapshot that it has no time to use.
	wantEvents := []string{"snapshot", "close", "create " + record.RolloutKey(record.GroupKey(domainID, "dda"), 2).String()}
	if diff := cmp.Diff(wantEvents, rec.events); diff != "" {
		t.Errorf("Run() snapshots and writes (-want +got):\n%s", diff)
	}
	end := log.with("run finished")
	if len(end) != 1 || end[0].Level != slog.LevelWarn || end[0].Attrs["out_of_time"] != true {
		t.Errorf("Run() logged %+v at its end, want one warning with out_of_time set", end)
	}
}

// interrupted is a Resolver during whose call something happens to the run.
type interrupted struct {
	upstreams
	by func()
}

func (r interrupted) Resolve(ctx context.Context, d *view.Domain) {
	r.by()
	r.upstreams.Resolve(ctx, d)
}

func TestRun_OutOfTimeBeforeOrchestratorPass(t *testing.T) {
	// The time is up before the run gets to its first write, and the manager
	// has nothing to write.
	tests := []struct {
		name    string
		timeout time.Duration
		// by is what happens while the run resolves the upstreams. cancel
		// ends the context that the run was called with.
		by func(cancel context.CancelFunc)
	}{
		{
			// A resolver that does not return at its deadline.
			name:    "budget_ends_while_resolving",
			timeout: 40 * time.Millisecond,
			by:      func(context.CancelFunc) { time.Sleep(40 * time.Millisecond) },
		},
		{
			// The platform stops the run.
			name:    "cancelled_while_resolving",
			timeout: runTimeout,
			by:      func(cancel context.CancelFunc) { cancel() },
		},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			// A rollout that is in progress and needs nothing: its one member
			// has its slot. With time left, the orchestrator would decide
			// for it and write nothing.
			f := newFixture(t)
			g := f.group("dda", nil)
			f.member(g, "host-a", fresh)
			ro := f.rollout(g, 1, inProgress(revB, 1))
			f.slot(ro, 0, record.Slot{State: record.SlotOpen})
			rec := &recorder{Store: f.s}
			ctx, cancel := context.WithCancel(context.Background())
			defer cancel()
			cfg, log := config(rec, interrupted{upstreams: upstreams{"dda": revB}, by: func() { tc.by(cancel) }})
			cfg.Timeout = tc.timeout

			rep, err := mustNew(t, cfg).Run(ctx, now)

			if err != nil {
				t.Fatalf("Run() failed: %v", err)
			}
			if rep.Writes.Attempted != 0 || !rep.Writes.Exhausted || len(rep.Orchestrator) != 0 {
				t.Errorf("Run() = writes %+v and %d orchestrator decisions, want no write attempted, exhausted, and no decision", rep.Writes, len(rep.Orchestrator))
			}
			if diff := cmp.Diff([]string{"snapshot", "close"}, rec.events); diff != "" {
				t.Errorf("Run() snapshots and writes (-want +got):\n%s", diff)
			}
			if lines := log.with("orchestrator decided"); len(lines) != 0 {
				t.Errorf("Run() logged orchestrator decisions %+v, want none", lines)
			}
			end := log.with("run finished")
			if len(end) != 1 || end[0].Level != slog.LevelWarn || end[0].Attrs["out_of_time"] != true {
				t.Errorf("Run() logged %+v at its end, want one warning with out_of_time set", end)
			}
		})
	}
}

func TestRun_UnresolvedUpstream(t *testing.T) {
	permanent := func(err error) bool { return errors.Is(err, errNoImage) }
	tests := []struct {
		name      string
		err       error
		permanent func(error) bool
		want      slog.Level
	}{
		{name: "transient", err: errRegistry, permanent: permanent, want: slog.LevelWarn},
		{name: "permanent", err: fmt.Errorf("resolving: %w", errNoImage), permanent: permanent, want: slog.LevelError},
		{name: "nothing_is_permanent", err: errNoImage, permanent: nil, want: slog.LevelWarn},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			f := newFixture(t)
			newRevision(f)
			other := f.group("ddb", succeeded(1, revA))
			f.member(other, "host-b", fresh)
			cfg, log := config(f.s, upstreams{"dda": revB, "ddb": tc.err})
			cfg.Permanent = tc.permanent

			rep, err := mustNew(t, cfg).Run(context.Background(), now)

			if err != nil {
				t.Fatalf("Run() failed: %v", err)
			}
			// The group that resolved is not held back by the one that did not.
			wantRows := []decided{
				{Group: "dda", Row: string(manager.RowCreateRollout)},
				{Group: "ddb", Row: string(manager.RowUnresolved)},
			}
			if diff := cmp.Diff(wantRows, managerRows(rep)); diff != "" {
				t.Errorf("Run() manager rows (-want +got):\n%s", diff)
			}
			wantLog := []logLine{{Level: tc.want, Msg: "upstream not resolved", Attrs: map[string]any{
				"domain": domainID, "group": "ddb", "error": tc.err.Error(),
			}}}
			if diff := cmp.Diff(wantLog, log.with("upstream not resolved")); diff != "" {
				t.Errorf("Run() logged about upstreams (-want +got):\n%s", diff)
			}
		})
	}
}

func TestRun_LogsAnomaliesOfBothPasses(t *testing.T) {
	f := newFixture(t)
	// A group document that does not match the schema: the manager's anomaly.
	f.put(record.GroupKey(domainID, "dda"), store.Record{"image": int64(5)})
	// A slot claimed by an agent without a member row: the orchestrator's.
	g := f.group("ddb", nil)
	f.member(g, "host-b", fresh)
	ro := f.rollout(g, 1, inProgress(revB, 1))
	f.slot(ro, 0, task("host-gone", record.TaskRunning, fresh))
	cfg, log := config(f.s, upstreams{"ddb": revB})

	rep, err := mustNew(t, cfg).Run(context.Background(), now)

	if err != nil || rep.Writes.Attempted != 0 {
		t.Errorf("Run() = %+v, %v, want no error and no write", rep.Writes, err)
	}
	var groups []string
	for _, line := range log.with("anomaly") {
		if line.Level != slog.LevelError || line.Attrs["anomaly"] == "" {
			t.Errorf("Run() logged anomaly %+v, want an error that says what is wrong", line)
		}
		groups = append(groups, fmt.Sprint(line.Attrs["group"]))
	}
	if diff := cmp.Diff([]string{"dda", "ddb"}, groups); diff != "" {
		t.Errorf("Run() logged anomalies for groups (-want +got):\n%s", diff)
	}
}

// updateFailer is a store whose updates all fail with err.
type updateFailer struct {
	store.Store
	err error
}

func (s updateFailer) Update(_ context.Context, key store.Key, _ store.Record, _ store.Version) (store.Version, error) {
	return store.Version{}, fmt.Errorf("updating %q: %w", key, s.err)
}

func TestRun_FailedWriteIsLeftToNextRun(t *testing.T) {
	tests := []struct {
		name string
		err  error
		want logLine
	}{
		{name: "lost_race", err: store.ErrVersionMismatch, want: logLine{Level: slog.LevelInfo, Msg: "write lost a race"}},
		{name: "store_failure", err: errStore, want: logLine{Level: slog.LevelWarn, Msg: "write failed"}},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			f := newFixture(t)
			staleMember(f)
			cfg, log := config(updateFailer{Store: f.s, err: tc.err}, upstreams{"dda": revA})

			rep, err := mustNew(t, cfg).Run(context.Background(), now)

			if err != nil {
				t.Fatalf("Run() failed: %v", err)
			}
			if len(rep.Writes.Errs) != 1 || !errors.Is(rep.Writes.Errs[0], tc.err) {
				t.Fatalf("Run() write errors = %v, want one wrapping %v", rep.Writes.Errs, tc.err)
			}
			tc.want.Attrs = map[string]any{"domain": domainID, "error": rep.Writes.Errs[0].Error()}
			if diff := cmp.Diff([]logLine{tc.want}, log.with(tc.want.Msg)); diff != "" {
				t.Errorf("Run() logged about the write (-want +got):\n%s", diff)
			}
			if end := log.with("run finished"); len(end) != 1 || end[0].Level != slog.LevelInfo || end[0].Attrs["write_errors"] != int64(1) {
				t.Errorf("Run() logged %+v at its end, want one line at INFO that counts 1 write error", end)
			}
		})
	}
}

// until returns how far away the deadline of ctx is, or -1 if it has none.
func until(ctx context.Context) time.Duration {
	deadline, ok := ctx.Deadline()
	if !ok {
		return -1
	}
	return time.Until(deadline)
}

// timed is a store and a resolver that record how much time the job gave
// each step: the most, if it did the step more than once.
type timed struct {
	store.Store
	upstreams
	read, resolve, write time.Duration
}

func (s *timed) Snapshot(ctx context.Context) (store.Snapshot, error) {
	s.read = max(s.read, until(ctx))
	return s.Store.Snapshot(ctx)
}

func (s *timed) Resolve(ctx context.Context, d *view.Domain) {
	s.resolve = max(s.resolve, until(ctx))
	s.upstreams.Resolve(ctx, d)
}

func (s *timed) Create(ctx context.Context, key store.Key, r store.Record) (store.Version, error) {
	s.write = max(s.write, until(ctx))
	return s.Store.Create(ctx, key, r)
}

func TestRun_DerivesBudgetFromTimeout(t *testing.T) {
	const timeout = 12 * time.Minute
	f := newFixture(t)
	newRevision(f)
	s := &timed{Store: f.s, upstreams: upstreams{"dda": revB}}
	cfg, _ := config(s, s)
	cfg.Timeout = timeout

	if _, err := mustNew(t, cfg).Run(context.Background(), now); err != nil {
		t.Fatalf("Run() failed: %v", err)
	}

	tests := []struct {
		step      string
		got, want time.Duration
	}{
		// The run reads twice and creates twice: the rollout in the
		// manager's pass, its slot in the orchestrator's.
		{step: "read", got: s.read, want: 9 * time.Minute},
		{step: "resolve", got: s.resolve, want: 2 * time.Minute},
		{step: "write", got: s.write, want: 5 * time.Second},
	}
	for _, tc := range tests {
		// The deadline is at most tc.want away, and less by the little time
		// the run had taken by then.
		if tc.got > tc.want || tc.got < tc.want-4*time.Second {
			t.Errorf("Run() with a timeout of %v gave the %s step %v, want %v", timeout, tc.step, tc.got, tc.want)
		}
	}
}
