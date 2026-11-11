package record

import (
	"fmt"
	"strconv"
	"strings"
	"time"

	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/store"
)

// Each document type below has a Parse function and a Record method.
//
// A Parse function returns an error wrapping ErrInvalid if the record does
// not match the schema. It works on a canonical copy of the record (see
// store.Record.Canonical), so the caller may change the record afterwards,
// and times in the document are the times the store would return.
//
// A Record method returns a new record: the one the document was parsed from
// (none for a document built as a literal), with the document's fields
// written over it. Fields that the schema does not know are kept as read, at
// every level, as long as they still describe the same thing: unknown fields
// of an archived rollout or of a revision are dropped when a different
// rollout or revision replaces it.

// Domain is domains/{domain_id}. Humans write it.
type Domain struct {
	// Paused stops anything new from starting in the domain.
	Paused bool

	src store.Record
}

// ParseDomain reads a domain record.
func ParseDomain(r store.Record) (Domain, error) {
	src := r.Canonical()
	f := newFields(src)
	d := Domain{Paused: f.boolean("paused"), src: src}
	if err := f.err(); err != nil {
		return Domain{}, err
	}
	return d, nil
}

// Record returns d as a store record.
func (d Domain) Record() store.Record {
	r := d.src.Canonical()
	set(r, "paused", d.Paused)
	return r
}

// Wave is one entry of a policy's waves: a number of agents, or a
// percentage of the group's online eligible members when the rollout is
// created.
type Wave struct {
	Value   int64
	Percent bool
}

// parseWave accepts an int64 or a string such as "3" or "5%".
func parseWave(v any) (Wave, error) {
	switch v := v.(type) {
	case int64:
		if v < 1 {
			return Wave{}, fmt.Errorf("%d is not positive", v)
		}
		return Wave{Value: v}, nil
	case string:
		s, percent := strings.CutSuffix(v, "%")
		n, err := strconv.ParseInt(s, 10, 64)
		if err != nil || n < 1 || (percent && n > 100) {
			return Wave{}, fmt.Errorf("%q is not a positive count or a percentage up to 100%%", v)
		}
		return Wave{Value: n, Percent: percent}, nil
	default:
		return Wave{}, fmt.Errorf("want int64 or string, got %T", v)
	}
}

func (w Wave) value() any {
	if w.Percent {
		return strconv.FormatInt(w.Value, 10) + "%"
	}
	return w.Value
}

// Policy is a group's rollout policy. Humans write it.
type Policy struct {
	// Waves are cumulative counts of finished agents, in order; there is at
	// least one. The manager resolves them to integers when it creates a
	// rollout.
	Waves []Wave
	// MaxInFlight bounds concurrent tasks. 0 means the policy sets no bound;
	// a stored value is always at least 1.
	MaxInFlight int64
	// MaxFailed is the number of failed tasks a rollout tolerates.
	MaxFailed int64
}

// parsePolicy reads a policy from the reader of its map.
func parsePolicy(f *fields) Policy {
	var p Policy
	p.MaxInFlight, p.MaxFailed = parseLimits(f)
	for i, w := range f.list("waves") {
		wave, err := parseWave(w)
		if err != nil {
			f.fail("waves", fmt.Sprintf("entry %d: %v", i, err))
			break
		}
		p.Waves = append(p.Waves, wave)
	}
	if f.ok() && len(p.Waves) == 0 {
		f.fail("waves", "missing or empty")
	}
	return p
}

// parseLimits reads max_in_flight and max_failed, which a policy holds and a
// rollout copies when it is created.
func parseLimits(f *fields) (maxInFlight, maxFailed int64) {
	maxInFlight, maxFailed = f.integer("max_in_flight"), f.integer("max_failed")
	switch {
	case !f.ok():
	case f.has("max_in_flight") && maxInFlight < 1:
		// 0 would let no task run, and reading it as "no bound" would turn a
		// limit an operator wrote into no limit at all.
		f.fail("max_in_flight", "must be at least 1 when set; omit it for no bound")
	case maxFailed < 0:
		f.fail("max_failed", "must not be negative")
	}
	return maxInFlight, maxFailed
}

