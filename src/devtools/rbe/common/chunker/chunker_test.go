package chunker

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"math/rand"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/google/uuid"
)

func TestChunkFile(t *testing.T) {
	const fileSizeKB = 2 * 1024

	tests := []struct {
		name             string
		duplicateContent bool
	}{
		{"regluar", false},
		{"duplicateContent", true},
	}

	for _, test := range tests {
		targetDir := filepath.Join(os.TempDir(), "chunker_tmp", uuid.New().String())
		os.MkdirAll(targetDir, 0755)
		defer func() {
			if err := os.RemoveAll(targetDir); err != nil {
				t.Logf("failed to remove tmp dir: %v", err)
			}
		}()

		chunksDir := filepath.Join(targetDir, "chunks")
		os.MkdirAll(chunksDir, 0755)

		seed := time.Now().UnixNano()
		t.Logf("Seed: %d\n", seed)

		sourcePath := filepath.Join(targetDir, test.name+"_source")
		if err := createRandomFile(t, sourcePath, fileSizeKB*1024, seed, test.duplicateContent); err != nil {
			t.Fatalf("Failed to create random file: %v", err)
		}

		avgChunkSizeKB := fileSizeKB / 10
		chunks, err := ChunkFile(sourcePath, chunksDir, avgChunkSizeKB)
		if err != nil {
			t.Fatalf("Failed to chunk file: %v", err)
		}

		restoredPath := filepath.Join(targetDir, test.name+"_restored")
		t.Logf("Restoring file %s from dir: %s\n", restoredPath, chunksDir)
		if err := RestoreFile(restoredPath, chunksDir, chunks, false); err != nil {
			t.Fatalf("Failed to restore file: %v", err)
		}

		if matched, err := compareFilesByHash(sourcePath, restoredPath); err != nil {
			t.Fatalf("Failed to compare files by hash: %v", err)
		} else if !matched {
			t.Fatalf("The hashes for the source and restored file do not match")
		}
	}
}

func TestRestoreFile_ManyChunks(t *testing.T) {
	tempDir := t.TempDir()
	chunksDir := filepath.Join(tempDir, "chunks")
	if err := os.MkdirAll(chunksDir, 0755); err != nil {
		t.Fatalf("Failed to create chunks dir: %v", err)
	}

	numChunks := 50
	var chunks []ChunkInfo
	var expectedContent []byte

	for i := 0; i < numChunks; i++ {
		chunkData := []byte(filepath.Join("chunk_data_content_block_", string(rune('A'+(i%26))), string(rune('0'+(i%10)))))
		h := sha256.Sum256(chunkData)
		sha := hex.EncodeToString(h[:])

		chunkPath := filepath.Join(chunksDir, sha)
		if err := os.WriteFile(chunkPath, chunkData, 0644); err != nil {
			t.Fatalf("Failed to write chunk %d: %v", i, err)
		}

		chunks = append(chunks, ChunkInfo{
			SHA256: sha,
			Offset: int64(len(expectedContent)),
		})
		expectedContent = append(expectedContent, chunkData...)
	}

	restoredPath := filepath.Join(tempDir, "restored_large_file.bin")
	if err := RestoreFile(restoredPath, chunksDir, chunks, false); err != nil {
		t.Fatalf("RestoreFile failed: %v", err)
	}

	gotContent, err := os.ReadFile(restoredPath)
	if err != nil {
		t.Fatalf("Failed to read restored file: %v", err)
	}

	if !bytes.Equal(gotContent, expectedContent) {
		t.Fatalf("Restored content mismatch: got %d bytes (want %d bytes), content differs", len(gotContent), len(expectedContent))
	}
}

// writeChunks writes each of contents as a chunk file in chunksDir and returns
// the chunk list that restores their concatenation.
func writeChunks(t *testing.T, chunksDir string, contents ...string) []ChunkInfo {
	t.Helper()
	if err := os.MkdirAll(chunksDir, 0755); err != nil {
		t.Fatalf("Failed to create chunks dir: %v", err)
	}
	var chunks []ChunkInfo
	var offset int64
	for _, c := range contents {
		h := sha256.Sum256([]byte(c))
		sha := hex.EncodeToString(h[:])
		if err := os.WriteFile(filepath.Join(chunksDir, sha), []byte(c), 0644); err != nil {
			t.Fatalf("Failed to write chunk: %v", err)
		}
		chunks = append(chunks, ChunkInfo{SHA256: sha, Offset: offset})
		offset += int64(len(c))
	}
	return chunks
}

