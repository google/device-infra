package storage

import (
	"context"
	"fmt"
	"math/rand"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"syscall"
	"time"

	log "github.com/golang/glog"
	"github.com/google/device-infra/src/devtools/rbe/common/monitoring"
)

// EvictorConfig holds configuration parameters for the storage evictor.
type EvictorConfig struct {
	// MinFreeSpace is the threshold below which eviction triggers (e.g. "15%" or "400gb").
	MinFreeSpace string
	// TargetFreeSpace is the threshold at which eviction concludes (e.g. "25%" or "800gb").
	TargetFreeSpace string
	// ReservedSpaceGB is a fixed storage buffer (in GB) excluded from cache space calculations.
	ReservedSpaceGB int64
	// CheckInterval is the polling interval for disk capacity checks.
	CheckInterval time.Duration
	// SampleBuckets is the number of random leaf directories sampled to estimate cutoff age.
	SampleBuckets int
	// BatchSize is the number of unlinks per batch before yielding execution.
	BatchSize int
	// BatchDelay is the cooperative pause duration between unlink batches.
	BatchDelay time.Duration
	// LazyTouchInterval is the minimum age before touch updates mtime on read hit.
	LazyTouchInterval time.Duration
	// MinBlobAge is the grace period protecting newly written/renamed blobs from eviction.
	MinBlobAge time.Duration
}

// DefaultEvictorConfig returns the default configuration for the storage evictor per the design doc.
func DefaultEvictorConfig() EvictorConfig {
	return EvictorConfig{
		MinFreeSpace:      "15%",
		TargetFreeSpace:   "25%",
		ReservedSpaceGB:   100,
		CheckInterval:     1 * time.Minute,
		SampleBuckets:     16,
		BatchSize:         500,
		BatchDelay:        1 * time.Millisecond,
		LazyTouchInterval: 4 * time.Hour,
		MinBlobAge:        10 * time.Minute,
	}
}

// SpaceThreshold represents a parsed storage threshold (either percentage or fixed byte size).
type SpaceThreshold struct {
	IsPercent bool
	Percent   float64
	Bytes     int64
}

// ParseSpaceThreshold parses a space threshold string like "15%", "15.0%", "400gb", "500GB", "2tb", "1048576b".
//
// Formats supported:
//  1. Explicit percentages: e.g. "15%", "15.5%", "25.0%" (must include the '%' character).
//  2. Explicit size units: e.g. "400gb", "500gib", "2tb", "100mb", "1024kb", "1048576b".
//  3. Raw integer byte values: e.g. "1073741824".
//
// Unadorned floats or ambiguous numbers without '%' or unit suffixes (e.g. "15.5") are rejected.
func ParseSpaceThreshold(s string) (SpaceThreshold, error) {
	trimmed := strings.TrimSpace(strings.ToLower(s))
	if trimmed == "" {
		return SpaceThreshold{}, fmt.Errorf("empty space threshold string")
	}

	// 1. Explicit percentage format: must end in '%'
	if strings.HasSuffix(trimmed, "%") {
		numStr := strings.TrimSpace(strings.TrimSuffix(trimmed, "%"))
		val, err := strconv.ParseFloat(numStr, 64)
		if err != nil || val < 0 || val > 100 {
			return SpaceThreshold{}, fmt.Errorf("invalid percentage threshold %q: must be between 0%% and 100%%", s)
		}
		return SpaceThreshold{IsPercent: true, Percent: val}, nil
	}

	// 2. Explicit unit suffix formats: tb, gb, mb, kb, b
	multipliers := []struct {
		suffix string
		mult   int64
	}{
		{"tb", 1024 * 1024 * 1024 * 1024},
		{"tib", 1024 * 1024 * 1024 * 1024},
		{"t", 1024 * 1024 * 1024 * 1024},
		{"gb", 1024 * 1024 * 1024},
		{"gib", 1024 * 1024 * 1024},
		{"g", 1024 * 1024 * 1024},
		{"mb", 1024 * 1024},
		{"mib", 1024 * 1024},
		{"m", 1024 * 1024},
		{"kb", 1024},
		{"kib", 1024},
		{"k", 1024},
		{"b", 1},
	}

	for _, m := range multipliers {
		if strings.HasSuffix(trimmed, m.suffix) {
			numStr := strings.TrimSpace(strings.TrimSuffix(trimmed, m.suffix))
			val, err := strconv.ParseFloat(numStr, 64)
			if err != nil || val < 0 {
				return SpaceThreshold{}, fmt.Errorf("invalid size threshold %q: %w", s, err)
			}
			return SpaceThreshold{IsPercent: false, Bytes: int64(val * float64(m.mult))}, nil
		}
	}

	return SpaceThreshold{}, fmt.Errorf("ambiguous space threshold %q: specify '%%' for percentage (e.g. '15%%') or a size unit (e.g. '400gb', '100mb', '1048576b')", s)
}

