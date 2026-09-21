package storage

import (
	"bytes"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"syscall"
	"testing"
	"time"
)

// testHash returns a syntactically valid 64-character hex blob name seeded with
// the given prefix, so each test can use a distinct sharded leaf bucket.
func testHash(prefix string) string {
	return prefix + strings.Repeat("0", 64-len(prefix))
}

// inodeOf returns the inode number of path, used to distinguish a hard link
// (shared inode) from a copy (distinct inode).
func inodeOf(t *testing.T, path string) uint64 {
	t.Helper()
	info, err := os.Stat(path)
	if err != nil {
		t.Fatalf("Failed to stat %s: %v", path, err)
	}
	st, ok := info.Sys().(*syscall.Stat_t)
	if !ok {
		t.Fatalf("Unexpected FileInfo.Sys() type %T for %s; expected *syscall.Stat_t", info.Sys(), path)
	}
	return st.Ino
}

// stubLinkFailure replaces linkFile with one that always fails with errno,
// restoring the original when the test ends.
//
// Injection is the only practical way to exercise the copy fallback: EMLINK
// requires creating 65,000 links to a single inode, EXDEV requires a second
// mount point, and EPERM requires changing a system-wide sysctl. None of those
// are available to a hermetic unit test.
func stubLinkFailure(t *testing.T, errno syscall.Errno) {
	t.Helper()
	orig := linkFile
	linkFile = func(oldname, newname string) error {
		return &os.LinkError{Op: "link", Old: oldname, New: newname, Err: errno}
	}
	t.Cleanup(func() { linkFile = orig })
}

// forbidLink fails the test if link(2) is attempted at all. Avoiding the
// syscall, not merely surviving its failure, is the entire reason
// WithoutHardlinks exists, and nothing else observable distinguishes the two.
func forbidLink(t *testing.T) {
	t.Helper()
	orig := linkFile
	linkFile = func(oldname, newname string) error {
		t.Errorf("Unexpected link(%s, %s): hard linking is disabled on this Storage", oldname, newname)
		return &os.LinkError{Op: "link", Old: oldname, New: newname, Err: syscall.EXDEV}
	}
	t.Cleanup(func() { linkFile = orig })
}

// setupCopyOnlyStorage returns a Storage that stands in for a deployment where
// the cache reaches the process through a mount, such as containerized
// Cuttlefish on ARM.
func setupCopyOnlyStorage(t *testing.T) *Storage {
	t.Helper()
	s, err := New(t.TempDir(), WithoutHardlinks())
	if err != nil {
		t.Fatalf("Failed to initialize storage: %v", err)
	}
	return s
}

// assertCopyFallbackCounted checks that exactly one copy fallback was recorded
// and that it landed in the bucket errno belongs to.
//
// Asserting the bucket rather than the total matters because the buckets are
// what the counters are for: EMLINK is expected background noise on a host
// serving popular blobs, while EXDEV means every blob is being written twice
// and someone should look at the mount layout. A call site that recorded the
// wrong one would report a real problem as routine, or the reverse.
func assertCopyFallbackCounted(t *testing.T, s *Storage, errno syscall.Errno) {
	t.Helper()
	stats := s.Stats()
	var got int64
	switch errno {
	case syscall.EMLINK:
		got = stats.CopyFallbackEMLINK
	case syscall.EXDEV:
		got = stats.CopyFallbackEXDEV
	default:
		got = stats.CopyFallbackOther
	}
	if got != 1 {
		t.Errorf("Expected %v to be counted in its own bucket, got %d (EMLINK=%d, EXDEV=%d, Other=%d)",
			errno, got, stats.CopyFallbackEMLINK, stats.CopyFallbackEXDEV, stats.CopyFallbackOther)
	}
	if total := stats.CopyFallbacks(); total != 1 {
		t.Errorf("Expected exactly one copy fallback in total, got %d", total)
	}
}

func TestIsCopyFallbackErr(t *testing.T) {
	tests := []struct {
		name string
		err  error
		want bool
	}{
		{"EMLINK link ceiling", syscall.EMLINK, true},
		{"EXDEV cross mount", syscall.EXDEV, true},
		{"EPERM protected hardlinks", syscall.EPERM, true},
		{"EOPNOTSUPP no link support", syscall.EOPNOTSUPP, true},
		{"ENOSYS no link support", syscall.ENOSYS, true},
		{"wrapped in LinkError", &os.LinkError{Op: "link", Err: syscall.EXDEV}, true},
		{"ENOSPC disk full", syscall.ENOSPC, false},
		{"EIO media failure", syscall.EIO, false},
		{"EROFS read only", syscall.EROFS, false},
		{"ENOENT missing source", syscall.ENOENT, false},
		{"EEXIST existing destination", syscall.EEXIST, false},
		{"nil", nil, false},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			if got := isCopyFallbackErr(tc.err); got != tc.want {
				t.Errorf("isCopyFallbackErr(%v) = %v, want %v", tc.err, got, tc.want)
			}
		})
	}
}

