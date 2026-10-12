package storage

import (
	"context"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"syscall"
	"time"

	log "github.com/golang/glog"
	"github.com/google/device-infra/src/devtools/rbe/common/monitoring"
)

// ErrEvictionBusy reports that another process already holds the cross-process
// eviction lock. It is an expected, benign outcome rather than a failure: the
// other process is doing the work, so the caller should either proceed or wait
// for free space to appear.
var ErrEvictionBusy = errors.New("storage: another process is already evicting")

// evictLockName is the cross-process eviction lock file, placed directly in
// the cache root. The leading dot and the root-level placement both keep it
// invisible to the evictor, which only scans the layout's leaf buckets for
// 64-character non-dot entries.
const evictLockName = ".evict.lock"

// evictLock is a held cross-process eviction lock.
type evictLock struct {
	f *os.File
}

// tryLockEviction attempts to take the cross-process eviction lock without
// blocking, returning ErrEvictionBusy if a peer already holds it.
//
// Why a lock at all, when eviction is otherwise lock-free:
//
// Unlinking a blob is safe to race; deciding how much to unlink is not. On a
// host running dozens of CLI invocations against one shared cache (the
// Cuttlefish testbeds run roughly sixty containers per machine), a dip below
// the low watermark is observed by every process at once. Without
// coordination all of them would sample, compute a cutoff from the same
// pre-eviction statfs, and then evict that full amount independently,
// collectively reclaiming dozens of times more than necessary and flushing a
// cache that took hours to warm. The lock reduces that thundering herd to a
// single flight while everyone else keeps working.
//
// flock(2) is the right primitive here specifically because these are
// short-lived CLI processes:
//
//   - The kernel drops the lock when the file descriptor closes, including on
//     SIGKILL, OOM-kill or a panic. A lock file containing a PID would instead
//     need stale-owner detection, and would deadlock the whole host the first
//     time a downloader was killed mid-eviction.
//   - It is advisory and costs one open plus one syscall, so a process that
//     never evicts never pays for it.
//   - It nests correctly with the evictor's in-process single-flight gate:
//     flock is owned by the open file description, so even two goroutines in
//     one process that open the file separately will contend as expected.
func tryLockEviction(rootDir string) (*evictLock, error) {
	path := filepath.Join(rootDir, evictLockName)
	f, err := os.OpenFile(path, os.O_CREATE|os.O_RDWR, 0644)
	if err != nil {
		return nil, fmt.Errorf("failed to open eviction lock %s: %w", path, err)
	}

	if err := syscall.Flock(int(f.Fd()), syscall.LOCK_EX|syscall.LOCK_NB); err != nil {
		f.Close()
		if errors.Is(err, syscall.EWOULDBLOCK) {
			return nil, ErrEvictionBusy
		}
		return nil, fmt.Errorf("failed to acquire eviction lock %s: %w", path, err)
	}
	return &evictLock{f: f}, nil
}

// release drops the lock. Closing the descriptor is what releases the flock;
// the lock file itself is intentionally left in place so that it keeps a
// stable identity across processes.
func (l *evictLock) release() {
	l.f.Close()
}

// ConfigureEvictor attaches an evictor to the storage without starting the
// background polling loop. The evictor walks the storage's Layout.
//
// This is the entry point for short-lived command line tools, which have no
// use for a ticker that would outlive the process by microseconds but still
// need CheckAndEvict. Long-running servers should call StartEvictor instead.
func (s *Storage) ConfigureEvictor(cfg EvictorConfig) error {
	ev, err := NewEvictor(s.rootDir, cfg, nil)
	if err != nil {
		return err
	}
	s.SetEvictor(ev)
	return nil
}

// Outcomes of a one-shot eviction pass, as reported in TrimResult.Outcome.
// They are meant to become values of a metric field, so an existing value
// should not be renamed.
const (
	// TrimOutcomeNoop means the pass deleted nothing: free space was already
	// above the threshold, or the pass found nothing it could reclaim and
	// exited early.
	TrimOutcomeNoop = "noop"
	// TrimOutcomeEvicted means the pass ran to completion. It may still have
	// fallen short of its target.
	TrimOutcomeEvicted = "evicted"
	// TrimOutcomeBusy means another process or goroutine was already evicting,
	// so this pass did nothing.
	TrimOutcomeBusy = "busy"
	// TrimOutcomeTimedOut means the context deadline expired partway through
	// the pass. Whatever was deleted until then stays deleted, which is safe
	// because the cache is content-addressed.
	TrimOutcomeTimedOut = "timed_out"
	// TrimOutcomeError means the pass failed. The accompanying error says why.
	TrimOutcomeError = "error"
)