func (p Policy) value() map[string]any {
	waves := make([]any, len(p.Waves))
	for i, w := range p.Waves {
		waves[i] = w.value()
	}
	m := map[string]any{"waves": waves}
	set(m, "max_in_flight", p.MaxInFlight)
	set(m, "max_failed", p.MaxFailed)
	return m
}

// LastRollout is what the manager copies from a rollout when it archives it.
type LastRollout struct {
	Seq        int64
	Revision   Revision
	Outcome    Outcome
	Summary    map[string]SummaryEntry
	FinishedAt time.Time
}

// parseLastRollout reads a last rollout from the reader of its map.
func parseLastRollout(f *fields) *LastRollout {
	lr := &LastRollout{
		Seq:        f.integer("seq"),
		Revision:   parseRevision(f, "revision"),
		Outcome:    parseOutcome(f),
		Summary:    parseSummary(f),
		FinishedAt: f.when("finished_at"),
	}
	// Only a finished rollout is archived, so these are always set.
	switch {
	case !f.ok():
	case lr.Seq < 1:
		f.fail("seq", "must be at least 1")
	case lr.Revision.IsZero():
		f.fail("revision", "missing")
	case lr.Outcome == "":
		f.fail("outcome", "missing")
	case lr.FinishedAt.IsZero():
		f.fail("finished_at", "missing")
	}
	return lr
}

// writeTo writes lr as the map at m["last_rollout"]. Unknown keys of the
// existing map are kept only if it records the same rollout (by seq).
func (lr *LastRollout) writeTo(m map[string]any) {
	sub := nested(m, "last_rollout", func(existing map[string]any) bool {
		return existing["seq"] == lr.Seq
	})
	set(sub, "seq", lr.Seq)
	lr.Revision.writeTo(sub, "revision")
	set(sub, "outcome", string(lr.Outcome))
	set(sub, "summary", summaryValue(lr.Summary))
	set(sub, "finished_at", lr.FinishedAt)
}

// Group is groups/{group_id}: human inputs and requests, and the manager's
// record of the last rollout.
type Group struct {
	Image string
	Tag   string
	// Policy is a human input. Record never rewrites a policy that was read:
	// it encodes Policy only for a Group that was not parsed from a record.
	Policy Policy
	// Cancel and Rerun are human requests; the manager clears them.
	Cancel bool
	Rerun  bool
	// LastRollout is nil until the group has archived a rollout.
	LastRollout *LastRollout

	src store.Record
}

// ParseGroup reads a group record.
func ParseGroup(r store.Record) (Group, error) {
	src := r.Canonical()
	f := newFields(src)
	g := Group{
		Image:  f.str("image"),
		Tag:    f.str("tag"),
		Cancel: f.boolean("cancel"),
		Rerun:  f.boolean("rerun"),
		src:    src,
	}
	policy, hasPolicy := f.sub("policy")
	if hasPolicy {
		g.Policy = parsePolicy(policy)
	}
	if last, ok := f.sub("last_rollout"); ok {
		g.LastRollout = parseLastRollout(last)
	}
	switch {
	case !f.ok():
	case g.Image == "":
		f.fail("image", "missing")
	case g.Tag == "":
		f.fail("tag", "missing")
	case !hasPolicy:
		f.fail("policy", "missing")
	}
	if err := f.err(); err != nil {
		return Group{}, err
	}
	return g, nil
}

// Record returns g as a store record.
func (g Group) Record() store.Record {
	r := g.src.Canonical()
	set(r, "image", g.Image)
	set(r, "tag", g.Tag)
	if _, ok := r["policy"]; !ok && len(g.Policy.Waves) > 0 {
		r["policy"] = g.Policy.value()
	}
	set(r, "cancel", g.Cancel)
	set(r, "rerun", g.Rerun)
	if g.LastRollout == nil {
		delete(r, "last_rollout")
	} else {
		g.LastRollout.writeTo(r)
	}
	return r
}