func TestHardlinkTo_SharesInode(t *testing.T) {
	s := setupTestStorage(t)
	hash := testHash("ab")
	data := []byte("hard linked blob contents")

	if err := s.Write(hash, data); err != nil {
		t.Fatalf("Write failed: %v", err)
	}

	dest := filepath.Join(t.TempDir(), "artifact.bin")
	hit, err := s.HardlinkTo(hash, dest)
	if err != nil {
		t.Fatalf("HardlinkTo failed: %v", err)
	}
	if !hit {
		t.Fatalf("HardlinkTo reported a miss for a blob that was just written")
	}

	got, err := os.ReadFile(dest)
	if err != nil {
		t.Fatalf("Failed to read destination: %v", err)
	}
	if !bytes.Equal(got, data) {
		t.Errorf("Destination content mismatch: got %q, want %q", got, data)
	}

	if srcIno, dstIno := inodeOf(t, s.blobPath(hash)), inodeOf(t, dest); srcIno != dstIno {
		t.Errorf("Expected destination to share the blob's inode, got blob inode %d and destination inode %d", srcIno, dstIno)
	}
}

func TestHardlinkTo_MissIsNotAnError(t *testing.T) {
	s := setupTestStorage(t)
	dest := filepath.Join(t.TempDir(), "artifact.bin")

	hit, err := s.HardlinkTo(testHash("cd"), dest)
	if err != nil {
		t.Fatalf("HardlinkTo on an uncached blob returned an error: %v", err)
	}
	if hit {
		t.Errorf("HardlinkTo reported a hit for an uncached blob")
	}
	if _, err := os.Stat(dest); !errors.Is(err, os.ErrNotExist) {
		t.Errorf("Expected destination to be absent after a miss, stat returned %v", err)
	}
}

func TestHardlinkTo_CreatesMissingDestinationDirectories(t *testing.T) {
	s := setupTestStorage(t)
	hash := testHash("ef")
	if err := s.Write(hash, []byte("nested destination")); err != nil {
		t.Fatalf("Write failed: %v", err)
	}

	dest := filepath.Join(t.TempDir(), "deeply", "nested", "output", "artifact.bin")
	hit, err := s.HardlinkTo(hash, dest)
	if err != nil {
		t.Fatalf("HardlinkTo failed: %v", err)
	}
	if !hit {
		t.Fatalf("HardlinkTo reported a miss for a blob that was just written")
	}
	if _, err := os.Stat(dest); err != nil {
		t.Errorf("Expected destination to exist, stat returned %v", err)
	}
}

func TestHardlinkTo_ReplacesExistingDestination(t *testing.T) {
	s := setupTestStorage(t)
	hash := testHash("12")
	data := []byte("replacement contents")
	if err := s.Write(hash, data); err != nil {
		t.Fatalf("Write failed: %v", err)
	}

	dest := filepath.Join(t.TempDir(), "artifact.bin")
	if err := os.WriteFile(dest, []byte("stale contents that must be replaced"), 0644); err != nil {
		t.Fatalf("Failed to seed a stale destination: %v", err)
	}

	hit, err := s.HardlinkTo(hash, dest)
	if err != nil {
		t.Fatalf("HardlinkTo failed: %v", err)
	}
	if !hit {
		t.Fatalf("HardlinkTo reported a miss for a blob that was just written")
	}

	got, err := os.ReadFile(dest)
	if err != nil {
		t.Fatalf("Failed to read destination: %v", err)
	}
	if !bytes.Equal(got, data) {
		t.Errorf("Stale destination was not replaced: got %q, want %q", got, data)
	}
}

func TestHardlinkTo_CopyFallback(t *testing.T) {
	// Every errno that should degrade to a copy must produce a correct,
	// independent file rather than an error.
	for _, errno := range []syscall.Errno{syscall.EMLINK, syscall.EXDEV, syscall.EPERM, syscall.EOPNOTSUPP, syscall.ENOSYS} {
		t.Run(errno.Error(), func(t *testing.T) {
			s := setupTestStorage(t)
			hash := testHash("34")
			data := []byte("copied rather than linked")
			if err := s.Write(hash, data); err != nil {
				t.Fatalf("Write failed: %v", err)
			}

			stubLinkFailure(t, errno)

			dest := filepath.Join(t.TempDir(), "artifact.bin")
			hit, err := s.HardlinkTo(hash, dest)
			if err != nil {
				t.Fatalf("HardlinkTo failed instead of falling back to a copy: %v", err)
			}
			if !hit {
				t.Fatalf("HardlinkTo reported a miss for a blob that was just written")
			}

			got, err := os.ReadFile(dest)
			if err != nil {
				t.Fatalf("Failed to read destination: %v", err)
			}
			if !bytes.Equal(got, data) {
				t.Errorf("Copied content mismatch: got %q, want %q", got, data)
			}
			if srcIno, dstIno := inodeOf(t, s.blobPath(hash)), inodeOf(t, dest); srcIno == dstIno {
				t.Errorf("Expected the copy fallback to produce a distinct inode, but both are %d", srcIno)
			}

			// A copy must not leave its temporary file behind.
			assertNoLeftoverTempFiles(t, filepath.Dir(dest))

			// The copy is only half the job: an unreported fallback is
			// invisible, and these are the counters that tell an operator a
			// host is quietly writing every blob twice.
			assertCopyFallbackCounted(t, s, errno)
		})
	}
}

