package cache

import (
	"github.com/google/device-infra/src/devtools/rbe/common/monitoring"
)

// MonitoringStats describes what c did since it was created, for export as
// metrics. c may be nil, for a run without a cache.
//
// Activity is filled in only from caches that report it. Leaving it nil for
// the others, rather than zero, keeps runs on LocalCache or without a cache
// from emitting counters that would read as a lock-free cache with nothing to
// report.
func MonitoringStats(c Cache) *monitoring.LocalCacheStats {
	stats := &monitoring.LocalCacheStats{Impl: Impl(c)}
	modes, hasModes := c.(ModeCopyReporter)
	store, hasStore := c.(StorageStatsReporter)
	if !hasModes && !hasStore {
		return stats
	}
	a := &monitoring.LocalCacheActivity{}
	if hasModes {
		m := modes.ModeCopies()
		a.ExecMismatchCopies = m.ExecMismatchCopies
		a.ExecMismatchBytes = m.ExecMismatchBytes
		a.PermDriftHits = m.PermDriftHits
		a.UnreadableRepairs = m.UnreadableRepairs
	}
	if hasStore {
		s := store.StorageStats()
		a.CorruptBlobs = s.CorruptBlobs
		a.CopyFallbackEMLINK = s.CopyFallbackEMLINK
		a.CopyFallbackEXDEV = s.CopyFallbackEXDEV
		a.CopyFallbackOther = s.CopyFallbackOther
		a.HeadroomChecks = s.HeadroomChecks
		a.HeadroomEvictions = s.HeadroomEvictions
		a.HeadroomReclaimedBytes = s.HeadroomReclaimedBytes
		a.HeadroomPeerWaits = s.HeadroomPeerWaits
		a.HeadroomBelowWatermark = s.HeadroomBelowWatermark
	}
	stats.Activity = a
	return stats
}
