// Package monitoring provides utility functions to record Streamz/Murdock metrics for CAS clients.
package monitoring

import (
	"sync/atomic"
	"time"
)

// DownloadStats mirror struct to avoid dependency on casdownloader/download.
// The size and count fields partition the tree; see the download package's
// Stats for what each tier means.
type DownloadStats struct {
	SizeHot            int64
	SizeDedup          int64
	SizeProxyHot       int64
	SizeProxyCold      int64
	SizeCold           int64
	CountHot           int
	CountDedup         int
	CountProxyHot      int
	CountProxyCold     int
	CountCold          int
	E2ETimeMS          int64
	DirRetrieveTimeMS  int64
	DirPrepareTimeMS   int64
	FileDownloadTimeMS int64
	ChunkRestoreTimeMS int64
	DownloadError      string
	// NoteReasons classifies the notes the run carried, one entry per note.
	// Repeats are allowed; each distinct reason is counted once per run.
	NoteReasons []string

	// Metadata fields
	Caller  string
	Version string
	BuildID string
	Branch  string
	Flavor  string
}

// distinctNoteReasons returns reasons with repeats removed, in order of first
// appearance. A run is counted once per reason however many notes of that
// reason it carried, so the note counter reads as runs affected and can be
// divided by the download count.
func distinctNoteReasons(reasons []string) []string {
	var out []string
	seen := make(map[string]bool, len(reasons))
	for _, r := range reasons {
		if seen[r] {
			continue
		}
		seen[r] = true
		out = append(out, r)
	}
	return out
}

// Option configures monitoring parameters.
type Option func(*config)

type config struct {
	murdockAddr string
	trimOnly    bool
}

// WithMurdockAddr sets the address (host:port) for the murdockd daemon.
func WithMurdockAddr(addr string) Option {
	return func(c *config) {
		c.murdockAddr = addr
	}
}

// WithTrimOnly marks the process as a casdownloader -trim-only evictor. It
// registers the trim metrics (see RecordTrim) instead of the per-run ones, so
// that the evictor, which downloads start in the background, neither counts
// as a run nor exports the initial values of the download metrics that have no
// fields, such as an empty caller and version.
func WithTrimOnly() Option {
	return func(c *config) {
		c.trimOnly = true
	}
}

// Init initializes monitoring. Inside Google3 it configures Streamz/Murdock.
func Init(clientName string, opts ...Option) {
	var cfg config
	for _, opt := range opts {
		opt(&cfg)
	}
	trimOnlyProcess.Store(cfg.trimOnly)
	initMetrics(clientName, cfg.murdockAddr, cfg.trimOnly)
}

// trimOnlyProcess is set by an Init with WithTrimOnly.
var trimOnlyProcess atomic.Bool

// RecordLatency records download duration.
func RecordLatency(success bool, rbeStatus string, duration time.Duration) {
	recordLatency(success, rbeStatus, duration)
}

// RecordBytes records downloaded bytes.
func RecordBytes(success bool, bytes int64) {
	recordBytes(success, bytes)
}

// RecordUsage records client invocation usage. It records nothing in a
// process initialized WithTrimOnly, which is not an invocation of its own.
func RecordUsage(success bool, exitCode int) {
	if trimOnlyProcess.Load() {
		return
	}
	recordUsage(success, exitCode)
}

// RecordDownloadStats records all download related metrics.
func RecordDownloadStats(stats *DownloadStats, casInstance string, localCacheEnabled bool, chunksOnly bool) {
	recordDownloadStats(stats, casInstance, localCacheEnabled, chunksOnly)
}

// LocalCacheStats describes what casdownloader's local cache did in one run.
//
// It is recorded separately from DownloadStats so that the existing download
// metrics keep their schema: adding a field to a metric splits its streams and
// breaks queries that do not aggregate the new field away.
type LocalCacheStats struct {
	// Impl names the cache implementation the run actually used, which is
	// "none" when it had no cache, including when setup failed.
	Impl string
	// Activity is set only for caches that report it, so that runs without
	// such a cache do not emit zeros that read as a healthy one.
	Activity *LocalCacheActivity

	// BypassedFiles and BypassedBytes count the distinct blobs of the tree,
	// and their bytes, that skipped the cache because they were larger than
	// -cache-max-blob-size; a blob at several paths counts once. They are
	// also in the tiers of DownloadStats, as cold or proxy_cold.
	BypassedFiles int64
	BypassedBytes int64
	// OutOfSpaceErrors counts the out-of-space errors the run hit, with or
	// without a cache.
	OutOfSpaceErrors int64
	// TrimStarts lists each attempt the run made to start the lock-free
	// cache's detached evictor.
	TrimStarts []TrimStart
	// Volume describes the volume that holds the cache, or is nil if the run
	// had no cache or could not measure it.
	Volume *VolumeStats
}

