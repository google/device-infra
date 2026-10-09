package cache

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"syscall"
	"testing"
	"time"

	"github.com/bazelbuild/remote-apis-sdks/go/pkg/client"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/digest"
	"github.com/google/device-infra/src/devtools/rbe/common/storage"
)

const (
	execMode    = os.FileMode(0o755)
	nonExecMode = os.FileMode(0o644)
)

func hashOf(content string) string {
	sum := sha256.Sum256([]byte(content))
	return hex.EncodeToString(sum[:])
}

// wantLayoutDir is spelled out rather than taken from LayoutVersionDir so that
// these tests do not simply follow the production value wherever it goes. The
// directory name is part of the on-disk contract: changing it strands every
// deployed cache, and emptying it would silently merge the sharded layout back
// into the flat one it exists to stay clear of.
const wantLayoutDir = "v2"

// blobPath mirrors the sharded layout common/storage writes, so tests can make
// assertions about the cached inode itself rather than only about what comes
// back out of the cache.
func blobPath(cacheDir, hash string) string {
	return filepath.Join(cacheDir, wantLayoutDir, hash[:2], hash[2:4], hash)
}

func newTestCache(t *testing.T, cacheDir string, useHardlink bool) *LockFreeCache {
	t.Helper()
	c, err := NewLockFreeCache(cacheDir, storage.DefaultEvictorConfig(), useHardlink)
	if err != nil {
		t.Fatalf("NewLockFreeCache failed: %v", err)
	}
	return c
}

// stage writes content to a file under dir, standing in for a blob the
// downloader has just fetched, and returns its path and TreeOutput.
func stage(t *testing.T, dir, name, content string, executable bool) (string, *client.TreeOutput) {
	t.Helper()
	path := filepath.Join(dir, name)
	if err := os.MkdirAll(filepath.Dir(path), 0755); err != nil {
		t.Fatalf("Failed to create %s: %v", filepath.Dir(path), err)
	}
	// Deliberately not the canonical mode: the downloader's output permissions
	// are not something the cache should have to assume.
	if err := os.WriteFile(path, []byte(content), 0600); err != nil {
		t.Fatalf("Failed to write %s: %v", path, err)
	}
	return path, &client.TreeOutput{
		Digest:       digest.Digest{Hash: hashOf(content), Size: int64(len(content))},
		Path:         path,
		IsExecutable: executable,
	}
}

func inodeOf(t *testing.T, path string) uint64 {
	t.Helper()
	info, err := os.Stat(path)
	if err != nil {
		t.Fatalf("Failed to stat %s: %v", path, err)
	}
	return info.Sys().(*syscall.Stat_t).Ino
}

func permOf(t *testing.T, path string) os.FileMode {
	t.Helper()
	info, err := os.Stat(path)
	if err != nil {
		t.Fatalf("Failed to stat %s: %v", path, err)
	}
	return info.Mode().Perm()
}

func TestLockFreeCache_RoundTrip(t *testing.T) {
	dir := t.TempDir()
	cacheDir := filepath.Join(dir, "cache")
	ctx := context.Background()
	c := newTestCache(t, cacheDir, true)

	const content = "round trip"
	_, out := stage(t, dir, "work/file.txt", content, false)

	// Nothing is cached yet.
	cached, missed, err := c.Pull(ctx, []*client.TreeOutput{out})
	if err != nil {
		t.Fatalf("Pull failed: %v", err)
	}
	if len(cached) != 0 || len(missed) != 1 {
		t.Fatalf("Pull on an empty cache = %d cached, %d missed; want 0 cached, 1 missed", len(cached), len(missed))
	}

	if err := c.Push(ctx, map[digest.Digest]*client.TreeOutput{out.Digest: out}); err != nil {
		t.Fatalf("Push failed: %v", err)
	}

	// Pull the same blob to a fresh path.
	dest := filepath.Join(dir, "work2", "file.txt")
	second := &client.TreeOutput{Digest: out.Digest, Path: dest}

	cached, missed, err = c.Pull(ctx, []*client.TreeOutput{second})
	if err != nil {
		t.Fatalf("Pull after Push failed: %v", err)
	}
	if len(cached) != 1 || len(missed) != 0 {
		t.Fatalf("Pull after Push = %d cached, %d missed; want 1 cached, 0 missed", len(cached), len(missed))
	}

	got, err := os.ReadFile(dest)
	if err != nil {
		t.Fatalf("Failed to read %s: %v", dest, err)
	}
	if string(got) != content {
		t.Errorf("Pulled content = %q, want %q", got, content)
	}
}

// TestLockFreeCache_PushNormalizesMode pins the reason Pull is cheap in the
// common case: whatever permissions the downloader happened to leave on the
// file, the cached blob ends up carrying the mode its executability calls for.
func TestLockFreeCache_PushNormalizesMode(t *testing.T) {
	for _, tc := range []struct {
		name       string
		executable bool
		want       os.FileMode
	}{
		{"executable", true, execMode},
		{"regular", false, nonExecMode},
	} {
		t.Run(tc.name, func(t *testing.T) {
			dir := t.TempDir()
			cacheDir := filepath.Join(dir, "cache")
			c := newTestCache(t, cacheDir, true)

			_, out := stage(t, dir, "work/file", "content-"+tc.name, tc.executable)
			if err := c.Push(context.Background(), map[digest.Digest]*client.TreeOutput{out.Digest: out}); err != nil {
				t.Fatalf("Push failed: %v", err)
			}

			if got := permOf(t, blobPath(cacheDir, out.Digest.Hash)); got != tc.want {
				t.Errorf("Cached blob mode = %#o, want %#o", got, tc.want)
			}
		})
	}
}

