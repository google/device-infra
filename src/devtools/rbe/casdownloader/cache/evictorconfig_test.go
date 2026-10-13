package cache

import (
	"errors"
	"fmt"
	"testing"
	"time"

	"github.com/google/device-infra/src/devtools/rbe/common/storage"
)

const gib = int64(1) << 30

func pct(p float64) storage.SpaceThreshold {
	return storage.SpaceThreshold{IsPercent: true, Percent: p}
}
func byteCount(b int64) storage.SpaceThreshold {
	return storage.SpaceThreshold{Bytes: b}
}

// volumeOf reports a volume of total bytes.
func volumeOf(total int64) func() (int64, error) {
	return func() (int64, error) { return total, nil }
}

// noVolume fails the test if the volume's size is asked for. It guards the
// cases that should be decided without a statfs on the download path.
func noVolume(t *testing.T) func() (int64, error) {
	return func() (int64, error) {
		t.Helper()
		t.Error("The volume's size was asked for, but these watermarks can be compared without it")
		return 0, errors.New("unexpected statfs")
	}
}

func TestParseSpace_AcceptsBytesAndPercentages(t *testing.T) {
	for _, tc := range []struct {
		in   string
		want storage.SpaceThreshold
	}{
		// A bare byte count is what -cache-min-free-space has always taken.
		{in: "1073741824", want: byteCount(gib)},
		{in: "0", want: byteCount(0)},
		{in: " 42 ", want: byteCount(42)},
		{in: "1gb", want: byteCount(gib)},
		{in: "512MiB", want: byteCount(512 << 20)},
		{in: "20%", want: pct(20)},
		{in: "25.5%", want: pct(25.5)},
	} {
		got, err := parseSpace(tc.in)
		if err != nil || got != tc.want {
			t.Errorf("parseSpace(%q) = %+v, %v; want %+v", tc.in, got, err, tc.want)
		}
	}
}

func TestParseSpace_RejectsWhatIsNeitherBytesNorAPercentage(t *testing.T) {
	for _, in := range []string{"", "lots", "15.5", "120%", "-5%"} {
		if got, err := parseSpace(in); err == nil {
			t.Errorf("parseSpace(%q) = %+v, want an error", in, got)
		}
	}
}

// The lock-based cache keeps taking -cache-min-free-space exactly as before:
// a byte count, 1 GiB by default.
func TestParseLegacyMinFreeSpace_KeepsTodaysBehavior(t *testing.T) {
	for _, tc := range []struct {
		in   string
		want int64
	}{
		{in: "", want: DefaultLegacyMinFreeSpace},
		{in: "1073741824", want: gib},
		{in: "0", want: 0},
		{in: "4294967296", want: 4 * gib},
		{in: "2gb", want: 2 * gib},
		// Passed through as the int64 flag passed it, for LocalCache to
		// interpret as it always has.
		{in: "-1", want: -1},
	} {
		got, err := parseLegacyMinFreeSpace(tc.in)
		if err != nil || got != tc.want {
			t.Errorf("parseLegacyMinFreeSpace(%q) = %d, %v; want %d", tc.in, got, err, tc.want)
		}
	}
	if DefaultLegacyMinFreeSpace != gib {
		t.Errorf("DefaultLegacyMinFreeSpace = %d, want 1 GiB as before", int64(DefaultLegacyMinFreeSpace))
	}
}

// A percentage and self-adaptive only mean something to the lock-free cache.
// LocalCache cannot honor them, so it rejects them rather than guessing.
func TestParseLegacyMinFreeSpace_RejectsLockFreeOnlyValues(t *testing.T) {
	for _, in := range []string{"20%", SelfAdaptive, "Self-Adaptive", "lots"} {
		if got, err := parseLegacyMinFreeSpace(in); err == nil {
			t.Errorf("parseLegacyMinFreeSpace(%q) = %d, want an error", in, got)
		}
	}
}

func TestLockFreeWatermarks_DefaultsArePercentages(t *testing.T) {
	got, err := lockFreeWatermarks("", "", "-cache-target-free-space", noVolume(t))
	if err != nil {
		t.Fatalf("lockFreeWatermarks failed: %v", err)
	}
	if want := (watermarks{min: pct(20), target: pct(25)}); got != want {
		t.Errorf("lockFreeWatermarks(defaults) = %+v, want %+v", got, want)
	}
}

