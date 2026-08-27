
package monitoring

import (
	"time"
)

func initMetrics(clientName string) {}

var (
	isBorg     bool
	binaryName string
	arch       string
	enabled    bool

	// Test tracking variables
	lastRecordedUsageExitCode int
	lastRecordedLatencyStatus string
	lastRecordedBytes         int64
	lastRecordedCaller        string
	lastRecordedMethod        string
	lastRecordedServedBytes   int64
	lastRecordedWANBytes      int64
	lastRecordedTotal         int64
	lastRecordedStatus        string
	lastRecordedUpstreamRPC   string
)

func isMurdockdPresent() bool { return false }

func recordLatency(success bool, rbeStatus string, duration time.Duration) {
	lastRecordedLatencyStatus = rbeStatus
}

func recordBytes(success bool, bytes int64) {
	lastRecordedBytes = bytes
}

func recordUsage(success bool, exitCode int) {
	lastRecordedUsageExitCode = exitCode
}

func recordDownloadStats(stats *DownloadStats, casInstance string, localCacheEnabled bool, chunksOnly bool) {
	if stats != nil {
		lastRecordedCaller = stats.Caller
	}
}

func recordCacheRequest(method string, hit bool) {
	lastRecordedMethod = method
}

func recordServedBytes(source string, bytes int64) {
	lastRecordedServedBytes = bytes
}

func recordWANBytes(rpc string, bytes int64) {
	lastRecordedWANBytes = bytes
}

func recordStorageUsage(totalBytes, freeBytes int64) {
	lastRecordedTotal = totalBytes
}

func recordEvictionRun(status string, reclaimedBytes, evictedFiles int64, duration time.Duration) {
	lastRecordedStatus = status
}

func recordUpstreamRPC(rpc, grpcCode string, duration time.Duration) {
	lastRecordedUpstreamRPC = rpc
}

func shutdown() {}
