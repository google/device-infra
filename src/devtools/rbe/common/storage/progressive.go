package storage

import (
	"context"
	"os"
	"path/filepath"
	"time"

	log "github.com/golang/glog"
)

// CapCheckName is the file, directly in the cache root, whose mtime records
// when a TrimWith pass last started checking the cache against its cap.
// Downloads stat it to decide whether a check is due. Like evictLockName, the leading dot and
// the root-level placement keep it out of the evictor's way.
const CapCheckName = ".cap_check"

// capCheckSteps are the early stops of the cap check. After reading buckets
// buckets, the check stops if the estimated size is at most 1 - allowance of
// the cap; past the last step it reads every bucket. The allowances are
// rounded from the 1st percentile of estimate / true size on 21 surveyed atc
// hosts at these steps of Layout256's 256 buckets (b/572891754#comment3), so
// that at each step about 1 check in 100 of a cache over its cap wrongly finds
// it under.
var capCheckSteps = []struct {
	buckets   int
	allowance float64
}{
	{buckets: 32, allowance: 0.20},
	{buckets: 64, allowance: 0.15},
	{buckets: 128, allowance: 0.08},
}

// progressiveSample is the buckets a TrimWith pass has read so far. The order
// is drawn once, when the pass starts, so that each step adds buckets to those
// already read rather than drawing a new sample, and both the cap check and
// the cutoff age use every bucket read.
type progressiveSample struct {
	rootDir    string
	buckets    []string // Every bucket of the layout, relative to rootDir, in random order.
	now        time.Time
	minBlobAge time.Duration
	// stats is what the buckets read hold; stats.bucketCount is how many of
	// buckets, from the start, have been read.
	stats sampleStats
}

func (e *Evictor) newProgressiveSample(minBlobAge time.Duration) *progressiveSample {
	return &progressiveSample{
		rootDir:    e.rootDir,
		buckets:    e.bucketPermutation(),
		now:        time.Now(),
		minBlobAge: minBlobAge,
		stats:      newSampleStats(e.maxAgeDays),
	}
}

// sampleForTrim reads the buckets a TrimWith pass needs before it can evict,
// on a volume with free bytes free, and returns them with the bytes the cap
// asks it to reclaim. It returns a nil sample if the pass has nothing to do.
//
// With maxSize set, it first records in CapCheckName that a cap check is
// starting, then runs it. The marker is written before the check rather than
// after it, so that a check that keeps running out of -trim-max-duration on a
// slow or large volume still holds off the next one for the interval, instead
// of leaving the marker stale and every download starting another evictor. Then, if free space is short of
// TargetFreeSpace or the cache is over its cap, it reads at least
// SampleBuckets buckets for the cutoff age.
func (e *Evictor) sampleForTrim(ctx context.Context, snap snapshotConfig, free, maxSize int64) (*progressiveSample, int64, error) {
	p := e.newProgressiveSample(snap.minBlobAge)
	var capExcess int64
	if maxSize > 0 {
		// The mtime is all that matters, and O_TRUNC updates it.
		if err := os.WriteFile(filepath.Join(e.rootDir, CapCheckName), nil, 0644); err != nil {
			log.WarningContextf(ctx, "Failed to record the cap check of %s: %v. Downloads will start another.", e.rootDir, err)
		}
		var err error
		if capExcess, err = p.checkCap(ctx, maxSize); err != nil {
			return nil, 0, err
		}
	}
	if free >= snap.targetFreeBytes && capExcess <= 0 {
		return nil, 0, nil
	}
	if err := p.readTo(ctx, snap.sampleBuckets); err != nil {
		return nil, 0, err
	}
	return p, capExcess, nil
}

// readTo reads further buckets until n of them, or all, have been read. It
// stops with ctx's error when ctx is done.
func (p *progressiveSample) readTo(ctx context.Context, n int) error {
	start := time.Now()
	defer func() { p.stats.duration += time.Since(start) }()
	for p.stats.bucketCount < min(n, len(p.buckets)) {
		if err := ctx.Err(); err != nil {
			return err
		}
		p.stats.addBucket(filepath.Join(p.rootDir, p.buckets[p.stats.bucketCount]), p.now, p.minBlobAge)
	}
	return nil
}