// Evaluate computes the concrete byte threshold given the effective total volume capacity.
func (t SpaceThreshold) Evaluate(effectiveTotalBytes int64) int64 {
	if t.IsPercent {
		return int64(float64(effectiveTotalBytes) * (t.Percent / 100.0))
	}
	return t.Bytes
}

// DiskStats represents total and available bytes on the storage filesystem.
type DiskStats struct {
	TotalBytes int64
	FreeBytes  int64
}

// StatfsFunc defines a function signature for querying filesystem capacity metrics.
type StatfsFunc func(path string) (DiskStats, error)

// RealStatfs queries live disk capacity using the POSIX statfs syscall.
func RealStatfs(path string) (DiskStats, error) {
	var stat syscall.Statfs_t
	if err := syscall.Statfs(path, &stat); err != nil {
		return DiskStats{}, err
	}
	total := int64(stat.Blocks) * int64(stat.Bsize)
	free := int64(stat.Bavail) * int64(stat.Bsize)
	return DiskStats{TotalBytes: total, FreeBytes: free}, nil
}

// ComputeEffectiveSpace calculates effective total and free space after deducting the reserved storage buffer.
func ComputeEffectiveSpace(stats DiskStats, reservedBytes int64) (effectiveTotal int64, effectiveFree int64, freePct float64) {
	effectiveTotal = stats.TotalBytes - reservedBytes
	if effectiveTotal < 1 {
		effectiveTotal = 1
	}
	effectiveFree = stats.FreeBytes - reservedBytes
	if effectiveFree < 0 {
		effectiveFree = 0
	}
	freePct = (float64(effectiveFree) / float64(effectiveTotal)) * 100.0
	return
}

// isCandidateBlob checks whether a directory entry represents a valid, non-temporary CAS blob.
// It filters out temporary files (starting with '.' or non-64 hex length) and subdirectories in O(1).
func isCandidateBlob(name string, isDir bool) bool {
	return !isDir && len(name) == 64 && name[0] != '.'
}

// isBlobEligible checks whether a candidate blob has exceeded the grace period without locking.
func isBlobEligible(modTime time.Time, now time.Time, minBlobAge time.Duration) bool {
	return now.Sub(modTime) >= minBlobAge
}

// EvictionStats holds runtime metrics and history of storage eviction passes.
type EvictionStats struct {
	IsEvicting          bool
	LastEvictionTime    time.Time
	LastDuration        time.Duration
	LastReclaimedBytes  int64
	LastEvictedFiles    int64
	TotalEvictionRuns   int64
	TotalReclaimedBytes int64
	TotalEvictedFiles   int64
}

// Evictor manages disk space monitoring and probabilistic LRU cache eviction.
type Evictor struct {
	rootDir         string
	config          EvictorConfig
	minThreshold    SpaceThreshold
	targetThreshold SpaceThreshold
	reservedBytes   int64
	statfs          StatfsFunc

	configMu sync.RWMutex

	isEvicting atomic.Bool
	stopOnce   sync.Once
	stopChan   chan struct{}
	doneChan   chan struct{}
	maxAgeDays int

	statsMu             sync.RWMutex
	lastEvictionTime    time.Time
	lastDuration        time.Duration
	lastReclaimedBytes  int64
	lastEvictedFiles    int64
	totalEvictionRuns   int64
	totalReclaimedBytes int64
	totalEvictedFiles   int64

	randMu sync.Mutex
	rand   *rand.Rand
}