// TestRestoreFile_ReplacesExistingWithoutWritingThroughIt covers a restore
// over a file that shares its inode with another path, as a file
// casdownloader materialized from its cache does. The restore must replace
// the path and leave the other link's content alone.
func TestRestoreFile_ReplacesExistingWithoutWritingThroughIt(t *testing.T) {
	tests := []struct {
		name    string
		restore func(string, string, []ChunkInfo, bool) error
		chunks  []string
	}{
		{"RestoreFile/single", RestoreFile, []string{"new content"}},
		{"RestoreFile/multi", RestoreFile, []string{"new ", "content"}},
		{"RestoreFileCopy/single", RestoreFileCopy, []string{"new content"}},
		{"RestoreFileCopy/multi", RestoreFileCopy, []string{"new ", "content"}},
		{"RestorePrivateFileCopy/single", RestorePrivateFileCopy, []string{"new content"}},
		{"RestorePrivateFileCopy/multi", RestorePrivateFileCopy, []string{"new ", "content"}},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			tempDir := t.TempDir()
			chunks := writeChunks(t, filepath.Join(tempDir, "chunks"), tc.chunks...)

			const oldContent = "cached blob content"
			blob := filepath.Join(tempDir, "cache_blob")
			if err := os.WriteFile(blob, []byte(oldContent), 0644); err != nil {
				t.Fatalf("Failed to write blob: %v", err)
			}
			restoredPath := filepath.Join(tempDir, "out", "restored")
			if err := os.MkdirAll(filepath.Dir(restoredPath), 0755); err != nil {
				t.Fatalf("Failed to create output dir: %v", err)
			}
			if err := os.Link(blob, restoredPath); err != nil {
				t.Fatalf("Failed to link blob: %v", err)
			}

			if err := tc.restore(restoredPath, filepath.Join(tempDir, "chunks"), chunks, false); err != nil {
				t.Fatalf("Restore failed: %v", err)
			}

			got, err := os.ReadFile(restoredPath)
			if err != nil {
				t.Fatalf("Failed to read restored file: %v", err)
			}
			if string(got) != "new content" {
				t.Errorf("Restored content = %q, want %q", got, "new content")
			}
			gotBlob, err := os.ReadFile(blob)
			if err != nil {
				t.Fatalf("Failed to read blob: %v", err)
			}
			if string(gotBlob) != oldContent {
				t.Errorf("Blob content = %q after restore, want it unchanged as %q", gotBlob, oldContent)
			}
			entries, err := os.ReadDir(filepath.Dir(restoredPath))
			if err != nil {
				t.Fatalf("Failed to read output dir: %v", err)
			}
			if len(entries) != 1 {
				t.Errorf("Output dir has %d entries, want only the restored file (temporary file left behind?)", len(entries))
			}
		})
	}
}

func TestRestoreFileCopy_SingleChunkDoesNotShareInode(t *testing.T) {
	tempDir := t.TempDir()
	chunksDir := filepath.Join(tempDir, "chunks")
	chunks := writeChunks(t, chunksDir, "only chunk")

	restoredPath := filepath.Join(tempDir, "restored")
	if err := RestoreFileCopy(restoredPath, chunksDir, chunks, false); err != nil {
		t.Fatalf("RestoreFileCopy failed: %v", err)
	}

	restoredInfo, err := os.Stat(restoredPath)
	if err != nil {
		t.Fatalf("Failed to stat restored file: %v", err)
	}
	chunkInfo, err := os.Stat(filepath.Join(chunksDir, chunks[0].SHA256))
	if err != nil {
		t.Fatalf("Failed to stat chunk: %v", err)
	}
	if os.SameFile(restoredInfo, chunkInfo) {
		t.Error("RestoreFileCopy shares an inode with the chunk, want an independent copy")
	}
	if got := restoredInfo.Mode().Perm(); got != restoredFileMode {
		t.Errorf("Restored file mode = %#o, want %#o", got, restoredFileMode)
	}
}

func TestRestoreFile_MissingChunkLeavesExistingFile(t *testing.T) {
	tests := []struct {
		name    string
		restore func(string, string, []ChunkInfo, bool) error
		chunks  []string
	}{
		{"RestoreFile/single", RestoreFile, []string{"only"}},
		{"RestoreFile/multi", RestoreFile, []string{"first ", "second"}},
		{"RestoreFileCopy/single", RestoreFileCopy, []string{"only"}},
		{"RestoreFileCopy/multi", RestoreFileCopy, []string{"first ", "second"}},
		{"RestorePrivateFileCopy/single", RestorePrivateFileCopy, []string{"only"}},
		{"RestorePrivateFileCopy/multi", RestorePrivateFileCopy, []string{"first ", "second"}},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			tempDir := t.TempDir()
			chunksDir := filepath.Join(tempDir, "chunks")
			chunks := writeChunks(t, chunksDir, tc.chunks...)
			last := chunks[len(chunks)-1]
			if err := os.Remove(filepath.Join(chunksDir, last.SHA256)); err != nil {
				t.Fatalf("Failed to remove chunk: %v", err)
			}

			restoredPath := filepath.Join(tempDir, "out", "restored")
			if err := os.MkdirAll(filepath.Dir(restoredPath), 0755); err != nil {
				t.Fatalf("Failed to create output dir: %v", err)
			}
			const oldContent = "previous content"
			if err := os.WriteFile(restoredPath, []byte(oldContent), 0644); err != nil {
				t.Fatalf("Failed to write existing file: %v", err)
			}

			if err := tc.restore(restoredPath, chunksDir, chunks, false); err == nil {
				t.Fatal("Restore succeeded with a missing chunk, want an error")
			}

			got, err := os.ReadFile(restoredPath)
			if err != nil {
				t.Fatalf("Failed to read existing file: %v", err)
			}
			if string(got) != oldContent {
				t.Errorf("Existing file content = %q after failed restore, want it unchanged as %q", got, oldContent)
			}
			entries, err := os.ReadDir(filepath.Dir(restoredPath))
			if err != nil {
				t.Fatalf("Failed to read output dir: %v", err)
			}
			if len(entries) != 1 {
				t.Errorf("Output dir has %d entries after failed restore, want 1 (temporary file left behind?)", len(entries))
			}
		})
	}
}