// size estimates the size of the cache, pinned and young blobs included, as
// bytes_read * N / k for k of its N buckets read. Once every bucket has been
// read it is exact.
func (p *progressiveSample) size() int64 {
	if p.stats.bucketCount == len(p.buckets) {
		return p.stats.blobBytes
	}
	return int64(float64(p.stats.blobBytes) * float64(len(p.buckets)) / float64(p.stats.bucketCount))
}

// checkCap reads buckets in steps until it can tell whether the cache is over
// maxSize, and returns how far its exact size is over 90% of maxSize, or zero.
//
// After each step of capCheckSteps it stops, with zero, if the estimated size
// is clearly under the cap. It never stops because the cache looks over the
// cap: a sampled estimate is too noisy on a cache whose bytes sit in a few
// large blobs, so any eviction for the cap waits for the exact size, from
// every bucket. Stopping early on a cache that is in fact over its cap only
// postpones that eviction to the next check, which draws a fresh order.
func (p *progressiveSample) checkCap(ctx context.Context, maxSize int64) (int64, error) {
	for _, step := range capCheckSteps {
		if step.buckets >= len(p.buckets) {
			break
		}
		if err := p.readTo(ctx, step.buckets); err != nil {
			return 0, err
		}
		if estimate := p.size(); float64(estimate) <= float64(maxSize)*(1-step.allowance) {
			log.InfoContextf(ctx, "Cap check of %s stopped after %d of %d buckets: an estimated %.2f GB, at most %.0f%% of the %.2f GB cap.",
				p.rootDir, p.stats.bucketCount, len(p.buckets), float64(estimate)/gib, 100*(1-step.allowance), float64(maxSize)/gib)
			return 0, nil
		}
	}
	if err := p.readTo(ctx, len(p.buckets)); err != nil {
		return 0, err
	}
	size := p.size()
	// 0.9 * maxSize, in integers so that it neither overflows nor rounds.
	excess := max(size-(maxSize-maxSize/10), 0)
	log.InfoContextf(ctx, "Cap check of %s read all %d buckets: %.2f GB (%.2f GB pinned by other links) against the %.2f GB cap, %.2f GB over 90%% of it.",
		p.rootDir, len(p.buckets), float64(size)/gib, float64(p.stats.pinnedBytes)/gib, float64(maxSize)/gib, float64(excess)/gib)
	return excess, nil
}

// cutoff returns the mtime before which blobs should be unlinked to reclaim
// about need bytes, oldest first, according to the histogram of the buckets
// read so far.
//
// It accumulates the histogram from its oldest bin until that holds the
// buckets' share of need, need * k / N. Bin h holds the blobs aged [h, h+1)
// hours, so a cutoff of now minus h hours takes that bin and every older one.
// If the histogram holds less than that share, the cutoff is now minus
// MinBlobAge, which leaves only the grace period standing.
func (p *progressiveSample) cutoff(ctx context.Context, need int64) time.Time {
	share := float64(need) * float64(p.stats.bucketCount) / float64(len(p.buckets))
	cutoff := p.now.Add(-p.minBlobAge)
	var accumulated int64
	for h := len(p.stats.histogram) - 1; h >= 0; h-- {
		accumulated += p.stats.histogram[h]
		if float64(accumulated) >= share {
			cutoff = p.now.Add(-time.Duration(h) * time.Hour)
			break
		}
	}
	log.InfoContextf(ctx, "Storage eviction cutoff from %d of %d buckets (read in %v): %d candidate blobs, %.2f GB; T_cutoff = %v, to reclaim %.2f GB.",
		p.stats.bucketCount, len(p.buckets), p.stats.duration, p.stats.totalCount, float64(p.stats.totalBytes)/gib, cutoff, float64(need)/gib)
	return cutoff
}
