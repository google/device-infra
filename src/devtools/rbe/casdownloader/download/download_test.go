package download

import (
	"bytes"
	"context"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/bazelbuild/remote-apis-sdks/go/pkg/fakes"
	repb "github.com/bazelbuild/remote-apis/build/bazel/remote/execution/v2"
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