// self-adaptive is reserved. Until it exists, a launcher that already passes it
// gets the default rather than a broken cache.
func TestLockFreeWatermarks_SelfAdaptiveFallsBackToTheDefault(t *testing.T) {
	for _, in := range []string{SelfAdaptive, "Self-Adaptive", " self-adaptive "} {
		got, err := lockFreeWatermarks(in, "", "-cache-target-free-space", noVolume(t))
		if err != nil {
			t.Errorf("lockFreeWatermarks(%q) failed: %v", in, err)
			continue
		}
		if got.min != pct(20) {
			t.Errorf("lockFreeWatermarks(%q).min = %+v, want the 20%% default", in, got.min)
		}
	}
}

// Only -cache-min-free-space reserves self-adaptive.
func TestLockFreeWatermarks_RejectsUnparsableValues(t *testing.T) {
	for _, tc := range []struct{ min, target string }{
		{min: "lots"},
		{target: "lots"},
		{target: SelfAdaptive},
		{min: "120%"},
	} {
		if got, err := lockFreeWatermarks(tc.min, tc.target, "-cache-target-free-space", volumeOf(100*gib)); err == nil {
			t.Errorf("lockFreeWatermarks(%q, %q) = %+v, want an error", tc.min, tc.target, got)
		}
	}
}

func TestLockFreeWatermarks_AcceptsBytesOrPercentages(t *testing.T) {
	for _, tc := range []struct {
		min, target string
		want        watermarks
	}{
		{min: "10%", target: "30%", want: watermarks{min: pct(10), target: pct(30)}},
		{min: "10gb", target: "20gb", want: watermarks{min: byteCount(10 * gib), target: byteCount(20 * gib)}},
		{min: "10737418240", target: "", want: watermarks{min: byteCount(10 * gib), target: pct(25)}},
		{min: "10%", target: "50gb", want: watermarks{min: pct(10), target: byteCount(50 * gib)}},
		// Zero means no trigger, so any positive target is above it.
		{min: "0", target: "", want: watermarks{min: byteCount(0), target: pct(25)}},
	} {
		got, err := lockFreeWatermarks(tc.min, tc.target, "-cache-target-free-space", volumeOf(100*gib))
		if err != nil || got != tc.want {
			t.Errorf("lockFreeWatermarks(%q, %q) = %+v, %v; want %+v", tc.min, tc.target, got, err, tc.want)
		}
	}
}

// A negative byte count would not survive the round trip through the
// evictor's parser, so it becomes zero, "no threshold", instead of taking the
// cache down.
func TestLockFreeWatermarks_ClampsANegativeByteCountToZero(t *testing.T) {
	got, err := lockFreeWatermarks("-5", "", "-cache-target-free-space", volumeOf(100*gib))
	if err != nil {
		t.Fatalf("lockFreeWatermarks failed: %v", err)
	}
	if got.min != byteCount(0) {
		t.Errorf("min = %+v, want 0 bytes", got.min)
	}
}

// The minimum is where a trim starts and the target where it stops. A target
// at or below the minimum would stop every trim before it reclaimed
// anything, so it is reset to the minimum plus 5 points of the volume rather
// than rejected: a mistyped tuning flag must not cost the host its cache.
func TestLockFreeWatermarks_ResetsATargetNotAboveTheMinimum(t *testing.T) {
	const total = 100 * gib
	for _, tc := range []struct {
		name        string
		min, target string
		want        storage.SpaceThreshold
	}{
		{name: "percent below", min: "30%", target: "25%", want: pct(35)},
		{name: "percent equal", min: "30%", target: "30%", want: pct(35)},
		{name: "default target below an explicit minimum", min: "30%", target: "", want: pct(35)},
		{name: "percent capped at 100", min: "97%", target: "50%", want: pct(100)},
		{name: "bytes equal", min: "10gb", target: "10gb", want: byteCount(15 * gib)},
		{name: "bytes below", min: "10gb", target: "5gb", want: byteCount(15 * gib)},
		{name: "percent minimum, byte target", min: "30%", target: "20gb", want: byteCount(35 * gib)},
		{name: "byte minimum, percent target", min: "10gb", target: "5%", want: byteCount(15 * gib)},
		{name: "zero target", min: "20%", target: "0", want: byteCount(25 * gib)},
	} {
		t.Run(tc.name, func(t *testing.T) {
			got, err := lockFreeWatermarks(tc.min, tc.target, "-cache-target-free-space", volumeOf(total))
			if err != nil {
				t.Fatalf("lockFreeWatermarks(%q, %q) failed: %v; a bad target must be reset, never fatal", tc.min, tc.target, err)
			}
			if got.target != tc.want {
				t.Errorf("lockFreeWatermarks(%q, %q).target = %+v, want %+v", tc.min, tc.target, got.target, tc.want)
			}
			if want, _ := parseLockFreeMinFreeSpace(tc.min); got.min != want {
				t.Errorf("min = %+v, want %+v; the minimum must survive whatever happens to the target", got.min, want)
			}
		})
	}
}

