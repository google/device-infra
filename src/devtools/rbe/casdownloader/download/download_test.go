package download

import (
	"bytes"
	"context"
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

// If a duplicate cannot be materialized, the download has not produced the
// tree it was asked for, and must say so rather than report success with a
// path missing. It must also not leave the fetched copy behind, which would
// hand the caller a directory that looks like a partial download.
func TestDoDownload_DuplicateCopyFails_NoLocalCache(t *testing.T) {
	destDir := t.TempDir()
	job, _ := newDuplicateDigestsJob(t, destDir)

	// second.txt is the duplicate, materialized from first.txt after the
	// fetch. Occupying its path makes that step fail.
	if err := os.WriteFile(filepath.Join(destDir, "second.txt"), []byte("in the way"), 0o600); err != nil {
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
}
