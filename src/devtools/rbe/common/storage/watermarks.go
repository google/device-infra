package storage

import (
	"errors"
	"fmt"
	"strings"
)

// Watermarks are a pair of free-space thresholds: eviction starts when free
// space falls below Min and stops once it reaches Target.
type Watermarks struct {
	Min, Target SpaceThreshold
}

// WatermarkBounds are the legal values of a pair of watermarks, in
// percentage points of the volume, and what NormalizeWatermarks does with a
// pair outside them. The bounds and the gap are inclusive.
// StrictWatermarkBounds and FlexibleWatermarkBounds are the two sets in use.
//
// With Clamp, the bounds must leave room for the gap, MinHigh+Gap <=
// TargetHigh. Otherwise raising the target above a clamped minimum can take
// it past TargetHigh, and NormalizeWatermarks keeps the gap rather than the
// bound.
type WatermarkBounds struct {
	// MinLow and MinHigh bound the minimum.
	MinLow, MinHigh float64
	// TargetLow and TargetHigh bound the target.
	TargetLow, TargetHigh float64
	// Gap is how many points, at least, the target must be above the
	// minimum.
	Gap float64
	// Clamp selects what happens to a pair outside the bounds. With Clamp,
	// a value outside its bounds is moved to the nearest one, and a target
	// less than Gap points above the minimum is raised to exactly Gap
	// points above it, each with a warning, so the result is always
	// usable. Without Clamp, each of those is an error.
	Clamp bool
}

var (
	// StrictWatermarkBounds are for caches on shared test hosts:
	// casdownloader's lock-free cache, and Tradefed, which mirrors them.
	//
	// The minimum must be 5-45% and the target 10-50%, at least 5 points
	// above the minimum. A minimum of 0 never evicts, and one near 100%
	// evicts everything, and on these hosts either leaves the cache
	// useless. Their configuration changes only with a release, so a bad
	// value is clamped into range, with a warning, rather than rejected,
	// which would cost every affected host its cache until the next one.
	// A minimum of at most 45% leaves room for a target 5 points above it.
	StrictWatermarkBounds = WatermarkBounds{
		MinLow: 5, MinHigh: 45,
		TargetLow: 10, TargetHigh: 50,
		Gap:   5,
		Clamp: true,
	}

	// FlexibleWatermarkBounds are for casproxy, which runs on dedicated,
	// monitored hosts. The minimum must be 1-50% and the target 2-60%, at
	// least 1 point above the minimum. casproxy's percentages are of its
	// effective total, the volume less ReservedSpaceGB.
	//
	// The bounds keep out values that break the evictor rather than tune
	// it. A minimum of 0% never starts a flight, since free space, which
	// is clamped at 0, is never below it. A minimum near 100% starts one
	// every CheckInterval that walks the whole cache towards a target it
	// cannot reach, wiping the cache constantly, and a target of 100% wipes
	// it on every flight. A target at or below the minimum makes every
	// flight a no-op while the disk fills.
	//
	// A value outside them is rejected rather than clamped, so that
	// casproxy's operators see the mistake and fix it: casproxy fails at
	// startup on a bad flag, and rejects a bad live update while keeping
	// its old config.
	FlexibleWatermarkBounds = WatermarkBounds{
		MinLow: 1, MinHigh: 50,
		TargetLow: 2, TargetHigh: 60,
		Gap: 1,
	}
)

