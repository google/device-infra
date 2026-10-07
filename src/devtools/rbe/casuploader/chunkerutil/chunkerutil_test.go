package chunkerutil

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"math/rand"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/google/device-infra/src/devtools/rbe/casuploader/chunker"
)

func TestFindChunksIndex(t *testing.T) {
	// Test case: Index file exists directly in the given directory.
	t.Run("IndexInDir", func(t *testing.T) {
		// Create a temporary directory for the test.
		tmpDir := t.TempDir()
		// Define the expected path for the index file.
		expectedPath := filepath.Join(tmpDir, ChunksIndexFileName)
		// Create a dummy index file at the expected location.
		if _, err := os.Create(expectedPath); err != nil {
			t.Fatalf("Failed to create dummy index file: %v", err)
		}

		// Call FindChunksIndex and check if it returns the correct path.
		gotPath, err := FindChunksIndex(tmpDir)
		if err != nil {
			t.Errorf("FindChunksIndex() returned an unexpected error: %v", err)
		}
		if gotPath != expectedPath {
			t.Errorf("FindChunksIndex() = %v, want %v", gotPath, expectedPath)
		}
	})

	// Test case: Index file exists in the _chunks subfolder.
	t.Run("IndexInChunksSubfolder", func(t *testing.T) {
		// Create a temporary directory and a _chunks subdirectory.
		tmpDir := t.TempDir()
		chunksDir := filepath.Join(tmpDir, ChunksDirName)
		if err := os.Mkdir(chunksDir, 0755); err != nil {
			t.Fatalf("Failed to create chunks dir: %v", err)
		}
		// Define the expected path for the index file inside _chunks.
		expectedPath := filepath.Join(chunksDir, ChunksIndexFileName)
		// Create a dummy index file at the expected location.
		if _, err := os.Create(expectedPath); err != nil {
			t.Fatalf("Failed to create dummy index file: %v", err)
		}

		// Call FindChunksIndex and verify the result.
		gotPath, err := FindChunksIndex(tmpDir)
		if err != nil {
			t.Errorf("FindChunksIndex() returned an unexpected error: %v", err)
		}
		if gotPath != expectedPath {
			t.Errorf("FindChunksIndex() = %v, want %v", gotPath, expectedPath)
		}
	})

	// Test case: Index file is not found in either location.
	t.Run("IndexNotFound", func(t *testing.T) {
		// Create an empty temporary directory.
		tmpDir := t.TempDir()
		// Call FindChunksIndex and expect an error.
		_, err := FindChunksIndex(tmpDir)
		if err == nil {
			t.Error("FindChunksIndex() was expected to return an error, but it did not")
		}
	})

	// Test case: Index file exists in both the directory and the _chunks subfolder.
	// The function should prioritize the one in the _chunks subfolder.
	t.Run("IndexInBothLocations", func(t *testing.T) {
		// Create a temporary directory and a _chunks subdirectory.
		tmpDir := t.TempDir()
		chunksDir := filepath.Join(tmpDir, ChunksDirName)
		if err := os.Mkdir(chunksDir, 0755); err != nil {
			t.Fatalf("Failed to create chunks dir: %v", err)
		}

		// Create a dummy index file in the root of the temp directory.
		indexPathInDir := filepath.Join(tmpDir, ChunksIndexFileName)
		if _, err := os.Create(indexPathInDir); err != nil {
			t.Fatalf("Failed to create dummy index file in dir: %v", err)
		}

		// Create another dummy index file in the _chunks subfolder.
		// This is the one we expect to be found.
		expectedPath := filepath.Join(chunksDir, ChunksIndexFileName)
		if _, err := os.Create(expectedPath); err != nil {
			t.Fatalf("Failed to create dummy index file in chunks dir: %v", err)
		}

		// Call FindChunksIndex and verify it returns the path to the file in _chunks.
		gotPath, err := FindChunksIndex(tmpDir)
		if err != nil {
			t.Errorf("FindChunksIndex() returned an unexpected error: %v", err)
		}
		if gotPath != expectedPath {
			t.Errorf("FindChunksIndex() = %v, want %v", gotPath, expectedPath)
		}
	})
}

