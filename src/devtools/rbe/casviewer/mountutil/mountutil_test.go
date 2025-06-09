package mountutil

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/google/device-infra/src/devtools/rbe/casuploader/chunkerutil"
)

func TestValidateMountPoint(t *testing.T) {
	tempDir := t.TempDir()

	// Test Case 1: Mount point does not exist
	nonExistentPath := filepath.Join(tempDir, "nonexistent")
	err := ValidateMountPoint(nonExistentPath)
	if err == nil {
		t.Errorf("Expected error for non-existent mount point, got nil")
	}

	// Test Case 2: Mount point is a file, not a directory
	filePath := filepath.Join(tempDir, "file.txt")
	if _, err := os.Create(filePath); err != nil {
		t.Fatalf("Failed to create test file: %v", err)
	}
	err = ValidateMountPoint(filePath)
	if err == nil {
		t.Errorf("Expected error for mount point being a file, got nil")
	}

	// Test Case 3: Mount point is an empty directory
	emptyDirPath := filepath.Join(tempDir, "empty_dir")
	if err := os.Mkdir(emptyDirPath, 0755); err != nil {
		t.Fatalf("Failed to create empty dir: %v", err)
	}
	err = ValidateMountPoint(emptyDirPath)
	if err != nil {
		t.Errorf("Expected no error for empty directory, got: %v", err)
	}

	// Test Case 4: Mount point is not an empty directory
	nonEmptyDirPath := filepath.Join(tempDir, "non_empty_dir")
	if err := os.Mkdir(nonEmptyDirPath, 0755); err != nil {
		t.Fatalf("Failed to create non-empty dir: %v", err)
	}
	if _, err := os.Create(filepath.Join(nonEmptyDirPath, "dummy.txt")); err != nil {
		t.Fatalf("Failed to create file in non-empty dir: %v", err)
	}
	err = ValidateMountPoint(nonEmptyDirPath)
	if err == nil {
		t.Errorf("Expected error for non-empty directory, got nil")
	} else if err.Error() != "mount point "+nonEmptyDirPath+" is not empty. FUSE requires an empty directory" {
		t.Errorf("Unexpected error message for non-empty dir: %v", err)
	}

	// Test Case 5: Mount point not specified (empty string)
	err = ValidateMountPoint("")
	if err == nil {
		t.Errorf("Expected error for empty mount point string, got nil")
	} else if err.Error() != "mount point must be specified with --mount" {
		t.Errorf("Unexpected error message for empty mount point string: %v", err)
	}
}

func TestDefaultIndexFile(t *testing.T) {
	// Helper function to create a dummy file.
	createDummyFile := func(t *testing.T, path string) {
		t.Helper()
		if err := os.WriteFile(path, []byte("test"), 0666); err != nil {
			t.Fatalf("Failed to create dummy file %s: %v", path, err)
		}
	}

	t.Run("SuccessWhenIndexIsInPrimaryDir", func(t *testing.T) {
		// Create a temp directory with the index file inside it.
		// e.g., /tmp/test123/chunks/index.yaml
		tmpDir := t.TempDir()
		chunkDir := filepath.Join(tmpDir, "chunks")
		if err := os.Mkdir(chunkDir, 0755); err != nil {
			t.Fatal(err)
		}
		expectedPath := filepath.Join(chunkDir, chunkerutil.ChunksIndexFileName)
		createDummyFile(t, expectedPath)

		path, err := DefaultIndexFile(chunkDir)

		if err != nil {
			t.Errorf("Expected no error, but got: %v", err)
		}
		if path != expectedPath {
			t.Errorf("Expected path '%s', but got '%s'", expectedPath, path)
		}
	})

	t.Run("SuccessWhenIndexIsInParentDir_BackwardCompatibility", func(t *testing.T) {
		// Create a temp directory with the index file in the parent of chunkDir.
		// e.g., /tmp/test456/index.yaml
		//       /tmp/test456/chunks/
		tmpDir := t.TempDir()
		chunkDir := filepath.Join(tmpDir, "chunks")
		if err := os.Mkdir(chunkDir, 0755); err != nil {
			t.Fatal(err)
		}
		expectedPath := filepath.Join(tmpDir, chunkerutil.ChunksIndexFileName)
		createDummyFile(t, expectedPath)

		path, err := DefaultIndexFile(chunkDir)

		if err != nil {
			t.Errorf("Expected no error, but got: %v", err)
		}
		if path != expectedPath {
			t.Errorf("Expected path '%s' from parent dir, but got '%s'", expectedPath, path)
		}
	})

	t.Run("NotFoundWhenIndexDoesNotExist", func(t *testing.T) {
		// Create a directory structure with no index file.
		tmpDir := t.TempDir()
		chunkDir := filepath.Join(tmpDir, "chunks")
		if err := os.Mkdir(chunkDir, 0755); err != nil {
			t.Fatal(err)
		}

		path, err := DefaultIndexFile(chunkDir)

		if err != nil {
			t.Errorf("Expected no error for a simple 'not found' case, but got: %v", err)
		}
		if path != "" {
			t.Errorf("Expected empty path for a 'not found' case, but got '%s'", path)
		}
	})

	t.Run("IgnoresDirectoryWithSameNameAsIndex", func(t *testing.T) {
		// An entry with the index name exists, but it's a directory.
		// This should be ignored, and the function should find the real file in the parent.
		tmpDir := t.TempDir()
		chunkDir := filepath.Join(tmpDir, "chunks")

		// 1. Create the decoy directory in the primary location.
		decoyDirPath := filepath.Join(chunkDir, chunkerutil.ChunksIndexFileName)
		if err := os.MkdirAll(decoyDirPath, 0755); err != nil {
			t.Fatal(err)
		}

		// 2. Create the real index file in the parent (fallback) location.
		expectedPath := filepath.Join(tmpDir, chunkerutil.ChunksIndexFileName)
		createDummyFile(t, expectedPath)

		path, err := DefaultIndexFile(chunkDir)

		if err != nil {
			t.Errorf("Expected no error, but got: %v", err)
		}
		if path != expectedPath {
			t.Errorf("Expected path from parent dir '%s', but got '%s'", expectedPath, path)
		}
	})

	t.Run("NotFoundIfOnlyADirectoryExists", func(t *testing.T) {
		// A decoy directory exists in the primary location, but no file exists anywhere.
		tmpDir := t.TempDir()
		chunkDir := filepath.Join(tmpDir, "chunks")
		decoyDirPath := filepath.Join(chunkDir, chunkerutil.ChunksIndexFileName)
		if err := os.MkdirAll(decoyDirPath, 0755); err != nil {
			t.Fatal(err)
		}

		path, err := DefaultIndexFile(chunkDir)

		if err != nil {
			t.Errorf("Expected no error, but got: %v", err)
		}
		if path != "" {
			t.Errorf("Expected empty path when only a directory exists, but got '%s'", path)
		}
	})
}
