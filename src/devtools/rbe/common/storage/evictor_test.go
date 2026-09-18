package storage

import (
	"context"
	"fmt"
	"math/rand"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/google/device-infra/src/devtools/rbe/common/monitoring"
)

func TestParseSpaceThreshold(t *testing.T) {
	tests := []struct {
		input       string
		wantPercent bool
		wantVal     float64
		wantBytes   int64
		wantErr     bool
	}{
		{input: "15%", wantPercent: true, wantVal: 15.0},
		{input: "15.5%", wantPercent: true, wantVal: 15.5},
		{input: "0%", wantPercent: true, wantVal: 0.0},
		{input: "100%", wantPercent: true, wantVal: 100.0},
		{input: "25", wantErr: true},   // ambiguous without % or unit
		{input: "15.5", wantErr: true}, // ambiguous without % or unit
		{input: "400gb", wantPercent: false, wantBytes: 400 * 1024 * 1024 * 1024},
		{input: "500GB", wantPercent: false, wantBytes: 500 * 1024 * 1024 * 1024},
		{input: "1gib", wantPercent: false, wantBytes: 1024 * 1024 * 1024},
		{input: "1g", wantPercent: false, wantBytes: 1024 * 1024 * 1024},
		{input: "2tb", wantPercent: false, wantBytes: 2 * 1024 * 1024 * 1024 * 1024},
		{input: "1tib", wantPercent: false, wantBytes: 1024 * 1024 * 1024 * 1024},
		{input: "1t", wantPercent: false, wantBytes: 1024 * 1024 * 1024 * 1024},
		{input: "100mb", wantPercent: false, wantBytes: 100 * 1024 * 1024},
		{input: "1mib", wantPercent: false, wantBytes: 1024 * 1024},
		{input: "1m", wantPercent: false, wantBytes: 1024 * 1024},
		{input: "1kb", wantPercent: false, wantBytes: 1024},
		{input: "1kib", wantPercent: false, wantBytes: 1024},
		{input: "1k", wantPercent: false, wantBytes: 1024},
		{input: "1b", wantPercent: false, wantBytes: 1},
		{input: "1024b", wantPercent: false, wantBytes: 1024},
		{input: "1073741824b", wantPercent: false, wantBytes: 1073741824},
		{input: "1.5gb", wantPercent: false, wantBytes: int64(1.5 * 1024 * 1024 * 1024)},
		{input: "-500gb", wantErr: true},
		{input: "1073741824", wantErr: true}, // naked integer without unit suffix rejected
		{input: "150%", wantErr: true},
		{input: "-5%", wantErr: true},
		{input: "invalid", wantErr: true},
		{input: "", wantErr: true},
	}

	for _, tc := range tests {
		thresh, err := ParseSpaceThreshold(tc.input)
		if (err != nil) != tc.wantErr {
			t.Errorf("ParseSpaceThreshold(%q) error = %v, wantErr = %v", tc.input, err, tc.wantErr)
			continue
		}
		if tc.wantErr {
			continue
		}
		if thresh.IsPercent != tc.wantPercent {
			t.Errorf("ParseSpaceThreshold(%q).IsPercent = %v, want %v", tc.input, thresh.IsPercent, tc.wantPercent)
		}
		if tc.wantPercent && thresh.Percent != tc.wantVal {
			t.Errorf("ParseSpaceThreshold(%q).Percent = %v, want %v", tc.input, thresh.Percent, tc.wantVal)
		}
		if !tc.wantPercent && thresh.Bytes != tc.wantBytes {
			t.Errorf("ParseSpaceThreshold(%q).Bytes = %v, want %v", tc.input, thresh.Bytes, tc.wantBytes)
		}
	}
}

func TestSpaceThreshold_Evaluate(t *testing.T) {
	effectiveTotal := int64(1000 * 1024 * 1024 * 1024) // 1000 GB

	pctThresh, _ := ParseSpaceThreshold("15%")
	if got := pctThresh.Evaluate(effectiveTotal); got != 150*1024*1024*1024 {
		t.Errorf("pctThresh.Evaluate() = %d, want %d", got, 150*1024*1024*1024)
	}

	byteThresh, _ := ParseSpaceThreshold("200gb")
	if got := byteThresh.Evaluate(effectiveTotal); got != 200*1024*1024*1024 {
		t.Errorf("byteThresh.Evaluate() = %d, want %d", got, 200*1024*1024*1024)
	}
}

func TestComputeEffectiveSpace(t *testing.T) {
	reserved := int64(500 * 1024 * 1024 * 1024) // 500 GB

	// Normal case: Total 10TB, Free 2TB
	stats := DiskStats{
		TotalBytes: 10 * 1024 * 1024 * 1024 * 1024,
		FreeBytes:  2 * 1024 * 1024 * 1024 * 1024,
	}
	effTotal, effFree, freePct := ComputeEffectiveSpace(stats, reserved)
	wantTotal := stats.TotalBytes - reserved
	wantFree := stats.FreeBytes - reserved
	if effTotal != wantTotal || effFree != wantFree {
		t.Errorf("ComputeEffectiveSpace got (%d, %d), want (%d, %d)", effTotal, effFree, wantTotal, wantFree)
	}
	wantPct := (float64(wantFree) / float64(wantTotal)) * 100.0
	if freePct != wantPct {
		t.Errorf("ComputeEffectiveSpace freePct = %f, want %f", freePct, wantPct)
	}

	// Edge case: Free space less than reserved
	lowStats := DiskStats{
		TotalBytes: 10 * 1024 * 1024 * 1024 * 1024,
		FreeBytes:  300 * 1024 * 1024 * 1024, // 300GB < 500GB reserved
	}
	_, lowFree, lowPct := ComputeEffectiveSpace(lowStats, reserved)
	if lowFree != 0 || lowPct != 0 {
		t.Errorf("ComputeEffectiveSpace with low free got free=%d, pct=%f; want 0, 0", lowFree, lowPct)
	}

	// Edge case: Total space less than reserved (clamp effectiveTotal to 1)
	tinyStats := DiskStats{
		TotalBytes: 400 * 1024 * 1024 * 1024, // 400GB < 500GB reserved
		FreeBytes:  100 * 1024 * 1024 * 1024,
	}
	tinyTotal, tinyFree, tinyPct := ComputeEffectiveSpace(tinyStats, reserved)
	if tinyTotal != 1 || tinyFree != 0 || tinyPct != 0 {
		t.Errorf("ComputeEffectiveSpace with tiny total got (%d, %d, %f), want (1, 0, 0)", tinyTotal, tinyFree, tinyPct)
	}
}

func TestCheckAndEvict(t *testing.T) {
	tempDir := t.TempDir()
	cfg := DefaultEvictorConfig()

	// 1. Statfs error branch
	var statfsCalled atomic.Int32
	evErr, err := NewEvictor(tempDir, cfg, func(path string) (DiskStats, error) {
		statfsCalled.Add(1)
		return DiskStats{}, fmt.Errorf("statfs error")
	})
	if err != nil {
		t.Fatalf("NewEvictor failed: %v", err)
	}
	evErr.checkAndEvict(context.Background())
	if statfsCalled.Load() != 1 {
		t.Errorf("statfsCalled = %d, want 1", statfsCalled.Load())
	}

	// 2. Free space above threshold -> should not trigger EvictOnce
	var aboveCalls atomic.Int32
	evAbove, err := NewEvictor(tempDir, cfg, func(path string) (DiskStats, error) {
		aboveCalls.Add(1)
		return DiskStats{TotalBytes: 1000 * 1024 * 1024 * 1024, FreeBytes: 900 * 1024 * 1024 * 1024}, nil
	})
	if err != nil {
		t.Fatalf("NewEvictor failed: %v", err)
	}
	evAbove.checkAndEvict(context.Background())
	if aboveCalls.Load() != 1 {
		t.Errorf("aboveCalls = %d, want 1 (statfs queried only once, EvictOnce not called)", aboveCalls.Load())
	}

	// 3. Free space below threshold -> triggers EvictOnce, which invokes statfs again
	var belowCalls atomic.Int32
	evBelow, err := NewEvictor(tempDir, cfg, func(path string) (DiskStats, error) {
		belowCalls.Add(1)
		return DiskStats{TotalBytes: 1000 * 1024 * 1024 * 1024, FreeBytes: 120 * 1024 * 1024 * 1024}, nil
	})
	if err != nil {
		t.Fatalf("NewEvictor failed: %v", err)
	}
	evBelow.checkAndEvict(context.Background())
	if belowCalls.Load() != 2 {
		t.Errorf("belowCalls = %d, want 2 (1 for checkAndEvict + 1 for EvictOnce)", belowCalls.Load())
	}
}

func TestRealStatfs(t *testing.T) {
	tempDir := t.TempDir()
	stats, err := RealStatfs(tempDir)
	if err != nil {
		t.Fatalf("RealStatfs failed: %v", err)
	}
	if stats.TotalBytes <= 0 || stats.FreeBytes <= 0 {
		t.Errorf("RealStatfs returned non-positive stats: %+v", stats)
	}

	// Invalid path returns error
	if _, err := RealStatfs("/invalid/path/that/does/not/exist"); err == nil {
		t.Errorf("RealStatfs on nonexistent path should return error")
	}
}

