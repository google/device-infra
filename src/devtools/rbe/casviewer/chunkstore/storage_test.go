package chunkstore

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/google/device-infra/src/devtools/rbe/casuploader/chunker"
)

func TestChunkStore_ReadFileToDest(t *testing.T) {
	chunkAContent := []byte("AAAAAAAAAA") // len 10
	chunkBContent := []byte("BBBBBBBBBB") // len 10
	chunkCContent := []byte("CCCCCCCCCC") // len 10

	commonChunkDir := filepath.Join(t.TempDir(), "shared_chunks_for_read_test")
	_ = os.Mkdir(commonChunkDir, 0755)
	shaA := createChunkFile(t, commonChunkDir, chunkAContent)
	shaB := createChunkFile(t, commonChunkDir, chunkBContent)
	shaC := createChunkFile(t, commonChunkDir, chunkCContent)

	filesData := []TestFileEntry{
		{
			Path:    "testfile.txt", // A(0-9), B(10-19), C(20-29) -> Size 30
			ModTime: time.Now().Format(time.RFC3339Nano),
			Mode:    0644,
			Chunks: []TestChunkInfo{
				{SHA256: shaA, Offset: 0},
				{SHA256: shaB, Offset: 10},
				{SHA256: shaC, Offset: 20},
			},
		},
	}

	tempIndexDir := t.TempDir()
	indexPath := filepath.Join(tempIndexDir, "_chunks_index.json")
	indexJSON, _ := json.MarshalIndent(filesData, "", "  ")
	_ = os.WriteFile(indexPath, indexJSON, 0644)

	store, err := NewChunkStore(commonChunkDir, indexPath)
	if err != nil {
		t.Fatalf("NewChunkStore failed: %v", err)
	}

	tests := []struct {
		name        string
		path        string
		offset      int64
		readLen     int
		expected    []byte
		expectedN   int
		expectedErr error
	}{
		{"read full file", "testfile.txt", 0, 30, []byte("AAAAAAAAAABBBBBBBBBBCCCCCCCCCC"), 30, nil},
		{"read past EOF", "testfile.txt", 25, 10, []byte("CCCCC"), 5, nil}, // Reads last 5 bytes
		{"read at EOF", "testfile.txt", 30, 10, []byte{}, 0, io.EOF},
		{"read negative offset", "testfile.txt", -5, 5, nil, 0, fmt.Errorf("negative offset not allowed")},
		{"read part of first chunk", "testfile.txt", 0, 5, []byte("AAAAA"), 5, nil},
		{"read across first and second chunk", "testfile.txt", 5, 10, []byte("AAAAABBBBB"), 10, nil},
		{"read only second chunk", "testfile.txt", 10, 10, []byte("BBBBBBBBBB"), 10, nil},
		{"read from middle of second chunk", "testfile.txt", 12, 5, []byte("BBBBB"), 5, nil},
		{"read zero length", "testfile.txt", 5, 0, []byte{}, 0, nil},
		{"read non-existent file", "nosuchfile.txt", 0, 5, nil, 0, os.ErrNotExist},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			dest := make([]byte, tt.readLen) // FUSE usually provides a zeroed buffer
			n, err := store.ReadFileToDest(tt.path, dest, tt.offset)

			if tt.expectedErr != nil {
				if err == nil {
					t.Errorf("Expected error '%v', got nil", tt.expectedErr)
				} else if !errors.Is(err, tt.expectedErr) && err.Error() != tt.expectedErr.Error() { // Check for wrapped or exact match
					t.Errorf("Expected error '%v', got '%v'", tt.expectedErr, err)
				}
			} else if err != nil {
				t.Errorf("Unexpected error: %v", err)
			}

			if n != tt.expectedN {
				t.Errorf("Expected to read %d bytes, got %d", tt.expectedN, n)
			}

			if tt.expected != nil {
				actualData := dest[:n]
				if !bytes.Equal(actualData, tt.expected) {
					t.Errorf("Expected data '%s', got '%s'", string(tt.expected), string(actualData))
				}
			}
		})
	}
}

func TestChunkStore_findChunkIndex(t *testing.T) {
	chunks := []ChunkInfo{
		{ChunkInfo: chunker.ChunkInfo{Offset: 0}, Length: 10},  // index 0, covers 0-9
		{ChunkInfo: chunker.ChunkInfo{Offset: 10}, Length: 10}, // index 1, covers 10-19
		{ChunkInfo: chunker.ChunkInfo{Offset: 20}, Length: 10}, // index 2, covers 20-29
		{ChunkInfo: chunker.ChunkInfo{Offset: 35}, Length: 5},  // index 3, covers 35-39 (gap between 29 and 35)
	}
	cs := &ChunkStore{} // findChunkIndex is a method on ChunkStore

	tests := []struct {
		name   string
		offset int64
		want   int
	}{
		{"offset before first chunk", -5, -1},
		{"offset at start of first chunk", 0, 0},
		{"offset in first chunk", 5, 0},
		{"offset at end of first chunk (exclusive start of next)", 10, 1}, // Finds chunk starting at 10
		{"offset in second chunk", 15, 1},
		{"offset in gap before chunk 3", 30, 2}, // Finds last chunk whose offset is <= 30 (chunk 2)
		{"offset at start of chunk 3", 35, 3},
		{"offset in chunk 3", 37, 3},
		{"offset after last chunk", 100, 3}, // Finds last chunk whose offset is <= 100
		{"offset exactly at start of a chunk not first", 20, 2},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := cs.findChunkIndex(chunks, tt.offset); got != tt.want {
				t.Errorf("findChunkIndex() = %v, want %v", got, tt.want)
			}
		})
	}

	// Test with empty chunks
	if got := cs.findChunkIndex([]ChunkInfo{}, 50); got != -1 {
		t.Errorf("findChunkIndex() with empty chunks = %v, want -1", got)
	}
}
