package cache

import (
	"context"
	"os"
	"path/filepath"
	"testing"

	"github.com/bazelbuild/remote-apis-sdks/go/pkg/client"
	"github.com/google/device-infra/src/devtools/rbe/common/monitoring"
	"github.com/google/device-infra/src/devtools/rbe/common/storage"
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
// mode-mismatch copy and one corrupt hit.
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
	}
	if *got.Activity != want {
		t.Errorf("Activity = %+v, want %+v", *got.Activity, want)
	}
}

// TestTrimMonitoringStats_FromATrim checks what a trim over its cap exports:
// the cap check reads every bucket to find the cache over it, and the trim
// evicts down to 90% of it.
func TestTrimMonitoringStats_FromATrim(t *testing.T) {
	cacheDir := filepath.Join(t.TempDir(), "cache")
	seedOldBlobs(t, cacheDir, trimBlobCount, trimBlobSize)
	res, err := Trim(context.Background(), TrimOptions{
		Dir:     cacheDir,
		MaxSize: 1000 * trimBlobSize * 10 / 9,
		statfs:  fixedVolume(100*mib, 50*mib),
	})
	if err != nil {
		t.Fatalf("Trim failed: %v", err)
	}

	got := TrimMonitoringStats(res)
	if got.Duration <= 0 || got.Sample == nil {
		t.Fatalf("TrimMonitoringStats = %+v, want a duration and a sample", got)
	}
	got.Duration, got.Sample.Duration = 0, 0
	want := monitoring.TrimStats{
		Outcome:           storage.TrimOutcomeEvicted,
		BytesReclaimed:    200 * trimBlobSize,
		FilesDeleted:      200,
		StartFreeFraction: 0.5,
	}
	wantSample := monitoring.TrimSampleStats{
		Files:           trimBlobCount,
		SizeBytes:       trimBlobCount * trimBlobSize,
		Exact:           true,
		CapCheckBuckets: 256,
	}
	gotSample := *got.Sample
	got.Sample = nil
	if *got != want || gotSample != wantSample {
		t.Errorf("TrimMonitoringStats = %+v with sample %+v, want %+v with %+v", *got, gotSample, want, wantSample)
	}
}

// A cap check that stops early is exported as such, with an estimated size,
// and a pass that measured nothing exports neither a free fraction nor a
// sample.
func TestTrimMonitoringStats_EarlyStopAndBusy(t *testing.T) {
	got := TrimMonitoringStats(storage.TrimResult{
		Outcome: storage.TrimOutcomeNoop, StartFreeBytes: 30, VolumeBytes: 120,
		Sample: &storage.TrimSample{Buckets: 32, TotalBuckets: 256, CapCheckBuckets: 32, SizeBytes: 1 << 30},
	})
	if got.StartFreeFraction != 0.25 || got.Sample == nil || got.Sample.Exact || !got.Sample.CapCheckStoppedEarly || got.Sample.CapCheckBuckets != 32 {
		t.Errorf("TrimMonitoringStats of an early stop = %+v with sample %+v, want a free fraction of 0.25 and an estimated size from a check stopped at 32", got, got.Sample)
	}

	got = TrimMonitoringStats(storage.TrimResult{Outcome: storage.TrimOutcomeBusy})
	if got.Outcome != storage.TrimOutcomeBusy || got.StartFreeFraction >= 0 || got.Sample != nil {
		t.Errorf("TrimMonitoringStats of a busy pass = %+v, want %q with a negative free fraction and no sample", got, storage.TrimOutcomeBusy)
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

// A -trim-only run that fails before its pass is still counted, as an error,
// with no volume or sample figures.
func TestFailedTrimMonitoringStats(t *testing.T) {
	got := FailedTrimMonitoringStats()
	if got.Outcome != storage.TrimOutcomeError || got.Sample != nil || got.StartFreeFraction >= 0 || got.BytesReclaimed != 0 {
		t.Errorf("FailedTrimMonitoringStats() = %+v, want outcome %q and nothing else", got, storage.TrimOutcomeError)
	}
}
