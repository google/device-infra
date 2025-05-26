package chunkstore

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"os"
	"path/filepath"
	"testing"
	// "time"
)

// Helper to create a chunk file and return its SHA256 hash.
func createChunkFile(t *testing.T, chunkDir string, content []byte) string {
	t.Helper()
	hasher := sha256.New()
	hasher.Write(content)
	hashBytes := hasher.Sum(nil)
	sha256sum := hex.EncodeToString(hashBytes)

	chunkPath := filepath.Join(chunkDir, sha256sum)
	err := os.WriteFile(chunkPath, content, 0644)
	if err != nil {
		t.Fatalf("Failed to write chunk file %s: %v", chunkPath, err)
	}
	return sha256sum
}

// TestIndexData represents the structure of the index.json for testing.
// Using a separate struct for test data generation to match JSON field names if they differ from FileInfo.
type TestFileEntry struct {
	Path    string          `json:"path"`
	ModTime string          `json:"mod_time"` // ISO 8601 format
	Mode    int             `json:"mode"`     // os.FileMode as int (e.g., 0644 -> 420)
	Chunks  []TestChunkInfo `json:"chunks"`
}

type TestChunkInfo struct {
	SHA256 string `json:"sha256"`
	Offset int64  `json:"offset"`
}

// setupTestChunkStore creates a temporary directory with an index.json and chunk files,
// then initializes and returns a ChunkStore.
func setupTestChunkStore(t *testing.T, filesData []TestFileEntry, allChunkContents map[string][]byte) (*ChunkStore, string) {
	t.Helper()

	tempDir := t.TempDir()
	chunkDir := filepath.Join(tempDir, "chunks")
	indexPath := filepath.Join(tempDir, "_chunks_index.json")

	err := os.Mkdir(chunkDir, 0755)
	if err != nil {
		t.Fatalf("Failed to create chunk dir: %v", err)
	}

	// Create actual chunk files from allChunkContents
	// This ensures that createChunkFile isn't called again if a SHA256 is already generated
	// and allows pre-defining SHA256s if needed for specific test cases.
	for sha, content := range allChunkContents {
		chunkPath := filepath.Join(chunkDir, sha)
		err := os.WriteFile(chunkPath, content, 0644)
		if err != nil {
			t.Fatalf("Failed to write predefined chunk file %s: %v", chunkPath, err)
		}
	}

	indexJSON, err := json.MarshalIndent(filesData, "", "  ")
	if err != nil {
		t.Fatalf("Failed to marshal index data: %v", err)
	}
	err = os.WriteFile(indexPath, indexJSON, 0644)
	if err != nil {
		t.Fatalf("Failed to write index file: %v", err)
	}

	store, err := NewChunkStore(chunkDir, indexPath)
	if err != nil {
		t.Fatalf("NewChunkStore failed: %v", err)
	}
	return store, tempDir
}
