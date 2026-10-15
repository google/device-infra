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
	c, note, _ := OpenOrDegrade(context.Background(), unopenableOptions(t), "the download")

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
	c, note, watermarks := OpenOrDegrade(context.Background(), usableOptions(t), "the download")

	if c == nil {
		t.Fatal("Got no cache for a usable directory")
	}
	defer c.Close()
	if note != "" || watermarks != "" {
		t.Errorf("Got notes %q, %q for a cache that was built with the default watermarks; want none, or every successful run carries a warning", note, watermarks)
	}
}

// A disabled cache is a nil cache too, and it must not look like a broken one:
// switching the cache off is a deliberate configuration, not a degradation.
func TestOpenOrDegrade_DisabledCacheIsNotReportedAsAFailure(t *testing.T) {
	c, note, _ := OpenOrDegrade(context.Background(), Options{Disabled: true}, "the download")

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

// The lock-based cache cannot honor a percentage or self-adaptive, and a value
// that does not parse, such as a negative one, means nothing. A cache set up
// with one is built anyway, with the 1 GiB default, and the run gets a
// watermarks note naming the flag rather than losing its cache.
func TestOpenOrDegrade_LockBasedCacheUsesTheDefaultForAnUnusableMinFreeSpace(t *testing.T) {
	for _, value := range []string{"20%", SelfAdaptive, "-5", "lots"} {
		t.Run(value, func(t *testing.T) {
			opts := usableOptions(t)
			opts.LockFree = false
			opts.MinFreeSpace = value

			c, note, watermarks := OpenOrDegrade(context.Background(), opts, "the download")
			if c == nil {
				t.Fatalf("Got no cache with -cache-min-free-space=%s (note %q); want the lock-based cache with the default", value, note)
			}
			defer c.Close()
			lc, ok := c.(*LocalCache)
			if !ok {
				t.Fatalf("OpenOrDegrade = %T, want *LocalCache", c)
			}
			if got := int64(lc.cachePolicies.MinFreeSpace); got != DefaultLegacyMinFreeSpace {
				t.Errorf("MinFreeSpace = %d, want the default of %d", got, int64(DefaultLegacyMinFreeSpace))
			}
			if note != "" {
				t.Errorf("Got failure note %q; the cache was built", note)
			}
			if !strings.Contains(watermarks, "-cache-min-free-space") || !strings.Contains(watermarks, value) {
				t.Errorf("Watermarks note %q does not name the flag and the value that was ignored", watermarks)
			}
		})
	}
}

// Unparsable watermarks no longer cost a download its lock-free cache: the
// cache is built with the defaults, and the run gets a note naming both flags.
func TestOpenOrDegrade_LockFreeCacheStaysOnWithUnparsableWatermarks(t *testing.T) {
	opts := usableOptions(t)
	opts.MinFreeSpace, opts.TargetFreeSpace = "lots", "-5%"

	c, note, watermarks := OpenOrDegrade(context.Background(), opts, "the download")
	if c == nil {
		t.Fatalf("Got no cache (note %q); want the lock-free cache with the default watermarks", note)
	}
	defer c.Close()
	cfg, err := c.(*LockFreeCache).store.EvictorConfig()
	if err != nil {
		t.Fatalf("EvictorConfig failed: %v", err)
	}
	if cfg.MinFreeSpace != DefaultLockFreeMinFreeSpace || cfg.TargetFreeSpace != DefaultLockFreeTargetFreeSpace {
		t.Errorf("Watermarks = %q, %q; want the defaults %q, %q", cfg.MinFreeSpace, cfg.TargetFreeSpace, DefaultLockFreeMinFreeSpace, DefaultLockFreeTargetFreeSpace)
	}
	if note != "" {
		t.Errorf("Got failure note %q; the cache was built", note)
	}
	for _, want := range []string{"the download", "-cache-min-free-space", "lots", "-cache-target-free-space", "-5%"} {
		if !strings.Contains(watermarks, want) {
			t.Errorf("Watermarks note %q does not mention %q", watermarks, want)
		}
	}
}

// The lock-free cache takes bytes, percentages and the reserved
// self-adaptive, and a bad or out-of-range value is replaced rather than
// fatal.
func TestOpen_LockFreeAcceptsByteOrPercentageWatermarks(t *testing.T) {
	for _, tc := range []struct{ min, target string }{
		{min: "", target: ""},
		{min: "20%", target: "25%"},
		{min: "1073741824", target: "30%"},
		{min: SelfAdaptive, target: ""},
		{min: "30%", target: "10%"},
		{min: "lots", target: "-5%"},
		{min: "90%", target: "100%"},
	} {
		t.Run(tc.min+","+tc.target, func(t *testing.T) {
			opts := usableOptions(t)
			opts.MinFreeSpace, opts.TargetFreeSpace = tc.min, tc.target

			c, err := Open(opts)
			if err != nil {
				t.Fatalf("Open(-cache-min-free-space=%q, -cache-target-free-space=%q) = %v", tc.min, tc.target, err)
			}
			c.Close()
		})
	}
}
