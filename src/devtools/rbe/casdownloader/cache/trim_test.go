package cache

import (
	"context"
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"syscall"
	"testing"
	"time"

	"github.com/google/device-infra/src/devtools/rbe/common/storage"
)

const (
	trimBlobCount = 1200
	trimBlobSize  = 8192
	mib           = 1 << 20
)

// fixedVolume is a statfs whose answer never changes. Against it a trim can
// only stop on the bytes it counts itself, which makes how much it reclaims
// exact rather than dependent on when free space was last re-checked.
func fixedVolume(total, free int64) storage.StatfsFunc {
	return func(string) (storage.DiskStats, error) {
		return storage.DiskStats{TotalBytes: total, FreeBytes: free}, nil
	}
}

// seedOldBlobs writes n blobs of size bytes into the lock-free cache under
// cacheDir and backdates them past the evictor's grace period, so that a trim
// may evict any of them.
//
// They are written through the production store but backdated through
// blobPath, which spells the layout out independently, so a store that wrote
// anywhere else fails here rather than producing a trim with nothing to do.
func seedOldBlobs(t *testing.T, cacheDir string, n, size int) {
	t.Helper()
	store, err := newLockFreeStorage(cacheDir)
	if err != nil {
		t.Fatalf("newLockFreeStorage failed: %v", err)
	}
	data := make([]byte, size)
	old := time.Now().Add(-72 * time.Hour)
	for i := 0; i < n; i++ {
		// Vary the first byte so the blobs spread over the buckets.
		hash := fmt.Sprintf("%02x%062x", i%256, i)
		if err := store.Write(hash, data); err != nil {
			t.Fatalf("Failed to seed blob %d: %v", i, err)
		}
		if err := os.Chtimes(blobPath(cacheDir, hash), old, old); err != nil {
			t.Fatalf("Failed to backdate blob %d: %v", i, err)
		}
	}
}

// countCachedBlobs returns how many blobs remain in the lock-free cache.
func countCachedBlobs(t *testing.T, cacheDir string) int {
	t.Helper()
	n := 0
	err := filepath.WalkDir(filepath.Join(cacheDir, wantLayoutDir), func(_ string, d fs.DirEntry, err error) error {
		if err != nil {
			return err
		}
		if !d.IsDir() && len(d.Name()) == 64 && d.Name()[0] != '.' {
			n++
		}
		return nil
	})
	if err != nil {
		t.Fatalf("Failed to walk the cache: %v", err)
	}
	return n
}

// TestTrim_ReclaimsTheShortfall covers the pass itself: with the default
// watermarks of 20% and 25%, a volume 20% free is short 5% of its size, and
// the trim reclaims that much and stops, rather than emptying the cache.
func TestTrim_ReclaimsTheShortfall(t *testing.T) {
	cacheDir := filepath.Join(t.TempDir(), "cache")
	seedOldBlobs(t, cacheDir, trimBlobCount, trimBlobSize)

	res, err := Trim(context.Background(), TrimOptions{
		Dir:         cacheDir,
		MaxDuration: time.Minute,
		statfs:      fixedVolume(100*mib, 20*mib),
	})
	if err != nil {
		t.Fatalf("Trim failed: %v", err)
	}
	const need = 5 * mib
	if res.Outcome != storage.TrimOutcomeEvicted || res.BytesReclaimed != need || res.FilesDeleted != need/trimBlobSize {
		t.Errorf("Trim = %+v, want %q with %d bytes across %d files", res, storage.TrimOutcomeEvicted, need, need/trimBlobSize)
	}
	if got, want := countCachedBlobs(t, cacheDir), trimBlobCount-int(need/trimBlobSize); got != want {
		t.Errorf("%d blobs remain, want %d", got, want)
	}
}

func TestTrim_NoOpAtTheTarget(t *testing.T) {
	cacheDir := filepath.Join(t.TempDir(), "cache")
	seedOldBlobs(t, cacheDir, trimBlobCount, trimBlobSize)

	res, err := Trim(context.Background(), TrimOptions{Dir: cacheDir, statfs: fixedVolume(100*mib, 30*mib)})
	if err != nil {
		t.Fatalf("Trim failed: %v", err)
	}
	if res.Outcome != storage.TrimOutcomeNoop || res.FilesDeleted != 0 {
		t.Errorf("Trim = %+v, want a noop that deleted nothing", res)
	}
	if got := countCachedBlobs(t, cacheDir); got != trimBlobCount {
		t.Errorf("%d blobs remain, want all %d", got, trimBlobCount)
	}
}

// -trim-target-free asks one run for more room than -cache-target-free-space,
// for a caller under disk pressure. The pass works toward it even though free
// space is already above the configured target.
func TestTrim_TrimTargetFreeOverridesTheTarget(t *testing.T) {
	cacheDir := filepath.Join(t.TempDir(), "cache")
	seedOldBlobs(t, cacheDir, trimBlobCount, trimBlobSize)

	res, err := Trim(context.Background(), TrimOptions{
		Dir:             cacheDir,
		TargetFreeSpace: "25%",
		TrimTargetFree:  "35%",
		statfs:          fixedVolume(100*mib, 30*mib),
	})
	if err != nil {
		t.Fatalf("Trim failed: %v", err)
	}
	const need = 5 * mib
	if res.Outcome != storage.TrimOutcomeEvicted || res.BytesReclaimed != need {
		t.Errorf("Trim = %+v, want %q with %d bytes", res, storage.TrimOutcomeEvicted, need)
	}
}

