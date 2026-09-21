package storage

import (
	"errors"
	"fmt"
	"strings"
	"sync/atomic"
	"syscall"

	log "github.com/golang/glog"
)

// Observability policy for the hot path
//
// Storage operations run once per blob. A casdownloader invocation materializes
// thousands of blobs in a few seconds, so any log statement on a per-blob path
// is a log statement multiplied by several thousand. The conditions worth
// reporting here are also precisely the conditions that repeat: a popular blob
// that has exhausted its inode link count takes the copy fallback for every
// consumer that wants it, not just the first.
//
// The predecessor implementation solved this by saying nothing at all. See
// casdownloader/cache/localcache.go, where pushByHardlink discards the link
// error outright with the comment "Do not log the link error as it can be
// spammy", and pullByHardlink swallows EMLINK the same way. That keeps the logs
// readable but destroys the signal: there is no way to tell from a run whether
// the copy fallback fired for one blob or for every blob, and the difference
// matters because a copy costs a second full-size write to a disk we are
// already trying to keep from filling up.
//
// So this package splits the two jobs that a log line was being asked to do:
//
//   - Onset, which needs a timestamp and full context, is reported once via a
//     first-occurrence warning. You learn when the condition started and what
//     the triggering operation was.
//   - Magnitude, which needs a count and not a timestamp, is accumulated in
//     atomic counters and reported once by the caller through Stats.
//
// Everything in between is silent. The counters are plain atomics rather than a
// mutex-guarded struct so that this stays consistent with the lock-free
// contract documented on Storage.

// onceFlag reports true to exactly one caller, ever. It is the gate behind
// first-occurrence log lines.
type onceFlag struct{ fired atomic.Bool }

// first reports whether this is the first call. All later calls return false.
func (f *onceFlag) first() bool { return f.fired.CompareAndSwap(false, true) }

// counters accumulates per-Storage activity. Every field is incremented on a
// hot path, so all of them are atomic and none of them are ever read except by
// Stats.
type counters struct {
	hardlinkHits     atomic.Int64
	hardlinkMisses   atomic.Int64
	ingested         atomic.Int64
	ingestDeduped    atomic.Int64
	copyFallbackLink atomic.Int64 // EMLINK: source inode is out of links.
	copyFallbackDev  atomic.Int64 // EXDEV: cache and destination differ by mount.
	copyFallbackElse atomic.Int64 // EPERM, EOPNOTSUPP, ENOSYS.

	firstStructuralFallback onceFlag
}

// Stats is a point-in-time snapshot of one Storage instance's activity.
//
// It is the reporting half of the policy described at the top of this file:
// conditions that are too frequent to log individually are counted here
// instead. Callers are expected to render it once, at a natural boundary, such
// as the end of a casdownloader invocation or an admin handler in a long-lived
// server.
type Stats struct {
	// HardlinkHits and HardlinkMisses count HardlinkTo outcomes. A miss is an
	// ordinary absent blob, not an error.
	HardlinkHits   int64
	HardlinkMisses int64

	// Ingested counts blobs HardlinkFrom added; IngestDeduped counts calls
	// that found the blob already cached and did nothing.
	Ingested      int64
	IngestDeduped int64

	// CopyFallbackEMLINK counts links refused because the source inode has hit
	// the filesystem's per-inode link ceiling. This is expected in small
	// numbers for very popular blobs and is not actionable on its own.
	CopyFallbackEMLINK int64

	// CopyFallbackEXDEV counts links refused because the cache root and the
	// destination are on different mounts. Unlike EMLINK this is a
	// configuration problem: every affected blob is written to disk twice, so
	// a nonzero value here on a machine that is running out of space is a
	// strong lead.
	CopyFallbackEXDEV int64

	// CopyFallbackOther counts the remaining structural link failures (EPERM
	// under protected_hardlinks or overlayfs, EOPNOTSUPP, ENOSYS).
	CopyFallbackOther int64
}

