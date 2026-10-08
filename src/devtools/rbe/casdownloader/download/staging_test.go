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
	"testing"
	"time"

	"github.com/bazelbuild/remote-apis-sdks/go/pkg/client"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/digest"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/fakes"
	repb "github.com/bazelbuild/remote-apis/build/bazel/remote/execution/v2"
	"github.com/google/device-infra/src/devtools/rbe/casdownloader/cache"
	"google.golang.org/protobuf/proto"
)

// newTreeJob returns a cacheless job that downloads files, keyed by
// slash-separated path, from a fake CAS into a new Dir. A key ending in "/"
// is an empty directory. The blobs of the paths in missing are left out of
// the CAS, so fetching them fails.
func newTreeJob(t *testing.T, files map[string][]byte, missing ...string) *DownloadJob {
	t.Helper()
	server, err := fakes.NewServer(t)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(server.Stop)
	isMissing := map[string]bool{}
	for _, m := range missing {
		isMissing[m] = true
	}

	var build func(prefix string) *repb.Directory
	build = func(prefix string) *repb.Directory {
		dir := &repb.Directory{}
		subdirs := map[string]bool{}
		for path, data := range files {
			rest, ok := strings.CutPrefix(path, prefix)
			if !ok || rest == "" {
				continue
			}
			if name, _, isDir := strings.Cut(rest, "/"); isDir {
				subdirs[name] = true
				continue
			}
			dg := digest.NewFromBlob(data)
			if !isMissing[path] {
				server.CAS.Put(data)
			}
			dir.Files = append(dir.Files, &repb.FileNode{Name: rest, Digest: dg.ToProto()})
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
	return &DownloadJob{Client: rbe, Digest: fmt.Sprintf("%s/%d", root.Hash, root.Size), Dir: t.TempDir()}
}

// chunkedArtifact returns the files of a chunked artifact's tree holding
// files, keyed by slash-separated path, each split into the given chunks.
// Every file is recorded with mode 0644 and modTime.
func chunkedArtifact(t *testing.T, modTime time.Time, files map[string][]string) map[string][]byte {
	t.Helper()
	tree := map[string][]byte{}
	var index []map[string]any
	for path, chunks := range files {
		var infos []map[string]any
		var offset int
		for _, c := range chunks {
			sum := sha256.Sum256([]byte(c))
			sha := hex.EncodeToString(sum[:])
			tree["_chunks/"+sha] = []byte(c)
			infos = append(infos, map[string]any{"sha256": sha, "offset": offset})
			offset += len(c)
		}
		index = append(index, map[string]any{"path": path, "mod_time": modTime, "mode": 0o644, "chunks": infos})
	}
	b, err := json.Marshal(index)
	if err != nil {
		t.Fatal(err)
	}
	tree["_chunks/_chunks_index.json"] = b
	return tree
}

// assertNoTmpFiles fails if anything under dir has a staging temporary name.
func assertNoTmpFiles(t *testing.T, dir string) {
	t.Helper()
	filepath.WalkDir(dir, func(path string, d os.DirEntry, err error) error {
		if err == nil && strings.HasPrefix(d.Name(), ".tmp.") {
			t.Errorf("Temporary file %s was left behind", path)
		}
		return nil
	})
}

func readFile(t *testing.T, path string) string {
	t.Helper()
	b, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("Failed to read %s: %v", path, err)
	}
	return string(b)
}

func writeFile(t *testing.T, path, content string, mode os.FileMode) {
	t.Helper()
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(path, []byte(content), mode); err != nil {
		t.Fatal(err)
	}
}

func exists(t *testing.T, path string) bool {
	t.Helper()
	_, err := os.Lstat(path)
	if err != nil && !os.IsNotExist(err) {
		t.Fatalf("Failed to stat %s: %v", path, err)
	}
	return err == nil
}

// cacheVariants runs a download with and without a local cache.
var cacheVariants = []struct {
	name  string
	cache func(t *testing.T) cache.Cache
}{
	{"without local cache", func(*testing.T) cache.Cache { return nil }},
	{"with local cache", func(t *testing.T) cache.Cache { return &hardlinkCache{dir: t.TempDir()} }},
}

// TestDoDownload_ReplacesExistingFiles downloads into a Dir that already
// holds the tree's paths, one of them a hard link to a cache blob, twice. The
// files are replaced, the blob is not written through, and duplicates no
// longer collide with what is there.
func TestDoDownload_ReplacesExistingFiles(t *testing.T) {
	for _, cv := range cacheVariants {
		t.Run(cv.name, func(t *testing.T) {
			job := newTreeJob(t, map[string][]byte{
				"a.txt":     []byte("new a"),
				"dup.txt":   []byte("new a"),
				"sub/b.txt": []byte("new b"),
			})
			c := cv.cache(t)

			const blobContent = "cached blob content"
			blob := filepath.Join(t.TempDir(), "blob")
			writeFile(t, blob, blobContent, 0o444)
			if err := os.Link(blob, filepath.Join(job.Dir, "a.txt")); err != nil {
				t.Fatal(err)
			}
			writeFile(t, filepath.Join(job.Dir, "dup.txt"), "old dup", 0o600)
			writeFile(t, filepath.Join(job.Dir, "sub", "b.txt"), "old b", 0o600)

			// The second run is warm when there is a cache.
			for i := 0; i < 2; i++ {
				job.Cache = c
				if err := job.DoDownload(context.Background()); err != nil {
					t.Fatalf("DoDownload #%d failed: %v", i+1, err)
				}
				for path, want := range map[string]string{"a.txt": "new a", "dup.txt": "new a", "sub/b.txt": "new b"} {
					if got := readFile(t, filepath.Join(job.Dir, path)); got != want {
						t.Errorf("After DoDownload #%d, %s = %q, want %q", i+1, path, got, want)
					}
				}
				if got := readFile(t, blob); got != blobContent {
					t.Errorf("Blob = %q after DoDownload #%d, want it unchanged as %q", got, i+1, blobContent)
				}
				assertNoTmpFiles(t, job.Dir)
			}
		})
	}
}

// TestDoDownload_FailureLeavesDirUntouched fails a download on a missing
// blob, after other files have been written. Files that were in Dir keep
// their content and mode, and what the download wrote, including the
// directories it created, is gone.
func TestDoDownload_FailureLeavesDirUntouched(t *testing.T) {
	for _, cv := range cacheVariants {
		t.Run(cv.name, func(t *testing.T) {
			job := newTreeJob(t, map[string][]byte{
				"a.txt":          []byte("new a"),
				"new/deep/b.txt": []byte("new b"),
				"z.txt":          []byte("never uploaded"),
			}, "z.txt")
			job.Cache = cv.cache(t)
			writeFile(t, filepath.Join(job.Dir, "a.txt"), "old a", 0o640)
			writeFile(t, filepath.Join(job.Dir, "z.txt"), "old z", 0o600)

			if err := job.DoDownload(context.Background()); err == nil {
				t.Fatal("DoDownload of a tree with a missing blob succeeded")
			}

			for path, want := range map[string]string{"a.txt": "old a", "z.txt": "old z"} {
				if got := readFile(t, filepath.Join(job.Dir, path)); got != want {
					t.Errorf("%s = %q after a failed download, want it unchanged as %q", path, got, want)
				}
			}
			if info, err := os.Stat(filepath.Join(job.Dir, "a.txt")); err != nil || info.Mode().Perm() != 0o640 {
				t.Errorf("a.txt mode = %v, %v after a failed download, want it unchanged as 0640", info.Mode(), err)
			}
			if exists(t, filepath.Join(job.Dir, "new")) {
				t.Error("Directory new, created by the failed download, was left behind")
			}
			if !exists(t, job.Dir) {
				t.Error("Dir itself was removed")
			}
			assertNoTmpFiles(t, job.Dir)
		})
	}
}

// TestDoDownload_CommitFailure fails to move a file into place because a
// directory is at its path. The error names the path, the directory is left
// alone, and nothing staged is left behind.
func TestDoDownload_CommitFailure(t *testing.T) {
	job := newTreeJob(t, map[string][]byte{"a.txt": []byte("new a"), "b.txt": []byte("new b")})
	writeFile(t, filepath.Join(job.Dir, "b.txt", "keep"), "keep", 0o600)

	err := job.DoDownload(context.Background())
	if err == nil {
		t.Fatal("DoDownload over a directory succeeded")
	}
	if !strings.Contains(err.Error(), "b.txt") || !strings.Contains(err.Error(), "1 of 2") {
		t.Errorf("DoDownload error = %q, want it to name b.txt and say 1 of 2 files were moved", err)
	}
	if got := readFile(t, filepath.Join(job.Dir, "b.txt", "keep")); got != "keep" {
		t.Errorf("File in the directory at b.txt = %q, want it unchanged", got)
	}
	assertNoTmpFiles(t, job.Dir)
}

func TestDoDownload_ChunkedArtifact(t *testing.T) {
	modTime := time.Date(2020, 1, 2, 3, 4, 5, 0, time.UTC)
	tree := chunkedArtifact(t, modTime, map[string][]string{
		"IMAGES/single.img": {"single chunk"},
		"multi.img":         {"first ", "second"},
	})
	for _, cv := range cacheVariants {
		t.Run(cv.name, func(t *testing.T) {
			job := newTreeJob(t, tree)
			writeFile(t, filepath.Join(job.Dir, "IMAGES", "single.img"), "old content", 0o600)
			c := cv.cache(t)

			// The second download checks that the first one cleaned up the
			// chunk data, which would otherwise block it. It is warm when
			// there is a cache.
			for i := 0; i < 2; i++ {
				job.Cache = c
				if err := job.DoDownload(context.Background()); err != nil {
					t.Fatalf("DoDownload #%d failed: %v", i+1, err)
				}
				for path, want := range map[string]string{"IMAGES/single.img": "single chunk", "multi.img": "first second"} {
					p := filepath.Join(job.Dir, path)
					if got := readFile(t, p); got != want {
						t.Errorf("%s = %q, want %q", path, got, want)
					}
					info, err := os.Stat(p)
					if err != nil {
						t.Fatal(err)
					}
					if info.Mode().Perm() != 0o644 || !info.ModTime().Equal(modTime) {
						t.Errorf("%s mode, mtime = %v, %v, want 0644, %v", path, info.Mode().Perm(), info.ModTime(), modTime)
					}
				}
				if exists(t, filepath.Join(job.Dir, "_chunks")) {
					t.Error("_chunks was left behind after restoring")
				}
				assertNoTmpFiles(t, job.Dir)
			}
		})
	}
}

// TestDoDownload_ChunkDataAlreadyPresent downloads a chunked artifact into a
// Dir that already holds chunk data, as an earlier -chunks-only run leaves.
// The download fails before fetching anything and leaves the chunk data
// alone.
func TestDoDownload_ChunkDataAlreadyPresent(t *testing.T) {
	tree := chunkedArtifact(t, time.Date(2020, 1, 2, 3, 4, 5, 0, time.UTC), map[string][]string{"a.img": {"content"}})
	for _, existing := range []string{"_chunks/_chunks_index.json", "_chunks_index.json"} {
		t.Run(existing, func(t *testing.T) {
			job := newTreeJob(t, tree)
			job.ChunksOnly = true
			if existing == "_chunks/_chunks_index.json" {
				// An earlier -chunks-only run.
				if err := job.DoDownload(context.Background()); err != nil {
					t.Fatalf("-chunks-only DoDownload failed: %v", err)
				}
			} else {
				writeFile(t, filepath.Join(job.Dir, existing), "stale", 0o600)
			}
			before := readFile(t, filepath.Join(job.Dir, existing))

			for _, chunksOnly := range []bool{false, true} {
				job.ChunksOnly = chunksOnly
				err := job.DoDownload(context.Background())
				if err == nil || !strings.Contains(err.Error(), "already exists") {
					t.Fatalf("DoDownload (chunksOnly=%v) into a Dir holding %s = %v, want an error saying it already exists", chunksOnly, existing, err)
				}
				if job.RemoteFailed() {
					t.Error("RemoteFailed() = true, want false")
				}
				if got := readFile(t, filepath.Join(job.Dir, existing)); got != before {
					t.Errorf("%s changed after the failed download", existing)
				}
				if exists(t, filepath.Join(job.Dir, "a.img")) {
					t.Error("a.img was restored, want nothing written")
				}
			}
		})
	}
}

// TestDoDownload_ChunksOnly keeps the chunk data, which is the output, and
// restores nothing.
func TestDoDownload_ChunksOnly(t *testing.T) {
	tree := chunkedArtifact(t, time.Date(2020, 1, 2, 3, 4, 5, 0, time.UTC), map[string][]string{"a.img": {"content"}})
	job := newTreeJob(t, tree)
	job.ChunksOnly = true
	if err := job.DoDownload(context.Background()); err != nil {
		t.Fatalf("DoDownload failed: %v", err)
	}
	for path, want := range tree {
		if got := readFile(t, filepath.Join(job.Dir, path)); got != string(want) {
			t.Errorf("%s = %q, want %q", path, got, want)
		}
	}
	if exists(t, filepath.Join(job.Dir, "a.img")) {
		t.Error("a.img was restored with -chunks-only")
	}
	assertNoTmpFiles(t, job.Dir)
}

// TestDoDownload_NonChunkedTreeIgnoresStaleChunkData downloads a tree without
// chunk data into a Dir with some. It neither claims nor restores it.
func TestDoDownload_NonChunkedTreeIgnoresStaleChunkData(t *testing.T) {
	job := newTreeJob(t, map[string][]byte{"a.txt": []byte("a")})
	stale := chunkedArtifact(t, time.Date(2020, 1, 2, 3, 4, 5, 0, time.UTC), map[string][]string{"stale.img": {"stale"}})
	for path, data := range stale {
		writeFile(t, filepath.Join(job.Dir, path), string(data), 0o600)
	}

	if err := job.DoDownload(context.Background()); err != nil {
		t.Fatalf("DoDownload failed: %v", err)
	}
	if exists(t, filepath.Join(job.Dir, "stale.img")) {
		t.Error("stale.img was restored from chunk data this download did not fetch")
	}
	for path := range stale {
		if !exists(t, filepath.Join(job.Dir, path)) {
			t.Errorf("Stale chunk data %s was removed", path)
		}
	}
}

// TestDoDownload_ChunkRestoreFailure fails restoring a chunked file, here for
// a chunk the tree does not hold, or for a path outside Dir. A file already
// at the restored path is untouched and the chunk data is removed.
func TestDoDownload_ChunkRestoreFailure(t *testing.T) {
	modTime := time.Date(2020, 1, 2, 3, 4, 5, 0, time.UTC)
	tests := []struct {
		name   string
		mangle func(tree map[string][]byte)
	}{
		{"missing chunk", func(tree map[string][]byte) {
			for path := range tree {
				if path != "_chunks/_chunks_index.json" {
					delete(tree, path)
				}
			}
		}},
		{"path outside Dir", func(tree map[string][]byte) {
			tree["_chunks/_chunks_index.json"] = bytes.Replace(tree["_chunks/_chunks_index.json"], []byte(`"b.img"`), []byte(`"../escape.img"`), 1)
		}},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			tree := chunkedArtifact(t, modTime, map[string][]string{"a.img": {"a content"}, "b.img": {"b content"}})
			tc.mangle(tree)
			job := newTreeJob(t, tree)
			writeFile(t, filepath.Join(job.Dir, "a.img"), "old a", 0o600)

			if err := job.DoDownload(context.Background()); err == nil {
				t.Fatal("DoDownload succeeded, want a restore error")
			}
			if got := readFile(t, filepath.Join(job.Dir, "a.img")); got != "old a" {
				t.Errorf("a.img = %q after a failed restore, want it unchanged", got)
			}
			if exists(t, filepath.Join(job.Dir, "b.img")) || exists(t, filepath.Join(filepath.Dir(job.Dir), "escape.img")) {
				t.Error("A file was restored by the failed download")
			}
			if exists(t, filepath.Join(job.Dir, "_chunks")) {
				t.Error("_chunks was left behind after a failed restore")
			}
			assertNoTmpFiles(t, job.Dir)
		})
	}
}

