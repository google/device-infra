package cache

import (
	"context"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/bazelbuild/remote-apis-sdks/go/pkg/client"
)

// unopenableOptions describes a cache whose directory cannot be created,
// standing in for every way setup fails in practice: something about this host,
// nothing about the artifacts being fetched.
func unopenableOptions(t *testing.T) Options {
	t.Helper()
	blocker := filepath.Join(t.TempDir(), "not-a-directory")
	if err := os.WriteFile(blocker, []byte("in the way"), 0644); err != nil {
		t.Fatalf("Failed to stage the blocking file: %v", err)
	}
	return Options{
		LockFree: true,
		Dir:      filepath.Join(blocker, "cache"),
	}
}

func usableOptions(t *testing.T) Options {
	t.Helper()
	return Options{
		LockFree: true,
		Dir:      filepath.Join(t.TempDir(), "cache"),
	}
}

// The whole point of OpenOrDegrade: a cache we cannot build must cost the run
// its speed, not its output.
func TestOpenOrDegrade_UnbuildableCacheDoesNotStopTheCaller(t *testing.T) {
	c, note := OpenOrDegrade(context.Background(), unopenableOptions(t), "the download")

	if c != nil {
		t.Errorf("Got a cache back from options that cannot produce one; want nil so the caller takes the no-cache path")
	}
	if note == "" {
		t.Fatal("Cache setup failed silently; want a note, or a degraded run is indistinguishable from a healthy one")
	}
	if !strings.Contains(note, "the download") {
		t.Errorf("Note %q does not say which attempt lost its cache", note)
	}
}

func TestOpenOrDegrade_WorkingCacheIsSilent(t *testing.T) {
	c, note := OpenOrDegrade(context.Background(), usableOptions(t), "the download")

	if c == nil {
		t.Fatal("Got no cache for a usable directory")
	}
	defer c.Close()
	if note != "" {
		t.Errorf("Got note %q for a cache that was built; want none, or every successful run carries a warning", note)
	}
}

// A disabled cache is a nil cache too, and it must not look like a broken one:
// switching the cache off is a deliberate configuration, not a degradation.
func TestOpenOrDegrade_DisabledCacheIsNotReportedAsAFailure(t *testing.T) {
	c, note := OpenOrDegrade(context.Background(), Options{Disabled: true}, "the download")

	if c != nil {
		t.Errorf("Got a cache back with Disabled set")
	}
	if note != "" {
		t.Errorf("Got note %q for a cache that was switched off on purpose", note)
	}
}

func TestOpen_DisabledReturnsNoCacheAndNoError(t *testing.T) {
	c, err := Open(Options{Disabled: true})

	if err != nil {
		t.Errorf("Open(Disabled) = error %v; want nil, since no cache is a configuration rather than a failure", err)
	}
	if c != nil {
		t.Errorf("Open(Disabled) returned a cache")
	}
}

func TestOpen_SelectsTheRequestedImplementation(t *testing.T) {
	for _, tc := range []struct {
		name     string
		lockFree bool
	}{
		{name: "lock-free", lockFree: true},
		{name: "lock-based", lockFree: false},
	} {
		t.Run(tc.name, func(t *testing.T) {
			opts := usableOptions(t)
			opts.LockFree = tc.lockFree
			opts.MaxSize = 1 << 20

			c, err := Open(opts)
			if err != nil {
				t.Fatalf("Open() = error %v", err)
			}
			defer c.Close()

			_, isLockFree := c.(*LockFreeCache)
			if isLockFree != tc.lockFree {
				t.Errorf("Open() with LockFree=%v produced lock-free=%v; the flag does not select what it names",
					tc.lockFree, isLockFree)
			}
		})
	}
}

func TestOpen_ReportsWhyTheCacheCouldNotBeBuilt(t *testing.T) {
	_, err := Open(unopenableOptions(t))

	if err == nil {
		t.Fatal("Open() succeeded on a directory that cannot be created")
	}
	if !strings.Contains(err.Error(), "lock-free") {
		t.Errorf("Open() = %v; the error does not say which cache failed to build", err)
	}
}

// TestOpen_LockFreeHonorsUseHardlink checks that Options.UseHardlink, which
// carries -use-hardlink and the same-filesystem check in main, reaches the
// lock-free cache: with it, a pulled file is the cached blob; without it, a
// pulled file is a copy that nothing done to it can carry into the cache.
func TestOpen_LockFreeHonorsUseHardlink(t *testing.T) {
	for _, tc := range []struct {
		useHardlink bool
		wantShared  bool
	}{
		{useHardlink: true, wantShared: true},
		{useHardlink: false, wantShared: false},
	} {
		t.Run(fmt.Sprintf("UseHardlink=%v", tc.useHardlink), func(t *testing.T) {
			dir := t.TempDir()
			opts := Options{LockFree: true, Dir: filepath.Join(dir, "cache"), UseHardlink: tc.useHardlink}
			c, err := Open(opts)
			if err != nil {
				t.Fatalf("Open(%+v) failed: %v", opts, err)
			}
			defer c.Close()
			lf, ok := c.(*LockFreeCache)
			if !ok {
				t.Fatalf("Open(%+v) = %T, want *LockFreeCache", opts, c)
			}

			downloaded, out := stage(t, dir, "work/file", "use-hardlink", false)
			push(t, lf, out)
			blob := blobPath(opts.Dir, out.Digest.Hash)
			if shared := inodeOf(t, downloaded) == inodeOf(t, blob); shared != tc.wantShared {
				t.Errorf("After Push, downloaded file shares the cached inode = %v, want %v", shared, tc.wantShared)
			}

			pulled := filepath.Join(dir, "out", "file")
			if !pullOne(t, lf, &client.TreeOutput{Digest: out.Digest, Path: pulled}) {
				t.Fatal("Pull missed a blob that was just pushed")
			}
			if shared := inodeOf(t, pulled) == inodeOf(t, blob); shared != tc.wantShared {
				t.Errorf("After Pull, pulled file shares the cached inode = %v, want %v", shared, tc.wantShared)
			}
		})
	}
}
