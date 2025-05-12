// Package chunkerutil provides utility functions for chunking files.
package chunkerutil

import (
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"time"

	log "github.com/golang/glog"
	"github.com/google/device-infra/src/devtools/rbe/casuploader/chunker"
)

const (
	// ChunksDirName is the name of the dir for chunk files.
	ChunksDirName = "_chunks"
	// ChunksIndexFileName is the name of the chunks index file.
	ChunksIndexFileName = "_chunks_index.json"
)

// ChunksIndex is the index of all chunks for a file.
// A chunks index file contains a list of chunks index entries, one for each file for the upload.
type ChunksIndex struct {
	// Relative to target dir
	Path    string              `json:"path"`
	ModTime time.Time           `json:"mod_time"`
	Mode    os.FileMode         `json:"mode"`
	Chunks  []chunker.ChunkInfo `json:"chunks"`
}

// ChunkFile chunks the file and returns ChunksIndex for restoration.
func ChunkFile(srcPath string, dstPath, chunksDir string, avgChunkSize int) (ChunksIndex, error) {
	chunks, err := chunker.ChunkFile(srcPath, chunksDir, avgChunkSize)
	if err != nil {
		return ChunksIndex{}, fmt.Errorf("failed to chunk the file %s: %v", srcPath, err)
	}
	info, err := os.Stat(srcPath)
	if err != nil {
		return ChunksIndex{}, err
	}
	return ChunksIndex{Path: dstPath, ModTime: info.ModTime(), Mode: info.Mode(), Chunks: chunks}, nil
}

// CreateIndexFile creates the index file for the collection of ChunksIndex and chunks.
func CreateIndexFile(inDir string, chunksIndex []ChunksIndex) error {
	outputContent, err := json.MarshalIndent(chunksIndex, "", "  ")
	if err != nil {
		return fmt.Errorf("failed to marshall chunk index: %v", err)
	}

	indexPath := filepath.Join(inDir, ChunksIndexFileName)
	if err = os.WriteFile(indexPath, outputContent, 0644); err != nil {
		return fmt.Errorf("failed to write chunk index file: %v", err)
	}

	linkedPath := filepath.Join(inDir, ChunksDirName, ChunksIndexFileName)
	if err := os.Link(indexPath, linkedPath); err != nil {
		log.Errorf("failed to hardlink chunk index file: %v", err)
	}

	return nil
}

// RestoreFiles restores files to dstDir with chunks index file and chunks file in srcDir.
func RestoreFiles(srcDir string, dstDir string) error {
	indexPath, err := FindChunksIndex(srcDir)
	if err != nil {
		log.Infof("no chunk index file found, skip restoring chunked files")
		return nil
	}

	index, err := os.ReadFile(indexPath)
	if err != nil {
		return fmt.Errorf("can't read chunk index file: %v", err)
	}

	var chunksIndexEntries []ChunksIndex
	if err := json.Unmarshal(index, &chunksIndexEntries); err != nil {
		return fmt.Errorf("can't unmarshal chunk index file: %v", err)
	}

	chunksDir := filepath.Join(srcDir, ChunksDirName)
	for _, chunksIndex := range chunksIndexEntries {
		dstPath := filepath.Join(dstDir, chunksIndex.Path)
		if err := chunker.RestoreFile(dstPath, chunksDir, chunksIndex.Chunks); err != nil {
			return err
		}
		if !chunksIndex.ModTime.IsZero() { // for backward compatibility
			os.Chtimes(dstPath, chunksIndex.ModTime, chunksIndex.ModTime)
			os.Chmod(dstPath, chunksIndex.Mode)
		}
	}

	err = DeleteChunkFilesAndIndex(srcDir)
	log.Infof("restored %d chunked files", len(chunksIndexEntries))
	return err
}

// FindChunksIndex returns the path of ChunksIndex file in the dir.
func FindChunksIndex(dir string) (string, error) {
	indexPath := filepath.Join(dir, ChunksDirName, ChunksIndexFileName)
	if _, err := os.Stat(indexPath); err != nil {
		indexPath := filepath.Join(dir, ChunksIndexFileName)
		if _, err := os.Stat(indexPath); os.IsNotExist(err) {
			return "", fmt.Errorf("chunk index file not found in %s", dir)
		}
	}
	return indexPath, nil
}

// DeleteChunkFilesAndIndex deletes chunk files dir and the chunk index file.
func DeleteChunkFilesAndIndex(dir string) error {
	// Delete chunk index file and chunks dir
	if err := os.Remove(filepath.Join(dir, ChunksIndexFileName)); err != nil {
		if !errors.Is(err, os.ErrNotExist) {
			return fmt.Errorf("error deleting chunk index file: %v", err)
		}
	}
	if err := os.RemoveAll(filepath.Join(dir, ChunksDirName)); err != nil {
		return fmt.Errorf("error deleting chunks dir: %v", err)
	}

	return nil
}