// TestLockFreeCache_PullHonorsExecutabilityWithoutTouchingTheBlob covers the
// case a content-addressed cache cannot represent directly: one blob, two
// executabilities.
//
// In REAPI the executable bit belongs to the tree node, not to the content, so
// the same bytes can legitimately be executable in one directory and not in
// another. Permissions, meanwhile, belong to the inode. A hard link cannot
// satisfy both at once.
//
// LocalCache resolved this by chmod'ing the link it had just made, which
// rewrote the shared inode and changed every path already linked to it. This
// test asserts the opposite: the destination gets what it asked for, and the
// cached blob is left exactly as it was.
func TestLockFreeCache_PullHonorsExecutabilityWithoutTouchingTheBlob(t *testing.T) {
	dir := t.TempDir()
	cacheDir := filepath.Join(dir, "cache")
	ctx := context.Background()
	c := newTestCache(t, cacheDir, true)

	// Ingest as a regular file.
	const content = "shared by two trees"
	_, out := stage(t, dir, "work/lib.so", content, false)
	if err := c.Push(ctx, map[digest.Digest]*client.TreeOutput{out.Digest: out}); err != nil {
		t.Fatalf("Push failed: %v", err)
	}

	blob := blobPath(cacheDir, out.Digest.Hash)
	blobInode := inodeOf(t, blob)

	// A second tree wants the same bytes, executable this time.
	dest := filepath.Join(dir, "work2", "tool")
	if _, _, err := c.Pull(ctx, []*client.TreeOutput{
		{Digest: out.Digest, Path: dest, IsExecutable: true},
	}); err != nil {
		t.Fatalf("Pull failed: %v", err)
	}

	if got := permOf(t, dest); got != execMode {
		t.Errorf("Pulled file mode = %#o, want %#o: an executable tree node must be executable", got, execMode)
	}
	if got := permOf(t, blob); got != nonExecMode {
		t.Errorf("Cached blob mode = %#o, want %#o: pulling must not rewrite the shared inode", got, nonExecMode)
	}
	if inodeOf(t, dest) == blobInode {
		t.Error("Pulled file shares the cached inode; its mode cannot differ without corrupting the blob")
	}
	if got, err := os.ReadFile(dest); err != nil || string(got) != content {
		t.Errorf("Pulled content = %q (err %v), want %q", got, err, content)
	}
}

// TestLockFreeCache_PullSharesTheInodeWhenModesAgree is the counterpart to the
// test above, and the reason the extra copy there is acceptable: the ordinary
// case still costs no bytes.
func TestLockFreeCache_PullSharesTheInodeWhenModesAgree(t *testing.T) {
	dir := t.TempDir()
	cacheDir := filepath.Join(dir, "cache")
	ctx := context.Background()
	c := newTestCache(t, cacheDir, true)

	_, out := stage(t, dir, "work/file", "consistent", false)
	if err := c.Push(ctx, map[digest.Digest]*client.TreeOutput{out.Digest: out}); err != nil {
		t.Fatalf("Push failed: %v", err)
	}

	dest := filepath.Join(dir, "work2", "file")
	if _, _, err := c.Pull(ctx, []*client.TreeOutput{{Digest: out.Digest, Path: dest}}); err != nil {
		t.Fatalf("Pull failed: %v", err)
	}

	if inodeOf(t, dest) != inodeOf(t, blobPath(cacheDir, out.Digest.Hash)) {
		t.Error("Pulled file does not share the cached inode; the bytes were copied when a link would have done")
	}
}

// TestLockFreeCache_WithoutHardlinksCopies checks the cross-device deployment,
// where the cache reaches the process through a mount and link(2) can never
// succeed.
func TestLockFreeCache_WithoutHardlinksCopies(t *testing.T) {
	dir := t.TempDir()
	cacheDir := filepath.Join(dir, "cache")
	ctx := context.Background()
	c := newTestCache(t, cacheDir, false)

	const content = "copied, not linked"
	_, out := stage(t, dir, "work/file", content, false)
	if err := c.Push(ctx, map[digest.Digest]*client.TreeOutput{out.Digest: out}); err != nil {
		t.Fatalf("Push failed: %v", err)
	}

	dest := filepath.Join(dir, "work2", "file")
	cached, _, err := c.Pull(ctx, []*client.TreeOutput{{Digest: out.Digest, Path: dest}})
	if err != nil {
		t.Fatalf("Pull failed: %v", err)
	}
	if len(cached) != 1 {
		t.Fatalf("Pull = %d cached, want 1", len(cached))
	}
	if inodeOf(t, dest) == inodeOf(t, blobPath(cacheDir, out.Digest.Hash)) {
		t.Error("Pulled file shares the cached inode despite hard links being disabled")
	}
	if got, err := os.ReadFile(dest); err != nil || string(got) != content {
		t.Errorf("Pulled content = %q (err %v), want %q", got, err, content)
	}
}

// TestLockFreeCache_StoresUnderVersionedSubdir guards the property that makes
// the rollout flag safe to flip: the two layouts do not share a directory, so
// an existing luci cache is neither read nor disturbed.
func TestLockFreeCache_StoresUnderVersionedSubdir(t *testing.T) {
	dir := t.TempDir()
	cacheDir := filepath.Join(dir, "cache")
	ctx := context.Background()

	// A cache directory as LocalCache would have left it: blobs flat at the
	// root, next to a state.json index.
	if err := os.MkdirAll(cacheDir, 0755); err != nil {
		t.Fatalf("Failed to create %s: %v", cacheDir, err)
	}
	legacyBlob := filepath.Join(cacheDir, hashOf("legacy"))
	if err := os.WriteFile(legacyBlob, []byte("legacy"), 0640); err != nil {
		t.Fatalf("Failed to write %s: %v", legacyBlob, err)
	}
	legacyState := filepath.Join(cacheDir, "state.json")
	if err := os.WriteFile(legacyState, []byte("{}"), 0640); err != nil {
		t.Fatalf("Failed to write %s: %v", legacyState, err)
	}

	c := newTestCache(t, cacheDir, true)
	_, out := stage(t, dir, "work/file", "new layout", false)
	if err := c.Push(ctx, map[digest.Digest]*client.TreeOutput{out.Digest: out}); err != nil {
		t.Fatalf("Push failed: %v", err)
	}

	if _, err := os.Stat(blobPath(cacheDir, out.Digest.Hash)); err != nil {
		t.Errorf("Blob was not stored under %s/: %v", wantLayoutDir, err)
	}
	for _, path := range []string{legacyBlob, legacyState} {
		if _, err := os.Stat(path); err != nil {
			t.Errorf("Legacy cache file %s was disturbed: %v", filepath.Base(path), err)
		}
	}
}

