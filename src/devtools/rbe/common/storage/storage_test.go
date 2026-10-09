package storage

import (
	"bytes"
	"context"
	"errors"
	"io"
	"os"
	"path/filepath"
	"sync"
	"testing"
	"time"
)

func setupTestStorage(t *testing.T) *Storage {
	t.Helper()
	s, err := New(t.TempDir())
	if err != nil {
		t.Fatalf("Failed to initialize storage: %v", err)
	}
	return s
}

func TestStorage_WriteAndRead(t *testing.T) {
	s := setupTestStorage(t)

	hash := "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
	data := []byte("hello world storage test")

	if s.Has(hash) {
		t.Fatalf("Expected blob to not exist initially")
	}

	if err := s.Write(hash, data); err != nil {
		t.Fatalf("Write failed: %v", err)
	}

	if !s.Has(hash) {
		t.Fatalf("Expected blob to exist after write")
	}

	got, err := s.Read(hash)
	if err != nil {
		t.Fatalf("Read failed: %v", err)
	}

	if !bytes.Equal(got, data) {
		t.Errorf("Read data mismatch: got %q, want %q", got, data)
	}
}

func TestStorage_StoreFromReaderAndOpen(t *testing.T) {
	s := setupTestStorage(t)

	hash := "1234567890abcdef"
	data := []byte("streaming test data")

	if err := s.StoreFromReader(hash, bytes.NewReader(data)); err != nil {
		t.Fatalf("StoreFromReader failed: %v", err)
	}

	f, err := s.Open(hash)
	if err != nil {
		t.Fatalf("Open failed: %v", err)
	}
	defer f.Close()

	readData, err := io.ReadAll(f)
	if err != nil {
		t.Fatalf("ReadAll failed: %v", err)
	}

	if !bytes.Equal(readData, data) {
		t.Errorf("Data mismatch: got %q, want %q", readData, data)
	}
}

func TestStorage_BlobPath_ShortHash(t *testing.T) {
	s := setupTestStorage(t)

	shortHash := "abc"
	expected := filepath.Join(s.rootDir, "abc")
	if p := s.blobPath(shortHash); p != expected {
		t.Errorf("blobPath(%q) = %q, want %q", shortHash, p, expected)
	}

	longHash := "abcdef"
	expectedLong := filepath.Join(s.rootDir, "ab", "cd", "abcdef")
	if p := s.blobPath(longHash); p != expectedLong {
		t.Errorf("blobPath(%q) = %q, want %q", longHash, p, expectedLong)
	}
}

func TestStorage_TmpPath_Uniqueness(t *testing.T) {
	s := setupTestStorage(t)

	target := filepath.Join(s.rootDir, "target_file")
	tmp1 := s.tmpPath(target)
	tmp2 := s.tmpPath(target)

	if tmp1 == tmp2 {
		t.Errorf("Expected distinct tmp paths, got duplicate %q", tmp1)
	}
	if filepath.Dir(tmp1) != filepath.Dir(target) {
		t.Errorf("tmpPath directory mismatch: got %q, want %q", filepath.Dir(tmp1), filepath.Dir(target))
	}
	if filepath.Base(tmp1)[0] != '.' {
		t.Errorf("tmpPath base should start with leading dot, got %q", filepath.Base(tmp1))
	}
}

func TestStorage_Has_Directory(t *testing.T) {
	s := setupTestStorage(t)

	hash := "abcd1234"
	p := s.blobPath(hash)
	if err := os.MkdirAll(p, 0755); err != nil {
		t.Fatalf("Failed to create directory at blob path: %v", err)
	}

	if s.Has(hash) {
		t.Errorf("Has() should return false when path is a directory")
	}
}

func TestStorage_Read_NonExistent(t *testing.T) {
	s := setupTestStorage(t)

	if _, err := s.Read("nonexistent_hash"); err == nil {
		t.Errorf("Read() should return error for nonexistent blob")
	}
	if _, err := s.Open("nonexistent_hash"); err == nil {
		t.Errorf("Open() should return error for nonexistent blob")
	}
}