func TestStaging_TmpName(t *testing.T) {
	s, err := newStaging("/out")
	if err != nil {
		t.Fatal(err)
	}
	other, err := newStaging("/out")
	if err != nil {
		t.Fatal(err)
	}
	if s.runID == other.runID {
		t.Errorf("Two stagings share run ID %q", s.runID)
	}

	if got, want := s.tmpName("/out/sub/a.txt"), "/out/sub/.tmp."+s.runID+".a.txt"; got != want {
		t.Errorf("tmpName = %q, want %q", got, want)
	}
	long := strings.Repeat("x", maxNameLen)
	got := s.tmpName("/out/" + long)
	if filepath.Dir(got) != "/out" || len(filepath.Base(got)) > maxNameLen || !strings.HasPrefix(filepath.Base(got), ".tmp."+s.runID+".") {
		t.Errorf("tmpName of a %d-byte name = %q, want a name under /out of at most %d bytes with the run's prefix", len(long), got, maxNameLen)
	}
	if got == s.tmpName("/out/"+long[1:]+"y") {
		t.Error("tmpName gives two long names the same temporary name")
	}
}

// TestStaging_ClaimChunkDataIsExclusive checks that of two attempts on one
// Dir only one can claim the chunk data.
func TestStaging_ClaimChunkDataIsExclusive(t *testing.T) {
	dir := filepath.Join(t.TempDir(), "out")
	first, _ := newStaging(dir)
	second, _ := newStaging(dir)
	if err := first.claimChunkData(); err != nil {
		t.Fatalf("First claim failed: %v", err)
	}
	if err := second.claimChunkData(); err == nil {
		t.Fatal("Second claim succeeded, want an error")
	}
	second.abort()
	if !exists(t, filepath.Join(dir, "_chunks")) {
		t.Error("The second attempt's abort removed the first attempt's chunk data")
	}
	first.abort()
	if exists(t, filepath.Join(dir, "_chunks")) {
		t.Error("The first attempt's abort left its chunk data behind")
	}
	if !exists(t, dir) {
		t.Error("abort removed Dir itself")
	}
}

