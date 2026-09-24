package cache

import (
	"testing"
	"time"

	"github.com/google/device-infra/src/devtools/rbe/common/storage"
)

// TestNewEvictorConfig_ProducesAConfigTheEvictorAccepts is the test that
// matters most here, because the failure it guards against is invisible at
// compile time and fatal at startup: NewEvictor rejects thresholds it cannot
// parse or that are not strictly ordered, and casdownloader builds those
// strings itself rather than taking them from an operator.
func TestNewEvictorConfig_ProducesAConfigTheEvictorAccepts(t *testing.T) {
	sizes := []int64{
		-1,        // nonsense from a caller
		0,         // "do not enforce a threshold"
		1,         // pathologically small
		1 << 30,   // the flag default
		500 << 30, // a generous setting on a large volume
		1 << 40,   // a terabyte
	}

	for _, size := range sizes {
		cfg := NewEvictorConfig(size, EvictorOverrides{})
		if _, err := storage.NewEvictor(t.TempDir(), cfg, nil); err != nil {
			t.Errorf("NewEvictorConfig(%d) produced a config the evictor rejected: %v (min %q, target %q)",
				size, err, cfg.MinFreeSpace, cfg.TargetFreeSpace)
		}
	}
}

// TestNewEvictorConfig_ThresholdsMatchTheFlag pins the translation from the
// flag's bytes to the evictor's threshold strings. The evictor accepts several
// spellings, and one of them -- a bare integer -- it documents but does not in
// fact parse, so the suffix here is load-bearing.
func TestNewEvictorConfig_ThresholdsMatchTheFlag(t *testing.T) {
	const tenGiB = 10 << 30

	cfg := NewEvictorConfig(tenGiB, EvictorOverrides{})

	min, err := storage.ParseSpaceThreshold(cfg.MinFreeSpace)
	if err != nil {
		t.Fatalf("MinFreeSpace %q did not parse: %v", cfg.MinFreeSpace, err)
	}
	if min.IsPercent {
		t.Errorf("MinFreeSpace %q parsed as a percentage; a byte count from a flag must not become one", cfg.MinFreeSpace)
	}
	if min.Bytes != tenGiB {
		t.Errorf("MinFreeSpace = %d bytes, want %d: the flag's value must survive the round trip", min.Bytes, int64(tenGiB))
	}

	target, err := storage.ParseSpaceThreshold(cfg.TargetFreeSpace)
	if err != nil {
		t.Fatalf("TargetFreeSpace %q did not parse: %v", cfg.TargetFreeSpace, err)
	}
	if target.Bytes != 2*tenGiB {
		t.Errorf("TargetFreeSpace = %d bytes, want %d", target.Bytes, int64(2*tenGiB))
	}
}

// TestNewEvictorConfig_KeepsAUsefulBandOnASmallThreshold covers the reason the
// band has a floor. Doubling a tiny threshold yields a tiny band, and a tiny
// band means the next write pushes free space back under the trigger, so the
// cache evicts a handful of blobs on every single download instead of
// reclaiming a worthwhile amount once.
func TestNewEvictorConfig_KeepsAUsefulBandOnASmallThreshold(t *testing.T) {
	for _, size := range []int64{0, 1, 1 << 20} {
		cfg := NewEvictorConfig(size, EvictorOverrides{})

		min, err := storage.ParseSpaceThreshold(cfg.MinFreeSpace)
		if err != nil {
			t.Fatalf("MinFreeSpace %q did not parse: %v", cfg.MinFreeSpace, err)
		}
		target, err := storage.ParseSpaceThreshold(cfg.TargetFreeSpace)
		if err != nil {
			t.Fatalf("TargetFreeSpace %q did not parse: %v", cfg.TargetFreeSpace, err)
		}

		if got := target.Bytes - min.Bytes; got < minHeadroomBand {
			t.Errorf("NewEvictorConfig(%d) left a band of %d bytes, want at least %d", size, got, int64(minHeadroomBand))
		}
	}
}

// TestNewEvictorConfig_ClampsANegativeThresholdToZero keeps a nonsensical
// input from becoming a negative threshold, which would parse as an error and
// take the binary down at startup rather than at the flag that caused it.
func TestNewEvictorConfig_ClampsANegativeThresholdToZero(t *testing.T) {
	cfg := NewEvictorConfig(-(1 << 30), EvictorOverrides{})

	min, err := storage.ParseSpaceThreshold(cfg.MinFreeSpace)
	if err != nil {
		t.Fatalf("MinFreeSpace %q did not parse: %v", cfg.MinFreeSpace, err)
	}
	if min.Bytes != 0 {
		t.Errorf("MinFreeSpace = %d bytes, want 0", min.Bytes)
	}
}

