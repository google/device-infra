package storage

import (
	"strings"
	"testing"
)

const testGiB = int64(1) << 30

func percentFree(p float64) SpaceThreshold { return SpaceThreshold{IsPercent: true, Percent: p} }
func bytesFree(b int64) SpaceThreshold     { return SpaceThreshold{Bytes: b} }

func TestNormalizeWatermarks_Strict(t *testing.T) {
	const total = 100 * testGiB
	for _, tc := range []struct {
		name         string
		in           Watermarks
		total        int64
		want         Watermarks
		wantWarnings int
	}{
		// Within the bounds.
		{
			name: "defaults pass through",
			in:   Watermarks{Min: percentFree(20), Target: percentFree(25)}, total: total,
			want: Watermarks{Min: percentFree(20), Target: percentFree(25)},
		},
		{
			name: "lowest bounds pass through",
			in:   Watermarks{Min: percentFree(5), Target: percentFree(10)}, total: total,
			want: Watermarks{Min: percentFree(5), Target: percentFree(10)},
		},
		{
			name: "highest bounds pass through",
			in:   Watermarks{Min: percentFree(45), Target: percentFree(50)}, total: total,
			want: Watermarks{Min: percentFree(45), Target: percentFree(50)},
		},
		{
			name: "byte counts in range pass through as byte counts",
			in:   Watermarks{Min: bytesFree(10 * testGiB), Target: bytesFree(20 * testGiB)}, total: total,
			want: Watermarks{Min: bytesFree(10 * testGiB), Target: bytesFree(20 * testGiB)},
		},

		// Each bound clamps.
		{
			name: "minimum below range",
			in:   Watermarks{Min: percentFree(1), Target: percentFree(25)}, total: total,
			want:         Watermarks{Min: percentFree(5), Target: percentFree(25)},
			wantWarnings: 1,
		},
		{
			name: "minimum of zero",
			in:   Watermarks{Min: percentFree(0), Target: percentFree(25)}, total: total,
			want:         Watermarks{Min: percentFree(5), Target: percentFree(25)},
			wantWarnings: 1,
		},
		{
			name: "minimum above range",
			in:   Watermarks{Min: percentFree(46), Target: percentFree(50)}, total: total,
			want:         Watermarks{Min: percentFree(45), Target: percentFree(50)},
			wantWarnings: 1,
		},
		{
			name: "target below range",
			in:   Watermarks{Min: percentFree(5), Target: percentFree(7)}, total: total,
			want:         Watermarks{Min: percentFree(5), Target: percentFree(10)},
			wantWarnings: 1,
		},
		{
			name: "target above range",
			in:   Watermarks{Min: percentFree(20), Target: percentFree(80)}, total: total,
			want:         Watermarks{Min: percentFree(20), Target: percentFree(50)},
			wantWarnings: 1,
		},

		// Byte counts are converted to percentages and clamped.
		{
			name: "byte minimum below range",
			in:   Watermarks{Min: bytesFree(1 * testGiB), Target: percentFree(25)}, total: total,
			want:         Watermarks{Min: percentFree(5), Target: percentFree(25)},
			wantWarnings: 1,
		},
		{
			name: "byte minimum above range",
			in:   Watermarks{Min: bytesFree(80 * testGiB), Target: percentFree(50)}, total: total,
			want:         Watermarks{Min: percentFree(45), Target: percentFree(50)},
			wantWarnings: 1,
		},
		{
			name: "byte target above range",
			in:   Watermarks{Min: percentFree(20), Target: bytesFree(90 * testGiB)}, total: total,
			want:         Watermarks{Min: percentFree(20), Target: percentFree(50)},
			wantWarnings: 1,
		},
		{
			name: "byte target larger than the volume",
			in:   Watermarks{Min: percentFree(20), Target: bytesFree(10 * total)}, total: total,
			want:         Watermarks{Min: percentFree(20), Target: percentFree(50)},
			wantWarnings: 1,
		},

		// The gap.
		{
			name: "target too close to the minimum is raised",
			in:   Watermarks{Min: percentFree(30), Target: percentFree(32)}, total: total,
			want:         Watermarks{Min: percentFree(30), Target: percentFree(35)},
			wantWarnings: 1,
		},
		{
			name: "target equal to the minimum is raised",
			in:   Watermarks{Min: percentFree(30), Target: percentFree(30)}, total: total,
			want:         Watermarks{Min: percentFree(30), Target: percentFree(35)},
			wantWarnings: 1,
		},
		{
			name: "target below the minimum is raised",
			in:   Watermarks{Min: percentFree(30), Target: percentFree(25)}, total: total,
			want:         Watermarks{Min: percentFree(30), Target: percentFree(35)},
			wantWarnings: 1,
		},
		{
			name: "byte target too close to a percentage minimum is raised",
			in:   Watermarks{Min: percentFree(30), Target: bytesFree(20 * testGiB)}, total: total,
			want:         Watermarks{Min: percentFree(30), Target: percentFree(35)},
			wantWarnings: 1,
		},
		{
			name: "target too close to a byte minimum is raised",
			in:   Watermarks{Min: bytesFree(30 * testGiB), Target: bytesFree(31 * testGiB)}, total: total,
			want:         Watermarks{Min: bytesFree(30 * testGiB), Target: percentFree(35)},
			wantWarnings: 1,
		},
		{
			name: "zero byte target is clamped, then raised",
			in:   Watermarks{Min: percentFree(20), Target: bytesFree(0)}, total: total,
			want:         Watermarks{Min: percentFree(20), Target: percentFree(25)},
			wantWarnings: 2,
		},
		{
			// The highest minimum plus the gap is the highest target, so
			// the raised target is always in range.
			name: "45 plus 5 fits under 50",
			in:   Watermarks{Min: percentFree(100), Target: percentFree(100)}, total: total,
			want:         Watermarks{Min: percentFree(45), Target: percentFree(50)},
			wantWarnings: 2,
		},
		{
			name: "minimum clamped down, then target raised to the top",
			in:   Watermarks{Min: percentFree(90), Target: percentFree(40)}, total: total,
			want:         Watermarks{Min: percentFree(45), Target: percentFree(50)},
			wantWarnings: 2,
		},

		// The size of the volume is unknown.
		{
			name: "unknown size keeps byte counts as given",
			in:   Watermarks{Min: bytesFree(10 * testGiB), Target: bytesFree(20 * testGiB)}, total: 0,
			want:         Watermarks{Min: bytesFree(10 * testGiB), Target: bytesFree(20 * testGiB)},
			wantWarnings: 2,
		},
		{
			name: "unknown size still clamps percentages",
			in:   Watermarks{Min: percentFree(1), Target: percentFree(80)}, total: 0,
			want:         Watermarks{Min: percentFree(5), Target: percentFree(50)},
			wantWarnings: 2,
		},
		{
			name: "unknown size keeps a byte target not above a byte minimum",
			in:   Watermarks{Min: bytesFree(20 * testGiB), Target: bytesFree(10 * testGiB)}, total: 0,
			want:         Watermarks{Min: bytesFree(20 * testGiB), Target: bytesFree(10 * testGiB)},
			wantWarnings: 3,
		},
		{
			name: "negative size is unknown",
			in:   Watermarks{Min: percentFree(30), Target: bytesFree(20 * testGiB)}, total: -1,
			want:         Watermarks{Min: percentFree(30), Target: bytesFree(20 * testGiB)},
			wantWarnings: 1,
		},
	} {
		t.Run(tc.name, func(t *testing.T) {
			got, warnings, err := NormalizeWatermarks(tc.in, tc.total, StrictWatermarkBounds)
			if err != nil {
				t.Fatalf("NormalizeWatermarks(%+v, %d, Strict) failed: %v; strict bounds must never fail", tc.in, tc.total, err)
			}
			if got != tc.want {
				t.Errorf("NormalizeWatermarks(%+v, %d, Strict) = %+v, want %+v", tc.in, tc.total, got, tc.want)
			}
			if len(warnings) != tc.wantWarnings {
				t.Errorf("NormalizeWatermarks(%+v, %d, Strict) warned %d times, want %d: %q", tc.in, tc.total, len(warnings), tc.wantWarnings, warnings)
			}
		})
	}
}