// TestRestoreFile_RelinkSameChunk restores a single-chunk file over a path
// that is already a link to that chunk, which rename(2) treats as a no-op.
func TestRestoreFile_RelinkSameChunk(t *testing.T) {
	tempDir := t.TempDir()
	chunksDir := filepath.Join(tempDir, "chunks")
	chunks := writeChunks(t, chunksDir, "only chunk")

	chunkInfo, err := os.Stat(filepath.Join(chunksDir, chunks[0].SHA256))
	if err != nil {
		t.Fatalf("Failed to stat chunk: %v", err)
	}

	restoredPath := filepath.Join(tempDir, "out", "restored")
	for i := 0; i < 2; i++ {
		if err := RestoreFile(restoredPath, chunksDir, chunks, false); err != nil {
			t.Fatalf("RestoreFile #%d failed: %v", i+1, err)
		}
		restoredInfo, err := os.Stat(restoredPath)
		if err != nil {
			t.Fatalf("RestoreFile #%d: failed to stat restored file: %v", i+1, err)
		}
		if !os.SameFile(restoredInfo, chunkInfo) {
			t.Errorf("RestoreFile #%d: restored file is not a link to the chunk, want a hard link", i+1)
		}
	}

	entries, err := os.ReadDir(filepath.Dir(restoredPath))
	if err != nil {
		t.Fatalf("Failed to read output dir: %v", err)
	}
	if len(entries) != 1 {
		t.Errorf("Output dir has %d entries, want only the restored file (temporary link left behind?)", len(entries))
	}
}

func createRandomFile(t *testing.T, path string, size int, seed int64, duplicateContent bool) error {
	t.Logf("Creating a random file %s of size %d using seed %d\n", path, size, seed)

	// Generate seeded random data
	rng := rand.New(rand.NewSource(seed))

	// To generate a duplicateContent file, the file size is doubled and
	// the content of the second half is a duplicate of the first half.
	// This can be useful for testing the files that result in duplicated chunks.
	fileSize := size
	if duplicateContent {
		fileSize += size
	}

	randomData := make([]byte, fileSize)
	for i := range randomData {
		if i < size {
			randomData[i] = byte(rng.Intn(256))
		} else {
			randomData[i] = randomData[i-size]
		}
	}

	// Write the generated data to the file.
	if err := os.WriteFile(path, randomData, 0644); err != nil {
		t.Logf("Error writing to file %s, %s\n", path, err)
		return err
	}

	return nil
}

func compareFilesByHash(path1, path2 string) (bool, error) {
	f1, err := os.Open(path1)
	if err != nil {
		return false, err
	}
	defer f1.Close()

	f2, err := os.Open(path2)
	if err != nil {
		return false, err
	}
	defer f2.Close()

	h1 := sha256.New()
	if _, err := io.Copy(h1, f1); err != nil {
		return false, err
	}

	h2 := sha256.New()
	if _, err := io.Copy(h2, f2); err != nil {
		return false, err
	}

	return hex.EncodeToString(h1.Sum(nil)) == hex.EncodeToString(h2.Sum(nil)), nil
}

// randomBytes returns size bytes generated from seed.
func randomBytes(size int, seed int64) []byte {
	data := make([]byte, size)
	rand.New(rand.NewSource(seed)).Read(data)
	return data
}

// restoreFuncs lists the restore entry points, each with chunk verification
// off and on, for tests that cover each.
var restoreFuncs = []struct {
	name    string
	restore func(string, string, []ChunkInfo) error
}{
	{"RestoreFile", func(p, d string, c []ChunkInfo) error { return RestoreFile(p, d, c, false) }},
	{"RestoreFile_verified", func(p, d string, c []ChunkInfo) error { return RestoreFile(p, d, c, true) }},
	{"RestoreFileCopy", func(p, d string, c []ChunkInfo) error { return RestoreFileCopy(p, d, c, false) }},
	{"RestoreFileCopy_verified", func(p, d string, c []ChunkInfo) error { return RestoreFileCopy(p, d, c, true) }},
	{"RestorePrivateFileCopy", func(p, d string, c []ChunkInfo) error { return RestorePrivateFileCopy(p, d, c, false) }},
	{"RestorePrivateFileCopy_verified", func(p, d string, c []ChunkInfo) error { return RestorePrivateFileCopy(p, d, c, true) }},
}

