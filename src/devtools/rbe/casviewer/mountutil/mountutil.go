// Package mountutil provides utility functions for mounting the CAS viewer.
package mountutil

import (
	"errors"
	"fmt"
	"io"
	"os"

	"path/filepath"

	"github.com/google/device-infra/src/devtools/rbe/casuploader/chunkerutil"
)

// ValidateMountPoint validates the mount point directory exists and is empty.
func ValidateMountPoint(mountPoint string) error {
	if mountPoint == "" {
		return errors.New("mount point must be specified with --mount")
	}

	mpFile, err := os.Open(mountPoint)
	if err != nil {
		return fmt.Errorf("mount point %s cannot be opened: %v", mountPoint, err)
	}
	defer mpFile.Close()

	_, err = mpFile.Readdirnames(1) // Try to read one entry
	if err == nil {
		return fmt.Errorf("mount point %s is not empty. FUSE requires an empty directory", mountPoint)
	}

	if err != io.EOF {
		return fmt.Errorf("error checking if mount point %s is empty: %v", mountPoint, err)
	}

	return nil
}

// DefaultIndexFile returns the path to the index file, or an error.
// It searches in a prioritized list of directories.
func DefaultIndexFile(chunkDir string) (string, error) {
	// A list of directories to search, in order of priority.
	searchDirs := []string{
		chunkDir,               // Primary location
		filepath.Dir(chunkDir), // Fallback for backward compatibility
	}

	for _, dir := range searchDirs {
		path := filepath.Join(dir, chunkerutil.ChunksIndexFileName)
		fileInfo, err := os.Stat(path)

		if err == nil {
			if !fileInfo.IsDir() {
				return path, nil // Found it, and it's a file.
			}
			// If it's a directory, continue to the next search location.
		} else if !os.IsNotExist(err) {
			return "", err
		}
	}

	return "", nil // No default index file found in any location.
}