// TestLockFreeCache_PullRemovesPulledFilesOnError keeps a failed Pull from
// leaving a half-populated tree behind, which the caller would otherwise be
// unable to distinguish from a complete one.
func TestLockFreeCache_PullRemovesPulledFilesOnError(t *testing.T) {
	dir := t.TempDir()
	cacheDir := filepath.Join(dir, "cache")
	ctx := context.Background()
	c := newTestCache(t, cacheDir, true)

	_, first := stage(t, dir, "work/first", "first", false)
	_, second := stage(t, dir, "work/second", "second", false)
	if err := c.Push(ctx, map[digest.Digest]*client.TreeOutput{
		first.Digest:  first,
		second.Digest: second,
	}); err != nil {
		t.Fatalf("Push failed: %v", err)
	}

	// A regular file standing where the second destination needs a directory,
	// so creating its parent fails with ENOTDIR.
	out := filepath.Join(dir, "out")
	if err := os.MkdirAll(out, 0755); err != nil {
		t.Fatalf("Failed to create %s: %v", out, err)
	}
	blocker := filepath.Join(out, "blocked")
	if err := os.WriteFile(blocker, nil, 0644); err != nil {
		t.Fatalf("Failed to write %s: %v", blocker, err)
	}

	good := filepath.Join(out, "good")
	_, _, err := c.Pull(ctx, []*client.TreeOutput{
		{Digest: first.Digest, Path: good},
		{Digest: second.Digest, Path: filepath.Join(blocker, "child")},
	})
	if err == nil {
		t.Fatal("Expected Pull to fail when a destination directory cannot be created, got nil")
	}
	if _, statErr := os.Stat(good); !os.IsNotExist(statErr) {
		t.Errorf("File pulled before the failure was left behind at %s (stat err %v)", good, statErr)
	}
}

// TestLockFreeCache_PullRemovesTheFileItFailedToChmod covers the one file the
// cleanup above can most easily miss: the one being worked on when the failure
// happened.
//
// Pull materializes a file and only then gives it the mode its tree node asks
// for, so there is a window in which a destination exists on disk but is not
// yet accounted for. A file left behind from that window is worse than a
// missing one, because it is complete and readable and carries the wrong
// permissions, so nothing downstream has a reason to suspect it.
func TestLockFreeCache_PullRemovesTheFileItFailedToChmod(t *testing.T) {
	dir := t.TempDir()
	cacheDir := filepath.Join(dir, "cache")
	ctx := context.Background()
	c := newTestCache(t, cacheDir, true)

	_, first := stage(t, dir, "work/first", "first", false)
	_, second := stage(t, dir, "work/second", "second", false)
	if err := c.Push(ctx, map[digest.Digest]*client.TreeOutput{
		first.Digest:  first,
		second.Digest: second,
	}); err != nil {
		t.Fatalf("Push failed: %v", err)
	}

	out := filepath.Join(dir, "out")
	if err := os.MkdirAll(out, 0755); err != nil {
		t.Fatalf("Failed to create %s: %v", out, err)
	}

	// Both blobs were ingested non-executable, so asking for one of them
	// executable forces the copy-onto-its-own-inode path. That copy goes
	// through a temporary sibling named after the destination, which is how
	// the failure is arranged: a basename this long is a legal filename, but
	// the decorated temporary name derived from it exceeds NAME_MAX and
	// cannot be created. The link itself is already on disk by then.
	good := filepath.Join(out, "good")
	doomed := filepath.Join(out, strings.Repeat("n", 250))
	_, _, err := c.Pull(ctx, []*client.TreeOutput{
		{Digest: first.Digest, Path: good},
		{Digest: second.Digest, Path: doomed, IsExecutable: true},
	})
	if err == nil {
		t.Fatal("Expected Pull to fail when a pulled file cannot be given its mode, got nil")
	}
	if _, statErr := os.Stat(doomed); !os.IsNotExist(statErr) {
		t.Errorf("File that failed mid-chmod was left behind with the wrong mode (stat err %v)", statErr)
	}
	if _, statErr := os.Stat(good); !os.IsNotExist(statErr) {
		t.Errorf("File pulled before the failure was left behind at %s (stat err %v)", good, statErr)
	}
}

// TestLockFreeCache_PullLeavesTheTreeAloneWhenTheFirstItemFails checks the
// boundary of the cleanup: when nothing has been materialized yet, the failure
// must not reach outside the call.
//
// The cleanup runs on a list that is still empty here, and the loop has to stop
// rather than carry on with the remaining items. Getting that wrong would be
// invisible in the successful case and would show up only as a destination that
// exists after a call that returned an error.
func TestLockFreeCache_PullLeavesTheTreeAloneWhenTheFirstItemFails(t *testing.T) {
	dir := t.TempDir()
	cacheDir := filepath.Join(dir, "cache")
	ctx := context.Background()
	c := newTestCache(t, cacheDir, true)

	_, blob := stage(t, dir, "work/blob", "cached", false)
	if err := c.Push(ctx, map[digest.Digest]*client.TreeOutput{blob.Digest: blob}); err != nil {
		t.Fatalf("Push failed: %v", err)
	}

	out := filepath.Join(dir, "out")
	if err := os.MkdirAll(out, 0755); err != nil {
		t.Fatalf("Failed to create %s: %v", out, err)
	}
	// A regular file where the first destination needs a directory, so its
	// parent cannot be created and the very first item fails.
	blocker := filepath.Join(out, "blocked")
	if err := os.WriteFile(blocker, nil, 0644); err != nil {
		t.Fatalf("Failed to write %s: %v", blocker, err)
	}

	untouched := filepath.Join(out, "untouched")
	if _, _, err := c.Pull(ctx, []*client.TreeOutput{
		{Digest: blob.Digest, Path: filepath.Join(blocker, "child")},
		{Digest: blob.Digest, Path: untouched},
	}); err == nil {
		t.Fatal("Expected Pull to fail on the first item, got nil")
	}

	if _, err := os.Stat(untouched); !os.IsNotExist(err) {
		t.Errorf("Pull kept going after the first item failed and materialized %s (stat err %v)", untouched, err)
	}
}

