package uploader

import (
	"fmt"
	"io"
	"os"
	"path"
	"path/filepath"

	"time"

	log "github.com/golang/glog"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/digest"
	"github.com/google/device-infra/src/devtools/rbe/casuploader/chunkerutil"
)

// FileUploader is the uploader for uploading a file to CAS.
type FileUploader struct {
	CommonConfig
	path string
}

// NewFileUploader creates a new file uploader to upload a single file to CAS.
func NewFileUploader(config *CommonConfig, path string) Uploader {
	return &FileUploader{
		CommonConfig: *config,
		path:         path,
	}
}

func copyFile(dstPath string, srcPath string, mode os.FileMode) error {
	src, err := os.Open(srcPath)
	if err != nil {
		return err
	}
	defer src.Close()

	dst, err := os.OpenFile(dstPath, os.O_CREATE|os.O_EXCL|os.O_WRONLY, mode)
	if err != nil {
		return err
	}
	defer dst.Close()

	_, err = io.Copy(dst, src)
	return err
}

// DoUpload uploads the file to CAS, and returns the digest of the root.
func (fu *FileUploader) DoUpload() (digest.Digest, error) {
	targetDir := createTmpDir()
	defer func() {
		if err := os.RemoveAll(targetDir); err != nil {
			log.Errorf("Failed to remove tmp dir: %v\n", err)
		}
	}()

	if fu.CommonConfig.chunk {
		start := time.Now()
		chunksDir := filepath.Join(targetDir, chunkerutil.ChunksDirName)
		os.MkdirAll(chunksDir, 0755)

		chunksIndex, err := chunkerutil.ChunkFile(fu.path, path.Base(fu.path), chunksDir, fu.CommonConfig.avgChunkSize)
		if err != nil {
			return digest.Digest{}, fmt.Errorf("failed to chunk the file %s: %v", fu.path, err)
		}

		if err := chunkerutil.CreateIndexFile(targetDir, []chunkerutil.ChunksIndex{chunksIndex}); err != nil {
			return digest.Digest{}, fmt.Errorf("failed to create index file for file %s: %v", fu.path, err)
		}
		fu.CommonConfig.metrics.ChunkTimeMs = time.Since(start).Milliseconds()
	} else {
		// Upload as a dir with the file in it.
		path := filepath.Join(targetDir, filepath.Base(fu.path))
		if err := os.Symlink(fu.path, path); err != nil {
			// Failover to copy the file.
			fileInfo, err := os.Stat(fu.path)
			if err != nil {
				return digest.Digest{}, err
			}
			if err := copyFile(path, fu.path, fileInfo.Mode()); err != nil {
				return digest.Digest{}, fmt.Errorf("failed to copy file: %w", err)
			}
		}
	}

	du := NewDirUploader(&fu.CommonConfig, targetDir, nil)
	rootDigest, err := du.DoUpload()
	if err != nil {
		return rootDigest, fmt.Errorf("failed to upload the directory %s for file %s: %v", targetDir, fu.path, err)
	}
	return rootDigest, nil
}
