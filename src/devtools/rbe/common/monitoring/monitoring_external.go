
package monitoring

import (
	"sync"
	"time"
)

func initMetrics(clientName string) {}

var (
	isBorg     bool
	binaryName string
	arch       string
	enabled    bool

	testMu sync.Mutex

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
	testMu.Lock()
	lastRecordedLatencyStatus = rbeStatus
	testMu.Unlock()
}

func recordBytes(success bool, bytes int64) {
	testMu.Lock()
	lastRecordedBytes = bytes
	testMu.Unlock()
}

func recordUsage(success bool, exitCode int) {
	testMu.Lock()
	lastRecordedUsageExitCode = exitCode
	testMu.Unlock()
}

func recordDownloadStats(stats *DownloadStats, casInstance string, localCacheEnabled bool, chunksOnly bool) {
	if stats != nil {
		testMu.Lock()
		lastRecordedCaller = stats.Caller
		testMu.Unlock()
	}
}

func recordCacheRequest(method string, hit bool) {
	testMu.Lock()
	lastRecordedMethod = method
	testMu.Unlock()
}

func recordServedBytes(source string, bytes int64) {
	testMu.Lock()
	lastRecordedServedBytes = bytes
	testMu.Unlock()
}

func recordWANBytes(rpc string, bytes int64) {
	testMu.Lock()
	lastRecordedWANBytes = bytes
	testMu.Unlock()
}

func recordStorageUsage(totalBytes, freeBytes int64) {
	testMu.Lock()
	lastRecordedTotal = totalBytes
	testMu.Unlock()
}

func recordEvictionRun(status string, reclaimedBytes, evictedFiles int64, duration time.Duration) {
	testMu.Lock()
	lastRecordedStatus = status
	testMu.Unlock()
}

func recordUpstreamRPC(rpc, grpcCode string, duration time.Duration) {
	testMu.Lock()
	lastRecordedUpstreamRPC = rpc
	testMu.Unlock()
}

func shutdown() {}