func TestStorage_ConcurrentWrites(t *testing.T) {
	s := setupTestStorage(t)

	hash := "concurrent_test_blob_hash_12345678"
	data := []byte("concurrent write payload")

	var wg sync.WaitGroup
	for i := 0; i < 20; i++ {
		wg.Add(1)
		go func(workerID int) {
			defer wg.Done()
			if err := s.Write(hash, data); err != nil {
				t.Errorf("Worker %d failed to write: %v", workerID, err)
			}
		}(i)
	}
	wg.Wait()

	got, err := s.Read(hash)
	if err != nil {
		t.Fatalf("Read after concurrent writes failed: %v", err)
	}
	if !bytes.Equal(got, data) {
		t.Errorf("Data corrupted after concurrent writes: got %q, want %q", got, data)
	}
}

func TestStorage_RootDir(t *testing.T) {
	tempDir := t.TempDir()
	s, err := New(tempDir)
	if err != nil {
		t.Fatalf("New failed: %v", err)
	}
	if s.RootDir() != tempDir {
		t.Errorf("s.RootDir() = %q, want %q", s.RootDir(), tempDir)
	}
}

func TestStorage_TouchIfOlderThan(t *testing.T) {
	s := setupTestStorage(t)

	hash := "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
	if err := s.Write(hash, []byte("touch-test-data")); err != nil {
		t.Fatalf("Write failed: %v", err)
	}

	p := s.blobPath(hash)
	now := time.Now()

	// 1. Fresh file (created just now) -> TouchIfOlderThan(4h) should NOT update mtime
	if s.TouchIfOlderThan(hash, 4*time.Hour) {
		t.Errorf("TouchIfOlderThan on fresh blob returned true, want false")
	}

	// 2. Stale file (mtime set to 5 hours ago) -> TouchIfOlderThan(4h) should update mtime
	fiveHoursAgo := now.Add(-5 * time.Hour)
	_ = os.Chtimes(p, fiveHoursAgo, fiveHoursAgo)

	if !s.TouchIfOlderThan(hash, 4*time.Hour) {
		t.Errorf("TouchIfOlderThan on 5h stale blob returned false, want true")
	}

	// Verify mtime was actually updated to near now
	info, err := os.Stat(p)
	if err != nil {
		t.Fatalf("Stat failed: %v", err)
	}
	if time.Since(info.ModTime()) > 1*time.Minute {
		t.Errorf("Blob mtime %v was not updated to near now (%v)", info.ModTime(), now)
	}

	// 3. Non-existent hash -> should safely return false without panic or error
	if s.TouchIfOlderThan("nonexistent_hash_12345", 4*time.Hour) {
		t.Errorf("TouchIfOlderThan on nonexistent blob returned true, want false")
	}
}

func TestStorage_EvictorInjectionAndLifecycle(t *testing.T) {
	s := setupTestStorage(t)

	cfg := DefaultEvictorConfig()
	cfg.CheckInterval = 50 * time.Millisecond

	// 1. Test manual Evictor dependency injection
	mockEvictor, err := NewEvictor(s.RootDir(), cfg, func(path string) (DiskStats, error) {
		return DiskStats{TotalBytes: 1000, FreeBytes: 500}, nil
	})
	if err != nil {
		t.Fatalf("NewEvictor failed: %v", err)
	}

	s.SetEvictor(mockEvictor)
	if s.Evictor() != mockEvictor {
		t.Errorf("s.Evictor() = %v, want injected %v", s.Evictor(), mockEvictor)
	}

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	// 2. Start injected evictor
	if err := s.StartEvictor(ctx, cfg); err != nil {
		t.Fatalf("StartEvictor failed: %v", err)
	}

	// Verify StartEvictor preserved the injected mockEvictor rather than overwriting it
	if s.Evictor() != mockEvictor {
		t.Errorf("s.Evictor() was overwritten during StartEvictor: got %v, want %v", s.Evictor(), mockEvictor)
	}

	// 3. Stop evictor
	s.StopEvictor()
}

