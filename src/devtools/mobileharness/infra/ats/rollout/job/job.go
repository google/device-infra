// Package job is one run of mtt-domain-manager: it reads a domain, resolves
// the upstream of each group, has the manager and the orchestrator decide,
// and applies their writes, all within the time one run is given.
//
// The steps are those of "Job" in node_auto_rollout_design.md. The decisions
// are made by pure functions in packages manager and orchestrator. This
// package holds what is left: the order of the steps, the time each may
// take, and what a run logs.
package job

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"time"

	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/apply"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/manager"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/orchestrator"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/record"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/store"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/view"
)

// writeTimeout bounds each store write. The rest of the budget is a share of
// Config.Timeout; see Run.
const writeTimeout = 5 * time.Second

// Resolver resolves the upstreams of a domain's groups. upstream.Resolver is
// one.
type Resolver interface {
	// Resolve sets Upstream, or UpstreamErr, on every valid group of d. It
	// returns when the deadline of ctx passes at the latest.
	Resolve(ctx context.Context, d *view.Domain)
}

// Config is what a Job is made of. Every field but Permanent is required.
type Config struct {
	// DomainID is the ID of the domain to run, in the form that
	// record.DomainID returns: the form in which the hosts derive it.
	DomainID string
	Store    store.Store
	Resolver Resolver
	// Timeout is the time the platform gives one run before it kills it: the
	// task timeout of the Cloud Run Job. Run derives its budget from it.
	Timeout time.Duration
	// Permanent reports whether an error of the resolver is one that does
	// not fix itself, such as an image that does not exist. A run logs such
	// an error as an error, and any other as a warning. Nil means that no
	// error is permanent.
	Permanent func(error) bool
	// Log receives what a run logs. Every line carries the domain ID.
	Log *slog.Logger
}

// Job runs one domain. It keeps no state between runs.
type Job struct {
	cfg Config
	log *slog.Logger
}

// New returns the job that cfg describes.
func New(cfg Config) (*Job, error) {
	id, err := record.DomainID(cfg.DomainID)
	switch {
	case err != nil:
		return nil, fmt.Errorf("job: the domain ID: %w", err)
	case id != cfg.DomainID:
		// The hosts would read and write another domain than this job.
		return nil, fmt.Errorf("job: domain ID %q is not in the form the hosts derive it in, %q", cfg.DomainID, id)
	case cfg.Store == nil:
		return nil, errors.New("job: the store is nil")
	case cfg.Resolver == nil:
		return nil, errors.New("job: the resolver is nil")
	case cfg.Timeout <= 0:
		return nil, fmt.Errorf("job: the timeout is %v, want more than zero", cfg.Timeout)
	case cfg.Log == nil:
		return nil, errors.New("job: the logger is nil")
	}
	return &Job{cfg: cfg, log: cfg.Log.With("domain", cfg.DomainID)}, nil
}

// Report is what one run decided and did.
type Report struct {
	// Manager holds the manager's decision for each group, in the order of
	// their IDs.
	Manager []manager.Decision
	// Orchestrator holds the orchestrator's decision for each current
	// rollout, in the order of the group IDs. It is empty if the run was out
	// of time before that pass.
	Orchestrator []orchestrator.Decision
	// Writes is the result of all the writes of the run.
	Writes apply.Result
}

// Run does one run. now is the single time of the run: every heartbeat and
// every lease is judged against it.
//
// The run has three quarters of Config.Timeout. When they are over it starts
// no further write and returns, leaving the rest to the next run; the last
// quarter is the margin for a write in progress to settle before the
// platform kills the run. Within that, resolving the upstreams has one sixth
// of Config.Timeout, and each write five seconds.
//
// The writes are applied in four stages, and the time is spent in that
// order: the manager's rows, the orchestrator's rows, the bookkeeping of
// both, and the cleanup. If the manager wrote anything, the orchestrator
// decides on a second snapshot, so that a rollout the manager created or
// stopped is driven in the same run. If the time is up before the
// orchestrator's pass, the run ends without it.
//
// Run returns an error only when the run fails as a whole: the domain could
// not be read, or its document is missing or invalid. It does not log that
// error; the caller does. Everything else is logged and left to the next
// run: an upstream that did not resolve, an anomaly, a write that failed or
// lost a race, and running out of time.
func (j *Job) Run(ctx context.Context, now time.Time) (Report, error) {
	start := time.Now()
	ctx, cancel := context.WithTimeout(ctx, j.cfg.Timeout*3/4)
	defer cancel()
	var rep Report

	d, err := j.read(ctx)
	if err != nil {
		return rep, err
	}
	reading := time.Since(start)
	resolving := j.resolve(ctx, &d)

	// Manager pass. Its rows are applied now; its bookkeeping waits for the
	// orchestrator's rows.
	rep.Manager = manager.Decide(d, now)
	rows := make([]apply.Decision, len(rep.Manager))
	rest := make([]apply.Decision, len(rep.Manager))
	for i, dec := range rep.Manager {
		j.logManager(ctx, &d.Groups[i], dec)
		rows[i].Row = dec.Writes.Row
		rest[i] = dec.Writes
		rest[i].Row = nil
	}
	opts := apply.Options{WriteTimeout: writeTimeout}
	rep.Writes = apply.Apply(ctx, j.cfg.Store, rows, opts)
	j.logWriteErrors(ctx, rep.Writes.Errs)

	if ctx.Err() == nil && rep.Writes.Attempted > 0 {
		// The first snapshot is out of date. The second one carries no
		// upstreams: the orchestrator reads none.
		again := time.Now()
		d, err = j.read(ctx)
		reading += time.Since(again)
	}
	switch {
	case ctx.Err() != nil:
		// The time is up, whichever step used it. The orchestrator does not
		// decide on a run that can no longer write: what it would decide is
		// the next run's to decide, on what the store holds then.
		rep.Writes.Exhausted = true
	case err == nil:
		j.orchestrate(ctx, d, now, rest, &rep)
	default:
		return rep, fmt.Errorf("after the manager's writes: %w", err)
	}

	level := slog.LevelInfo
	if rep.Writes.Exhausted {
		level = slog.LevelWarn
	}
	j.log.Log(ctx, level, "run finished",
		"writes", rep.Writes.Attempted,
		"write_errors", len(rep.Writes.Errs),
		"out_of_time", rep.Writes.Exhausted,
		"read_seconds", reading.Seconds(),
		"resolve_seconds", resolving.Seconds(),
		"total_seconds", time.Since(start).Seconds())
	return rep, nil
}