// NormalizeWatermarks checks w against b and returns the watermarks to use,
// along with a warning for each value it changed or could not check.
//
// total is the size, in bytes, that percentages are of: for an evictor,
// its effective total after ReservedSpaceGB. A byte count is checked as a
// percentage of total. If it is within its bounds it is returned as given,
// still a byte count; if it is clamped, the result is the bound, a
// percentage. If total is 0 or less, meaning the size is unknown, byte
// counts are returned as given, with a warning, though two of them are still
// compared with each other.
//
// With b.Clamp the error is always nil. Without it, an error lists every
// violation, and w is returned unchanged.
func NormalizeWatermarks(w Watermarks, total int64, b WatermarkBounds) (Watermarks, []string, error) {
	n := normalizer{total: total, bounds: b}
	minFree := n.check("minimum", w.Min, b.MinLow, b.MinHigh)
	target := n.check("target", w.Target, b.TargetLow, b.TargetHigh)

	switch {
	case minFree.known && target.known && target.percent < minFree.percent+b.Gap:
		if !b.Clamp {
			n.violatef("target free space %s%s must be at least %g points above the minimum of %s%s", target.t, n.ofVolume(target), b.Gap, minFree.t, n.ofVolume(minFree))
			break
		}
		raised := SpaceThreshold{IsPercent: true, Percent: minFree.percent + b.Gap}
		n.warnf("target free space %s%s is less than %g points above the minimum of %s%s; using %s", target.t, n.ofVolume(target), b.Gap, minFree.t, n.ofVolume(minFree), raised)
		target = checked{t: raised, percent: raised.Percent, known: true}
	case total <= 0 && !minFree.t.IsPercent && !target.t.IsPercent && target.t.Bytes <= minFree.t.Bytes:
		// Two byte counts on a volume of unknown size: the target is known
		// to be too low, but not by how many points.
		if b.Clamp {
			n.warnf("target free space %s is not above the minimum of %s, and the size of the volume is unknown; using it as given", target.t, minFree.t)
		} else {
			n.violatef("target free space %s must be above the minimum of %s", target.t, minFree.t)
		}
	}

	if len(n.violations) > 0 {
		return w, n.warnings, fmt.Errorf("invalid free-space watermarks: %s", strings.Join(n.violations, "; "))
	}
	return Watermarks{Min: minFree.t, Target: target.t}, n.warnings, nil
}

// WatermarkError is the error CheckWatermarks returns, with its violations
// sorted by the watermark at fault, so that a caller can tell a bad value
// from a bad pair.
type WatermarkError struct {
	// Min and Target say why that watermark is invalid by itself: it does
	// not parse, or it is outside its own bounds. Each is nil if that
	// watermark is valid by itself.
	Min, Target error
	// Pair says why two watermarks, each valid by itself, are invalid
	// together: the target is not far enough above the minimum. It is set
	// only if Min and Target are both nil.
	Pair error
}

func (e *WatermarkError) Error() string {
	if e.Min == nil && e.Target == nil && e.Pair != nil {
		return e.Pair.Error()
	}
	var msgs []string
	for _, err := range []error{e.Min, e.Target} {
		if err != nil {
			msgs = append(msgs, err.Error())
		}
	}
	return "invalid free-space watermarks: " + strings.Join(msgs, "; ")
}

// CheckWatermarks checks the watermarks of cfg against b the way an evictor
// for dir evaluates them: percentages are of the effective total, the size
// of the volume holding dir less cfg.ReservedSpaceGB, and a byte count is
// checked as a percentage of that. It returns a *WatermarkError if either
// watermark does not parse or, without b.Clamp, if the pair is outside b,
// and a warning for each value it could not check.
//
// The volume is measured with statfs, or RealStatfs if statfs is nil, and
// only when a watermark is a byte count. If that fails, its size is treated
// as unknown, as NormalizeWatermarks describes.
func CheckWatermarks(cfg EvictorConfig, dir string, statfs StatfsFunc, b WatermarkBounds) ([]string, error) {
	var werr WatermarkError
	minFree, err := ParseSpaceThreshold(cfg.MinFreeSpace)
	if err != nil {
		werr.Min = fmt.Errorf("invalid min-free-space: %w", err)
	}
	target, err := ParseSpaceThreshold(cfg.TargetFreeSpace)
	if err != nil {
		werr.Target = fmt.Errorf("invalid target-free-space: %w", err)
	}

	var warnings []string
	var total int64 // 0 tells NormalizeWatermarks that the size is unknown.
	if (werr.Min == nil && !minFree.IsPercent) || (werr.Target == nil && !target.IsPercent) {
		if statfs == nil {
			statfs = RealStatfs
		}
		if st, err := statfs(dir); err != nil {
			warnings = append(warnings, fmt.Sprintf("cannot measure the volume of %s: %v", dir, err))
		} else {
			total, _, _ = ComputeEffectiveSpace(st, cfg.ReservedSpaceGB*1024*1024*1024)
		}
	}

	// Each watermark that parses is checked by itself first, so that one
	// that is fine is not blamed for the other.
	if werr.Min == nil {
		werr.Min = outOfBounds("minimum", minFree, total, b, b.MinLow, b.MinHigh)
	}
	if werr.Target == nil {
		werr.Target = outOfBounds("target", target, total, b, b.TargetLow, b.TargetHigh)
	}
	if werr.Min != nil || werr.Target != nil {
		return warnings, &werr
	}
	_, normWarnings, err := NormalizeWatermarks(Watermarks{Min: minFree, Target: target}, total, b)
	warnings = append(warnings, normWarnings...)
	if err != nil {
		// Both are within their bounds, so what is left is the pair.
		werr.Pair = err
		return warnings, &werr
	}
	return warnings, nil
}

