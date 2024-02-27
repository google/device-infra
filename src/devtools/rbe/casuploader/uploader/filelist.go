package uploader

import (
	"bufio"
	"fmt"
	"os"
	"path/filepath"

	log "github.com/golang/glog"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/digest"
)

// FilelistUploader is the uploader to uploader the a zip
type FilelistUploader struct {
	CommonConfig
	filelistPath string
	repoRoot     string
}

// NewFilelistUploader creates
func NewFilelistUploader(config *CommonConfig, filelistPath, repoRoot string) Uploader {
	return &FilelistUploader{
		CommonConfig: *config,
		filelistPath: filelistPath,
		repoRoot:     repoRoot,
	}
}

// DoUpload uploads the unarchived zip file to CAS, and returns the digest of the root directory.
func (zu *FilelistUploader) DoUpload() (digest.Digest, error) {
	log.Infof("Reading %s\n", zu.filelistPath)

	targetDir := createTmpDir()
	defer func() {
		if err := os.RemoveAll(targetDir); err != nil {
			log.Errorf("Failed to remove tmp dir: %v\n", err)
		}
	}()

	f, err := os.Open(zu.filelistPath)
	if err != nil {
		return digest.Digest{}, fmt.Errorf("failed to open filelist file %s: %v", zu.filelistPath, err)
	}
	defer f.Close()

	scanner := bufio.NewScanner(f)

	for scanner.Scan() {
		line := scanner.Text()

		if line == "" {
			continue
		}
		original := filepath.Join(zu.repoRoot, line)
		target := filepath.Join(targetDir, line)

		info, err := os.Stat(original)
		if err != nil {
			log.Infof("Failed to stat %s: %v", original, err)
			continue
		}

		if info.IsDir() {
			log.Infof("Skipping directory %s", original)
			continue
		}

		// Ensure the directory structure for the target exists
		targetDirPath := filepath.Dir(target)                    // Get the directory part of the target path
		if err := os.MkdirAll(targetDirPath, 0755); err != nil { // 0755 is a common permission setting
			return digest.Digest{}, fmt.Errorf("failed to create directory structure for %s: %v", target, err)
		}

		err = os.Symlink(original, target)
		// sourceFile, err := os.Open(original)
		// if err != nil {
		// 	return digest.Digest{}, fmt.Errorf("failed to open %s: %v", original, err)
		// }
		// defer sourceFile.Close()

		// destinationFile, err := os.Create(target)
		// if err != nil {
		// 	return digest.Digest{}, fmt.Errorf("failed to create %s: %v", target, err)
		// }
		// defer destinationFile.Close()

		// _, err = io.Copy(destinationFile, sourceFile)
		// if err != nil {
		// 	return digest.Digest{}, fmt.Errorf("failed to copy %s to %s: %v", original, target, err)
		// }

		// // The following call ensures that the destination file is flushed properly before closing.
		// err = destinationFile.Sync()
		if err != nil {
			return digest.Digest{}, fmt.Errorf("failed to create symlink from %s to %s: %v", original, target, err)
		}
	}

	du := NewDirUploader(&zu.CommonConfig, targetDir, nil)
	rootDigest, err := du.DoUpload()
	if err != nil {
		return rootDigest, fmt.Errorf("failed to upload the directory %s: %v", targetDir, err)
	}
	return rootDigest, nil
}
