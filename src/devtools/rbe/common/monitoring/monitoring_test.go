package monitoring

import (
	"slices"
	"testing"
	"time"
)

func TestMonitoringWorkflows(t *testing.T) {
	// Initialize monitoring with a mock/test client name
	Init("test-client")

	expectedEnabled := isBorg || isMurdockdPresent("localhost:2444")
	if enabled != expectedEnabled {
		t.Errorf("enabled = %v, expected %v (isBorg = %v, isMurdockdPresent = %v)", enabled, expectedEnabled, isBorg, isMurdockdPresent("localhost:2444"))
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

func TestDistinctNoteReasons(t *testing.T) {
	tests := []struct {
		name string
		in   []string
		want []string
	}{
		{name: "none", in: nil, want: nil},
		{name: "one", in: []string{"a"}, want: []string{"a"}},
		{name: "repeats keep first appearance order", in: []string{"b", "a", "b", "a"}, want: []string{"b", "a"}},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			if got := distinctNoteReasons(tc.in); !slices.Equal(got, tc.want) {
				t.Errorf("distinctNoteReasons(%q) = %q, want %q", tc.in, got, tc.want)
			}
		})
	}
}

// TestRecordDownloadStats_CountsEachReasonOncePerRun pins the note counter's
// unit: runs affected, not notes written. A run whose cache failed to open on
// both the proxy attempt and the direct retry carries the reason twice.
func TestRecordDownloadStats_CountsEachReasonOncePerRun(t *testing.T) {
	Init("casdownloader")
	RecordDownloadStats(&DownloadStats{
		NoteReasons: []string{"cache_setup_failed", "cache_write_failed", "cache_setup_failed"},
	}, "test-instance", false, false)

	testMu.Lock()
	got := lastRecordedNoteReasons
	testMu.Unlock()
	want := []string{"cache_setup_failed", "cache_write_failed"}
	if !slices.Equal(got, want) {
		t.Errorf("recorded note reasons = %q, want %q", got, want)
	}
	Shutdown()
}

func TestRecordDownloadStats_RecordsCaller(t *testing.T) {
	Init("casdownloader")
	RecordDownloadStats(&DownloadStats{Caller: "em25-arm"}, "test-instance", false, false)

	testMu.Lock()
	got := lastRecordedCaller
	testMu.Unlock()
	if got != "em25-arm" {
		t.Errorf("recorded caller = %q, want %q", got, "em25-arm")
	}
	Shutdown()
}

// TestRecordDownloadStats_NilStatsIsNoOp checks that nil stats neither panic
// nor overwrite what the previous run recorded.
func TestRecordDownloadStats_NilStatsIsNoOp(t *testing.T) {
	Init("casdownloader")
	RecordDownloadStats(&DownloadStats{
		Caller:      "previous-caller",
		NoteReasons: []string{"cache_write_failed"},
	}, "test-instance", false, false)

	RecordDownloadStats(nil, "test-instance", false, false)

	testMu.Lock()
	gotCaller := lastRecordedCaller
	gotReasons := lastRecordedNoteReasons
	testMu.Unlock()
	if gotCaller != "previous-caller" {
		t.Errorf("recorded caller after nil stats = %q, want %q", gotCaller, "previous-caller")
	}
	if want := []string{"cache_write_failed"}; !slices.Equal(gotReasons, want) {
		t.Errorf("recorded note reasons after nil stats = %q, want %q", gotReasons, want)
	}
	Shutdown()
}

func TestRecordLocalCacheStats(t *testing.T) {
	Init("casdownloader")
	defer Shutdown()

	activity := &LocalCacheActivity{PermDriftCopies: 2, HeadroomChecks: 1}
	RecordLocalCacheStats(&LocalCacheStats{Impl: "lockfree", Activity: activity}, "test-instance")
	testMu.Lock()
	gotImpl, gotActivity := lastRecordedCacheImpl, lastRecordedCacheActivity
	testMu.Unlock()
	if gotImpl != "lockfree" || gotActivity != activity {
		t.Errorf("recorded (%q, %+v), want (%q, %+v)", gotImpl, gotActivity, "lockfree", activity)
	}

	RecordLocalCacheStats(&LocalCacheStats{Impl: "none"}, "test-instance")
	testMu.Lock()
	gotImpl, gotActivity = lastRecordedCacheImpl, lastRecordedCacheActivity
	testMu.Unlock()
	if gotImpl != "none" || gotActivity != nil {
		t.Errorf("recorded (%q, %+v), want (%q, nil)", gotImpl, gotActivity, "none")
	}

	// nil stats neither panic nor overwrite what was recorded before.
	RecordLocalCacheStats(nil, "test-instance")
	testMu.Lock()
	gotImpl = lastRecordedCacheImpl
	testMu.Unlock()
	if gotImpl != "none" {
		t.Errorf("recorded impl after nil stats = %q, want %q", gotImpl, "none")
	}
}

func TestMonitoringWithMurdockAddr(t *testing.T) {
	// Initialize with custom address option
	Init("casdownloader", WithMurdockAddr("127.0.0.1:2444"))
	RecordUsage(true, 0)
	Shutdown()
}