// TestLockFreeCache_WithoutHardlinksHonorsExecutability covers the combination
// the other tests each miss half of: the cross-device deployment, where nothing
// is ever linked, and a tree node whose executability disagrees with the
// ingested blob.
//
// It is worth pinning separately because the reasoning that makes the copy
// necessary does not apply here. With links disabled the destination is already
// an inode of its own, so nothing is shared and nothing could be corrupted by a
// plain chmod. The mode still has to come out right, which is what this
// asserts; that it is currently arrived at by copying the file a second time is
// a cost, not a correctness problem.
func TestLockFreeCache_WithoutHardlinksHonorsExecutability(t *testing.T) {
	dir := t.TempDir()
	cacheDir := filepath.Join(dir, "cache")
	ctx := context.Background()
	c := newTestCache(t, cacheDir, false)

	const content = "same bytes, two executabilities, no links"
	_, out := stage(t, dir, "work/lib.so", content, false)
	if err := c.Push(ctx, map[digest.Digest]*client.TreeOutput{out.Digest: out}); err != nil {
		t.Fatalf("Push failed: %v", err)
	}

	dest := filepath.Join(dir, "work2", "tool")
	if _, _, err := c.Pull(ctx, []*client.TreeOutput{
		{Digest: out.Digest, Path: dest, IsExecutable: true},
	}); err != nil {
		t.Fatalf("Pull failed: %v", err)
	}

	if got := permOf(t, dest); got != execMode {
		t.Errorf("Pulled file mode = %#o, want %#o: an executable tree node must be executable", got, execMode)
	}
	if got := permOf(t, blobPath(cacheDir, out.Digest.Hash)); got != nonExecMode {
		t.Errorf("Cached blob mode = %#o, want %#o: pulling must not rewrite the blob", got, nonExecMode)
	}
	if got, err := os.ReadFile(dest); err != nil || string(got) != content {
		t.Errorf("Pulled content = %q (err %v), want %q", got, err, content)
	}
}

// TestLockFreeCache_WithoutHardlinksChmodsInPlace is the same property stated
// as a cost rather than an outcome.
//
// Re-materializing a file is how a mode gets fixed without disturbing the other
// paths that share the blob's inode. When nothing shares it, that reasoning does
// not apply and the copy is pure overhead -- and it is overhead in exactly the
// deployment that already pays for a copy to materialize the file at all, so a
// mismatched executable bit would otherwise cost two full copies of every such
// file.
//
// The inode is the evidence: a chmod preserves it, a re-materialization renames
// a new file over it.
func TestLockFreeCache_WithoutHardlinksChmodsInPlace(t *testing.T) {
	dir := t.TempDir()
	c := newTestCache(t, filepath.Join(dir, "cache"), false)

	// stage writes 0644 and an executable tree node wants 0750, so this is a
	// mismatch that has to be resolved one way or the other.
	path, item := stage(t, dir, "work/tool", "already an inode of its own", true)
	before := inodeOf(t, path)

	info, err := os.Lstat(path)
	if err != nil {
		t.Fatalf("Failed to stat %s: %v", path, err)
	}
	if err := c.applyFileMode(item, info); err != nil {
		t.Fatalf("applyFileMode failed: %v", err)
	}

	if got := permOf(t, path); got != execMode {
		t.Errorf("File mode = %#o, want %#o", got, execMode)
	}
	if got := inodeOf(t, path); got != before {
		t.Errorf("File was re-materialized (inode %d -> %d) to change a mode nobody else could see; a chmod would have done", before, got)
	}
}

// TestLockFreeCache_PushIsIdempotent covers the ordinary case of two
// casdownloaders racing to cache the same blob: whoever loses finds the entry
// already published and carries on.
func TestLockFreeCache_PushIsIdempotent(t *testing.T) {
	dir := t.TempDir()
	cacheDir := filepath.Join(dir, "cache")
	ctx := context.Background()
	c := newTestCache(t, cacheDir, true)

	_, out := stage(t, dir, "work/file", "pushed twice", false)
	batch := map[digest.Digest]*client.TreeOutput{out.Digest: out}

	if err := c.Push(ctx, batch); err != nil {
		t.Fatalf("First Push failed: %v", err)
	}
	if err := c.Push(ctx, batch); err != nil {
		t.Errorf("Second Push failed: %v", err)
	}
}

// TestLockFreeCache_EnsureHeadroomOnHealthyDisk checks the wiring, not the
// eviction policy: a modest request against a temp directory should be
// satisfied by the initial statfs without any eviction at all.
func TestLockFreeCache_EnsureHeadroomOnHealthyDisk(t *testing.T) {
	dir := t.TempDir()
	c := newTestCache(t, filepath.Join(dir, "cache"), true)

	if err := c.EnsureHeadroom(context.Background(), 1024); err != nil {
		t.Errorf("EnsureHeadroom(1KiB) failed: %v", err)
	}
}

// TestLockFreeCache_IsDiscoverableAsHeadroomReserver pins the mechanism the
// downloader uses to find pre-flight eviction.
//
// The conformance itself is asserted at compile time in lockfreecache.go. What
// that cannot catch is the call site going looking for the wrong thing: a type
// assertion that does not match is not an error, it is a skipped branch, so a
// mismatch here would disable eviction silently rather than failing the build.
func TestLockFreeCache_IsDiscoverableAsHeadroomReserver(t *testing.T) {
	var c Cache = &LockFreeCache{}

	if _, ok := c.(HeadroomReserver); !ok {
		t.Error("A LockFreeCache held as a Cache is not discoverable as a HeadroomReserver; the downloader would skip pre-flight eviction without any error")
	}
}

// TestLockFreeCache_CloseReportsAndSucceeds covers the one method that runs on
// every single invocation.
//
// Close has no index to flush and nothing to fail at, which is exactly why it
// is worth executing once: it reaches into the store for statistics on the way
// out, and a nil dereference there would abort every run that used the cache,
// after all the useful work was already done.
func TestLockFreeCache_CloseReportsAndSucceeds(t *testing.T) {
	dir := t.TempDir()
	ctx := context.Background()
	c := newTestCache(t, filepath.Join(dir, "cache"), true)

	_, out := stage(t, dir, "work/file", "something to report", false)
	if err := c.Push(ctx, map[digest.Digest]*client.TreeOutput{out.Digest: out}); err != nil {
		t.Fatalf("Push failed: %v", err)
	}

	if err := c.Close(); err != nil {
		t.Errorf("Close failed: %v", err)
	}
}