func TestStorage_StartEvictor_AutoConstruct(t *testing.T) {
	s := setupTestStorage(t)

	cfg := DefaultEvictorConfig()
	cfg.CheckInterval = 50 * time.Millisecond

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	// Calling StartEvictor without prior SetEvictor should auto-construct Evictor
	if err := s.StartEvictor(ctx, cfg); err != nil {
		t.Fatalf("StartEvictor auto-construct failed: %v", err)
	}

	if s.Evictor() == nil {
		t.Errorf("s.Evictor() should not be nil after StartEvictor")
	}

	s.StopEvictor()
}

func TestStorage_StoreFileFunc_HappyPath(t *testing.T) {
	s := setupTestStorage(t)

	hash := "11223344556677889900aabbccddeeff11223344556677889900aabbccddeeff"
	content := []byte("streamed-file-content-via-storefilefunc")

	err := s.StoreFileFunc(hash, func(tmpPath string) error {
		return os.WriteFile(tmpPath, content, 0644)
	})
	if err != nil {
		t.Fatalf("StoreFileFunc failed: %v", err)
	}

	got, err := s.Read(hash)
	if err != nil {
		t.Fatalf("Read failed: %v", err)
	}
	if !bytes.Equal(got, content) {
		t.Errorf("Read data mismatch: got %q, want %q", got, content)
	}
}

func TestStorage_StoreFileFunc_ErrorCleansUpTemp(t *testing.T) {
	s := setupTestStorage(t)

	hash := "99887766554433221100ffeeddccbbaa99887766554433221100ffeeddccbbaa"
	var recordedTmpPath string

	expectedErr := errors.New("simulated upstream network failure")
	err := s.StoreFileFunc(hash, func(tmpPath string) error {
		recordedTmpPath = tmpPath
		_ = os.WriteFile(tmpPath, []byte("partial data"), 0644)
		return expectedErr
	})

	if !errors.Is(err, expectedErr) {
		t.Fatalf("StoreFileFunc error = %v, want %v", err, expectedErr)
	}

	if s.Has(hash) {
		t.Errorf("Blob %s was created despite writeFn error", hash)
	}

	if _, err := os.Stat(recordedTmpPath); !os.IsNotExist(err) {
		t.Errorf("Temporary file %s was not cleaned up after error", recordedTmpPath)
	}
}

func TestStorage_DiskUsage(t *testing.T) {
	s := setupTestStorage(t)

	usage, err := s.DiskUsage()
	if err != nil {
		t.Fatalf("DiskUsage() failed: %v", err)
	}
	if usage.TotalBytes <= 0 {
		t.Errorf("DiskUsage TotalBytes = %d, want > 0", usage.TotalBytes)
	}
	if usage.FreeBytes < 0 {
		t.Errorf("DiskUsage FreeBytes = %d, want >= 0", usage.FreeBytes)
	}
}

func TestEvictor_StatsAndConfig(t *testing.T) {
	cfg := DefaultEvictorConfig()
	cfg.MinFreeSpace = "20%"
	cfg.TargetFreeSpace = "30%"

	ev, err := NewEvictor(t.TempDir(), cfg, nil)
	if err != nil {
		t.Fatalf("NewEvictor failed: %v", err)
	}

	if ev.Config().MinFreeSpace != "20%" {
		t.Errorf("Config MinFreeSpace = %s, want 20%%", ev.Config().MinFreeSpace)
	}

	stats := ev.Stats()
	if stats.IsEvicting {
		t.Errorf("Expected IsEvicting = false initially")
	}
	if stats.TotalEvictionRuns != 0 {
		t.Errorf("Expected TotalEvictionRuns = 0 initially")
	}
}