// TestChunkFile_RoundTrip chunks files around the 1 KiB average, 256 B
// minimum and 4 KiB maximum chunk sizes, checks the chunk list and chunk
// files, and restores each file with both restore functions.
func TestChunkFile_RoundTrip(t *testing.T) {
	for _, size := range []int{0, 1, 255, 256, 1024, 4096, 4097, 64*1024 + 13} {
		t.Run(fmt.Sprintf("size=%d", size), func(t *testing.T) {
			tempDir := t.TempDir()
			chunksDir := filepath.Join(tempDir, "chunks")
			if err := os.Mkdir(chunksDir, 0755); err != nil {
				t.Fatalf("Failed to create chunks dir: %v", err)
			}
			data := randomBytes(size, int64(size))
			src := filepath.Join(tempDir, "src")
			if err := os.WriteFile(src, data, 0644); err != nil {
				t.Fatalf("Failed to write source: %v", err)
			}

			chunks, err := ChunkFile(src, chunksDir, 1)
			if err != nil {
				t.Fatalf("ChunkFile failed: %v", err)
			}
			if size == 0 && len(chunks) != 0 {
				t.Errorf("ChunkFile returned %d chunks for an empty file, want 0", len(chunks))
			}
			if size > 0 && len(chunks) == 0 {
				t.Fatal("ChunkFile returned no chunks for a non-empty file")
			}
			var end int64
			for i, c := range chunks {
				if c.Offset != end {
					t.Fatalf("Chunk %d offset = %d, want %d (end of the previous chunk)", i, c.Offset, end)
				}
				b, err := os.ReadFile(filepath.Join(chunksDir, c.SHA256))
				if err != nil {
					t.Fatalf("Failed to read chunk %d: %v", i, err)
				}
				if len(b) == 0 {
					t.Fatalf("Chunk %d is empty", i)
				}
				if sum := sha256.Sum256(b); hex.EncodeToString(sum[:]) != c.SHA256 {
					t.Errorf("Chunk %d content hashes to %x, want it to match its name %s", i, sum, c.SHA256)
				}
				end = c.Offset + int64(len(b))
				if end > int64(size) || !bytes.Equal(b, data[c.Offset:end]) {
					t.Fatalf("Chunk %d does not match the source at offset %d", i, c.Offset)
				}
			}
			if end != int64(size) {
				t.Errorf("Chunks cover %d bytes, want %d", end, size)
			}

			for _, r := range restoreFuncs {
				restored := filepath.Join(tempDir, "out", r.name)
				if err := r.restore(restored, chunksDir, chunks); err != nil {
					t.Fatalf("%s failed: %v", r.name, err)
				}
				got, err := os.ReadFile(restored)
				if err != nil {
					t.Fatalf("Failed to read restored file: %v", err)
				}
				if !bytes.Equal(got, data) {
					t.Errorf("%s restored %d bytes that differ from the %d byte source", r.name, len(got), len(data))
				}
			}
			if size == 0 {
				info, err := os.Stat(filepath.Join(tempDir, "out", "RestoreFile"))
				if err != nil {
					t.Fatalf("Failed to stat restored file: %v", err)
				}
				if got := info.Mode().Perm(); got != restoredFileMode {
					t.Errorf("Restored empty file mode = %#o, want %#o", got, restoredFileMode)
				}
			}
		})
	}
}

// TestChunkFile_DeduplicatesChunks chunks a file made of a repeated block,
// whose chunk boundaries resynchronize in each copy, and checks that each
// distinct chunk is written once.
func TestChunkFile_DeduplicatesChunks(t *testing.T) {
	tempDir := t.TempDir()
	chunksDir := filepath.Join(tempDir, "chunks")
	if err := os.Mkdir(chunksDir, 0755); err != nil {
		t.Fatalf("Failed to create chunks dir: %v", err)
	}
	data := bytes.Repeat(randomBytes(16*1024, 1), 4)
	src := filepath.Join(tempDir, "src")
	if err := os.WriteFile(src, data, 0644); err != nil {
		t.Fatalf("Failed to write source: %v", err)
	}

	chunks, err := ChunkFile(src, chunksDir, 1)
	if err != nil {
		t.Fatalf("ChunkFile failed: %v", err)
	}
	distinct := make(map[string]bool)
	for _, c := range chunks {
		distinct[c.SHA256] = true
	}
	if len(distinct) >= len(chunks) {
		t.Errorf("ChunkFile returned %d distinct chunks out of %d, want repeats", len(distinct), len(chunks))
	}
	entries, err := os.ReadDir(chunksDir)
	if err != nil {
		t.Fatalf("Failed to read chunks dir: %v", err)
	}
	if len(entries) != len(distinct) {
		t.Errorf("Chunks dir has %d files, want one per distinct chunk (%d)", len(entries), len(distinct))
	}

	restored := filepath.Join(tempDir, "restored")
	if err := RestoreFile(restored, chunksDir, chunks, false); err != nil {
		t.Fatalf("RestoreFile failed: %v", err)
	}
	if matched, err := compareFilesByHash(src, restored); err != nil {
		t.Fatalf("Failed to compare files by hash: %v", err)
	} else if !matched {
		t.Error("The hashes for the source and restored file do not match")
	}
}