func TestEvictor_CutoffEstimation(t *testing.T) {
	tempDir, err := os.MkdirTemp("", "storage_evictor_cutoff_test")
	if err != nil {
		t.Fatalf("failed to create temp dir: %v", err)
	}
	defer os.RemoveAll(tempDir)

	now := time.Now()

	cfg := EvictorConfig{
		MinFreeSpace:    "15%",
		TargetFreeSpace: "25%",
		ReservedSpaceGB: 0,
		CheckInterval:   10 * time.Second,
		SampleBuckets:   16,
		BatchSize:       100,
		MinBlobAge:      10 * time.Minute,
	}

	mockStatfs := func(path string) (DiskStats, error) {
		return DiskStats{
			TotalBytes: 100 * 1024 * 1024,
			FreeBytes:  10 * 1024 * 1024, // 10% free
		}, nil
	}

	ev, err := NewEvictor(tempDir, cfg, mockStatfs)
	if err != nil {
		t.Fatalf("NewEvictor failed: %v", err)
	}

	ev.rand = rand.New(rand.NewSource(42))
	sampledPaths := ev.pickRandomBuckets(ev.config.SampleBuckets)

	for bIdx, dir := range sampledPaths {
		if err := os.MkdirAll(dir, 0755); err != nil {
			t.Fatalf("MkdirAll failed: %v", err)
		}
		// Create 10 files in each bucket with staggered ages (from 2h to 20h ago)
		for i := 1; i <= 10; i++ {
			hash := fmt.Sprintf("%02x00%060x", bIdx, i)
			p := filepath.Join(dir, hash)
			data := make([]byte, 1024) // 1KB each
			if err := os.WriteFile(p, data, 0644); err != nil {
				t.Fatalf("WriteFile failed: %v", err)
			}
			modTime := now.Add(-time.Duration(i*2) * time.Hour)
			_ = os.Chtimes(p, modTime, modTime)
		}
	}

	// Reset RNG so estimateCutoffAge samples the exact same buckets
	ev.rand = rand.New(rand.NewSource(42))
	flight := ev.newFlight(ev.snapshot())
	cutoff := flight.estimateCutoffAge(context.Background(), 100*1024*1024, 10*1024*1024, 15*1024*1024)
	cutoffAgeHours := int(time.Since(cutoff).Round(time.Hour).Hours())
	if cutoffAgeHours != 18 {
		t.Errorf("estimated cutoff age = %d hours, want 18h (%v)", cutoffAgeHours, cutoff)
	}
}

func TestEvictor_EvictOnce_ReclaimsSpace(t *testing.T) {
	tempDir, err := os.MkdirTemp("", "storage_evictor_test")
	if err != nil {
		t.Fatalf("failed to create temp dir: %v", err)
	}
	defer os.RemoveAll(tempDir)

	store, err := New(tempDir)
	if err != nil {
		t.Fatalf("New storage failed: %v", err)
	}

	now := time.Now()

	// 1. Create 20 "old" blobs (older than 2 hours)
	for i := 0; i < 20; i++ {
		hash := fmt.Sprintf("11%02x%060x", i, i)
		data := []byte(fmt.Sprintf("old-content-%d", i))
		if err := store.Write(hash, data); err != nil {
			t.Fatalf("store.Write failed: %v", err)
		}
		p := store.blobPath(hash)
		modTime := now.Add(-5 * time.Hour)
		_ = os.Chtimes(p, modTime, modTime)
	}

	// 2. Create 5 "recent" blobs (created 2 minutes ago, within 10m MinBlobAge)
	var recentHashes []string
	for i := 0; i < 5; i++ {
		hash := fmt.Sprintf("22%02x%060x", i, i)
		data := []byte(fmt.Sprintf("recent-content-%d", i))
		if err := store.Write(hash, data); err != nil {
			t.Fatalf("store.Write failed: %v", err)
		}
		p := store.blobPath(hash)
		modTime := now.Add(-2 * time.Minute)
		_ = os.Chtimes(p, modTime, modTime)
		recentHashes = append(recentHashes, hash)
	}

	// 3. Create 2 temporary in-flight files (.tmp.xxx)
	tmpDir := filepath.Join(tempDir, "11", "00")
	tmpFile := filepath.Join(tmpDir, ".tmp.11000000.12345.1")
	if err := os.WriteFile(tmpFile, []byte("in-flight-tmp-data"), 0644); err != nil {
		t.Fatalf("WriteFile tmp failed: %v", err)
	}

	// Mock dynamic statfs: free space starts at 100MB, increases as unlinks occur
	var simulatedFree atomic.Int64
	simulatedFree.Store(100 * 1024 * 1024) // 100MB free initially (triggers eviction)

	mockStatfs := func(path string) (DiskStats, error) {
		// Simulate space being reclaimed
		free := simulatedFree.Add(50 * 1024 * 1024)
		return DiskStats{
			TotalBytes: 1000 * 1024 * 1024, // 1000MB
			FreeBytes:  free,
		}, nil
	}

	cfg := EvictorConfig{
		MinFreeSpace:    "15%", // 150MB trigger
		TargetFreeSpace: "25%", // 250MB target
		ReservedSpaceGB: 0,
		CheckInterval:   10 * time.Second,
		SampleBuckets:   16,
		BatchSize:       2, // Batch size 2 to trigger frequent statfs checks
		MinBlobAge:      10 * time.Minute,
	}

	ev, err := NewEvictor(tempDir, cfg, mockStatfs)
	if err != nil {
		t.Fatalf("NewEvictor failed: %v", err)
	}

	// Run EvictOnce
	reclaimed, deleted, err := ev.EvictOnce(context.Background())
	if err != nil {
		t.Fatalf("EvictOnce failed: %v", err)
	}

	if deleted == 0 || reclaimed == 0 {
		t.Errorf("EvictOnce expected non-zero deletions, got deleted=%d, reclaimed=%d", deleted, reclaimed)
	}

	// Verify all recent blobs are still present
	for _, h := range recentHashes {
		if !store.Has(h) {
			t.Errorf("recent blob %s was unexpectedly evicted", h)
		}
	}

	// Verify temporary in-flight file was not deleted
	if _, err := os.Stat(tmpFile); os.IsNotExist(err) {
		t.Errorf("temporary in-flight file %s was unexpectedly deleted", tmpFile)
	}
}

func TestEvictor_SingleFlight(t *testing.T) {
	tempDir, err := os.MkdirTemp("", "storage_singleflight_test")
	if err != nil {
		t.Fatalf("failed to create temp dir: %v", err)
	}
	defer os.RemoveAll(tempDir)

	cfg := DefaultEvictorConfig()
	ev, err := NewEvictor(tempDir, cfg, func(path string) (DiskStats, error) {
		return DiskStats{TotalBytes: 1000, FreeBytes: 100}, nil
	})
	if err != nil {
		t.Fatalf("NewEvictor failed: %v", err)
	}

	// Simulate an active eviction flight
	ev.isEvicting.Store(true)

	// Second invocation should immediately return 0 without doing work
	reclaimed, deleted, err := ev.EvictOnce(context.Background())
	if err != nil {
		t.Errorf("EvictOnce returned error: %v", err)
	}
	if reclaimed != 0 || deleted != 0 {
		t.Errorf("EvictOnce during active flight returned (%d, %d), want (0, 0)", reclaimed, deleted)
	}
}

func TestEvictor_EmergencyMode(t *testing.T) {
	tempDir, err := os.MkdirTemp("", "storage_emergency_test")
	if err != nil {
		t.Fatalf("failed to create temp dir: %v", err)
	}
	defer os.RemoveAll(tempDir)

	store, err := New(tempDir)
	if err != nil {
		t.Fatalf("New storage failed: %v", err)
	}

	// Create a large blob (60MB via truncated file size) with modTime 15m ago
	// (older than 10m grace period, but newer than the 50-hour cutoff).
	largeHash := "3300000000000000000000000000000000000000000000000000000000000000"
	p := store.blobPath(largeHash)
	if err := os.MkdirAll(filepath.Dir(p), 0755); err != nil {
		t.Fatalf("MkdirAll failed: %v", err)
	}
	f, err := os.Create(p)
	if err != nil {
		t.Fatalf("Create failed: %v", err)
	}
	// Truncate to 60MB (sparse file)
	if err := f.Truncate(60 * 1024 * 1024); err != nil {
		f.Close()
		t.Fatalf("Truncate failed: %v", err)
	}
	f.Close()
	// Set modTime to 15m ago (newer than cutoff, so it only evicts via emergency large-blob mode)
	modTime := time.Now().Add(-15 * time.Minute)
	_ = os.Chtimes(p, modTime, modTime)

	cfg := EvictorConfig{
		MinFreeSpace:    "15%",
		TargetFreeSpace: "25%",
		ReservedSpaceGB: 0,
		CheckInterval:   10 * time.Second,
		SampleBuckets:   16,
		BatchSize:       200,
		MinBlobAge:      10 * time.Minute,
	}

	// 1. Critical free space (2% free < 5% emergency threshold) -> triggers emergency large-blob eviction
	mockStatfsEmergency := func(path string) (DiskStats, error) {
		return DiskStats{
			TotalBytes: 1000 * 1024 * 1024,
			FreeBytes:  20 * 1024 * 1024, // 2% free
		}, nil
	}

	ev, err := NewEvictor(tempDir, cfg, mockStatfsEmergency)
	if err != nil {
		t.Fatalf("NewEvictor failed: %v", err)
	}

	reclaimed, deleted, err := ev.EvictOnce(context.Background())
	if err != nil {
		t.Fatalf("EvictOnce failed: %v", err)
	}

	if deleted != 1 || reclaimed != 60*1024*1024 {
		t.Errorf("EvictOnce emergency mode got deleted=%d, reclaimed=%d; want (1, %d)", deleted, reclaimed, 60*1024*1024)
	}
}