func TestHardlinkTo_CopyFallbackOnMissingBlobIsAMiss(t *testing.T) {
	s := setupTestStorage(t)
	stubLinkFailure(t, syscall.EXDEV)

	dest := filepath.Join(t.TempDir(), "artifact.bin")
	hit, err := s.HardlinkTo(testHash("56"), dest)
	if err != nil {
		t.Fatalf("Expected a plain miss when the copy fallback cannot open the blob, got error: %v", err)
	}
	if hit {
		t.Errorf("HardlinkTo reported a hit for an uncached blob")
	}
}

func TestHardlinkTo_NonRecoverableErrorPropagates(t *testing.T) {
	s := setupTestStorage(t)
	hash := testHash("78")
	if err := s.Write(hash, []byte("contents")); err != nil {
		t.Fatalf("Write failed: %v", err)
	}

	// ENOSPC is not something a copy could fix, so it must surface.
	stubLinkFailure(t, syscall.ENOSPC)

	if _, err := s.HardlinkTo(hash, filepath.Join(t.TempDir(), "artifact.bin")); err == nil {
		t.Errorf("Expected ENOSPC to propagate, got nil error")
	}
}

// TestHardlinkTo_UncreatableDestinationDirectory checks that a caller who asks
// for an impossible destination gets an error rather than a silent miss, since
// the blob is present and a miss would send the caller off to refetch it.
func TestHardlinkTo_UncreatableDestinationDirectory(t *testing.T) {
	s := setupTestStorage(t)
	hash := testHash("7a")
	if err := s.Write(hash, []byte("contents")); err != nil {
		t.Fatalf("Write failed: %v", err)
	}

	// A regular file where a parent directory is expected makes MkdirAll fail
	// with ENOTDIR regardless of ownership or umask.
	blocker := filepath.Join(t.TempDir(), "not-a-directory")
	if err := os.WriteFile(blocker, []byte("in the way"), 0644); err != nil {
		t.Fatalf("Failed to seed the blocking file: %v", err)
	}

	if _, err := s.HardlinkTo(hash, filepath.Join(blocker, "artifact.bin")); err == nil {
		t.Errorf("Expected an error when the destination directory cannot be created, got nil")
	}
}

// TestHardlinkTo_UnremovableExistingDestination covers the replace path when
// the existing destination cannot be unlinked. A non-empty directory is the
// portable way to provoke this: link(2) refuses with EEXIST and the subsequent
// remove refuses with ENOTEMPTY.
func TestHardlinkTo_UnremovableExistingDestination(t *testing.T) {
	s := setupTestStorage(t)
	hash := testHash("7b")
	if err := s.Write(hash, []byte("contents")); err != nil {
		t.Fatalf("Write failed: %v", err)
	}

	dest := filepath.Join(t.TempDir(), "artifact.bin")
	if err := os.MkdirAll(filepath.Join(dest, "occupant"), 0755); err != nil {
		t.Fatalf("Failed to seed the blocking directory: %v", err)
	}

	if _, err := s.HardlinkTo(hash, dest); err == nil {
		t.Errorf("Expected an error when the existing destination cannot be replaced, got nil")
	}
}

// TestHardlinkTo_CopyFallbackFailurePropagates covers the compound failure:
// the link is refused for a structural reason and the copy that should rescue
// it cannot be published either. That is distinct from a missing blob, so it
// must surface as an error rather than as a cache miss.
func TestHardlinkTo_CopyFallbackFailurePropagates(t *testing.T) {
	s := setupTestStorage(t)
	hash := testHash("7c")
	if err := s.Write(hash, []byte("contents")); err != nil {
		t.Fatalf("Write failed: %v", err)
	}

	stubLinkFailure(t, syscall.EXDEV)

	// The copy itself succeeds into a temporary sibling; the rename onto a
	// non-empty directory is what fails.
	destDir := t.TempDir()
	dest := filepath.Join(destDir, "artifact.bin")
	if err := os.MkdirAll(filepath.Join(dest, "occupant"), 0755); err != nil {
		t.Fatalf("Failed to seed the blocking directory: %v", err)
	}

	hit, err := s.HardlinkTo(hash, dest)
	if err == nil {
		t.Fatalf("Expected an error when the copy fallback cannot be published, got nil")
	}
	if hit {
		t.Errorf("Expected hit=false alongside the error, got true")
	}
	// The abandoned copy must not be left behind: temporary names are
	// invisible to the evictor, so a leak here is permanent.
	assertNoLeftoverTempFiles(t, destDir)
}

