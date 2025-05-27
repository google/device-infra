package chunkstore

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"testing"
	"time"

	"google3/third_party/golang/gofuse/fuse/fuse" // For fuseMode constants
)

func TestNewChunkStore_Success(t *testing.T) {
	chunk1Content := []byte("Hello ")
	chunk2Content := []byte("World!")
	chunk3Content := []byte("Extra")

	// Manually calculate SHA256 to use them in TestFileEntry
	// This also ensures our helper createChunkFile matches reality.
	// Or, we can just predefine them if the test doesn't focus on hash generation itself.
	hasher1 := sha256.New()
	hasher1.Write(chunk1Content)
	sha1 := hex.EncodeToString(hasher1.Sum(nil))

	hasher2 := sha256.New()
	hasher2.Write(chunk2Content)
	sha2 := hex.EncodeToString(hasher2.Sum(nil))

	hasher3 := sha256.New()
	hasher3.Write(chunk3Content)
	sha3 := hex.EncodeToString(hasher3.Sum(nil))

	allChunkContents := map[string][]byte{
		sha1: chunk1Content,
		sha2: chunk2Content,
		sha3: chunk3Content,
	}

	filesData := []TestFileEntry{
		{
			Path:    "file1.txt",
			ModTime: time.Now().Format(time.RFC3339Nano),
			Mode:    0644, // os.FileMode representation
			Chunks: []TestChunkInfo{
				{SHA256: sha1, Offset: 0},
				{SHA256: sha2, Offset: int64(len(chunk1Content))},
			},
		},
		{
			Path:    "empty.txt",
			ModTime: time.Now().Format(time.RFC3339Nano),
			Mode:    0600,
			Chunks:  []TestChunkInfo{},
		},
		{
			Path:    "single_chunk.txt",
			ModTime: time.Now().Format(time.RFC3339Nano),
			Mode:    0755,
			Chunks:  []TestChunkInfo{{SHA256: sha3, Offset: 0}},
		},
	}

	store, _ := setupTestChunkStore(t, filesData, allChunkContents)

	// --- Assertions ---
	if len(store.GetFiles()) != 3 {
		t.Errorf("Expected 3 files, got %d", len(store.GetFiles()))
	}

	// Test file1.txt
	f1, err := store.GetFile("file1.txt")
	if err != nil {
		t.Fatalf("Failed to get file1.txt: %v", err)
	}
	expectedF1Size := int64(len(chunk1Content) + len(chunk2Content))
	if f1.Size != expectedF1Size {
		t.Errorf("file1.txt: Expected size %d, got %d", expectedF1Size, f1.Size)
	}
	if len(f1.Chunks) != 2 {
		t.Errorf("file1.txt: Expected 2 chunks, got %d", len(f1.Chunks))
	}
	if f1.Chunks[0].Length != len(chunk1Content) {
		t.Errorf("file1.txt: Chunk 0 expected length %d, got %d", len(chunk1Content), f1.Chunks[0].Length)
	}
	if f1.Chunks[1].Length != len(chunk2Content) {
		t.Errorf("file1.txt: Chunk 1 expected length %d, got %d", len(chunk2Content), f1.Chunks[1].Length)
	}
	// Check mode conversion (assuming 0644 os.FileMode -> S_IFREG | 0644 fuseMode)
	if f1.Mode != (fuse.S_IFREG | 0644) {
		t.Errorf("file1.txt: Expected mode %o, got %o", (fuse.S_IFREG | 0644), f1.Mode)
	}

	// Test empty.txt
	fEmpty, err := store.GetFile("empty.txt")
	if err != nil {
		t.Fatalf("Failed to get empty.txt: %v", err)
	}
	if fEmpty.Size != 0 {
		t.Errorf("empty.txt: Expected size 0, got %d", fEmpty.Size)
	}
	if len(fEmpty.Chunks) != 0 {
		t.Errorf("empty.txt: Expected 0 chunks, got %d", len(fEmpty.Chunks))
	}
	if fEmpty.Mode != (fuse.S_IFREG | 0600) {
		t.Errorf("empty.txt: Expected mode %o, got %o", (fuse.S_IFREG | 0600), fEmpty.Mode)
	}

	// Test single_chunk.txt
	fSingle, err := store.GetFile("single_chunk.txt")
	if err != nil {
		t.Fatalf("Failed to get single_chunk.txt: %v", err)
	}
	if fSingle.Size != int64(len(chunk3Content)) {
		t.Errorf("single_chunk.txt: Expected size %d, got %d", len(chunk3Content), fSingle.Size)
	}
	if len(fSingle.Chunks) != 1 {
		t.Errorf("single_chunk.txt: Expected 1 chunk, got %d", len(fSingle.Chunks))
	}
	if fSingle.Chunks[0].Length != len(chunk3Content) {
		t.Errorf("single_chunk.txt: Chunk 0 expected length %d, got %d", len(chunk3Content), fSingle.Chunks[0].Length)
	}
	if fSingle.Mode != (fuse.S_IFREG | 0755) {
		t.Errorf("single_chunk.txt: Expected mode %o, got %o", (fuse.S_IFREG | 0755), fSingle.Mode)
	}
}