// TrimStart is one attempt by a download to start the detached evictor.
type TrimStart struct {
	// Reason is free_space or cap_check, from the open-time check, or
	// enospc, from the ENOSPC hook.
	Reason string
	// Result is started, busy (another process held the eviction lock) or
	// failed (the lock could not be probed or the process not started).
	Result string
}

// VolumeStats is the size of a volume, as statfs reports it: TotalBytes is
// its size, FreeBytes what an unprivileged process can still write, and
// UsedBytes what is in use. As with df, UsedBytes + FreeBytes can be less
// than TotalBytes, by the blocks reserved for root.
type VolumeStats struct {
	TotalBytes int64
	FreeBytes  int64
	UsedBytes  int64
	// Host names the physical host the volume is on. Inside a container,
	// whose own hostname is its ID, it comes from the host (see the
	// casdownloader cache package's HostName).
	Host string
}

// LocalCacheActivity holds the counters a lock-free cache accumulates in a run.
// See the casdownloader cache and common/storage packages for their meaning.
type LocalCacheActivity struct {
	ExecMismatchCopies int64
	ExecMismatchBytes  int64
	PermDriftHits      int64
	UnreadableRepairs  int64
	CorruptBlobs       int64

	CopyFallbackEMLINK int64
	CopyFallbackEXDEV  int64
	CopyFallbackOther  int64
}

// RecordLocalCacheStats records what the local cache did in one casdownloader
// run.
func RecordLocalCacheStats(stats *LocalCacheStats, casInstance string) {
	recordLocalCacheStats(stats, casInstance)
}

// TrimStats describes one casdownloader -trim-only pass over the lock-free
// cache. It mirrors storage.TrimResult, which this package cannot import.
type TrimStats struct {
	// Outcome is noop, evicted, busy, timed_out or error.
	Outcome            string
	Duration           time.Duration
	BytesReclaimed     int64
	FilesDeleted       int64
	SkippedLinkedBytes int64
	// StartFreeFraction is the free fraction of the cache's volume, from 0
	// to 1, when the pass started. It is negative if the pass did not
	// measure it, as when another process was already trimming.
	StartFreeFraction float64
	// Sample describes the buckets the pass read, or is nil if it read none.
	Sample *TrimSampleStats
}

// TrimSampleStats describes the buckets one trim read to check the cache
// against its cap and to find the cutoff age.
type TrimSampleStats struct {
	Duration time.Duration
	Files    int64
	// SizeBytes is the cache's size, estimated from the buckets read, or
	// exact if Exact: every bucket was read. PinnedBytes, what other names
	// pin of it, is recorded only then, since only then is it the cache's.
	SizeBytes   int64
	Exact       bool
	PinnedBytes int64
	// CapCheckBuckets is how many buckets the cap check had read when it
	// reached its answer, or zero if the pass ran none or it did not finish,
	// and CapCheckStoppedEarly whether that was before reading every bucket.
	CapCheckBuckets      int64
	CapCheckStoppedEarly bool
}

// RecordTrim records one casdownloader -trim-only pass. Its metrics are
// registered only by an Init with WithTrimOnly.
func RecordTrim(stats *TrimStats) {
	recordTrim(stats)
}

// RecordCacheRequest records a cache read request and whether it was a hit or miss.
func RecordCacheRequest(method string, hit bool) {
	recordCacheRequest(method, hit)
}

// RecordServedBytes records bytes delivered downstream to clients.
func RecordServedBytes(source string, bytes int64) {
	recordServedBytes(source, bytes)
}

// RecordWANBytes records bytes downloaded from upstream RBE.
func RecordWANBytes(rpc string, bytes int64) {
	recordWANBytes(rpc, bytes)
}

// RecordStorageUsage records the total and effective free bytes of the cache storage.
func RecordStorageUsage(totalBytes, freeBytes int64) {
	recordStorageUsage(totalBytes, freeBytes)
}

// RecordEvictionRun records an eviction cycle's status, reclaimed bytes, file count, and duration.
func RecordEvictionRun(status string, reclaimedBytes, evictedFiles int64, duration time.Duration) {
	recordEvictionRun(status, reclaimedBytes, evictedFiles, duration)
}

// RecordUpstreamRPC records an upstream RBE RPC call duration and status code.
func RecordUpstreamRPC(rpc, grpcCode string, duration time.Duration) {
	recordUpstreamRPC(rpc, grpcCode, duration)
}

// RecordConfigWatermarkIgnored counts a watermark in casproxy's saved
// configuration file that casproxy ignored at startup, because it was
// invalid, in favour of the flag value. key is its key in the file,
// min_free_space or target_free_space.
func RecordConfigWatermarkIgnored(key string) {
	recordConfigWatermarkIgnored(key)
}

// Shutdown flushes all pending metrics.
func Shutdown() {
	shutdown()
}
