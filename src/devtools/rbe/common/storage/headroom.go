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
// invisible to the evictor, which only scans two-level leaf buckets for
// 64-character non-dot entries.
const evictLockName = ".evict.lock"

// These are variables rather than constants only so that tests can shorten the
// peer-waiting path; nothing in production reassigns them.
var (
	// headroomWaitTimeout bounds how long EnsureHeadroom waits for a peer
	// process to finish reclaiming space before giving up and evaluating the
	// disk on its own terms.
	headroomWaitTimeout = 30 * time.Second

	// headroomPollInterval is how often EnsureHeadroom re-checks free space
	// while a peer holds the eviction lock. A statfs is a handful of
	// microseconds, so polling this often costs nothing even with dozens of
	// waiters.
	headroomPollInterval = 250 * time.Millisecond
)

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
// background polling loop.
//
// This is the entry point for short-lived command line tools, which have no
// use for a ticker that would outlive the process by microseconds but still
// need CheckAndEvict and EnsureHeadroom. Long-running servers should call
// StartEvictor instead.
func (s *Storage) ConfigureEvictor(cfg EvictorConfig) error {
	ev, err := NewEvictor(s.rootDir, cfg, nil)
	if err != nil {
		return err
	}
	s.evictor = ev
	return nil
}

// CheckAndEvict runs a single eviction pass if free space has fallen below
// MinFreeSpace, serialized across processes sharing the cache root.
//
// It returns ErrEvictionBusy, with no work done, when a peer process is
// already evicting. Callers driving a CLI should treat that as success.
func (s *Storage) CheckAndEvict(ctx context.Context) (reclaimedBytes int64, filesDeleted int, err error) {
	if s.evictor == nil {
		return 0, 0, errors.New("storage: evictor is not configured; call ConfigureEvictor or StartEvictor first")
	}

	lock, err := tryLockEviction(s.rootDir)
	if err != nil {
		return 0, 0, err
	}
	defer lock.release()

	return s.evictor.EvictIfNeeded(ctx)
}

// EnsureHeadroom makes room for an upcoming write of requiredBytes, evicting
// first if necessary, and reports an error only when the write cannot possibly
// succeed.
//
// This is the pre-flight counterpart to the evictor's reactive polling. The
// production failure it exists to prevent is that eviction has historically
// only ever run *after* bytes landed on disk, so a burst of large concurrent
// downloads could exhaust the volume and fail with ENOSPC long before any
// trimming was triggered. Calling EnsureHeadroom before a fetch moves the
// check ahead of the write.
//
// The satisfied condition is effectiveFree >= MinFreeSpace + requiredBytes,
// that is, the write must fit and must still leave the low watermark intact
// afterwards. When it is not satisfied, eviction runs with its usual target
// raised by requiredBytes so that a sequence of fetches does not re-trigger a
// flight apiece.
//
// Failing to reach that goal is not by itself an error. The low watermark is a
// safety buffer, and refusing to write while the disk demonstrably has room
// would convert a tuning problem into an outage. EnsureHeadroom therefore logs
// a warning and returns nil whenever the write still fits, and returns an
// error only when free space is genuinely below requiredBytes.
//
// On a healthy disk the entire call is a single statfs.
//
// # This is advisory, not a reservation
//
// EnsureHeadroom checks and returns; it holds nothing back. Between the check
// and the caller's write, any other process sharing this cache root can take
// the space that was just confirmed free. Under a burst, several processes can
// each observe the same free bytes, each independently conclude that their own
// write fits, and collectively over-commit the volume. The cross-process
// eviction lock neither prevents that nor is meant to: it serializes eviction
// flights, not the space each caller is about to consume, and the healthy path
// above returns before that lock is ever reached.
//
// So this narrows the ENOSPC window rather than closing it. Narrowing is still
// worth doing, because the reactive evictor cannot act until the bytes have
// already landed. Closing the window entirely requires that a single process
// own every write into the cache, so that all pending writes can be counted
// against free space before the first byte of any of them is fetched. That is
// technically feasible, but it is a change to which process owns the cache
// rather than something this function can fix on its own.
func (s *Storage) EnsureHeadroom(ctx context.Context, requiredBytes int64) error {
	if requiredBytes < 0 {
		return fmt.Errorf("storage: requiredBytes must not be negative, got %d", requiredBytes)
	}
	if s.evictor == nil {
		return errors.New("storage: evictor is not configured; call ConfigureEvictor or StartEvictor first")
	}
	e := s.evictor

	s.counters.headroomChecks.Add(1)

	free, snap, err := e.effectiveFree()
	if err != nil {
		return err
	}
	if free >= snap.minFreeBytes+requiredBytes {
		return nil
	}

	// Either reclaim the space ourselves or, if a peer is already doing it,
	// wait for its flight to bear fruit rather than piling on.
	deadline := time.Now().Add(headroomWaitTimeout)
	for {
		lock, lockErr := tryLockEviction(s.rootDir)
		if lockErr == nil {
			reclaimed, deleted, evictErr := e.EvictForHeadroom(ctx, requiredBytes)
			lock.release()
			if evictErr != nil {
				return fmt.Errorf("failed to reclaim space for a %s write: %w", formatBytes(requiredBytes), evictErr)
			}
			s.counters.headroomEvictions.Add(1)
			s.counters.headroomReclaimedBytes.Add(reclaimed)
			log.InfoContextf(ctx, "Storage headroom eviction reclaimed %s across %d files to make room for a %s write.",
				formatBytes(reclaimed), deleted, formatBytes(requiredBytes))
			break
		}
		if !errors.Is(lockErr, ErrEvictionBusy) {
			return lockErr
		}

		// A peer holds the lock. Poll until its flight frees enough space.
		free, snap, err = e.effectiveFree()
		if err != nil {
			return err
		}
		if free >= snap.minFreeBytes+requiredBytes {
			return nil
		}
		if time.Now().After(deadline) {
			s.counters.headroomPeerWaits.Add(1)
			if s.counters.firstPeerWait.first() {
				log.WarningContextf(ctx, "Storage headroom: waited %v for a peer process to reclaim space for a %s write; proceeding with the space currently available. "+
					"Only the first occurrence is logged; the rest are counted in the storage summary.",
					headroomWaitTimeout, formatBytes(requiredBytes))
			}
			break
		}
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-time.After(headroomPollInterval):
		}
	}

	free, snap, err = e.effectiveFree()
	if err != nil {
		return err
	}
	if free >= snap.minFreeBytes+requiredBytes {
		return nil
	}
	if free >= requiredBytes {
		s.counters.headroomBelowWatermark.Add(1)
		if s.counters.firstBelowWatermark.first() {
			log.WarningContextf(ctx, "Storage headroom: a %s write fits but will leave only %s free, below the %s low watermark. The cache is under sustained pressure. "+
				"A cache that has settled at its watermark trips this on every subsequent write, so only the first occurrence is logged; "+
				"the running total is reported in the storage summary.",
				formatBytes(requiredBytes), formatBytes(free-requiredBytes), formatBytes(snap.minFreeBytes))
		}
		return nil
	}

	return fmt.Errorf("storage: cannot make room for a %s write in %s: only %s free after eviction (reserved %s is excluded). "+
		"Reduce download concurrency, lower ReservedSpaceGB, or provision a larger cache volume",
		formatBytes(requiredBytes), s.rootDir, formatBytes(free), formatBytes(snap.reservedBytes))
}