// TestNewEvictorConfig_DropsTheProxysReservedSpace is a real behavioral
// difference rather than a tidying-up. casproxy withholds 100 GB from its
// calculations to protect the OS, logs and Envoy on a machine it has to
// itself. A test host that took the same deduction against a few hundred
// gigabytes of volume would compute an effective capacity so much smaller than
// the real one that the cache would sit permanently in eviction.
func TestNewEvictorConfig_DropsTheProxysReservedSpace(t *testing.T) {
	if storage.DefaultEvictorConfig().ReservedSpaceGB == 0 {
		t.Skip("the shared default no longer reserves space, so there is nothing to override")
	}
	if got := NewEvictorConfig(1<<30, EvictorOverrides{}).ReservedSpaceGB; got != 0 {
		t.Errorf("ReservedSpaceGB = %d, want 0", got)
	}
}

// TestNewEvictorConfig_LeavesTheRemainingTuningAlone checks that this function
// stays a translation of one flag rather than a second place where the shared
// evictor's behavior is defined. Anything it does not deliberately change
// should still be whatever common/storage says it is.
func TestNewEvictorConfig_LeavesTheRemainingTuningAlone(t *testing.T) {
	want := storage.DefaultEvictorConfig()
	got := NewEvictorConfig(1<<30, EvictorOverrides{})

	if got.CheckInterval != want.CheckInterval {
		t.Errorf("CheckInterval = %v, want %v", got.CheckInterval, want.CheckInterval)
	}
	if got.SampleBuckets != want.SampleBuckets {
		t.Errorf("SampleBuckets = %v, want %v", got.SampleBuckets, want.SampleBuckets)
	}
	if got.BatchSize != want.BatchSize {
		t.Errorf("BatchSize = %v, want %v", got.BatchSize, want.BatchSize)
	}
	if got.BatchDelay != want.BatchDelay {
		t.Errorf("BatchDelay = %v, want %v", got.BatchDelay, want.BatchDelay)
	}
	if got.LazyTouchInterval != want.LazyTouchInterval {
		t.Errorf("LazyTouchInterval = %v, want %v", got.LazyTouchInterval, want.LazyTouchInterval)
	}
	if got.MinBlobAge != want.MinBlobAge {
		t.Errorf("MinBlobAge = %v, want %v", got.MinBlobAge, want.MinBlobAge)
	}
}

// TestNewEvictorConfig_EmptyOverridesChangeNothing is the no-op guarantee that
// makes replacing the configuration file with flags safe to roll out: a caller
// who passes none of the new tuning flags must get byte-for-byte the config
// they got before those flags existed.
//
// The expected values are written out rather than compared against a second
// call, because a call cannot detect the case this guards against -- an
// override applied unconditionally would be present on both sides.
func TestNewEvictorConfig_EmptyOverridesChangeNothing(t *testing.T) {
	const minFree = 4 << 30

	got := NewEvictorConfig(minFree, EvictorOverrides{})

	want := storage.DefaultEvictorConfig()
	want.MinFreeSpace = "4294967296b"
	want.TargetFreeSpace = "8589934592b"
	want.ReservedSpaceGB = 0

	if got != want {
		t.Errorf("NewEvictorConfig(%d, EvictorOverrides{}) = %+v, want %+v", minFree, got, want)
	}
}

// Each override must reach exactly the field it names and leave the rest at the
// value the single dial derived. A table keeps that honest: a copy-paste slip
// that wired two settings to the same field would pass any test that only
// checked the field it set.
func TestNewEvictorConfig_OverridesApplyIndividually(t *testing.T) {
	const minFree = 1 << 30
	base := NewEvictorConfig(minFree, EvictorOverrides{})

	targetBytes := int64(99 << 30)
	buckets := 64
	batch := 2000
	delay := 2 * time.Millisecond
	touch := 30 * time.Minute
	blobAge := 90 * time.Second

	for _, tc := range []struct {
		name      string
		overrides EvictorOverrides
		want      func(storage.EvictorConfig) storage.EvictorConfig
	}{
		{
			name:      "target free space",
			overrides: EvictorOverrides{TargetFreeSpaceBytes: &targetBytes},
			want: func(c storage.EvictorConfig) storage.EvictorConfig {
				c.TargetFreeSpace = "106300440576b"
				return c
			},
		},
		{
			name:      "sample buckets",
			overrides: EvictorOverrides{SampleBuckets: &buckets},
			want: func(c storage.EvictorConfig) storage.EvictorConfig {
				c.SampleBuckets = buckets
				return c
			},
		},
		{
			name:      "batch size",
			overrides: EvictorOverrides{BatchSize: &batch},
			want: func(c storage.EvictorConfig) storage.EvictorConfig {
				c.BatchSize = batch
				return c
			},
		},
		{
			name:      "batch delay",
			overrides: EvictorOverrides{BatchDelay: &delay},
			want: func(c storage.EvictorConfig) storage.EvictorConfig {
				c.BatchDelay = delay
				return c
			},
		},
		{
			name:      "lazy touch interval",
			overrides: EvictorOverrides{LazyTouchInterval: &touch},
			want: func(c storage.EvictorConfig) storage.EvictorConfig {
				c.LazyTouchInterval = touch
				return c
			},
		},
		{
			name:      "min blob age",
			overrides: EvictorOverrides{MinBlobAge: &blobAge},
			want: func(c storage.EvictorConfig) storage.EvictorConfig {
				c.MinBlobAge = blobAge
				return c
			},
		},
	} {
		t.Run(tc.name, func(t *testing.T) {
			got := NewEvictorConfig(minFree, tc.overrides)
			if want := tc.want(base); got != want {
				t.Errorf("NewEvictorConfig(%d, %+v) = %+v, want %+v", minFree, tc.overrides, got, want)
			}
		})
	}
}