func TestDeriveCutoffAge(t *testing.T) {
	now := time.Now()
	ev := &Evictor{
		config: EvictorConfig{
			MinBlobAge: 10 * time.Minute,
		},
	}

	hist := make([]int64, 1441)
	// Put 100MB in 5-hour bin, 100MB in 10-hour bin, 100MB in 20-hour bin
	hist[5] = 100 * 1024 * 1024
	hist[10] = 100 * 1024 * 1024
	hist[20] = 100 * 1024 * 1024

	stats := sampleStats{
		histogram:  hist,
		totalBytes: 300 * 1024 * 1024,
		maxDays:    60,
	}

	flight := ev.newFlight(ev.snapshot())

	// 1. Reclaim 100MB out of 300MB total occupancy (targetFraction = 33.3%) -> reclaimTarget = 100MB
	// Oldest populated bin is 20h (has 100MB) -> cutoff should be 20 hours ago
	cutoff, cutoffHours := flight.deriveCutoffAge(stats, 1000*1024*1024, 700*1024*1024, 100*1024*1024, now)
	if cutoffHours != 20 {
		t.Errorf("cutoffHours = %d, want 20", cutoffHours)
	}
	expectedCutoff := now.Add(-20 * time.Hour)
	if !cutoff.Equal(expectedCutoff) {
		t.Errorf("cutoff = %v, want %v", cutoff, expectedCutoff)
	}

	// 2. Reclaim 200MB out of 300MB total occupancy (targetFraction = 66.6%) -> reclaimTarget = 200MB
	// Bins: 20h (100MB) + 10h (100MB) = 200MB -> cutoff should be 10 hours ago
	cutoff2, cutoffHours2 := flight.deriveCutoffAge(stats, 1000*1024*1024, 700*1024*1024, 200*1024*1024, now)
	if cutoffHours2 != 10 {
		t.Errorf("cutoffHours2 = %d, want 10", cutoffHours2)
	}
	expectedCutoff2 := now.Add(-10 * time.Hour)
	if !cutoff2.Equal(expectedCutoff2) {
		t.Errorf("cutoff2 = %v, want %v", cutoff2, expectedCutoff2)
	}

	// 3. Overflow bin: 100MB in bin 1440 (>=60 days)
	hist[1440] = 100 * 1024 * 1024
	stats.totalBytes += 100 * 1024 * 1024
	cutoff3, cutoffHours3 := flight.deriveCutoffAge(stats, 1000*1024*1024, 700*1024*1024, 50*1024*1024, now)
	if cutoffHours3 != 1440 {
		t.Errorf("cutoffHours3 = %d, want 1440", cutoffHours3)
	}
	expectedCutoff3 := now.Add(-1440 * time.Hour)
	if !cutoff3.Equal(expectedCutoff3) {
		t.Errorf("cutoff3 = %v, want %v", cutoff3, expectedCutoff3)
	}

	// 4. Exact boundary test: totalOccupancy=1000, targetReclaimBytes=500
	// hist[2]=500, hist[1]=500, stats.totalBytes=1000
	// targetFraction = 500/1000 = 0.5 -> reclaimTarget = 500 -> cutoff is bin 2
	// If targetReclaimBytes was mutated to 501 -> reclaimTarget = 501 > 500 -> cutoff becomes bin 1
	histBoundary := make([]int64, 1441)
	histBoundary[2] = 500
	histBoundary[1] = 500
	statsBoundary := sampleStats{
		histogram:  histBoundary,
		totalBytes: 1000,
		maxDays:    60,
	}
	_, cutoffHoursBoundary := flight.deriveCutoffAge(statsBoundary, 1000, 0, 500, now)
	if cutoffHoursBoundary != 2 {
		t.Errorf("cutoffHoursBoundary = %d, want 2", cutoffHoursBoundary)
	}

	// 5. Zero occupancy test (effectiveTotal == effectiveFree): clamps totalOccupancy to 1 without div-by-zero
	// targetFraction becomes 1.0 (reclaim 100% of data) -> cutoff reaches newest bin (5 hours)
	cutoffZero, cutoffHoursZero := flight.deriveCutoffAge(stats, 1000, 1000, 100, now)
	if cutoffHoursZero != 5 {
		t.Errorf("cutoffHoursZero = %d, want 5 (full reclaim)", cutoffHoursZero)
	}
	expectedCutoffZero := now.Add(-5 * time.Hour)
	if !cutoffZero.Equal(expectedCutoffZero) {
		t.Errorf("cutoffZero = %v, want %v", cutoffZero, expectedCutoffZero)
	}

	// 6. Insufficient samples fallback: histogram has fewer bytes than reclaim target
	insufficientHist := make([]int64, 1441)
	insufficientHist[10] = 50
	insufficientStats := sampleStats{
		histogram:  insufficientHist,
		totalBytes: 1000,
		maxDays:    60,
	}
	cutoffFallback, cutoffHoursFallback := flight.deriveCutoffAge(insufficientStats, 1000, 0, 800, now)
	if cutoffHoursFallback != -1 {
		t.Errorf("cutoffHoursFallback = %d, want -1", cutoffHoursFallback)
	}
	expectedFallback := now.Add(-10 * time.Minute)
	if !cutoffFallback.Equal(expectedFallback) {
		t.Errorf("cutoffFallback = %v, want %v", cutoffFallback, expectedFallback)
	}
}

func TestBuildAgeHistogram_IgnoresNonCandidates(t *testing.T) {
	tempDir := t.TempDir()
	now := time.Now()

	cfg := EvictorConfig{
		MinFreeSpace:    "15%",
		TargetFreeSpace: "25%",
		SampleBuckets:   1,
		MinBlobAge:      10 * time.Minute,
	}

	ev, err := NewEvictor(tempDir, cfg, nil)
	if err != nil {
		t.Fatalf("NewEvictor failed: %v", err)
	}

	ev.rand = rand.New(rand.NewSource(42))
	sampledPaths := ev.pickRandomBuckets(1)
	bucketDir := sampledPaths[0]
	if err := os.MkdirAll(bucketDir, 0755); err != nil {
		t.Fatalf("MkdirAll failed: %v", err)
	}

	// 1. Valid blob: 1KB, 2 hours old
	validHash := "0000000000000000000000000000000000000000000000000000000000000001"
	validFile := filepath.Join(bucketDir, validHash)
	if err := os.WriteFile(validFile, make([]byte, 1024), 0644); err != nil {
		t.Fatalf("WriteFile failed: %v", err)
	}
	mTime := now.Add(-2 * time.Hour)
	_ = os.Chtimes(validFile, mTime, mTime)

	// 2. In-flight temp file (should be ignored by buildAgeHistogram)
	tmpFile := filepath.Join(bucketDir, ".tmp.12345")
	if err := os.WriteFile(tmpFile, make([]byte, 10*1024*1024), 0644); err != nil {
		t.Fatalf("WriteFile failed: %v", err)
	}
	_ = os.Chtimes(tmpFile, mTime, mTime)

	// 3. Subdirectory (should be ignored by buildAgeHistogram)
	subDir := filepath.Join(bucketDir, "nested_dir")
	if err := os.MkdirAll(subDir, 0755); err != nil {
		t.Fatalf("MkdirAll failed: %v", err)
	}

	// 4. Invalid name file (should be ignored by buildAgeHistogram)
	invalidFile := filepath.Join(bucketDir, "not_a_valid_hash")
	if err := os.WriteFile(invalidFile, make([]byte, 10*1024*1024), 0644); err != nil {
		t.Fatalf("WriteFile failed: %v", err)
	}
	_ = os.Chtimes(invalidFile, mTime, mTime)

	// Reset RNG so buildAgeHistogram samples the exact same bucketDir
	ev.rand = rand.New(rand.NewSource(42))
	flight := ev.newFlight(ev.snapshot())
	stats := flight.buildAgeHistogram(now)

	if stats.totalCount != 1 {
		t.Errorf("stats.totalCount = %d, want 1 (should ignore temp files, directories, and non-blob names)", stats.totalCount)
	}
	if stats.totalBytes != 1024 {
		t.Errorf("stats.totalBytes = %d, want 1024", stats.totalBytes)
	}
}

