// Package mountpoint provides functions to validate the mount point directory exists and is empty.
package mountpoint

import (
	"errors"
	"fmt"
	"io"
	"os"
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