// NewEvictor creates and initializes a new Evictor.
func NewEvictor(rootDir string, cfg EvictorConfig, statfs StatfsFunc) (*Evictor, error) {
	minThresh, err := ParseSpaceThreshold(cfg.MinFreeSpace)
	if err != nil {
		return nil, fmt.Errorf("invalid min-free-space: %w", err)
	}

	targetThresh, err := ParseSpaceThreshold(cfg.TargetFreeSpace)
	if err != nil {
		return nil, fmt.Errorf("invalid target-free-space: %w", err)
	}

	if statfs == nil {
		statfs = RealStatfs
	}

	if cfg.CheckInterval <= 0 {
		cfg.CheckInterval = 1 * time.Minute
	}
	if cfg.SampleBuckets <= 0 {
		cfg.SampleBuckets = 16
	} else if cfg.SampleBuckets < minSampleBuckets {
		cfg.SampleBuckets = minSampleBuckets
	} else if cfg.SampleBuckets > maxSampleBuckets {
		cfg.SampleBuckets = maxSampleBuckets
	}
	if cfg.BatchSize <= 0 {
		cfg.BatchSize = 500
	} else if cfg.BatchSize < minBatchSize {
		cfg.BatchSize = minBatchSize
	} else if cfg.BatchSize > maxBatchSize {
		cfg.BatchSize = maxBatchSize
	}
	if cfg.BatchDelay <= 0 {
		cfg.BatchDelay = 1 * time.Millisecond
	} else if cfg.BatchDelay < minBatchDelay {
		cfg.BatchDelay = minBatchDelay
	} else if cfg.BatchDelay > maxBatchDelay {
		cfg.BatchDelay = maxBatchDelay
	}
	if cfg.LazyTouchInterval <= 0 {
		cfg.LazyTouchInterval = 4 * time.Hour
	}
	if cfg.MinBlobAge <= 0 {
		cfg.MinBlobAge = 10 * time.Minute
	}

	return &Evictor{
		rootDir:         rootDir,
		config:          cfg,
		minThreshold:    minThresh,
		targetThreshold: targetThresh,
		reservedBytes:   cfg.ReservedSpaceGB * 1024 * 1024 * 1024,
		statfs:          statfs,
		maxAgeDays:      defaultMaxAgeDays,
		stopChan:        make(chan struct{}),
		doneChan:        make(chan struct{}),
		rand:            rand.New(rand.NewSource(time.Now().UnixNano())),
	}, nil
}

// Start launches the background eviction ticker loop.
func (e *Evictor) Start(ctx context.Context) {
	go e.run(ctx)
}

// Stop stops the background eviction loop and waits for any in-flight iteration to complete.
// It is thread-safe and can be called reentrantly or concurrently by multiple goroutines.
func (e *Evictor) Stop() {
	e.stopOnce.Do(func() {
		close(e.stopChan)
	})
	<-e.doneChan
}

// run executes the periodic polling loop.
func (e *Evictor) run(ctx context.Context) {
	defer close(e.doneChan)
	e.configMu.RLock()
	interval := e.config.CheckInterval
	e.configMu.RUnlock()

	ticker := time.NewTicker(interval)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return
		case <-e.stopChan:
			return
		case <-ticker.C:
			e.checkAndEvict(ctx)

			// Check if CheckInterval was updated and reset ticker for subsequent cycles.
			e.configMu.RLock()
			desired := e.config.CheckInterval
			e.configMu.RUnlock()
			if desired <= 0 {
				desired = 1 * time.Minute
			}
			if desired != interval {
				ticker.Reset(desired)
				interval = desired
			}
		}
	}
}

// snapshotConfig captures an immutable point-in-time snapshot of the evictor's
// configuration for an eviction flight, with thresholds pre-calculated into concrete byte scalars.
type snapshotConfig struct {
	minFreeBytes    int64
	targetFreeBytes int64
	minBlobAge      time.Duration
	sampleBuckets   int
	batchSize       int
	batchDelay      time.Duration
	reservedBytes   int64
}