// Whatever comes in, strict bounds hand back a pair that keeps the cache
// working: both values in range and the target at least 5 points up.
func TestNormalizeWatermarks_StrictResultIsAlwaysInRange(t *testing.T) {
	const total = 100 * testGiB
	var values []SpaceThreshold
	for _, p := range []float64{0, 1, 4.9, 5, 9.9, 10, 20, 44.9, 45, 45.1, 49.9, 50, 50.1, 99, 100} {
		values = append(values, percentFree(p), bytesFree(int64(p*float64(total)/100)))
	}
	b := StrictWatermarkBounds
	for _, minFree := range values {
		for _, target := range values {
			in := Watermarks{Min: minFree, Target: target}
			got, _, err := NormalizeWatermarks(in, total, b)
			if err != nil {
				t.Errorf("NormalizeWatermarks(%+v) failed: %v", in, err)
				continue
			}
			minPct := float64(got.Min.Evaluate(total)) / float64(total) * 100
			targetPct := float64(got.Target.Evaluate(total)) / float64(total) * 100
			// Evaluate truncates to whole bytes, so allow a few bytes' worth.
			const slack = 1e-6
			if minPct < b.MinLow-slack || minPct > b.MinHigh+slack ||
				targetPct < b.TargetLow-slack || targetPct > b.TargetHigh+slack ||
				targetPct < minPct+b.Gap-slack {
				t.Errorf("NormalizeWatermarks(%+v) = %+v (%.6g%%, %.6g%%), want min in [%g, %g], target in [%g, %g] and at least %g points above min",
					in, got, minPct, targetPct, b.MinLow, b.MinHigh, b.TargetLow, b.TargetHigh, b.Gap)
			}
		}
	}
}

