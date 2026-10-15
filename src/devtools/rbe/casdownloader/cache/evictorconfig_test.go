package cache

import (
	"errors"
	"fmt"
	"strings"
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

// wantWarning fails the test unless one of warnings contains every one of
// parts: the flag and the value it is about, so that a host's log says what
// to fix.
func wantWarning(t *testing.T, warnings []string, parts ...string) {
	t.Helper()
	for _, w := range warnings {
		matched := true
		for _, p := range parts {
			if !strings.Contains(w, p) {
				matched = false
				break
			}
		}
		if matched {
			return
		}
	}
	t.Errorf("Warnings %q, want one that mentions %q", warnings, parts)
}

// The lock-based cache keeps taking -cache-min-free-space as before: a byte
// count, bare or with a unit, 1 GiB by default.
func TestParseLegacyMinFreeSpace_TakesByteCounts(t *testing.T) {
	for _, tc := range []struct {
		in   string
		want int64
	}{
		{in: "", want: DefaultLegacyMinFreeSpace},
		{in: "1073741824", want: gib},
		{in: "0", want: 0},
		{in: " 42 ", want: 42},
		{in: "4294967296", want: 4 * gib},
		{in: "2gb", want: 2 * gib},
		{in: "512MiB", want: 512 << 20},
		{in: "1.5gb", want: 3 * gib / 2},
		{in: "1048576b", want: 1 << 20},
	} {
		got, warnings := parseLegacyMinFreeSpace(tc.in)
		if got != tc.want || len(warnings) != 0 {
			t.Errorf("parseLegacyMinFreeSpace(%q) = %d, %q; want %d and no warnings", tc.in, got, warnings, tc.want)
		}
	}
	if DefaultLegacyMinFreeSpace != gib {
		t.Errorf("DefaultLegacyMinFreeSpace = %d, want 1 GiB as before", int64(DefaultLegacyMinFreeSpace))
	}
}

// Whatever is not a byte count means the 1 GiB default, with a warning, rather
// than costing the host its cache. A negative value in particular must never
// reach luci, which reads it as a huge unsigned number and evicts everything.
// A percentage and self-adaptive only mean something to the lock-free cache.
func TestParseLegacyMinFreeSpace_FallsBackToTheDefault(t *testing.T) {
	for _, in := range []string{"-1", "-1073741824", "-1gb", "lots", "1.5", "+5", "20%", SelfAdaptive, "Self-Adaptive"} {
		got, warnings := parseLegacyMinFreeSpace(in)
		if got != DefaultLegacyMinFreeSpace {
			t.Errorf("parseLegacyMinFreeSpace(%q) = %d, want the default of %d", in, got, int64(DefaultLegacyMinFreeSpace))
		}
		wantWarning(t, warnings, "-cache-min-free-space", in)
	}
}

func TestLockFreeWatermarks_DefaultsArePercentages(t *testing.T) {
	got, warnings := lockFreeWatermarks("", "", "-cache-target-free-space", noVolume(t))
	if want := (storage.Watermarks{Min: pct(20), Target: pct(25)}); got != want || len(warnings) != 0 {
		t.Errorf("lockFreeWatermarks(defaults) = %+v, %q; want %+v and no warnings", got, warnings, want)
	}
}

// self-adaptive is reserved. Until it exists, a launcher that already passes it
// gets the default rather than a broken cache.
func TestLockFreeWatermarks_SelfAdaptiveFallsBackToTheDefault(t *testing.T) {
	for _, in := range []string{SelfAdaptive, "Self-Adaptive", " self-adaptive "} {
		got, warnings := lockFreeWatermarks(in, "", "-cache-target-free-space", noVolume(t))
		if got.Min != pct(20) {
			t.Errorf("lockFreeWatermarks(%q).Min = %+v, want the 20%% default", in, got.Min)
		}
		wantWarning(t, warnings, "-cache-min-free-space", strings.TrimSpace(in))
	}
}

// A value that does not parse means that flag's default, with a warning that
// names the flag and the value. Only -cache-min-free-space reserves
// self-adaptive.
func TestLockFreeWatermarks_UnparsableMeansTheDefault(t *testing.T) {
	for _, tc := range []struct {
		min, target string
		flag, value string
		want        storage.Watermarks
	}{
		{min: "lots", flag: "-cache-min-free-space", value: "lots", want: storage.Watermarks{Min: pct(20), Target: pct(25)}},
		{min: "-5", target: "30%", flag: "-cache-min-free-space", value: "-5", want: storage.Watermarks{Min: pct(20), Target: pct(30)}},
		{min: "-5%", flag: "-cache-min-free-space", value: "-5%", want: storage.Watermarks{Min: pct(20), Target: pct(25)}},
		{min: "120%", flag: "-cache-min-free-space", value: "120%", want: storage.Watermarks{Min: pct(20), Target: pct(25)}},
		{min: "nan%", flag: "-cache-min-free-space", value: "nan%", want: storage.Watermarks{Min: pct(20), Target: pct(25)}},
		{min: "1.5", flag: "-cache-min-free-space", value: "1.5", want: storage.Watermarks{Min: pct(20), Target: pct(25)}},
		{min: "10%", target: "lots", flag: "-cache-target-free-space", value: "lots", want: storage.Watermarks{Min: pct(10), Target: pct(25)}},
		{min: "10%", target: SelfAdaptive, flag: "-cache-target-free-space", value: SelfAdaptive, want: storage.Watermarks{Min: pct(10), Target: pct(25)}},
		{min: "10%", target: "1e3gb", flag: "-cache-target-free-space", value: "1e3gb", want: storage.Watermarks{Min: pct(10), Target: pct(25)}},
	} {
		got, warnings := lockFreeWatermarks(tc.min, tc.target, "-cache-target-free-space", noVolume(t))
		if got != tc.want {
			t.Errorf("lockFreeWatermarks(%q, %q) = %+v, want %+v", tc.min, tc.target, got, tc.want)
		}
		wantWarning(t, warnings, tc.flag, tc.value)
	}
}

// Each bound of storage.StrictWatermarkBounds clamps: the minimum to 5-45% and
// the target to 10-50%. A minimum of 0 never trims and one near 100% trims all
// the time, so neither is honored on a test host.
func TestLockFreeWatermarks_ClampsEachBound(t *testing.T) {
	for _, tc := range []struct {
		name, min, target string
		want              storage.Watermarks
		clamped           string // Which watermark the warning is about.
	}{
		{name: "minimum below 5%", min: "2%", target: "25%", want: storage.Watermarks{Min: pct(5), Target: pct(25)}, clamped: "minimum free space"},
		{name: "minimum of 0%", min: "0%", target: "25%", want: storage.Watermarks{Min: pct(5), Target: pct(25)}, clamped: "minimum free space"},
		{name: "minimum above 45%", min: "80%", target: "50%", want: storage.Watermarks{Min: pct(45), Target: pct(50)}, clamped: "minimum free space"},
		{name: "target below 10%", min: "5%", target: "1%", want: storage.Watermarks{Min: pct(5), Target: pct(10)}, clamped: "target free space"},
		{name: "target above 50%", min: "20%", target: "90%", want: storage.Watermarks{Min: pct(20), Target: pct(50)}, clamped: "target free space"},
		{name: "target of 100%", min: "20%", target: "100%", want: storage.Watermarks{Min: pct(20), Target: pct(50)}, clamped: "target free space"},
	} {
		t.Run(tc.name, func(t *testing.T) {
			// All percentages: no statfs is needed.
			got, warnings := lockFreeWatermarks(tc.min, tc.target, "-cache-target-free-space", noVolume(t))
			if got != tc.want {
				t.Errorf("lockFreeWatermarks(%q, %q) = %+v, want %+v", tc.min, tc.target, got, tc.want)
			}
			wantWarning(t, warnings, "-cache-min-free-space", "-cache-target-free-space", tc.clamped)
		})
	}
}

// A byte count is checked as a share of the volume and clamped to the same
// bounds, so a value too large or too small for this host still gives a
// working cache. One within the bounds is kept as a byte count.
func TestLockFreeWatermarks_ClampsByteCountsAsAShareOfTheVolume(t *testing.T) {
	const total = 100 * gib
	for _, tc := range []struct {
		name, min, target string
		want              storage.Watermarks
	}{
		{name: "minimum too small", min: "1gb", target: "", want: storage.Watermarks{Min: pct(5), Target: pct(25)}},
		{name: "bare minimum too small", min: "1073741824", target: "30%", want: storage.Watermarks{Min: pct(5), Target: pct(30)}},
		{name: "zero minimum", min: "0", target: "", want: storage.Watermarks{Min: pct(5), Target: pct(25)}},
		{name: "both too large", min: "80gb", target: "90gb", want: storage.Watermarks{Min: pct(45), Target: pct(50)}},
		{name: "target larger than the volume", min: "20%", target: "1tb", want: storage.Watermarks{Min: pct(20), Target: pct(50)}},
	} {
		t.Run(tc.name, func(t *testing.T) {
			got, warnings := lockFreeWatermarks(tc.min, tc.target, "-cache-target-free-space", volumeOf(total))
			if got != tc.want {
				t.Errorf("lockFreeWatermarks(%q, %q) = %+v, want %+v", tc.min, tc.target, got, tc.want)
			}
			if len(warnings) == 0 {
				t.Errorf("lockFreeWatermarks(%q, %q) clamped without a warning", tc.min, tc.target)
			}
		})
	}
}

// The same, through the statfs that lockFreeEvictorConfig is given: the size
// of the volume comes from it, and the evictor gets the clamped values.
func TestLockFreeEvictorConfig_ClampsByteCountsThroughStatfs(t *testing.T) {
	statfs := func(string) (storage.DiskStats, error) {
		return storage.DiskStats{TotalBytes: 200 * gib, FreeBytes: 100 * gib}, nil
	}
	// 2 GiB of 200 GiB is 1%, below 5%; 30 GiB is 15%, kept as bytes.
	cfg, warnings := lockFreeEvictorConfig(t.TempDir(), "2gb", "30gb", "-cache-target-free-space", EvictorOverrides{}, statfs)
	if cfg.MinFreeSpace != "5%" || cfg.TargetFreeSpace != "32212254720" {
		t.Errorf("thresholds = %q, %q; want %q, %q", cfg.MinFreeSpace, cfg.TargetFreeSpace, "5%", "32212254720")
	}
	wantWarning(t, warnings, "-cache-min-free-space", "2147483648")
}

// The minimum is where a trim starts and the target where it stops. A target
// less than 5 points above the minimum is raised to the minimum plus 5 points
// rather than rejected: a mistyped tuning flag must not cost the host its
// cache.
func TestLockFreeWatermarks_RaisesATargetTooCloseToTheMinimum(t *testing.T) {
	const total = 100 * gib
	for _, tc := range []struct {
		name        string
		min, target string
		want        storage.SpaceThreshold
	}{
		{name: "percent below", min: "30%", target: "25%", want: pct(35)},
		{name: "percent equal", min: "30%", target: "30%", want: pct(35)},
		{name: "percent less than 5 points above", min: "30%", target: "33%", want: pct(35)},
		{name: "default target below an explicit minimum", min: "30%", target: "", want: pct(35)},
		{name: "clamped minimum", min: "97%", target: "40%", want: pct(50)},
		{name: "bytes equal", min: "10gb", target: "10gb", want: pct(15)},
		{name: "bytes below", min: "10gb", target: "5gb", want: pct(15)},
		{name: "percent minimum, byte target", min: "30%", target: "20gb", want: pct(35)},
		{name: "byte minimum, percent target", min: "10gb", target: "12%", want: pct(15)},
		{name: "zero target", min: "20%", target: "0", want: pct(25)},
	} {
		t.Run(tc.name, func(t *testing.T) {
			got, warnings := lockFreeWatermarks(tc.min, tc.target, "-cache-target-free-space", volumeOf(total))
			if got.Target != tc.want {
				t.Errorf("lockFreeWatermarks(%q, %q).Target = %+v, want %+v", tc.min, tc.target, got.Target, tc.want)
			}
			wantWarning(t, warnings, "-cache-target-free-space", "points above the minimum")
		})
	}
}

// -trim-target-free is held to the same rules as -cache-target-free-space,
// and the warnings name it.
func TestLockFreeWatermarks_TrimTargetFreeFollowsTheTargetsRules(t *testing.T) {
	for _, tc := range []struct {
		target string
		want   storage.SpaceThreshold
	}{
		{target: "35%", want: pct(35)},
		{target: "90%", want: pct(50)},
		{target: "1%", want: pct(25)},
		{target: "lots", want: pct(25)},
	} {
		got, warnings := lockFreeWatermarks("", tc.target, "-trim-target-free", noVolume(t))
		if got.Target != tc.want {
			t.Errorf("lockFreeWatermarks(-trim-target-free=%q).Target = %+v, want %+v", tc.target, got.Target, tc.want)
		}
		if tc.want != pct(35) {
			wantWarning(t, warnings, "-trim-target-free")
		}
	}
}

// Legal values are used exactly as before, without a warning.
func TestLockFreeWatermarks_KeepsLegalValuesAsGiven(t *testing.T) {
	for _, tc := range []struct {
		min, target string
		want        storage.Watermarks
	}{
		{min: "10%", target: "30%", want: storage.Watermarks{Min: pct(10), Target: pct(30)}},
		{min: "5%", target: "10%", want: storage.Watermarks{Min: pct(5), Target: pct(10)}},
		{min: "45%", target: "50%", want: storage.Watermarks{Min: pct(45), Target: pct(50)}},
		{min: "12.5%", target: "17.5%", want: storage.Watermarks{Min: pct(12.5), Target: pct(17.5)}},
		{min: "10gb", target: "20gb", want: storage.Watermarks{Min: byteCount(10 * gib), Target: byteCount(20 * gib)}},
		{min: "10737418240", target: "", want: storage.Watermarks{Min: byteCount(10 * gib), Target: pct(25)}},
		{min: "10%", target: "50gb", want: storage.Watermarks{Min: pct(10), Target: byteCount(50 * gib)}},
	} {
		got, warnings := lockFreeWatermarks(tc.min, tc.target, "-cache-target-free-space", volumeOf(100*gib))
		if got != tc.want || len(warnings) != 0 {
			t.Errorf("lockFreeWatermarks(%q, %q) = %+v, %q; want %+v and no warnings", tc.min, tc.target, got, warnings, tc.want)
		}
	}
}

// Percentages, including clamped and defaulted ones, are decided without
// asking for the volume's size, so the download path pays no statfs for them.
func TestLockFreeWatermarks_NeedsNoStatfsForPercentages(t *testing.T) {
	for _, tc := range []struct{ min, target string }{
		{min: "", target: ""},
		{min: "10%", target: "15%"},
		{min: "20%", target: "90%"},
		{min: "30%", target: "30%"},
		{min: "lots", target: ""},
	} {
		lockFreeWatermarks(tc.min, tc.target, "-cache-target-free-space", noVolume(t))
	}
}

// If the volume cannot be measured, byte counts are used as given and
// percentages are still clamped, with a warning that says why. Nothing fails:
// the evictor cannot run on a volume it cannot statfs anyway.
func TestLockFreeWatermarks_StatfsFailureKeepsByteCountsAsGiven(t *testing.T) {
	broken := func() (int64, error) { return 0, errors.New("statfs failed") }
	for _, tc := range []struct {
		min, target string
		want        storage.Watermarks
	}{
		{min: "30%", target: "20gb", want: storage.Watermarks{Min: pct(30), Target: byteCount(20 * gib)}},
		{min: "60%", target: "20gb", want: storage.Watermarks{Min: pct(45), Target: byteCount(20 * gib)}},
		{min: "1gb", target: "2gb", want: storage.Watermarks{Min: byteCount(gib), Target: byteCount(2 * gib)}},
	} {
		got, warnings := lockFreeWatermarks(tc.min, tc.target, "-cache-target-free-space", broken)
		if got != tc.want {
			t.Errorf("lockFreeWatermarks(%q, %q) = %+v, want %+v", tc.min, tc.target, got, tc.want)
		}
		wantWarning(t, warnings, "statfs failed")
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
	for _, w := range []storage.Watermarks{
		{Min: pct(20), Target: pct(25)},
		{Min: pct(0), Target: pct(100)},
		{Min: pct(12.5), Target: pct(17.5)},
		{Min: byteCount(0), Target: byteCount(1)},
		{Min: byteCount(gib), Target: byteCount(1 << 40)},
		{Min: pct(30), Target: byteCount(35 * gib)},
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

// The thresholds must reach the evictor as written, in canonical form: a byte
// count is bare, which the evictor accepts.
func TestNewEvictorConfig_ThresholdStrings(t *testing.T) {
	cfg := newEvictorConfig(storage.Watermarks{Min: pct(20), Target: byteCount(10 * gib)}, EvictorOverrides{})
	if cfg.MinFreeSpace != "20%" || cfg.TargetFreeSpace != "10737418240" {
		t.Errorf("thresholds = %q, %q; want %q, %q", cfg.MinFreeSpace, cfg.TargetFreeSpace, "20%", "10737418240")
	}
}

// The deliberate departures from casproxy's defaults. Anything not listed
// here should still be whatever common/storage says it is.
func TestNewEvictorConfig_CasdownloaderDefaults(t *testing.T) {
	shared := storage.DefaultEvictorConfig()
	if shared.ReservedSpaceGB == 0 || !shared.EmergencyLargeBlobFirst {
		t.Fatal("The shared defaults no longer reserve space or enable emergency mode, so this test proves nothing")
	}

	got := newEvictorConfig(storage.Watermarks{Min: pct(20), Target: pct(25)}, EvictorOverrides{})

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
	w := storage.Watermarks{Min: pct(20), Target: pct(25)}
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

	got := newEvictorConfig(storage.Watermarks{Min: pct(20), Target: pct(25)}, EvictorOverrides{BatchDelay: &zero})

	if got.BatchDelay != 0 {
		t.Errorf("BatchDelay = %v, want 0; an explicit zero was treated as unset", got.BatchDelay)
	}
}

// -cache-eviction-sample-buckets and -cache-eviction-batch-size document 0 as
// "use casdownloader's default". Passing that zero on to the evictor would
// select casproxy's defaults instead.
func TestNewEvictorConfig_NonPositiveCountsKeepTheCasdownloaderDefaults(t *testing.T) {
	for _, n := range []int{0, -1} {
		got := newEvictorConfig(storage.Watermarks{Min: pct(20), Target: pct(25)}, EvictorOverrides{SampleBuckets: &n, BatchSize: &n})
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
