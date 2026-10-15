package storage

import (
	"math"
	"strings"
	"testing"
)

// rejected marks an input of spaceThresholdGrammar that ParseSpaceThreshold
// must reject.
const rejected = ""

// spaceThresholdGrammar is the shared table of inputs for the free-space
// threshold format of b/572891754. Tradefed's SpaceThreshold mirrors
// ParseSpaceThreshold and its tests mirror this table, so change both
// together.
//
// want is the canonical form, as String formats it, of what the input
// parses to, or rejected.
var spaceThresholdGrammar = []struct{ in, want string }{
	// Byte counts.
	{"0", "0"},
	{"25", "25"},
	{"1073741824", "1073741824"},
	{"007", "7"},
	{" 42 ", "42"},
	{"9223372036854775807", "9223372036854775807"},

	// Sizes: units are case-insensitive and all binary.
	{"1b", "1"},
	{"1024b", "1024"},
	{"1073741824b", "1073741824"},
	{"1k", "1024"},
	{"1kb", "1024"},
	{"1kib", "1024"},
	{"1KiB", "1024"},
	{"1024kb", "1048576"},
	{"1m", "1048576"},
	{"1mb", "1048576"},
	{"1mib", "1048576"},
	{"100mb", "104857600"},
	{"512MiB", "536870912"},
	{"1g", "1073741824"},
	{"1gb", "1073741824"},
	{"1gib", "1073741824"},
	{"1GB", "1073741824"},
	{"400gb", "429496729600"},
	{"500GB", "536870912000"},
	{"10 GB", "10737418240"},
	{"1t", "1099511627776"},
	{"1tb", "1099511627776"},
	{"1tib", "1099511627776"},
	{"2tb", "2199023255552"},
	{"8388607t", "9223370937343148032"},
	{"9223372036854775807b", "9223372036854775807"},

	// A size may have a fractional part, except in bytes, and is truncated
	// to whole bytes.
	{"1.5gb", "1610612736"},
	{"1.5 TiB", "1649267441664"},
	{"0.5k", "512"},
	{"0.0001kb", "0"},
	{"1.5b", rejected},
	{"1.0B", rejected},

	// Percentages, from 0 to 100.
	{"0%", "0%"},
	{"15%", "15%"},
	{"15.5%", "15.5%"},
	{"15.0%", "15%"},
	{"25.0%", "25%"},
	{"012.50%", "12.5%"},
	{"20 %", "20%"},
	{" 20% ", "20%"},
	{"100%", "100%"},
	{"100.0%", "100%"},

	// Empty.
	{"", rejected},
	{"   ", rejected},

	// Signs.
	{"-1", rejected},
	{"+1", rejected},
	{"-500gb", rejected},
	{"+1gb", rejected},
	{"-5%", rejected},
	{"+5%", rejected},
	{"-0", rejected},

	// A fractional part needs a unit or "%".
	{"1.5", rejected},
	{"15.5", rejected},
	{".5gb", rejected},
	{"5.gb", rejected},
	{".5%", rejected},
	{"5.%", rejected},
	{"1.5.5gb", rejected},

	// Percentages out of range.
	{"100.1%", rejected},
	{"150%", rejected},

	// Exponents, NaN, Inf and hex.
	{"1e3", rejected},
	{"1E3", rejected},
	{"1e3gb", rejected},
	{"1e2%", rejected},
	{"nan", rejected},
	{"nan%", rejected},
	{"NaN%", rejected},
	{"inf", rejected},
	{"infgb", rejected},
	{"inf%", rejected},
	{"0x10", rejected},
	{"0x10gb", rejected},
	{"0x10%", rejected},

	// Unknown units and malformed input.
	{"10pb", rejected},
	{"10 bytes", rejected},
	{"10gbb", rejected},
	{"10%gb", rejected},
	{"10gb%", rejected},
	{"gb", rejected},
	{"%", rejected},
	{"1,000", rejected},
	{"1_000", rejected},
	{"1 000", rejected},
	{"invalid", rejected},

	// Too many bytes for an int64.
	{"9223372036854775808", rejected},
	{"9223372036854775808b", rejected},
	{"8388608t", rejected},
	{"99999999999999999999tb", rejected},
}

