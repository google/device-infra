// Package chunkstore implements a chunk store that stores files as chunks.
package chunkstore

import (
	"encoding/json"
	"fmt"

	"os"
	"path/filepath"
	"time"

	"github.com/google/device-infra/src/devtools/rbe/casuploader/chunker"
	"github.com/hanwen/go-fuse/v2/fuse"
)

type fuseMode uint32

// ChunkInfo represents a chunk of a file.
type ChunkInfo struct {
	chunker.ChunkInfo
	Length int `json:"-"` // FastCDC chunk length is typically small, ~128KB.
}

// FileInfo represents a file in the chunk store.
type FileInfo struct {
	Path    string      `json:"path"`
	ModTime time.Time   `json:"mod_time"`
	Mode    fuseMode    `json:"mode"`   // os.FileMode in json, converted to fuseMode on unmarshal.
	Chunks  []ChunkInfo `json:"chunks"` // FastCDC chunks are perfectly contiguous and sorted by offset.
	Size    int64       `json:"-"`
}

// ChunkStore represents a chunk store that stores files as chunks.
type ChunkStore struct {
	chunkDir string
	files    []FileInfo
	fileMap  map[string]*FileInfo // Map of file path to FileInfo
}

func getFileSize(filePath string) (int64, error) {
	stat, err := os.Stat(filePath)
	if err != nil {
		return 0, fmt.Errorf("file %s not found: %w", filePath, err)
	}

	return stat.Size(), nil
}

// NewChunkStore creates a new ChunkStore from the given index path.
func NewChunkStore(chunkDir, indexPath string) (*ChunkStore, error) {
	data, err := os.ReadFile(indexPath)
	if err != nil {
		return nil, err
	}

	var files []FileInfo
	if err := json.Unmarshal(data, &files); err != nil {
		return nil, err
	}

	// Calculate file sizes -
	fileMap := map[string]*FileInfo{}
	for i, file := range files {
		fileMap[file.Path] = &files[i]

		fileSize := int64(0)
		chunkCount := len(file.Chunks)
		for c, chunk := range file.Chunks {
			if c < chunkCount-1 {
				file.Chunks[c].Length = int(file.Chunks[c+1].Offset - chunk.Offset)
				continue
			}

			// Last chunk, get chunk size
			path := filepath.Join(chunkDir, chunk.SHA256)
			length, err := getFileSize(path)
			if err != nil {
				return nil, err
			}
			file.Chunks[c].Length = int(length)

			// file.chunks are perfectly contiguous and sorted by offset.
			fileSize = chunk.Offset + int64(length)
		}
		files[i].Size = fileSize
	}

	return &ChunkStore{
		chunkDir: chunkDir,
		files:    files,
		fileMap:  fileMap,
	}, nil
}

// UnmarshalJSON is a custom unmarshal to convert os.FileMode → fuseMode.
func (fi *FileInfo) UnmarshalJSON(data []byte) error {
	// Create shadow type to avoid recursion
	type Alias FileInfo
	aux := &struct {
		*Alias
	}{
		Alias: (*Alias)(fi),
	}

	if err := json.Unmarshal(data, &aux); err != nil {
		return err
	}

	fi.Mode = osFileModeToFuseMode((os.FileMode)(fi.Mode))
	return nil
}

func osFileModeToFuseMode(m os.FileMode) fuseMode {
	// Extract permission bits (last 9 bits)
	perm := fuseMode(m & os.ModePerm)

	// Add file type flags
	var mode fuseMode
	if m&os.ModeDir != 0 {
		mode = fuse.S_IFDIR | perm
	} else if m&os.ModeSymlink != 0 {
		mode = fuse.S_IFLNK | perm
	} else { // Regular file
		mode = fuse.S_IFREG | perm
	}
	return mode
}

// GetFiles returns all files in the chunk store.
func (cs *ChunkStore) GetFiles() []FileInfo {
	return cs.files
}

// GetFile returns the FileInfo for the given file path.
func (cs *ChunkStore) GetFile(path string) (*FileInfo, error) {
	if fileInfo, ok := cs.fileMap[path]; ok {
		return fileInfo, nil
	}
	return nil, os.ErrNotExist
}
