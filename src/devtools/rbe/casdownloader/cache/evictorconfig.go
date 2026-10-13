package cache

import (
	"fmt"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	log "github.com/golang/glog"
	"github.com/google/device-infra/src/devtools/rbe/common/storage"
)

const (
	// DefaultLegacyMinFreeSpace is the default -cache-min-free-space of the
	// lock-based cache, in bytes.
	DefaultLegacyMinFreeSpace = 1 << 30 // 1 GiB

	// DefaultLockFreeMinFreeSpace is the lock-free cache's default for
	// -cache-min-free-space: a trim starts below it.
	//
	// It and DefaultLockFreeTargetFreeSpace are percentages of the volume
	// rather than byte counts because the cache shares its volume with
	// Tradefed and the tests, whose own limits are percentages: Tradefed stops
	// leasing above cluster:max-disk-usage, which is 70% in cloud-ate and
	// keystone. A fixed byte count would be too little on a large volume and
	// too much on a small one. The values are the ones proposed in
	// b/572891754 and are still to be confirmed during the canary.
	DefaultLockFreeMinFreeSpace = "20%"

	// DefaultLockFreeTargetFreeSpace is the lock-free cache's default for
	// -cache-target-free-space: a trim stops once free space reaches it. See
	// DefaultLockFreeMinFreeSpace for why it is a percentage.
	DefaultLockFreeTargetFreeSpace = "25%"

	// SelfAdaptive is the value of -cache-min-free-space reserved for
	// watermarks that adapt to each host. Until that exists the lock-free
	// cache uses its default instead, with a warning, and the lock-based
	// cache rejects it.
	SelfAdaptive = "self-adaptive"

	// targetGapPoints is how many percentage points of the volume a target
	// that is not above the minimum is reset to above it.
	targetGapPoints = 5

	// lockFreeSampleBuckets is how many buckets a trim samples to choose its
	// cutoff age. The shared default of 16 was sized for casproxy's 65,536
	// buckets of about 1,500 files each; casdownloader's caches hold 30-680
	// files in each of their 256, so it samples the evictor's maximum.
	lockFreeSampleBuckets = 64
	// lockFreeBatchSize is how many blobs a trim unlinks between free-space
	// checks. It is the evictor's minimum rather than its default of 500, so
	// that a trim on a small cache overshoots its target by less.
	lockFreeBatchSize = 200
)

// EvictorOverrides carries eviction settings the caller set explicitly.
//
// A nil field means "leave the default alone". That is the difference that
// cannot be recovered from the values themselves: a flag left at its default
// is indistinguishable from one deliberately set to the same value.
//
// Only the settings that change casdownloader's behavior appear here.
// CheckInterval is omitted because it drives the background eviction loop,
// which this binary never starts; ReservedSpaceGB is omitted because it is
// pinned to zero for the reasons given on newEvictorConfig. The free-space
// watermarks are not overrides at all: they always have a value, and are
// resolved by lockFreeWatermarks.
type EvictorOverrides struct {
	SampleBuckets     *int
	BatchSize         *int
	BatchDelay        *time.Duration
	LazyTouchInterval *time.Duration
	MinBlobAge        *time.Duration
}

// watermarks are the lock-free cache's two free-space thresholds: a trim
// starts when free space falls below min and stops when it reaches target.
type watermarks struct {
	min, target storage.SpaceThreshold
}

// parseSpace parses a free-space flag value: a bare byte count, as
// -cache-min-free-space has always taken, or anything
// storage.ParseSpaceThreshold accepts, such as "20%" or "10gb".
//
// The bare byte count is handled here because ParseSpaceThreshold documents
// it but rejects it as ambiguous.
func parseSpace(value string) (storage.SpaceThreshold, error) {
	if n, err := strconv.ParseInt(strings.TrimSpace(value), 10, 64); err == nil {
		return storage.SpaceThreshold{Bytes: n}, nil
	}
	return storage.ParseSpaceThreshold(value)
}

// isSelfAdaptive reports whether value is the reserved SelfAdaptive value.
func isSelfAdaptive(value string) bool {
	return strings.EqualFold(strings.TrimSpace(value), SelfAdaptive)
}

// parseLegacyMinFreeSpace parses -cache-min-free-space for the lock-based
// cache, which takes a byte count, as it always has. Empty means
// DefaultLegacyMinFreeSpace.
//
// A percentage and SelfAdaptive are rejected: LocalCache has no notion of the
// volume's size, and both only mean something to the lock-free cache.
func parseLegacyMinFreeSpace(value string) (int64, error) {
	if strings.TrimSpace(value) == "" {
		return DefaultLegacyMinFreeSpace, nil
	}
	if isSelfAdaptive(value) {
		return 0, fmt.Errorf("-cache-min-free-space=%s requires -enable-lock-free-cache", value)
	}
	t, err := parseSpace(value)
	if err != nil {
		return 0, fmt.Errorf("invalid -cache-min-free-space: %w", err)
	}
	if t.IsPercent {
		return 0, fmt.Errorf("-cache-min-free-space=%s is a percentage, which requires -enable-lock-free-cache; give a byte count instead", value)
	}
	return t.Bytes, nil
}

