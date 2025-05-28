package mountpoint

import (
	"os"
	"path/filepath"
	"testing"
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