func (e *Evictor) snapshot(stats ...DiskStats) snapshotConfig {
	var st DiskStats
	if len(stats) > 0 {
		st = stats[0]
	} else if e.statfs != nil {
		var err error
		st, err = e.statfs(e.rootDir)
		if err != nil {
			st = DiskStats{TotalBytes: 1000 * 1024 * 1024 * 1024, FreeBytes: 500 * 1024 * 1024 * 1024}
		}
	}

	e.configMu.RLock()
	defer e.configMu.RUnlock()

	effectiveTotal, _, _ := ComputeEffectiveSpace(st, e.reservedBytes)
	return snapshotConfig{
		minFreeBytes:    e.minThreshold.Evaluate(effectiveTotal),
		targetFreeBytes: e.targetThreshold.Evaluate(effectiveTotal),
		minBlobAge:      e.config.MinBlobAge,
		sampleBuckets:   e.config.SampleBuckets,
		batchSize:       e.config.BatchSize,
		batchDelay:      e.config.BatchDelay,
		reservedBytes:   e.reservedBytes,
	}
}

// evictionFlight encapsulates the execution of a single eviction run.
// It is ephemeral and scoped to the goroutine performing the flight, guaranteeing that
// all phases operate consistently on the same immutable configuration snapshot.
type evictionFlight struct {
	e   *Evictor
	cfg snapshotConfig
}

func (e *Evictor) newFlight(cfg snapshotConfig) *evictionFlight {
	return &evictionFlight{e: e, cfg: cfg}
}

// isEligible checks whether a candidate blob has exceeded the grace period using the flight snapshot.
func (f *evictionFlight) isEligible(modTime, now time.Time) bool {
	return isBlobEligible(modTime, now, f.cfg.minBlobAge)
}

// checkAndEvict is the background loop's entry point. It delegates to
// EvictIfNeeded and discards the outcome, which the loop has no use for.
func (e *Evictor) checkAndEvict(ctx context.Context) {
	_, _, _ = e.EvictIfNeeded(ctx)
}

// EvictOnce executes a single eviction flight to restore free space above TargetFreeSpace.
// It is thread-safe and protected by an atomic single-flight gate.
func (e *Evictor) EvictOnce(ctx context.Context) (reclaimedBytes int64, filesDeleted int, err error) {
	stats, err := e.statfs(e.rootDir)
	if err != nil {
		return 0, 0, fmt.Errorf("statfs failed: %w", err)
	}
	flight := e.newFlight(e.snapshot(stats))
	return flight.run(ctx)
}

func (f *evictionFlight) run(ctx context.Context) (reclaimedBytes int64, filesDeleted int, err error) {
	e := f.e
	// Single-Flight Gate: ensure only one eviction flight runs at any given time.
	if !e.isEvicting.CompareAndSwap(false, true) {
		return 0, 0, nil // Another eviction flight is already active
	}
	defer e.isEvicting.Store(false)

	startTime := time.Now()
	stats, err := e.statfs(e.rootDir)
	if err != nil {
		monitoring.RecordEvictionRun("error", 0, 0, time.Since(startTime))
		return 0, 0, fmt.Errorf("statfs failed: %w", err)
	}

	effectiveTotal, effectiveFree, freePct := ComputeEffectiveSpace(stats, f.cfg.reservedBytes)
	monitoring.RecordStorageUsage(effectiveTotal, effectiveFree)

	if effectiveFree >= f.cfg.targetFreeBytes {
		monitoring.RecordEvictionRun("noop", 0, 0, time.Since(startTime))
		return 0, 0, nil // Already sufficient space
	}

	targetReclaimBytes := f.cfg.targetFreeBytes - effectiveFree
	emergencyMode := freePct < 5.0

	// 1. Estimate cutoff age from random bucket sampling
	cutoff := f.estimateCutoffAge(ctx, effectiveTotal, effectiveFree, targetReclaimBytes)

	// 2. Perform lock-free paced streaming unlinks across sharded leaf buckets
	reclaimedBytes, filesDeleted, err = f.evictStream(ctx, cutoff, f.cfg.targetFreeBytes, emergencyMode)
	duration := time.Since(startTime)

	evictionStatus := "evicted"
	if err != nil {
		evictionStatus = "error"
	}
	monitoring.RecordEvictionRun(evictionStatus, reclaimedBytes, int64(filesDeleted), duration)

	e.statsMu.Lock()
	e.lastEvictionTime = startTime
	e.lastDuration = duration
	e.lastReclaimedBytes = reclaimedBytes
	e.lastEvictedFiles = int64(filesDeleted)
	e.totalEvictionRuns++
	e.totalReclaimedBytes += reclaimedBytes
	e.totalEvictedFiles += int64(filesDeleted)
	e.statsMu.Unlock()

	return reclaimedBytes, filesDeleted, err
}