// TestHardlinkTo_ConcurrentEvictionRace hammers the TOCTOU window that the
// implementation is designed around: a blob may be unlinked by the evictor at
// any instant, including between the caller's decision to link and the link
// syscall itself. Every outcome must be either a correct hit or a clean miss,
// never an error and never a partially materialized destination.
func TestHardlinkTo_ConcurrentEvictionRace(t *testing.T) {
	s := setupTestStorage(t)
	hash := testHash("9a")
	data := []byte("contents that must never be observed torn")
	destDir := t.TempDir()

	const iterations = 300
	var wg sync.WaitGroup
	failures := make(chan error, iterations)

	wg.Add(1)
	go func() {
		defer wg.Done()
		for i := 0; i < iterations; i++ {
			if err := s.Write(hash, data); err != nil {
				failures <- fmt.Errorf("Write failed: %v", err)
				return
			}
			if err := os.Remove(s.blobPath(hash)); err != nil && !errors.Is(err, os.ErrNotExist) {
				failures <- fmt.Errorf("Simulated eviction failed: %v", err)
				return
			}
		}
	}()

	wg.Add(1)
	go func() {
		defer wg.Done()
		for i := 0; i < iterations; i++ {
			dest := filepath.Join(destDir, fmt.Sprintf("artifact-%d.bin", i))
			hit, err := s.HardlinkTo(hash, dest)
			if err != nil {
				failures <- fmt.Errorf("HardlinkTo returned an error during concurrent eviction: %v", err)
				return
			}
			if !hit {
				if _, err := os.Stat(dest); !errors.Is(err, os.ErrNotExist) {
					failures <- fmt.Errorf("HardlinkTo reported a miss but left %s behind (stat: %v)", dest, err)
					return
				}
				continue
			}
			got, err := os.ReadFile(dest)
			if err != nil {
				failures <- fmt.Errorf("HardlinkTo reported a hit but the destination is unreadable: %v", err)
				return
			}
			if !bytes.Equal(got, data) {
				failures <- fmt.Errorf("HardlinkTo produced torn contents: got %q, want %q", got, data)
				return
			}
		}
	}()

	wg.Wait()
	close(failures)
	for err := range failures {
		t.Error(err)
	}
}

func TestHardlinkFrom_SharesInode(t *testing.T) {
	s := setupTestStorage(t)
	hash := testHash("bc")
	data := []byte("ingested blob contents")

	src := filepath.Join(t.TempDir(), "downloaded.bin")
	if err := os.WriteFile(src, data, 0644); err != nil {
		t.Fatalf("Failed to seed the source file: %v", err)
	}

	if err := s.HardlinkFrom(hash, src); err != nil {
		t.Fatalf("HardlinkFrom failed: %v", err)
	}
	if !s.Has(hash) {
		t.Fatalf("Expected the blob to be cached after ingestion")
	}

	got, err := s.Read(hash)
	if err != nil {
		t.Fatalf("Read failed: %v", err)
	}
	if !bytes.Equal(got, data) {
		t.Errorf("Ingested content mismatch: got %q, want %q", got, data)
	}
	if srcIno, blobIno := inodeOf(t, src), inodeOf(t, s.blobPath(hash)); srcIno != blobIno {
		t.Errorf("Expected the cached blob to share the source's inode, got source inode %d and blob inode %d", srcIno, blobIno)
	}
	assertNoLeftoverTempFiles(t, filepath.Dir(s.blobPath(hash)))
}

// TestHardlinkFrom_RefreshesStaleModTime pins the documented mtime behavior: a
// source file older than the evictor's grace period must enter the cache with
// a current timestamp, otherwise it would be evicted on the next sweep and the
// ingestion would have been pure waste. The side effect on the source file is
// intentional and asserted here so it cannot be changed silently.
func TestHardlinkFrom_RefreshesStaleModTime(t *testing.T) {
	s := setupTestStorage(t)
	hash := testHash("de")

	src := filepath.Join(t.TempDir(), "restored-from-build-cache.bin")
	if err := os.WriteFile(src, []byte("an old artifact"), 0644); err != nil {
		t.Fatalf("Failed to seed the source file: %v", err)
	}
	stale := time.Now().Add(-30 * 24 * time.Hour)
	if err := os.Chtimes(src, stale, stale); err != nil {
		t.Fatalf("Failed to age the source file: %v", err)
	}

	before := time.Now()
	if err := s.HardlinkFrom(hash, src); err != nil {
		t.Fatalf("HardlinkFrom failed: %v", err)
	}

	blobInfo, err := os.Stat(s.blobPath(hash))
	if err != nil {
		t.Fatalf("Failed to stat the cached blob: %v", err)
	}
	if blobInfo.ModTime().Before(before.Add(-time.Second)) {
		t.Errorf("Expected the cached blob to carry a fresh mtime, got %v (ingested at %v)", blobInfo.ModTime(), before)
	}

	srcInfo, err := os.Stat(src)
	if err != nil {
		t.Fatalf("Failed to stat the source file: %v", err)
	}
	if !srcInfo.ModTime().Equal(blobInfo.ModTime()) {
		t.Errorf("Expected the shared inode to give the source and the blob the same mtime, got source %v and blob %v", srcInfo.ModTime(), blobInfo.ModTime())
	}
}

