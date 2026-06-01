
package monitoring


import (
	"time"
)

func initMetrics(clientName string) {}

var (
	isBorg     bool
	binaryName string
	arch       string
)

func recordLatency(success bool, rbeStatus string, duration time.Duration) {}

func recordBytes(success bool, bytes int64) {}

func recordUsage(success bool, exitCode int) {}

func recordDownloadStats(stats *DownloadStats, casInstance string, localCacheEnabled bool, chunksOnly bool) {}

func shutdown() {}