// TestDoDownload_EmptyDirectories creates the tree's empty directories, and
// removes them again when the download fails.
func TestDoDownload_EmptyDirectories(t *testing.T) {
	files := map[string][]byte{"empty/": nil, "a.txt": []byte("a")}
	job := newTreeJob(t, files)
	if err := job.DoDownload(context.Background()); err != nil {
		t.Fatalf("DoDownload failed: %v", err)
	}
	if info, err := os.Stat(filepath.Join(job.Dir, "empty")); err != nil || !info.IsDir() {
		t.Errorf("Empty directory was not created (stat err %v)", err)
	}

	files["z.txt"] = []byte("never uploaded")
	job = newTreeJob(t, files, "z.txt")
	if err := job.DoDownload(context.Background()); err == nil {
		t.Fatal("DoDownload of a tree with a missing blob succeeded")
	}
	if exists(t, filepath.Join(job.Dir, "empty")) {
		t.Error("Empty directory created by the failed download was left behind")
	}
}

// TestDoDownload_FailureKeepsDirGivenWithTrailingSlash fails a download into
// a Dir, given as "out/", that does not exist yet. The download creates it,
// and must not remove it again, even though the directories it records
// creating are cleaned paths.
func TestDoDownload_FailureKeepsDirGivenWithTrailingSlash(t *testing.T) {
	for _, cv := range cacheVariants {
		t.Run(cv.name, func(t *testing.T) {
			job := newTreeJob(t, map[string][]byte{
				"sub/a.txt": []byte("a"),
				"z.txt":     []byte("never uploaded"),
			}, "z.txt")
			job.Cache = cv.cache(t)
			out := filepath.Join(job.Dir, "out")
			job.Dir = out + string(filepath.Separator)

			if err := job.DoDownload(context.Background()); err == nil {
				t.Fatal("DoDownload of a tree with a missing blob succeeded")
			}
			if !exists(t, out) {
				t.Error("Dir, given with a trailing slash, was removed")
			}
			if exists(t, filepath.Join(out, "sub")) {
				t.Error("Directory sub, created by the failed download, was left behind")
			}
			assertNoTmpFiles(t, out)
		})
	}
}