func TestHardlinkFrom_AlreadyCachedIsANoOp(t *testing.T) {
	s := setupTestStorage(t)
	hash := testHash("f0")
	cached := []byte("the authoritative cached contents")
	if err := s.Write(hash, cached); err != nil {
		t.Fatalf("Write failed: %v", err)
	}
	originalIno := inodeOf(t, s.blobPath(hash))

	src := filepath.Join(t.TempDir(), "duplicate.bin")
	if err := os.WriteFile(src, cached, 0644); err != nil {
		t.Fatalf("Failed to seed the source file: %v", err)
	}

	if err := s.HardlinkFrom(hash, src); err != nil {
		t.Fatalf("HardlinkFrom on an already cached blob failed: %v", err)
	}

	if got := inodeOf(t, s.blobPath(hash)); got != originalIno {
		t.Errorf("Expected the cached blob to be left untouched (inode %d), but it was replaced (inode %d)", originalIno, got)
	}
}

func TestHardlinkFrom_MissingSource(t *testing.T) {
	s := setupTestStorage(t)
	hash := testHash("a1")

	err := s.HardlinkFrom(hash, filepath.Join(t.TempDir(), "does-not-exist.bin"))
	if err == nil {
		t.Fatalf("Expected an error when ingesting a nonexistent source file")
	}
	if s.Has(hash) {
		t.Errorf("Expected no blob to be cached after a failed ingestion")
	}
	assertNoLeftoverTempFiles(t, filepath.Dir(s.blobPath(hash)))
}

func TestHardlinkFrom_CopyFallback(t *testing.T) {
	s := setupTestStorage(t)
	hash := testHash("a2")
	data := []byte("ingested by copying")

	src := filepath.Join(t.TempDir(), "downloaded.bin")
	if err := os.WriteFile(src, data, 0640); err != nil {
		t.Fatalf("Failed to seed the source file: %v", err)
	}

	stubLinkFailure(t, syscall.EXDEV)

	if err := s.HardlinkFrom(hash, src); err != nil {
		t.Fatalf("HardlinkFrom failed instead of falling back to a copy: %v", err)
	}

	got, err := s.Read(hash)
	if err != nil {
		t.Fatalf("Read failed: %v", err)
	}
	if !bytes.Equal(got, data) {
		t.Errorf("Copied content mismatch: got %q, want %q", got, data)
	}
	if srcIno, blobIno := inodeOf(t, src), inodeOf(t, s.blobPath(hash)); srcIno == blobIno {
		t.Errorf("Expected the copy fallback to produce a distinct inode, but both are %d", srcIno)
	}

	blobInfo, err := os.Stat(s.blobPath(hash))
	if err != nil {
		t.Fatalf("Failed to stat the cached blob: %v", err)
	}
	if got, want := blobInfo.Mode().Perm(), os.FileMode(0640); got != want {
		t.Errorf("Copy fallback did not preserve permissions: got %v, want %v", got, want)
	}
	assertNoLeftoverTempFiles(t, filepath.Dir(s.blobPath(hash)))

	// Ingesting by copy is the case that costs a second full-size write, so
	// it has to be reported as well as performed.
	assertCopyFallbackCounted(t, s, syscall.EXDEV)
	if got := s.Stats().Ingested; got != 1 {
		t.Errorf("Expected the ingest to be counted, got Ingested=%d", got)
	}
}

func TestHardlinkFrom_NonRecoverableErrorPropagates(t *testing.T) {
	s := setupTestStorage(t)
	hash := testHash("a3")

	src := filepath.Join(t.TempDir(), "downloaded.bin")
	if err := os.WriteFile(src, []byte("contents"), 0644); err != nil {
		t.Fatalf("Failed to seed the source file: %v", err)
	}

	stubLinkFailure(t, syscall.ENOSPC)

	if err := s.HardlinkFrom(hash, src); err == nil {
		t.Errorf("Expected ENOSPC to propagate, got nil error")
	}
	if s.Has(hash) {
		t.Errorf("Expected no blob to be cached after a failed ingestion")
	}
}

// TestHardlinkFrom_UncreatableShard covers the case where the sharded leaf
// directory cannot be created, which is the earliest way ingestion can fail.
func TestHardlinkFrom_UncreatableShard(t *testing.T) {
	s := setupTestStorage(t)
	hash := testHash("a6")

	src := filepath.Join(t.TempDir(), "downloaded.bin")
	if err := os.WriteFile(src, []byte("contents"), 0644); err != nil {
		t.Fatalf("Failed to seed the source file: %v", err)
	}

	// Occupy the first level of the shard with a regular file so MkdirAll
	// fails with ENOTDIR.
	if err := os.WriteFile(filepath.Join(s.RootDir(), hash[:2]), []byte("in the way"), 0644); err != nil {
		t.Fatalf("Failed to seed the blocking file: %v", err)
	}

	if err := s.HardlinkFrom(hash, src); err == nil {
		t.Errorf("Expected an error when the cache shard cannot be created, got nil")
	}
}