// read returns the domain as one snapshot shows it.
func (j *Job) read(ctx context.Context) (view.Domain, error) {
	snap, err := j.cfg.Store.Snapshot(ctx)
	if err != nil {
		return view.Domain{}, fmt.Errorf("opening a snapshot to read domain %q: %w", j.cfg.DomainID, err)
	}
	// The snapshot is read-only, so nothing is lost if releasing it fails.
	defer snap.Close(ctx)
	return view.Read(ctx, snap, j.cfg.DomainID)
}

// resolve fills in the upstream of every valid group of d, logs the ones
// that did not resolve, and returns the time it took.
func (j *Job) resolve(ctx context.Context, d *view.Domain) time.Duration {
	start := time.Now()
	resolveCtx, cancel := context.WithTimeout(ctx, j.cfg.Timeout/6)
	defer cancel()
	j.cfg.Resolver.Resolve(resolveCtx, d)
	for i := range d.Groups {
		g := &d.Groups[i]
		if g.Err != nil || g.UpstreamErr == nil {
			continue
		}
		level := slog.LevelWarn
		if j.cfg.Permanent != nil && j.cfg.Permanent(g.UpstreamErr) {
			level = slog.LevelError
		}
		j.log.Log(ctx, level, "upstream not resolved", "group", g.ID, "error", g.UpstreamErr)
	}
	return time.Since(start)
}

// orchestrate does the orchestrator pass on d and applies its writes
// together with rest, the writes that the manager pass left over.
func (j *Job) orchestrate(ctx context.Context, d view.Domain, now time.Time, rest []apply.Decision, rep *Report) {
	var decisions []apply.Decision
	for _, rv := range orchestrator.Views(d) {
		dec := orchestrator.Decide(rv, now)
		j.log.InfoContext(ctx, "orchestrator decided", "group", dec.GroupID, "row", string(dec.Row))
		j.logAnomalies(ctx, dec.GroupID, dec.Anomalies)
		rep.Orchestrator = append(rep.Orchestrator, dec)
		decisions = append(decisions, dec.Writes)
	}
	// The orchestrator's decisions come first, so that within each stage its
	// writes do. The manager's marks were decided on the first snapshot;
	// each is conditional on its member's version, and no decision of either
	// pass reads a stored mark, so applying them last changes no result.
	res := apply.Apply(ctx, j.cfg.Store, append(decisions, rest...), apply.Options{WriteTimeout: writeTimeout})
	j.logWriteErrors(ctx, res.Errs)
	rep.Writes.Attempted += res.Attempted
	rep.Writes.Errs = append(rep.Writes.Errs, res.Errs...)
	rep.Writes.Exhausted = rep.Writes.Exhausted || res.Exhausted
}

// logManager logs what the manager decided for g, with the upstream it
// decided on.
func (j *Job) logManager(ctx context.Context, g *view.Group, dec manager.Decision) {
	attrs := []any{"group", dec.GroupID, "row", string(dec.Row)}
	if g.Err == nil && g.Resolved() {
		attrs = append(attrs,
			"upstream_build", g.Upstream.Build,
			"upstream_tenant_config_version", g.Upstream.TenantConfigVersion,
			"upstream_pool_config_version", g.Upstream.PoolConfigVersion)
	}
	j.log.InfoContext(ctx, "manager decided", attrs...)
	j.logAnomalies(ctx, dec.GroupID, dec.Anomalies)
}

// logAnomalies logs the states of a group that only a human can repair.
func (j *Job) logAnomalies(ctx context.Context, groupID string, anomalies []string) {
	for _, a := range anomalies {
		j.log.ErrorContext(ctx, "anomaly", "group", groupID, "anomaly", a)
	}
}

// logWriteErrors logs the writes that did not land. Losing a race with an
// agent or an operator is how concurrent writers are meant to meet, and no
// warning.
func (j *Job) logWriteErrors(ctx context.Context, errs []error) {
	for _, err := range errs {
		if errors.Is(err, store.ErrVersionMismatch) {
			j.log.InfoContext(ctx, "write lost a race", "error", err)
		} else {
			j.log.WarnContext(ctx, "write failed", "error", err)
		}
	}
}
