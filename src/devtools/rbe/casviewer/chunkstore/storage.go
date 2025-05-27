package chunkstore

import (
	"fmt"
	"io"
	"os"
	"path/filepath"
)

// ReadFileToDest reads content from the virtual file specified by path, at a given offset,
// into the provided dest buffer. It returns the number of bytes read and any error.
func (cs *ChunkStore) ReadFileToDest(path string, dest []byte, offset int64) (int, error) {
	file, err := cs.GetFile(path)
	if err != nil {
		return 0, err
	}

	if offset < 0 {
		return 0, fmt.Errorf("negative offset not allowed")
	}
	if offset >= file.Size {
		return 0, io.EOF // Reading at or past EOF
	}

	destLen := int64(len(dest))
	bytesToReadOverall := min(destLen, file.Size-offset) // Don't read past EOF of virtual file.
	if bytesToReadOverall <= 0 {
		return 0, nil // Nothing to read (e.g. offset is exactly at EOF, or len(dest) is 0)
	}

	bytesActuallyCopiedToDest := 0
	// Virtual read window in the file: [offset, offset + bytesToReadOverall)
	virtualReadStart := offset
	virtualReadEnd := offset + bytesToReadOverall

	startIndex := cs.findChunkIndex(file.Chunks, virtualReadStart)
	if startIndex == -1 {
		return 0, fmt.Errorf("could not find chunk for offset: %v", virtualReadStart)
	}

	// file.chunks are perfectly contiguous, without any gaps.
	// offset is verified to be in the range of [0, file.Size), so startIndex can't be negative.
	for i := startIndex; i < len(file.Chunks); i++ {
		chunk := file.Chunks[i]
		chunkVirtualStart := chunk.Offset
		chunkVirtualEnd := chunk.Offset + int64(chunk.Length)

		overlapStart := max(virtualReadStart, chunkVirtualStart)
		overlapEnd := min(virtualReadEnd, chunkVirtualEnd)

		if overlapStart >= overlapEnd {
			// No more chunks to read
			continue
		}

		lengthOfOverlap := overlapEnd - overlapStart
		if lengthOfOverlap <= 0 {
			continue
		}

		readPosInChunkFile := overlapStart - chunkVirtualStart
		chunkFilePath := filepath.Join(cs.chunkDir, chunk.SHA256)
		f, err := os.Open(chunkFilePath)
		if err != nil {
			return bytesActuallyCopiedToDest, fmt.Errorf("failed to open chunk %s: %w", chunk.SHA256, err)
		}

		_, err = f.Seek(readPosInChunkFile, io.SeekStart)
		if err != nil {
			f.Close()
			return bytesActuallyCopiedToDest, fmt.Errorf("failed to seek in chunk %s to %d: %w", chunk.SHA256, readPosInChunkFile, err)
		}

		n, readErr := io.ReadFull(f, dest[bytesActuallyCopiedToDest:bytesActuallyCopiedToDest+int(lengthOfOverlap)])
		f.Close()

		if readErr != nil && readErr != io.EOF && readErr != io.ErrUnexpectedEOF {
			// For io.ReadFull, an error means not all bytes were read.
			return bytesActuallyCopiedToDest + n, fmt.Errorf("failed to read %d bytes from chunk %s: %w", lengthOfOverlap, chunk.SHA256, readErr)
		}

		bytesActuallyCopiedToDest += n

		if int64(bytesActuallyCopiedToDest) >= bytesToReadOverall {
			break
		}
	}

	return bytesActuallyCopiedToDest, nil
}

func (cs *ChunkStore) findChunkIndex(chunks []ChunkInfo, offset int64) int {
	// The alternative can be estimating the chunk index based on the average chunk size.
	// It doesn't actually help due to deviations in chunk sizes from FastCDC.
	low, high := 0, len(chunks)-1
	result := -1
	for low <= high {
		mid := low + (high-low)/2
		if chunks[mid].Offset <= offset {
			result = mid
			low = mid + 1
		} else {
			high = mid - 1
		}
	}
	return result
}

func max(a, b int64) int64 {
	if a > b {
		return a
	}
	return b
}

func min(a, b int64) int64 {
	if a < b {
		return a
	}
	return b
}
