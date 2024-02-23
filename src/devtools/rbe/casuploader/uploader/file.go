package uploader

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"

	log "github.com/golang/glog"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/digest"
	"github.com/google/device-infra/src/devtools/rbe/casuploader/chunker"
)

const (
	// ChunksDirName is the name of the dir for chunk files.
	ChunksDirName = "_chunks"
	// ChunksIndexFileName is the name of the chunks index file.
	ChunksIndexFileName = "_chunks_index.json"
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

	chunksDir := filepath.Join(targetDir, ChunksDirName)
	os.MkdirAll(chunksDir, 0755)

	chunks, err := chunker.ChunkFile(fu.path, chunksDir, fu.CommonConfig.avgChunkSize)
	if err != nil {
		return digest.Digest{}, fmt.Errorf("failed to chunk the file %s: %v", fu.path, err)
	}

	if createIndexFile(fu.path, targetDir, chunks) != nil {
		return digest.Digest{}, fmt.Errorf("failed to create index file for file %s: %v", fu.path, err)
	}

	du := NewDirUploader(&fu.CommonConfig, targetDir, nil)
	rootDigest, err := du.DoUpload()
	if err != nil {
		return rootDigest, fmt.Errorf("failed to upload the directory %s for file %s: %v", targetDir, fu.path, err)
	}
	return rootDigest, nil
}

// ChunksIndex is the index of all chunks for a file.
// A chunks index file contains a list of chunks index entries, one for each file for the upload.
// TODO: For restoration, add fields for file attributes like permissions settings, time etc.
type ChunksIndex struct {
	// Relative to target dir
	Path   string              `json:"path"`
	Chunks []chunker.ChunkInfo `json:"chunks"`
}

func createIndexFile(path string, targetDir string, chunks []chunker.ChunkInfo) error {
	filename := filepath.Base(path)
	chunksIndex := ChunksIndex{Path: filename, Chunks: chunks}
	content := []ChunksIndex{chunksIndex}
	outputContent, err := json.MarshalIndent(content, "", "  ")
	if err != nil {
		return fmt.Errorf("failed to marshall chunk index for the file %s: %v", path, err)
	}

	indexPath := filepath.Join(targetDir, ChunksIndexFileName)
	if err = os.WriteFile(indexPath, outputContent, 0644); err != nil {
		return fmt.Errorf("failed to write chunk index file for the file %s: %v", path, err)
	}

	return nil
}
