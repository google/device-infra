package storage

import (
	"bufio"
	"context"
	"errors"
	"fmt"
	"io/fs"
	"math"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

// diskModel is a fake filesystem whose free space is derived from the blobs
// actually present in the cache, so eviction visibly reclaims space exactly the
// way it would on a real volume instead of against a hand-maintained counter.
type diskModel struct {
	root string
	// totalBytes is the capacity of the simulated volume.
	totalBytes int64
	// otherTenantBytes is space consumed on the same volume by something other
	// than the cache, which eviction can never reclaim.
	otherTenantBytes int64
}

func (m *diskModel) statfs(string) (DiskStats, error) {
	var used int64
	err := filepath.WalkDir(m.root, func(_ string, d fs.DirEntry, err error) error {
		if err != nil || d.IsDir() {
			return nil
		}
		if info, infoErr := d.Info(); infoErr == nil {
			used += info.Size()
		}
		return nil
	})
	if err != nil {
		return DiskStats{}, err
	}

	free := m.totalBytes - m.otherTenantBytes - used
	if free < 0 {
		free = 0
	}
	return DiskStats{TotalBytes: m.totalBytes, FreeBytes: free}, nil
}

// headroomTestConfig returns an evictor configuration sized for the small
// simulated volumes used below. ReservedSpaceGB must be zero because it is
// denominated in whole gigabytes, which would swallow the entire fake volume.
func headroomTestConfig() EvictorConfig {
	cfg := DefaultEvictorConfig()
	cfg.ReservedSpaceGB = 0
	return cfg
}

// seedBlobs writes n blobs of the given size and backdates them well past the
// evictor's grace period so they are immediately eligible for eviction.
//
// The first two bytes of each hash are varied so the blobs land in distinct
// leaf buckets. Naively formatting the index would zero-pad it and pile every
// blob into shard 00/00, which would leave the sharded walk untested.
func seedBlobs(t *testing.T, s *Storage, n, size int) {
	t.Helper()
	data := make([]byte, size)
	stale := time.Now().Add(-72 * time.Hour)

	for i := 0; i < n; i++ {
		hash := fmt.Sprintf("%02x%02x%060x", i%256, (i/256)%256, i)
		if err := s.Write(hash, data); err != nil {
			t.Fatalf("Failed to seed blob %d: %v", i, err)
		}
		if err := os.Chtimes(s.blobPath(hash), stale, stale); err != nil {
			t.Fatalf("Failed to backdate blob %d: %v", i, err)
		}
	}
}

// countBlobs returns how many cache blobs remain on disk.
func countBlobs(t *testing.T, s *Storage) int {
	t.Helper()
	count := 0
	err := filepath.WalkDir(s.RootDir(), func(_ string, d fs.DirEntry, err error) error {
		if err != nil || d.IsDir() {
			return nil
		}
		if isCandidateBlob(d.Name(), false) {
			count++
		}
		return nil
	})
	if err != nil {
		t.Fatalf("Failed to walk the cache root: %v", err)
	}
	return count
}

// newPressuredStorage builds a Storage holding blobCount blobs on a simulated
// volume of totalBytes, wired to a disk model that reflects real deletions.
func newPressuredStorage(t *testing.T, totalBytes int64, blobCount, blobSize int) (*Storage, *diskModel) {
	t.Helper()
	s := setupTestStorage(t)
	seedBlobs(t, s, blobCount, blobSize)

	model := &diskModel{root: s.RootDir(), totalBytes: totalBytes}
	ev, err := NewEvictor(s.RootDir(), headroomTestConfig(), model.statfs)
	if err != nil {
		t.Fatalf("Failed to create the evictor: %v", err)
	}
	s.SetEvictor(ev)
	return s, model
}

// shortenHeadroomWaits makes the peer-waiting path finish quickly so tests do
// not pay the production 30 second timeout.
func shortenHeadroomWaits(t *testing.T) {
	t.Helper()
	origTimeout, origInterval := headroomWaitTimeout, headroomPollInterval
	headroomWaitTimeout = 300 * time.Millisecond
	headroomPollInterval = 10 * time.Millisecond
	t.Cleanup(func() {
		headroomWaitTimeout, headroomPollInterval = origTimeout, origInterval
	})
}

func TestTryLockEviction_ExcludesASecondHolder(t *testing.T) {
	dir := t.TempDir()

	first, err := tryLockEviction(dir)
	if err != nil {
		t.Fatalf("Failed to acquire the eviction lock: %v", err)
	}

	if _, err := tryLockEviction(dir); !errors.Is(err, ErrEvictionBusy) {
		t.Errorf("Expected ErrEvictionBusy while the lock is held, got %v", err)
	}

	first.release()

	second, err := tryLockEviction(dir)
	if err != nil {
		t.Fatalf("Failed to acquire the eviction lock after release: %v", err)
	}
	second.release()
}

// TestTryLockEviction_LockFileIsInvisibleToEviction guards against the lock
// file itself being mistaken for a blob and unlinked, which would let two
// processes believe they each hold the lock.
func TestTryLockEviction_LockFileIsInvisibleToEviction(t *testing.T) {
	if isCandidateBlob(evictLockName, false) {
		t.Errorf("The eviction lock file %q is treated as an evictable blob", evictLockName)
	}
	if !strings.HasPrefix(evictLockName, ".") {
		t.Errorf("The eviction lock file %q must be dot-prefixed", evictLockName)
	}
}

// TestEvictLock_AcrossProcesses verifies the two properties that make flock the
// right choice for short-lived CLI invocations: a peer process is genuinely
// excluded, and the kernel releases the lock when that peer is killed without
// ever getting to run cleanup code.
func TestEvictLock_AcrossProcesses(t *testing.T) {
	dir := t.TempDir()

	cmd := exec.Command(os.Args[0], "-test.run=^TestEvictLockHelperProcess$")
	cmd.Env = append(os.Environ(), evictLockHelperEnv+"="+dir)
	stdout, err := cmd.StdoutPipe()
	if err != nil {
		t.Fatalf("Failed to create a stdout pipe: %v", err)
	}
	if err := cmd.Start(); err != nil {
		t.Fatalf("Failed to start the helper process: %v", err)
	}
	t.Cleanup(func() {
		cmd.Process.Kill()
		cmd.Wait()
	})

	locked := make(chan struct{})
	go func() {
		scanner := bufio.NewScanner(stdout)
		for scanner.Scan() {
			if strings.Contains(scanner.Text(), evictLockHelperReady) {
				close(locked)
				return
			}
		}
	}()

	select {
	case <-locked:
	case <-time.After(60 * time.Second):
		t.Fatalf("The helper process did not report acquiring the lock")
	}

	if _, err := tryLockEviction(dir); !errors.Is(err, ErrEvictionBusy) {
		t.Errorf("Expected ErrEvictionBusy while another process holds the lock, got %v", err)
	}

	// SIGKILL gives the helper no chance to release anything, so a successful
	// acquisition below can only come from the kernel closing its descriptors.
	if err := cmd.Process.Kill(); err != nil {
		t.Fatalf("Failed to kill the helper process: %v", err)
	}
	cmd.Wait()

	// Reaping is asynchronous, so allow the descriptor teardown a moment.
	deadline := time.Now().Add(30 * time.Second)
	for {
		lock, err := tryLockEviction(dir)
		if err == nil {
			lock.release()
			return
		}
		if !errors.Is(err, ErrEvictionBusy) {
			t.Fatalf("Failed to acquire the eviction lock after the holder died: %v", err)
		}
		if time.Now().After(deadline) {
			t.Fatalf("The eviction lock was never released after its holder was killed")
		}
		time.Sleep(50 * time.Millisecond)
	}
}

const (
	evictLockHelperEnv   = "STORAGE_EVICT_LOCK_HELPER_DIR"
	evictLockHelperReady = "EVICT_LOCK_ACQUIRED"
)

// TestEvictLockHelperProcess is not a test. It is the child half of
// TestEvictLock_AcrossProcesses, re-executed from the same test binary, and it
// is skipped during an ordinary run.
func TestEvictLockHelperProcess(t *testing.T) {
	dir := os.Getenv(evictLockHelperEnv)
	if dir == "" {
		t.Skip("helper process for TestEvictLock_AcrossProcesses")
	}

	if _, err := tryLockEviction(dir); err != nil {
		fmt.Printf("failed to acquire the eviction lock: %v\n", err)
		os.Exit(1)
	}
	fmt.Println(evictLockHelperReady)

	// Hold the lock until the parent kills us. The bound is a backstop so a
	// failed parent cannot leave this process behind indefinitely.
	time.Sleep(5 * time.Minute)
}

func TestCheckAndEvict_RequiresAnEvictor(t *testing.T) {
	s := setupTestStorage(t)
	if _, _, err := s.CheckAndEvict(context.Background()); err == nil {
		t.Errorf("Expected an error when no evictor is configured, got nil")
	}
}

func TestCheckAndEvict_ReportsBusy(t *testing.T) {
	s, _ := newPressuredStorage(t, 11*1024*1024, 1200, 8192)

	lock, err := tryLockEviction(s.RootDir())
	if err != nil {
		t.Fatalf("Failed to acquire the eviction lock: %v", err)
	}
	defer lock.release()

	before := countBlobs(t, s)
	_, _, err = s.CheckAndEvict(context.Background())
	if !errors.Is(err, ErrEvictionBusy) {
		t.Errorf("Expected ErrEvictionBusy, got %v", err)
	}
	if after := countBlobs(t, s); after != before {
		t.Errorf("Expected no eviction while a peer holds the lock, blob count went from %d to %d", before, after)
	}
}

func TestCheckAndEvict_EvictsUnderPressure(t *testing.T) {
	s, model := newPressuredStorage(t, 11*1024*1024, 1200, 8192)

	before, err := model.statfs("")
	if err != nil {
		t.Fatalf("statfs failed: %v", err)
	}
	snap := s.Evictor().snapshot(before)
	if before.FreeBytes >= snap.minFreeBytes {
		t.Fatalf("Test setup is wrong: free space %d is already above the low watermark %d", before.FreeBytes, snap.minFreeBytes)
	}

	reclaimed, deleted, err := s.CheckAndEvict(context.Background())
	if err != nil {
		t.Fatalf("CheckAndEvict failed: %v", err)
	}
	if deleted == 0 || reclaimed == 0 {
		t.Fatalf("Expected eviction to reclaim space, got %d bytes across %d files", reclaimed, deleted)
	}

	after, err := model.statfs("")
	if err != nil {
		t.Fatalf("statfs failed: %v", err)
	}
	if after.FreeBytes < snap.targetFreeBytes {
		t.Errorf("Expected eviction to restore free space to the target %d, got %d", snap.targetFreeBytes, after.FreeBytes)
	}
	// Eviction must be bounded: a pass that empties the cache would throw away
	// hours of warming to reclaim a few megabytes.
	if remaining := countBlobs(t, s); remaining == 0 {
		t.Errorf("Eviction emptied the cache instead of trimming it to the target")
	}
}

func TestCheckAndEvict_NoOpAboveWatermark(t *testing.T) {
	// A roomy volume: the same blobs occupy a negligible fraction of it.
	s, _ := newPressuredStorage(t, 10*1024*1024*1024, 200, 8192)

	before := countBlobs(t, s)
	reclaimed, deleted, err := s.CheckAndEvict(context.Background())
	if err != nil {
		t.Fatalf("CheckAndEvict failed: %v", err)
	}
	if reclaimed != 0 || deleted != 0 {
		t.Errorf("Expected no eviction above the low watermark, reclaimed %d bytes across %d files", reclaimed, deleted)
	}
	if after := countBlobs(t, s); after != before {
		t.Errorf("Expected the cache to be untouched, blob count went from %d to %d", before, after)
	}
}

func TestEnsureHeadroom_FastPathDoesNotEvict(t *testing.T) {
	s, _ := newPressuredStorage(t, 10*1024*1024*1024, 200, 8192)

	before := countBlobs(t, s)
	if err := s.EnsureHeadroom(context.Background(), 1024*1024); err != nil {
		t.Fatalf("EnsureHeadroom failed: %v", err)
	}
	if after := countBlobs(t, s); after != before {
		t.Errorf("Expected no eviction when the disk already has headroom, blob count went from %d to %d", before, after)
	}
}

func TestEnsureHeadroom_ReclaimsSpaceForTheWrite(t *testing.T) {
	s, model := newPressuredStorage(t, 11*1024*1024, 1200, 8192)
	const required = 2 * 1024 * 1024

	if err := s.EnsureHeadroom(context.Background(), required); err != nil {
		t.Fatalf("EnsureHeadroom failed: %v", err)
	}

	stats, err := model.statfs("")
	if err != nil {
		t.Fatalf("statfs failed: %v", err)
	}
	snap := s.Evictor().snapshot(stats)
	if want := snap.minFreeBytes + required; stats.FreeBytes < want {
		t.Errorf("Expected at least %d free bytes after making headroom for a %d byte write, got %d", want, required, stats.FreeBytes)
	}
	if remaining := countBlobs(t, s); remaining == 0 {
		t.Errorf("EnsureHeadroom emptied the cache instead of reclaiming just enough")
	}
}

// TestEnsureHeadroom_ToleratesPressureItCannotFullyRelieve pins the deliberate
// decision not to fail while the write still fits. The low watermark is a
// safety buffer, and refusing to write on a disk that demonstrably has room
// would turn a tuning problem into an outage.
func TestEnsureHeadroom_ToleratesPressureItCannotFullyRelieve(t *testing.T) {
	// The cache holds only 800 KiB, and a co-tenant occupies almost all of the
	// rest of the volume. Even evicting every blob cannot restore the low
	// watermark, which is precisely the situation that must not be fatal.
	s, model := newPressuredStorage(t, 11*1024*1024, 100, 8192)
	model.otherTenantBytes = 10 * 1024 * 1024

	stats, err := model.statfs("")
	if err != nil {
		t.Fatalf("statfs failed: %v", err)
	}
	required := stats.FreeBytes / 4
	if required <= 0 {
		t.Fatalf("Test setup is wrong: no free space to work with")
	}

	if err := s.EnsureHeadroom(context.Background(), required); err != nil {
		t.Errorf("Expected EnsureHeadroom to tolerate a write that still fits, got error: %v", err)
	}
}

func TestEnsureHeadroom_FailsWhenTheWriteCannotFit(t *testing.T) {
	s, _ := newPressuredStorage(t, 11*1024*1024, 1200, 8192)

	err := s.EnsureHeadroom(context.Background(), 100*1024*1024)
	if err == nil {
		t.Fatalf("Expected an error when the write is larger than the volume")
	}
	// The message is what an on-call engineer sees, so it must name the
	// directory and suggest a remedy.
	for _, want := range []string{s.RootDir(), "Reduce download concurrency"} {
		if !strings.Contains(err.Error(), want) {
			t.Errorf("Expected the error to mention %q, got: %v", want, err)
		}
	}
}

func TestEnsureHeadroom_RejectsNegativeRequest(t *testing.T) {
	s, _ := newPressuredStorage(t, 10*1024*1024*1024, 10, 8192)
	if err := s.EnsureHeadroom(context.Background(), -1); err == nil {
		t.Errorf("Expected an error for a negative byte count, got nil")
	}
}

func TestEnsureHeadroom_RequiresAnEvictor(t *testing.T) {
	s := setupTestStorage(t)
	if err := s.EnsureHeadroom(context.Background(), 1024); err == nil {
		t.Errorf("Expected an error when no evictor is configured, got nil")
	}
}

// TestEnsureHeadroom_WaitsForAPeerRatherThanPilingOn is the thundering-herd
// case: while one process evicts, the others must not each start their own
// full flight.
func TestEnsureHeadroom_WaitsForAPeerRatherThanPilingOn(t *testing.T) {
	shortenHeadroomWaits(t)
	s, _ := newPressuredStorage(t, 11*1024*1024, 1200, 8192)

	lock, err := tryLockEviction(s.RootDir())
	if err != nil {
		t.Fatalf("Failed to acquire the eviction lock: %v", err)
	}
	defer lock.release()

	before := countBlobs(t, s)
	// The peer never frees anything, so this falls through to the tolerant
	// path once the wait expires; what matters is that no eviction happened.
	if err := s.EnsureHeadroom(context.Background(), 1024); err != nil {
		t.Fatalf("EnsureHeadroom failed: %v", err)
	}
	if after := countBlobs(t, s); after != before {
		t.Errorf("Expected no eviction while a peer holds the lock, blob count went from %d to %d", before, after)
	}
}

// TestEnsureHeadroom_StopsWaitingOnceAPeerFreesTheSpace is the other half of
// the thundering-herd story. Waiting is only worthwhile if the waiter notices
// the peer's flight landing and gets on with its write; a waiter that slept
// out the full timeout regardless would turn one process's eviction into a
// stall for every other process on the host.
func TestEnsureHeadroom_StopsWaitingOnceAPeerFreesTheSpace(t *testing.T) {
	shortenHeadroomWaits(t)
	// Deliberately long. The assertions below distinguish "noticed the space"
	// from "gave up waiting", so the timeout only needs to be far enough away
	// that a slow machine cannot reach it by accident.
	headroomWaitTimeout = 10 * time.Second

	s := setupTestStorage(t)
	seedBlobs(t, s, 1200, 8192)
	model := &diskModel{root: s.RootDir(), totalBytes: 11 * 1024 * 1024}

	// Report the peer's flight as having landed on the third statfs: the
	// first is the fast-path check that sends us into the wait loop, the
	// second is the loop's first poll, and the third is the poll that must
	// see the new space and return. Only EnsureHeadroom calls this, on the
	// test's own goroutine, so an unguarded counter is safe.
	calls := 0
	peerFreesSpace := func(path string) (DiskStats, error) {
		stats, err := model.statfs(path)
		calls++
		if err != nil || calls < 3 {
			return stats, err
		}
		stats.FreeBytes = stats.TotalBytes
		return stats, nil
	}

	ev, err := NewEvictor(s.RootDir(), headroomTestConfig(), peerFreesSpace)
	if err != nil {
		t.Fatalf("Failed to create the evictor: %v", err)
	}
	s.SetEvictor(ev)

	lock, err := tryLockEviction(s.RootDir())
	if err != nil {
		t.Fatalf("Failed to acquire the eviction lock: %v", err)
	}
	defer lock.release()

	before := countBlobs(t, s)
	if err := s.EnsureHeadroom(context.Background(), 1024*1024); err != nil {
		t.Fatalf("EnsureHeadroom failed: %v", err)
	}

	stats := s.Stats()
	// The timeout path is the one that records a peer wait, so a zero here is
	// what proves the loop returned because it saw the space rather than
	// because it ran out of patience.
	if stats.HeadroomPeerWaits != 0 {
		t.Errorf("Expected the wait to end as soon as the peer freed space, but it timed out (HeadroomPeerWaits=%d)", stats.HeadroomPeerWaits)
	}
	if stats.HeadroomEvictions != 0 {
		t.Errorf("Expected no eviction of our own while a peer held the lock, got %d", stats.HeadroomEvictions)
	}
	if after := countBlobs(t, s); after != before {
		t.Errorf("Expected the cache to be untouched, blob count went from %d to %d", before, after)
	}
}

// TestEnsureHeadroom_PropagatesAnUnexpectedLockFailure covers a lock that
// cannot be taken for a reason other than a peer holding it. ErrEvictionBusy
// means someone else is doing the work and is safe to shrug off; anything else
// means the cache root is not usable, and quietly treating it as a busy peer
// would strand the caller waiting on an eviction that will never happen.
func TestEnsureHeadroom_PropagatesAnUnexpectedLockFailure(t *testing.T) {
	shortenHeadroomWaits(t)
	s, _ := newPressuredStorage(t, 11*1024*1024, 1200, 8192)

	// A directory sitting where the lock file belongs makes the open fail
	// with EISDIR, which is neither success nor EWOULDBLOCK.
	if err := os.Mkdir(filepath.Join(s.RootDir(), evictLockName), 0755); err != nil {
		t.Fatalf("Failed to plant a directory at the lock path: %v", err)
	}

	err := s.EnsureHeadroom(context.Background(), 1024)
	if err == nil {
		t.Fatalf("Expected an error when the eviction lock cannot be opened")
	}
	if errors.Is(err, ErrEvictionBusy) {
		t.Errorf("An unopenable lock must not be reported as a busy peer, got %v", err)
	}
	if !strings.Contains(err.Error(), evictLockName) {
		t.Errorf("Expected the error to name the lock file %q, got %v", evictLockName, err)
	}
}

func TestEnsureHeadroom_HonorsContextCancellation(t *testing.T) {
	shortenHeadroomWaits(t)
	headroomWaitTimeout = time.Minute // outlast the cancellation
	s, _ := newPressuredStorage(t, 11*1024*1024, 1200, 8192)

	lock, err := tryLockEviction(s.RootDir())
	if err != nil {
		t.Fatalf("Failed to acquire the eviction lock: %v", err)
	}
	defer lock.release()

	ctx, cancel := context.WithCancel(context.Background())
	time.AfterFunc(50*time.Millisecond, cancel)

	if err := s.EnsureHeadroom(ctx, 4*1024*1024); !errors.Is(err, context.Canceled) {
		t.Errorf("Expected context.Canceled, got %v", err)
	}
}

func TestEvictForHeadroom_RejectsNegativeExtra(t *testing.T) {
	s, _ := newPressuredStorage(t, 11*1024*1024, 10, 8192)
	if _, _, err := s.Evictor().EvictForHeadroom(context.Background(), -1); err == nil {
		t.Errorf("Expected an error for a negative byte count, got nil")
	}
}

// TestEvictForHeadroom_SaturatesAnAbsurdRequest pins the overflow guard on the
// eviction goal. A request large enough to wrap targetFreeBytes past MaxInt64
// leaves the flight comparing free space against a negative goal, so it
// concludes it already has room to spare and evicts nothing at all: the exact
// opposite of what was asked for, and silent. Saturating at the volume's
// capacity instead makes the pass reclaim everything it is allowed to.
func TestEvictForHeadroom_SaturatesAnAbsurdRequest(t *testing.T) {
	s, _ := newPressuredStorage(t, 11*1024*1024, 1200, 8192)
	before := countBlobs(t, s)

	_, deleted, err := s.Evictor().EvictForHeadroom(context.Background(), math.MaxInt64)
	if err != nil {
		t.Fatalf("EvictForHeadroom failed: %v", err)
	}

	// Two distinct failures are being excluded here. An overflowed goal
	// evicts nothing, and a goal left at the ordinary target stops partway,
	// so only a saturated goal empties the cache of eligible blobs.
	if deleted != before {
		t.Errorf("Expected all %d eligible blobs to be evicted, got %d", before, deleted)
	}
	if remaining := countBlobs(t, s); remaining != 0 {
		t.Errorf("Expected the flight to reclaim everything it could, but %d blobs remain", remaining)
	}
}

func TestConfigureEvictorDoesNotStartTheBackgroundLoop(t *testing.T) {
	s := setupTestStorage(t)
	cfg := headroomTestConfig()
	cfg.CheckInterval = time.Millisecond

	if err := s.ConfigureEvictor(cfg); err != nil {
		t.Fatalf("ConfigureEvictor failed: %v", err)
	}
	if s.Evictor() == nil {
		t.Fatalf("Expected an evictor to be attached")
	}

	// A configured-but-unstarted evictor has no goroutine to stop, so Stop
	// would block forever on doneChan. Confirm the loop really is not running
	// by checking that nothing ever reports an eviction.
	time.Sleep(20 * time.Millisecond)
	if got := s.Evictor().Stats().TotalEvictionRuns; got != 0 {
		t.Errorf("Expected the background loop to be idle, but %d eviction runs were recorded", got)
	}
}

func TestConfigureEvictor_RejectsInvalidConfig(t *testing.T) {
	s := setupTestStorage(t)
	cfg := headroomTestConfig()
	cfg.MinFreeSpace = "not a threshold"

	if err := s.ConfigureEvictor(cfg); err == nil {
		t.Errorf("Expected an error for an unparseable threshold, got nil")
	}
}

func TestFormatBytes(t *testing.T) {
	tests := []struct {
		bytes int64
		want  string
	}{
		{0, "0 B"},
		{512, "512 B"},
		{1024, "1.00 KiB"},
		{1024 * 1024, "1.00 MiB"},
		{3 * 1024 * 1024 * 1024 / 2, "1.50 GiB"},
		{1024 * 1024 * 1024 * 1024, "1024.00 GiB"},
	}

	for _, tc := range tests {
		if got := formatBytes(tc.bytes); got != tc.want {
			t.Errorf("formatBytes(%d) = %q, want %q", tc.bytes, got, tc.want)
		}
	}
}