func TestBuildAgeHistogram_OverflowAndGracePeriod(t *testing.T) {
	tempDir := t.TempDir()
	now := time.Now()

	cfg := EvictorConfig{
		MinFreeSpace:    "15%",
		TargetFreeSpace: "25%",
		SampleBuckets:   1,
		MinBlobAge:      10 * time.Minute,
	}

	ev, err := NewEvictor(tempDir, cfg, nil)
	if err != nil {
		t.Fatalf("NewEvictor failed: %v", err)
	}

	ev.rand = rand.New(rand.NewSource(42))
	sampledPaths := ev.pickRandomBuckets(1)
	bucketDir := sampledPaths[0]
	if err := os.MkdirAll(bucketDir, 0755); err != nil {
		t.Fatalf("MkdirAll failed: %v", err)
	}

	// 1. Blob older than maxAgeDays (60 days) -> should land in overflow bin
	overflowHash := "1111111111111111111111111111111111111111111111111111111111111111"
	overflowFile := filepath.Join(bucketDir, overflowHash)
	if err := os.WriteFile(overflowFile, make([]byte, 2048), 0644); err != nil {
		t.Fatalf("WriteFile failed: %v", err)
	}
	oldTime := now.Add(-70 * 24 * time.Hour)
	_ = os.Chtimes(overflowFile, oldTime, oldTime)

	// 2. Blob in grace period (5 minutes old) -> should be excluded by isEligible
	graceHash := "2222222222222222222222222222222222222222222222222222222222222222"
	graceFile := filepath.Join(bucketDir, graceHash)
	if err := os.WriteFile(graceFile, make([]byte, 4096), 0644); err != nil {
		t.Fatalf("WriteFile failed: %v", err)
	}
	graceTime := now.Add(-5 * time.Minute)
	_ = os.Chtimes(graceFile, graceTime, graceTime)

	ev.rand = rand.New(rand.NewSource(42))
	flight := ev.newFlight(ev.snapshot())
	stats := flight.buildAgeHistogram(now)

	if stats.totalCount != 1 {
		t.Errorf("stats.totalCount = %d, want 1 (grace period blob excluded)", stats.totalCount)
	}
	if stats.totalBytes != 2048 {
		t.Errorf("stats.totalBytes = %d, want 2048", stats.totalBytes)
	}
	maxHours := 60 * 24
	if stats.histogram[maxHours] != 2048 {
		t.Errorf("overflow bin histogram[%d] = %d, want 2048", maxHours, stats.histogram[maxHours])
	}
}

func TestIsCandidateBlob(t *testing.T) {
	validHash := "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
	if !isCandidateBlob(validHash, false) {
		t.Errorf("isCandidateBlob(%q, false) = false, want true", validHash)
	}

	// Directory -> false
	if isCandidateBlob(validHash, true) {
		t.Errorf("isCandidateBlob on dir = true, want false")
	}

	// Leading dot temp file -> false
	if isCandidateBlob(".tmp."+validHash, false) {
		t.Errorf("isCandidateBlob on leading dot temp file = true, want false")
	}

	// Short name -> false
	if isCandidateBlob("short", false) {
		t.Errorf("isCandidateBlob on short name = true, want false")
	}
}

func TestIsEligibleForEviction(t *testing.T) {
	cfg := DefaultEvictorConfig()
	cfg.MinBlobAge = 10 * time.Minute
	ev := &Evictor{config: cfg}
	flight := ev.newFlight(ev.snapshot())

	now := time.Now()

	// 20 minutes old -> eligible (true)
	if !flight.isEligible(now.Add(-20*time.Minute), now) {
		t.Errorf("flight.isEligible on 20m old blob = false, want true")
	}

	// 5 minutes old -> not eligible (false)
	if flight.isEligible(now.Add(-5*time.Minute), now) {
		t.Errorf("flight.isEligible on 5m old blob = true, want false")
	}

	// Direct isBlobEligible helper
	if !isBlobEligible(now.Add(-20*time.Minute), now, 10*time.Minute) {
		t.Errorf("isBlobEligible on 20m old blob = false, want true")
	}
	if isBlobEligible(now.Add(-5*time.Minute), now, 10*time.Minute) {
		t.Errorf("isBlobEligible on 5m old blob = true, want false")
	}
}

func TestSumHistogramRange(t *testing.T) {
	hist := make([]int64, 1441)
	for i := 0; i < 10; i++ {
		hist[i] = 100 // 100 bytes per bin for bins [0..9]
	}

	// Normal slice [0..5) -> 5 * 100 = 500
	if got := sumHistogramRange(hist, 0, 5); got != 500 {
		t.Errorf("sumHistogramRange(0, 5) = %d, want 500", got)
	}

	// Range beyond populated bins [5..15) -> 5 * 100 = 500
	if got := sumHistogramRange(hist, 5, 15); got != 500 {
		t.Errorf("sumHistogramRange(5, 15) = %d, want 500", got)
	}

	// Negative start hour clamping
	if got := sumHistogramRange(hist, -10, 5); got != 500 {
		t.Errorf("sumHistogramRange(-10, 5) = %d, want 500", got)
	}

	// End hour beyond maxHours clamping
	if got := sumHistogramRange(hist, 0, 5000); got != 1000 {
		t.Errorf("sumHistogramRange(0, 5000) = %d, want 1000", got)
	}
}

func TestEvictor_AutoAdjustMaxAgeDays(t *testing.T) {
	tempDir := t.TempDir()
	now := time.Now()

	cfg := EvictorConfig{
		MinFreeSpace:    "15%",
		TargetFreeSpace: "25%",
		ReservedSpaceGB: 0,
		CheckInterval:   10 * time.Second,
		SampleBuckets:   1,
		BatchSize:       100,
		MinBlobAge:      10 * time.Minute,
	}

	ev, err := NewEvictor(tempDir, cfg, func(path string) (DiskStats, error) {
		return DiskStats{TotalBytes: 100 * 1024 * 1024, FreeBytes: 10 * 1024 * 1024}, nil
	})
	if err != nil {
		t.Fatalf("NewEvictor failed: %v", err)
	}

	// Deterministically seed RNG and get the sampled bucket path
	ev.rand = rand.New(rand.NewSource(42))
	sampledPaths := ev.pickRandomBuckets(1)
	bucketDir := sampledPaths[0]
	if err := os.MkdirAll(bucketDir, 0755); err != nil {
		t.Fatalf("MkdirAll failed: %v", err)
	}

	for i := 1; i <= 10; i++ {
		hash := fmt.Sprintf("0000%060x", i)
		p := filepath.Join(bucketDir, hash)
		data := make([]byte, 1024)
		if err := os.WriteFile(p, data, 0644); err != nil {
			t.Fatalf("WriteFile failed: %v", err)
		}
		modTime := now.Add(-70 * 24 * time.Hour)
		_ = os.Chtimes(p, modTime, modTime)
	}

	// Reset RNG so estimateCutoffAge samples the exact same bucketDir
	ev.rand = rand.New(rand.NewSource(42))

	if ev.MaxAgeDays() != 60 {
		t.Errorf("Initial MaxAgeDays() = %d, want 60", ev.MaxAgeDays())
	}

	// First estimateCutoffAge pass -> 100% of samples in overflow bin (>60d), should auto-adjust 60 -> 120
	flight := ev.newFlight(ev.snapshot())
	_ = flight.estimateCutoffAge(context.Background(), 100*1024*1024, 10*1024*1024, 15*1024*1024)
	if ev.MaxAgeDays() != 120 {
		t.Errorf("MaxAgeDays() after first overflow = %d, want 120", ev.MaxAgeDays())
	}
}