// A second evictor must leave the work to the one already running, at once
// and without an error, so that a burst of downloads that each start one
// costs a single pass.
func TestTrim_HonorsTheLock(t *testing.T) {
	cacheDir := filepath.Join(t.TempDir(), "cache")
	seedOldBlobs(t, cacheDir, trimBlobCount, trimBlobSize)

	// Hold the lock as a peer evictor would: through a descriptor of its own.
	f, err := os.OpenFile(filepath.Join(cacheDir, wantLayoutDir, ".evict.lock"), os.O_CREATE|os.O_RDWR, 0644)
	if err != nil {
		t.Fatalf("Failed to open the lock file: %v", err)
	}
	defer f.Close()
	if err := syscall.Flock(int(f.Fd()), syscall.LOCK_EX|syscall.LOCK_NB); err != nil {
		t.Fatalf("Failed to take the lock: %v", err)
	}

	res, err := Trim(context.Background(), TrimOptions{Dir: cacheDir, statfs: fixedVolume(100*mib, 20*mib)})
	if err != nil {
		t.Errorf("Trim returned an error while a peer holds the lock: %v", err)
	}
	if res.Outcome != storage.TrimOutcomeBusy {
		t.Errorf("Outcome = %q, want %q", res.Outcome, storage.TrimOutcomeBusy)
	}
	if got := countCachedBlobs(t, cacheDir); got != trimBlobCount {
		t.Errorf("%d blobs remain while a peer holds the lock, want all %d", got, trimBlobCount)
	}
}

// -trim-max-duration bounds the pass. Stopping partway is a bounded run, not a
// failure, and what was deleted until then is reported.
func TestTrim_HonorsTrimMaxDuration(t *testing.T) {
	cacheDir := filepath.Join(t.TempDir(), "cache")
	seedOldBlobs(t, cacheDir, trimBlobCount, trimBlobSize)

	res, err := Trim(context.Background(), TrimOptions{
		Dir:         cacheDir,
		MaxDuration: time.Nanosecond,
		statfs:      fixedVolume(100*mib, 20*mib),
	})
	if err != nil {
		t.Errorf("Trim returned an error for a pass cut short by -trim-max-duration: %v", err)
	}
	if res.Outcome != storage.TrimOutcomeTimedOut {
		t.Errorf("Outcome = %q, want %q", res.Outcome, storage.TrimOutcomeTimedOut)
	}
	if got := trimBlobCount - countCachedBlobs(t, cacheDir); got != res.FilesDeleted {
		t.Errorf("FilesDeleted = %d, but %d blobs disappeared", res.FilesDeleted, got)
	}
}

// The downloads that fill the cache and the trim that empties it are separate
// processes, so they must agree on where blobs live. A blob a LockFreeCache
// pushed has to be one the trim can find.
func TestTrim_EvictsWhatTheCacheStored(t *testing.T) {
	dir := t.TempDir()
	cacheDir := filepath.Join(dir, "cache")
	c := newTestCache(t, cacheDir, true)
	_, out := stage(t, dir, "work/file", "cached by a download", false)
	push(t, c, out)
	blob := blobPath(cacheDir, out.Digest.Hash)
	old := time.Now().Add(-72 * time.Hour)
	if err := os.Chtimes(blob, old, old); err != nil {
		t.Fatalf("Failed to backdate %s: %v", blob, err)
	}
	// The downloaded file shares the blob's inode, which would rightly keep
	// the trim from unlinking it; the download is over, so let it go.
	if err := os.Remove(out.Path); err != nil {
		t.Fatalf("Failed to remove %s: %v", out.Path, err)
	}

	res, err := Trim(context.Background(), TrimOptions{Dir: cacheDir, statfs: fixedVolume(100*mib, 20*mib)})
	if err != nil {
		t.Fatalf("Trim failed: %v", err)
	}
	if res.FilesDeleted != 1 {
		t.Errorf("Trim = %+v, want it to delete the one cached blob", res)
	}
	assertAbsent(t, blob, "The cached blob")
}

func TestTrim_FailsOnAnUnparsableFlag(t *testing.T) {
	cacheDir := filepath.Join(t.TempDir(), "cache")
	for _, opts := range []TrimOptions{
		{Dir: cacheDir, MinFreeSpace: "lots"},
		{Dir: cacheDir, TrimTargetFree: "lots"},
	} {
		opts.statfs = fixedVolume(100*mib, 20*mib)
		res, err := Trim(context.Background(), opts)
		if err == nil || res.Outcome != storage.TrimOutcomeError {
			t.Errorf("Trim(%+v) = %+v, %v; want %q and an error", opts, res, err, storage.TrimOutcomeError)
		}
	}
}

// A cache directory that cannot be created makes the pass an error, before it
// takes the lock or looks at the volume.
func TestTrim_ReportsACacheDirThatCannotBeCreated(t *testing.T) {
	// A regular file where the cache directory's parent belongs fails the
	// mkdir with ENOTDIR, whoever runs the test.
	blocker := filepath.Join(t.TempDir(), "not-a-dir")
	if err := os.WriteFile(blocker, nil, 0644); err != nil {
		t.Fatalf("Failed to write %s: %v", blocker, err)
	}

	res, err := Trim(context.Background(), TrimOptions{
		Dir:    filepath.Join(blocker, "cache"),
		statfs: fixedVolume(100*mib, 1*mib),
	})
	if err == nil || res.Outcome != storage.TrimOutcomeError {
		t.Errorf("Trim = %+v, %v; want %q and an error", res, err, storage.TrimOutcomeError)
	}
}