// TrimResult describes one one-shot eviction pass.
//
// It replaces the (reclaimedBytes, filesDeleted, err) tuple on the one-shot
// path, because a short-lived process needs to tell apart outcomes that the
// tuple folds together: a pass that found nothing to do, one that another
// process was already running, and one cut short by its deadline all look
// like (0, 0, nil) there.
type TrimResult struct {
	// Outcome is one of the TrimOutcome constants.
	Outcome string
	// BytesReclaimed and FilesDeleted count the blobs this pass unlinked.
	BytesReclaimed int64
	FilesDeleted   int
	// SkippedLinked is the size of the blobs the pass left in place because
	// other names still pin their bytes (see isLinkedElsewhere). When the
	// pass exits early, it is what the sampled buckets held of such blobs.
	SkippedLinked int64
	// Duration is the wall time of the pass, including taking the lock.
	Duration time.Duration
}

// CheckAndEvict runs a single eviction pass if free space has fallen below
// MinFreeSpace, serialized across processes sharing the cache root, and
// reports what it did.
//
// The pass exits early, deleting nothing, when the sampled buckets show that
// TargetFreeSpace cannot be reached by eviction: every blob they hold is
// either pinned by another name or still inside its MinBlobAge grace period.
// Walking every bucket would then only cost time.
//
// A peer process already evicting is reported as TrimOutcomeBusy with a nil
// error; callers driving a CLI should treat that as success. The error is
// non-nil only for TrimOutcomeError.
func (s *Storage) CheckAndEvict(ctx context.Context) (TrimResult, error) {
	start := time.Now()
	result, err := s.checkAndEvict(ctx)
	result.Duration = time.Since(start)
	return result, err
}

func (s *Storage) checkAndEvict(ctx context.Context) (TrimResult, error) {
	if s.evictor == nil {
		return TrimResult{Outcome: TrimOutcomeError}, errors.New("storage: evictor is not configured; call ConfigureEvictor or StartEvictor first")
	}

	lock, err := tryLockEviction(s.rootDir)
	if errors.Is(err, ErrEvictionBusy) {
		return TrimResult{Outcome: TrimOutcomeBusy}, nil
	}
	if err != nil {
		return TrimResult{Outcome: TrimOutcomeError}, err
	}
	defer lock.release()

	return s.evictor.trimIfNeeded(ctx)
}

// trimIfNeeded is the one-shot counterpart of EvictIfNeeded. It runs a
// single eviction flight if effective free space has fallen below
// MinFreeSpace, letting the flight exit early when the target is out of
// reach, and reports the result as a TrimResult.
func (e *Evictor) trimIfNeeded(ctx context.Context) (TrimResult, error) {
	stats, err := e.statfs(e.rootDir)
	if err != nil {
		return TrimResult{Outcome: TrimOutcomeError}, fmt.Errorf("failed to query disk capacity for %s: %w", e.rootDir, err)
	}

	snap := e.snapshot(stats)
	effectiveTotal, effectiveFree, _ := ComputeEffectiveSpace(stats, snap.reservedBytes)
	monitoring.RecordStorageUsage(effectiveTotal, effectiveFree)

	if effectiveFree >= snap.minFreeBytes {
		return TrimResult{Outcome: TrimOutcomeNoop}, nil
	}

	f := e.newFlight(snap)
	f.exitIfUnreachable = true
	r := f.execute(ctx)
	if r.outcome == TrimOutcomeTimedOut {
		log.InfoContextf(ctx, "Storage eviction pass stopped at its deadline after reclaiming %.2f GB across %d files.",
			float64(r.reclaimed)/gib, r.deleted)
	}

	result := TrimResult{
		Outcome:        r.outcome,
		BytesReclaimed: r.reclaimed,
		FilesDeleted:   r.deleted,
		SkippedLinked:  r.skippedLinkedBytes,
	}
	if r.outcome != TrimOutcomeError {
		return result, nil
	}
	return result, r.err
}

// EvictIfNeeded runs a single eviction flight if effective free space has
// fallen below MinFreeSpace, and reports what it reclaimed.
//
// Unlike EvictOnce it is a no-op while the cache is comfortably below the low
// watermark, which makes it the right call for a periodic or per-invocation
// check.
func (e *Evictor) EvictIfNeeded(ctx context.Context) (reclaimedBytes int64, filesDeleted int, err error) {
	stats, err := e.statfs(e.rootDir)
	if err != nil {
		return 0, 0, fmt.Errorf("failed to query disk capacity for %s: %w", e.rootDir, err)
	}

	snap := e.snapshot(stats)
	effectiveTotal, effectiveFree, _ := ComputeEffectiveSpace(stats, snap.reservedBytes)
	monitoring.RecordStorageUsage(effectiveTotal, effectiveFree)

	if effectiveFree >= snap.minFreeBytes {
		return 0, 0, nil
	}
	return e.newFlight(snap).run(ctx)
}
