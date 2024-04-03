package uploader

import (
	"fmt"
	"os"
	"path"
	"path/filepath"

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

// DoUpload uploads the file to CAS, and returns the digest of the root.
func (fu *FileUploader) DoUpload() (digest.Digest, error) {
	// TODO: Support uploading single file with chunking disabled.
	if !fu.CommonConfig.chunk {
		return digest.Digest{}, fmt.Errorf("not supported - upload single file with chunking disabled: %s", fu.path)
	}

	targetDir := createTmpDir()
	defer func() {
		if err := os.RemoveAll(targetDir); err != nil {
			log.Errorf("Failed to remove tmp dir: %v\n", err)
		}
	}()

	chunksDir := filepath.Join(targetDir, chunkerutil.ChunksDirName)
	os.MkdirAll(chunksDir, 0755)

	chunksIndex, err := chunkerutil.ChunkFile(fu.path, path.Base(fu.path), chunksDir, fu.CommonConfig.avgChunkSize)
	if err != nil {
		return digest.Digest{}, fmt.Errorf("failed to chunk the file %s: %v", fu.path, err)
	}

	if err := chunkerutil.CreateIndexFile(targetDir, []chunkerutil.ChunksIndex{chunksIndex}); err != nil {
		return digest.Digest{}, fmt.Errorf("failed to create index file for file %s: %v", fu.path, err)
	}

	du := NewDirUploader(&fu.CommonConfig, targetDir, nil)
	rootDigest, err := du.DoUpload()
	if err != nil {
		return rootDigest, fmt.Errorf("failed to upload the directory %s for file %s: %v", targetDir, fu.path, err)
	}
	return rootDigest, nil
}