// TestLockFreeCache_PullRefreshesStaleBlobs pins the LRU half of the cache.
//
// The evictor orders blobs by mtime, so a hit has to move the blob's mtime
// forward or a blob reused by every run is evicted as if it had not been used
// since it was first downloaded. -cache-lazy-touch-interval exists to throttle
// that refresh, which this test also checks, because the flag was once plumbed
// all the way into the evictor config without Pull ever consulting it.
func TestLockFreeCache_PullRefreshesStaleBlobs(t *testing.T) {
	for _, tc := range []struct {
		name          string
		interval      time.Duration // 0 leaves the shared default (4h).
		age           time.Duration
		useHardlink   bool
		wantRefreshed bool
	}{
		{name: "older_than_default_interval", age: 5 * time.Hour, useHardlink: true, wantRefreshed: true},
		{name: "within_default_interval", age: 1 * time.Hour, useHardlink: true, wantRefreshed: false},
		{name: "older_than_configured_interval", interval: 30 * time.Minute, age: 1 * time.Hour, useHardlink: true, wantRefreshed: true},
		{name: "without_hardlinks", age: 5 * time.Hour, useHardlink: false, wantRefreshed: true},
	} {
		t.Run(tc.name, func(t *testing.T) {
			dir := t.TempDir()
			cacheDir := filepath.Join(dir, "cache")
			ctx := context.Background()

			cfg := storage.DefaultEvictorConfig()
			if tc.interval > 0 {
				cfg.LazyTouchInterval = tc.interval
			}
			c, err := NewLockFreeCache(cacheDir, cfg, tc.useHardlink)
			if err != nil {
				t.Fatalf("NewLockFreeCache failed: %v", err)
			}

			_, out := stage(t, dir, "work/file", "reused-"+tc.name, false)
			if err := c.Push(ctx, map[digest.Digest]*client.TreeOutput{out.Digest: out}); err != nil {
				t.Fatalf("Push failed: %v", err)
			}
			blob := blobPath(cacheDir, out.Digest.Hash)
			old := time.Now().Add(-tc.age).Truncate(time.Second)
			if err := os.Chtimes(blob, old, old); err != nil {
				t.Fatalf("Chtimes(%s) failed: %v", blob, err)
			}

			before := time.Now()
			dest := filepath.Join(dir, "work2", "file")
			cached, _, err := c.Pull(ctx, []*client.TreeOutput{{Digest: out.Digest, Path: dest}})
			if err != nil {
				t.Fatalf("Pull failed: %v", err)
			}
			if len(cached) != 1 {
				t.Fatalf("Pull = %d cached, want 1", len(cached))
			}

			info, err := os.Stat(blob)
			if err != nil {
				t.Fatalf("Failed to stat %s: %v", blob, err)
			}
			got := info.ModTime()
			if tc.wantRefreshed {
				// Allow for filesystems that store coarser timestamps than
				// time.Now reports.
				if got.Before(before.Add(-time.Second)) {
					t.Errorf("Cached blob mtime = %v after a hit, want at or after %v: the hit was not credited", got, before)
				}
			} else if !got.Equal(old) {
				t.Errorf("Cached blob mtime = %v after a hit, want it left at %v: a hit within the interval must not write the inode", got, old)
			}
		})
	}
}

// cacheModes are the two deployments the size check has to work in: hard
// links, where a pulled output is the cached blob, and WithoutHardlinks, where
// it is always a copy.
var cacheModes = []struct {
	name        string
	useHardlink bool
}{
	{name: "hardlinks", useHardlink: true},
	{name: "without_hardlinks", useHardlink: false},
}

// push caches outs, failing the test if Push does.
func push(t *testing.T, c *LockFreeCache, outs ...*client.TreeOutput) {
	t.Helper()
	batch := make(map[digest.Digest]*client.TreeOutput, len(outs))
	for _, out := range outs {
		batch[out.Digest] = out
	}
	if err := c.Push(context.Background(), batch); err != nil {
		t.Fatalf("Push failed: %v", err)
	}
}

// assertAbsent fails the test if anything exists at path.
func assertAbsent(t *testing.T, path, what string) {
	t.Helper()
	if _, err := os.Lstat(path); !os.IsNotExist(err) {
		t.Errorf("%s is still at %s (stat err %v), want it removed", what, path, err)
	}
}

// pullOne pulls item and returns whether it was a hit, failing the test if
// Pull returns an error or does not account for the item exactly once.
func pullOne(t *testing.T, c *LockFreeCache, item *client.TreeOutput) bool {
	t.Helper()
	cached, missed, err := c.Pull(context.Background(), []*client.TreeOutput{item})
	if err != nil {
		t.Fatalf("Pull(%s) failed: %v", item.Path, err)
	}
	if len(cached)+len(missed) != 1 {
		t.Fatalf("Pull(%s) = %d cached, %d missed; want the item reported exactly once", item.Path, len(cached), len(missed))
	}
	return len(cached) == 1
}

