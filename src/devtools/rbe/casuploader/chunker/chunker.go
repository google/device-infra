// Package chunker provides functions to chunk a file.
package chunker

import (
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"math/rand/v2"
	"os"
	"path/filepath"
	"strconv"

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

	fileInfo, err := source.Stat()
	if err != nil {
		return nil, fmt.Errorf("failed to get file info for %s: %v", path, err)
	}

	chunker, err := fastcdc.NewChunker(source, fastcdc.Options{
		AverageSize: 1024 * avgChunkSizeKb,
	})
	if err != nil {
		return nil, fmt.Errorf("failed to create chunker for %s: %v", path, err)
	}

	// Add 5% to the estimated chunks to hopefully avoid in-loop reallocating of a large slice.
	estimatedChunks := int(1.05*float64(fileInfo.Size())/float64(1024*avgChunkSizeKb)) + 1
	seenChunks := make(map[string]struct{}) // Use the map as a set to deduplicate chunks.
	chunkList := make([]ChunkInfo, 0, estimatedChunks)

	for {
		chunk, err := chunker.Next()
		if err == io.EOF {
			break
		}
		if err != nil {
			return nil, err
		}

		sha256 := chunkSHA256(chunk)
		if _, ok := seenChunks[sha256]; !ok {
			// To add the sha to the set, assign an empty struct value
			seenChunks[sha256] = struct{}{}

			if err := writeChunkToFile(chunksDir, sha256, chunk); err != nil {
				return nil, fmt.Errorf("failed to write chunk %s: %w", sha256, err)
			}
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

// restoredFileMode is the permission given to a file restored by copying. It
// matches what os.Create produced under the usual 022 umask, which is what
// this package did before restores were written to a temporary file first.
const restoredFileMode = 0644

// RestoreFile restores a file from its chunks file in chunksDir.
//
// A file made of a single chunk is hard linked to that chunk rather than
// copied. The chunk may itself be a hard link into a shared cache, so the
// restored file must then be treated as read-only: writing, truncating,
// chmod'ing or touching it would change the chunk, and every other file that
// shares its inode, as well. Callers that go on to modify the restored file,
// including its mode or times, must use RestoreFileCopy instead.
//
// Whatever was at path before is replaced, never modified in place, and path
// never holds a partially restored file.
func RestoreFile(path string, chunksDir string, chunks []ChunkInfo) error {
	return restoreFile(path, chunksDir, chunks, true, false)
}

// RestoreFileCopy is like RestoreFile, but always writes an independent copy,
// so the restored file shares no inode with any chunk and is safe to modify.
func RestoreFileCopy(path string, chunksDir string, chunks []ChunkInfo) error {
	return restoreFile(path, chunksDir, chunks, false, false)
}

// RestorePrivateFileCopy is like RestoreFileCopy, but for a path that is
// private to the caller, such as a run-unique staging name, which nothing
// else reads until the caller moves it into place.
//
// If nothing is at path yet, the file is written there directly, saving the
// temporary sibling and rename that RestoreFileCopy uses. A failed restore
// removes it, but a crash part way can leave a partial file at path, so path
// must never be a name that others read. If something is already at path, it
// is replaced as RestoreFileCopy does.
func RestorePrivateFileCopy(path string, chunksDir string, chunks []ChunkInfo) error {
	return restoreFile(path, chunksDir, chunks, false, true)
}

func restoreFile(path string, chunksDir string, chunks []ChunkInfo, allowLink, private bool) error {
	if allowLink && len(chunks) == 1 {
		// Hard link the file if there is only one chunk. If linking fails,
		// fall back to copying the data.
		src := filepath.Join(chunksDir, chunks[0].SHA256)
		err := linkFile(src, path)
		if errors.Is(err, os.ErrNotExist) {
			if mkdirErr := os.MkdirAll(filepath.Dir(path), 0755); mkdirErr != nil {
				return fmt.Errorf("error creating directories: %w", mkdirErr)
			}
			err = linkFile(src, path)
		}
		if err == nil {
			return nil
		}
	}

	// Restore into a temporary sibling and rename it into place, so path never
	// holds a partially restored file. Opening path itself with O_TRUNC would
	// also truncate whatever inode is there, which may be shared with a cached
	// blob. A private path that is not in use yet (for example, a staging
	// temporary name from RestoreFilesTo) is instead created directly with
	// O_EXCL, saving the extra temporary file and rename.
	dir, base := filepath.Dir(path), filepath.Base(path)
	openDst := func() (*os.File, bool, error) {
		if private {
			f, err := os.OpenFile(path, os.O_CREATE|os.O_EXCL|os.O_WRONLY, restoredFileMode)
			if !errors.Is(err, os.ErrExist) {
				return f, false, err
			}
		}
		f, err := os.CreateTemp(dir, "."+base+".restore.*")
		return f, true, err
	}
	dst, needRename, err := openDst()
	if errors.Is(err, os.ErrNotExist) {
		if mkdirErr := os.MkdirAll(dir, 0755); mkdirErr != nil {
			return fmt.Errorf("error creating directories: %w", mkdirErr)
		}
		dst, needRename, err = openDst()
	}
	if err != nil {
		return fmt.Errorf("error creating file: %w", err)
	}
	dstName := dst.Name()
	committed := false
	defer func() {
		if !committed {
			dst.Close()
			os.Remove(dstName)
		}
	}()

	// CreateTemp creates the file 0600, and OpenFile filters its mode through
	// the umask. Chmod on the descriptor is not filtered through the umask.
	if err := dst.Chmod(restoredFileMode); err != nil {
		return fmt.Errorf("failed to set mode on %s: %w", dstName, err)
	}

	// Restore the file by appending chunks.
	for _, chunk := range chunks {
		chunkFile, err := os.Open(filepath.Join(chunksDir, chunk.SHA256))
		if err != nil {
			return fmt.Errorf("failed to open chunk file: %v", err)
		}
		_, copyErr := io.Copy(dst, chunkFile)
		closeErr := chunkFile.Close()
		if copyErr != nil {
			return fmt.Errorf("failed to append chunk to artifact: %w", copyErr)
		}
		if closeErr != nil {
			return fmt.Errorf("failed to close chunk file: %w", closeErr)
		}
	}

	if err := dst.Close(); err != nil {
		return fmt.Errorf("failed to close restored file %s: %w", dstName, err)
	}
	if needRename {
		if err := os.Rename(dstName, path); err != nil {
			return fmt.Errorf("failed to move restored file into place at %s: %w", path, err)
		}
	}
	committed = true
	return nil
}

// linkFile makes path a hard link to src.
//
// An existing file at path is replaced by linking src to a temporary sibling
// and renaming that over path. It is not unlinked first, so path is never
// absent and is left untouched if the link fails, and it is never written
// through, since it may share an inode with a cached blob.
func linkFile(src, path string) error {
	err := os.Link(src, path)
	if !errors.Is(err, os.ErrExist) {
		return err
	}

	// link(2) fails rather than replace an existing name, so a collision on
	// the random name only sends the caller to its copy fallback.
	tmpName := filepath.Join(filepath.Dir(path), "."+filepath.Base(path)+".link."+strconv.FormatUint(rand.Uint64(), 36))
	if err := os.Link(src, tmpName); err != nil {
		return err
	}
	err = os.Rename(tmpName, path)
	// rename(2) does nothing, successfully, when path is already a link to the
	// same inode, leaving tmpName behind; it is also left behind if the rename
	// failed. After an effective rename it no longer exists, so the error is
	// not interesting.
	os.Remove(tmpName)
	return err
}