func TestChunkFile_Errors(t *testing.T) {
	tempDir := t.TempDir()
	chunksDir := filepath.Join(tempDir, "chunks")
	if err := os.Mkdir(chunksDir, 0755); err != nil {
		t.Fatalf("Failed to create chunks dir: %v", err)
	}
	src := filepath.Join(tempDir, "src")
	if err := os.WriteFile(src, []byte("some content"), 0644); err != nil {
		t.Fatalf("Failed to write source: %v", err)
	}

	tests := []struct {
		name      string
		path      string
		chunksDir string
		avgKB     int
	}{
		{"missing source", filepath.Join(tempDir, "missing"), chunksDir, 1},
		{"source is a directory", tempDir, chunksDir, 1},
		{"zero average chunk size", src, chunksDir, 0},
		{"missing chunks dir", src, filepath.Join(tempDir, "missing_chunks"), 1},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			if _, err := ChunkFile(tc.path, tc.chunksDir, tc.avgKB); err == nil {
				t.Error("ChunkFile succeeded, want an error")
			}
		})
	}
}

// chunkLayouts lists a single-chunk file, which RestoreFile links, and a
// multi-chunk file, which is always copied.
var chunkLayouts = []struct {
	name   string
	chunks []string
}{
	{"single", []string{"only chunk"}},
	{"multi", []string{"first ", "second"}},
}

func TestRestoreFile_ParentIsAFile(t *testing.T) {
	for _, r := range restoreFuncs {
		for _, l := range chunkLayouts {
			t.Run(r.name+"/"+l.name, func(t *testing.T) {
				tempDir := t.TempDir()
				chunksDir := filepath.Join(tempDir, "chunks")
				chunks := writeChunks(t, chunksDir, l.chunks...)
				parent := filepath.Join(tempDir, "file")
				if err := os.WriteFile(parent, []byte("not a dir"), 0644); err != nil {
					t.Fatalf("Failed to write file: %v", err)
				}

				if err := r.restore(filepath.Join(parent, "restored"), chunksDir, chunks); err == nil {
					t.Error("Restore under a regular file succeeded, want an error")
				}
			})
		}
	}
}

// TestRestoreFile_TargetIsADirectory restores over a non-empty directory,
// which neither link nor rename can replace. The directory must be left
// intact, with no temporary file beside it.
func TestRestoreFile_TargetIsADirectory(t *testing.T) {
	for _, r := range restoreFuncs {
		for _, l := range chunkLayouts {
			t.Run(r.name+"/"+l.name, func(t *testing.T) {
				tempDir := t.TempDir()
				chunksDir := filepath.Join(tempDir, "chunks")
				chunks := writeChunks(t, chunksDir, l.chunks...)
				outDir := filepath.Join(tempDir, "out")
				target := filepath.Join(outDir, "restored")
				keep := filepath.Join(target, "keep")
				if err := os.MkdirAll(target, 0755); err != nil {
					t.Fatalf("Failed to create target dir: %v", err)
				}
				if err := os.WriteFile(keep, []byte("keep"), 0644); err != nil {
					t.Fatalf("Failed to write file in target dir: %v", err)
				}

				if err := r.restore(target, chunksDir, chunks); err == nil {
					t.Error("Restore over a non-empty directory succeeded, want an error")
				}

				if _, err := os.Stat(keep); err != nil {
					t.Errorf("File in target dir is gone after failed restore: %v", err)
				}
				entries, err := os.ReadDir(outDir)
				if err != nil {
					t.Fatalf("Failed to read output dir: %v", err)
				}
				if len(entries) != 1 {
					t.Errorf("Output dir has %d entries after failed restore, want 1 (temporary file left behind?)", len(entries))
				}
			})
		}
	}
}

// TestRestoreFile_UnreadableChunk restores from a chunk that is a directory,
// so it opens but cannot be read. The existing file must be left unchanged.
func TestRestoreFile_UnreadableChunk(t *testing.T) {
	for _, r := range restoreFuncs {
		for _, l := range chunkLayouts {
			t.Run(r.name+"/"+l.name, func(t *testing.T) {
				tempDir := t.TempDir()
				chunksDir := filepath.Join(tempDir, "chunks")
				chunks := writeChunks(t, chunksDir, l.chunks...)
				last := filepath.Join(chunksDir, chunks[len(chunks)-1].SHA256)
				if err := os.Remove(last); err != nil {
					t.Fatalf("Failed to remove chunk: %v", err)
				}
				if err := os.Mkdir(last, 0755); err != nil {
					t.Fatalf("Failed to replace chunk with a dir: %v", err)
				}

				outDir := filepath.Join(tempDir, "out")
				restored := filepath.Join(outDir, "restored")
				if err := os.MkdirAll(outDir, 0755); err != nil {
					t.Fatalf("Failed to create output dir: %v", err)
				}
				const oldContent = "previous content"
				if err := os.WriteFile(restored, []byte(oldContent), 0644); err != nil {
					t.Fatalf("Failed to write existing file: %v", err)
				}

				if err := r.restore(restored, chunksDir, chunks); err == nil {
					t.Fatal("Restore from an unreadable chunk succeeded, want an error")
				}

				got, err := os.ReadFile(restored)
				if err != nil {
					t.Fatalf("Failed to read existing file: %v", err)
				}
				if string(got) != oldContent {
					t.Errorf("Existing file content = %q after failed restore, want it unchanged as %q", got, oldContent)
				}
				entries, err := os.ReadDir(outDir)
				if err != nil {
					t.Fatalf("Failed to read output dir: %v", err)
				}
				if len(entries) != 1 {
					t.Errorf("Output dir has %d entries after failed restore, want 1 (temporary file left behind?)", len(entries))
				}
			})
		}
	}
}