func TestNewEvictor_Sanitization(t *testing.T) {
	tempDir := t.TempDir()

	// 1. Test upper-bound clamping
	cfgHigh := EvictorConfig{
		MinFreeSpace:    "15%",
		TargetFreeSpace: "25%",
		ReservedSpaceGB: 200,
		SampleBuckets:   100,              // exceeds max 64
		BatchSize:       5000,             // exceeds max 2000
		BatchDelay:      10 * time.Second, // exceeds max 2ms
	}

	evHigh, err := NewEvictor(tempDir, cfgHigh, nil)
	if err != nil {
		t.Fatalf("NewEvictor failed: %v", err)
	}

	// Verify reservedBytes calculation (cfg.ReservedSpaceGB * 1GB)
	if evHigh.reservedBytes != 200*1024*1024*1024 {
		t.Errorf("reservedBytes = %d, want 200GB (%d)", evHigh.reservedBytes, 200*1024*1024*1024)
	}

	// Verify nil statfs defaults to RealStatfs
	if evHigh.statfs == nil {
		t.Errorf("evHigh.statfs is nil, want RealStatfs")
	} else {
		stats, err := evHigh.statfs(tempDir)
		if err != nil || stats.TotalBytes <= 0 {
			t.Errorf("evHigh.statfs(tempDir) failed: err=%v, stats=%+v", err, stats)
		}
	}

	if evHigh.config.SampleBuckets != 64 {
		t.Errorf("SampleBuckets = %d, want clamped 64", evHigh.config.SampleBuckets)
	}
	if evHigh.config.BatchSize != 2000 {
		t.Errorf("BatchSize = %d, want clamped 2000", evHigh.config.BatchSize)
	}
	if evHigh.config.BatchDelay != 2*time.Millisecond {
		t.Errorf("BatchDelay = %v, want clamped 2ms", evHigh.config.BatchDelay)
	}

	// 2. Test lower-bound clamping for small positive values
	cfgLow := EvictorConfig{
		MinFreeSpace:    "15%",
		TargetFreeSpace: "25%",
		SampleBuckets:   2,                      // below min 16
		BatchSize:       50,                     // below min 200
		BatchDelay:      100 * time.Microsecond, // below min 500us
	}

	evLow, err := NewEvictor(tempDir, cfgLow, nil)
	if err != nil {
		t.Fatalf("NewEvictor failed: %v", err)
	}

	if evLow.config.SampleBuckets != 16 {
		t.Errorf("SampleBuckets = %d, want clamped 16", evLow.config.SampleBuckets)
	}
	if evLow.config.BatchSize != 200 {
		t.Errorf("BatchSize = %d, want clamped 200", evLow.config.BatchSize)
	}
	if evLow.config.BatchDelay != 500*time.Microsecond {
		t.Errorf("BatchDelay = %v, want clamped 500us", evLow.config.BatchDelay)
	}

	// 3. Test zero / negative fallback to standard defaults (16 buckets, 500 size, 1ms delay)
	cfgZero := EvictorConfig{
		MinFreeSpace:    "15%",
		TargetFreeSpace: "25%",
		SampleBuckets:   0,
		BatchSize:       0,
		BatchDelay:      -5 * time.Millisecond,
	}
	evZero, err := NewEvictor(tempDir, cfgZero, nil)
	if err != nil {
		t.Fatalf("NewEvictor failed: %v", err)
	}
	if evZero.config.SampleBuckets != 16 {
		t.Errorf("SampleBuckets = %d, want default 16", evZero.config.SampleBuckets)
	}
	if evZero.config.BatchSize != 500 {
		t.Errorf("BatchSize = %d, want default 500", evZero.config.BatchSize)
	}
	if evZero.config.BatchDelay != 1*time.Millisecond {
		t.Errorf("BatchDelay = %v, want default 1ms", evZero.config.BatchDelay)
	}
	if evZero.config.CheckInterval != 1*time.Minute {
		t.Errorf("CheckInterval = %v, want default 1m", evZero.config.CheckInterval)
	}
}

func TestEvictor_StopConcurrent(t *testing.T) {
	tempDir := t.TempDir()
	cfg := DefaultEvictorConfig()
	cfg.CheckInterval = 50 * time.Millisecond

	ev, err := NewEvictor(tempDir, cfg, func(path string) (DiskStats, error) {
		return DiskStats{TotalBytes: 1000, FreeBytes: 500}, nil
	})
	if err != nil {
		t.Fatalf("NewEvictor failed: %v", err)
	}

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	ev.Start(ctx)

	// Call Stop() concurrently from 10 goroutines to verify sync.Once safety without panic
	var wg sync.WaitGroup
	for i := 0; i < 10; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			ev.Stop()
		}()
	}
	wg.Wait()
}

func TestEvictor_StopWaitsForDone(t *testing.T) {
	tempDir := t.TempDir()
	cfg := DefaultEvictorConfig()
	cfg.CheckInterval = 20 * time.Millisecond

	ev, err := NewEvictor(tempDir, cfg, func(path string) (DiskStats, error) {
		time.Sleep(10 * time.Millisecond)
		return DiskStats{TotalBytes: 1000, FreeBytes: 500}, nil
	})
	if err != nil {
		t.Fatalf("NewEvictor failed: %v", err)
	}

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	ev.Start(ctx)
	time.Sleep(30 * time.Millisecond)

	ev.Stop()

	// Assert that doneChan is closed synchronously upon return of Stop()
	select {
	case <-ev.doneChan:
		// Success: run() finished and closed doneChan before Stop() unblocked
	default:
		t.Errorf("Stop() returned while doneChan was still open")
	}
}

func TestEvictor_WithMonitoring(t *testing.T) {
	monitoring.Init("casproxy")
	defer monitoring.Shutdown()

	tempDir := t.TempDir()
	cfg := DefaultEvictorConfig()
	cfg.ReservedSpaceGB = 0
	cfg.MinFreeSpace = "50%"
	cfg.TargetFreeSpace = "80%"
	cfg.MinBlobAge = 1 * time.Nanosecond

	var currentFree int64 = 100 * 1024 * 1024
	ev, err := NewEvictor(tempDir, cfg, func(path string) (DiskStats, error) {
		return DiskStats{TotalBytes: 1000 * 1024 * 1024, FreeBytes: currentFree}, nil
	})
	if err != nil {
		t.Fatalf("NewEvictor failed: %v", err)
	}

	// 1. Trigger checkAndEvict when free space is above minFreeSpace (noop)
	currentFree = 600 * 1024 * 1024
	ev.checkAndEvict(context.Background())

	// 2. Trigger checkAndEvict when free space is below minFreeSpace (causes eviction)
	currentFree = 200 * 1024 * 1024
	ev.checkAndEvict(context.Background())

	// 3. Test EvictOnce error branch
	errEv, err := NewEvictor(tempDir, cfg, func(path string) (DiskStats, error) {
		return DiskStats{}, fmt.Errorf("simulated statfs error")
	})
	if err != nil {
		t.Fatalf("NewEvictor error test failed: %v", err)
	}
	if _, _, err := errEv.EvictOnce(context.Background()); err == nil {
		t.Errorf("Expected error from EvictOnce, got nil")
	}
}

func TestEvictor_UpdateConfig(t *testing.T) {
	tempDir := t.TempDir()
	cfg := DefaultEvictorConfig()
	cfg.MinFreeSpace = "15%"
	cfg.TargetFreeSpace = "25%"
	cfg.ReservedSpaceGB = 100

	ev, err := NewEvictor(tempDir, cfg, nil)
	if err != nil {
		t.Fatalf("NewEvictor failed: %v", err)
	}

	// 1. Successful update
	newCfg := cfg
	newCfg.MinFreeSpace = "20%"
	newCfg.TargetFreeSpace = "30%"
	newCfg.ReservedSpaceGB = 150
	newCfg.BatchSize = 1000
	newCfg.BatchDelay = 2 * time.Millisecond
	newCfg.CheckInterval = 30 * time.Second

	if err := ev.UpdateConfig(newCfg); err != nil {
		t.Fatalf("UpdateConfig failed: %v", err)
	}

	confirmed := ev.Config()
	if confirmed.MinFreeSpace != "20%" {
		t.Errorf("MinFreeSpace = %s, want 20%%", confirmed.MinFreeSpace)
	}
	if confirmed.TargetFreeSpace != "30%" {
		t.Errorf("TargetFreeSpace = %s, want 30%%", confirmed.TargetFreeSpace)
	}
	if confirmed.ReservedSpaceGB != 150 {
		t.Errorf("ReservedSpaceGB = %d, want 150", confirmed.ReservedSpaceGB)
	}
	if confirmed.BatchSize != 1000 {
		t.Errorf("BatchSize = %d, want 1000", confirmed.BatchSize)
	}
	if confirmed.BatchDelay != 2*time.Millisecond {
		t.Errorf("BatchDelay = %v, want 2ms", confirmed.BatchDelay)
	}
	if confirmed.CheckInterval != 30*time.Second {
		t.Errorf("CheckInterval = %v, want 30s", confirmed.CheckInterval)
	}

	// 2. Reject invalid min-free-space
	badCfg := newCfg
	badCfg.MinFreeSpace = "not-a-number"
	if err := ev.UpdateConfig(badCfg); err == nil {
		t.Errorf("UpdateConfig with invalid MinFreeSpace succeeded, want error")
	}

	// 3. Reject invalid target-free-space
	badCfg = newCfg
	badCfg.TargetFreeSpace = "invalid-target"
	if err := ev.UpdateConfig(badCfg); err == nil {
		t.Errorf("UpdateConfig with invalid TargetFreeSpace succeeded, want error")
	}

	// 4. Reject min >= target (percentages)
	badCfg = newCfg
	badCfg.MinFreeSpace = "35%"
	badCfg.TargetFreeSpace = "25%"
	if err := ev.UpdateConfig(badCfg); err == nil {
		t.Errorf("UpdateConfig with min >= target succeeded, want error")
	}

	// 5. Reject min >= target (fixed bytes)
	badCfg = newCfg
	badCfg.MinFreeSpace = "500gb"
	badCfg.TargetFreeSpace = "400gb"
	if err := ev.UpdateConfig(badCfg); err == nil {
		t.Errorf("UpdateConfig with fixed min >= target succeeded, want error")
	}

	// Verify original values remained intact after failed updates
	intact := ev.Config()
	if intact.MinFreeSpace != "20%" || intact.TargetFreeSpace != "30%" {
		t.Errorf("Config corrupted after failed updates: %+v", intact)
	}

	// 6. Test clamping of bounds
	clampCfg := newCfg
	clampCfg.SampleBuckets = 999
	clampCfg.BatchSize = 99999
	clampCfg.BatchDelay = 100 * time.Millisecond
	if err := ev.UpdateConfig(clampCfg); err != nil {
		t.Fatalf("UpdateConfig with extreme bounds failed: %v", err)
	}
	clamped := ev.Config()
	if clamped.SampleBuckets != maxSampleBuckets {
		t.Errorf("SampleBuckets = %d, want clamped %d", clamped.SampleBuckets, maxSampleBuckets)
	}
	if clamped.BatchSize != maxBatchSize {
		t.Errorf("BatchSize = %d, want clamped %d", clamped.BatchSize, maxBatchSize)
	}
	if clamped.BatchDelay != maxBatchDelay {
		t.Errorf("BatchDelay = %v, want clamped %v", clamped.BatchDelay, maxBatchDelay)
	}

	clampLowerCfg := newCfg
	clampLowerCfg.SampleBuckets = 2
	clampLowerCfg.BatchSize = 50
	clampLowerCfg.BatchDelay = 50 * time.Microsecond
	if err := ev.UpdateConfig(clampLowerCfg); err != nil {
		t.Fatalf("UpdateConfig with lower bounds failed: %v", err)
	}
	clampedLower := ev.Config()
	if clampedLower.SampleBuckets != minSampleBuckets {
		t.Errorf("SampleBuckets = %d, want clamped %d", clampedLower.SampleBuckets, minSampleBuckets)
	}
	if clampedLower.BatchSize != minBatchSize {
		t.Errorf("BatchSize = %d, want clamped %d", clampedLower.BatchSize, minBatchSize)
	}
	if clampedLower.BatchDelay != minBatchDelay {
		t.Errorf("BatchDelay = %v, want clamped %v", clampedLower.BatchDelay, minBatchDelay)
	}

	// 7. Test defaulting of zero/negative values
	zeroCfg := newCfg
	zeroCfg.CheckInterval = 0
	zeroCfg.SampleBuckets = 0
	zeroCfg.BatchSize = 0
	zeroCfg.BatchDelay = 0
	zeroCfg.LazyTouchInterval = 0
	zeroCfg.MinBlobAge = 0
	if err := ev.UpdateConfig(zeroCfg); err != nil {
		t.Fatalf("UpdateConfig with zero values failed: %v", err)
	}
	defaulted := ev.Config()
	if defaulted.CheckInterval != 1*time.Minute {
		t.Errorf("CheckInterval = %v, want defaulted 1m", defaulted.CheckInterval)
	}
	if defaulted.SampleBuckets != 16 {
		t.Errorf("SampleBuckets = %d, want defaulted 16", defaulted.SampleBuckets)
	}
	if defaulted.BatchSize != 500 {
		t.Errorf("BatchSize = %d, want defaulted 500", defaulted.BatchSize)
	}
	if defaulted.BatchDelay != 1*time.Millisecond {
		t.Errorf("BatchDelay = %v, want defaulted 1ms", defaulted.BatchDelay)
	}
	if defaulted.LazyTouchInterval != 4*time.Hour {
		t.Errorf("LazyTouchInterval = %v, want defaulted 4h", defaulted.LazyTouchInterval)
	}
	if defaulted.MinBlobAge != 10*time.Minute {
		t.Errorf("MinBlobAge = %v, want defaulted 10m", defaulted.MinBlobAge)
	}
}