// Stats returns a copy of eviction runtime metrics.
func (e *Evictor) Stats() EvictionStats {
	e.statsMu.RLock()
	defer e.statsMu.RUnlock()
	return EvictionStats{
		IsEvicting:          e.isEvicting.Load(),
		LastEvictionTime:    e.lastEvictionTime,
		LastDuration:        e.lastDuration,
		LastReclaimedBytes:  e.lastReclaimedBytes,
		LastEvictedFiles:    e.lastEvictedFiles,
		TotalEvictionRuns:   e.totalEvictionRuns,
		TotalReclaimedBytes: e.totalReclaimedBytes,
		TotalEvictedFiles:   e.totalEvictedFiles,
	}
}

// Config returns the active evictor configuration.
func (e *Evictor) Config() EvictorConfig {
	e.configMu.RLock()
	defer e.configMu.RUnlock()
	return e.config
}

// UpdateConfig dynamically updates the evictor configuration at runtime.
func (e *Evictor) UpdateConfig(newCfg EvictorConfig) error {
	minThresh, err := ParseSpaceThreshold(newCfg.MinFreeSpace)
	if err != nil {
		return fmt.Errorf("invalid min-free-space: %w", err)
	}

	targetThresh, err := ParseSpaceThreshold(newCfg.TargetFreeSpace)
	if err != nil {
		return fmt.Errorf("invalid target-free-space: %w", err)
	}

	if minThresh.IsPercent && targetThresh.IsPercent && minThresh.Percent >= targetThresh.Percent {
		return fmt.Errorf("min-free-space (%.1f%%) must be strictly less than target-free-space (%.1f%%)", minThresh.Percent, targetThresh.Percent)
	}
	if !minThresh.IsPercent && !targetThresh.IsPercent && minThresh.Bytes >= targetThresh.Bytes {
		return fmt.Errorf("min-free-space (%d bytes) must be strictly less than target-free-space (%d bytes)", minThresh.Bytes, targetThresh.Bytes)
	}

	if newCfg.CheckInterval <= 0 {
		newCfg.CheckInterval = 1 * time.Minute
	}
	if newCfg.SampleBuckets <= 0 {
		newCfg.SampleBuckets = 16
	} else if newCfg.SampleBuckets < minSampleBuckets {
		newCfg.SampleBuckets = minSampleBuckets
	} else if newCfg.SampleBuckets > maxSampleBuckets {
		newCfg.SampleBuckets = maxSampleBuckets
	}
	if newCfg.BatchSize <= 0 {
		newCfg.BatchSize = 500
	} else if newCfg.BatchSize < minBatchSize {
		newCfg.BatchSize = minBatchSize
	} else if newCfg.BatchSize > maxBatchSize {
		newCfg.BatchSize = maxBatchSize
	}
	if newCfg.BatchDelay <= 0 {
		newCfg.BatchDelay = 1 * time.Millisecond
	} else if newCfg.BatchDelay < minBatchDelay {
		newCfg.BatchDelay = minBatchDelay
	} else if newCfg.BatchDelay > maxBatchDelay {
		newCfg.BatchDelay = maxBatchDelay
	}
	if newCfg.LazyTouchInterval <= 0 {
		newCfg.LazyTouchInterval = 4 * time.Hour
	}
	if newCfg.MinBlobAge <= 0 {
		newCfg.MinBlobAge = 10 * time.Minute
	}

	reservedBytes := newCfg.ReservedSpaceGB * 1024 * 1024 * 1024

	e.configMu.Lock()
	e.config = newCfg
	e.minThreshold = minThresh
	e.targetThreshold = targetThresh
	e.reservedBytes = reservedBytes
	e.configMu.Unlock()

	return nil
}