// The common cases are decided without asking for the volume's size, so the
// download path pays no statfs for them.
func TestLockFreeWatermarks_KeepsATargetAboveTheMinimumWithoutAStatfs(t *testing.T) {
	for _, tc := range []struct{ min, target string }{
		{min: "", target: ""},
		{min: "10%", target: "11%"},
		{min: "1gb", target: "2gb"},
		{min: "0", target: "25%"},
	} {
		got, err := lockFreeWatermarks(tc.min, tc.target, "-cache-target-free-space", noVolume(t))
		if err != nil {
			t.Errorf("lockFreeWatermarks(%q, %q) failed: %v", tc.min, tc.target, err)
			continue
		}
		wantTarget, _ := parseSpace(tc.target)
		if tc.target == "" {
			wantTarget = pct(25)
		}
		if got.target != wantTarget {
			t.Errorf("lockFreeWatermarks(%q, %q).target = %+v, want it kept at %+v", tc.min, tc.target, got.target, wantTarget)
		}
	}
}

// When a percentage and a byte count cannot be compared because the volume
// cannot be measured, the target is used as given. Nothing fails: the evictor
// cannot run on a volume it cannot statfs anyway.
func TestLockFreeWatermarks_KeepsAnUncheckableTargetAsGiven(t *testing.T) {
	broken := func() (int64, error) { return 0, errors.New("statfs failed") }

	got, err := lockFreeWatermarks("30%", "20gb", "-cache-target-free-space", broken)
	if err != nil {
		t.Fatalf("lockFreeWatermarks failed: %v; an unmeasurable volume must not be fatal", err)
	}
	if want := (watermarks{min: pct(30), target: byteCount(20 * gib)}); got != want {
		t.Errorf("lockFreeWatermarks = %+v, want %+v", got, want)
	}
}

func TestVolumeTotalOf_AsksTheNearestExistingAncestor(t *testing.T) {
	dir := t.TempDir()
	var asked []string
	statfs := func(path string) (storage.DiskStats, error) {
		asked = append(asked, path)
		if path != dir {
			return storage.DiskStats{}, errors.New("no such directory")
		}
		return storage.DiskStats{TotalBytes: 7 * gib}, nil
	}

	got, err := volumeTotalOf(dir+"/not/yet/created", statfs)()
	if err != nil || got != 7*gib {
		t.Errorf("volumeTotalOf = %d, %v; want %d from %s (asked %v)", got, err, 7*gib, dir, asked)
	}
}

// TestNewEvictorConfig_ProducesAConfigTheEvictorAccepts is the test that
// matters most for newEvictorConfig, because the failure it guards against is
// invisible at compile time and fatal at startup: NewEvictor rejects
// thresholds it cannot parse, and casdownloader builds those strings itself.
func TestNewEvictorConfig_ProducesAConfigTheEvictorAccepts(t *testing.T) {
	for _, w := range []watermarks{
		{min: pct(20), target: pct(25)},
		{min: pct(0), target: pct(100)},
		{min: pct(12.5), target: pct(17.5)},
		{min: byteCount(0), target: byteCount(1)},
		{min: byteCount(gib), target: byteCount(1 << 40)},
		{min: pct(30), target: byteCount(35 * gib)},
	} {
		cfg := newEvictorConfig(w, EvictorOverrides{})
		ev, err := storage.NewEvictor(t.TempDir(), cfg, nil)
		if err != nil {
			t.Errorf("newEvictorConfig(%+v) produced a config the evictor rejected: %v (min %q, target %q)", w, err, cfg.MinFreeSpace, cfg.TargetFreeSpace)
			continue
		}
		for name, s := range map[string]string{"MinFreeSpace": ev.Config().MinFreeSpace, "TargetFreeSpace": ev.Config().TargetFreeSpace} {
			if _, err := storage.ParseSpaceThreshold(s); err != nil {
				t.Errorf("%s %q does not parse: %v", name, s, err)
			}
		}
	}
}

