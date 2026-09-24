package cache

import (
	"context"
	"os"
	"path/filepath"
	"strings"
	"testing"
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