const (
	// defaultMaxAgeDays defines the initial tracking horizon for the age histogram (60 days).
	defaultMaxAgeDays = 60
	// maxAllowedAgeDays defines the upper ceiling for automatic horizon adjustments (365 days).
	maxAllowedAgeDays = 365
	// emergencyBlobSizeThreshold defines the minimum size (50MB) for blobs prioritized during emergency eviction.
	emergencyBlobSizeThreshold = 50 * 1024 * 1024

	// minSampleBuckets and maxSampleBuckets define the safe operational range for histogram sampling.
	minSampleBuckets = 16
	maxSampleBuckets = 64

	// minBatchSize and maxBatchSize define the safe range of unlinks per batch.
	minBatchSize = 200
	maxBatchSize = 2000

	// minBatchDelay and maxBatchDelay define the safe range of cooperative sleep pauses.
	minBatchDelay = 500 * time.Microsecond
	maxBatchDelay = 2 * time.Millisecond

	// gib is bytes in a GiB, used for log formatting.
	gib = 1024.0 * 1024.0 * 1024.0
)

// sampleStats holds aggregated metrics from histogram sampling.
type sampleStats struct {
	histogram   []int64
	totalBytes  int64
	totalCount  int
	maxDays     int
	bucketCount int
	duration    time.Duration
}

// estimateCutoffAge samples K random leaf buckets to construct an age histogram and derive T_cutoff.
func (f *evictionFlight) estimateCutoffAge(ctx context.Context, effectiveTotal, effectiveFree, targetReclaimBytes int64) time.Time {
	now := time.Now()
	minBlobAge := f.cfg.minBlobAge

	stats := f.buildAgeHistogram(now)

	// Fallback to MinBlobAge grace period if no candidate samples were found in sampled buckets.
	if stats.totalBytes == 0 || stats.totalCount == 0 {
		log.InfoContextf(ctx, "Storage eviction histogram sampling: 0 candidate samples found across %d buckets (scan took %v). Falling back to MinBlobAge cutoff (%v)",
			stats.bucketCount, stats.duration, now.Add(-minBlobAge))
		return now.Add(-minBlobAge)
	}

	cutoff, cutoffHours := f.deriveCutoffAge(stats, effectiveTotal, effectiveFree, targetReclaimBytes, now)
	f.e.logAndAdjustHorizon(ctx, stats, cutoff, cutoffHours)
	return cutoff
}

// buildAgeHistogram walks sampled leaf buckets and bins candidate blob sizes by age.
func (f *evictionFlight) buildAgeHistogram(now time.Time) sampleStats {
	start := time.Now()
	maxDays := f.e.maxAgeDays
	if maxDays <= 0 {
		maxDays = defaultMaxAgeDays
	}
	maxHours := maxDays * 24
	histogram := make([]int64, maxHours+1)

	var totalBytes int64
	var totalCount int

	sampleBuckets := f.cfg.sampleBuckets

	bucketPaths := f.e.pickRandomBuckets(sampleBuckets)
	for _, bPath := range bucketPaths {
		entries, err := os.ReadDir(bPath)
		if err != nil {
			continue
		}

		for _, entry := range entries {
			if !isCandidateBlob(entry.Name(), entry.IsDir()) {
				continue
			}

			info, err := entry.Info()
			if err != nil {
				continue
			}

			// Exclude blobs currently in their grace period from the histogram
			if !f.isEligible(info.ModTime(), now) {
				continue
			}

			age := now.Sub(info.ModTime())
			hour := int(age.Hours())
			if hour < 0 {
				hour = 0
			} else if hour > maxHours {
				hour = maxHours
			}

			histogram[hour] += info.Size()
			totalBytes += info.Size()
			totalCount++
		}
	}

	return sampleStats{
		histogram:   histogram,
		totalBytes:  totalBytes,
		totalCount:  totalCount,
		maxDays:     maxDays,
		bucketCount: len(bucketPaths),
		duration:    time.Since(start),
	}
}