// Rollout is rollouts/{seq}: the manager's target and the orchestrator's
// progress.
type Rollout struct {
	// Written by the manager when it creates the rollout. Waves,
	// MaxInFlight, and MaxFailed are the group's policy frozen at that
	// moment, with the waves resolved to counts; the orchestrator never reads
	// the group's policy. MaxInFlight 0 means no bound.
	Revision    Revision
	Waves       []int64
	MaxInFlight int64
	MaxFailed   int64
	CreatedAt   time.Time
	// Written by the manager to stop the rollout.
	StopRequest StopRequest
	// Written by the orchestrator. WaveIndex is always a valid index of Waves.
	// Outcome, Summary, and FinishedAt are written together when the rollout
	// is concluded.
	WaveIndex  int64
	Outcome    Outcome
	Summary    map[string]SummaryEntry
	FinishedAt time.Time
	Finished   bool

	src store.Record
}

// ParseRollout reads a rollout record.
func ParseRollout(r store.Record) (Rollout, error) {
	src := r.Canonical()
	f := newFields(src)
	ro := Rollout{
		Revision:    parseRevision(f, "revision"),
		CreatedAt:   f.when("created_at"),
		StopRequest: enum(f, "stop_request", StopSuperseded, StopCancelled),
		WaveIndex:   f.integer("wave_index"),
		Outcome:     parseOutcome(f),
		Summary:     parseSummary(f),
		FinishedAt:  f.when("finished_at"),
		Finished:    f.boolean("finished"),
		src:         src,
	}
	ro.MaxInFlight, ro.MaxFailed = parseLimits(f)
	for i, w := range f.list("waves") {
		n, ok := w.(int64)
		if !ok || n < 1 || (i > 0 && n <= ro.Waves[i-1]) {
			f.fail("waves", "want strictly increasing positive int64 values")
			break
		}
		ro.Waves = append(ro.Waves, n)
	}
	concluded := ro.Outcome != ""
	switch {
	case !f.ok():
	case ro.Revision.IsZero():
		f.fail("revision", "missing")
	case len(ro.Waves) == 0:
		f.fail("waves", "missing or empty")
	case ro.WaveIndex < 0 || ro.WaveIndex >= int64(len(ro.Waves)):
		// Callers index Waves with WaveIndex.
		f.fail("wave_index", fmt.Sprintf("%d is out of range for %d waves", ro.WaveIndex, len(ro.Waves)))
	case concluded != !ro.FinishedAt.IsZero():
		f.fail("finished_at", "must be set if and only if outcome is set")
	case !concluded && ro.Summary != nil:
		f.fail("summary", "set on a rollout without an outcome")
	case !concluded && ro.Finished:
		f.fail("finished", "set on a rollout without an outcome")
	}
	if err := f.err(); err != nil {
		return Rollout{}, err
	}
	return ro, nil
}

// Record returns ro as a store record.
func (ro Rollout) Record() store.Record {
	r := ro.src.Canonical()
	ro.Revision.writeTo(r, "revision")
	var waves []any
	for _, w := range ro.Waves {
		waves = append(waves, w)
	}
	set(r, "waves", waves)
	set(r, "max_in_flight", ro.MaxInFlight)
	set(r, "max_failed", ro.MaxFailed)
	set(r, "created_at", ro.CreatedAt)
	set(r, "stop_request", string(ro.StopRequest))
	set(r, "wave_index", ro.WaveIndex)
	set(r, "outcome", string(ro.Outcome))
	set(r, "summary", summaryValue(ro.Summary))
	set(r, "finished_at", ro.FinishedAt)
	set(r, "finished", ro.Finished)
	return r
}

func parseOutcome(f *fields) Outcome {
	return enum(f, "outcome", OutcomeSucceeded, OutcomeIncomplete, OutcomeFailed, OutcomeCancelled, OutcomeSuperseded)
}