// TestStorage_RemoveCorruptBlob covers which blobs RemoveCorruptBlob removes.
//
// The caller has found a materialized file of the wrong size and passes its
// Lstat. The blob must go if it is still that file, whatever size it reads
// now, or if it is still the wrong size. A blob that another process has
// already replaced with one of the right size must stay. Every call is counted.
func TestStorage_RemoveCorruptBlob(t *testing.T) {
	const good = "good"
	const bad = "good, then bytes written in place"
	for _, tc := range []struct {
		name string
		// cached is what the blob holds when RemoveCorruptBlob runs; empty
		// means there is no blob.
		cached string
		// linked makes the caller's file a hard link to the blob, as on the
		// hard link path. Otherwise it is a separate file holding bad, as on
		// the copy path.
		linked      bool
		wantRemoved bool
	}{
		{name: "same inode, wrong size", cached: bad, linked: true, wantRemoved: true},
		{name: "same inode, right size", cached: good, linked: true, wantRemoved: true},
		{name: "other inode, wrong size", cached: bad, linked: false, wantRemoved: true},
		{name: "other inode, right size", cached: good, linked: false, wantRemoved: false},
		{name: "no blob", cached: "", linked: false, wantRemoved: false},
	} {
		t.Run(tc.name, func(t *testing.T) {
			s := setupTestStorage(t)
			hash := testHash("c0")
			if tc.cached != "" {
				if err := s.Write(hash, []byte(tc.cached)); err != nil {
					t.Fatalf("Write failed: %v", err)
				}
			}

			pulled := filepath.Join(t.TempDir(), "pulled")
			if tc.linked {
				if hit, err := s.HardlinkTo(hash, pulled); err != nil || !hit {
					t.Fatalf("HardlinkTo: hit=%v err=%v, want hit=true err=nil", hit, err)
				}
			} else if err := os.WriteFile(pulled, []byte(bad), 0644); err != nil {
				t.Fatalf("Failed to write %s: %v", pulled, err)
			}
			info, err := os.Lstat(pulled)
			if err != nil {
				t.Fatalf("Failed to stat %s: %v", pulled, err)
			}

			removed, err := s.RemoveCorruptBlob(hash, int64(len(good)), info)
			if err != nil {
				t.Fatalf("RemoveCorruptBlob failed: %v", err)
			}
			if removed != tc.wantRemoved {
				t.Errorf("RemoveCorruptBlob removed = %v, want %v", removed, tc.wantRemoved)
			}
			wantCached := tc.cached != "" && !tc.wantRemoved
			if got := s.Has(hash); got != wantCached {
				t.Errorf("Has after RemoveCorruptBlob = %v, want %v", got, wantCached)
			}
			if got := s.Stats().CorruptBlobs; got != 1 {
				t.Errorf("Stats().CorruptBlobs = %d, want 1", got)
			}
		})
	}
}

// TestStorage_RemoveCorruptBlob_ReportsUnremovableBlob checks that a blob that
// is the wrong size but cannot be unlinked is reported as an error, not as a
// blob that was already gone. A non-empty directory at the blob path is the
// portable way to make the unlink fail.
func TestStorage_RemoveCorruptBlob_ReportsUnremovableBlob(t *testing.T) {
	s := setupTestStorage(t)
	hash := testHash("c1")
	if err := os.MkdirAll(filepath.Join(s.blobPath(hash), "occupant"), 0755); err != nil {
		t.Fatalf("Failed to seed the blocking directory: %v", err)
	}
	pulled := filepath.Join(t.TempDir(), "pulled")
	if err := os.WriteFile(pulled, []byte("wrong size"), 0644); err != nil {
		t.Fatalf("Failed to write %s: %v", pulled, err)
	}
	info, err := os.Lstat(pulled)
	if err != nil {
		t.Fatalf("Failed to stat %s: %v", pulled, err)
	}

	removed, err := s.RemoveCorruptBlob(hash, 1, info)
	if err == nil {
		t.Error("RemoveCorruptBlob returned nil error for a blob it could not unlink")
	}
	if removed {
		t.Error("RemoveCorruptBlob reported removing a blob it could not unlink")
	}
	if got := s.Stats().CorruptBlobs; got != 1 {
		t.Errorf("Stats().CorruptBlobs = %d, want 1: the corrupt file was still found", got)
	}
}