// Tradefed mirrors the strict numbers, and clamping relies on a clamped
// minimum leaving room for the gap below the highest target.
func TestWatermarkBoundsPresets(t *testing.T) {
	want := WatermarkBounds{MinLow: 5, MinHigh: 45, TargetLow: 10, TargetHigh: 50, Gap: 5, Clamp: true}
	if StrictWatermarkBounds != want {
		t.Errorf("StrictWatermarkBounds = %+v, want %+v", StrictWatermarkBounds, want)
	}
	if b := StrictWatermarkBounds; b.MinHigh+b.Gap > b.TargetHigh {
		t.Errorf("StrictWatermarkBounds: MinHigh %g + Gap %g > TargetHigh %g, so a clamped minimum can leave no legal target", b.MinHigh, b.Gap, b.TargetHigh)
	}
	want = WatermarkBounds{MinLow: 1, MinHigh: 50, TargetLow: 2, TargetHigh: 60, Gap: 1}
	if FlexibleWatermarkBounds != want {
		t.Errorf("FlexibleWatermarkBounds = %+v, want %+v", FlexibleWatermarkBounds, want)
	}
}

func TestNormalizeWatermarks_Flexible(t *testing.T) {
	const total = 100 * testGiB
	for _, tc := range []struct {
		name         string
		in           Watermarks
		total        int64
		wantErr      bool
		wantWarnings int
	}{
		// Within the bounds: returned unchanged.
		{name: "casproxy defaults", in: Watermarks{Min: percentFree(15), Target: percentFree(25)}, total: total},
		{name: "lowest bounds", in: Watermarks{Min: percentFree(1), Target: percentFree(2)}, total: total},
		{name: "highest bounds", in: Watermarks{Min: percentFree(50), Target: percentFree(60)}, total: total},
		{name: "outside the strict bounds", in: Watermarks{Min: percentFree(48), Target: percentFree(49.5)}, total: total},
		{name: "byte counts", in: Watermarks{Min: bytesFree(10 * testGiB), Target: bytesFree(11 * testGiB)}, total: total},
		{name: "mixed", in: Watermarks{Min: percentFree(10), Target: bytesFree(20 * testGiB)}, total: total},
		{name: "byte counts on a volume of unknown size", in: Watermarks{Min: bytesFree(90 * testGiB), Target: bytesFree(95 * testGiB)}, wantWarnings: 2},

		// Each bound rejects.
		{name: "minimum of 0%", in: Watermarks{Min: percentFree(0), Target: percentFree(25)}, total: total, wantErr: true},
		{name: "minimum below 1%", in: Watermarks{Min: percentFree(0.9), Target: percentFree(25)}, total: total, wantErr: true},
		{name: "minimum above 50%", in: Watermarks{Min: percentFree(50.1), Target: percentFree(60)}, total: total, wantErr: true},
		{name: "target below 2%", in: Watermarks{Min: percentFree(1), Target: percentFree(1.9)}, total: total, wantErr: true},
		{name: "target above 60%", in: Watermarks{Min: percentFree(15), Target: percentFree(60.1)}, total: total, wantErr: true},
		{name: "target of 100%", in: Watermarks{Min: percentFree(15), Target: percentFree(100)}, total: total, wantErr: true},

		// The gap.
		{name: "target less than 1 point above the minimum", in: Watermarks{Min: percentFree(20), Target: percentFree(20.9)}, total: total, wantErr: true},
		{name: "target below the minimum", in: Watermarks{Min: percentFree(30), Target: percentFree(20)}, total: total, wantErr: true},

		// Byte counts are converted to percentages and checked.
		{name: "zero bytes", in: Watermarks{Min: bytesFree(0), Target: percentFree(25)}, total: total, wantErr: true},
		{name: "byte target above 60%", in: Watermarks{Min: percentFree(15), Target: bytesFree(61 * testGiB)}, total: total, wantErr: true},
		{name: "byte target less than 1 point above a byte minimum", in: Watermarks{Min: bytesFree(20 * testGiB), Target: bytesFree(20*testGiB + testGiB/2)}, total: total, wantErr: true},
		{name: "byte target not above a byte minimum on a volume of unknown size", in: Watermarks{Min: bytesFree(2 * testGiB), Target: bytesFree(2 * testGiB)}, wantErr: true, wantWarnings: 2},
	} {
		t.Run(tc.name, func(t *testing.T) {
			got, warnings, err := NormalizeWatermarks(tc.in, tc.total, FlexibleWatermarkBounds)
			if gotErr := err != nil; gotErr != tc.wantErr {
				t.Errorf("NormalizeWatermarks(%+v, %d, Flexible) error = %v, want error: %t", tc.in, tc.total, err, tc.wantErr)
			}
			if got != tc.in {
				t.Errorf("NormalizeWatermarks(%+v, %d, Flexible) = %+v, want it unchanged", tc.in, tc.total, got)
			}
			if len(warnings) != tc.wantWarnings {
				t.Errorf("NormalizeWatermarks(%+v, %d, Flexible) warned %d times, want %d: %q", tc.in, tc.total, len(warnings), tc.wantWarnings, warnings)
			}
		})
	}
}

// casproxy shows the error to an operator, who should be able to fix every
// problem in one go.
func TestNormalizeWatermarks_FlexibleErrorListsEveryViolation(t *testing.T) {
	_, _, err := NormalizeWatermarks(Watermarks{Min: percentFree(0), Target: percentFree(100)}, 100*testGiB, FlexibleWatermarkBounds)
	if err == nil {
		t.Fatal("NormalizeWatermarks(0%, 100%, Flexible) succeeded, want an error")
	}
	for _, want := range []string{"minimum free space 0%", "target free space 100%"} {
		if !strings.Contains(err.Error(), want) {
			t.Errorf("NormalizeWatermarks(0%%, 100%%, Flexible) error = %q, want it to mention %q", err, want)
		}
	}
}
