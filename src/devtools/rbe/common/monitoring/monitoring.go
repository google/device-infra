// Package monitoring provides utility functions to record Streamz/Murdock metrics for CAS clients.
package monitoring

import (
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
}

// WithMurdockAddr sets the address (host:port) for the murdockd daemon.
func WithMurdockAddr(addr string) Option {
	return func(c *config) {
		c.murdockAddr = addr
	}
}

// Init initializes monitoring. Inside Google3 it configures Streamz/Murdock.
func Init(clientName string, opts ...Option) {
	var cfg config
	for _, opt := range opts {
		opt(&cfg)
	}
	initMetrics(clientName, cfg.murdockAddr)
}

// RecordLatency records download duration.
func RecordLatency(success bool, rbeStatus string, duration time.Duration) {
	recordLatency(success, rbeStatus, duration)
}

// RecordBytes records downloaded bytes.
func RecordBytes(success bool, bytes int64) {
	recordBytes(success, bytes)
}

// RecordUsage records client invocation usage.
func RecordUsage(success bool, exitCode int) {
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

	HeadroomChecks         int64
	HeadroomEvictions      int64
	HeadroomReclaimedBytes int64
	HeadroomPeerWaits      int64
	HeadroomBelowWatermark int64
}

// RecordLocalCacheStats records what the local cache did in one casdownloader
// run.
func RecordLocalCacheStats(stats *LocalCacheStats, casInstance string) {
	recordLocalCacheStats(stats, casInstance)
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

// Shutdown flushes all pending metrics.
func Shutdown() {
	shutdown()
}