// TestStorage_RemoveCorruptBlob_ReportsUninspectableBlob checks that a blob
// whose path cannot be inspected is reported as an error, not as a blob that
// was already gone. A regular file where the blob's bucket directory belongs
// makes the Lstat fail with ENOTDIR rather than ENOENT.
func TestStorage_RemoveCorruptBlob_ReportsUninspectableBlob(t *testing.T) {
	s := setupTestStorage(t)
	hash := testHash("c2")
	bucket := filepath.Dir(s.blobPath(hash))
	if err := os.MkdirAll(filepath.Dir(bucket), 0755); err != nil {
		t.Fatalf("Failed to create the parent of %s: %v", bucket, err)
	}
	if err := os.WriteFile(bucket, nil, 0644); err != nil {
		t.Fatalf("Failed to write the file blocking %s: %v", bucket, err)
	}
	pulled := filepath.Join(t.TempDir(), "pulled")
	if err := os.WriteFile(pulled, []byte("wrong size"), 0644); err != nil {
		t.Fatalf("Failed to write %s: %v", pulled, err)
	}
	info, err := os.Lstat(pulled)
	if err != nil {
		t.Fatalf("Failed to stat %s: %v", pulled, err)
	}

	removed, err := s.RemoveCorruptBlob(hash, 1, info)
	if err == nil {
		t.Error("RemoveCorruptBlob returned nil error for a blob it could not inspect")
	}
	if removed {
		t.Error("RemoveCorruptBlob reported removing a blob it could not inspect")
	}
	if got := s.Stats().CorruptBlobs; got != 1 {
		t.Errorf("Stats().CorruptBlobs = %d, want 1: the corrupt file was still found", got)
	}
}

// TestStorage_RemoveCorruptBlob_LosesTheRaceQuietly covers another process
// unlinking the blob between RemoveCorruptBlob's Lstat and its own unlink. The
// blob is gone either way, so this is neither an error nor a removal by this
// call, but the corrupt file a caller was served is still counted.
func TestStorage_RemoveCorruptBlob_LosesTheRaceQuietly(t *testing.T) {
	s := setupTestStorage(t)
	hash := testHash("c3")
	if err := s.Write(hash, []byte("good, then bytes written in place")); err != nil {
		t.Fatalf("Write failed: %v", err)
	}
	pulled := filepath.Join(t.TempDir(), "pulled")
	if hit, err := s.HardlinkTo(hash, pulled); err != nil || !hit {
		t.Fatalf("HardlinkTo: hit=%v err=%v, want hit=true err=nil", hit, err)
	}
	info, err := os.Lstat(pulled)
	if err != nil {
		t.Fatalf("Failed to stat %s: %v", pulled, err)
	}

	// The other process wins: the blob is unlinked just before this call's
	// own unlink, which then finds nothing there.
	orig := removeBlob
	t.Cleanup(func() { removeBlob = orig })
	removeBlob = func(path string) error {
		if err := orig(path); err != nil {
			t.Fatalf("Failed to remove %s on behalf of the other process: %v", path, err)
		}
		return orig(path)
	}

	removed, err := s.RemoveCorruptBlob(hash, int64(len("good")), info)
	if err != nil {
		t.Errorf("RemoveCorruptBlob failed after losing the race: %v", err)
	}
	if removed {
		t.Error("RemoveCorruptBlob reported removing a blob another process removed")
	}
	if s.Has(hash) {
		t.Error("Blob is still cached after the race")
	}
	if got := s.Stats().CorruptBlobs; got != 1 {
		t.Errorf("Stats().CorruptBlobs = %d, want 1: the corrupt file was still found", got)
	}
}