// deriveCutoffAge calculates the age threshold needed to reclaim targetReclaimBytes from histogram.
func (f *evictionFlight) deriveCutoffAge(stats sampleStats, effectiveTotal, effectiveFree, reclaimTarget int64, now time.Time) (time.Time, int) {
	totalOccupancy := effectiveTotal - effectiveFree
	if totalOccupancy < 1 {
		totalOccupancy = 1
	}
	targetFraction := float64(reclaimTarget) / float64(totalOccupancy)
	if targetFraction > 1.0 {
		targetFraction = 1.0
	}
	if targetFraction < 0.05 {
		targetFraction = 0.05
	}

	reclaimTargetVal := int64(float64(stats.totalBytes) * targetFraction)
	var accumulated int64
	cutoffHours := -1

	// Accumulate from oldest bin (index maxHours = >= maxDays) down to newest bin (index 0)
	for h := len(stats.histogram) - 1; h >= 0; h-- {
		accumulated += stats.histogram[h]
		if accumulated >= reclaimTargetVal {
			cutoffHours = h
			break
		}
	}

	minBlobAge := f.cfg.minBlobAge

	maxHours := stats.maxDays * 24
	var cutoff time.Time
	if cutoffHours == -1 {
		cutoff = now.Add(-minBlobAge)
	} else if cutoffHours >= maxHours {
		cutoff = now.Add(-time.Duration(maxHours) * time.Hour)
	} else {
		cutoff = now.Add(-time.Duration(cutoffHours) * time.Hour)
	}

	return cutoff, cutoffHours
}

// logAndAdjustHorizon logs tail age distributions and auto-adjusts maxAgeDays on overflow.
func (e *Evictor) logAndAdjustHorizon(ctx context.Context, stats sampleStats, cutoff time.Time, cutoffHours int) {
	maxDays := stats.maxDays
	maxHours := maxDays * 24

	dayMinus2Bytes := sumHistogramRange(stats.histogram, (maxDays-2)*24, (maxDays-1)*24)
	dayMinus1Bytes := sumHistogramRange(stats.histogram, (maxDays-1)*24, maxDays*24)
	overflowBytes := stats.histogram[maxHours]

	log.InfoContextf(ctx, "Storage eviction histogram sampling complete: %d candidate blobs (%.2f GB across %d buckets, %d-day histogram). Sampling scan took %v. Tail days [%dd: %.2f GB, %dd: %.2f GB, >%dd: %.2f GB]. Derived T_cutoff = %v (%d hours ago)",
		stats.totalCount, float64(stats.totalBytes)/gib, stats.bucketCount, maxDays, stats.duration,
		maxDays-1, float64(dayMinus2Bytes)/gib,
		maxDays, float64(dayMinus1Bytes)/gib,
		maxDays, float64(overflowBytes)/gib, cutoff, cutoffHours)

	if stats.totalBytes > 0 && float64(overflowBytes)/float64(stats.totalBytes) > 0.5 {
		if e.maxAgeDays < maxAllowedAgeDays {
			newMaxDays := e.maxAgeDays * 2
			if newMaxDays > maxAllowedAgeDays {
				newMaxDays = maxAllowedAgeDays
			}
			log.InfoContextf(ctx, "Storage eviction tail overflow: oldest bin (>%d days) holds %.1f%% of sampled data (%.2f / %.2f GB). Auto-adjusting maxAgeDays from %d to %d days for subsequent cycles.",
				maxDays, (float64(overflowBytes)/float64(stats.totalBytes))*100.0, float64(overflowBytes)/gib, float64(stats.totalBytes)/gib, e.maxAgeDays, newMaxDays)
			e.maxAgeDays = newMaxDays
		} else {
			log.WarningContextf(ctx, "Storage eviction tail overflow: oldest bin (>%d days) holds %.1f%% of sampled data (%.2f / %.2f GB) and maxAgeDays is at ceiling (%d days).",
				maxDays, (float64(overflowBytes)/float64(stats.totalBytes))*100.0, float64(overflowBytes)/gib, float64(stats.totalBytes)/gib, maxAllowedAgeDays)
		}
	}
}

// sumHistogramRange computes the total byte size across a half-open range of hourly histogram bins [startHour, endHour).
func sumHistogramRange(hist []int64, startHour, endHour int) int64 {
	if startHour < 0 {
		startHour = 0
	}
	maxHours := len(hist) - 1
	if endHour > maxHours {
		endHour = maxHours
	}
	var total int64
	for h := startHour; h < endHour; h++ {
		total += hist[h]
	}
	return total
}

// MaxAgeDays returns the current histogram age tracking horizon in days.
func (e *Evictor) MaxAgeDays() int {
	return e.maxAgeDays
}