// TestRestoreFile_ReadOnlyDirLeavesExistingFile restores over a file in a
// directory where nothing can be created, so neither a temporary link nor a
// temporary copy can be made. The existing file must be left unchanged.
func TestRestoreFile_ReadOnlyDirLeavesExistingFile(t *testing.T) {
	if os.Geteuid() == 0 {
		t.Skip("Directory permissions are not enforced for root")
	}
	for _, r := range restoreFuncs {
		for _, l := range chunkLayouts {
			t.Run(r.name+"/"+l.name, func(t *testing.T) {
				tempDir := t.TempDir()
				chunksDir := filepath.Join(tempDir, "chunks")
				chunks := writeChunks(t, chunksDir, l.chunks...)
				outDir := filepath.Join(tempDir, "out")
				restored := filepath.Join(outDir, "restored")
				if err := os.MkdirAll(outDir, 0755); err != nil {
					t.Fatalf("Failed to create output dir: %v", err)
				}
				const oldContent = "previous content"
				if err := os.WriteFile(restored, []byte(oldContent), 0644); err != nil {
					t.Fatalf("Failed to write existing file: %v", err)
				}
				if err := os.Chmod(outDir, 0555); err != nil {
					t.Fatalf("Failed to make output dir read-only: %v", err)
				}
				t.Cleanup(func() { os.Chmod(outDir, 0755) })

				if err := r.restore(restored, chunksDir, chunks); err == nil {
					t.Fatal("Restore into a read-only dir succeeded, want an error")
				}

				got, err := os.ReadFile(restored)
				if err != nil {
					t.Fatalf("Failed to read existing file: %v", err)
				}
				if string(got) != oldContent {
					t.Errorf("Existing file content = %q after failed restore, want it unchanged as %q", got, oldContent)
				}
			})
		}
	}
}

// TestRestoreFile_MissingChunkRemovesNewFile restores into a path that does
// not exist yet (which RestorePrivateFileCopy writes directly with
// O_CREATE|O_EXCL rather than through a temporary sibling) when a chunk is
// missing, and verifies that nothing is left in the output directory.
func TestRestoreFile_MissingChunkRemovesNewFile(t *testing.T) {
	for _, r := range restoreFuncs {
		for _, l := range chunkLayouts {
			t.Run(r.name+"/"+l.name, func(t *testing.T) {
				tempDir := t.TempDir()
				chunksDir := filepath.Join(tempDir, "chunks")
				chunks := writeChunks(t, chunksDir, l.chunks...)
				last := chunks[len(chunks)-1]
				if err := os.Remove(filepath.Join(chunksDir, last.SHA256)); err != nil {
					t.Fatalf("Failed to remove chunk: %v", err)
				}

				outDir := filepath.Join(tempDir, "out")
				restoredPath := filepath.Join(outDir, "restored")
				if err := r.restore(restoredPath, chunksDir, chunks); err == nil {
					t.Fatal("Restore succeeded with a missing chunk, want an error")
				}

				entries, err := os.ReadDir(outDir)
				if err != nil {
					t.Fatalf("Failed to read output dir: %v", err)
				}
				if len(entries) != 0 {
					t.Errorf("Output dir has %d entries after failed restore of a new file, want 0", len(entries))
				}
			})
		}
	}
}

// corruptions are the ways a chunk file can stop matching its name: a single
// flipped bit, as from a RAM or page cache error, and a length change, as from
// a short write or an append through a hard link.
var corruptions = []struct {
	name    string
	corrupt func(content []byte) []byte
}{
	{"bit_flip", func(c []byte) []byte {
		b := bytes.Clone(c)
		b[len(b)/2] ^= 0x08
		return b
	}},
	{"truncated", func(c []byte) []byte { return c[:len(c)-1] }},
	{"extra_byte", func(c []byte) []byte { return append(bytes.Clone(c), 'x') }},
}

// corruptChunkFile rewrites the chunk file at path in place with corrupt
// applied to its content, keeping its inode, and returns the new content.
func corruptChunkFile(t *testing.T, path string, corrupt func([]byte) []byte) []byte {
	t.Helper()
	content, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("Failed to read chunk %s: %v", path, err)
	}
	bad := corrupt(content)
	if err := os.WriteFile(path, bad, 0644); err != nil {
		t.Fatalf("Failed to corrupt chunk %s: %v", path, err)
	}
	return bad
}