// parseSummary reads the summary map: one entry per agent. It is nil if the
// record has none or an empty one, which Record writes the same way.
func parseSummary(f *fields) map[string]SummaryEntry {
	sub, ok := f.sub("summary")
	if !ok || len(sub.m) == 0 {
		return nil
	}
	summary := make(map[string]SummaryEntry, len(sub.m))
	for agentID := range sub.m {
		summary[agentID] = enum(sub, agentID,
			SummaryEntry(TaskDone), SummaryEntry(TaskFailed), SummaryEntry(TaskSkipped), SummaryEntry(TaskLost),
			SummaryOffline, SummaryNotStarted)
	}
	return summary
}

// summaryValue encodes a summary, or nil for an empty one. The summary is
// replaced as a whole: every key in it is an agent ID, so there are no
// unknown keys to keep.
func summaryValue(summary map[string]SummaryEntry) map[string]any {
	if len(summary) == 0 {
		return nil
	}
	m := make(map[string]any, len(summary))
	for agentID, e := range summary {
		m[agentID] = string(e)
	}
	return m
}

// Slot is slots/{n}: one unit of permission in a rollout, and the task of
// the agent that claimed it.
type Slot struct {
	State SlotState
	// Written by the claiming agent, and set only on a CLAIMED slot. A claim
	// always sets AgentID, ClaimedAt, RenewedAt, and Result; Deadline only
	// if the host drains.
	AgentID   string
	ClaimedAt time.Time
	RenewedAt time.Time
	Deadline  time.Time
	Result    TaskResult
	// Reason is ReasonNotConverged or ReasonCoreDown, set only on a FAILED
	// task.
	Reason string
	// Written by the orchestrator when a DONE task's host goes DOWN.
	DownAfterDoneAt time.Time

	src store.Record
}

// ParseSlot reads a slot record.
func ParseSlot(r store.Record) (Slot, error) {
	src := r.Canonical()
	f := newFields(src)
	s := Slot{
		State:           enum(f, FieldState, SlotOpen, SlotClaimed, SlotClosed),
		AgentID:         f.str(FieldAgentID),
		ClaimedAt:       f.when("claimed_at"),
		RenewedAt:       f.when("renewed_at"),
		Deadline:        f.when("deadline"),
		Result:          enum(f, "result", TaskRunning, TaskDone, TaskFailed, TaskSkipped, TaskLost),
		Reason:          f.str("reason"),
		DownAfterDoneAt: f.when("down_after_done_at"),
		src:             src,
	}
	claimed := s.State == SlotClaimed
	hasTask := s.AgentID != "" || !s.ClaimedAt.IsZero() || !s.RenewedAt.IsZero() ||
		!s.Deadline.IsZero() || s.Result != "" || s.Reason != "" || !s.DownAfterDoneAt.IsZero()
	switch {
	case !f.ok():
	case s.State == "":
		f.fail(FieldState, "missing")
	case !claimed && hasTask:
		f.fail(FieldState, fmt.Sprintf("%s slot has task fields", s.State))
	case claimed && (s.AgentID == "" || s.ClaimedAt.IsZero() || s.RenewedAt.IsZero() || s.Result == ""):
		// The claim writes all four at once, with the task's first result.
		f.fail(FieldState, "CLAIMED slot is missing agent_id, claimed_at, renewed_at, or result")
	case s.Reason != "" && s.Result != TaskFailed:
		f.fail("reason", "set on a task that is not FAILED")
	case !s.DownAfterDoneAt.IsZero() && s.Result != TaskDone:
		f.fail("down_after_done_at", "set on a task that is not DONE")
	}
	if err := f.err(); err != nil {
		return Slot{}, err
	}
	return s, nil
}