func TestStorage_UpdateEvictorConfig(t *testing.T) {
	tempDir := t.TempDir()
	store, err := New(tempDir)
	if err != nil {
		t.Fatalf("storage.New failed: %v", err)
	}

	cfg := DefaultEvictorConfig()

	// Test uninitialized evictor error returns
	if err := store.UpdateEvictorConfig(cfg); err == nil {
		t.Errorf("UpdateEvictorConfig on uninitialized store succeeded, want error")
	}
	if _, err := store.EvictorConfig(); err == nil {
		t.Errorf("EvictorConfig on uninitialized store succeeded, want error")
	}

	if err := store.StartEvictor(context.Background(), cfg); err != nil {
		t.Fatalf("StartEvictor failed: %v", err)
	}
	defer store.StopEvictor()

	newCfg := cfg
	newCfg.MinFreeSpace = "18%"
	newCfg.TargetFreeSpace = "28%"
	if err := store.UpdateEvictorConfig(newCfg); err != nil {
		t.Fatalf("store.UpdateEvictorConfig failed: %v", err)
	}

	readCfg, err := store.EvictorConfig()
	if err != nil {
		t.Fatalf("store.EvictorConfig failed: %v", err)
	}
	if readCfg.MinFreeSpace != "18%" || readCfg.TargetFreeSpace != "28%" {
		t.Errorf("store.EvictorConfig returned %+v, want min 18%% / target 28%%", readCfg)
	}
}

func TestEvictor_Run_TickerResetOnConfigUpdate(t *testing.T) {
	tempDir := t.TempDir()
	cfg := DefaultEvictorConfig()
	cfg.CheckInterval = 80 * time.Millisecond
	cfg.MinFreeSpace = "10%"
	cfg.TargetFreeSpace = "20%"

	tickTimes := make(chan time.Time, 20)
	ev, err := NewEvictor(tempDir, cfg, func(path string) (DiskStats, error) {
		tickTimes <- time.Now()
		return DiskStats{TotalBytes: 1000, FreeBytes: 500}, nil
	})
	if err != nil {
		t.Fatalf("NewEvictor failed: %v", err)
	}

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	ev.Start(ctx)

	// Wait for the first tick at the initial 80ms interval
	select {
	case <-tickTimes:
	case <-time.After(1 * time.Second):
		t.Fatalf("timed out waiting for first tick")
	}

	// 1. Update config with same interval: desired == interval ensures ticker is not reset unnecessarily
	sameCfg := ev.Config()
	if err := ev.UpdateConfig(sameCfg); err != nil {
		t.Fatalf("UpdateConfig with same interval failed: %v", err)
	}

	// Wait for next tick
	select {
	case <-tickTimes:
	case <-time.After(1 * time.Second):
		t.Fatalf("timed out waiting for second tick")
	}

	// 2. Update config to a faster interval (20ms)
	fasterCfg := ev.Config()
	fasterCfg.CheckInterval = 20 * time.Millisecond
	if err := ev.UpdateConfig(fasterCfg); err != nil {
		t.Fatalf("UpdateConfig with faster interval failed: %v", err)
	}

	// Verify subsequent ticks fire at the new faster interval
	select {
	case t1 := <-tickTimes:
		select {
		case t2 := <-tickTimes:
			diff := t2.Sub(t1)
			if diff > 60*time.Millisecond {
				t.Errorf("ticker did not speed up after reset: interval was %v, want <= 60ms", diff)
			}
		case <-time.After(500 * time.Millisecond):
			t.Fatalf("timed out waiting for second fast tick")
		}
	case <-time.After(1 * time.Second):
		t.Fatalf("timed out waiting for first fast tick")
	}

	ev.Stop()
}

func TestEvictor_Run_NoTickerResetWhenIntervalUnchanged(t *testing.T) {
	tempDir := t.TempDir()
	cfg := DefaultEvictorConfig()
	cfg.CheckInterval = 120 * time.Millisecond
	cfg.MinFreeSpace = "10%"
	cfg.TargetFreeSpace = "20%"

	var tickCount atomic.Int32
	var workDoneTime atomic.Pointer[time.Time]
	tickTimes := make(chan time.Time, 20)

	ev, err := NewEvictor(tempDir, cfg, func(path string) (DiskStats, error) {
		now := time.Now()
		tickTimes <- now
		if tickCount.Add(1) == 1 {
			// Simulate work taking a significant portion of CheckInterval.
			time.Sleep(90 * time.Millisecond)
			finish := time.Now()
			workDoneTime.Store(&finish)
		}
		return DiskStats{TotalBytes: 1000, FreeBytes: 500}, nil
	})
	if err != nil {
		t.Fatalf("NewEvictor failed: %v", err)
	}

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	ev.Start(ctx)

	// Wait for first tick
	select {
	case <-tickTimes:
	case <-time.After(1 * time.Second):
		t.Fatalf("timed out waiting for first tick")
	}

	// Wait for second tick
	select {
	case t2 := <-tickTimes:
		done := workDoneTime.Load()
		if done == nil {
			t.Fatalf("first tick workDoneTime was not recorded")
		}
		delayAfterWork := t2.Sub(*done)
		// Since CheckInterval was unchanged (120ms), ticker.Reset must NOT have been called.
		// If ticker was mistakenly reset at the end of tick 1 work, tick 2 would take >= 120ms from done.
		// Because ticker was not reset, tick 2 fires at the scheduled 120ms mark, which is only ~30ms after done.
		if delayAfterWork >= 90*time.Millisecond {
			t.Errorf("ticker was mistakenly reset when interval was unchanged: delay after tick 1 work finished was %v, want < 90ms", delayAfterWork)
		}
	case <-time.After(1 * time.Second):
		t.Fatalf("timed out waiting for second tick")
	}

	ev.Stop()
}

