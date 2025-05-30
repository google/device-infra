package chunkerutil

import (
	"os"
	"path/filepath"
	"testing"
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
