package record

import (
	"fmt"
	"slices"
	"strings"
)

// Revision is what a group should run, or what a host runs: a build plus the
// Parameter Manager versions of the tenant and pool configs it was made
// from. The controller's revision has only a build. It is one encoding for
// rollouts.revision, members.running_revision, and last_rollout.revision.
type Revision struct {
	// Build is an image reference with a digest, such as
	// "asia-docker.pkg.dev/p/r/omnilab-ats@sha256:...".
	Build string
	// TenantConfigVersion and PoolConfigVersion are Parameter Manager version
	// resource IDs. They are compared for equality only, never ordered.
	TenantConfigVersion string
	PoolConfigVersion   string
}

// Digest returns the part of Build after "@". A build without a digest is
// its own digest. Builds are compared by digest so that repository spelling
// cannot cause a false mismatch.
func (r Revision) Digest() string {
	if _, digest, ok := strings.Cut(r.Build, "@"); ok && digest != "" {
		return digest
	}
	return r.Build
}

// Equal reports whether two revisions name the same build (by digest) and
// the same config versions.
func (r Revision) Equal(o Revision) bool {
	return r.Digest() == o.Digest() &&
		r.TenantConfigVersion == o.TenantConfigVersion &&
		r.PoolConfigVersion == o.PoolConfigVersion
}

// IsZero reports whether every field of r is empty. A zero revision is
// written as an absent field.
func (r Revision) IsZero() bool {
	return r == Revision{}
}

// parseRevision reads the revision map at name. An absent map is the zero
// revision; a present map must have a build, because writers omit a zero
// revision rather than store an empty or config-only map. A missing config
// version reads as "".
func parseRevision(f *fields, name string) Revision {
	sub, present := f.sub(name)
	rev := Revision{
		Build:               sub.str("build"),
		TenantConfigVersion: sub.str("tenant_config_version"),
		PoolConfigVersion:   sub.str("pool_config_version"),
	}
	if present && f.ok() && rev.Build == "" {
		f.fail(name, "has no build")
	}
	return rev
}

// writeTo writes r as the revision map at m[name], or removes the field for
// a zero revision. Keys it does not know are kept only if the existing map
// is the same revision; a new revision starts from an empty map, so that
// metadata about an old build does not carry over to a new one.
func (r Revision) writeTo(m map[string]any, name string) {
	if r.IsZero() {
		delete(m, name)
		return
	}
	sub := nested(m, name, func(existing map[string]any) bool {
		return existing["build"] == r.Build &&
			stringOrEmpty(existing["tenant_config_version"]) == r.TenantConfigVersion &&
			stringOrEmpty(existing["pool_config_version"]) == r.PoolConfigVersion
	})
	set(sub, "build", r.Build)
	set(sub, "tenant_config_version", r.TenantConfigVersion)
	set(sub, "pool_config_version", r.PoolConfigVersion)
}

// stringOrEmpty returns v as a string, or "" if it is not one.
func stringOrEmpty(v any) string {
	s, _ := v.(string)
	return s
}

// StopRequest asks the orchestrator to end a rollout.
type StopRequest string

// Stop requests. The empty string means none.
const (
	StopSuperseded StopRequest = "SUPERSEDED"
	StopCancelled  StopRequest = "CANCELLED"
)

// Outcome is how a rollout ended.
type Outcome string

// Outcomes. The empty string means the rollout is still in progress.
const (
	OutcomeSucceeded  Outcome = "SUCCEEDED"
	OutcomeIncomplete Outcome = "INCOMPLETE"
	OutcomeFailed     Outcome = "FAILED"
	OutcomeCancelled  Outcome = "CANCELLED"
	OutcomeSuperseded Outcome = "SUPERSEDED"
)

// SlotState is whether a slot is claimable, claimed, or closed.
type SlotState string

// Slot states.
const (
	SlotOpen    SlotState = "OPEN"
	SlotClaimed SlotState = "CLAIMED"
	SlotClosed  SlotState = "CLOSED"
)

// TaskResult is the result of a claimed slot.
type TaskResult string

// Task results. All but TaskRunning are final.
const (
	TaskRunning TaskResult = "RUNNING"
	TaskDone    TaskResult = "DONE"
	TaskFailed  TaskResult = "FAILED"
	TaskSkipped TaskResult = "SKIPPED"
	TaskLost    TaskResult = "LOST"
)

// Reasons an agent records with a FAILED task.
const (
	ReasonNotConverged = "not_converged"
	ReasonCoreDown     = "core_down"
)

// SummaryEntry is an agent's entry in a rollout's summary: its task result,
// or why it never had one.
type SummaryEntry string

// Summary entries beyond the final task results.
const (
	SummaryOffline    SummaryEntry = "OFFLINE"
	SummaryNotStarted SummaryEntry = "NOT_STARTED"
)

// MemberStatus is whether an agent is enrolled and its host is up.
type MemberStatus string

// Member statuses.
const (
	MemberActive   MemberStatus = "ACTIVE"
	MemberDown     MemberStatus = "DOWN"
	MemberOptedOut MemberStatus = "OPTED_OUT"
	MemberOffline  MemberStatus = "OFFLINE"
)

// Eligible reports whether the member may take part in a rollout: enrolled
// and online.
func (s MemberStatus) Eligible() bool {
	return s == MemberActive || s == MemberDown
}

// enum reads a string field that must be one of allowed. It returns "" only
// when the key is absent; a field that is present and empty is a problem,
// because writers omit a state rather than store "".
func enum[T ~string](f *fields, name string, allowed ...T) T {
	if !f.has(name) {
		return ""
	}
	v := T(f.str(name))
	if !f.ok() {
		return ""
	}
	if !slices.Contains(allowed, v) {
		f.fail(name, fmt.Sprintf("want one of %s, got %q", joinEnum(allowed), string(v)))
		return ""
	}
	return v
}

func joinEnum[T ~string](vs []T) string {
	s := make([]string, len(vs))
	for i, v := range vs {
		s[i] = string(v)
	}
	return strings.Join(s, "|")
}