func TestParseSpaceThreshold_Grammar(t *testing.T) {
	for _, tc := range spaceThresholdGrammar {
		got, err := ParseSpaceThreshold(tc.in)
		if tc.want == rejected {
			if err == nil {
				t.Errorf("ParseSpaceThreshold(%q) = %v, want an error", tc.in, got)
			}
			continue
		}
		if err != nil {
			t.Errorf("ParseSpaceThreshold(%q) failed: %v; want %s", tc.in, err, tc.want)
			continue
		}
		if got.String() != tc.want {
			t.Errorf("ParseSpaceThreshold(%q) = %v, want %s", tc.in, got, tc.want)
		}
		if wantPercent := strings.HasSuffix(tc.want, "%"); got.IsPercent != wantPercent {
			t.Errorf("ParseSpaceThreshold(%q).IsPercent = %t, want %t", tc.in, got.IsPercent, wantPercent)
		}
	}
}

// An error used to wrap a nil error, which formats as "%!w(<nil>)".
func TestParseSpaceThreshold_ErrorsAreReadable(t *testing.T) {
	for _, tc := range spaceThresholdGrammar {
		if tc.want != rejected {
			continue
		}
		_, err := ParseSpaceThreshold(tc.in)
		if err == nil {
			continue // Reported by TestParseSpaceThreshold_Grammar.
		}
		if msg := err.Error(); strings.Contains(msg, "%!") {
			t.Errorf("ParseSpaceThreshold(%q) error = %q, which has a formatting error", tc.in, msg)
		}
	}
}

// The canonical form is what components pass to each other, so anything the
// parser returns must survive a trip through String, or StringWithUnit,
// unchanged.
func TestSpaceThreshold_StringRoundTrips(t *testing.T) {
	var thresholds []SpaceThreshold
	for _, tc := range spaceThresholdGrammar {
		if got, err := ParseSpaceThreshold(tc.in); err == nil {
			thresholds = append(thresholds, got)
		}
	}
	for _, p := range []float64{0, 1e-9, 0.1, 12.5, 100.0 / 3, 99.99999999999999, 100} {
		thresholds = append(thresholds, SpaceThreshold{IsPercent: true, Percent: p})
	}
	for _, b := range []int64{0, 1, 1 << 30, math.MaxInt64} {
		thresholds = append(thresholds, SpaceThreshold{Bytes: b})
	}

	for _, want := range thresholds {
		for _, s := range []string{want.String(), want.StringWithUnit()} {
			got, err := ParseSpaceThreshold(s)
			if err != nil || got != want {
				t.Errorf("ParseSpaceThreshold(%q) = %+v, %v; want %+v, the value it was formatted from", s, got, err, want)
			}
		}
	}
}

func TestSpaceThreshold_String(t *testing.T) {
	for _, tc := range []struct {
		t              SpaceThreshold
		want, withUnit string
	}{
		{SpaceThreshold{Bytes: 10 << 30}, "10737418240", "10737418240b"},
		{SpaceThreshold{Bytes: 0}, "0", "0b"},
		{SpaceThreshold{IsPercent: true, Percent: 20}, "20%", "20%"},
		{SpaceThreshold{IsPercent: true, Percent: 12.5}, "12.5%", "12.5%"},
		{SpaceThreshold{IsPercent: true, Percent: 1e-7}, "0.0000001%", "0.0000001%"},
	} {
		if got := tc.t.String(); got != tc.want {
			t.Errorf("%+v.String() = %q, want %q", tc.t, got, tc.want)
		}
		if got := tc.t.StringWithUnit(); got != tc.withUnit {
			t.Errorf("%+v.StringWithUnit() = %q, want %q", tc.t, got, tc.withUnit)
		}
	}
}
