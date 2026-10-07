package chunker

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"io"
	"math/rand"
	"os"
	"path/filepath"
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
		if err := RestoreFile(restoredPath, chunksDir, chunks); err != nil {
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
	if err := RestoreFile(restoredPath, chunksDir, chunks); err != nil {
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
		restore func(string, string, []ChunkInfo) error
		chunks  []string
	}{
		{"RestoreFile/single", RestoreFile, []string{"new content"}},
		{"RestoreFile/multi", RestoreFile, []string{"new ", "content"}},
		{"RestoreFileCopy/single", RestoreFileCopy, []string{"new content"}},
		{"RestoreFileCopy/multi", RestoreFileCopy, []string{"new ", "content"}},
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

			if err := tc.restore(restoredPath, filepath.Join(tempDir, "chunks"), chunks); err != nil {
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
	if err := RestoreFileCopy(restoredPath, chunksDir, chunks); err != nil {
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
		restore func(string, string, []ChunkInfo) error
		chunks  []string
	}{
		{"RestoreFile/single", RestoreFile, []string{"only"}},
		{"RestoreFile/multi", RestoreFile, []string{"first ", "second"}},
		{"RestoreFileCopy/single", RestoreFileCopy, []string{"only"}},
		{"RestoreFileCopy/multi", RestoreFileCopy, []string{"first ", "second"}},
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

			if err := tc.restore(restoredPath, chunksDir, chunks); err == nil {
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

	restoredPath := filepath.Join(tempDir, "out", "restored")
	for i := 0; i < 2; i++ {
		if err := RestoreFile(restoredPath, chunksDir, chunks); err != nil {
			t.Fatalf("RestoreFile #%d failed: %v", i+1, err)
		}
	}

	restoredInfo, err := os.Stat(restoredPath)
	if err != nil {
		t.Fatalf("Failed to stat restored file: %v", err)
	}
	chunkInfo, err := os.Stat(filepath.Join(chunksDir, chunks[0].SHA256))
	if err != nil {
		t.Fatalf("Failed to stat chunk: %v", err)
	}
	if !os.SameFile(restoredInfo, chunkInfo) {
		t.Error("Restored file is not a link to the chunk, want a hard link")
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

// restoreFuncs lists both restore entry points, for tests that cover each.
var restoreFuncs = []struct {
	name    string
	restore func(string, string, []ChunkInfo) error
}{
	{"RestoreFile", RestoreFile},
	{"RestoreFileCopy", RestoreFileCopy},
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
	if err := RestoreFile(restored, chunksDir, chunks); err != nil {
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