// A zero is a value a caller can legitimately mean -- "never pause between
// unlink batches" -- so an override carrying it must be honored rather than
// mistaken for an unset field. This is the reason the overrides are pointers.
func TestNewEvictorConfig_ZeroOverrideIsHonored(t *testing.T) {
	zero := time.Duration(0)

	got := NewEvictorConfig(1<<30, EvictorOverrides{BatchDelay: &zero})

	if got.BatchDelay != 0 {
		t.Errorf("BatchDelay = %v, want 0; an explicit zero was treated as unset", got.BatchDelay)
	}
}

// MinFreeSpace is where a headroom eviction starts and TargetFreeSpace is where
// the pass stops, but the evictor parses the two independently and never checks
// that the second is above the first. A target at or below the minimum produces
// a cache whose every pass concludes before deleting anything, which from the
// outside is indistinguishable from -cache-min-free-space having no effect; a
// target just barely above it produces a cache that pays for a pass on nearly
// every download. Both are raised to one band above the minimum.
//
// Zero is the one override rejected rather than honored here, unlike BatchDelay
// above: it cannot clear a non-negative minimum by a whole band.
func TestNewEvictorConfig_RaisesATargetThatCrowdsTheMinimum(t *testing.T) {
	const minFree = 4 << 30
	const floor = minFree + minHeadroomBand // 5 GiB

	for _, tc := range []struct {
		name   string
		target int64
		want   string
	}{
		{name: "below the minimum", target: minFree - 1, want: "5368709120b"},
		{name: "equal to the minimum", target: minFree, want: "5368709120b"},
		{name: "zero", target: 0, want: "5368709120b"},
		{name: "one byte under the floor", target: floor - 1, want: "5368709120b"},
		{name: "exactly the floor", target: floor, want: "5368709120b"},
		{name: "one byte above the floor", target: floor + 1, want: "5368709121b"},
	} {
		t.Run(tc.name, func(t *testing.T) {
			got := NewEvictorConfig(minFree, EvictorOverrides{TargetFreeSpaceBytes: &tc.target})

			if got.TargetFreeSpace != tc.want {
				t.Errorf("NewEvictorConfig(%d, target=%d).TargetFreeSpace = %q, want %q", minFree, tc.target, got.TargetFreeSpace, tc.want)
			}
			if got.MinFreeSpace != "4294967296b" {
				t.Errorf("MinFreeSpace = %q, want %q; the minimum must survive whatever happens to the target", got.MinFreeSpace, "4294967296b")
			}
		})
	}
}

// Lowering the target is the main reason to set the flag: the derived value
// holds twice the minimum free, which on a large minimum is a great deal of
// cache given up. Anything that clears the floor is honored, including values
// well under what the single dial would have derived.
func TestNewEvictorConfig_HonorsATargetBelowTheDerivedOne(t *testing.T) {
	const minFree = 4 << 30
	derived := NewEvictorConfig(minFree, EvictorOverrides{}).TargetFreeSpace
	target := int64(6 << 30)

	got := NewEvictorConfig(minFree, EvictorOverrides{TargetFreeSpaceBytes: &target})

	if got.TargetFreeSpace != "6442450944b" {
		t.Errorf("TargetFreeSpace = %q, want %q", got.TargetFreeSpace, "6442450944b")
	}
	if derived != "8589934592b" {
		t.Fatalf("derived target = %q, want %q; this test is only meaningful while the override sits below it", derived, "8589934592b")
	}
}

// A clamped target must not take the rest of the overrides down with it: the
// caller set those separately and they remain valid.
func TestNewEvictorConfig_AClampedTargetLeavesTheOtherOverridesAlone(t *testing.T) {
	const minFree = 4 << 30
	tooLow := int64(1 << 20)
	buckets := 64

	got := NewEvictorConfig(minFree, EvictorOverrides{TargetFreeSpaceBytes: &tooLow, SampleBuckets: &buckets})

	if got.SampleBuckets != buckets {
		t.Errorf("SampleBuckets = %d, want %d", got.SampleBuckets, buckets)
	}
}