// TestRestoreFiles_DoesNotModifyCachedChunk restores a single-chunk file whose
// chunk is hard linked into a cache, as casdownloader's chunks are. Applying
// the file's mode and mtime must not touch the cached blob.
func TestRestoreFiles_DoesNotModifyCachedChunk(t *testing.T) {
	for _, keepChunks := range []bool{false, true} {
		t.Run(fmt.Sprintf("keepChunks=%v", keepChunks), func(t *testing.T) {
			srcDir := t.TempDir()
			dstDir := t.TempDir()
			cacheDir := t.TempDir()

			const content = "single chunk content"
			h := sha256.Sum256([]byte(content))
			sha := hex.EncodeToString(h[:])
			chunksDir := filepath.Join(srcDir, ChunksDirName)
			if err := os.Mkdir(chunksDir, 0755); err != nil {
				t.Fatalf("Failed to create chunks dir: %v", err)
			}
			chunkPath := filepath.Join(chunksDir, sha)
			if err := os.WriteFile(chunkPath, []byte(content), 0644); err != nil {
				t.Fatalf("Failed to write chunk: %v", err)
			}
			blobPath := filepath.Join(cacheDir, sha)
			if err := os.Link(chunkPath, blobPath); err != nil {
				t.Fatalf("Failed to link chunk into cache: %v", err)
			}
			blobBefore, err := os.Stat(blobPath)
			if err != nil {
				t.Fatalf("Failed to stat blob: %v", err)
			}

			modTime := time.Date(2020, 1, 2, 3, 4, 5, 0, time.UTC)
			const mode = os.FileMode(0755)
			index := []ChunksIndex{{
				Path:    filepath.Join("a", "restored.bin"),
				ModTime: modTime,
				Mode:    mode,
				Chunks:  []chunker.ChunkInfo{{SHA256: sha, Offset: 0}},
			}}
			if err := CreateIndexFile(srcDir, index); err != nil {
				t.Fatalf("CreateIndexFile failed: %v", err)
			}

			if err := RestoreFiles(srcDir, dstDir, keepChunks); err != nil {
				t.Fatalf("RestoreFiles failed: %v", err)
			}

			restoredPath := filepath.Join(dstDir, "a", "restored.bin")
			got, err := os.ReadFile(restoredPath)
			if err != nil {
				t.Fatalf("Failed to read restored file: %v", err)
			}
			if string(got) != content {
				t.Errorf("Restored content = %q, want %q", got, content)
			}
			restored, err := os.Stat(restoredPath)
			if err != nil {
				t.Fatalf("Failed to stat restored file: %v", err)
			}
			if restored.Mode().Perm() != mode {
				t.Errorf("Restored mode = %#o, want %#o", restored.Mode().Perm(), mode)
			}
			if !restored.ModTime().Equal(modTime) {
				t.Errorf("Restored mtime = %v, want %v", restored.ModTime(), modTime)
			}

			blobAfter, err := os.Stat(blobPath)
			if err != nil {
				t.Fatalf("Failed to stat blob: %v", err)
			}
			if os.SameFile(restored, blobAfter) {
				t.Error("Restored file shares an inode with the cached blob, want an independent copy")
			}
			if blobAfter.Mode() != blobBefore.Mode() {
				t.Errorf("Cached blob mode = %v after restore, want it unchanged as %v", blobAfter.Mode(), blobBefore.Mode())
			}
			if !blobAfter.ModTime().Equal(blobBefore.ModTime()) {
				t.Errorf("Cached blob mtime = %v after restore, want it unchanged as %v", blobAfter.ModTime(), blobBefore.ModTime())
			}
		})
	}
}

// writeChunk writes content as a chunk file in chunksDir and returns its name.
func writeChunk(t *testing.T, chunksDir, content string) string {
	t.Helper()
	if err := os.MkdirAll(chunksDir, 0755); err != nil {
		t.Fatalf("Failed to create chunks dir: %v", err)
	}
	h := sha256.Sum256([]byte(content))
	sha := hex.EncodeToString(h[:])
	if err := os.WriteFile(filepath.Join(chunksDir, sha), []byte(content), 0644); err != nil {
		t.Fatalf("Failed to write chunk: %v", err)
	}
	return sha
}

// exists reports whether path exists.
func exists(t *testing.T, path string) bool {
	t.Helper()
	_, err := os.Stat(path)
	if err != nil && !errors.Is(err, os.ErrNotExist) {
		t.Fatalf("Failed to stat %s: %v", path, err)
	}
	return err == nil
}

