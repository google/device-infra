// Package monitoring provides utility functions to record Streamz/Murdock metrics for CAS clients.
package monitoring

import (
	"time"
)

// DownloadStats mirror struct to avoid dependency on casdownloader/download.
type DownloadStats struct {
	SizeCold           int64
	SizeHot            int64
	CountCold          int
	CountHot           int
	E2ETimeMS          int64
	DirRetrieveTimeMS  int64
	DirPrepareTimeMS   int64
	FileDownloadTimeMS int64
	ChunkRestoreTimeMS int64
	DownloadError      string

	// Metadata fields
	Caller  string
	Version string
	BuildID string
	Branch  string
	Flavor  string
}

// Init initializes monitoring. Inside Google3 it configures Streamz/Murdock.
func Init(clientName string) {
	initMetrics(clientName)
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