// SetMaxAgeDays sets the histogram age tracking horizon in days.
func (e *Evictor) SetMaxAgeDays(days int) {
	e.maxAgeDays = days
}

// evictStream streams through leaf buckets, unlinking files matching eviction criteria.
func (f *evictionFlight) evictStream(ctx context.Context, cutoff time.Time, targetFreeBytes int64, emergencyMode bool) (int64, int, error) {
	now := time.Now()
	batchSize := f.cfg.batchSize
	batchDelay := f.cfg.batchDelay
	reservedBytes := f.cfg.reservedBytes

	var totalReclaimed int64
	var totalDeleted int
	var unlinksInBatch int

	// Random permutation of all 65,536 bucket paths
	bucketPerm := f.e.getAllShardedBucketPermutation()

	for _, relDir := range bucketPerm {
		select {
		case <-ctx.Done():
			return totalReclaimed, totalDeleted, ctx.Err()
		case <-f.e.stopChan:
			return totalReclaimed, totalDeleted, nil
		default:
		}

		bucketPath := filepath.Join(f.e.rootDir, relDir)
		entries, err := os.ReadDir(bucketPath)
		if err != nil {
			continue
		}

		for _, entry := range entries {
			if !isCandidateBlob(entry.Name(), entry.IsDir()) {
				continue
			}

			info, err := entry.Info()
			if err != nil {
				continue
			}

			// Enforce grace period using flight snapshot's MinBlobAge
			if !f.isEligible(info.ModTime(), now) {
				continue
			}

			shouldEvict := false
			if emergencyMode && info.Size() > emergencyBlobSizeThreshold {
				// Emergency large-blob priority mode
				shouldEvict = true
			} else if info.ModTime().Before(cutoff) {
				// Standard sampled cutoff eviction
				shouldEvict = true
			}

			if shouldEvict {
				filePath := filepath.Join(bucketPath, entry.Name())
				if err := os.Remove(filePath); err == nil {
					totalReclaimed += info.Size()
					totalDeleted++
					unlinksInBatch++

					// Cooperative Pacing: yield execution every BatchSize unlinks
					if unlinksInBatch >= batchSize {
						unlinksInBatch = 0
						if batchDelay > 0 {
							time.Sleep(batchDelay)
						}

						// Check if we reached the target free space
						stats, err := f.e.statfs(f.e.rootDir)
						if err == nil {
							_, effectiveFree, _ := ComputeEffectiveSpace(stats, reservedBytes)
							if effectiveFree >= targetFreeBytes {
								log.InfoContextf(ctx, "Storage eviction flight complete: reclaimed %.2f GB across %d files. Target free space achieved.",
									float64(totalReclaimed)/gib, totalDeleted)
								return totalReclaimed, totalDeleted, nil
							}
						}
					}
				}
			}
		}
	}

	log.InfoContextf(ctx, "Storage eviction flight finished pass: reclaimed %.2f GB across %d files.", float64(totalReclaimed)/gib, totalDeleted)
	return totalReclaimed, totalDeleted, nil
}

// pickRandomBuckets returns K random leaf bucket directory paths.
func (e *Evictor) pickRandomBuckets(k int) []string {
	e.randMu.Lock()
	defer e.randMu.Unlock()

	paths := make([]string, 0, k)
	seen := make(map[string]bool, k)

	for len(paths) < k {
		b1 := e.rand.Intn(256)
		b2 := e.rand.Intn(256)
		rel := fmt.Sprintf("%02x/%02x", b1, b2)
		if !seen[rel] {
			seen[rel] = true
			paths = append(paths, filepath.Join(e.rootDir, rel))
		}
	}
	return paths
}

// getAllShardedBucketPermutation returns a pseudo-random permutation of all 65,536 leaf bucket relative paths.
func (e *Evictor) getAllShardedBucketPermutation() []string {
	e.randMu.Lock()
	defer e.randMu.Unlock()

	total := 256 * 256
	perm := e.rand.Perm(total)
	result := make([]string, total)

	for i, p := range perm {
		b1 := p / 256
		b2 := p % 256
		result[i] = fmt.Sprintf("%02x/%02x", b1, b2)
	}
	return result
}
