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

// Shutdown flushes all pending metrics.
func Shutdown() {
	shutdown()
}