// TestDoDownload_PathConflicts covers paths the tree needs as directories
// that are taken by files, including Dir itself. The download fails, and the
// file is untouched.
func TestDoDownload_PathConflicts(t *testing.T) {
	chunked := chunkedArtifact(t, time.Date(2020, 1, 2, 3, 4, 5, 0, time.UTC), map[string][]string{"sub/a.img": {"a"}})
	tests := []struct {
		name string
		tree map[string][]byte
		// file is the path, relative to Dir, taken by a file.
		file string
	}{
		{"subdirectory", map[string][]byte{"sub/a.txt": []byte("a")}, "sub"},
		{"restored subdirectory", chunked, "sub"},
		{"Dir", map[string][]byte{"a.txt": []byte("a")}, "."},
		{"Dir of a chunked tree", chunked, "."},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			job := newTreeJob(t, tc.tree)
			if tc.file == "." {
				job.Dir = filepath.Join(job.Dir, "out")
			}
			path := filepath.Join(job.Dir, tc.file)
			writeFile(t, path, "a file", 0o600)

			if err := job.DoDownload(context.Background()); err == nil {
				t.Fatal("DoDownload succeeded, want an error")
			}
			if got := readFile(t, path); got != "a file" {
				t.Errorf("File at %s = %q after the failed download, want it unchanged", tc.file, got)
			}
			if tc.file != "." {
				assertNoTmpFiles(t, job.Dir)
			}
		})
	}
}

