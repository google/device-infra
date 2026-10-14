package cache

import (
	"github.com/google/device-infra/src/devtools/rbe/common/monitoring"
	"github.com/google/device-infra/src/devtools/rbe/common/storage"
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
	}
	stats.Activity = a
	return stats
}

// TrimMonitoringStats describes the -trim-only pass res reports, for export
// as metrics.
func TrimMonitoringStats(res storage.TrimResult) *monitoring.TrimStats {
	stats := &monitoring.TrimStats{
		Outcome:            res.Outcome,
		Duration:           res.Duration,
		BytesReclaimed:     res.BytesReclaimed,
		FilesDeleted:       int64(res.FilesDeleted),
		SkippedLinkedBytes: res.SkippedLinked,
		StartFreeFraction:  -1,
	}
	if res.VolumeBytes > 0 {
		stats.StartFreeFraction = float64(res.StartFreeBytes) / float64(res.VolumeBytes)
	}
	if s := res.Sample; s != nil {
		stats.Sample = &monitoring.TrimSampleStats{
			Duration:             s.Duration,
			Files:                int64(s.Files),
			SizeBytes:            s.SizeBytes,
			Exact:                s.Exact(),
			PinnedBytes:          s.PinnedBytes,
			CapCheckBuckets:      int64(s.CapCheckBuckets),
			CapCheckStoppedEarly: s.CapCheckBuckets > 0 && s.CapCheckBuckets < s.TotalBuckets,
		}
	}
	return stats
}

// FailedTrimMonitoringStats is what a -trim-only run that failed before its
// pass started, such as one without -cache-dir, reports: an error outcome and
// nothing else.
func FailedTrimMonitoringStats() *monitoring.TrimStats {
	return TrimMonitoringStats(storage.TrimResult{Outcome: storage.TrimOutcomeError})
}