// TestChunkFileAndRestoreFiles_RoundTrip chunks files of several sizes,
// modes and mtimes the way casuploader does, writes the index, and restores
// them the way casdownloader does.
func TestChunkFileAndRestoreFiles_RoundTrip(t *testing.T) {
	files := []struct {
		path    string
		size    int
		mode    os.FileMode
		modTime time.Time
	}{
		{"empty", 0, 0644, time.Date(2019, 1, 1, 0, 0, 0, 0, time.UTC)},
		{"small.txt", 100, 0600, time.Date(2020, 2, 2, 2, 2, 2, 0, time.UTC)},
		{filepath.Join("bin", "tool"), 10000, 0755, time.Date(2021, 3, 3, 3, 3, 3, 0, time.UTC)},
		{filepath.Join("deep", "nested", "ro.dat"), 70000, 0444, time.Date(2022, 4, 4, 4, 4, 4, 0, time.UTC)},
	}
	for _, keepChunks := range []bool{false, true} {
		t.Run(fmt.Sprintf("keepChunks=%v", keepChunks), func(t *testing.T) {
			srcDir := t.TempDir()
			uploadDir := t.TempDir()
			dstDir := t.TempDir()
			chunksDir := filepath.Join(uploadDir, ChunksDirName)
			if err := os.Mkdir(chunksDir, 0755); err != nil {
				t.Fatalf("Failed to create chunks dir: %v", err)
			}

			want := make(map[string][]byte)
			var index []ChunksIndex
			for i, f := range files {
				data := make([]byte, f.size)
				rand.New(rand.NewSource(int64(i))).Read(data)
				want[f.path] = data
				src := filepath.Join(srcDir, fmt.Sprintf("src%d", i))
				if err := os.WriteFile(src, data, 0644); err != nil {
					t.Fatalf("Failed to write source: %v", err)
				}
				if err := os.Chmod(src, f.mode); err != nil {
					t.Fatalf("Failed to chmod source: %v", err)
				}
				if err := os.Chtimes(src, f.modTime, f.modTime); err != nil {
					t.Fatalf("Failed to set source times: %v", err)
				}

				entry, err := ChunkFile(src, f.path, chunksDir, 1)
				if err != nil {
					t.Fatalf("ChunkFile(%s) failed: %v", f.path, err)
				}
				if entry.Path != f.path || entry.Mode != f.mode || !entry.ModTime.Equal(f.modTime) {
					t.Errorf("ChunkFile(%s) = {Path: %q, Mode: %v, ModTime: %v}, want {%q, %v, %v}",
						f.path, entry.Path, entry.Mode, entry.ModTime, f.path, f.mode, f.modTime)
				}
				index = append(index, entry)
			}
			if err := CreateIndexFile(uploadDir, index); err != nil {
				t.Fatalf("CreateIndexFile failed: %v", err)
			}
			indexPath := filepath.Join(uploadDir, ChunksIndexFileName)
			if !exists(t, indexPath) || !exists(t, filepath.Join(chunksDir, ChunksIndexFileName)) {
				t.Fatal("CreateIndexFile did not write the index to both locations")
			}

			if err := RestoreFiles(uploadDir, dstDir, keepChunks); err != nil {
				t.Fatalf("RestoreFiles failed: %v", err)
			}

			for _, f := range files {
				restored := filepath.Join(dstDir, f.path)
				got, err := os.ReadFile(restored)
				if err != nil {
					t.Fatalf("Failed to read restored file: %v", err)
				}
				if !bytes.Equal(got, want[f.path]) {
					t.Errorf("Restored %s has %d bytes that differ from the %d byte source", f.path, len(got), len(want[f.path]))
				}
				info, err := os.Stat(restored)
				if err != nil {
					t.Fatalf("Failed to stat restored file: %v", err)
				}
				if info.Mode().Perm() != f.mode {
					t.Errorf("Restored %s mode = %#o, want %#o", f.path, info.Mode().Perm(), f.mode)
				}
				if !info.ModTime().Equal(f.modTime) {
					t.Errorf("Restored %s mtime = %v, want %v", f.path, info.ModTime(), f.modTime)
				}
			}
			if got := exists(t, chunksDir); got != keepChunks {
				t.Errorf("Chunks dir exists = %v after restore, want %v", got, keepChunks)
			}
			if got := exists(t, indexPath); got != keepChunks {
				t.Errorf("Index file exists = %v after restore, want %v", got, keepChunks)
			}
		})
	}
}

