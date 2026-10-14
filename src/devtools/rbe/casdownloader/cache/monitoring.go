package cache

import (
	"os"
	"strings"
	"syscall"

	log "github.com/golang/glog"
	"github.com/google/device-infra/src/devtools/rbe/common/monitoring"
	"github.com/google/device-infra/src/devtools/rbe/common/storage"
)

// MonitoringStats describes what c did since it was created, for export as
// metrics. c may be nil, for a run without a cache. The out-of-space errors
// and the attempts to start the detached evictor are this process's, whatever
// cache it had.
//
// Activity is filled in only from caches that report it. Leaving it nil for
// the others, rather than zero, keeps runs on LocalCache or without a cache
// from emitting counters that would read as a lock-free cache with nothing to
// report.
func MonitoringStats(c Cache) *monitoring.LocalCacheStats {
	stats := &monitoring.LocalCacheStats{
		Impl:             Impl(c),
		OutOfSpaceErrors: OutOfSpace().Errors,
		TrimStarts:       TrimStarts(),
	}
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

// VolumeMonitoringStats measures the volume that holds dir, for export as
// metrics, as df does: used is what the volume has allocated, free what an
// unprivileged process can still allocate. It returns nil, and logs why, if
// dir cannot be measured.
func VolumeMonitoringStats(dir string) *monitoring.VolumeStats {
	var st syscall.Statfs_t
	if err := syscall.Statfs(dir, &st); err != nil {
		log.Warningf("Could not measure the volume of the local cache in %s: %v.", dir, err)
		return nil
	}
	bsize := int64(st.Bsize)
	return &monitoring.VolumeStats{
		TotalBytes: int64(st.Blocks) * bsize,
		FreeBytes:  int64(st.Bavail) * bsize,
		UsedBytes:  int64(st.Blocks-st.Bfree) * bsize,
		Host:       HostName(),
	}
}

// hostNameFile is where a container launcher exposes the physical host's name
// to casdownloader: launch-cf.sh bind-mounts the host's /etc/hostname there,
// read-only. It is a variable so that tests can point it elsewhere.
var hostNameFile = "/etc/casdownloader_hostname"

// HostName returns the name of the physical host casdownloader runs on: the
// first line of hostNameFile if it exists and is not empty, as in a
// Cuttlefish container, whose own hostname is its ID, and otherwise this
// process's hostname, which on a bare-metal host or in a container with the
// host's network is the host's. It returns "" if neither is known.
func HostName() string {
	if data, err := os.ReadFile(hostNameFile); err == nil {
		line, _, _ := strings.Cut(string(data), "\n")
		if name := strings.TrimSpace(line); name != "" {
			return name
		}
	} else if !os.IsNotExist(err) {
		log.Warningf("Could not read the host's name from %s: %v. Using this process's hostname.", hostNameFile, err)
	}
	name, err := os.Hostname()
	if err != nil {
		log.Warningf("Could not get the hostname: %v.", err)
		return ""
	}
	return name
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
