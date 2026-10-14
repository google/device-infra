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
	// StartFreeBytes and VolumeBytes are the effective free and total space
	// of the cache's volume when the pass started, before it evicted
	// anything. Both are zero if the pass stopped before measuring them, as
	// when another process held the lock.
	StartFreeBytes int64
	VolumeBytes    int64
	// Sample describes the buckets a TrimWith pass read before evicting, or
	// is nil if it read none.
	Sample *TrimSample
}

// TrimSample describes the buckets one TrimWith pass read, for the cap check
// and the cutoff age together (see progressiveSample).
type TrimSample struct {
	// Buckets is how many of the layout's TotalBuckets the pass read.
	Buckets      int
	TotalBuckets int
	// Files is how many blobs those buckets held, and Duration how long
	// reading them took.
	Files    int
	Duration time.Duration
	// SizeBytes is the size of the cache, estimated from the buckets read,
	// or exact once every bucket has been read. PinnedBytes is what those
	// buckets hold of blobs that other names pin: the whole cache's once
	// every bucket has been read.
	SizeBytes   int64
	PinnedBytes int64
	// CapCheckBuckets is how many buckets the cap check had read when it
	// reached its answer, or zero if the pass ran no cap check or stopped
	// before it finished. Fewer than TotalBuckets means that it stopped
	// early, finding the cache clearly under the cap.
	CapCheckBuckets int
}

// Exact reports whether the pass read every bucket, so that SizeBytes and
// PinnedBytes are the whole cache's.
func (s *TrimSample) Exact() bool {
	return s.Buckets == s.TotalBuckets
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
	result, err := s.lockedPass(ctx, false, TrimOptions{})
	result.Duration = time.Since(start)
	return result, err
}

// Trim runs a single eviction pass toward TargetFreeSpace, serialized across
// processes sharing the cache root, and reports what it did. It is the pass a
// detached evictor process runs, such as casdownloader -trim-only.
//
// Unlike CheckAndEvict, it does not wait for free space to fall below
// MinFreeSpace. It computes need = TargetFreeSpace - free, and does nothing
// when that is not positive. Otherwise it samples buckets for a cutoff age and
// unlinks blobs older than the cutoff until about need bytes are reclaimed or
// free space reaches TargetFreeSpace, whichever comes first. The byte stop
// overshoots by at most one blob; the free-space stop is coarser, because
// free space is only re-checked every BatchSize unlinks.
//
// The trigger is left to the caller, which has already decided that a trim
// is worth starting; gating on MinFreeSpace again here would make a run that
// asks for a higher target than the current free space a silent no-op.
//
// Like CheckAndEvict, it exits early when the sample shows the target is out
// of reach, reports a peer holding the lock as TrimOutcomeBusy with a nil
// error, and reports a context deadline as TrimOutcomeTimedOut with a nil
// error. Stopping partway is safe because the cache is content-addressed.
func (s *Storage) Trim(ctx context.Context) (TrimResult, error) {
	return s.TrimWith(ctx, TrimOptions{})
}

// TrimOptions refines the pass TrimWith runs. The zero value is Trim's pass.
type TrimOptions struct {
	// MaxSize is an advisory cap on the cache's total size, in bytes. Zero
	// or less means no cap.
	MaxSize int64
}

// TrimWith is Trim with an advisory cap on the cache's size.
//
// The pass reads the layout's buckets in a random order, in steps, keeping
// what it has read: the cap check (see progressiveSample.checkCap) reads 32,
// 64, 128 and then all 256 buckets of Layout256, and stops early only once
// the cache is clearly under opts.MaxSize. Only a full read, whose size is
// exact, can find the cache over the cap, and then
//
//	need = max(TargetFreeSpace - free, size - 0.9 * MaxSize)
//
// rather than TargetFreeSpace - free. Keeping the cache at 90% of the cap
// rather than at the cap leaves room for the downloads between passes. Each
// cap check touches CapCheckName as it starts, early stop, timeout or not,
// which tells downloads when the last one ran.
//
// If need is positive, the pass reads at least SampleBuckets buckets, takes
// its cutoff age from the histogram of every bucket read so far, and unlinks
// blobs older than the cutoff until about need bytes are reclaimed or free
// space has grown by need.
func (s *Storage) TrimWith(ctx context.Context, opts TrimOptions) (TrimResult, error) {
	start := time.Now()
	result, err := s.lockedPass(ctx, true, opts)
	result.Duration = time.Since(start)
	return result, err
}