// outOfBounds returns the violation, worded as NormalizeWatermarks words it,
// if the watermark t, named name, is outside [low, high] percent of total,
// and nil otherwise, including if total is unknown and t is a byte count.
func outOfBounds(name string, t SpaceThreshold, total int64, b WatermarkBounds, low, high float64) error {
	n := normalizer{total: total, bounds: b}
	n.check(name, t, low, high)
	if len(n.violations) == 0 {
		return nil
	}
	return errors.New(n.violations[0])
}

// normalizer collects the warnings and violations NormalizeWatermarks
// reports.
type normalizer struct {
	total      int64
	bounds     WatermarkBounds
	warnings   []string
	violations []string
}

// checked is a watermark after its bounds have been checked.
type checked struct {
	t SpaceThreshold // The value to use.
	// percent is t as a percentage of the volume, if known is true.
	percent float64
	known   bool
}

// check checks the watermark t, named name in messages, against [low, high].
func (n *normalizer) check(name string, t SpaceThreshold, low, high float64) checked {
	c := checked{t: t, percent: t.Percent, known: t.IsPercent}
	if !t.IsPercent && n.total > 0 {
		// Multiplying first keeps a whole percentage exact: 30 GiB of 100
		// GiB is 30%, not 30.000000000000004%.
		c.percent = float64(t.Bytes) * 100 / float64(n.total)
		c.known = true
	}
	if !c.known {
		n.warnf("%s free space %s cannot be checked against its bounds because the size of the volume is unknown; using it as given", name, t)
		return c
	}

	switch {
	case n.bounds.Clamp && c.percent < low:
		return n.clamp(name, c, low, "below the lowest")
	case n.bounds.Clamp && c.percent > high:
		return n.clamp(name, c, high, "above the highest")
	case !n.bounds.Clamp && (c.percent < low || c.percent > high):
		n.violatef("%s free space %s%s must be from %g%% to %g%%", name, t, n.ofVolume(c), low, high)
	}
	return c
}

// clamp returns c moved to bound, with a warning that says it was where.
func (n *normalizer) clamp(name string, c checked, bound float64, where string) checked {
	clamped := SpaceThreshold{IsPercent: true, Percent: bound}
	n.warnf("%s free space %s%s is %s allowed, %g%%; using %s", name, c.t, n.ofVolume(c), where, bound, clamped)
	return checked{t: clamped, percent: bound, known: true}
}

// ofVolume describes a byte count c as a percentage of the volume, for
// messages. It returns "" for a percentage.
func (n *normalizer) ofVolume(c checked) string {
	if c.t.IsPercent {
		return ""
	}
	return fmt.Sprintf(" (%.4g%% of %d bytes)", c.percent, n.total)
}

func (n *normalizer) warnf(format string, args ...any) {
	n.warnings = append(n.warnings, fmt.Sprintf(format, args...))
}

func (n *normalizer) violatef(format string, args ...any) {
	n.violations = append(n.violations, fmt.Sprintf(format, args...))
}
