package monitoring

import (
	"testing"
	"time"
)

func TestMonitoringWorkflows(t *testing.T) {
	// Initialize monitoring with a mock/test client name
	Init("test-client")

	t.Logf("isBorg: %v", isBorg)
	t.Logf("binaryName: %s", binaryName)
	t.Logf("arch: %s", arch)

	// Record various telemetry data points
	RecordUsage(true, 0)
	RecordUsage(false, 1)

	RecordLatency(true, "OK", 100*time.Millisecond)
	RecordLatency(false, "INTERNAL", 500*time.Millisecond)

	RecordBytes(true, 1024)
	RecordBytes(false, 0)

	// Test new RecordDownloadStats API
	stats := &DownloadStats{
		SizeCold:           100,
		SizeHot:            200,
		CountCold:          1,
		CountHot:           2,
		E2ETimeMS:          300,
		DirRetrieveTimeMS:  50,
		DirPrepareTimeMS:   10,
		FileDownloadTimeMS: 200,
		ChunkRestoreTimeMS: 40,
		DownloadError:      "test-err",
		Caller:             "test-caller",
		Version:            "test-ver",
		BuildID:            "test-bid",
		Branch:             "test-branch",
		Flavor:             "test-flavor",
	}
	RecordDownloadStats(stats, "test-instance", true, false)

	// Signal shutdown to verify flushing logic does not panic or deadlock
	Shutdown()
}
