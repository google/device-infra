// Package chunker provides functions to chunk a file.
package chunker

import (
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"io"
	"os"
	"path/filepath"

	log "github.com/golang/glog"
	"google3/third_party/fastcdc_go/fastcdc"
)

// ChunkInfo contains the sha256 and offset of a chunk in a file.
type ChunkInfo struct {
	SHA256 string `json:"sha256"`
	Offset int64  `json:"offset"`
}

// ChunkFile divides a file into chunks
// and saves them in "chunks" subfolder under targetDir, each named with the its sha256.
// It returns the list of the chunks with their SHA256 and offset in the source file.
func ChunkFile(path string, targetDir string, avgChunkSizeKb int) ([]ChunkInfo, error) {
	fmt.Println("Chunking file:", path)

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
	var dupChunkCount int
	var dupChunkSize int64

	chunkDir := filepath.Join(targetDir, "chunks")
	os.MkdirAll(chunkDir, 0755)

	chunkList := []ChunkInfo{}

	for {
		chunk, err := chunker.Next()
		if err == io.EOF {
			break
		}
		if err != nil {
			log.Fatal(err)
		}

		sha256 := chunkSHA256(chunk)
		fmt.Println("Generated Chunk:", sha256, "Size:", chunk.Length/1024, "KiB, Offset:", chunk.Offset)

		chunks = append(chunks, chunk)
		_, ok := chunkmap[sha256]
		if !ok {
			chunkmap[sha256] = chunk.Length
			writeChunkToFile(chunkDir, sha256, chunk)
		} else {
			dupChunkCount++
			dupChunkSize += int64(chunk.Length)
		}
		chunkList = append(chunkList, ChunkInfo{SHA256: sha256, Offset: chunk.Offset})
	}

	fmt.Println("Chunks data wrote to", targetDir)

	fmt.Println("Average Chunk Size (KiB):", avgChunkSizeKb)
	fmt.Println("Chunk count:", len(chunks))
	fmt.Println("Dup Chunk count:", dupChunkCount)
	fmt.Println("Dup Chunk size (MB):", dupChunkSize/1024/1024)
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