// parseLockFreeMinFreeSpace parses -cache-min-free-space for the lock-free
// cache. Empty and SelfAdaptive both mean DefaultLockFreeMinFreeSpace; the
// latter is reserved for future work, so it is accepted with a warning rather
// than rejected, and a launcher that already passes it keeps working.
func parseLockFreeMinFreeSpace(value string) (storage.SpaceThreshold, error) {
	switch {
	case strings.TrimSpace(value) == "":
		value = DefaultLockFreeMinFreeSpace
	case isSelfAdaptive(value):
		log.Warningf("-cache-min-free-space=%s is reserved for watermarks that adapt to each host, which are not implemented yet. Using the default of %s instead.",
			value, DefaultLockFreeMinFreeSpace)
		value = DefaultLockFreeMinFreeSpace
	}
	t, err := parseSpace(value)
	if err != nil {
		return storage.SpaceThreshold{}, fmt.Errorf("invalid -cache-min-free-space: %w", err)
	}
	return clampNegative(t), nil
}

// clampNegative turns a negative byte count into zero, which means "no
// threshold". A negative threshold would otherwise fail to parse when the
// evictor reads it back, taking the cache down over a nonsensical flag.
func clampNegative(t storage.SpaceThreshold) storage.SpaceThreshold {
	if !t.IsPercent && t.Bytes < 0 {
		t.Bytes = 0
	}
	return t
}

// lockFreeWatermarks parses the lock-free cache's free-space flags and makes
// sure the target is above the minimum.
//
// minValue is -cache-min-free-space and targetValue is the target, which
// targetFlag names for messages: -cache-target-free-space, or -trim-target-free
// when a -trim-only run overrides it. Empty values select the defaults.
//
// A target that is not above the minimum is reset to the minimum plus
// targetGapPoints percentage points of the volume, with a warning. The
// minimum is where a trim starts and the target where it stops, so a target
// at or below the minimum would stop every trim before it reclaimed
// anything, and -cache-min-free-space would silently do nothing. It is reset
// rather than rejected because failing here would cost the host its whole
// cache over a mistyped tuning flag.
//
// Comparing a percentage with a byte count, or resetting a byte count, needs
// the size of the volume, which volumeTotal reports. It is called only then,
// so the common all-percentage case costs no syscall. If it fails, the target
// is kept as given, with a warning: the evictor cannot run on a volume it
// cannot statfs anyway.
func lockFreeWatermarks(minValue, targetValue, targetFlag string, volumeTotal func() (int64, error)) (watermarks, error) {
	minFree, err := parseLockFreeMinFreeSpace(minValue)
	if err != nil {
		return watermarks{}, err
	}

	targetIsDefault := strings.TrimSpace(targetValue) == ""
	if targetIsDefault {
		targetValue = DefaultLockFreeTargetFreeSpace
	}
	target, err := parseSpace(targetValue)
	if err != nil {
		return watermarks{}, fmt.Errorf("invalid %s: %w", targetFlag, err)
	}
	target = clampNegative(target)

	described := fmt.Sprintf("%s=%s", targetFlag, targetValue)
	if targetIsDefault {
		described = fmt.Sprintf("The default %s of %s", targetFlag, targetValue)
	}
	reset, err := targetAboveMin(minFree, target, volumeTotal)
	switch {
	case err != nil:
		log.Warningf("Could not check that %s is above -cache-min-free-space=%s: %v. Using it as given.",
			described, thresholdString(minFree), err)
	case reset != target:
		log.Warningf("%s is not above -cache-min-free-space=%s. A trim starts below the minimum and stops at the target, so a target at or below the minimum would stop every trim before it reclaimed anything. Using %s, the minimum plus %d points of the volume, instead.",
			described, thresholdString(minFree), thresholdString(reset), targetGapPoints)
	}
	return watermarks{min: minFree, target: reset}, nil
}

// targetAboveMin returns target if it is above minFree, and otherwise the
// target to use instead: minFree plus targetGapPoints percentage points of the
// volume. If the two cannot be compared because the volume's size is
// unknown, it returns target and the error.
func targetAboveMin(minFree, target storage.SpaceThreshold, volumeTotal func() (int64, error)) (storage.SpaceThreshold, error) {
	// The cases that need no volume size.
	switch {
	case minFree.IsPercent && target.IsPercent:
		if target.Percent > minFree.Percent {
			return target, nil
		}
		return storage.SpaceThreshold{IsPercent: true, Percent: capPercent(minFree.Percent + targetGapPoints)}, nil
	case !minFree.IsPercent && !target.IsPercent && target.Bytes > minFree.Bytes:
		return target, nil
	case isZero(minFree) && !isZero(target):
		return target, nil
	}

	total, err := volumeTotal()
	if err != nil {
		return target, fmt.Errorf("the size of the volume is unknown: %w", err)
	}
	minBytes, targetBytes := minFree.Evaluate(total), target.Evaluate(total)
	if targetBytes > minBytes {
		return target, nil
	}
	return storage.SpaceThreshold{Bytes: minBytes + total*targetGapPoints/100}, nil
}