// TestLockFreeCache_PullDiscardsBlobsOfTheWrongSize covers what the size check
// is for: a cached blob that was changed after it was cached.
//
// With hard links, a pulled output is the cached blob, so a caller that
// appends to or truncates its output changes the blob for every later Pull.
// Without hard links, a pulled output is a copy and writing it cannot reach
// the cache, so the test changes the blob itself, as another writer or a crash
// would. In both cases the next Pull must not serve the blob. It must remove
// the blob, report a miss so the file is downloaded again, and leave the cache
// able to take a good copy from the next Push.
func TestLockFreeCache_PullDiscardsBlobsOfTheWrongSize(t *testing.T) {
	for _, edit := range []struct {
		name   string
		modify func(t *testing.T, path string)
	}{
		{
			name: "append",
			modify: func(t *testing.T, path string) {
				f, err := os.OpenFile(path, os.O_WRONLY|os.O_APPEND, 0)
				if err != nil {
					t.Fatalf("Failed to open %s for appending: %v", path, err)
				}
				defer f.Close()
				if _, err := f.WriteString(" plus bytes written in place"); err != nil {
					t.Fatalf("Failed to append to %s: %v", path, err)
				}
			},
		},
		{
			name: "truncate",
			modify: func(t *testing.T, path string) {
				if err := os.Truncate(path, 3); err != nil {
					t.Fatalf("Failed to truncate %s: %v", path, err)
				}
			},
		},
	} {
		for _, mode := range cacheModes {
			t.Run(edit.name+"_"+mode.name, func(t *testing.T) {
				dir := t.TempDir()
				cacheDir := filepath.Join(dir, "cache")
				c := newTestCache(t, cacheDir, mode.useHardlink)

				const content = "bytes that match their digest"
				_, out := stage(t, dir, "work/vbmeta.img", content, false)
				push(t, c, out)
				blob := blobPath(cacheDir, out.Digest.Hash)

				first := &client.TreeOutput{Digest: out.Digest, Path: filepath.Join(dir, "run1", "vbmeta.img")}
				if !pullOne(t, c, first) {
					t.Fatal("First Pull missed a blob that was just pushed")
				}

				target := blob
				if mode.useHardlink {
					if inodeOf(t, first.Path) != inodeOf(t, blob) {
						t.Fatal("First Pull did not link the blob, so writing its output cannot reach the cache")
					}
					target = first.Path
				}
				edit.modify(t, target)

				second := &client.TreeOutput{Digest: out.Digest, Path: filepath.Join(dir, "run2", "vbmeta.img")}
				if pullOne(t, c, second) {
					t.Fatal("Pull served a cached blob whose size no longer matches its digest, want a miss")
				}
				assertAbsent(t, second.Path, "The wrong-size file pulled from cache")
				assertAbsent(t, blob, "The wrong-size blob")
				if got := c.CorruptBlobsQuarantined(); got != 1 {
					t.Errorf("CorruptBlobsQuarantined() = %d, want 1", got)
				}

				// The caller downloads the file again and pushes it, as the
				// downloader does with every miss. The cache must take it.
				_, refetched := stage(t, dir, "run2/vbmeta.img", content, false)
				push(t, c, refetched)
				third := &client.TreeOutput{Digest: out.Digest, Path: filepath.Join(dir, "run3", "vbmeta.img")}
				if !pullOne(t, c, third) {
					t.Fatal("Pull missed after the good copy was pushed; the cache did not take it")
				}
				if got, err := os.ReadFile(third.Path); err != nil || string(got) != content {
					t.Errorf("Pulled content = %q (err %v), want %q", got, err, content)
				}
				if got := c.CorruptBlobsQuarantined(); got != 1 {
					t.Errorf("CorruptBlobsQuarantined() = %d after a good hit, want it to stay 1", got)
				}
			})
		}
	}
}

// TestLockFreeCache_PullHealsAPrePoisonedBlob covers caches that are already
// bad. Before -copy-filters, Cuttlefish's EnforceVbMetaSize padded 4 KiB vbmeta
// images to 64 KiB in place, through the hard link, so those caches hold a
// 64 KiB file under the 4 KiB image's hash. Pull must find and remove it.
//
// The tree names the image twice and also holds a good blob. The good blob must
// still be served. The second reference to the image must be a plain miss, not
// a second corrupt blob.
func TestLockFreeCache_PullHealsAPrePoisonedBlob(t *testing.T) {
	for _, mode := range cacheModes {
		t.Run(mode.name, func(t *testing.T) {
			dir := t.TempDir()
			cacheDir := filepath.Join(dir, "cache")
			c := newTestCache(t, cacheDir, mode.useHardlink)

			_, good := stage(t, dir, "work/boot.img", "a blob nobody modified", false)
			push(t, c, good)

			image := "AVB0" + strings.Repeat("v", 4<<10-4)
			imageDigest := digest.Digest{Hash: hashOf(image), Size: int64(len(image))}
			poisoned := blobPath(cacheDir, imageDigest.Hash)
			if err := os.MkdirAll(filepath.Dir(poisoned), 0755); err != nil {
				t.Fatalf("Failed to create %s: %v", filepath.Dir(poisoned), err)
			}
			padded := image + strings.Repeat("\x00", 64<<10-len(image))
			if err := os.WriteFile(poisoned, []byte(padded), nonExecMode); err != nil {
				t.Fatalf("Failed to seed %s: %v", poisoned, err)
			}

			out := filepath.Join(dir, "out")
			goodItem := &client.TreeOutput{Digest: good.Digest, Path: filepath.Join(out, "boot.img")}
			vbmeta := &client.TreeOutput{Digest: imageDigest, Path: filepath.Join(out, "vbmeta.img")}
			vbmetaCopy := &client.TreeOutput{Digest: imageDigest, Path: filepath.Join(out, "vbmeta_system.img")}
			cached, missed, err := c.Pull(context.Background(), []*client.TreeOutput{goodItem, vbmeta, vbmetaCopy})
			if err != nil {
				t.Fatalf("Pull failed: %v", err)
			}
			if len(cached) != 1 || cached[0] != goodItem {
				t.Errorf("Pull cached %d items, want only %s", len(cached), goodItem.Path)
			}
			if len(missed) != 2 || missed[0] != vbmeta || missed[1] != vbmetaCopy {
				t.Errorf("Pull missed %d items, want both references to the padded image", len(missed))
			}
			assertAbsent(t, vbmeta.Path, "The padded image pulled from cache")
			assertAbsent(t, vbmetaCopy.Path, "The second reference to the padded image")
			assertAbsent(t, poisoned, "The padded blob")
			if got, err := os.ReadFile(goodItem.Path); err != nil || string(got) != "a blob nobody modified" {
				t.Errorf("Good blob content = %q (err %v), want it served unchanged", got, err)
			}
			if got := c.CorruptBlobsQuarantined(); got != 1 {
				t.Errorf("CorruptBlobsQuarantined() = %d, want 1", got)
			}

			// After the download, Push caches the real 4 KiB image.
			_, fetched := stage(t, dir, "out/vbmeta.img", image, false)
			push(t, c, fetched)
			healed := &client.TreeOutput{Digest: imageDigest, Path: filepath.Join(dir, "next", "vbmeta.img")}
			if !pullOne(t, c, healed) {
				t.Fatal("Pull missed after the real image was pushed")
			}
			if got, err := os.ReadFile(healed.Path); err != nil || string(got) != image {
				t.Errorf("Pulled image is %d bytes (err %v), want the %d-byte original", len(got), err, len(image))
			}
		})
	}
}