// TestRestoreFiles_LegacyIndexWithoutModTime restores index entries written
// before mod_time and mode were recorded. Nothing is applied to these files
// afterwards, so a single-chunk file is still linked to its chunk.
func TestRestoreFiles_LegacyIndexWithoutModTime(t *testing.T) {
	srcDir := t.TempDir()
	dstDir := t.TempDir()
	chunksDir := filepath.Join(srcDir, ChunksDirName)
	single := writeChunk(t, chunksDir, "single chunk")
	first := writeChunk(t, chunksDir, "first ")
	second := writeChunk(t, chunksDir, "second")
	index := []ChunksIndex{
		{Path: "single", Chunks: []chunker.ChunkInfo{{SHA256: single, Offset: 0}}},
		{Path: filepath.Join("sub", "multi"), Chunks: []chunker.ChunkInfo{{SHA256: first, Offset: 0}, {SHA256: second, Offset: 6}}},
	}
	if err := CreateIndexFile(srcDir, index); err != nil {
		t.Fatalf("CreateIndexFile failed: %v", err)
	}

	if err := RestoreFiles(srcDir, dstDir, true /* keepChunks */); err != nil {
		t.Fatalf("RestoreFiles failed: %v", err)
	}

	for path, want := range map[string]string{"single": "single chunk", filepath.Join("sub", "multi"): "first second"} {
		got, err := os.ReadFile(filepath.Join(dstDir, path))
		if err != nil {
			t.Fatalf("Failed to read restored file: %v", err)
		}
		if string(got) != want {
			t.Errorf("Restored %s = %q, want %q", path, got, want)
		}
	}
	restored, err := os.Stat(filepath.Join(dstDir, "single"))
	if err != nil {
		t.Fatalf("Failed to stat restored file: %v", err)
	}
	chunk, err := os.Stat(filepath.Join(chunksDir, single))
	if err != nil {
		t.Fatalf("Failed to stat chunk: %v", err)
	}
	if !os.SameFile(restored, chunk) {
		t.Error("Restored single-chunk file is not a link to its chunk, want a hard link")
	}
}

func TestRestoreFiles_NoIndex(t *testing.T) {
	if err := RestoreFiles(t.TempDir(), t.TempDir(), false); err != nil {
		t.Errorf("RestoreFiles without an index = %v, want nil", err)
	}
}

func TestRestoreFiles_UnreadableIndex(t *testing.T) {
	srcDir := t.TempDir()
	if err := os.Mkdir(filepath.Join(srcDir, ChunksIndexFileName), 0755); err != nil {
		t.Fatalf("Failed to create index dir: %v", err)
	}
	if err := RestoreFiles(srcDir, t.TempDir(), false); err == nil {
		t.Error("RestoreFiles with an unreadable index succeeded, want an error")
	}
}

func TestRestoreFiles_MalformedIndex(t *testing.T) {
	tests := []struct {
		name    string
		content string
	}{
		{"empty", ""},
		{"short", "{not json"},
		{"long", "[" + strings.Repeat("x", 3*snippetSize)},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			srcDir := t.TempDir()
			if err := os.WriteFile(filepath.Join(srcDir, ChunksIndexFileName), []byte(tc.content), 0644); err != nil {
				t.Fatalf("Failed to write index: %v", err)
			}
			if err := RestoreFiles(srcDir, t.TempDir(), false); err == nil {
				t.Error("RestoreFiles with a malformed index succeeded, want an error")
			}
		})
	}
}

// writeOneFileIndex writes an index for a single file made of one chunk, with
// a mode and mtime, in srcDir.
func writeOneFileIndex(t *testing.T, srcDir string) {
	t.Helper()
	sha := writeChunk(t, filepath.Join(srcDir, ChunksDirName), "content")
	index := []ChunksIndex{{
		Path:    "restored",
		ModTime: time.Date(2020, 1, 2, 3, 4, 5, 0, time.UTC),
		Mode:    0644,
		Chunks:  []chunker.ChunkInfo{{SHA256: sha, Offset: 0}},
	}}
	if err := CreateIndexFile(srcDir, index); err != nil {
		t.Fatalf("CreateIndexFile failed: %v", err)
	}
}

// TestRestoreFiles_MissingChunk checks that a failed restore returns an error
// and keeps the chunks and index, rather than deleting them.
func TestRestoreFiles_MissingChunk(t *testing.T) {
	for _, legacy := range []bool{false, true} {
		t.Run(fmt.Sprintf("legacy=%v", legacy), func(t *testing.T) {
			srcDir := t.TempDir()
			chunksDir := filepath.Join(srcDir, ChunksDirName)
			if err := os.Mkdir(chunksDir, 0755); err != nil {
				t.Fatalf("Failed to create chunks dir: %v", err)
			}
			entry := ChunksIndex{
				Path:   "restored",
				Mode:   0644,
				Chunks: []chunker.ChunkInfo{{SHA256: strings.Repeat("0", 64), Offset: 0}},
			}
			if !legacy {
				entry.ModTime = time.Date(2020, 1, 2, 3, 4, 5, 0, time.UTC)
			}
			if err := CreateIndexFile(srcDir, []ChunksIndex{entry}); err != nil {
				t.Fatalf("CreateIndexFile failed: %v", err)
			}

			if err := RestoreFiles(srcDir, t.TempDir(), false); err == nil {
				t.Error("RestoreFiles with a missing chunk succeeded, want an error")
			}
			if !exists(t, chunksDir) || !exists(t, filepath.Join(srcDir, ChunksIndexFileName)) {
				t.Error("RestoreFiles deleted the chunks or index after failing")
			}
		})
	}
}

