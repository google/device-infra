package download

import (
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"syscall"
	"testing"

	"github.com/bazelbuild/remote-apis-sdks/go/pkg/client"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/digest"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/fakes"
	repb "github.com/bazelbuild/remote-apis/build/bazel/remote/execution/v2"
	"google.golang.org/protobuf/proto"
)

// hardlinkCache is a cache.Cache that links blobs in and out by hash, like the
// real caches do with hardlinks enabled.
type hardlinkCache struct{ dir string }

func (h *hardlinkCache) blob(data []byte) string {
	return filepath.Join(h.dir, digest.NewFromBlob(data).Hash)
}

func (h *hardlinkCache) Pull(ctx context.Context, all []*client.TreeOutput) (cached, missed []*client.TreeOutput, err error) {
	for _, o := range all {
		if os.Link(filepath.Join(h.dir, o.Digest.Hash), o.Path) == nil {
			cached = append(cached, o)
		} else {
			missed = append(missed, o)
		}
	}
	return cached, missed, nil
}

func (h *hardlinkCache) Push(ctx context.Context, all map[digest.Digest]*client.TreeOutput) error {
	for dg, o := range all {
		if err := os.Link(o.Path, filepath.Join(h.dir, dg.Hash)); err != nil && !os.IsExist(err) {
			return err
		}
	}
	return nil
}

func (h *hardlinkCache) Close() error { return nil }

// newCopyFiltersJob returns a job that downloads files, keyed by
// slash-separated path, from a fake CAS through c.
func newCopyFiltersJob(t *testing.T, c *hardlinkCache, files map[string][]byte) *DownloadJob {
	t.Helper()
	server, err := fakes.NewServer(t)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(server.Stop)

	var build func(prefix string) *repb.Directory
	build = func(prefix string) *repb.Directory {
		dir := &repb.Directory{}
		subdirs := map[string]bool{}
		for path, data := range files {
			rest, ok := strings.CutPrefix(path, prefix)
			if !ok {
				continue
			}
			if name, _, isDir := strings.Cut(rest, "/"); isDir {
				subdirs[name] = true
			} else {
				dir.Files = append(dir.Files, &repb.FileNode{Name: rest, Digest: server.CAS.Put(data).ToProto()})
			}
		}
		for name := range subdirs {
			b, _ := proto.Marshal(build(prefix + name + "/"))
			dir.Directories = append(dir.Directories, &repb.DirectoryNode{Name: name, Digest: server.CAS.Put(b).ToProto()})
		}
		sort.Slice(dir.Files, func(i, j int) bool { return dir.Files[i].Name < dir.Files[j].Name })
		sort.Slice(dir.Directories, func(i, j int) bool { return dir.Directories[i].Name < dir.Directories[j].Name })
		return dir
	}
	b, _ := proto.Marshal(build(""))
	root := server.CAS.Put(b)

	rbe, err := server.NewTestClient(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { rbe.Close() })
	return &DownloadJob{Client: rbe, Digest: fmt.Sprintf("%s/%d", root.Hash, root.Size), Dir: t.TempDir(), Cache: c}
}

func nlink(t *testing.T, path string) uint64 {
	t.Helper()
	info, err := os.Lstat(path)
	if err != nil {
		t.Fatal(err)
	}
	return uint64(info.Sys().(*syscall.Stat_t).Nlink)
}

// Matched files come out unshared on cold (linked into the cache by Push) and
// warm (linked from the cache) runs, so truncating them in place, as Cuttlefish
// does to vbmeta images, leaves the cache intact. Unmatched files stay linked.
func TestDoDownload_CopyFilters(t *testing.T) {
	vbmeta := bytes.Repeat([]byte("v"), 4096)
	super := []byte("super")
	for _, warm := range []bool{false, true} {
		t.Run(fmt.Sprintf("warm=%v", warm), func(t *testing.T) {
			c := &hardlinkCache{dir: t.TempDir()}
			if warm {
				for _, data := range [][]byte{vbmeta, super} {
					if err := os.WriteFile(c.blob(data), data, 0o600); err != nil {
						t.Fatal(err)
					}
				}
			}
			job := newCopyFiltersJob(t, c, map[string][]byte{
				"vbmeta.img": vbmeta, "IMAGES/vbmeta.img": vbmeta, "super.img": super,
			})
			job.CopyFilters = []string{`^vbmeta.*\.img$`, `^IMAGES/vbmeta.*\.img$`}

			if err := job.DoDownload(context.Background()); err != nil {
				t.Fatalf("DoDownload failed: %v", err)
			}
			for _, name := range []string{"vbmeta.img", "IMAGES/vbmeta.img"} {
				path := filepath.Join(job.Dir, name)
				if got := nlink(t, path); got != 1 {
					t.Errorf("%s has %d links, want 1", name, got)
				}
				if err := os.Truncate(path, 65536); err != nil {
					t.Fatal(err)
				}
			}
			if got, _ := os.ReadFile(c.blob(vbmeta)); !bytes.Equal(got, vbmeta) {
				t.Errorf("cached vbmeta is %d bytes after truncating the downloaded files, want %d", len(got), len(vbmeta))
			}
			if got := nlink(t, filepath.Join(job.Dir, "super.img")); got < 2 {
				t.Errorf("super.img has %d links, want it still hardlinked to the cache", got)
			}
		})
	}
}