// effectiveFree returns the current effective free bytes alongside the
// configuration snapshot the number was evaluated against, so that callers
// compare free space against thresholds derived from the same statfs.
func (e *Evictor) effectiveFree() (int64, snapshotConfig, error) {
	stats, err := e.statfs(e.rootDir)
	if err != nil {
		return 0, snapshotConfig{}, fmt.Errorf("failed to query disk capacity for %s: %w", e.rootDir, err)
	}
	snap := e.snapshot(stats)
	_, free, _ := ComputeEffectiveSpace(stats, snap.reservedBytes)
	return free, snap, nil
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

// EvictForHeadroom runs a single eviction flight with the target free-space
// threshold raised by extraBytes, so that the caller's pending write still
// leaves the cache at its normal target afterwards.
func (e *Evictor) EvictForHeadroom(ctx context.Context, extraBytes int64) (reclaimedBytes int64, filesDeleted int, err error) {
	if extraBytes < 0 {
		return 0, 0, fmt.Errorf("extraBytes must not be negative, got %d", extraBytes)
	}

	stats, err := e.statfs(e.rootDir)
	if err != nil {
		return 0, 0, fmt.Errorf("failed to query disk capacity for %s: %w", e.rootDir, err)
	}

	snap := e.snapshot(stats)
	effectiveTotal, _, _ := ComputeEffectiveSpace(stats, snap.reservedBytes)

	// Saturate rather than add and then clamp. A request large enough to push
	// the goal past MaxInt64 would wrap it negative, and the flight compares
	// free space against that goal: it would decide it already had room to
	// spare and evict nothing at all, which is the exact opposite of what the
	// caller asked for. Capping at the volume's capacity loses nothing either
	// way, since reclaiming the whole volume is already the most an eviction
	// pass could possibly achieve, and the caller's own failure path is what
	// reports the shortfall.
	if extraBytes > effectiveTotal-snap.targetFreeBytes {
		snap.targetFreeBytes = effectiveTotal
	} else {
		snap.targetFreeBytes += extraBytes
	}

	return e.newFlight(snap).run(ctx)
}

// formatBytes renders a byte count in the largest unit that keeps it readable,
// for use in operator-facing log lines and error messages.
func formatBytes(b int64) string {
	switch {
	case b >= 1024*1024*1024:
		return fmt.Sprintf("%.2f GiB", float64(b)/gib)
	case b >= 1024*1024:
		return fmt.Sprintf("%.2f MiB", float64(b)/(1024*1024))
	case b >= 1024:
		return fmt.Sprintf("%.2f KiB", float64(b)/1024)
	default:
		return fmt.Sprintf("%d B", b)
	}
}
