package cache

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"os"
	"path/filepath"
	"strings"
	"syscall"
	"testing"

	"github.com/bazelbuild/remote-apis-sdks/go/pkg/client"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/digest"
	"github.com/google/device-infra/src/devtools/rbe/common/storage"
)

const (
	execMode    = os.FileMode(0o750)
	nonExecMode = os.FileMode(0o640)
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
	if err := os.WriteFile(path, []byte(content), 0644); err != nil {
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

	if err := c.applyFileMode(item); err != nil {
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