func sha256Hex(b []byte) string {
	sum := sha256.Sum256(b)
	return hex.EncodeToString(sum[:])
}

// copyingRestores lists the restores that copy every chunk, and so, with
// verify, verify every chunk: all three for a multi-chunk file, and the two
// copy variants for a single chunk, which RestoreFile links instead.
var copyingRestores = []struct {
	name    string
	restore func(string, string, []ChunkInfo, bool) error
	chunks  []string
}{
	{"RestoreFile_multi", RestoreFile, []string{"first 1111 ", "second 1111"}},
	{"RestoreFileCopy_single", RestoreFileCopy, []string{"only chunk 1111"}},
	{"RestoreFileCopy_multi", RestoreFileCopy, []string{"first 1111 ", "second 1111"}},
	{"RestorePrivateFileCopy_single", RestorePrivateFileCopy, []string{"only chunk 1111"}},
	{"RestorePrivateFileCopy_multi", RestorePrivateFileCopy, []string{"first 1111 ", "second 1111"}},
}

// TestRestore_CorruptChunkFails corrupts the last chunk of a file in each way
// a chunk can be corrupt and restores the file, with verify, over an existing
// one. The restore must fail with a *ChunkMismatchError naming the chunk, its
// expected hash and the hash of what was actually read, and must leave the
// existing file alone with nothing written beside it.
func TestRestore_CorruptChunkFails(t *testing.T) {
	for _, r := range copyingRestores {
		for _, c := range corruptions {
			t.Run(r.name+"_"+c.name, func(t *testing.T) {
				tempDir := t.TempDir()
				chunksDir := filepath.Join(tempDir, "chunks")
				chunks := writeChunks(t, chunksDir, r.chunks...)
				last := chunks[len(chunks)-1]
				lastPath := filepath.Join(chunksDir, last.SHA256)
				bad := corruptChunkFile(t, lastPath, c.corrupt)

				outDir := filepath.Join(tempDir, "out")
				restored := filepath.Join(outDir, "restored")
				const oldContent = "previous content"
				if err := os.MkdirAll(outDir, 0755); err != nil {
					t.Fatalf("Failed to create output dir: %v", err)
				}
				if err := os.WriteFile(restored, []byte(oldContent), 0644); err != nil {
					t.Fatalf("Failed to write existing file: %v", err)
				}

				err := r.restore(restored, chunksDir, chunks, true)
				var mismatch *ChunkMismatchError
				if !errors.As(err, &mismatch) {
					t.Fatalf("%s with a corrupt chunk = %v, want a *ChunkMismatchError", r.name, err)
				}
				want := ChunkMismatchError{Path: lastPath, Want: last.SHA256, Got: sha256Hex(bad), Size: int64(len(bad))}
				if *mismatch != want {
					t.Errorf("%s with a corrupt chunk returned %+v, want %+v", r.name, *mismatch, want)
				}

				got, err := os.ReadFile(restored)
				if err != nil {
					t.Fatalf("Failed to read existing file: %v", err)
				}
				if string(got) != oldContent {
					t.Errorf("Existing file content = %q after a restore from a corrupt chunk, want it unchanged as %q", got, oldContent)
				}
				entries, err := os.ReadDir(outDir)
				if err != nil {
					t.Fatalf("Failed to read output dir: %v", err)
				}
				if len(entries) != 1 {
					t.Errorf("Output dir has %d entries after a restore from a corrupt chunk, want 1 (partial file left behind?)", len(entries))
				}
			})
		}
	}
}

// TestRestore_CorruptChunkRemovesNewFile restores, with verify, a file that
// does not exist yet from a corrupt chunk. RestorePrivateFileCopy writes such
// a file in place rather than through a temporary sibling, so this is the case
// where the corrupt bytes reach the target path itself: it must be removed.
func TestRestore_CorruptChunkRemovesNewFile(t *testing.T) {
	for _, r := range copyingRestores {
		t.Run(r.name, func(t *testing.T) {
			tempDir := t.TempDir()
			chunksDir := filepath.Join(tempDir, "chunks")
			chunks := writeChunks(t, chunksDir, r.chunks...)
			corruptChunkFile(t, filepath.Join(chunksDir, chunks[0].SHA256), corruptions[0].corrupt)

			outDir := filepath.Join(tempDir, "out")
			err := r.restore(filepath.Join(outDir, "restored"), chunksDir, chunks, true)
			var mismatch *ChunkMismatchError
			if !errors.As(err, &mismatch) {
				t.Fatalf("%s with a corrupt chunk = %v, want a *ChunkMismatchError", r.name, err)
			}
			entries, err := os.ReadDir(outDir)
			if err != nil {
				t.Fatalf("Failed to read output dir: %v", err)
			}
			if len(entries) != 0 {
				t.Errorf("Output dir has %d entries after a restore of a new file from a corrupt chunk, want 0", len(entries))
			}
		})
	}
}