// A single-chunk member is restored as a hardlink to _chunks/<sha256>, which is
// linked to the cache, and is not in the tree under its own name. For a chunked
// artifact only restored files are subject to the filters; the chunk store is
// not, even when kept and matched.
func TestDoDownload_CopyFilters_ChunkRestoredMember(t *testing.T) {
	vbmeta := bytes.Repeat([]byte("c"), 4096)
	sum := sha256.Sum256(vbmeta)
	sha := hex.EncodeToString(sum[:])
	index, _ := json.Marshal([]map[string]any{{
		"path": "IMAGES/vbmeta.img", "chunks": []map[string]any{{"sha256": sha, "offset": 0}},
	}})
	c := &hardlinkCache{dir: t.TempDir()}
	if err := os.WriteFile(c.blob(vbmeta), vbmeta, 0o600); err != nil {
		t.Fatal(err)
	}
	job := newCopyFiltersJob(t, c, map[string][]byte{"_chunks/" + sha: vbmeta, "_chunks/_chunks_index.json": index})
	job.KeepChunks = true
	job.CopyFilters = []string{`^IMAGES/vbmeta.*\.img$`, `^_chunks/`}

	if err := job.DoDownload(context.Background()); err != nil {
		t.Fatalf("DoDownload failed: %v", err)
	}
	if got := nlink(t, filepath.Join(job.Dir, "IMAGES/vbmeta.img")); got != 1 {
		t.Errorf("IMAGES/vbmeta.img has %d links, want 1", got)
	}
	if got := nlink(t, filepath.Join(job.Dir, "_chunks", sha)); got < 2 {
		t.Errorf("_chunks/%s has %d links, want it left hardlinked to the cache", sha, got)
	}
}

// Files in Dir that this job did not download are left alone even if they
// match.
func TestDoDownload_CopyFilters_IgnoresOtherFiles(t *testing.T) {
	job := newCopyFiltersJob(t, &hardlinkCache{dir: t.TempDir()}, map[string][]byte{"a.txt": []byte("a")})
	job.CopyFilters = []string{`\.img$`}
	other := filepath.Join(job.Dir, "other.img")
	if err := os.WriteFile(other, []byte("x"), 0o600); err != nil {
		t.Fatal(err)
	}
	if err := os.Link(other, filepath.Join(job.Dir, "other_link")); err != nil {
		t.Fatal(err)
	}

	if err := job.DoDownload(context.Background()); err != nil {
		t.Fatalf("DoDownload failed: %v", err)
	}
	if got := nlink(t, other); got != 2 {
		t.Errorf("other.img has %d links, want it untouched at 2", got)
	}
}

// An invalid pattern fails before anything is fetched, and is not a remote
// failure, which would trigger a pointless fallback from casproxy.
func TestDoDownload_CopyFilters_InvalidPattern(t *testing.T) {
	job := newCopyFiltersJob(t, nil, map[string][]byte{"a.txt": []byte("a")})
	job.Cache = nil
	job.CopyFilters = []string{"("}

	if err := job.DoDownload(context.Background()); err == nil || !strings.Contains(err.Error(), "copy-filters") {
		t.Fatalf("DoDownload error = %v, want one naming -copy-filters", err)
	}
	if job.RemoteFailed() {
		t.Error("RemoteFailed() = true, want false")
	}
}

// With ChunksOnly no files are restored, so CopyFilters does nothing, even for
// callers of the library that set both.
func TestDoDownload_CopyFilters_SkippedWithChunksOnly(t *testing.T) {
	data := []byte("a downloaded file")
	job := newCopyFiltersJob(t, &hardlinkCache{dir: t.TempDir()}, map[string][]byte{"vbmeta.img": data})
	job.ChunksOnly = true
	job.KeepChunks = true
	job.CopyFilters = []string{`.*`}

	if err := job.DoDownload(context.Background()); err != nil {
		t.Fatalf("DoDownload failed: %v", err)
	}
	if got := nlink(t, filepath.Join(job.Dir, "vbmeta.img")); got < 2 {
		t.Errorf("vbmeta.img has %d links, want it left hardlinked to the cache", got)
	}
}

// Whether an artifact is chunked is decided from what this job downloaded, not
// from a chunks index an earlier -keep-chunks run left in Dir.
func TestDoDownload_CopyFilters_IgnoresStaleChunksIndex(t *testing.T) {
	vbmeta := bytes.Repeat([]byte("v"), 4096)
	c := &hardlinkCache{dir: t.TempDir()}
	if err := os.WriteFile(c.blob(vbmeta), vbmeta, 0o600); err != nil {
		t.Fatal(err)
	}
	job := newCopyFiltersJob(t, c, map[string][]byte{"vbmeta.img": vbmeta})
	job.CopyFilters = []string{`^vbmeta.*\.img$`}

	stale := []byte("stale")
	sum := sha256.Sum256(stale)
	sha := hex.EncodeToString(sum[:])
	index, _ := json.Marshal([]map[string]any{{
		"path": "stale.img", "chunks": []map[string]any{{"sha256": sha, "offset": 0}},
	}})
	chunksDir := filepath.Join(job.Dir, "_chunks")
	if err := os.MkdirAll(chunksDir, 0o700); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(chunksDir, sha), stale, 0o600); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(chunksDir, "_chunks_index.json"), index, 0o600); err != nil {
		t.Fatal(err)
	}

	if err := job.DoDownload(context.Background()); err != nil {
		t.Fatalf("DoDownload failed: %v", err)
	}
	if got := nlink(t, filepath.Join(job.Dir, "vbmeta.img")); got != 1 {
		t.Errorf("vbmeta.img has %d links, want 1", got)
	}
}
