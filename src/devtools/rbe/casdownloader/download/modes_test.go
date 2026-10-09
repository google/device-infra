package download

import (
	"bytes"
	"context"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"syscall"
	"testing"

	"github.com/bazelbuild/remote-apis-sdks/go/pkg/fakes"
	repb "github.com/bazelbuild/remote-apis/build/bazel/remote/execution/v2"
	"github.com/google/device-infra/src/devtools/rbe/casdownloader/cache"
	"google.golang.org/protobuf/proto"
)

type modeTestFile struct {
	data       []byte
	executable bool
}

// newModesJob serves a flat tree of files through a fake CAS and returns a job
// that downloads it without a cache.
func newModesJob(t *testing.T, files map[string]modeTestFile) *DownloadJob {
	t.Helper()
	server, err := fakes.NewServer(t)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(server.Stop)

	dir := &repb.Directory{}
	for name, f := range files {
		dir.Files = append(dir.Files, &repb.FileNode{Name: name, Digest: server.CAS.Put(f.data).ToProto(), IsExecutable: f.executable})
	}
	sort.Slice(dir.Files, func(i, j int) bool { return dir.Files[i].Name < dir.Files[j].Name })
	b, err := proto.Marshal(dir)
	if err != nil {
		t.Fatal(err)
	}
	root := server.CAS.Put(b)

	rbe, err := server.NewTestClient(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { rbe.Close() })
	return &DownloadJob{Client: rbe, Digest: fmt.Sprintf("%s/%d", root.Hash, root.Size), Dir: t.TempDir()}
}

// TestDoDownload_FilesGetCanonicalModes checks that every file a download
// writes carries the canonical mode of its tree node, however it got there:
// fetched in a batch or streamed, or materialized from a duplicate by a link
// or by a copy. It runs under a restrictive umask to show the result does not
// depend on it.
//
// Before, the same executable node came out 0755 when small and 0777 when
// large, since the SDK chmods streamed executables without the umask, and a
// duplicate copied to the other executability came out 0600 or 0700.
func TestDoDownload_FilesGetCanonicalModes(t *testing.T) {
	old := syscall.Umask(0o077)
	t.Cleanup(func() { syscall.Umask(old) })

	shared := []byte("shared")
	// Large enough that the SDK streams it rather than batching it.
	large := bytes.Repeat([]byte("L"), 5<<20)
	job := newModesJob(t, map[string]modeTestFile{
		"data.txt":       {data: shared},
		"dup.txt":        {data: shared},                   // Linked to data.txt.
		"dup-tool":       {data: shared, executable: true}, // Copied: the other executability.
		"small-tool":     {data: []byte("small tool"), executable: true},
		"large-tool":     {data: large, executable: true},
		"large-data.bin": {data: append([]byte("D"), large...)},
	})

	if err := job.DoDownload(context.Background()); err != nil {
		t.Fatalf("DoDownload failed: %v", err)
	}
	for name, want := range map[string]os.FileMode{
		"data.txt":       cache.RegularMode,
		"dup.txt":        cache.RegularMode,
		"dup-tool":       cache.ExecutableMode,
		"small-tool":     cache.ExecutableMode,
		"large-tool":     cache.ExecutableMode,
		"large-data.bin": cache.RegularMode,
	} {
		info, err := os.Stat(filepath.Join(job.Dir, name))
		if err != nil {
			t.Errorf("Stat(%s) failed: %v", name, err)
			continue
		}
		if got := info.Mode().Perm(); got != want {
			t.Errorf("%s mode = %#o, want %#o", name, got, want)
		}
	}
}