// TestDoDownload_LegacyChunksIndex downloads a chunked artifact whose index
// is at the root of the tree, as very old builds put it.
func TestDoDownload_LegacyChunksIndex(t *testing.T) {
	tree := chunkedArtifact(t, time.Date(2020, 1, 2, 3, 4, 5, 0, time.UTC), map[string][]string{"a.img": {"content"}})
	tree["_chunks_index.json"] = tree["_chunks/_chunks_index.json"]
	delete(tree, "_chunks/_chunks_index.json")
	job := newTreeJob(t, tree)

	if err := job.DoDownload(context.Background()); err != nil {
		t.Fatalf("DoDownload failed: %v", err)
	}
	if got := readFile(t, filepath.Join(job.Dir, "a.img")); got != "content" {
		t.Errorf("a.img = %q, want %q", got, "content")
	}
	for _, path := range []string{"_chunks", "_chunks_index.json"} {
		if exists(t, filepath.Join(job.Dir, path)) {
			t.Errorf("%s was left behind after restoring", path)
		}
	}
	if !strings.Contains(job.Stats().Notes, "moved") {
		t.Errorf("Stats notes = %q, want one about moving the legacy index", job.Stats().Notes)
	}
}

// TestDoDownload_ReadOnlyDirWithChunkData fails to claim the chunk data in a
// Dir it cannot write to.
func TestDoDownload_ReadOnlyDirWithChunkData(t *testing.T) {
	if os.Geteuid() == 0 {
		t.Skip("Directory permissions are not enforced for root")
	}
	tree := chunkedArtifact(t, time.Date(2020, 1, 2, 3, 4, 5, 0, time.UTC), map[string][]string{"a.img": {"content"}})
	job := newTreeJob(t, tree)
	if err := os.Chmod(job.Dir, 0o555); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { os.Chmod(job.Dir, 0o755) })

	if err := job.DoDownload(context.Background()); err == nil || !strings.Contains(err.Error(), "_chunks") {
		t.Errorf("DoDownload into a read-only Dir = %v, want an error naming _chunks", err)
	}
}