func TestRestoreFiles_MetadataErrors(t *testing.T) {
	errInjected := errors.New("injected")
	tests := []struct {
		name   string
		inject func()
	}{
		{"chtimes", func() {
			chtimes = func(string, time.Time, time.Time) error { return errInjected }
		}},
		{"chmod", func() {
			chmod = func(string, os.FileMode) error { return errInjected }
		}},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			origChtimes, origChmod := chtimes, chmod
			t.Cleanup(func() { chtimes, chmod = origChtimes, origChmod })
			tc.inject()

			srcDir := t.TempDir()
			writeOneFileIndex(t, srcDir)
			if err := RestoreFiles(srcDir, t.TempDir(), false); !errors.Is(err, errInjected) {
				t.Errorf("RestoreFiles = %v, want an error wrapping %v", err, errInjected)
			}
		})
	}
}

func TestChunkFile_MissingSource(t *testing.T) {
	tempDir := t.TempDir()
	if _, err := ChunkFile(filepath.Join(tempDir, "missing"), "missing", tempDir, 1); err == nil {
		t.Error("ChunkFile of a missing file succeeded, want an error")
	}
}

func TestCreateIndexFile_MissingDir(t *testing.T) {
	if err := CreateIndexFile(filepath.Join(t.TempDir(), "missing"), nil); err == nil {
		t.Error("CreateIndexFile into a missing dir succeeded, want an error")
	}
}

// TestCreateIndexFile_NoChunksDir checks that failing to write the backup
// copy of the index in the chunks dir does not fail the upload.
func TestCreateIndexFile_NoChunksDir(t *testing.T) {
	dir := t.TempDir()
	if err := CreateIndexFile(dir, nil); err != nil {
		t.Fatalf("CreateIndexFile without a chunks dir = %v, want nil", err)
	}
	if !exists(t, filepath.Join(dir, ChunksIndexFileName)) {
		t.Error("CreateIndexFile did not write the index")
	}
}

func TestDeleteChunkFilesAndIndex(t *testing.T) {
	t.Run("present", func(t *testing.T) {
		dir := t.TempDir()
		writeOneFileIndex(t, dir)
		if err := DeleteChunkFilesAndIndex(dir); err != nil {
			t.Fatalf("DeleteChunkFilesAndIndex failed: %v", err)
		}
		if exists(t, filepath.Join(dir, ChunksDirName)) || exists(t, filepath.Join(dir, ChunksIndexFileName)) {
			t.Error("DeleteChunkFilesAndIndex left the chunks or index behind")
		}
	})

	t.Run("absent", func(t *testing.T) {
		if err := DeleteChunkFilesAndIndex(t.TempDir()); err != nil {
			t.Errorf("DeleteChunkFilesAndIndex with nothing to delete = %v, want nil", err)
		}
	})

	t.Run("index is a non-empty dir", func(t *testing.T) {
		dir := t.TempDir()
		indexPath := filepath.Join(dir, ChunksIndexFileName)
		if err := os.MkdirAll(filepath.Join(indexPath, "child"), 0755); err != nil {
			t.Fatalf("Failed to create index dir: %v", err)
		}
		if err := DeleteChunkFilesAndIndex(dir); err == nil {
			t.Error("DeleteChunkFilesAndIndex succeeded, want an error")
		}
	})

	t.Run("chunks dir is read-only", func(t *testing.T) {
		if os.Geteuid() == 0 {
			t.Skip("Directory permissions are not enforced for root")
		}
		dir := t.TempDir()
		chunksDir := filepath.Join(dir, ChunksDirName)
		writeChunk(t, chunksDir, "content")
		if err := os.Chmod(chunksDir, 0555); err != nil {
			t.Fatalf("Failed to make chunks dir read-only: %v", err)
		}
		t.Cleanup(func() { os.Chmod(chunksDir, 0755) })
		if err := DeleteChunkFilesAndIndex(dir); err == nil {
			t.Error("DeleteChunkFilesAndIndex succeeded, want an error")
		}
	})
}