// TestHardlinkFrom_CopyFallbackFailurePropagates covers the compound failure:
// the link is refused for a structural reason and the copy that should rescue
// it cannot run either. The caller must hear about it, since nothing was
// cached.
func TestHardlinkFrom_CopyFallbackFailurePropagates(t *testing.T) {
	s := setupTestStorage(t)
	hash := testHash("a7")

	stubLinkFailure(t, syscall.EXDEV)

	err := s.HardlinkFrom(hash, filepath.Join(t.TempDir(), "vanished.bin"))
	if err == nil {
		t.Fatalf("Expected an error when both the link and the copy fail, got nil")
	}
	if s.Has(hash) {
		t.Errorf("Expected no blob to be cached after a failed ingestion")
	}
	if got := s.Stats().CopyFallbacks(); got != 0 {
		t.Errorf("Expected an attempted-but-failed copy not to be counted, got CopyFallbacks=%d", got)
	}
	assertNoLeftoverTempFiles(t, filepath.Dir(s.blobPath(hash)))
}

// TestHardlinkFrom_LosesPublishRace covers the window between the Has
// pre-check and link(2), during which another process can publish the same
// digest. Because blobs are content addressed the rival's entry is
// interchangeable with ours, so the call reports success and leaves the rival's
// inode in place rather than replacing it.
func TestHardlinkFrom_LosesPublishRace(t *testing.T) {
	s := setupTestStorage(t)
	hash := testHash("a5")
	data := []byte("published by the winner")

	src := filepath.Join(t.TempDir(), "downloaded.bin")
	if err := os.WriteFile(src, data, 0644); err != nil {
		t.Fatalf("Failed to seed the source file: %v", err)
	}

	// Stand in for the rival publisher by populating the blob path in the
	// instant between the Has check and the link, which is the only way to hit
	// this branch deterministically.
	var rivalIno uint64
	orig := linkFile
	linkFile = func(oldname, newname string) error {
		if err := os.WriteFile(newname, data, 0644); err != nil {
			t.Fatalf("Failed to simulate a rival publisher: %v", err)
		}
		rivalIno = inodeOf(t, newname)
		return orig(oldname, newname)
	}
	t.Cleanup(func() { linkFile = orig })

	if err := s.HardlinkFrom(hash, src); err != nil {
		t.Fatalf("Losing the publish race should not be an error, got: %v", err)
	}
	if got := inodeOf(t, s.blobPath(hash)); got != rivalIno {
		t.Errorf("Expected the rival's blob to survive (inode %d), but it was replaced (inode %d)", rivalIno, got)
	}
	if got := s.Stats().IngestDeduped; got != 1 {
		t.Errorf("Expected the lost race to count as a dedup, got IngestDeduped=%d", got)
	}
	assertNoLeftoverTempFiles(t, filepath.Dir(s.blobPath(hash)))
}

// TestHardlinkFrom_Concurrent verifies that many callers racing to ingest the
// same content-addressed blob all succeed. Exactly one of them wins the link,
// and the rest observe EEXIST and no-op, so the published inode is decided once
// and never churns: no caller can replace a blob that another caller's
// destination files are already linked to.
func TestHardlinkFrom_Concurrent(t *testing.T) {
	s := setupTestStorage(t)
	hash := testHash("a4")
	data := []byte("identical content produced by every worker")
	srcDir := t.TempDir()

	const workers = 24
	var wg sync.WaitGroup
	failures := make(chan error, workers)
	srcs := make([]string, 0, workers)

	for i := 0; i < workers; i++ {
		src := filepath.Join(srcDir, fmt.Sprintf("worker-%d.bin", i))
		if err := os.WriteFile(src, data, 0644); err != nil {
			t.Fatalf("Failed to seed the source file: %v", err)
		}
		srcs = append(srcs, src)

		wg.Add(1)
		go func(src string) {
			defer wg.Done()
			if err := s.HardlinkFrom(hash, src); err != nil {
				failures <- fmt.Errorf("HardlinkFrom failed: %v", err)
			}
		}(src)
	}

	wg.Wait()
	close(failures)
	for err := range failures {
		t.Error(err)
	}

	got, err := s.Read(hash)
	if err != nil {
		t.Fatalf("Read failed after concurrent ingestion: %v", err)
	}
	if !bytes.Equal(got, data) {
		t.Errorf("Concurrent ingestion produced wrong content: got %q, want %q", got, data)
	}

	// Exactly one worker's file is the blob. More than one would mean the
	// sources were somehow merged; none would mean the blob was copied rather
	// than linked, or that a late writer replaced the winner's entry.
	blobIno := inodeOf(t, s.blobPath(hash))
	shared := 0
	for _, src := range srcs {
		if inodeOf(t, src) == blobIno {
			shared++
		}
	}
	if shared != 1 {
		t.Errorf("Expected the cached blob to share an inode with exactly one worker's file, got %d", shared)
	}

	stats := s.Stats()
	if stats.Ingested != 1 {
		t.Errorf("Expected exactly one worker to publish the blob, got Ingested=%d", stats.Ingested)
	}
	if total := stats.Ingested + stats.IngestDeduped; total != workers {
		t.Errorf("Expected every worker to be accounted for, got Ingested=%d and IngestDeduped=%d", stats.Ingested, stats.IngestDeduped)
	}
	assertNoLeftoverTempFiles(t, filepath.Dir(s.blobPath(hash)))
}

