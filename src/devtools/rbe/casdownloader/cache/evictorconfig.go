package cache

import (
	"fmt"
	"time"

	log "github.com/golang/glog"
	"github.com/google/device-infra/src/devtools/rbe/common/storage"
)

// minHeadroomBand is the smallest gap allowed between the level that starts an
// eviction pass and the level that ends one.
//
// Equal thresholds end every pass before it reclaims anything, and a gap that
// is merely nonzero is barely better: each pass reclaims a sliver, so the next
// download trips the trigger again and pays for another flight, which samples
// leaf buckets and stats their entries to decide what to delete. One gibibyte
// is chosen to match the default of -cache-min-free-space, which is to say it
// is the amount this binary has already decided is a meaningful quantity of
// disk.
//
// It floors a derived target and an explicitly set one alike, because nothing
// downstream enforces it: the evictor parses the two thresholds independently
// and never compares them.
const minHeadroomBand = 1 << 30 // 1 GiB

// EvictorOverrides carries eviction settings the caller set explicitly.
//
// A nil field means "leave the derived default alone", which is the difference
// that cannot be recovered from the values themselves: a flag left at its
// default is indistinguishable from one deliberately set to the same value, and
// the derived defaults below are not constants, so there is nothing to compare
// against.
//
// Only the settings that change casdownloader's behavior appear here.
// CheckInterval is omitted because it drives the background eviction loop,
// which this binary never starts; ReservedSpaceGB is omitted because it is
// pinned to zero for the reasons given below.
type EvictorOverrides struct {
	TargetFreeSpaceBytes *int64
	SampleBuckets        *int
	BatchSize            *int
	BatchDelay           *time.Duration
	LazyTouchInterval    *time.Duration
	MinBlobAge           *time.Duration
}

// NewEvictorConfig builds the evictor settings for a casdownloader cache from
// -cache-min-free-space, with any explicitly set tuning flags applied on top.
//
// The shared evictor is written for casproxy, which runs alone on a host it
// owns, and two of its defaults do not transfer to a downloader that shares a
// test host with the very artifacts it is unpacking:
//
// ReservedSpaceGB is zeroed. Its 100 GB default carves out room for the OS,
// logs and Envoy on a dedicated proxy machine. Here the same job is already
// done by -cache-min-free-space, and subtracting 100 GB from a volume that may
// only be a few hundred gigabytes would distort the percentage arithmetic
// badly enough to keep the cache permanently in eviction. It is deliberately
// not overridable.
//
// The thresholds become byte counts rather than percentages, because that is
// what the flag they come from already is. A percentage would also behave
// surprisingly across the fleet, where volume sizes differ by an order of
// magnitude.
//
// TargetFreeSpace is derived rather than required: one dial is easier to reason
// about than two that must stay ordered, and the ordering is a hard
// requirement of the evictor. Doubling keeps the band proportional on a large
// volume, and the floor keeps it useful on a small one.
//
// A caller that wants finer control sets -cache-target-free-space, and that
// value is checked here because nothing downstream checks it. The evictor
// parses the two thresholds independently and never compares them, and the
// ordering it needs is implicit: MinFreeSpace decides when a headroom eviction
// starts, TargetFreeSpace decides when the flight stops. Point the goal below
// the trigger and every pass concludes the instant it begins, so the effective
// low watermark silently becomes TargetFreeSpace and -cache-min-free-space --
// the flag that exists to keep the volume from filling up -- stops doing
// anything at all.
//
// An override closer to the minimum than minHeadroomBand is therefore raised
// to that floor, and reported when it is. The floor is the band constant
// rather than the derived target because lowering the target is the main
// reason to reach for the flag: doubling is generous on a large minimum, and
// an operator who would rather keep the cache warm than hold twice the minimum
// in reserve should be able to say so. Clamping also keeps more of the
// caller's intent than falling back to the derived value would, since someone
// asking for a narrow band gets the narrowest one allowed instead of the
// widest one available. Neither is an error: failing here would cost the host
// its whole cache over a single mistyped tuning flag.
//
// A zero or negative minFreeSpaceBytes is honored as "do not evict on a
// threshold", which is what the flag has always meant: the trigger sits at
// zero bytes and is therefore never reached. Pre-flight eviction still works,
// because EnsureHeadroom reclaims against the size of the pending write rather
// than against the trigger.
func NewEvictorConfig(minFreeSpaceBytes int64, overrides EvictorOverrides) storage.EvictorConfig {
	cfg := storage.DefaultEvictorConfig()

	if minFreeSpaceBytes < 0 {
		minFreeSpaceBytes = 0
	}
	band := minFreeSpaceBytes
	if band < minHeadroomBand {
		band = minHeadroomBand
	}

	cfg.MinFreeSpace = fmt.Sprintf("%db", minFreeSpaceBytes)
	cfg.TargetFreeSpace = fmt.Sprintf("%db", minFreeSpaceBytes+band)
	cfg.ReservedSpaceGB = 0

	if overrides.TargetFreeSpaceBytes != nil {
		target := *overrides.TargetFreeSpaceBytes
		if floor := minFreeSpaceBytes + minHeadroomBand; target < floor {
			log.Warningf("-cache-target-free-space=%d is less than %d bytes above -cache-min-free-space=%d. The minimum is where a headroom eviction starts and the target is where it stops, so a band this narrow makes nearly every download pay for an eviction pass of its own, and a target at or below the minimum would end each pass before it reclaimed anything. Using %d instead.",
				target, int64(minHeadroomBand), minFreeSpaceBytes, floor)
			target = floor
		}
		cfg.TargetFreeSpace = fmt.Sprintf("%db", target)
	}
	if overrides.SampleBuckets != nil {
		cfg.SampleBuckets = *overrides.SampleBuckets
	}
	if overrides.BatchSize != nil {
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