func TestEvictor_SnapshotConsistency(t *testing.T) {
	tempDir := t.TempDir()
	cfg := DefaultEvictorConfig()
	cfg.MinFreeSpace = "15%"
	cfg.TargetFreeSpace = "25%"
	cfg.ReservedSpaceGB = 100
	cfg.MinBlobAge = 10 * time.Minute

	ev, err := NewEvictor(tempDir, cfg, func(path string) (DiskStats, error) {
		return DiskStats{TotalBytes: 1000 * 1024 * 1024 * 1024, FreeBytes: 900 * 1024 * 1024 * 1024}, nil
	})
	if err != nil {
		t.Fatalf("NewEvictor failed: %v", err)
	}

	snap1 := ev.snapshot()
	expectedMin1 := int64(float64(900*1024*1024*1024) * 0.15)
	expectedTarget1 := int64(float64(900*1024*1024*1024) * 0.25)
	if snap1.minFreeBytes != expectedMin1 {
		t.Errorf("snap1 minFreeBytes = %d, want %d", snap1.minFreeBytes, expectedMin1)
	}
	if snap1.targetFreeBytes != expectedTarget1 {
		t.Errorf("snap1 targetFreeBytes = %d, want %d", snap1.targetFreeBytes, expectedTarget1)
	}
	if snap1.reservedBytes != 100*1024*1024*1024 {
		t.Errorf("snap1 reservedBytes = %d, want 100GB", snap1.reservedBytes)
	}
	if snap1.minBlobAge != 10*time.Minute {
		t.Errorf("snap1 minBlobAge = %v, want 10m", snap1.minBlobAge)
	}

	// Update configuration while holding the earlier snapshot
	newCfg := cfg
	newCfg.MinFreeSpace = "30%"
	newCfg.TargetFreeSpace = "40%"
	newCfg.ReservedSpaceGB = 200
	newCfg.MinBlobAge = 30 * time.Minute
	if err := ev.UpdateConfig(newCfg); err != nil {
		t.Fatalf("UpdateConfig failed: %v", err)
	}

	// Earlier snapshot must remain completely immutable and consistent
	if snap1.minFreeBytes != expectedMin1 {
		t.Errorf("snap1 minFreeBytes mutated: %d, want %d", snap1.minFreeBytes, expectedMin1)
	}
	if snap1.targetFreeBytes != expectedTarget1 {
		t.Errorf("snap1 targetFreeBytes mutated: %d, want %d", snap1.targetFreeBytes, expectedTarget1)
	}
	if snap1.reservedBytes != 100*1024*1024*1024 {
		t.Errorf("snap1 reservedBytes mutated: %d", snap1.reservedBytes)
	}
	if snap1.minBlobAge != 10*time.Minute {
		t.Errorf("snap1 minBlobAge mutated: %v", snap1.minBlobAge)
	}

	// New snapshot captures the updated values atomically
	snap2 := ev.snapshot()
	expectedMin2 := int64(float64(800*1024*1024*1024) * 0.30)
	expectedTarget2 := int64(float64(800*1024*1024*1024) * 0.40)
	if snap2.minFreeBytes != expectedMin2 {
		t.Errorf("snap2 minFreeBytes = %d, want %d", snap2.minFreeBytes, expectedMin2)
	}
	if snap2.targetFreeBytes != expectedTarget2 {
		t.Errorf("snap2 targetFreeBytes = %d, want %d", snap2.targetFreeBytes, expectedTarget2)
	}
	if snap2.reservedBytes != 200*1024*1024*1024 {
		t.Errorf("snap2 reservedBytes = %d, want 200GB", snap2.reservedBytes)
	}
	if snap2.minBlobAge != 30*time.Minute {
		t.Errorf("snap2 minBlobAge = %v, want 30m", snap2.minBlobAge)
	}
}

func TestEvictStream_BatchPacingAndTargetReached(t *testing.T) {
	tempDir := t.TempDir()
	store, err := New(tempDir)
	if err != nil {
		t.Fatalf("New failed: %v", err)
	}

	now := time.Now()
	for i := 0; i < 5; i++ {
		hash := fmt.Sprintf("11%02x%060x", i, i)
		data := []byte("content")
		if err := store.Write(hash, data); err != nil {
			t.Fatalf("Write failed: %v", err)
		}
		p := store.blobPath(hash)
		mTime := now.Add(-5 * time.Hour)
		_ = os.Chtimes(p, mTime, mTime)
	}

	statfs := func(path string) (DiskStats, error) {
		return DiskStats{TotalBytes: 1000, FreeBytes: 900}, nil
	}

	cfg := DefaultEvictorConfig()
	cfg.ReservedSpaceGB = 0
	ev, err := NewEvictor(tempDir, cfg, statfs)
	if err != nil {
		t.Fatalf("NewEvictor failed: %v", err)
	}

	snap := ev.snapshot()
	snap.batchSize = 2
	snap.batchDelay = 1 * time.Millisecond
	snap.reservedBytes = 0
	flight := ev.newFlight(snap)

	cutoff := now.Add(-1 * time.Hour)
	targetFreeBytes := int64(800)
	reclaimed, deleted, err := flight.evictStream(context.Background(), cutoff, targetFreeBytes, false)
	if err != nil {
		t.Fatalf("evictStream failed: %v", err)
	}
	if deleted != 2 {
		t.Errorf("deleted = %d, want 2 (stopped after reaching target)", deleted)
	}
	if reclaimed <= 0 {
		t.Errorf("reclaimed = %d, want > 0", reclaimed)
	}
}

func TestEvictStream_EmergencyMode(t *testing.T) {
	tempDir := t.TempDir()
	store, err := New(tempDir)
	if err != nil {
		t.Fatalf("New failed: %v", err)
	}

	now := time.Now()
	// Create a large blob (exceeding emergencyBlobSizeThreshold 50MB) but with a recent modTime (newer than cutoff, but older than MinBlobAge)
	hash := "3300" + strings.Repeat("0", 60)
	largeData := make([]byte, emergencyBlobSizeThreshold+1024)
	if err := store.Write(hash, largeData); err != nil {
		t.Fatalf("Write failed: %v", err)
	}
	p := store.blobPath(hash)
	modTime := now.Add(-30 * time.Minute) // older than MinBlobAge (10m), but newer than cutoff (1h ago)
	_ = os.Chtimes(p, modTime, modTime)

	ev, err := NewEvictor(tempDir, DefaultEvictorConfig(), func(string) (DiskStats, error) {
		return DiskStats{TotalBytes: 1000 * 1024 * 1024, FreeBytes: 10 * 1024 * 1024}, nil
	})
	if err != nil {
		t.Fatalf("NewEvictor failed: %v", err)
	}

	flight := ev.newFlight(ev.snapshot())
	cutoff := now.Add(-1 * time.Hour) // cutoff is 1 hour ago. Blob is 30 minutes old!

	// Normal mode (emergencyMode = false): blob should NOT be evicted because modTime is after cutoff
	reclaimed, deleted, err := flight.evictStream(context.Background(), cutoff, 500*1024*1024, false)
	if err != nil {
		t.Fatalf("evictStream failed: %v", err)
	}
	if deleted != 0 || reclaimed != 0 {
		t.Errorf("normal mode deleted = %d, reclaimed = %d; want 0, 0", deleted, reclaimed)
	}

	// Emergency mode (emergencyMode = true): large blob SHOULD be evicted despite being after cutoff
	reclaimed, deleted, err = flight.evictStream(context.Background(), cutoff, 500*1024*1024, true)
	if err != nil {
		t.Fatalf("evictStream emergency mode failed: %v", err)
	}
	if deleted != 1 {
		t.Errorf("emergency mode deleted = %d, want 1", deleted)
	}
	if reclaimed != int64(len(largeData)) {
		t.Errorf("reclaimed = %d, want %d", reclaimed, len(largeData))
	}
}

func TestEvictor_CheckIntervalUpdate(t *testing.T) {
	tempDir := t.TempDir()
	var checkCount atomic.Int32
	cfg := DefaultEvictorConfig()
	cfg.CheckInterval = 15 * time.Millisecond
	ev, err := NewEvictor(tempDir, cfg, func(string) (DiskStats, error) {
		checkCount.Add(1)
		return DiskStats{TotalBytes: 1000, FreeBytes: 900}, nil
	})
	if err != nil {
		t.Fatalf("NewEvictor failed: %v", err)
	}

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	waitForChecks := func(n int32, timeout time.Duration) error {
		start := checkCount.Load()
		deadline := time.Now().Add(timeout)
		for time.Now().Before(deadline) {
			if checkCount.Load() >= start+n {
				return nil
			}
			time.Sleep(2 * time.Millisecond)
		}
		return fmt.Errorf("timeout waiting for %d checks (got %d)", n, checkCount.Load()-start)
	}

	ev.Start(ctx)

	// Wait for at least 1 check at initial 15ms interval.
	if err := waitForChecks(1, 1*time.Second); err != nil {
		t.Fatalf("initial check failed: %v", err)
	}

	// 1. Slow down ticker to 70ms.
	cfg.CheckInterval = 70 * time.Millisecond
	if err := ev.UpdateConfig(cfg); err != nil {
		t.Fatalf("UpdateConfig failed: %v", err)
	}

	// Wait for 2 checks: first to pick up the config change and reset ticker, second at 70ms.
	if err := waitForChecks(2, 2*time.Second); err != nil {
		t.Fatalf("slowdown checks failed: %v", err)
	}

	// 2. Speed up ticker back to 15ms.
	// If `interval = desired` was deleted, interval remained 15ms,
	// so desired (15ms) != interval (15ms) is false, and ticker.Reset is never called,
	// leaving the ticker running at 70ms.
	cfg.CheckInterval = 15 * time.Millisecond
	if err := ev.UpdateConfig(cfg); err != nil {
		t.Fatalf("UpdateConfig failed: %v", err)
	}

	// Wait 1 check to fire and apply ticker.Reset(15ms).
	if err := waitForChecks(1, 2*time.Second); err != nil {
		t.Fatalf("speedup transition check failed: %v", err)
	}

	// After the reset, 3 checks at 15ms take ~45ms.
	// If stuck at 70ms (mutant), 3 checks take ~210ms and timeout at 120ms.
	if err := waitForChecks(3, 120*time.Millisecond); err != nil {
		t.Errorf("expected 3 checks at restored 15ms interval: %v", err)
	}

	// 3. Test that non-positive CheckInterval under lock is safely clamped without panic or error.
	ev.configMu.Lock()
	ev.config.CheckInterval = 0
	ev.configMu.Unlock()
	time.Sleep(30 * time.Millisecond)

	ev.Stop()
}