// assertNoLeftoverTempFiles fails if dir still contains any dot-prefixed
// temporary file. Leaked temp files are invisible to the evictor (it only
// considers 64-character non-dot entries), so they would accumulate forever.
func assertNoLeftoverTempFiles(t *testing.T, dir string) {
	t.Helper()
	entries, err := os.ReadDir(dir)
	if err != nil {
		t.Fatalf("Failed to read %s: %v", dir, err)
	}
	for _, entry := range entries {
		if strings.HasPrefix(entry.Name(), ".") {
			t.Errorf("Found a leaked temporary file %s in %s", entry.Name(), dir)
		}
	}
}

// TestCopyHelpersErrorPaths exercises failure modes of the copy helpers that
// the callers above cannot provoke on their own. Using a directory where a
// regular file is expected fails the same way for every user and every umask,
// unlike the permission tricks that would be needed otherwise.
func TestCopyHelpersErrorPaths(t *testing.T) {
	dir := t.TempDir()
	regular := filepath.Join(dir, "regular.bin")
	if err := os.WriteFile(regular, []byte("contents"), 0644); err != nil {
		t.Fatalf("Failed to seed the source file: %v", err)
	}

	// Opening a directory for writing fails outright, so nothing is copied.
	if err := copyFile(regular, dir); err == nil {
		t.Errorf("Expected copyFile to fail when the destination is a directory, got nil")
	}

	// A directory opens and stats like a file but cannot be streamed, so this
	// one fails partway through instead of at the open.
	if err := copyFile(dir, filepath.Join(dir, "out.bin")); err == nil {
		t.Errorf("Expected copyFile to fail when the source is a directory, got nil")
	}

	// copyFileAtomic needs to place its temporary file next to the
	// destination, which is impossible if that directory does not exist.
	if err := copyFileAtomic(regular, filepath.Join(dir, "absent", "out.bin")); err == nil {
		t.Errorf("Expected copyFileAtomic to fail when the temporary file cannot be created, got nil")
	}
}

// TestHardlinkTo_WithoutHardlinks covers the declared cross-device deployment.
// The blob must still be materialized correctly, but as an independent inode
// and without the wasted link syscall.
func TestHardlinkTo_WithoutHardlinks(t *testing.T) {
	s := setupCopyOnlyStorage(t)
	forbidLink(t)

	hash := testHash("b1")
	data := []byte("served by copy because the cache is on another drive")
	if err := s.Write(hash, data); err != nil {
		t.Fatalf("Write failed: %v", err)
	}

	dest := filepath.Join(t.TempDir(), "nested", "artifact.bin")
	hit, err := s.HardlinkTo(hash, dest)
	if err != nil {
		t.Fatalf("HardlinkTo failed: %v", err)
	}
	if !hit {
		t.Fatalf("HardlinkTo reported a miss for a blob that was just written")
	}

	got, err := os.ReadFile(dest)
	if err != nil {
		t.Fatalf("Failed to read destination: %v", err)
	}
	if !bytes.Equal(got, data) {
		t.Errorf("Destination content mismatch: got %q, want %q", got, data)
	}
	if blobIno, destIno := inodeOf(t, s.blobPath(hash)), inodeOf(t, dest); blobIno == destIno {
		t.Errorf("Expected an independent inode, but the destination shares the blob's inode %d", blobIno)
	}

	// A copy the caller asked for is not a fallback. Counting it would make
	// CopyFallbackEXDEV fire on every blob and bury the hosts where
	// cross-device copying is genuinely unintended.
	if got := s.Stats().CopyFallbacks(); got != 0 {
		t.Errorf("Expected a declared copy not to count as a fallback, got CopyFallbacks=%d", got)
	}
	if got := s.Stats().HardlinkHits; got != 1 {
		t.Errorf("Expected the copy to count as a cache hit, got HardlinkHits=%d", got)
	}
	assertNoLeftoverTempFiles(t, filepath.Dir(dest))
}