// lockedPass takes the cross-process eviction lock without blocking and runs
// one one-shot pass under it. toTarget selects Trim's semantics over
// CheckAndEvict's, and opts refines them; see oneShotPass.
func (s *Storage) lockedPass(ctx context.Context, toTarget bool, opts TrimOptions) (TrimResult, error) {
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

	return s.evictor.oneShotPass(ctx, toTarget, opts)
}

// EvictionInProgress reports whether another process holds the cross-process
// eviction lock of the cache rooted at rootDir, without waiting for it.
//
// It takes the lock and drops it at once, so it is a probe, not a
// reservation: a process that starts an evictor because of a false answer may
// find that a peer started one first, which the evictor handles by exiting.
func EvictionInProgress(rootDir string) (bool, error) {
	lock, err := tryLockEviction(rootDir)
	if errors.Is(err, ErrEvictionBusy) {
		return true, nil
	}
	if err != nil {
		return false, err
	}
	lock.release()
	return false, nil
}

// oneShotPass is the one-shot counterpart of EvictIfNeeded. It runs a single
// eviction flight, letting the flight exit early when the target is out of
// reach, and reports the result as a TrimResult.
//
// With toTarget false, as for CheckAndEvict, the flight runs only if
// effective free space has fallen below MinFreeSpace, and it stops when free
// space reaches TargetFreeSpace. With toTarget true, as for TrimWith, the
// pass first reads buckets as sampleForTrim describes; the flight runs
// whenever free space is below TargetFreeSpace or opts' cap is exceeded, and
// it also stops once it has reclaimed the shortfall it measured at the start.
// opts is read only with toTarget.
func (e *Evictor) oneShotPass(ctx context.Context, toTarget bool, opts TrimOptions) (TrimResult, error) {
	stats, err := e.statfs(e.rootDir)
	if err != nil {
		return TrimResult{Outcome: TrimOutcomeError}, fmt.Errorf("failed to query disk capacity for %s: %w", e.rootDir, err)
	}

	snap := e.snapshot(stats)
	effectiveTotal, effectiveFree, _ := ComputeEffectiveSpace(stats, snap.reservedBytes)
	monitoring.RecordStorageUsage(effectiveTotal, effectiveFree)
	result := TrimResult{StartFreeBytes: effectiveFree, VolumeBytes: effectiveTotal}

	f := e.newFlight(snap)
	f.exitIfUnreachable = true
	f.stopAtReclaimTarget = toTarget
	if toTarget {
		p := e.newProgressiveSample(snap.minBlobAge)
		var evict bool
		evict, f.minReclaim, err = e.sampleForTrim(ctx, p, snap, effectiveFree, opts.MaxSize)
		result.Sample = p.report()
		if errors.Is(err, context.DeadlineExceeded) {
			log.InfoContextf(ctx, "Storage eviction pass stopped at its deadline while reading buckets.")
			result.Outcome = TrimOutcomeTimedOut
			return result, nil
		}
		if err != nil {
			result.Outcome = TrimOutcomeError
			return result, err
		}
		if !evict {
			result.Outcome = TrimOutcomeNoop
			return result, nil
		}
		f.progressive = p
	} else if effectiveFree >= snap.minFreeBytes {
		result.Outcome = TrimOutcomeNoop
		return result, nil
	}

	r := f.execute(ctx)
	if r.outcome == TrimOutcomeTimedOut {
		log.InfoContextf(ctx, "Storage eviction pass stopped at its deadline after reclaiming %.2f GB across %d files.",
			float64(r.reclaimed)/gib, r.deleted)
	}

	result.Outcome = r.outcome
	result.BytesReclaimed = r.reclaimed
	result.FilesDeleted = r.deleted
	result.SkippedLinked = r.skippedLinkedBytes
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