// CopyFallbacks is the total number of writes that could not be hard linked
// and were copied instead. Each one costs a second full-size write.
func (s Stats) CopyFallbacks() int64 {
	return s.CopyFallbackEMLINK + s.CopyFallbackEXDEV + s.CopyFallbackOther
}

// Summary renders the snapshot as a single log line.
//
// Clean runs stay short: the counters that only matter when they are nonzero
// are omitted when they are zero, so an unremarkable invocation produces one
// line of hit/miss/ingest totals and nothing else. Anything appended past that
// point is worth reading.
func (s Stats) Summary() string {
	var b strings.Builder
	fmt.Fprintf(&b, "Storage summary: %d hits, %d misses, %d ingested, %d already cached",
		s.HardlinkHits, s.HardlinkMisses, s.Ingested, s.IngestDeduped)

	if n := s.CopyFallbacks(); n > 0 {
		fmt.Fprintf(&b, "; %d copy fallbacks (EMLINK %d, EXDEV %d, other %d)",
			n, s.CopyFallbackEMLINK, s.CopyFallbackEXDEV, s.CopyFallbackOther)
	}
	return b.String()
}

// Stats returns a snapshot of this Storage instance's activity counters.
//
// The fields are read individually rather than under a single lock, so a
// snapshot taken while other goroutines are still writing may mix values from
// slightly different instants. That is deliberate: these are diagnostic
// totals, and making them mutually consistent would mean putting a lock on
// every blob operation to serve a reader that runs once.
func (s *Storage) Stats() Stats {
	return Stats{
		HardlinkHits:       s.counters.hardlinkHits.Load(),
		HardlinkMisses:     s.counters.hardlinkMisses.Load(),
		Ingested:           s.counters.ingested.Load(),
		IngestDeduped:      s.counters.ingestDeduped.Load(),
		CopyFallbackEMLINK: s.counters.copyFallbackLink.Load(),
		CopyFallbackEXDEV:  s.counters.copyFallbackDev.Load(),
		CopyFallbackOther:  s.counters.copyFallbackElse.Load(),
	}
}

// noteCopyFallback records a link failure that degraded to a copy, and warns
// about the first structural one.
//
// The errno decides whether a human needs to know. EMLINK means a blob has been
// linked into more workspaces than the filesystem allows on one inode, which is
// routine here: Android artifacts are full of shared objects such as libc++.so
// and liblog.so that appear in nearly every test, and Tradefed carries a
// hardcoded allowlist for exactly those two names in FileUtil.hardlinkFile. A
// warning that fires on essentially every run is one people stop reading, so
// EMLINK is counted and nothing more.
//
// The rest are worth a line. EXDEV means the cache root and the destination are
// on different mounts, so every blob gets written twice; EPERM and friends mean
// protected_hardlinks or an overlayfs boundary is defeating the optimization.
// All of them are fixable configuration problems that are otherwise invisible,
// and all of them inflate disk usage on hosts we are already trying to keep
// from filling up. One line names the paths and the errno; the remainder are
// counted and surface through Stats.
func (s *Storage) noteCopyFallback(err error, srcPath, destPath string) {
	switch {
	case errors.Is(err, syscall.EMLINK):
		// Expected for over-shared blobs. Counted only, never logged.
		s.counters.copyFallbackLink.Add(1)
		return
	case errors.Is(err, syscall.EXDEV):
		s.counters.copyFallbackDev.Add(1)
	default:
		s.counters.copyFallbackElse.Add(1)
	}

	if s.counters.firstStructuralFallback.first() {
		log.Warningf("Storage: cannot hard link %s to %s (%v); copying instead. "+
			"This is a filesystem or mount configuration problem, not a transient error, so it "+
			"will recur for every blob and write each one to disk twice. This message is logged "+
			"only for the first occurrence; the rest are counted in the storage summary.",
			srcPath, destPath, err)
	}
}