func TestEvictor_EvictInternal_TargetReclaimBytesExact(t *testing.T) {
	tempDir := t.TempDir()
	now := time.Now()

	cfg := EvictorConfig{
		MinFreeSpace:    "15%",
		TargetFreeSpace: "35%",
		ReservedSpaceGB: 0,
		CheckInterval:   10 * time.Second,
		SampleBuckets:   1,
		BatchSize:       100,
		MinBlobAge:      1 * time.Hour,
	}

	// TotalBytes = 2000, FreeBytes = 200 (10% free space).
	// freePct = 10.0% >= 5.0%, so emergencyMode is false.
	// totalOccupancy = effectiveTotal - effectiveFree = 2000 - 200 = 1800.
	// targetFreeBytes = 35% of 2000 = 700.
	// targetReclaimBytes = targetFreeBytes - effectiveFree = 700 - 200 = 500.
	// stats.totalBytes in sampled bucket = 499 + 1 + 1300 = 1800.
	// targetFraction = 500 / 1800.
	// reclaimTargetVal = int64(1800 * (500 / 1800)) = 500.
	ev, err := NewEvictor(tempDir, cfg, func(path string) (DiskStats, error) {
		return DiskStats{TotalBytes: 2000, FreeBytes: 200}, nil
	})
	if err != nil {
		t.Fatalf("NewEvictor failed: %v", err)
	}

	ev.rand = rand.New(rand.NewSource(42))
	sampledPaths := ev.pickRandomBuckets(1)
	bucketDir := sampledPaths[0]
	if err := os.MkdirAll(bucketDir, 0755); err != nil {
		t.Fatalf("MkdirAll failed: %v", err)
	}

	// File 1: 499 bytes, age 12 hours
	f1 := filepath.Join(bucketDir, "0000000000000000000000000000000000000000000000000000000000000001")
	if err := os.WriteFile(f1, make([]byte, 499), 0644); err != nil {
		t.Fatalf("WriteFile failed: %v", err)
	}
	t1 := now.Add(-12 * time.Hour)
	_ = os.Chtimes(f1, t1, t1)

	// File 2: 1 byte, age 10 hours
	f2 := filepath.Join(bucketDir, "0000000000000000000000000000000000000000000000000000000000000002")
	if err := os.WriteFile(f2, make([]byte, 1), 0644); err != nil {
		t.Fatalf("WriteFile failed: %v", err)
	}
	t2 := now.Add(-10 * time.Hour)
	_ = os.Chtimes(f2, t2, t2)

	// File 3: 1300 bytes, age 5 hours
	f3 := filepath.Join(bucketDir, "0000000000000000000000000000000000000000000000000000000000000003")
	if err := os.WriteFile(f3, make([]byte, 1300), 0644); err != nil {
		t.Fatalf("WriteFile failed: %v", err)
	}
	t3 := now.Add(-5 * time.Hour)
	_ = os.Chtimes(f3, t3, t3)

	// Reset RNG so estimateCutoffAge samples the exact same bucketDir
	ev.rand = rand.New(rand.NewSource(42))

	reclaimed, deleted, err := ev.EvictOnce(context.Background())
	if err != nil {
		t.Fatalf("EvictOnce failed: %v", err)
	}

	// With exact targetReclaimBytes (500):
	// - Bin 12 has 499 bytes: accumulated (499) < reclaimTargetVal (500).
	// - Bin 10 has 1 byte: accumulated (499 + 1 = 500) >= reclaimTargetVal (500).
	//   cutoffHours = 10, cutoff = now - 10h.
	// f1 (12h) and f2 (10h) are evicted (deleted = 2, reclaimed = 500). f3 (5h) is retained.
	//
	// If targetReclaimBytes was mutated to 499 (-1):
	// accumulated (499) >= 499 at bin 12, cutoffHours becomes 12, so only f1 is evicted (deleted = 1, reclaimed = 499).
	//
	// If targetReclaimBytes was mutated to 501 (+1):
	// accumulated (500) < 501 at bin 10, cutoffHours becomes 5, and all 3 files are evicted (deleted = 3, reclaimed = 1800).
	if deleted != 2 {
		t.Errorf("deleted = %d, want 2", deleted)
	}
	if reclaimed != 500 {
		t.Errorf("reclaimed = %d, want 500", reclaimed)
	}
	if _, err := os.Stat(f1); !os.IsNotExist(err) {
		t.Errorf("expected f1 to be deleted, err: %v", err)
	}
	if _, err := os.Stat(f2); !os.IsNotExist(err) {
		t.Errorf("expected f2 to be deleted, err: %v", err)
	}
	if _, err := os.Stat(f3); err != nil {
		t.Errorf("expected f3 to exist, err: %v", err)
	}
}

func TestEvictor_DeriveCutoffAge_ExactTargetBoundary(t *testing.T) {
	hist := make([]int64, 30*24+1)
	hist[12] = 499
	hist[10] = 1
	hist[5] = 1300
	stats := sampleStats{
		histogram:  hist,
		totalBytes: 1800,
		totalCount: 3,
		maxDays:    30,
	}

	ev := &Evictor{config: DefaultEvictorConfig()}
	flight := ev.newFlight(ev.snapshot())
	now := time.Now()

	// effectiveTotal = 2000, effectiveFree = 200 => occupancy = 1800.
	// reclaimTarget = 500 => targetFraction = 500/1800.
	// reclaimTargetVal = int64(1800 * (500/1800)) = 500.
	// Bin 12 (499) is < 500.
	// Bin 10 (499 + 1 = 500) is >= 500 => cutoffHours = 10.
	// If reclaimTargetVal is mutated by -1 (reclaimTargetVal = 499):
	// Bin 12 (499) is >= 499 => cutoffHours = 12 (fails!).
	_, cutoffHours := flight.deriveCutoffAge(stats, 2000, 200, 500, now)
	if cutoffHours != 10 {
		t.Errorf("deriveCutoffAge cutoffHours = %d, want 10", cutoffHours)
	}

	// Boundary check for reclaimTarget = 499:
	_, cutoffHours499 := flight.deriveCutoffAge(stats, 2000, 200, 499, now)
	if cutoffHours499 != 12 {
		t.Errorf("deriveCutoffAge cutoffHours for 499 = %d, want 12", cutoffHours499)
	}

	// Boundary check for reclaimTarget = 501:
	_, cutoffHours501 := flight.deriveCutoffAge(stats, 2000, 200, 501, now)
	if cutoffHours501 != 5 {
		t.Errorf("deriveCutoffAge cutoffHours for 501 = %d, want 5", cutoffHours501)
	}
}

func TestEvictor_Snapshot_Fields(t *testing.T) {
	tempDir := t.TempDir()
	cfg := EvictorConfig{
		MinFreeSpace:    "20%",
		TargetFreeSpace: "30%",
		ReservedSpaceGB: 50,
		MinBlobAge:      10 * time.Minute,
	}
	stats := DiskStats{TotalBytes: 500 * 1024 * 1024 * 1024, FreeBytes: 400 * 1024 * 1024 * 1024}
	ev, err := NewEvictor(tempDir, cfg, func(string) (DiskStats, error) {
		return stats, nil
	})
	if err != nil {
		t.Fatalf("NewEvictor failed: %v", err)
	}

	snap := ev.snapshot(stats)
	expectedMin := int64(float64(450*1024*1024*1024) * 0.20)
	expectedTarget := int64(float64(450*1024*1024*1024) * 0.30)
	if snap.minFreeBytes != expectedMin {
		t.Errorf("snap.minFreeBytes = %d, want %d", snap.minFreeBytes, expectedMin)
	}
	if snap.targetFreeBytes != expectedTarget {
		t.Errorf("snap.targetFreeBytes = %d, want %d", snap.targetFreeBytes, expectedTarget)
	}
	if snap.reservedBytes != 50*1024*1024*1024 {
		t.Errorf("snap.reservedBytes = %d, want 50GB", snap.reservedBytes)
	}
	if snap.minBlobAge != 10*time.Minute {
		t.Errorf("snap.minBlobAge = %v, want 10m", snap.minBlobAge)
	}
}
