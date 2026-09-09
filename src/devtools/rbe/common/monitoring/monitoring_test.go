package monitoring

import (
	"testing"
	"time"
)

func TestMonitoringWorkflows(t *testing.T) {
	// Initialize monitoring with a mock/test client name
	Init("test-client")

	expectedEnabled := isBorg || isMurdockdPresent()
	if enabled != expectedEnabled {
		t.Errorf("enabled = %v, expected %v (isBorg = %v, isMurdockdPresent = %v)", enabled, expectedEnabled, isBorg, isMurdockdPresent())
	}

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

	// Test CASProxy metrics APIs
	Init("casproxy")

	testMu.Lock()
	lastRecordedMethod = ""
	testMu.Unlock()
	RecordCacheRequest("ByteStream.Read", true)
	testMu.Lock()
	if lastRecordedMethod != "ByteStream.Read" {
		t.Errorf("RecordCacheRequest: lastRecordedMethod = %q, want %q", lastRecordedMethod, "ByteStream.Read")
	}
	testMu.Unlock()

	testMu.Lock()
	lastRecordedServedBytes = 0
	testMu.Unlock()
	RecordServedBytes("local_cache", 2048)
	testMu.Lock()
	if lastRecordedServedBytes != 2048 {
		t.Errorf("RecordServedBytes: lastRecordedServedBytes = %d, want 2048", lastRecordedServedBytes)
	}
	testMu.Unlock()

	testMu.Lock()
	lastRecordedWANBytes = 0
	testMu.Unlock()
	RecordWANBytes("ByteStream.Read", 4096)
	testMu.Lock()
	if lastRecordedWANBytes != 4096 {
		t.Errorf("RecordWANBytes: lastRecordedWANBytes = %d, want 4096", lastRecordedWANBytes)
	}
	testMu.Unlock()

	testMu.Lock()
	lastRecordedTotal = 0
	testMu.Unlock()
	RecordStorageUsage(100*1024*1024*1024, 25*1024*1024*1024)
	testMu.Lock()
	if lastRecordedTotal != 100*1024*1024*1024 {
		t.Errorf("RecordStorageUsage: lastRecordedTotal = %d, want %d", lastRecordedTotal, int64(100*1024*1024*1024))
	}
	testMu.Unlock()

	testMu.Lock()
	lastRecordedStatus = ""
	testMu.Unlock()
	RecordEvictionRun("evicted", 1024*1024, 5, 25*time.Millisecond)
	testMu.Lock()
	if lastRecordedStatus != "evicted" {
		t.Errorf("RecordEvictionRun: lastRecordedStatus = %q, want %q", lastRecordedStatus, "evicted")
	}
	testMu.Unlock()

	testMu.Lock()
	lastRecordedUpstreamRPC = ""
	testMu.Unlock()
	RecordUpstreamRPC("CAS.BatchReadBlobs", "NOT_FOUND", 120*time.Millisecond)
	testMu.Lock()
	if lastRecordedUpstreamRPC != "CAS.BatchReadBlobs" {
		t.Errorf("RecordUpstreamRPC: lastRecordedUpstreamRPC = %q, want %q", lastRecordedUpstreamRPC, "CAS.BatchReadBlobs")
	}
	testMu.Unlock()

	// Signal shutdown to verify flushing logic does not panic or deadlock
	Shutdown()
}