func TestNewChunkStore_MissingIndex(t *testing.T) {
	tempDir := t.TempDir()
	chunkDir := filepath.Join(tempDir, "chunks")
	indexPath := filepath.Join(tempDir, "non_existent_index.json")
	_ = os.Mkdir(chunkDir, 0755)

	_, err := NewChunkStore(chunkDir, indexPath)
	if err == nil {
		t.Fatal("Expected error for missing index file, got nil")
	}
	if !os.IsNotExist(err) { // Check underlying error if wrapped
		t.Errorf("Expected os.ErrNotExist, got %v", err)
	}
}

func TestNewChunkStore_MalformedJSON(t *testing.T) {
	tempDir := t.TempDir()
	chunkDir := filepath.Join(tempDir, "chunks")
	indexPath := filepath.Join(tempDir, "_chunks_index.json")
	_ = os.Mkdir(chunkDir, 0755)

	err := os.WriteFile(indexPath, []byte("[{\"path\": \"file.txt\", invalid_json_structure"), 0644)
	if err != nil {
		t.Fatalf("Failed to write malformed index file: %v", err)
	}

	_, err = NewChunkStore(chunkDir, indexPath)
	if err == nil {
		t.Fatal("Expected error for malformed JSON, got nil")
	}
	// Check for json.SyntaxError or similar
	var syntaxError *json.SyntaxError
	if !errors.As(err, &syntaxError) {
		t.Errorf("Expected JSON syntax error, got %T: %v", err, err)
	}
}

func TestNewChunkStore_MissingChunkFile(t *testing.T) {
	// sha1 represents a chunk that will NOT be created on disk
	sha1Missing := "0000000000000000000000000000000000000000000000000000000000000000"
	filesData := []TestFileEntry{
		{
			Path:    "file_with_missing_chunk.txt",
			ModTime: time.Now().Format(time.RFC3339Nano),
			Mode:    0644,
			Chunks:  []TestChunkInfo{{SHA256: sha1Missing, Offset: 0}},
		},
	}
	// allChunkContents := map[string][]byte{} // Empty, so sha1Missing won't exist

	tempDir := t.TempDir()
	chunkDir := filepath.Join(tempDir, "chunks")
	indexPath := filepath.Join(tempDir, "_chunks_index.json")
	_ = os.Mkdir(chunkDir, 0755)

	indexJSON, err := json.MarshalIndent(filesData, "", "  ")
	if err != nil {
		t.Fatalf("Failed to marshal index data: %v", err)
	}
	err = os.WriteFile(indexPath, indexJSON, 0644)
	if err != nil {
		t.Fatalf("Failed to write index file: %v", err)
	}

	_, err = NewChunkStore(chunkDir, indexPath)
	if err == nil {
		t.Fatal("Expected error for missing chunk file, got nil")
	}
	// The error from getFileSize is wrapped, so check for os.ErrNotExist within the chain
	if !errors.Is(err, os.ErrNotExist) {
		t.Logf("Note: Expected error chain to contain os.ErrNotExist for missing chunk, got: %v", err)
	}
}

func TestChunkStore_GetFile(t *testing.T) {
	filesData := []TestFileEntry{
		{Path: "exists.txt", ModTime: time.Now().Format(time.RFC3339Nano), Mode: 0644, Chunks: []TestChunkInfo{}},
	}
	store, _ := setupTestChunkStore(t, filesData, map[string][]byte{})

	// Test existing file
	fInfo, err := store.GetFile("exists.txt")
	if err != nil {
		t.Errorf("GetFile(exists.txt): unexpected error: %v", err)
	}
	if fInfo == nil {
		t.Fatal("GetFile(exists.txt): expected FileInfo, got nil")
	}
	if fInfo.Path != "exists.txt" {
		t.Errorf("GetFile(exists.txt): expected path 'exists.txt', got '%s'", fInfo.Path)
	}

	// Test non-existent file
	_, err = store.GetFile("nonexistent.txt")
	if err == nil {
		t.Error("GetFile(nonexistent.txt): expected an error, got nil")
	}
	if !errors.Is(err, os.ErrNotExist) {
		t.Errorf("GetFile(nonexistent.txt): expected os.ErrNotExist, got %v", err)
	}
}

func TestOsFileModeToFuseMode(t *testing.T) {
	tests := []struct {
		name     string
		osMode   os.FileMode
		wantFuse fuseMode
	}{
		{"regular file 644", 0644, fuse.S_IFREG | 0644},
		{"regular file 755", 0755, fuse.S_IFREG | 0755},
		{"directory 755", os.ModeDir | 0755, fuse.S_IFDIR | 0755},
		{"symlink 777", os.ModeSymlink | 0777, fuse.S_IFLNK | 0777},
		{"device file (should be regular)", os.ModeDevice | 0600, fuse.S_IFREG | 0600}, // Assuming non-dir, non-symlink defaults to regular
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if gotFuse := osFileModeToFuseMode(tt.osMode); gotFuse != tt.wantFuse {
				t.Errorf("osFileModeToFuseMode() = %o, want %o", gotFuse, tt.wantFuse)
			}
		})
	}
}
