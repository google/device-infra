package storage

import (
	"bufio"
	"context"
	"errors"
	"fmt"
	"io/fs"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"sync/atomic"
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

// oneshotTestConfig returns an evictor configuration sized for the small
// simulated volumes used below. ReservedSpaceGB must be zero because it is
// denominated in whole gigabytes, which would swallow the entire fake volume.
func oneshotTestConfig() EvictorConfig {
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
	seedBlobsAged(t, s, n, size, 72*time.Hour)
}

// seedBlobsAged is seedBlobs with the blobs' age given explicitly.
func seedBlobsAged(t *testing.T, s *Storage, n, size int, age time.Duration) {
	t.Helper()
	data := make([]byte, size)
	mtime := time.Now().Add(-age)

	for i := 0; i < n; i++ {
		hash := fmt.Sprintf("%02x%02x%060x", i%256, (i/256)%256, i)
		if err := s.Write(hash, data); err != nil {
			t.Fatalf("Failed to seed blob %d: %v", i, err)
		}
		if err := os.Chtimes(s.blobPath(hash), mtime, mtime); err != nil {
			t.Fatalf("Failed to set the mtime of blob %d: %v", i, err)
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

// pinAllBlobs gives every cache blob a second name outside the cache, the way
// casdownloader links blobs into a test's workspace.
func pinAllBlobs(t *testing.T, s *Storage) {
	t.Helper()
	outside := t.TempDir()
	n := 0
	err := filepath.WalkDir(s.RootDir(), func(path string, d fs.DirEntry, err error) error {
		if err != nil || d.IsDir() || !isCandidateBlob(d.Name(), false) {
			return err
		}
		n++
		return os.Link(path, filepath.Join(outside, fmt.Sprintf("pin-%d", n)))
	})
	if err != nil {
		t.Fatalf("Failed to pin the cache's blobs: %v", err)
	}
}

// newPressuredStorage builds a Storage holding blobCount blobs on a simulated
// volume of totalBytes, wired to a disk model that reflects real deletions.
func newPressuredStorage(t *testing.T, totalBytes int64, blobCount, blobSize int) (*Storage, *diskModel) {
	t.Helper()
	s := setupTestStorage(t)
	seedBlobs(t, s, blobCount, blobSize)
	return s, attachDiskModel(t, s, totalBytes)
}

// attachDiskModel gives s an evictor whose statfs is a diskModel of a
// totalBytes volume.
func attachDiskModel(t *testing.T, s *Storage, totalBytes int64) *diskModel {
	t.Helper()
	model := &diskModel{root: s.RootDir(), totalBytes: totalBytes}
	ev, err := NewEvictor(s.RootDir(), oneshotTestConfig(), model.statfs)
	if err != nil {
		t.Fatalf("Failed to create the evictor: %v", err)
	}
	s.SetEvictor(ev)
	return model
}

// countingLayout counts the calls to Buckets, so a test can tell how many
// times the evictor enumerated the buckets: once to sample them, and once
// more to walk them all.
type countingLayout struct {
	Layout
	bucketCalls atomic.Int32
}

func (l *countingLayout) Buckets() []string {
	l.bucketCalls.Add(1)
	return l.Layout.Buckets()
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
	res, err := s.CheckAndEvict(context.Background())
	if err == nil {
		t.Errorf("Expected an error when no evictor is configured, got nil")
	}
	if res.Outcome != TrimOutcomeError {
		t.Errorf("Outcome = %q, want %q", res.Outcome, TrimOutcomeError)
	}
}

// TestCheckAndEvict_ReportsBusy covers the cross-process lock. A peer already
// evicting is the expected case on a shared host, so it is an outcome, not an
// error.
func TestCheckAndEvict_ReportsBusy(t *testing.T) {
	s, _ := newPressuredStorage(t, 11*1024*1024, 1200, 8192)

	lock, err := tryLockEviction(s.RootDir())
	if err != nil {
		t.Fatalf("Failed to acquire the eviction lock: %v", err)
	}
	defer lock.release()

	before := countBlobs(t, s)
	res, err := s.CheckAndEvict(context.Background())
	if err != nil {
		t.Errorf("CheckAndEvict returned an error for a busy peer: %v", err)
	}
	if res.Outcome != TrimOutcomeBusy {
		t.Errorf("Outcome = %q, want %q", res.Outcome, TrimOutcomeBusy)
	}
	if after := countBlobs(t, s); after != before {
		t.Errorf("Expected no eviction while a peer holds the lock, blob count went from %d to %d", before, after)
	}
}

// TestCheckAndEvict_ReportsBusyForAnInProcessFlight covers the evictor's own
// single-flight gate, which the one-shot path used to report as a pass that
// reclaimed nothing.
func TestCheckAndEvict_ReportsBusyForAnInProcessFlight(t *testing.T) {
	s, _ := newPressuredStorage(t, 11*1024*1024, 1200, 8192)
	s.Evictor().isEvicting.Store(true)
	defer s.Evictor().isEvicting.Store(false)

	res, err := s.CheckAndEvict(context.Background())
	if err != nil {
		t.Errorf("CheckAndEvict failed: %v", err)
	}
	if res.Outcome != TrimOutcomeBusy {
		t.Errorf("Outcome = %q, want %q", res.Outcome, TrimOutcomeBusy)
	}
}

func TestCheckAndEvict_EvictsUnderPressure(t *testing.T) {
	const blobSize = 8192
	s, model := newPressuredStorage(t, 11*1024*1024, 1200, blobSize)

	before, err := model.statfs("")
	if err != nil {
		t.Fatalf("statfs failed: %v", err)
	}
	snap := s.Evictor().snapshot(before)
	if before.FreeBytes >= snap.minFreeBytes {
		t.Fatalf("Test setup is wrong: free space %d is already above the low watermark %d", before.FreeBytes, snap.minFreeBytes)
	}
	blobsBefore := countBlobs(t, s)

	res, err := s.CheckAndEvict(context.Background())
	if err != nil {
		t.Fatalf("CheckAndEvict failed: %v", err)
	}
	if res.Outcome != TrimOutcomeEvicted {
		t.Errorf("Outcome = %q, want %q", res.Outcome, TrimOutcomeEvicted)
	}
	if res.FilesDeleted == 0 || res.BytesReclaimed == 0 {
		t.Fatalf("Expected eviction to reclaim space, got %d bytes across %d files", res.BytesReclaimed, res.FilesDeleted)
	}
	// The result must describe what actually happened on disk.
	blobsAfter := countBlobs(t, s)
	if got := blobsBefore - blobsAfter; got != res.FilesDeleted {
		t.Errorf("FilesDeleted = %d, but %d blobs disappeared", res.FilesDeleted, got)
	}
	if want := int64(res.FilesDeleted) * blobSize; res.BytesReclaimed != want {
		t.Errorf("BytesReclaimed = %d, want %d for %d blobs of %d bytes", res.BytesReclaimed, want, res.FilesDeleted, blobSize)
	}
	if res.SkippedLinked != 0 {
		t.Errorf("SkippedLinked = %d, want 0 with no blob linked elsewhere", res.SkippedLinked)
	}
	if res.Duration <= 0 {
		t.Errorf("Duration = %v, want it positive", res.Duration)
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
	if blobsAfter == 0 {
		t.Errorf("Eviction emptied the cache instead of trimming it to the target")
	}
}

func TestCheckAndEvict_NoOpAboveWatermark(t *testing.T) {
	// A roomy volume: the same blobs occupy a negligible fraction of it.
	s, _ := newPressuredStorage(t, 10*1024*1024*1024, 200, 8192)

	before := countBlobs(t, s)
	res, err := s.CheckAndEvict(context.Background())
	if err != nil {
		t.Fatalf("CheckAndEvict failed: %v", err)
	}
	if res.Outcome != TrimOutcomeNoop {
		t.Errorf("Outcome = %q, want %q", res.Outcome, TrimOutcomeNoop)
	}
	if res.BytesReclaimed != 0 || res.FilesDeleted != 0 {
		t.Errorf("Expected no eviction above the low watermark, reclaimed %d bytes across %d files", res.BytesReclaimed, res.FilesDeleted)
	}
	if after := countBlobs(t, s); after != before {
		t.Errorf("Expected the cache to be untouched, blob count went from %d to %d", before, after)
	}
}

// TestCheckAndEvict_ReportsTimedOut covers -trim-max-duration's mechanism: the
// pass stops at its context deadline, which is a bounded run rather than a
// failure.
func TestCheckAndEvict_ReportsTimedOut(t *testing.T) {
	s, _ := newPressuredStorage(t, 11*1024*1024, 1200, 8192)

	ctx, cancel := context.WithDeadline(context.Background(), time.Now().Add(-time.Second))
	defer cancel()

	before := countBlobs(t, s)
	res, err := s.CheckAndEvict(ctx)
	if err != nil {
		t.Errorf("CheckAndEvict returned an error for a pass cut short by its deadline: %v", err)
	}
	if res.Outcome != TrimOutcomeTimedOut {
		t.Errorf("Outcome = %q, want %q", res.Outcome, TrimOutcomeTimedOut)
	}
	if got := before - countBlobs(t, s); got != res.FilesDeleted {
		t.Errorf("FilesDeleted = %d, but %d blobs disappeared", res.FilesDeleted, got)
	}
}

// TestCheckAndEvict_PropagatesAnUnexpectedLockFailure covers a lock that
// cannot be taken for a reason other than a peer holding it. ErrEvictionBusy
// means someone else is doing the work and is safe to shrug off; anything else
// means the cache root is not usable, and quietly treating it as a busy peer
// would hide that.
func TestCheckAndEvict_PropagatesAnUnexpectedLockFailure(t *testing.T) {
	s, _ := newPressuredStorage(t, 11*1024*1024, 1200, 8192)

	// A directory sitting where the lock file belongs makes the open fail
	// with EISDIR, which is neither success nor EWOULDBLOCK.
	if err := os.Mkdir(filepath.Join(s.RootDir(), evictLockName), 0755); err != nil {
		t.Fatalf("Failed to plant a directory at the lock path: %v", err)
	}

	res, err := s.CheckAndEvict(context.Background())
	if err == nil {
		t.Fatalf("Expected an error when the eviction lock cannot be opened")
	}
	if res.Outcome != TrimOutcomeError {
		t.Errorf("Outcome = %q, want %q", res.Outcome, TrimOutcomeError)
	}
	if errors.Is(err, ErrEvictionBusy) {
		t.Errorf("An unopenable lock must not be reported as a busy peer, got %v", err)
	}
	if !strings.Contains(err.Error(), evictLockName) {
		t.Errorf("Expected the error to name the lock file %q, got %v", evictLockName, err)
	}
}

// TestCheckAndEvict_ExitsEarlyWhenEverythingIsPinned covers a cache whose
// every blob is still linked into a workspace. Unlinking those frees nothing,
// so walking every bucket would only cost time: the pass stops after sampling.
func TestCheckAndEvict_ExitsEarlyWhenEverythingIsPinned(t *testing.T) {
	const blobCount, blobSize = 1200, 8192
	layout := &countingLayout{Layout: Layout256}
	s, err := New(t.TempDir(), WithLayout(layout))
	if err != nil {
		t.Fatalf("New failed: %v", err)
	}
	seedBlobs(t, s, blobCount, blobSize)
	pinAllBlobs(t, s)
	// The pinned blobs count against the disk, so it stays below the low
	// watermark.
	attachDiskModel(t, s, 11*1024*1024)

	res, err := s.CheckAndEvict(context.Background())
	if err != nil {
		t.Fatalf("CheckAndEvict failed: %v", err)
	}
	if res.Outcome != TrimOutcomeNoop {
		t.Errorf("Outcome = %q, want %q", res.Outcome, TrimOutcomeNoop)
	}
	if res.FilesDeleted != 0 || res.BytesReclaimed != 0 {
		t.Errorf("Reclaimed %d bytes across %d files, want nothing", res.BytesReclaimed, res.FilesDeleted)
	}
	// Every sampled blob was pinned, so SkippedLinked is a whole number of
	// blobs, and at least one of them.
	if res.SkippedLinked <= 0 || res.SkippedLinked%blobSize != 0 {
		t.Errorf("SkippedLinked = %d, want a positive multiple of %d", res.SkippedLinked, blobSize)
	}
	if got := layout.bucketCalls.Load(); got != 1 {
		t.Errorf("Buckets() called %d times, want 1: the pass should sample and stop, not walk every bucket", got)
	}
	if got := countBlobs(t, s); got != blobCount {
		t.Errorf("%d blobs remain, want all %d", got, blobCount)
	}
}

// TestCheckAndEvict_ExitsEarlyWhenOnlyYoungBlobsRemain is the grace-period
// counterpart: blobs newer than MinBlobAge are never evicted, so a cache that
// holds only those cannot be trimmed either.
func TestCheckAndEvict_ExitsEarlyWhenOnlyYoungBlobsRemain(t *testing.T) {
	layout := &countingLayout{Layout: Layout256}
	s, err := New(t.TempDir(), WithLayout(layout))
	if err != nil {
		t.Fatalf("New failed: %v", err)
	}
	seedBlobsAged(t, s, 1200, 8192, time.Minute)
	attachDiskModel(t, s, 11*1024*1024)

	res, err := s.CheckAndEvict(context.Background())
	if err != nil {
		t.Fatalf("CheckAndEvict failed: %v", err)
	}
	if res.Outcome != TrimOutcomeNoop || res.FilesDeleted != 0 {
		t.Errorf("Result = %+v, want a noop that deleted nothing", res)
	}
	if res.SkippedLinked != 0 {
		t.Errorf("SkippedLinked = %d, want 0 with no blob linked elsewhere", res.SkippedLinked)
	}
	if got := layout.bucketCalls.Load(); got != 1 {
		t.Errorf("Buckets() called %d times, want 1: the pass should sample and stop, not walk every bucket", got)
	}
}

// TestEvictOnce_DoesNotExitEarly pins that the early exit belongs to the
// one-shot path only. casproxy's background flights keep walking every bucket
// as before, even when the sample says nothing is reclaimable.
func TestEvictOnce_DoesNotExitEarly(t *testing.T) {
	layout := &countingLayout{Layout: Layout256}
	s, err := New(t.TempDir(), WithLayout(layout))
	if err != nil {
		t.Fatalf("New failed: %v", err)
	}
	seedBlobs(t, s, 1200, 8192)
	pinAllBlobs(t, s)
	attachDiskModel(t, s, 11*1024*1024)

	if _, _, err := s.Evictor().EvictOnce(context.Background()); err != nil {
		t.Fatalf("EvictOnce failed: %v", err)
	}
	if got := layout.bucketCalls.Load(); got != 2 {
		t.Errorf("Buckets() called %d times, want 2: one to sample, one to walk every bucket", got)
	}
}

func TestConfigureEvictorDoesNotStartTheBackgroundLoop(t *testing.T) {
	s := setupTestStorage(t)
	cfg := oneshotTestConfig()
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
	cfg := oneshotTestConfig()
	cfg.MinFreeSpace = "not a threshold"

	if err := s.ConfigureEvictor(cfg); err == nil {
		t.Errorf("Expected an error for an unparseable threshold, got nil")
	}
}

// fixedDisk is a statfs whose answer never changes, standing in for a volume
// where deletions do not show up in free space right away. Against it, the
// only thing that can stop a pass short of walking every bucket is the bytes
// the pass counts itself.
func fixedDisk(total, free int64) StatfsFunc {
	return func(string) (DiskStats, error) {
		return DiskStats{TotalBytes: total, FreeBytes: free}, nil
	}
}

// newTrimStorage builds a 256-bucket Storage holding blobCount old blobs of
// blobSize bytes, with an evictor that sees statfs.
func newTrimStorage(t *testing.T, blobCount, blobSize int, statfs func(s *Storage) StatfsFunc) *Storage {
	t.Helper()
	s, err := New(t.TempDir(), WithLayout(Layout256))
	if err != nil {
		t.Fatalf("New failed: %v", err)
	}
	seedBlobs(t, s, blobCount, blobSize)
	ev, err := NewEvictor(s.RootDir(), oneshotTestConfig(), statfs(s))
	if err != nil {
		t.Fatalf("Failed to create the evictor: %v", err)
	}
	s.SetEvictor(ev)
	return s
}

// TestTrim_RunsBetweenTheWatermarks covers the difference between Trim and
// CheckAndEvict. A detached evictor has already been told a trim is needed,
// so it works toward TargetFreeSpace even when free space is above
// MinFreeSpace, where CheckAndEvict does nothing.
func TestTrim_RunsBetweenTheWatermarks(t *testing.T) {
	const blobCount, blobSize = 1200, 8192
	// 1200 blobs of 8 KiB use about 78% of a 12 MiB volume: free space is
	// above the 15% minimum and below the 25% target.
	const total = 12 * 1024 * 1024
	var model *diskModel
	s := newTrimStorage(t, blobCount, blobSize, func(s *Storage) StatfsFunc {
		model = &diskModel{root: s.RootDir(), totalBytes: total}
		return model.statfs
	})

	before, err := model.statfs("")
	if err != nil {
		t.Fatalf("statfs failed: %v", err)
	}
	snap := s.Evictor().snapshot(before)
	if before.FreeBytes < snap.minFreeBytes || before.FreeBytes >= snap.targetFreeBytes {
		t.Fatalf("Test setup is wrong: free space %d is not between the minimum %d and the target %d", before.FreeBytes, snap.minFreeBytes, snap.targetFreeBytes)
	}

	if res, err := s.CheckAndEvict(context.Background()); err != nil || res.Outcome != TrimOutcomeNoop {
		t.Fatalf("CheckAndEvict = %+v, %v; want a noop above the minimum, or this test proves nothing", res, err)
	}

	res, err := s.Trim(context.Background())
	if err != nil {
		t.Fatalf("Trim failed: %v", err)
	}
	if res.Outcome != TrimOutcomeEvicted {
		t.Errorf("Outcome = %q, want %q", res.Outcome, TrimOutcomeEvicted)
	}
	if got := blobCount - countBlobs(t, s); got != res.FilesDeleted {
		t.Errorf("FilesDeleted = %d, but %d blobs disappeared", res.FilesDeleted, got)
	}
	after, err := model.statfs("")
	if err != nil {
		t.Fatalf("statfs failed: %v", err)
	}
	if after.FreeBytes < snap.targetFreeBytes {
		t.Errorf("Free space after Trim = %d, want at least the target %d", after.FreeBytes, snap.targetFreeBytes)
	}
	// need was under 400 KiB, so a pass that reclaims much more than that
	// threw away cache it did not have to.
	if need := snap.targetFreeBytes - before.FreeBytes; res.BytesReclaimed >= need+blobSize {
		t.Errorf("BytesReclaimed = %d, want less than one blob over the %d bytes needed", res.BytesReclaimed, need)
	}
}

// TestTrim_StopsAfterReclaimingTheShortfall pins the byte goal: with free
// space that never moves, the pass still stops once it has unlinked the
// bytes it set out to reclaim, instead of deleting every eligible blob.
func TestTrim_StopsAfterReclaimingTheShortfall(t *testing.T) {
	const blobCount, blobSize = 1200, 8192
	const total, free = 100 * 1024 * 1024, 20 * 1024 * 1024
	s := newTrimStorage(t, blobCount, blobSize, func(*Storage) StatfsFunc { return fixedDisk(total, free) })

	res, err := s.Trim(context.Background())
	if err != nil {
		t.Fatalf("Trim failed: %v", err)
	}
	// The target is 25%, so need is 5 MiB: 640 blobs of 8 KiB.
	const need = total/4 - free
	if res.Outcome != TrimOutcomeEvicted || res.BytesReclaimed != need || res.FilesDeleted != need/blobSize {
		t.Errorf("Trim = %+v, want %q with %d bytes across %d files", res, TrimOutcomeEvicted, need, need/blobSize)
	}
	if got := countBlobs(t, s); got != blobCount-need/blobSize {
		t.Errorf("%d blobs remain, want %d", got, blobCount-need/blobSize)
	}
}

// TestEvictOnce_HasNoByteGoal is the casproxy side of the test above: its
// flights keep stopping only on free space, so against a disk that never
// shows progress they unlink every eligible blob, exactly as before Trim.
func TestEvictOnce_HasNoByteGoal(t *testing.T) {
	const blobCount = 1200
	s := newTrimStorage(t, blobCount, 8192, func(*Storage) StatfsFunc { return fixedDisk(100*1024*1024, 20*1024*1024) })

	if _, deleted, err := s.Evictor().EvictOnce(context.Background()); err != nil || deleted != blobCount {
		t.Errorf("EvictOnce deleted %d files (err %v), want all %d", deleted, err, blobCount)
	}
}

func TestTrim_NoOpAtTheTarget(t *testing.T) {
	const blobCount = 200
	s := newTrimStorage(t, blobCount, 8192, func(*Storage) StatfsFunc { return fixedDisk(100*1024*1024, 25*1024*1024) })

	res, err := s.Trim(context.Background())
	if err != nil {
		t.Fatalf("Trim failed: %v", err)
	}
	if res.Outcome != TrimOutcomeNoop || res.FilesDeleted != 0 {
		t.Errorf("Trim = %+v, want a noop that deleted nothing", res)
	}
	if got := countBlobs(t, s); got != blobCount {
		t.Errorf("%d blobs remain, want all %d", got, blobCount)
	}
}

func TestTrim_ReportsBusy(t *testing.T) {
	const blobCount = 1200
	s := newTrimStorage(t, blobCount, 8192, func(*Storage) StatfsFunc { return fixedDisk(100*1024*1024, 20*1024*1024) })

	lock, err := tryLockEviction(s.RootDir())
	if err != nil {
		t.Fatalf("Failed to acquire the eviction lock: %v", err)
	}
	defer lock.release()

	res, err := s.Trim(context.Background())
	if err != nil {
		t.Errorf("Trim returned an error for a busy peer: %v", err)
	}
	if res.Outcome != TrimOutcomeBusy {
		t.Errorf("Outcome = %q, want %q", res.Outcome, TrimOutcomeBusy)
	}
	if got := countBlobs(t, s); got != blobCount {
		t.Errorf("%d blobs remain while a peer holds the lock, want all %d", got, blobCount)
	}
}

func TestTrim_ReportsTimedOut(t *testing.T) {
	const blobCount = 1200
	s := newTrimStorage(t, blobCount, 8192, func(*Storage) StatfsFunc { return fixedDisk(100*1024*1024, 20*1024*1024) })

	ctx, cancel := context.WithDeadline(context.Background(), time.Now().Add(-time.Second))
	defer cancel()

	res, err := s.Trim(ctx)
	if err != nil {
		t.Errorf("Trim returned an error for a pass cut short by its deadline: %v", err)
	}
	if res.Outcome != TrimOutcomeTimedOut {
		t.Errorf("Outcome = %q, want %q", res.Outcome, TrimOutcomeTimedOut)
	}
	if got := blobCount - countBlobs(t, s); got != res.FilesDeleted {
		t.Errorf("FilesDeleted = %d, but %d blobs disappeared", res.FilesDeleted, got)
	}
}