// TestMaterializeDuplicate_CopiesWhenModesDiffer checks that a duplicate
// whose mode differs from the fetched file's is copied rather than linked,
// since a link would share one mode.
func TestMaterializeDuplicate_CopiesWhenModesDiffer(t *testing.T) {
	dir := t.TempDir()
	src := &client.TreeOutput{Path: filepath.Join(dir, "src")}
	dst := &client.TreeOutput{Path: filepath.Join(dir, "dst"), IsExecutable: true}
	writeFile(t, src.Path, "content", fileMode(src))

	if err := materializeDuplicate(dst, src); err != nil {
		t.Fatalf("materializeDuplicate failed: %v", err)
	}
	if got := readFile(t, dst.Path); got != "content" {
		t.Errorf("dst = %q, want %q", got, "content")
	}
	srcInfo, err := os.Stat(src.Path)
	if err != nil {
		t.Fatal(err)
	}
	dstInfo, err := os.Stat(dst.Path)
	if err != nil {
		t.Fatal(err)
	}
	if os.SameFile(srcInfo, dstInfo) {
		t.Error("dst is a link to src, want a copy")
	}
	if dstInfo.Mode().Perm() != fileMode(dst) {
		t.Errorf("dst mode = %#o, want %#o", dstInfo.Mode().Perm(), fileMode(dst))
	}

	// A taken name is an error, not something to overwrite.
	if err := materializeDuplicate(dst, src); err == nil {
		t.Error("materializeDuplicate over an existing file succeeded, want an error")
	}
}