func isZero(t storage.SpaceThreshold) bool {
	if t.IsPercent {
		return t.Percent == 0
	}
	return t.Bytes == 0
}

// capPercent caps a percentage at 100.
func capPercent(p float64) float64 {
	if p > 100 {
		return 100
	}
	return p
}

// thresholdString formats t the way storage.ParseSpaceThreshold reads it
// back. A byte count carries the "b" suffix because ParseSpaceThreshold
// rejects a bare integer.
func thresholdString(t storage.SpaceThreshold) string {
	if t.IsPercent {
		return strconv.FormatFloat(t.Percent, 'f', -1, 64) + "%"
	}
	return fmt.Sprintf("%db", t.Bytes)
}

// volumeTotalOf returns a function reporting the size of the volume that
// holds dir, or will hold it once it is created: if dir does not exist yet,
// its nearest existing ancestor is asked instead.
func volumeTotalOf(dir string, statfs storage.StatfsFunc) func() (int64, error) {
	return func() (int64, error) {
		for d := dir; ; {
			st, err := statfs(d)
			if err == nil {
				return st.TotalBytes, nil
			}
			parent := filepath.Dir(d)
			if parent == d {
				return 0, err
			}
			d = parent
		}
	}
}

// newEvictorConfig builds the evictor settings for a casdownloader lock-free
// cache from its watermarks, with any explicitly set tuning flags applied on
// top.
//
// The shared evictor is written for casproxy, which runs alone on a host it
// owns, and several of its defaults do not transfer to a downloader that
// shares a test host with the very artifacts it is unpacking:
//
// ReservedSpaceGB is zeroed. Its 100 GB default carves out room for the OS,
// logs and Envoy on a dedicated proxy machine. Here the same job is already
// done by the watermarks, and subtracting 100 GB from a volume that may only
// be a few hundred gigabytes would distort the percentage arithmetic badly
// enough to keep the cache permanently in eviction. It is deliberately not
// overridable.
//
// EmergencyLargeBlobFirst is turned off. On a cache this small, unlinking
// every large blob first throws away the hottest ones, such as device images,
// which the next test then downloads again.
//
// SampleBuckets and BatchSize get casdownloader's own defaults; see
// lockFreeSampleBuckets and lockFreeBatchSize. An override of zero or less
// keeps them, as the flags document, rather than falling through to the
// shared defaults.
func newEvictorConfig(w watermarks, overrides EvictorOverrides) storage.EvictorConfig {
	cfg := storage.DefaultEvictorConfig()
	cfg.MinFreeSpace = thresholdString(w.min)
	cfg.TargetFreeSpace = thresholdString(w.target)
	cfg.ReservedSpaceGB = 0
	cfg.EmergencyLargeBlobFirst = false
	cfg.SampleBuckets = lockFreeSampleBuckets
	cfg.BatchSize = lockFreeBatchSize

	if overrides.SampleBuckets != nil && *overrides.SampleBuckets > 0 {
		cfg.SampleBuckets = *overrides.SampleBuckets
	}
	if overrides.BatchSize != nil && *overrides.BatchSize > 0 {
		cfg.BatchSize = *overrides.BatchSize
	}
	if overrides.BatchDelay != nil {
		cfg.BatchDelay = *overrides.BatchDelay
	}
	if overrides.LazyTouchInterval != nil {
		cfg.LazyTouchInterval = *overrides.LazyTouchInterval
	}
	if overrides.MinBlobAge != nil {
		cfg.MinBlobAge = *overrides.MinBlobAge
	}
	return cfg
}

// lockFreeEvictorConfig resolves the lock-free cache's flags into an evictor
// configuration for the cache rooted at dir. See lockFreeWatermarks for the
// arguments.
func lockFreeEvictorConfig(dir, minValue, targetValue, targetFlag string, overrides EvictorOverrides, statfs storage.StatfsFunc) (storage.EvictorConfig, error) {
	if statfs == nil {
		statfs = storage.RealStatfs
	}
	w, err := lockFreeWatermarks(minValue, targetValue, targetFlag, volumeTotalOf(dir, statfs))
	if err != nil {
		return storage.EvictorConfig{}, err
	}
	return newEvictorConfig(w, overrides), nil
}