// TestLockFreeCache_PullRemovesTheBlobEvenIfItCannotRemoveTheFile covers a
// corrupt hit whose destination cannot be removed. Pull must fail, because it
// cannot take back the bad file it handed out, but it must still remove the
// blob and count it. The blob is shared by every consumer on the host, so
// leaving it would keep serving the bad bytes to all of them.
func TestLockFreeCache_PullRemovesTheBlobEvenIfItCannotRemoveTheFile(t *testing.T) {
	for _, mode := range cacheModes {
		t.Run(mode.name, func(t *testing.T) {
			dir := t.TempDir()
			cacheDir := filepath.Join(dir, "cache")
			c := newTestCache(t, cacheDir, mode.useHardlink)

			const content = "bytes that match their digest"
			_, out := stage(t, dir, "work/vbmeta.img", content, false)
			push(t, c, out)
			blob := blobPath(cacheDir, out.Digest.Hash)
			if err := os.Truncate(blob, 3); err != nil {
				t.Fatalf("Failed to truncate %s: %v", blob, err)
			}

			dest := filepath.Join(dir, "out", "vbmeta.img")
			orig := removeFile
			t.Cleanup(func() { removeFile = orig })
			removeFile = func(path string) error {
				if path == dest {
					return &os.PathError{Op: "remove", Path: path, Err: syscall.EACCES}
				}
				return orig(path)
			}

			_, _, err := c.Pull(context.Background(), []*client.TreeOutput{{Digest: out.Digest, Path: dest}})
			if err == nil {
				t.Fatal("Pull succeeded although it could not remove the wrong-size file it pulled, want an error")
			}
			assertAbsent(t, blob, "The wrong-size blob")
			if got := c.CorruptBlobsQuarantined(); got != 1 {
				t.Errorf("CorruptBlobsQuarantined() = %d, want 1", got)
			}
		})
	}
}

// TestLockFreeCache_IsDiscoverableAsCorruptBlobReporter pins how the
// downloader finds the corrupt-blob count for stats.json. As with
// HeadroomReserver, a type assertion that does not match skips the code
// instead of failing, so a mismatch would silently report zero.
func TestLockFreeCache_IsDiscoverableAsCorruptBlobReporter(t *testing.T) {
	var c Cache = &LockFreeCache{}

	if _, ok := c.(CorruptBlobReporter); !ok {
		t.Error("A LockFreeCache held as a Cache is not discoverable as a CorruptBlobReporter; stats.json would report no corrupt blobs")
	}
}

// TestLockFreeCache_DiscardCorruptHitWhateverBecameOfTheBlob covers the blob
// states that Pull alone cannot set up, because they need another process to
// act between the hit and the size check: the blob has already been replaced
// with a good copy, or it cannot be removed. In both cases the wrong-size file
// is still taken back and the hit still counted, and neither is an error: the
// caller downloads the file again whatever became of the blob.
func TestLockFreeCache_DiscardCorruptHitWhateverBecameOfTheBlob(t *testing.T) {
	const content = "bytes that match their digest"
	for _, tc := range []struct {
		name string
		// seed puts the blob in its state and returns a check of that state
		// afterwards.
		seed func(t *testing.T, c *LockFreeCache, dir, blob string) func(t *testing.T)
	}{
		{
			name: "already_replaced",
			seed: func(t *testing.T, c *LockFreeCache, dir, blob string) func(t *testing.T) {
				_, good := stage(t, dir, "other/vbmeta.img", content, false)
				push(t, c, good)
				return func(t *testing.T) {
					if got, err := os.ReadFile(blob); err != nil || string(got) != content {
						t.Errorf("Replacement blob = %q (err %v), want %q left in place", got, err, content)
					}
				}
			},
		},
		{
			name: "unremovable",
			seed: func(t *testing.T, c *LockFreeCache, dir, blob string) func(t *testing.T) {
				// A non-empty directory is the portable way to make the
				// unlink fail.
				if err := os.MkdirAll(filepath.Join(blob, "occupant"), 0755); err != nil {
					t.Fatalf("Failed to seed the blocking directory: %v", err)
				}
				return func(t *testing.T) {
					if _, err := os.Stat(filepath.Join(blob, "occupant")); err != nil {
						t.Errorf("Unremovable blob changed: %v", err)
					}
				}
			},
		},
	} {
		t.Run(tc.name, func(t *testing.T) {
			dir := t.TempDir()
			cacheDir := filepath.Join(dir, "cache")
			c := newTestCache(t, cacheDir, true)

			item := &client.TreeOutput{
				Digest: digest.Digest{Hash: hashOf(content), Size: int64(len(content))},
				Path:   filepath.Join(dir, "out", "vbmeta.img"),
			}
			check := tc.seed(t, c, dir, blobPath(cacheDir, item.Digest.Hash))

			// The file this run was served, on an inode of its own.
			if err := os.MkdirAll(filepath.Dir(item.Path), 0755); err != nil {
				t.Fatalf("Failed to create %s: %v", filepath.Dir(item.Path), err)
			}
			if err := os.WriteFile(item.Path, []byte(content+" plus bytes written in place"), 0644); err != nil {
				t.Fatalf("Failed to write %s: %v", item.Path, err)
			}
			info, err := os.Lstat(item.Path)
			if err != nil {
				t.Fatalf("Failed to stat %s: %v", item.Path, err)
			}

			if err := c.discardCorruptHit(item, info); err != nil {
				t.Errorf("discardCorruptHit failed: %v", err)
			}
			assertAbsent(t, item.Path, "The wrong-size file pulled from cache")
			check(t)
			if got := c.CorruptBlobsQuarantined(); got != 1 {
				t.Errorf("CorruptBlobsQuarantined() = %d, want 1", got)
			}
		})
	}
}