// The thresholds must reach the evictor as written. A byte count carries its
// "b" suffix because the evictor rejects a bare integer.
func TestNewEvictorConfig_ThresholdStrings(t *testing.T) {
	cfg := newEvictorConfig(watermarks{min: pct(20), target: byteCount(10 * gib)}, EvictorOverrides{})
	if cfg.MinFreeSpace != "20%" || cfg.TargetFreeSpace != "10737418240b" {
		t.Errorf("thresholds = %q, %q; want %q, %q", cfg.MinFreeSpace, cfg.TargetFreeSpace, "20%", "10737418240b")
	}
}

// The deliberate departures from casproxy's defaults. Anything not listed
// here should still be whatever common/storage says it is.
func TestNewEvictorConfig_CasdownloaderDefaults(t *testing.T) {
	shared := storage.DefaultEvictorConfig()
	if shared.ReservedSpaceGB == 0 || !shared.EmergencyLargeBlobFirst {
		t.Fatal("The shared defaults no longer reserve space or enable emergency mode, so this test proves nothing")
	}

	got := newEvictorConfig(watermarks{min: pct(20), target: pct(25)}, EvictorOverrides{})

	want := shared
	want.MinFreeSpace = "20%"
	want.TargetFreeSpace = "25%"
	want.ReservedSpaceGB = 0
	want.EmergencyLargeBlobFirst = false
	want.SampleBuckets = 64
	want.BatchSize = 200
	if got != want {
		t.Errorf("newEvictorConfig() = %+v, want %+v", got, want)
	}
}

// Each override must reach exactly the field it names. A table keeps that
// honest: a copy-paste slip that wired two settings to the same field would
// pass any test that only checked the field it set.
func TestNewEvictorConfig_OverridesApplyIndividually(t *testing.T) {
	w := watermarks{min: pct(20), target: pct(25)}
	base := newEvictorConfig(w, EvictorOverrides{})

	buckets := 32
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
			got := newEvictorConfig(w, tc.overrides)
			if want := tc.want(base); got != want {
				t.Errorf("newEvictorConfig(%+v) = %+v, want %+v", tc.overrides, got, want)
			}
		})
	}
}

// A zero is a value a caller can legitimately mean -- "never pause between
// unlink batches" -- so an override carrying it must be honored rather than
// mistaken for an unset field. This is the reason the overrides are pointers.
func TestNewEvictorConfig_ZeroOverrideIsHonored(t *testing.T) {
	zero := time.Duration(0)

	got := newEvictorConfig(watermarks{min: pct(20), target: pct(25)}, EvictorOverrides{BatchDelay: &zero})

	if got.BatchDelay != 0 {
		t.Errorf("BatchDelay = %v, want 0; an explicit zero was treated as unset", got.BatchDelay)
	}
}

// -cache-eviction-sample-buckets and -cache-eviction-batch-size document 0 as
// "use casdownloader's default". Passing that zero on to the evictor would
// select casproxy's defaults instead.
func TestNewEvictorConfig_NonPositiveCountsKeepTheCasdownloaderDefaults(t *testing.T) {
	for _, n := range []int{0, -1} {
		got := newEvictorConfig(watermarks{min: pct(20), target: pct(25)}, EvictorOverrides{SampleBuckets: &n, BatchSize: &n})
		if got.SampleBuckets != 64 || got.BatchSize != 200 {
			t.Errorf("With overrides of %d: SampleBuckets = %d, BatchSize = %d; want 64 and 200", n, got.SampleBuckets, got.BatchSize)
		}
	}
}

// If no directory up to the root can be statfs'ed, the size of the volume is
// unknown, and the last error is returned rather than a size of zero.
func TestVolumeTotalOf_FailsWhenNothingUpToTheRootCanBeMeasured(t *testing.T) {
	errStatfs := errors.New("statfs failed")
	var asked []string
	statfs := func(dir string) (storage.DiskStats, error) {
		asked = append(asked, dir)
		return storage.DiskStats{}, errStatfs
	}

	got, err := volumeTotalOf("/no/such/cache", statfs)()
	if !errors.Is(err, errStatfs) || got != 0 {
		t.Errorf("volumeTotalOf = %d, %v; want 0 and %v", got, err, errStatfs)
	}
	if want := []string{"/no/such/cache", "/no/such", "/no", "/"}; fmt.Sprint(asked) != fmt.Sprint(want) {
		t.Errorf("volumeTotalOf asked %v, want every ancestor up to the root: %v", asked, want)
	}
}