// Record returns s as a store record.
func (s Slot) Record() store.Record {
	r := s.src.Canonical()
	set(r, FieldState, string(s.State))
	set(r, FieldAgentID, s.AgentID)
	set(r, "claimed_at", s.ClaimedAt)
	set(r, "renewed_at", s.RenewedAt)
	set(r, "deadline", s.Deadline)
	set(r, "result", string(s.Result))
	set(r, "reason", s.Reason)
	set(r, "down_after_done_at", s.DownAfterDoneAt)
	return r
}

// Final reports whether the task has a result that will not change. It
// includes LOST; see AgentFinished for the count that drives waves.
func (s Slot) Final() bool {
	return s.Result != "" && s.Result != TaskRunning
}

// AgentFinished reports whether the agent reported a result (DONE, FAILED,
// or SKIPPED). LOST is not agent-finished: a dead agent must not advance a
// wave or use up one of its places.
func (s Slot) AgentFinished() bool {
	return s.Result == TaskDone || s.Result == TaskFailed || s.Result == TaskSkipped
}

// InFlight reports whether the task is RUNNING, expired or not. An expired
// task counts as in flight until the orchestrator's LOST mark has landed, so
// a mark that loses its race with a renewal is never followed by a conclude
// or a replacement slot in the same run.
func (s Slot) InFlight() bool {
	return s.Result == TaskRunning
}

// Expired reports whether the task is RUNNING and its lease has run out at
// now: the orchestrator marks it LOST.
func (s Slot) Expired(now time.Time) bool {
	return s.InFlight() && !now.Before(s.RenewedAt.Add(LeaseTimeout))
}

// Failed reports whether the task counts toward max_failed: it ended FAILED,
// or it ended DONE and its host went DOWN afterwards. m is the claiming
// agent's member row. The member's current state counts even before the
// orchestrator has recorded DownAfterDoneAt, so the answer does not depend on
// whether that bookkeeping write has landed.
func (s Slot) Failed(m Member, now time.Time) bool {
	return s.Result == TaskFailed || (s.Result == TaskDone && (!s.DownAfterDoneAt.IsZero() || m.Down(now)))
}

// Member is members/{agent_id}: an agent's heartbeat.
type Member struct {
	Status          MemberStatus
	HeartbeatAt     time.Time
	RunningRevision Revision

	src store.Record
}

// Online reports whether the member is eligible and its heartbeat is fresh at
// now: the agent is alive and may take part in a rollout. Callers decide
// liveness with this, not with Status == MemberOffline, so a silent ACTIVE or
// DOWN member counts as offline before the manager's OFFLINE mark lands.
func (m Member) Online(now time.Time) bool {
	return m.Status.Eligible() && now.Before(m.HeartbeatAt.Add(HeartbeatTimeout))
}

// Stale reports whether the member is eligible but silent: its heartbeat is
// older than the timeout at now. The manager marks it OFFLINE. OPTED_OUT and
// OFFLINE members are never stale.
func (m Member) Stale(now time.Time) bool {
	return m.Status.Eligible() && !m.Online(now)
}

// Down reports whether the member is online and its core service is not
// running: a DONE task of this agent counts as failed.
func (m Member) Down(now time.Time) bool {
	return m.Status == MemberDown && m.Online(now)
}

// ParseMember reads a member record.
func ParseMember(r store.Record) (Member, error) {
	src := r.Canonical()
	f := newFields(src)
	m := Member{
		Status:          enum(f, "status", MemberActive, MemberDown, MemberOptedOut, MemberOffline),
		HeartbeatAt:     f.when("heartbeat_at"),
		RunningRevision: parseRevision(f, "running_revision"),
		src:             src,
	}
	if f.ok() && m.Status == "" {
		f.fail("status", "missing")
	}
	if err := f.err(); err != nil {
		return Member{}, err
	}
	return m, nil
}

// Record returns m as a store record.
func (m Member) Record() store.Record {
	r := m.src.Canonical()
	set(r, "status", string(m.Status))
	set(r, "heartbeat_at", m.HeartbeatAt)
	m.RunningRevision.writeTo(r, "running_revision")
	return r
}
