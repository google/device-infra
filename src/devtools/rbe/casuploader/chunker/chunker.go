// Package chunker provides functions to chunk a file.
package chunker

import (
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"io"
	"os"
	"path/filepath"

	"github.com/jotfs/fastcdc-go"
)

// ChunkInfo contains the sha256 and offset of a chunk in a file.
type ChunkInfo struct {
	SHA256 string `json:"sha256"`
	Offset int64  `json:"offset"`
}

// ChunkFile divides a file into chunks
// and saves them in chunksDir, each named with its sha256.
// It returns the list of the chunks with their SHA256 and offset in the source file.
func ChunkFile(path string, chunksDir string, avgChunkSizeKb int) ([]ChunkInfo, error) {
	source, err := os.Open(path)
	if err != nil {
		return nil, fmt.Errorf("failed to open %s: %v", path, err)
	}
	defer source.Close()

	chunker, err := fastcdc.NewChunker(source, fastcdc.Options{
		AverageSize: 1024 * avgChunkSizeKb,
	})
	if err != nil {
		return nil, fmt.Errorf("failed to create chunker for %s: %v", path, err)
	}

	chunks := []fastcdc.Chunk{}
	chunkmap := make(map[string]int)
	chunkList := []ChunkInfo{}

	for {
		chunk, err := chunker.Next()
		if err == io.EOF {
			break
		}
		if err != nil {
			return nil, err
		}

		sha256 := chunkSHA256(chunk)
		chunks = append(chunks, chunk)
		_, ok := chunkmap[sha256]
		if !ok {
			chunkmap[sha256] = chunk.Length
			writeChunkToFile(chunksDir, sha256, chunk)
		}
		chunkList = append(chunkList, ChunkInfo{SHA256: sha256, Offset: int64(chunk.Offset)})
	}

	return chunkList, nil
}

func chunkSHA256(chunk fastcdc.Chunk) string {
	hash := sha256.New()
	hash.Write(chunk.Data)
	hashCode := hex.EncodeToString(hash.Sum(nil))
	return hashCode
}

func writeChunkToFile(dir string, sha256 string, chunk fastcdc.Chunk) error {
	path := filepath.Join(dir, sha256)
	return os.WriteFile(path, chunk.Data, 0644)
}

// RestoreFile restores a file from its chunks file in chunksDir using.
func RestoreFile(path string, chunksDir string, chunks []ChunkInfo) error {
	err := os.MkdirAll(filepath.Dir(path), 0755) // Standard permissions
	if err != nil {
		return fmt.Errorf("error creating directories: %w", err)
	}

	if len(chunks) == 1 {
		// Hard link the file if there is only one chunk.
		chunk := chunks[0]
		if err := os.Link(filepath.Join(chunksDir, chunk.SHA256), path); err == nil {
			return nil
		}
		// fallthrough as failover for file linking.
	}

	file, err := os.Create(path)
	if err != nil {
		return fmt.Errorf("error creating file: %w", err)
	}
	defer file.Close()

	// Restore the file by appending chunks.
	for _, chunk := range chunks {
		chunkFile, err := os.Open(filepath.Join(chunksDir, chunk.SHA256))
		if err != nil {
			return fmt.Errorf("failed to open chunk file: %v", err)
		}
		_, err = io.Copy(file, chunkFile)
		if err != nil {
			return fmt.Errorf("failed to append chunk to artifact: %v", err)
		}
	}

	return nil
}