func TestHardlinkTo_WithoutHardlinksMissIsNotAnError(t *testing.T) {
	s := setupCopyOnlyStorage(t)
	forbidLink(t)

	dest := filepath.Join(t.TempDir(), "artifact.bin")
	hit, err := s.HardlinkTo(testHash("b2"), dest)
	if err != nil {
		t.Fatalf("HardlinkTo on an uncached blob returned an error: %v", err)
	}
	if hit {
		t.Errorf("HardlinkTo reported a hit for an uncached blob")
	}
	if _, err := os.Stat(dest); !errors.Is(err, os.ErrNotExist) {
		t.Errorf("Expected destination to be absent after a miss, stat returned %v", err)
	}
	if got := s.Stats().HardlinkMisses; got != 1 {
		t.Errorf("Expected the miss to be counted, got HardlinkMisses=%d", got)
	}
}

// TestHardlinkFrom_WithoutHardlinks is the ingest counterpart. Note that the
// source keeps its own inode and therefore its own mtime, so the timestamp
// side effect documented on HardlinkFrom does not apply here.
func TestHardlinkFrom_WithoutHardlinks(t *testing.T) {
	s := setupCopyOnlyStorage(t)
	forbidLink(t)

	hash := testHash("b3")
	data := []byte("ingested by copy")
	src := filepath.Join(t.TempDir(), "downloaded.bin")
	if err := os.WriteFile(src, data, 0640); err != nil {
		t.Fatalf("Failed to seed the source file: %v", err)
	}
	stale := time.Now().Add(-30 * 24 * time.Hour)
	if err := os.Chtimes(src, stale, stale); err != nil {
		t.Fatalf("Failed to age the source file: %v", err)
	}

	if err := s.HardlinkFrom(hash, src); err != nil {
		t.Fatalf("HardlinkFrom failed: %v", err)
	}
	if !s.Has(hash) {
		t.Fatalf("Expected the blob to be cached after ingestion")
	}

	got, err := s.Read(hash)
	if err != nil {
		t.Fatalf("Read failed: %v", err)
	}
	if !bytes.Equal(got, data) {
		t.Errorf("Ingested content mismatch: got %q, want %q", got, data)
	}
	if srcIno, blobIno := inodeOf(t, src), inodeOf(t, s.blobPath(hash)); srcIno == blobIno {
		t.Errorf("Expected an independent inode, but the blob shares the source's inode %d", srcIno)
	}

	blobInfo, err := os.Stat(s.blobPath(hash))
	if err != nil {
		t.Fatalf("Failed to stat the cached blob: %v", err)
	}
	if got, want := blobInfo.Mode().Perm(), os.FileMode(0640); got != want {
		t.Errorf("Copy did not preserve permissions: got %v, want %v", got, want)
	}
	// The copy is new, so it enters the cache with a current mtime and is not
	// immediately eligible for eviction, even though the source is ancient.
	if blobInfo.ModTime().Before(time.Now().Add(-time.Hour)) {
		t.Errorf("Expected the cached blob to carry a fresh mtime, got %v", blobInfo.ModTime())
	}
	// The source is a separate inode now, so its mtime must be untouched.
	srcInfo, err := os.Stat(src)
	if err != nil {
		t.Fatalf("Failed to stat the source file: %v", err)
	}
	if srcInfo.ModTime().After(stale.Add(time.Minute)) {
		t.Errorf("Expected the source mtime to be left alone, got %v", srcInfo.ModTime())
	}

	stats := s.Stats()
	if stats.Ingested != 1 {
		t.Errorf("Expected the ingest to be counted, got Ingested=%d", stats.Ingested)
	}
	if got := stats.CopyFallbacks(); got != 0 {
		t.Errorf("Expected a declared copy not to count as a fallback, got CopyFallbacks=%d", got)
	}
	assertNoLeftoverTempFiles(t, filepath.Dir(s.blobPath(hash)))
}

func TestHardlinkFrom_WithoutHardlinksDedupes(t *testing.T) {
	s := setupCopyOnlyStorage(t)
	forbidLink(t)

	hash := testHash("b4")
	cached := []byte("already here")
	if err := s.Write(hash, cached); err != nil {
		t.Fatalf("Write failed: %v", err)
	}
	originalIno := inodeOf(t, s.blobPath(hash))

	src := filepath.Join(t.TempDir(), "duplicate.bin")
	if err := os.WriteFile(src, cached, 0644); err != nil {
		t.Fatalf("Failed to seed the source file: %v", err)
	}

	if err := s.HardlinkFrom(hash, src); err != nil {
		t.Fatalf("HardlinkFrom on an already cached blob failed: %v", err)
	}
	if got := inodeOf(t, s.blobPath(hash)); got != originalIno {
		t.Errorf("Expected the cached blob to be left untouched (inode %d), but it was replaced (inode %d)", originalIno, got)
	}
	if got := s.Stats().IngestDeduped; got != 1 {
		t.Errorf("Expected the duplicate to be counted, got IngestDeduped=%d", got)
	}
}