// TestRestore_UnverifiedCopiesCorruptChunk corrupts the last chunk of a file
// in each way a chunk can be corrupt and restores the file without verify.
// The restore copies the chunks as they are, without reading them to hash
// them, so it must succeed and restore exactly the bytes in the chunk files,
// the corrupt ones included.
func TestRestore_UnverifiedCopiesCorruptChunk(t *testing.T) {
	for _, r := range copyingRestores {
		for _, c := range corruptions {
			t.Run(r.name+"_"+c.name, func(t *testing.T) {
				tempDir := t.TempDir()
				chunksDir := filepath.Join(tempDir, "chunks")
				chunks := writeChunks(t, chunksDir, r.chunks...)
				bad := corruptChunkFile(t, filepath.Join(chunksDir, chunks[len(chunks)-1].SHA256), c.corrupt)
				want := strings.Join(r.chunks[:len(r.chunks)-1], "") + string(bad)

				restored := filepath.Join(tempDir, "out", "restored")
				if err := r.restore(restored, chunksDir, chunks, false); err != nil {
					t.Fatalf("%s without verify from a corrupt chunk = %v, want it to copy the chunk unchecked", r.name, err)
				}
				got, err := os.ReadFile(restored)
				if err != nil {
					t.Fatalf("Failed to read restored file: %v", err)
				}
				if string(got) != want {
					t.Errorf("%s without verify restored %q, want the chunk files' bytes %q", r.name, got, want)
				}
			})
		}
	}
}

// TestRestore_VerifiesChunksLargerThanTheCopyBuffer restores, with verify, a
// file whose chunks span several copy buffers, so the hash has to accumulate
// across reads. An intact file restores byte for byte; a bit flipped in the
// last buffer of the second chunk is caught.
func TestRestore_VerifiesChunksLargerThanTheCopyBuffer(t *testing.T) {
	first := randomBytes(copyBufferSize+17, 1)
	second := randomBytes(2*copyBufferSize+5, 2)
	restores := []struct {
		name    string
		restore func(string, string, []ChunkInfo, bool) error
	}{
		{"RestoreFile", RestoreFile},
		{"RestoreFileCopy", RestoreFileCopy},
		{"RestorePrivateFileCopy", RestorePrivateFileCopy},
	}
	for _, r := range restores {
		t.Run(r.name, func(t *testing.T) {
			tempDir := t.TempDir()
			chunksDir := filepath.Join(tempDir, "chunks")
			chunks := writeChunks(t, chunksDir, string(first), string(second))

			restored := filepath.Join(tempDir, "intact")
			if err := r.restore(restored, chunksDir, chunks, true); err != nil {
				t.Fatalf("%s from intact chunks failed: %v", r.name, err)
			}
			got, err := os.ReadFile(restored)
			if err != nil {
				t.Fatalf("Failed to read restored file: %v", err)
			}
			if want := append(bytes.Clone(first), second...); !bytes.Equal(got, want) {
				t.Errorf("%s restored %d bytes that differ from the %d expected", r.name, len(got), len(want))
			}

			corruptChunkFile(t, filepath.Join(chunksDir, chunks[1].SHA256), func(c []byte) []byte {
				b := bytes.Clone(c)
				b[len(b)-2] ^= 0x01
				return b
			})
			err = r.restore(filepath.Join(tempDir, "corrupt"), chunksDir, chunks, true)
			var mismatch *ChunkMismatchError
			if !errors.As(err, &mismatch) || mismatch.Want != chunks[1].SHA256 {
				t.Errorf("%s with a bit flipped at the end of a multi-buffer chunk = %v, want a *ChunkMismatchError for chunk %s", r.name, err, chunks[1].SHA256)
			}
		})
	}
}

// TestRestoreFile_SingleChunkLinkIsNotRead pins the one restore that does not
// verify its chunk, even with verify: RestoreFile hard links a single-chunk
// file in O(1) without reading it, and hashing would read the whole chunk.
// Its callers that need the bytes verified use a copying restore.
func TestRestoreFile_SingleChunkLinkIsNotRead(t *testing.T) {
	for _, verify := range []bool{false, true} {
		t.Run(fmt.Sprintf("verify=%v", verify), func(t *testing.T) {
			tempDir := t.TempDir()
			chunksDir := filepath.Join(tempDir, "chunks")
			chunks := writeChunks(t, chunksDir, "only chunk 1111")
			chunkPath := filepath.Join(chunksDir, chunks[0].SHA256)
			corruptChunkFile(t, chunkPath, corruptions[0].corrupt)

			restored := filepath.Join(tempDir, "restored")
			if err := RestoreFile(restored, chunksDir, chunks, verify); err != nil {
				t.Fatalf("RestoreFile(verify=%v) of a single linked chunk = %v, want it to link without reading the chunk", verify, err)
			}
			restoredInfo, err := os.Stat(restored)
			if err != nil {
				t.Fatalf("Failed to stat restored file: %v", err)
			}
			chunkInfo, err := os.Stat(chunkPath)
			if err != nil {
				t.Fatalf("Failed to stat chunk: %v", err)
			}
			if !os.SameFile(restoredInfo, chunkInfo) {
				t.Errorf("RestoreFile(verify=%v) of a single chunk did not hard link it", verify)
			}
		})
	}
}
