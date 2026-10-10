package cache

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/bazelbuild/remote-apis-sdks/go/pkg/client"
	"github.com/google/device-infra/src/devtools/rbe/common/monitoring"
)

func TestImpl(t *testing.T) {
	dir := t.TempDir()
	luci, err := NewLocalCache(filepath.Join(dir, "luci"), 1<<30, 0, false, true)
	if err != nil {
		t.Fatalf("NewLocalCache failed: %v", err)
	}
	defer luci.Close()

	for _, tc := range []struct {
		name  string
		cache Cache
		want  string
	}{
		{"no cache", nil, ImplNone},
		{"luci", luci, ImplLuci},
		{"lock-free", newTestCache(t, filepath.Join(dir, "lockfree"), true), ImplLockFree},
		{"something else", &otherCache{}, ImplOther},
	} {
		t.Run(tc.name, func(t *testing.T) {
			if got := Impl(tc.cache); got != tc.want {
				t.Errorf("Impl() = %q, want %q", got, tc.want)
			}
		})
	}
}

// otherCache is a Cache this package does not know.
type otherCache struct{ Cache }

// TestMonitoringStats_OnlyReportingCachesHaveActivity checks that a run
// without a cache, or on a cache that keeps no counters, is labeled but carries
// no activity, so that it does not export zeros that look like a healthy
// lock-free cache.
func TestMonitoringStats_OnlyReportingCachesHaveActivity(t *testing.T) {
	for _, tc := range []struct {
		name  string
		cache Cache
		impl  string
	}{
		{"no cache", nil, ImplNone},
		{"cache without counters", &otherCache{}, ImplOther},
	} {
		t.Run(tc.name, func(t *testing.T) {
			got := MonitoringStats(tc.cache)
			if got.Impl != tc.impl {
				t.Errorf("Impl = %q, want %q", got.Impl, tc.impl)
			}
			if got.Activity != nil {
				t.Errorf("Activity = %+v, want nil", got.Activity)
			}
		})
	}
}

// TestMonitoringStats_LockFreeCache checks that the lock-free cache's
// counters reach the monitoring stats, by driving the cache through one
// mode-mismatch copy, one corrupt hit and one headroom check.
func TestMonitoringStats_LockFreeCache(t *testing.T) {
	dir := t.TempDir()
	c := newTestCache(t, filepath.Join(dir, "cache"), true)

	_, mismatched := stage(t, dir, "work/mismatched", "mismatched", false)
	_, corrupt := stage(t, dir, "work/corrupt", "corrupt", false)
	push(t, c, mismatched, corrupt)

	pullOne(t, c, &client.TreeOutput{Digest: mismatched.Digest, Path: filepath.Join(dir, "out", "mismatched"), IsExecutable: true})
	// Pushing linked the downloaded file to the blob, so growing it grows the
	// blob, and the next pull of it finds the wrong size.
	appendTo(t, corrupt.Path, "more")
	pullOne(t, c, &client.TreeOutput{Digest: corrupt.Digest, Path: filepath.Join(dir, "out", "corrupt")})
	if err := c.EnsureHeadroom(t.Context(), 1); err != nil {
		t.Fatalf("EnsureHeadroom failed: %v", err)
	}

	got := MonitoringStats(c)
	if got.Impl != ImplLockFree {
		t.Errorf("Impl = %q, want %q", got.Impl, ImplLockFree)
	}
	if got.Activity == nil {
		t.Fatal("Activity = nil, want the lock-free cache's counters")
	}
	want := monitoring.LocalCacheActivity{
		ExecMismatchCopies: 1,
		ExecMismatchBytes:  mismatched.Digest.Size,
		CorruptBlobs:       1,
		HeadroomChecks:     1,
	}
	if *got.Activity != want {
		t.Errorf("Activity = %+v, want %+v", *got.Activity, want)
	}
}

// appendTo appends s to the file at path.
func appendTo(t *testing.T, path, s string) {
	t.Helper()
	f, err := os.OpenFile(path, os.O_WRONLY|os.O_APPEND, 0)
	if err != nil {
		t.Fatalf("Failed to open %s: %v", path, err)
	}
	defer f.Close()
	if _, err := f.WriteString(s); err != nil {
		t.Fatalf("Failed to append to %s: %v", path, err)
	}
}
