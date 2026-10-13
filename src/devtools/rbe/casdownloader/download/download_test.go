package download

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/bazelbuild/remote-apis-sdks/go/pkg/client"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/digest"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/fakes"
	repb "github.com/bazelbuild/remote-apis/build/bazel/remote/execution/v2"
	"github.com/google/device-infra/src/devtools/rbe/casdownloader/cache"
	"google.golang.org/grpc"
	"google.golang.org/grpc/metadata"
	"google.golang.org/protobuf/proto"
)

func TestDoDownloadReturnsErrorForInvalidDigest(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	d := DownloadJob{Digest: "INVALID_DIGEST"}
	err := d.DoDownload(ctx)
	if err == nil || !strings.Contains(err.Error(), "INVALID_DIGEST") {
		t.Fatalf("Failed to return error for invalid root digest")
	}
}

func TestCalculateTimeout(t *testing.T) {
	tests := []struct {
		name            string
		minDownloadMbps int64
		size            int64
		want            time.Duration
	}{
		{
			name:            "zero minDownloadMbps",
			minDownloadMbps: 0,
			size:            100,
			want:            10 * time.Second,
		},
		{
			name:            "negative minDownloadMbps",
			minDownloadMbps: -1,
			size:            100,
			want:            10 * time.Second,
		},
		{
			name:            "zero size",
			minDownloadMbps: 100,
			size:            0,
			want:            10 * time.Second,
		},
		{
			name:            "normal case 1: 1MB size, 1MBps speed",
			minDownloadMbps: 1,
			size:            1024 * 1024,
			want:            11 * time.Second,
		},
		{
			name:            "normal case 2: 0.5MB size, 1MBps speed",
			minDownloadMbps: 1,
			size:            512 * 1024,
			want:            10*time.Second + 500*time.Millisecond,
		},
		{
			name:            "normal case 3: 10MB size, 1MBps speed",
			minDownloadMbps: 1,
			size:            10 * 1024 * 1024,
			want:            20 * time.Second,
		},
		{
			name:            "normal case 4: 100MB size, 10MBps speed",
			minDownloadMbps: 10,
			size:            100 * 1024 * 1024,
			want:            20 * time.Second,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := CalculateTimeout(tt.minDownloadMbps, tt.size); got != tt.want {
				t.Errorf("CalculateTimeout() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestDoDownload_EndToEnd_WithDirectoriesAndSymlinks(t *testing.T) {
	ctx := context.Background()
	fakeServer, err := fakes.NewServer(t)
	if err != nil {
		t.Fatalf("Failed to create fake RBE server: %v", err)
	}
	defer fakeServer.Stop()

	// Create file contents
	file1Data := []byte("content of root file")
	file2Data := []byte("content of sub file")
	dFile1 := fakeServer.CAS.Put(file1Data)
	dFile2 := fakeServer.CAS.Put(file2Data)

	// Create sub directory
	subDir := &repb.Directory{
		Files: []*repb.FileNode{
			{Name: "sub_file.txt", Digest: &repb.Digest{Hash: dFile2.Hash, SizeBytes: dFile2.Size}, IsExecutable: false},
		},
		Symlinks: []*repb.SymlinkNode{
			{Name: "sub_symlink.txt", Target: "sub_file.txt"},
		},
	}
	subBytes, err := proto.Marshal(subDir)
	if err != nil {
		t.Fatal(err)
	}
	dSubDir := fakeServer.CAS.Put(subBytes)

	// Create root directory
	rootDir := &repb.Directory{
		Files: []*repb.FileNode{
			{Name: "root_file.txt", Digest: &repb.Digest{Hash: dFile1.Hash, SizeBytes: dFile1.Size}, IsExecutable: false},
		},
		Directories: []*repb.DirectoryNode{
			{Name: "subdir", Digest: &repb.Digest{Hash: dSubDir.Hash, SizeBytes: dSubDir.Size}},
		},
		Symlinks: []*repb.SymlinkNode{
			{Name: "root_symlink.txt", Target: "root_file.txt"},
		},
	}
	rootBytes, err := proto.Marshal(rootDir)
	if err != nil {
		t.Fatal(err)
	}
	dRootDir := fakeServer.CAS.Put(rootBytes)

	// Create test client
	testClient, err := fakeServer.NewTestClient(ctx)
	if err != nil {
		t.Fatalf("Failed to create test client: %v", err)
	}
	defer testClient.Close()

	destDir := t.TempDir()

	job := DownloadJob{
		Client: testClient,
		Digest: fmt.Sprintf("%s/%d", dRootDir.Hash, dRootDir.Size),
		Dir:    destDir,
	}

	if err := job.DoDownload(ctx); err != nil {
		t.Fatalf("DoDownload failed: %v", err)
	}

	// Verify root file
	rootFilePath := filepath.Join(destDir, "root_file.txt")
	gotRoot, err := os.ReadFile(rootFilePath)
	if err != nil {
		t.Fatalf("Failed to read root file: %v", err)
	}
	if !bytes.Equal(gotRoot, file1Data) {
		t.Errorf("Root file content mismatch: got %q, want %q", gotRoot, file1Data)
	}

	// Verify root symlink
	rootSymlinkPath := filepath.Join(destDir, "root_symlink.txt")
	target, err := os.Readlink(rootSymlinkPath)
	if err != nil {
		t.Fatalf("Failed to read root symlink: %v", err)
	}
	if target != "root_file.txt" {
		t.Errorf("Root symlink target mismatch: got %q, want %q", target, "root_file.txt")
	}

	// Verify sub file
	subFilePath := filepath.Join(destDir, "subdir", "sub_file.txt")
	gotSub, err := os.ReadFile(subFilePath)
	if err != nil {
		t.Fatalf("Failed to read sub file: %v", err)
	}
	if !bytes.Equal(gotSub, file2Data) {
		t.Errorf("Sub file content mismatch: got %q, want %q", gotSub, file2Data)
	}

	// Verify sub symlink
	subSymlinkPath := filepath.Join(destDir, "subdir", "sub_symlink.txt")
	subTarget, err := os.Readlink(subSymlinkPath)
	if err != nil {
		t.Fatalf("Failed to read sub symlink: %v", err)
	}
	if subTarget != "sub_file.txt" {
		t.Errorf("Sub symlink target mismatch: got %q, want %q", subTarget, "sub_file.txt")
	}
}

func TestDoDownload_SymlinkIdempotency_OverwriteExisting(t *testing.T) {
	ctx := context.Background()
	fakeServer, err := fakes.NewServer(t)
	if err != nil {
		t.Fatalf("Failed to create fake RBE server: %v", err)
	}
	defer fakeServer.Stop()

	fileData := []byte("target file for symlink idempotency test")
	dFile := fakeServer.CAS.Put(fileData)

	rootDir := &repb.Directory{
		Files: []*repb.FileNode{
			{Name: "data.txt", Digest: &repb.Digest{Hash: dFile.Hash, SizeBytes: dFile.Size}, IsExecutable: false},
		},
		Symlinks: []*repb.SymlinkNode{
			{Name: "data_link.txt", Target: "data.txt"},
		},
	}
	rootBytes, err := proto.Marshal(rootDir)
	if err != nil {
		t.Fatal(err)
	}
	dRootDir := fakeServer.CAS.Put(rootBytes)

	testClient, err := fakeServer.NewTestClient(ctx)
	if err != nil {
		t.Fatal(err)
	}
	defer testClient.Close()

	destDir := t.TempDir()

	// Pre-create an obsolete/conflicting symlink at data_link.txt pointing to nowhere
	preExistingLink := filepath.Join(destDir, "data_link.txt")
	if err := os.Symlink("obsolete_target.txt", preExistingLink); err != nil {
		t.Fatalf("Failed to pre-create conflicting symlink: %v", err)
	}

	job := DownloadJob{
		Client: testClient,
		Digest: fmt.Sprintf("%s/%d", dRootDir.Hash, dRootDir.Size),
		Dir:    destDir,
	}

	// First download must remove the conflicting symlink and recreate it cleanly without throwing os.ErrExist
	if err := job.DoDownload(ctx); err != nil {
		t.Fatalf("DoDownload failed on existing symlink: %v", err)
	}

	target, err := os.Readlink(preExistingLink)
	if err != nil {
		t.Fatalf("Failed to read symlink: %v", err)
	}
	if target != "data.txt" {
		t.Errorf("Symlink target mismatch: got %q, want %q", target, "data.txt")
	}

	// Subsequent download (e.g. retry / fallback) should also succeed idempotently
	if err := job.DoDownload(ctx); err != nil {
		t.Fatalf("Second DoDownload (retry) failed: %v", err)
	}
}

func TestDoDownload_CASProxyStatusStats(t *testing.T) {
	ctx := context.Background()
	fakeServer, err := fakes.NewServer(t)
	if err != nil {
		t.Fatalf("Failed to create fake RBE server: %v", err)
	}
	defer fakeServer.Stop()

	fileData := []byte("proxy metric test file")
	dFile := fakeServer.CAS.Put(fileData)

	rootDir := &repb.Directory{
		Files: []*repb.FileNode{
			{Name: "data.txt", Digest: &repb.Digest{Hash: dFile.Hash, SizeBytes: dFile.Size}},
		},
	}
	rootBytes, err := proto.Marshal(rootDir)
	if err != nil {
		t.Fatal(err)
	}
	dRootDir := fakeServer.CAS.Put(rootBytes)

	testClient, err := fakeServer.NewTestClient(ctx)
	if err != nil {
		t.Fatal(err)
	}
	defer testClient.Close()

	destDir := t.TempDir()
	dumpFile := filepath.Join(destDir, "stats.json")

	job := DownloadJob{
		Client:         testClient,
		Digest:         fmt.Sprintf("%s/%d", dRootDir.Hash, dRootDir.Size),
		Dir:            destDir,
		DumpJSON:       dumpFile,
		CASProxyStatus: "success",
	}

	if err := job.DoDownload(ctx); err != nil {
		t.Fatalf("DoDownload failed: %v", err)
	}

	if job.Stats() == nil || job.Stats().CASProxy != "success" {
		t.Errorf("Stats.CASProxy = %q, want %q", job.Stats().CASProxy, "success")
	}

	dumpedContent, err := os.ReadFile(dumpFile)
	if err != nil {
		t.Fatalf("Failed to read dumped stats file: %v", err)
	}
	if !strings.Contains(string(dumpedContent), `"casproxy":"success"`) {
		t.Errorf("Dumped JSON does not contain expected casproxy field: %s", string(dumpedContent))
	}
}

func TestUpdateDownloadStats_TierBreakdown(t *testing.T) {
	d1 := digest.Digest{Hash: "hash1", Size: 200}
	d2 := digest.Digest{Hash: "hash2", Size: 300}
	d3 := digest.Digest{Hash: "hash3", Size: 200}
	d4 := digest.Digest{Hash: "hash4", Size: 300}

	// file5 is a second reference to the blob behind file4.
	allOutputs := []*client.TreeOutput{
		{Digest: d1, Path: "file1"},
		{Digest: d2, Path: "file2"},
		{Digest: d3, Path: "file3"},
		{Digest: d4, Path: "file4"},
		{Digest: d4, Path: "file5"},
	}

	// File 1 came from the local cache, so 2, 3 and 4 were fetched (800 bytes).
	downloadedOutputs := map[digest.Digest]*client.TreeOutput{
		d2: {Digest: d2, Path: "file2"},
		d3: {Digest: d3, Path: "file3"},
		d4: {Digest: d4, Path: "file4"},
	}
	dups := []*client.TreeOutput{{Digest: d4, Path: "file5"}}

	tracker := NewProxyHitTracker()
	// casproxy served files 2 and 3 from its disk (300 + 200 = 500 bytes).
	tracker.AddHit(500, 2)

	job := &DownloadJob{
		DownloadStats: &Stats{},
		Tracker:       tracker,
		UseProxy:      true,
	}

	job.updateDownloadStats(allOutputs, downloadedOutputs, dups)

	stats := job.Stats()
	if stats.SizeHot != 200 || stats.CountHot != 1 {
		t.Errorf("Hot stats: got size=%d count=%d, want size=200 count=1", stats.SizeHot, stats.CountHot)
	}
	if stats.SizeDedup != 300 || stats.CountDedup != 1 {
		t.Errorf("Dedup stats: got size=%d count=%d, want size=300 count=1", stats.SizeDedup, stats.CountDedup)
	}
	if stats.SizeProxyHot != 500 || stats.CountProxyHot != 2 {
		t.Errorf("Proxy-hot stats: got size=%d count=%d, want size=500 count=2", stats.SizeProxyHot, stats.CountProxyHot)
	}
	if stats.SizeProxyCold != 300 || stats.CountProxyCold != 1 {
		t.Errorf("Proxy-cold stats: got size=%d count=%d, want size=300 count=1", stats.SizeProxyCold, stats.CountProxyCold)
	}
	// Nothing bypassed the proxy, so no bytes are attributed directly to CAS remote.
	if stats.SizeCold != 0 || stats.CountCold != 0 {
		t.Errorf("Cold stats: got size=%d count=%d, want size=0 count=0", stats.SizeCold, stats.CountCold)
	}

	if got, want := job.TotalSize(), int64(1300); got != want {
		t.Errorf("TotalSize() = %d, want %d", got, want)
	}
	if got, want := job.TransferredSize(), int64(800); got != want {
		t.Errorf("TransferredSize() = %d, want %d", got, want)
	}
	if got, want := job.WANSize(), int64(300); got != want {
		t.Errorf("WANSize() = %d, want %d", got, want)
	}
	if got, want := job.ProxyHotSize(), int64(500); got != want {
		t.Errorf("ProxyHotSize() = %d, want %d", got, want)
	}
	if got, want := job.HotSize(), int64(200); got != want {
		t.Errorf("HotSize() = %d, want %d", got, want)
	}
}

// Without a casproxy in the path, everything fetched is charged to CAS remote.
func TestUpdateDownloadStats_NoProxy(t *testing.T) {
	d1 := digest.Digest{Hash: "hash1", Size: 200}
	d2 := digest.Digest{Hash: "hash2", Size: 300}

	allOutputs := []*client.TreeOutput{
		{Digest: d1, Path: "file1"},
		{Digest: d2, Path: "file2"},
	}
	downloadedOutputs := map[digest.Digest]*client.TreeOutput{
		d2: {Digest: d2, Path: "file2"},
	}

	job := &DownloadJob{DownloadStats: &Stats{}, Tracker: NewProxyHitTracker()}
	job.updateDownloadStats(allOutputs, downloadedOutputs, nil)

	stats := job.Stats()
	if stats.SizeCold != 300 || stats.CountCold != 1 {
		t.Errorf("Cold stats: got size=%d count=%d, want size=300 count=1", stats.SizeCold, stats.CountCold)
	}
	if stats.SizeProxyHot != 0 || stats.SizeProxyCold != 0 {
		t.Errorf("Proxy stats: got hot=%d cold=%d, want 0 and 0", stats.SizeProxyHot, stats.SizeProxyCold)
	}
	if got, want := job.WANSize(), int64(300); got != want {
		t.Errorf("WANSize() = %d, want %d", got, want)
	}
}

// An over-reporting proxy must not be able to push the partition out of balance.
func TestUpdateDownloadStats_ClampsProxyOverReport(t *testing.T) {
	d1 := digest.Digest{Hash: "hash1", Size: 100}

	allOutputs := []*client.TreeOutput{{Digest: d1, Path: "file1"}}
	downloadedOutputs := map[digest.Digest]*client.TreeOutput{d1: {Digest: d1, Path: "file1"}}

	tracker := NewProxyHitTracker()
	// Twice what was downloaded, as a retried read of the same blob would report.
	tracker.AddHit(200, 2)

	job := &DownloadJob{DownloadStats: &Stats{}, Tracker: tracker, UseProxy: true}
	job.updateDownloadStats(allOutputs, downloadedOutputs, nil)

	stats := job.Stats()
	if stats.SizeProxyHot != 100 || stats.CountProxyHot != 1 {
		t.Errorf("Proxy-hot stats: got size=%d count=%d, want size=100 count=1", stats.SizeProxyHot, stats.CountProxyHot)
	}
	if stats.SizeProxyCold != 0 || stats.CountProxyCold != 0 {
		t.Errorf("Proxy-cold stats: got size=%d count=%d, want 0 and 0", stats.SizeProxyCold, stats.CountProxyCold)
	}
	if got, want := job.TotalSize(), int64(100); got != want {
		t.Errorf("TotalSize() = %d, want %d", got, want)
	}
}

// Bytes and blob count are clamped independently. A proxy can over-report
// one without the other -- a retried read of a blob that was already partly
// counted inflates the bytes but not the count -- and either alone is enough
// to unbalance the partition.
func TestUpdateDownloadStats_ClampsOneSidedProxyOverReport(t *testing.T) {
	d1 := digest.Digest{Hash: "hash1", Size: 100}
	d2 := digest.Digest{Hash: "hash2", Size: 100}
	allOutputs := []*client.TreeOutput{{Digest: d1, Path: "file1"}, {Digest: d2, Path: "file2"}}
	downloadedOutputs := map[digest.Digest]*client.TreeOutput{
		d1: {Digest: d1, Path: "file1"},
		d2: {Digest: d2, Path: "file2"},
	}

	tests := []struct {
		name                   string
		hitBytes               int64
		hitCount               int
		wantProxyHotSize       int64
		wantProxyHotCount      int
		wantProxyColdSize      int64
		wantProxyColdBlobCount int
	}{
		{
			name:     "bytes over, count within",
			hitBytes: 300, hitCount: 1,
			wantProxyHotSize: 200, wantProxyHotCount: 1,
			wantProxyColdSize: 0, wantProxyColdBlobCount: 1,
		},
		{
			name:     "count over, bytes within",
			hitBytes: 100, hitCount: 3,
			wantProxyHotSize: 100, wantProxyHotCount: 2,
			wantProxyColdSize: 100, wantProxyColdBlobCount: 0,
		},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			tracker := NewProxyHitTracker()
			tracker.AddHit(tc.hitBytes, tc.hitCount)
			job := &DownloadJob{DownloadStats: &Stats{}, Tracker: tracker, UseProxy: true}
			job.updateDownloadStats(allOutputs, downloadedOutputs, nil)

			stats := job.Stats()
			if stats.SizeProxyHot != tc.wantProxyHotSize || stats.CountProxyHot != tc.wantProxyHotCount {
				t.Errorf("Proxy-hot stats: got size=%d count=%d, want size=%d count=%d",
					stats.SizeProxyHot, stats.CountProxyHot, tc.wantProxyHotSize, tc.wantProxyHotCount)
			}
			if stats.SizeProxyCold != tc.wantProxyColdSize || stats.CountProxyCold != tc.wantProxyColdBlobCount {
				t.Errorf("Proxy-cold stats: got size=%d count=%d, want size=%d count=%d",
					stats.SizeProxyCold, stats.CountProxyCold, tc.wantProxyColdSize, tc.wantProxyColdBlobCount)
			}
		})
	}
}

func TestProxyHitTracker_AddHitAndReset(t *testing.T) {
	tracker := NewProxyHitTracker()
	if tracker.HitBytes() != 0 || tracker.HitCount() != 0 {
		t.Errorf("Initial tracker = (bytes:%d, count:%d), want (0, 0)", tracker.HitBytes(), tracker.HitCount())
	}

	tracker.AddHit(1024, 1)
	tracker.AddHit(2048, 2)
	if tracker.HitBytes() != 3072 || tracker.HitCount() != 3 {
		t.Errorf("After AddHit tracker = (bytes:%d, count:%d), want (3072, 3)", tracker.HitBytes(), tracker.HitCount())
	}

	tracker.Reset()
	if tracker.HitBytes() != 0 || tracker.HitCount() != 0 {
		t.Errorf("After Reset tracker = (bytes:%d, count:%d), want (0, 0)", tracker.HitBytes(), tracker.HitCount())
	}
}

func TestDoDownload_TierStats_DumpJSON(t *testing.T) {
	ctx := context.Background()
	fakeServer, err := fakes.NewServer(t)
	if err != nil {
		t.Fatalf("Failed to create fake RBE server: %v", err)
	}
	defer fakeServer.Stop()

	fileData := []byte("tier stats test file")
	dFile := fakeServer.CAS.Put(fileData)

	rootDir := &repb.Directory{
		Files: []*repb.FileNode{
			{Name: "file.txt", Digest: &repb.Digest{Hash: dFile.Hash, SizeBytes: dFile.Size}},
		},
	}
	rootBytes, err := proto.Marshal(rootDir)
	if err != nil {
		t.Fatal(err)
	}
	dRootDir := fakeServer.CAS.Put(rootBytes)

	testClient, err := fakeServer.NewTestClient(ctx)
	if err != nil {
		t.Fatal(err)
	}
	defer testClient.Close()

	destDir := t.TempDir()
	dumpFile := filepath.Join(destDir, "stats.json")

	tracker := NewProxyHitTracker()
	// Simulate casproxy having served the file out of its own disk cache.
	tracker.AddHit(dFile.Size, 1)

	job := DownloadJob{
		Client:         testClient,
		Digest:         fmt.Sprintf("%s/%d", dRootDir.Hash, dRootDir.Size),
		Dir:            destDir,
		DumpJSON:       dumpFile,
		CASProxyStatus: "",
		UseProxy:       true,
		Tracker:        tracker,
	}

	if err := job.DoDownload(ctx); err != nil {
		t.Fatalf("DoDownload failed: %v", err)
	}

	if job.Stats().SizeProxyHot != dFile.Size || job.Stats().CountProxyHot != 1 {
		t.Errorf("Stats.SizeProxyHot=%d, CountProxyHot=%d, want size=%d count=1",
			job.Stats().SizeProxyHot, job.Stats().CountProxyHot, dFile.Size)
	}

	dumpedContent, err := os.ReadFile(dumpFile)
	if err != nil {
		t.Fatalf("Failed to read dumped stats: %v", err)
	}
	contentStr := string(dumpedContent)
	for _, expectedKey := range []string{
		`"size_hot"`, `"size_dedup"`, `"size_proxy_hot"`, `"size_proxy_cold"`, `"size_cold"`,
		`"count_hot"`, `"count_dedup"`, `"count_proxy_hot"`, `"count_proxy_cold"`, `"count_cold"`,
	} {
		if !strings.Contains(contentStr, expectedKey) {
			t.Errorf("Dumped JSON does not contain expected key %s: %s", expectedKey, contentStr)
		}
	}
}

// corruptReportingCache is a fullCache that also reports corrupt hits, as
// LockFreeCache does.
type corruptReportingCache struct {
	fullCache
	corrupt int64
}

func (c *corruptReportingCache) CorruptBlobsQuarantined() int64 { return c.corrupt }

// TestDoDownload_DumpsCorruptBlobsQuarantined checks that the cache's count of
// corrupt hits reaches stats.json. The count is how a host whose cache is
// being written in place shows up, so it has to be in the file even when it is
// zero, where it says that the cache checked and found nothing.
func TestDoDownload_DumpsCorruptBlobsQuarantined(t *testing.T) {
	for _, tc := range []struct {
		name  string
		cache cache.Cache
		want  int64
	}{
		{name: "cache that checks hits", cache: &corruptReportingCache{corrupt: 3}, want: 3},
		{name: "cache that does not check hits", cache: &fullCache{}, want: 0},
	} {
		t.Run(tc.name, func(t *testing.T) {
			f := newRemoteFailedFixture(t)
			dumpFile := filepath.Join(t.TempDir(), "stats.json")
			job := DownloadJob{
				Client:   f.client,
				Digest:   f.rootDigest,
				Dir:      t.TempDir(),
				DumpJSON: dumpFile,
				Cache:    tc.cache,
			}
			if err := job.DoDownload(context.Background()); err != nil {
				t.Fatalf("DoDownload failed: %v", err)
			}
			if got := job.Stats().CorruptBlobsQuarantined; got != tc.want {
				t.Errorf("Stats.CorruptBlobsQuarantined = %d, want %d", got, tc.want)
			}

			dumped, err := os.ReadFile(dumpFile)
			if err != nil {
				t.Fatalf("Failed to read dumped stats: %v", err)
			}
			var fields map[string]any
			if err := json.Unmarshal(dumped, &fields); err != nil {
				t.Fatalf("Failed to parse dumped stats %s: %v", dumped, err)
			}
			got, ok := fields["corrupt_blobs_quarantined"]
			if !ok {
				t.Fatalf("Dumped JSON has no corrupt_blobs_quarantined: %s", dumped)
			}
			if got != float64(tc.want) {
				t.Errorf("Dumped corrupt_blobs_quarantined = %v, want %d", got, tc.want)
			}
		})
	}
}

// modeCopyReportingCache is a fullCache that also reports mode-mismatch
// copies, as LockFreeCache does.
type modeCopyReportingCache struct {
	fullCache
	copies cache.ModeCopyStats
}

func (c *modeCopyReportingCache) ModeCopies() cache.ModeCopyStats { return c.copies }

// TestDoDownload_DumpsModeCopies checks that the cache's mode-mismatch copies
// reach stats.json, by cause, and are there as zeros for a cache that does not
// make them.
func TestDoDownload_DumpsModeCopies(t *testing.T) {
	for _, tc := range []struct {
		name  string
		cache cache.Cache
		want  map[string]int64
	}{
		{
			name: "cache that copies on a mismatch",
			cache: &modeCopyReportingCache{copies: cache.ModeCopyStats{
				ExecMismatchCopies: 1, ExecMismatchBytes: 10, PermDriftHits: 2, UnreadableRepairs: 3,
			}},
			want: map[string]int64{"exec_mismatch_copies": 1, "exec_mismatch_bytes": 10, "perm_drift_hits": 2, "unreadable_repairs": 3},
		},
		{
			name:  "cache that does not",
			cache: &fullCache{},
			want:  map[string]int64{"exec_mismatch_copies": 0, "exec_mismatch_bytes": 0, "perm_drift_hits": 0, "unreadable_repairs": 0},
		},
	} {
		t.Run(tc.name, func(t *testing.T) {
			f := newRemoteFailedFixture(t)
			dumpFile := filepath.Join(t.TempDir(), "stats.json")
			job := DownloadJob{
				Client:   f.client,
				Digest:   f.rootDigest,
				Dir:      t.TempDir(),
				DumpJSON: dumpFile,
				Cache:    tc.cache,
			}
			if err := job.DoDownload(context.Background()); err != nil {
				t.Fatalf("DoDownload failed: %v", err)
			}

			dumped, err := os.ReadFile(dumpFile)
			if err != nil {
				t.Fatalf("Failed to read dumped stats: %v", err)
			}
			var fields map[string]any
			if err := json.Unmarshal(dumped, &fields); err != nil {
				t.Fatalf("Failed to parse dumped stats %s: %v", dumped, err)
			}
			for key, want := range tc.want {
				got, ok := fields[key]
				if !ok {
					t.Errorf("Dumped JSON has no %s: %s", key, dumped)
					continue
				}
				if got != float64(want) {
					t.Errorf("Dumped %s = %v, want %d", key, got, want)
				}
			}
		})
	}
}

type mockEOFClientStream struct {
	grpc.ClientStream
}

func (m *mockEOFClientStream) RecvMsg(msg any) error {
	return io.EOF
}

func TestTrackedClientStream_RecvMsgIdempotent(t *testing.T) {
	tracker := NewProxyHitTracker()
	trailer := metadata.Pairs(TrailerProxyHitBytes, "1024", TrailerProxyHitCount, "2")
	stream := &trackedClientStream{
		ClientStream: &mockEOFClientStream{},
		trailer:      &trailer,
		tracker:      tracker,
	}

	// First RecvMsg returns io.EOF and records warm metrics
	if err := stream.RecvMsg(nil); err != io.EOF {
		t.Fatalf("RecvMsg err = %v, want io.EOF", err)
	}
	if tracker.HitBytes() != 1024 || tracker.HitCount() != 2 {
		t.Errorf("After first RecvMsg tracker = (bytes:%d, count:%d), want (1024, 2)", tracker.HitBytes(), tracker.HitCount())
	}

	// Second RecvMsg returns io.EOF; should not double-count
	if err := stream.RecvMsg(nil); err != io.EOF {
		t.Fatalf("Second RecvMsg err = %v, want io.EOF", err)
	}
	if tracker.HitBytes() != 1024 || tracker.HitCount() != 2 {
		t.Errorf("After second RecvMsg tracker = (bytes:%d, count:%d), want (1024, 2) without double-counting", tracker.HitBytes(), tracker.HitCount())
	}
}

// newDuplicateDigestsJob returns a cacheless job, as -disable-cache produces,
// for a tree whose first.txt and second.txt share one blob, along with the
// blob's contents.
func newDuplicateDigestsJob(t *testing.T, destDir string) (*DownloadJob, []byte) {
	t.Helper()
	ctx := context.Background()
	fakeServer, err := fakes.NewServer(t)
	if err != nil {
		t.Fatalf("Failed to create fake RBE server: %v", err)
	}
	t.Cleanup(fakeServer.Stop)

	sharedData := []byte("this blob is referenced from two paths")
	dShared := fakeServer.CAS.Put(sharedData)

	rootDir := &repb.Directory{
		Files: []*repb.FileNode{
			{Name: "first.txt", Digest: &repb.Digest{Hash: dShared.Hash, SizeBytes: dShared.Size}},
			{Name: "second.txt", Digest: &repb.Digest{Hash: dShared.Hash, SizeBytes: dShared.Size}},
		},
	}
	rootBytes, err := proto.Marshal(rootDir)
	if err != nil {
		t.Fatal(err)
	}
	dRootDir := fakeServer.CAS.Put(rootBytes)

	testClient, err := fakeServer.NewTestClient(ctx)
	if err != nil {
		t.Fatalf("Failed to create test client: %v", err)
	}
	t.Cleanup(func() { testClient.Close() })

	return &DownloadJob{
		Client: testClient,
		Digest: fmt.Sprintf("%s/%d", dRootDir.Hash, dRootDir.Size),
		Dir:    destDir,
	}, sharedData
}

// A tree may reference the same blob from several paths. The blob is fetched
// once, but every path it appears at still has to end up on disk. The local
// cache path does this by copying the duplicates after the download; the
// no-cache path has to do it too.
func TestDoDownload_DuplicateDigests_NoLocalCache(t *testing.T) {
	destDir := t.TempDir()
	job, sharedData := newDuplicateDigestsJob(t, destDir)

	if err := job.DoDownload(context.Background()); err != nil {
		t.Fatalf("DoDownload failed: %v", err)
	}

	for _, name := range []string{"first.txt", "second.txt"} {
		got, err := os.ReadFile(filepath.Join(destDir, name))
		if err != nil {
			t.Errorf("Failed to read %s: %v", name, err)
			continue
		}
		if !bytes.Equal(got, sharedData) {
			t.Errorf("%s content = %q, want %q", name, got, sharedData)
		}
	}
}

// failDuplicates makes materializing a duplicate fail for the rest of the
// test.
func failDuplicates(t *testing.T) {
	t.Helper()
	orig := materializeDuplicate
	t.Cleanup(func() { materializeDuplicate = orig })
	materializeDuplicate = func(dst, src *client.TreeOutput) error {
		return fmt.Errorf("simulated: cannot materialize %s", dst.Path)
	}
}

// If a duplicate cannot be materialized, the download has not produced the
// tree it was asked for, and must say so rather than report success with a
// path missing. It must also not leave the fetched copy behind, which would
// hand the caller a directory that looks like a partial download, and must
// leave a file that was already at a path alone.
func TestDoDownload_DuplicateCopyFails_NoLocalCache(t *testing.T) {
	destDir := t.TempDir()
	job, _ := newDuplicateDigestsJob(t, destDir)
	failDuplicates(t)

	// second.txt is the duplicate, materialized from first.txt after the
	// fetch.
	const existing = "in the way"
	if err := os.WriteFile(filepath.Join(destDir, "second.txt"), []byte(existing), 0o600); err != nil {
		t.Fatal(err)
	}

	err := job.DoDownload(context.Background())
	if err == nil {
		t.Fatal("DoDownload succeeded although a duplicate path could not be materialized")
	}
	if !strings.Contains(err.Error(), "duplicated files") {
		t.Errorf("DoDownload error = %q, want it to name the duplicate copy as the failure", err)
	}
	if _, statErr := os.Stat(filepath.Join(destDir, "first.txt")); !os.IsNotExist(statErr) {
		t.Errorf("first.txt was left behind after the failed download (stat err %v)", statErr)
	}
	if got, err := os.ReadFile(filepath.Join(destDir, "second.txt")); err != nil || string(got) != existing {
		t.Errorf("second.txt = %q, %v after the failed download, want it unchanged as %q", got, err, existing)
	}
}

// errCacheVolumeFull stands in for the conditions that actually take a cache
// volume out of service on a test host: a full disk, a directory someone
// remounted read-only, an evictor that could not keep up.
var errCacheVolumeFull = errors.New("simulated: no space left on the cache volume")

// pushFailingCache misses everything, so the download runs, and then refuses
// to ingest what came back. If cancel is set, Push calls it first, so the
// refusal arrives together with the job being torn down.
type pushFailingCache struct {
	pushCalled bool
	cancel     context.CancelFunc
}

func (p *pushFailingCache) Pull(ctx context.Context, all []*client.TreeOutput) ([]*client.TreeOutput, []*client.TreeOutput, error) {
	return nil, all, nil
}

func (p *pushFailingCache) Push(ctx context.Context, all map[digest.Digest]*client.TreeOutput) error {
	p.pushCalled = true
	if p.cancel != nil {
		p.cancel()
	}
	return errCacheVolumeFull
}

func (p *pushFailingCache) Close() error { return nil }

// newPushFailingJob returns a job, backed by a fake CAS, that downloads a tree
// holding one file, artifact.bin, through c. It also returns the file's
// contents.
func newPushFailingJob(t *testing.T, c *pushFailingCache) (*DownloadJob, []byte) {
	t.Helper()
	fakeServer, err := fakes.NewServer(t)
	if err != nil {
		t.Fatalf("Failed to create fake RBE server: %v", err)
	}
	t.Cleanup(fakeServer.Stop)

	wantData := []byte("downloaded before the cache refused it")
	dFile := fakeServer.CAS.Put(wantData)

	rootDir := &repb.Directory{
		Files: []*repb.FileNode{
			{Name: "artifact.bin", Digest: &repb.Digest{Hash: dFile.Hash, SizeBytes: dFile.Size}},
		},
	}
	rootBytes, err := proto.Marshal(rootDir)
	if err != nil {
		t.Fatal(err)
	}
	dRootDir := fakeServer.CAS.Put(rootBytes)

	testClient, err := fakeServer.NewTestClient(context.Background())
	if err != nil {
		t.Fatalf("Failed to create test client: %v", err)
	}
	t.Cleanup(func() { testClient.Close() })

	return &DownloadJob{
		Client: testClient,
		Digest: fmt.Sprintf("%s/%d", dRootDir.Hash, dRootDir.Size),
		Dir:    t.TempDir(),
		Cache:  c,
	}, wantData
}

// TestDoDownload_CacheWriteFailureDoesNotFailTheDownload pins the blast radius
// of a broken cache volume.
//
// By the time Push runs, every byte the caller asked for is already on disk and
// the network work is done. A cache that cannot accept those bytes has cost the
// next run a fetch and nothing more. Failing here, and deleting the tree on the
// way out, converted a local and self-healing condition into a failed test run
// -- and did it on whichever hosts had the fullest disks, which are the hosts
// where it is least affordable and most likely to repeat.
func TestDoDownload_CacheWriteFailureDoesNotFailTheDownload(t *testing.T) {
	c := &pushFailingCache{}
	job, wantData := newPushFailingJob(t, c)

	if err := job.DoDownload(context.Background()); err != nil {
		t.Fatalf("DoDownload failed because the cache could not be written: %v", err)
	}
	if !c.pushCalled {
		t.Fatal("Push was never attempted, so this test proves nothing about its failure")
	}

	got, err := os.ReadFile(filepath.Join(job.Dir, "artifact.bin"))
	if err != nil {
		t.Fatalf("Downloaded file was deleted when the cache write failed: %v", err)
	}
	if !bytes.Equal(got, wantData) {
		t.Errorf("artifact.bin content = %q, want %q", got, wantData)
	}

	// Succeeding quietly would be its own problem: a host whose cache volume
	// is broken would report a perfectly healthy run forever, at a hit rate of
	// zero, with nothing in the output to say why.
	if notes := job.Stats().Notes; !strings.Contains(notes, "Failed to cache") {
		t.Errorf("Stats notes = %q, want it to report that the files could not be cached", notes)
	}
}

// TestDoDownload_CacheWriteFailureStillFailsOnContextCancellation is the limit
// on tolerating a failed cache write.
//
// A cache that cannot accept the bytes has cost the next run a fetch and
// nothing else, so the download stands. A cancelled context has not: the job
// is being torn down, nobody is going to read the tree, and reporting success
// would hand the caller a directory that was never finished. The distinction
// matters because Push is exactly where the two arrive looking alike -- the
// cache reports an error either way.
//
// The download has to succeed for Push to be reached at all, so the context is
// cancelled from inside Push rather than up front. DoDownload reports any
// cancelled context as an error on its own, so the error alone proves little;
// the tree being cleaned up is what shows the Push branch took the failure
// path rather than tolerating the write error.
func TestDoDownload_CacheWriteFailureStillFailsOnContextCancellation(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	c := &pushFailingCache{cancel: cancel}
	job, _ := newPushFailingJob(t, c)

	if err := job.DoDownload(ctx); err == nil {
		t.Fatal("DoDownload succeeded although the context was cancelled during Push")
	}
	if !c.pushCalled {
		t.Fatal("Push was never attempted, so this test proves nothing about its failure")
	}
	if _, err := os.Stat(filepath.Join(job.Dir, "artifact.bin")); !os.IsNotExist(err) {
		t.Errorf("artifact.bin was left behind after a cancelled download (stat err %v)", err)
	}
}

// blobSizeFixture is a fake CAS holding a tree of three blobs of increasing
// size: small.txt, medium.txt, and big.bin, whose blob big_copy.bin shares.
// Tests set CacheMaxBlobSize to the medium blob's size, so that only the big
// blob is over the limit and a blob exactly at the limit still uses the cache.
type blobSizeFixture struct {
	client                *client.Client
	rootDigest            string
	small, medium, big    []byte
	dSmall, dMedium, dBig digest.Digest
}

func newBlobSizeFixture(t *testing.T) *blobSizeFixture {
	t.Helper()
	fakeServer, err := fakes.NewServer(t)
	if err != nil {
		t.Fatalf("Failed to create fake RBE server: %v", err)
	}
	t.Cleanup(fakeServer.Stop)

	f := &blobSizeFixture{
		small:  []byte("small"),
		medium: []byte("a medium-sized blob"),
		big:    []byte("a blob larger than the cache blob size limit"),
	}
	f.dSmall = fakeServer.CAS.Put(f.small)
	f.dMedium = fakeServer.CAS.Put(f.medium)
	f.dBig = fakeServer.CAS.Put(f.big)

	rootBytes, err := proto.Marshal(&repb.Directory{
		Files: []*repb.FileNode{
			{Name: "big.bin", Digest: f.dBig.ToProto()},
			{Name: "big_copy.bin", Digest: f.dBig.ToProto()},
			{Name: "medium.txt", Digest: f.dMedium.ToProto()},
			{Name: "small.txt", Digest: f.dSmall.ToProto()},
		},
	})
	if err != nil {
		t.Fatal(err)
	}
	dRoot := fakeServer.CAS.Put(rootBytes)
	f.rootDigest = fmt.Sprintf("%s/%d", dRoot.Hash, dRoot.Size)

	f.client, err = fakeServer.NewTestClient(context.Background())
	if err != nil {
		t.Fatalf("Failed to create test client: %v", err)
	}
	t.Cleanup(func() { f.client.Close() })
	return f
}

// checkTree fails t unless dir holds every file of the fixture's tree with the
// contents CAS has for it.
func (f *blobSizeFixture) checkTree(t *testing.T, dir string) {
	t.Helper()
	for name, want := range map[string][]byte{
		"small.txt":    f.small,
		"medium.txt":   f.medium,
		"big.bin":      f.big,
		"big_copy.bin": f.big,
	} {
		got, err := os.ReadFile(filepath.Join(dir, name))
		if err != nil {
			t.Errorf("Failed to read %s: %v", name, err)
			continue
		}
		if !bytes.Equal(got, want) {
			t.Errorf("%s content = %q, want %q", name, got, want)
		}
	}
}

// recordingCache serves the blobs in has as hits, writing the given contents
// to the target path as a real cache would materialize them, and misses on
// everything else. It records which blobs it was asked to look up and which it
// was given to store.
type recordingCache struct {
	has       map[digest.Digest][]byte
	pullCalls int
	pushCalls int
	pulled    map[digest.Digest]bool
	pushed    map[digest.Digest]bool
}

func newRecordingCache(has map[digest.Digest][]byte) *recordingCache {
	return &recordingCache{
		has:    has,
		pulled: make(map[digest.Digest]bool),
		pushed: make(map[digest.Digest]bool),
	}
}

func (r *recordingCache) Pull(ctx context.Context, all []*client.TreeOutput) (cached, missed []*client.TreeOutput, err error) {
	r.pullCalls++
	for _, o := range all {
		r.pulled[o.Digest] = true
		data, ok := r.has[o.Digest]
		if !ok {
			missed = append(missed, o)
			continue
		}
		if err := os.WriteFile(o.Path, data, 0o644); err != nil {
			return nil, nil, err
		}
		cached = append(cached, o)
	}
	return cached, missed, nil
}

func (r *recordingCache) Push(ctx context.Context, all map[digest.Digest]*client.TreeOutput) error {
	r.pushCalls++
	for dg := range all {
		r.pushed[dg] = true
	}
	return nil
}

func (r *recordingCache) Close() error { return nil }

// TestDoDownload_OversizedBlobsSkipPullAndPush checks that a blob over
// CacheMaxBlobSize is neither served from the local cache, even though the
// cache holds it, nor offered to the cache once it has been fetched, and that
// every path it appears at is still written.
func TestDoDownload_OversizedBlobsSkipPullAndPush(t *testing.T) {
	f := newBlobSizeFixture(t)
	// A stale copy, so that serving it from the cache would show in the file.
	c := newRecordingCache(map[digest.Digest][]byte{f.dBig: []byte("stale cached copy")})
	job := DownloadJob{
		Client:           f.client,
		Digest:           f.rootDigest,
		Dir:              t.TempDir(),
		Cache:            c,
		CacheMaxBlobSize: f.dMedium.Size,
	}
	if err := job.DoDownload(context.Background()); err != nil {
		t.Fatalf("DoDownload failed: %v", err)
	}

	if c.pulled[f.dBig] {
		t.Error("Pull was asked for the blob over the size limit, want it fetched from CAS without a lookup")
	}
	if c.pushed[f.dBig] {
		t.Error("Push was given the blob over the size limit, want it kept out of the cache")
	}
	f.checkTree(t, job.Dir)

	// big.bin was fetched once and big_copy.bin made from it, along with the
	// two smaller blobs the cache did not have.
	stats := job.Stats()
	if want := f.dSmall.Size + f.dMedium.Size + f.dBig.Size; stats.SizeCold != want || stats.CountCold != 3 {
		t.Errorf("Cold stats: got size=%d count=%d, want size=%d count=3", stats.SizeCold, stats.CountCold, want)
	}
	if stats.SizeDedup != f.dBig.Size || stats.CountDedup != 1 {
		t.Errorf("Dedup stats: got size=%d count=%d, want size=%d count=1", stats.SizeDedup, stats.CountDedup, f.dBig.Size)
	}
	if stats.SizeHot != 0 || stats.CountHot != 0 {
		t.Errorf("Hot stats: got size=%d count=%d, want 0: nothing should have been served from the cache", stats.SizeHot, stats.CountHot)
	}
}

// TestDoDownload_AllBlobsOversizedSkipCache checks that a tree whose every blob
// is over CacheMaxBlobSize does not call the cache at all. The default cache
// with -cache-lock takes a host-wide lock for each call, which a download that
// has nothing to look up or store should not wait for.
func TestDoDownload_AllBlobsOversizedSkipCache(t *testing.T) {
	f := newBlobSizeFixture(t)
	c := newRecordingCache(nil)
	job := DownloadJob{
		Client:           f.client,
		Digest:           f.rootDigest,
		Dir:              t.TempDir(),
		Cache:            c,
		CacheMaxBlobSize: 1,
	}
	if err := job.DoDownload(context.Background()); err != nil {
		t.Fatalf("DoDownload failed: %v", err)
	}
	if c.pullCalls != 0 || c.pushCalls != 0 {
		t.Errorf("Cache got %d Pull and %d Push calls, want none when every blob is over the size limit", c.pullCalls, c.pushCalls)
	}
	f.checkTree(t, job.Dir)
}

// TestDoDownload_BlobsWithinCacheMaxBlobSizeUseCache checks that the limit
// only takes the oversized blob out of the cache path: the rest of the tree is
// still looked up, served from the cache when it is there, and stored when it
// is not, including a blob exactly at the limit.
func TestDoDownload_BlobsWithinCacheMaxBlobSizeUseCache(t *testing.T) {
	f := newBlobSizeFixture(t)
	c := newRecordingCache(map[digest.Digest][]byte{f.dSmall: f.small})
	job := DownloadJob{
		Client:           f.client,
		Digest:           f.rootDigest,
		Dir:              t.TempDir(),
		Cache:            c,
		CacheMaxBlobSize: f.dMedium.Size,
	}
	if err := job.DoDownload(context.Background()); err != nil {
		t.Fatalf("DoDownload failed: %v", err)
	}

	for _, tc := range []struct {
		name string
		dg   digest.Digest
	}{
		{name: "small.txt", dg: f.dSmall},
		{name: "medium.txt", dg: f.dMedium},
	} {
		if !c.pulled[tc.dg] {
			t.Errorf("Pull was not asked for %s, which is within the size limit", tc.name)
		}
	}
	if !c.pushed[f.dMedium] {
		t.Error("Push was not given medium.txt, a miss exactly at the size limit")
	}
	if c.pushed[f.dSmall] {
		t.Error("Push was given small.txt, which was a cache hit")
	}
	f.checkTree(t, job.Dir)

	stats := job.Stats()
	if stats.SizeHot != f.dSmall.Size || stats.CountHot != 1 {
		t.Errorf("Hot stats: got size=%d count=%d, want size=%d count=1", stats.SizeHot, stats.CountHot, f.dSmall.Size)
	}
	if want := f.dMedium.Size + f.dBig.Size; stats.SizeCold != want || stats.CountCold != 2 {
		t.Errorf("Cold stats: got size=%d count=%d, want size=%d count=2", stats.SizeCold, stats.CountCold, want)
	}
}

// TestDoDownload_CacheMaxBlobSizeIgnoredWithDisableCache checks that the limit
// has no effect without a cache, which is what -disable-cache produces: every
// blob is fetched and every path written, as without the limit.
func TestDoDownload_CacheMaxBlobSizeIgnoredWithDisableCache(t *testing.T) {
	f := newBlobSizeFixture(t)
	c, err := cache.Open(cache.Options{Disabled: true, Dir: t.TempDir()})
	if err != nil || c != nil {
		t.Fatalf("cache.Open with Disabled = (%v, %v), want (nil, nil)", c, err)
	}
	job := DownloadJob{
		Client:           f.client,
		Digest:           f.rootDigest,
		Dir:              t.TempDir(),
		Cache:            c,
		CacheMaxBlobSize: 1,
	}
	if err := job.DoDownload(context.Background()); err != nil {
		t.Fatalf("DoDownload failed: %v", err)
	}
	f.checkTree(t, job.Dir)

	stats := job.Stats()
	if want := f.dSmall.Size + f.dMedium.Size + f.dBig.Size; stats.SizeCold != want || stats.CountCold != 3 {
		t.Errorf("Cold stats: got size=%d count=%d, want size=%d count=3", stats.SizeCold, stats.CountCold, want)
	}
	if stats.SizeDedup != f.dBig.Size || stats.CountDedup != 1 {
		t.Errorf("Dedup stats: got size=%d count=%d, want size=%d count=1", stats.SizeDedup, stats.CountDedup, f.dBig.Size)
	}
}

// TestDoDownload_ZeroCacheMaxBlobSizeMeansNoLimit checks that a limit of zero,
// or a negative one, sends every blob through the cache, however large.
func TestDoDownload_ZeroCacheMaxBlobSizeMeansNoLimit(t *testing.T) {
	for _, limit := range []int64{0, -1} {
		t.Run(fmt.Sprintf("limit=%d", limit), func(t *testing.T) {
			f := newBlobSizeFixture(t)
			c := newRecordingCache(nil)
			job := DownloadJob{
				Client:           f.client,
				Digest:           f.rootDigest,
				Dir:              t.TempDir(),
				Cache:            c,
				CacheMaxBlobSize: limit,
			}
			if err := job.DoDownload(context.Background()); err != nil {
				t.Fatalf("DoDownload failed: %v", err)
			}
			for _, tc := range []struct {
				name string
				dg   digest.Digest
			}{
				{name: "small.txt", dg: f.dSmall},
				{name: "medium.txt", dg: f.dMedium},
				{name: "big.bin", dg: f.dBig},
			} {
				if !c.pulled[tc.dg] {
					t.Errorf("Pull was not asked for %s", tc.name)
				}
				if !c.pushed[tc.dg] {
					t.Errorf("Push was not given %s", tc.name)
				}
			}
			f.checkTree(t, job.Dir)
		})
	}
}

// TestDoDownload_CacheMaxBlobSizeInBothCacheModes runs the limit against the
// real caches, the default (luci) one and the lock-free one. After a first
// download, the oversized blob must not be anywhere in the cache directory;
// a second download into a fresh directory must then serve the rest of the
// tree from the cache and fetch the oversized blob again.
func TestDoDownload_CacheMaxBlobSizeInBothCacheModes(t *testing.T) {
	for _, tc := range []struct {
		name     string
		lockFree bool
	}{
		{name: "luci cache", lockFree: false},
		{name: "lock-free cache", lockFree: true},
	} {
		t.Run(tc.name, func(t *testing.T) {
			f := newBlobSizeFixture(t)
			opts := cache.Options{LockFree: tc.lockFree, Dir: t.TempDir(), UseHardlink: true}
			download := func() *Stats {
				t.Helper()
				// DoDownload closes the cache, so each run opens its own.
				c, err := cache.Open(opts)
				if err != nil {
					t.Fatalf("cache.Open failed: %v", err)
				}
				job := DownloadJob{
					Client:           f.client,
					Digest:           f.rootDigest,
					Dir:              t.TempDir(),
					Cache:            c,
					CacheMaxBlobSize: f.dMedium.Size,
				}
				if err := job.DoDownload(context.Background()); err != nil {
					t.Fatalf("DoDownload failed: %v", err)
				}
				f.checkTree(t, job.Dir)
				return job.Stats()
			}

			download()
			// Both caches name a blob file after its hash, whatever directory
			// they put it in.
			cachedNames := make(map[string]bool)
			if err := filepath.Walk(opts.Dir, func(path string, info os.FileInfo, err error) error {
				if err != nil {
					return err
				}
				if !info.IsDir() {
					cachedNames[info.Name()] = true
				}
				return nil
			}); err != nil {
				t.Fatalf("Failed to walk the cache directory: %v", err)
			}
			if !cachedNames[f.dMedium.Hash] {
				t.Fatalf("medium.txt's blob %s is not in the cache directory (files: %v), so its absence says nothing about big.bin's", f.dMedium.Hash, cachedNames)
			}
			if cachedNames[f.dBig.Hash] {
				t.Errorf("The blob over the size limit, %s, was stored in the cache", f.dBig.Hash)
			}

			stats := download()
			if want := f.dSmall.Size + f.dMedium.Size; stats.SizeHot != want || stats.CountHot != 2 {
				t.Errorf("Second run hot stats: got size=%d count=%d, want size=%d count=2", stats.SizeHot, stats.CountHot, want)
			}
			if stats.SizeCold != f.dBig.Size || stats.CountCold != 1 {
				t.Errorf("Second run cold stats: got size=%d count=%d, want size=%d count=1", stats.SizeCold, stats.CountCold, f.dBig.Size)
			}
		})
	}
}
