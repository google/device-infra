package cache

import (
	"fmt"
	"path/filepath"
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
	// watermarks that adapt to each host. Until that exists both caches use
	// their default instead, with a warning.
	SelfAdaptive = "self-adaptive"

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

// The lock-free cache's default watermarks, parsed.
var (
	defaultLockFreeMin    = mustParseSpace(DefaultLockFreeMinFreeSpace)
	defaultLockFreeTarget = mustParseSpace(DefaultLockFreeTargetFreeSpace)
)

// mustParseSpace parses one of the default watermarks, which are constants,
// so an error is a bug.
func mustParseSpace(value string) storage.SpaceThreshold {
	t, err := storage.ParseSpaceThreshold(value)
	if err != nil {
		panic(fmt.Sprintf("bad default watermark: %v", err))
	}
	return t
}

// warningLog collects the warnings that resolving the free-space flags
// produces. Each is logged as it is added; the list is kept so that a
// download can also report it as a note.
type warningLog []string

func (w *warningLog) addf(format string, args ...any) {
	msg := fmt.Sprintf(format, args...)
	log.Warningf("%s", msg)
	*w = append(*w, msg)
}

// isSelfAdaptive reports whether value is the reserved SelfAdaptive value.
func isSelfAdaptive(value string) bool {
	return strings.EqualFold(strings.TrimSpace(value), SelfAdaptive)
}

// parseLegacyMinFreeSpace resolves -cache-min-free-space for the lock-based
// cache, which takes a byte count, bare or with a unit, as it always has.
// Empty means DefaultLegacyMinFreeSpace.
//
// Any other value that is not a byte count also means the default, with a
// warning: one that does not parse, which includes a negative number, and a
// percentage or SelfAdaptive, which LocalCache cannot honor because it has
// no notion of the volume's size. Failing instead would cost the host its
// cache, which is far worse than the default threshold. A negative value in
// particular must never reach LocalCache: luci reads it as a huge unsigned
// number and evicts every entry on every run.
func parseLegacyMinFreeSpace(value string) (int64, []string) {
	if strings.TrimSpace(value) == "" {
		return DefaultLegacyMinFreeSpace, nil
	}
	var warns warningLog
	if isSelfAdaptive(value) {
		warns.addf("-cache-min-free-space=%s requires -enable-lock-free-cache. Using the default of %d bytes instead.",
			value, DefaultLegacyMinFreeSpace)
		return DefaultLegacyMinFreeSpace, warns
	}
	t, err := storage.ParseSpaceThreshold(value)
	switch {
	case err != nil:
		warns.addf("Ignoring -cache-min-free-space: %v. Using the default of %d bytes instead.", err, DefaultLegacyMinFreeSpace)
	case t.IsPercent:
		warns.addf("-cache-min-free-space=%s is a percentage, which requires -enable-lock-free-cache; give a byte count instead. Using the default of %d bytes.",
			value, DefaultLegacyMinFreeSpace)
	default:
		return t.Bytes, nil
	}
	return DefaultLegacyMinFreeSpace, warns
}

// parseWatermark parses the lock-free cache's free-space flag named flag.
// Empty means def, and so does a value that does not parse, with a warning:
// the cache stays on, since a cache with the default watermark is far better
// than none.
func parseWatermark(warns *warningLog, flag, value string, def storage.SpaceThreshold) storage.SpaceThreshold {
	if strings.TrimSpace(value) == "" {
		return def
	}
	t, err := storage.ParseSpaceThreshold(value)
	if err != nil {
		warns.addf("Ignoring %s: %v. Using the default of %s instead.", flag, err, def)
		return def
	}
	return t
}

// lockFreeWatermarks resolves the lock-free cache's free-space flags into the
// watermarks to use, and returns a warning for each value it could not use
// as given. It never fails: a bad watermark costs the host its tuning, not
// its cache.
//
// minValue is -cache-min-free-space and targetValue is the target, which
// targetFlag names for messages: -cache-target-free-space, or -trim-target-free
// when a -trim-only run overrides it. Empty values select the defaults, and
// so do values that do not parse, with a warning. So does SelfAdaptive for
// the minimum: it is reserved for future work, so it is accepted with a
// warning rather than rejected, and a launcher that already passes it keeps
// working.
//
// The pair is then held to storage.StrictWatermarkBounds: the minimum to
// 5-45% of the volume, and the target to 10-50% and at least 5 points above
// the minimum. A value out of range is clamped to the nearest bound, and a
// target too close to the minimum is raised, each with a warning. The minimum
// is where a trim starts and the target where it stops, so a minimum of 0
// never trims, one near 100% trims all the time, and a target at or below
// the minimum stops every trim before it reclaims anything.
//
// A byte count is checked as a percentage of the volume, whose size
// volumeTotal reports. It is called only then, so the common all-percentage
// case costs no syscall. If it fails, byte counts are used as given, with a
// warning, and percentages are still clamped: the evictor cannot run on a
// volume it cannot statfs anyway.
func lockFreeWatermarks(minValue, targetValue, targetFlag string, volumeTotal func() (int64, error)) (storage.Watermarks, []string) {
	var warns warningLog
	if isSelfAdaptive(minValue) {
		warns.addf("-cache-min-free-space=%s is reserved for watermarks that adapt to each host, which are not implemented yet. Using the default of %s instead.",
			minValue, defaultLockFreeMin)
		minValue = ""
	}
	w := storage.Watermarks{
		Min:    parseWatermark(&warns, "-cache-min-free-space", minValue, defaultLockFreeMin),
		Target: parseWatermark(&warns, targetFlag, targetValue, defaultLockFreeTarget),
	}

	var total int64 // 0 tells NormalizeWatermarks that the size is unknown.
	if !w.Min.IsPercent || !w.Target.IsPercent {
		t, err := volumeTotal()
		if err != nil {
			warns.addf("Could not measure the volume to check -cache-min-free-space and %s against it: %v.", targetFlag, err)
		} else {
			total = t
		}
	}
	normalized, adjustments, err := storage.NormalizeWatermarks(w, total, storage.StrictWatermarkBounds)
	if err != nil {
		// Not expected: with Clamp set, NormalizeWatermarks never fails.
		warns.addf("Could not check -cache-min-free-space and %s: %v. Using the defaults instead.", targetFlag, err)
		return storage.Watermarks{Min: defaultLockFreeMin, Target: defaultLockFreeTarget}, warns
	}
	for _, a := range adjustments {
		warns.addf("Free-space watermarks -cache-min-free-space and %s: %s.", targetFlag, a)
	}
	return normalized, warns
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
func newEvictorConfig(w storage.Watermarks, overrides EvictorOverrides) storage.EvictorConfig {
	cfg := storage.DefaultEvictorConfig()
	cfg.MinFreeSpace = w.Min.String()
	cfg.TargetFreeSpace = w.Target.String()
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
// configuration for the cache rooted at dir, along with a warning for each
// free-space flag it could not use as given. See lockFreeWatermarks for the
// arguments.
func lockFreeEvictorConfig(dir, minValue, targetValue, targetFlag string, overrides EvictorOverrides, statfs storage.StatfsFunc) (storage.EvictorConfig, []string) {
	if statfs == nil {
		statfs = storage.RealStatfs
	}
	w, warnings := lockFreeWatermarks(minValue, targetValue, targetFlag, volumeTotalOf(dir, statfs))
	return newEvictorConfig(w, overrides), warnings
}