// TestLockFreeCache_PullCleansUpWhenItCannotStatAPulledFile covers a stat
// that fails on a file Pull has just materialized. Pull cannot check the
// file's size or mode, so it must fail, and it must take back that file and
// every file it pulled before it. The blob itself is not suspect and stays.
func TestLockFreeCache_PullCleansUpWhenItCannotStatAPulledFile(t *testing.T) {
	dir := t.TempDir()
	cacheDir := filepath.Join(dir, "cache")
	c := newTestCache(t, cacheDir, true)

	_, first := stage(t, dir, "work/first", "first", false)
	_, second := stage(t, dir, "work/second", "second", false)
	push(t, c, first, second)

	good := filepath.Join(dir, "out", "good")
	doomed := filepath.Join(dir, "out", "doomed")
	orig := statPulled
	t.Cleanup(func() { statPulled = orig })
	statPulled = func(path string) (os.FileInfo, error) {
		if path == doomed {
			return nil, &os.PathError{Op: "lstat", Path: path, Err: syscall.EIO}
		}
		return orig(path)
	}

	_, _, err := c.Pull(context.Background(), []*client.TreeOutput{
		{Digest: first.Digest, Path: good},
		{Digest: second.Digest, Path: doomed},
	})
	if err == nil {
		t.Fatal("Pull succeeded although it could not stat a file it pulled, want an error")
	}
	assertAbsent(t, doomed, "The file Pull could not stat")
	assertAbsent(t, good, "The file pulled before the failure")
	if _, err := os.Stat(blobPath(cacheDir, second.Digest.Hash)); err != nil {
		t.Errorf("Blob of the file Pull could not stat was removed: %v", err)
	}
	if got := c.CorruptBlobsQuarantined(); got != 0 {
		t.Errorf("CorruptBlobsQuarantined() = %d, want 0: nothing was found to be the wrong size", got)
	}
}

// TestLockFreeCache_ModeCopiesAreCountedByCause checks that every copy
// applyFileMode makes is counted once, under the cause that calls for its fix,
// and that a hit that shares the inode is not counted at all.
//
// The blob is ingested as a regular file. Drift is simulated the way it
// happens in the field: by chmod'ing a pulled file, which on the hard link path
// is the cached blob.
func TestLockFreeCache_ModeCopiesAreCountedByCause(t *testing.T) {
	const content = "mode copies"
	size := int64(len(content))
	for _, tc := range []struct {
		name string
		// drift, if not zero, is the mode a client sets on a pulled file
		// before the pull under test.
		drift      os.FileMode
		executable bool
		want       ModeCopyStats
	}{
		{name: "modes agree", executable: false, want: ModeCopyStats{}},
		{name: "other executability", executable: true,
			want: ModeCopyStats{ExecMismatchCopies: 1, ExecMismatchBytes: size}},
		{name: "drifted, same executability", drift: 0o600, executable: false,
			want: ModeCopyStats{PermDriftCopies: 1, PermDriftBytes: size}},
		// 0777 also flips the executable bit, but the chmod is what has to
		// be fixed, so it counts as drift.
		{name: "drifted to 0777", drift: 0o777, executable: false,
			want: ModeCopyStats{PermDriftCopies: 1, PermDriftBytes: size}},
		{name: "drifted to 0777, wanted executable", drift: 0o777, executable: true,
			want: ModeCopyStats{PermDriftCopies: 1, PermDriftBytes: size}},
		// A drifted mode that happens to be the canonical mode of the other
		// executability cannot be told apart from content the trees disagree
		// about, and is counted as such.
		{name: "drifted to the other canonical mode", drift: execMode, executable: false,
			want: ModeCopyStats{ExecMismatchCopies: 1, ExecMismatchBytes: size}},
	} {
		t.Run(tc.name, func(t *testing.T) {
			dir := t.TempDir()
			c := newTestCache(t, filepath.Join(dir, "cache"), true)
			_, out := stage(t, dir, "work/file", content, false)
			push(t, c, out)

			if tc.drift != 0 {
				if err := os.Chmod(out.Path, tc.drift); err != nil {
					t.Fatalf("Failed to chmod %s: %v", out.Path, err)
				}
			}

			item := &client.TreeOutput{Digest: out.Digest, Path: filepath.Join(dir, "out", "file"), IsExecutable: tc.executable}
			if !pullOne(t, c, item) {
				t.Fatal("Pull missed a blob that was just pushed")
			}
			if got := c.ModeCopies(); got != tc.want {
				t.Errorf("ModeCopies() = %+v, want %+v", got, tc.want)
			}
		})
	}
}

// TestLockFreeCache_ModeCopiesAccumulate checks that the counters add up
// across hits rather than recording only the latest, since a drifted blob costs
// a copy on every hit until it is evicted.
func TestLockFreeCache_ModeCopiesAccumulate(t *testing.T) {
	dir := t.TempDir()
	c := newTestCache(t, filepath.Join(dir, "cache"), true)
	_, out := stage(t, dir, "work/file", "accumulate", false)
	push(t, c, out)

	for i := range 3 {
		pullOne(t, c, &client.TreeOutput{Digest: out.Digest, Path: filepath.Join(dir, "out", strconv.Itoa(i)), IsExecutable: true})
	}
	want := ModeCopyStats{ExecMismatchCopies: 3, ExecMismatchBytes: 3 * out.Digest.Size}
	if got := c.ModeCopies(); got != want {
		t.Errorf("ModeCopies() = %+v, want %+v", got, want)
	}
}

// TestLockFreeCache_WithoutHardlinksCountsNoModeCopies checks that a store
// that cannot hard link reports no copies: it fixes a mismatch with a chmod of
// a file it already owns, so there is no extra copy to count.
func TestLockFreeCache_WithoutHardlinksCountsNoModeCopies(t *testing.T) {
	dir := t.TempDir()
	c := newTestCache(t, filepath.Join(dir, "cache"), false)
	_, out := stage(t, dir, "work/file", "no copies", false)
	push(t, c, out)

	pullOne(t, c, &client.TreeOutput{Digest: out.Digest, Path: filepath.Join(dir, "out", "file"), IsExecutable: true})
	if got := c.ModeCopies(); got != (ModeCopyStats{}) {
		t.Errorf("ModeCopies() = %+v, want none", got)
	}
}
