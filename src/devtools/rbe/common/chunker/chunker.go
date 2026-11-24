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
	"sync"

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

// ChunkMismatchError is the error a restore returns when the bytes it read
// from a chunk file do not hash to the SHA-256 the chunk is named after.
//
// A chunk is content-addressed, so such a file is corrupt: a bit flipped in
// memory or in the page cache after the chunk was downloaded and verified, a
// short write, or an edit through a hard link. Callers that got the chunk from
// a cache can use Path and Want to remove it there, so that the next attempt
// fetches a good copy instead of failing on the same bytes again.
type ChunkMismatchError struct {
	// Path is the chunk file that was read.
	Path string
	// Want is the SHA-256, in hex, that the chunk is named after.
	Want string
	// Got is the SHA-256, in hex, of the bytes read from Path.
	Got string
	// Size is the number of bytes read from Path.
	Size int64
}

func (e *ChunkMismatchError) Error() string {
	return fmt.Sprintf("chunk %s is corrupt: its %d bytes have SHA-256 %s, want %s", e.Path, e.Size, e.Got, e.Want)
}

// copyBufferSize is the size of the buffers that verifying restores copy
// chunks through. Every chunk byte passes through one of them on its way to
// both the restored file and the hash that verifies it, so they are large
// enough to keep the number of read and write calls per chunk small.
const copyBufferSize = 1 << 20

// copyBuffers holds the *[]byte buffers of copyBufferSize that verifying
// restores copy chunks through. Files are restored in parallel and most of
// them are small, so buffers are reused rather than allocated per file.
// Restores that do not verify leave the copy to the kernel and take none.
var copyBuffers = sync.Pool{
	New: func() any {
		b := make([]byte, copyBufferSize)
		return &b
	},
}

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
//
// Without verify, chunks are copied as they are, by the kernel where it can
// (copy_file_range on Linux), without passing through user space.
//
// With verify, every chunk that is copied is verified against its SHA-256 as
// it is copied, so the check reads nothing that the copy does not read anyway,
// but every chunk byte is read into user space, hashed and written back out. A
// chunk that does not match fails the restore with a *ChunkMismatchError, and
// nothing is left at path. A single chunk that is hard linked is not read, and
// so not verified, either way: linking costs the same whatever the chunk's
// size, and hashing it would read the whole chunk.
func RestoreFile(path string, chunksDir string, chunks []ChunkInfo, verify bool) error {
	return restoreFile(path, chunksDir, chunks, true, false, verify)
}

// RestoreFileCopy is like RestoreFile, but always writes an independent copy,
// so the restored file shares no inode with any chunk and is safe to modify.
// Every chunk is therefore copied, and with verify, verified against its
// SHA-256.
func RestoreFileCopy(path string, chunksDir string, chunks []ChunkInfo, verify bool) error {
	return restoreFile(path, chunksDir, chunks, false, false, verify)
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
func RestorePrivateFileCopy(path string, chunksDir string, chunks []ChunkInfo, verify bool) error {
	return restoreFile(path, chunksDir, chunks, false, true, verify)
}

func restoreFile(path string, chunksDir string, chunks []ChunkInfo, allowLink, private, verify bool) error {
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
	switch {
	case len(chunks) == 0:
		// An empty file: there is nothing to copy or verify, so no buffer is
		// taken.
	case verify:
		bufp := copyBuffers.Get().(*[]byte)
		defer copyBuffers.Put(bufp)
		for _, chunk := range chunks {
			if err := appendChunk(dst, filepath.Join(chunksDir, chunk.SHA256), chunk.SHA256, *bufp); err != nil {
				return err
			}
		}
	default:
		for _, chunk := range chunks {
			if err := appendChunkUnverified(dst, filepath.Join(chunksDir, chunk.SHA256)); err != nil {
				return err
			}
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

// appendChunk copies the chunk file at chunkPath to the end of dst through
// buf, and fails with a *ChunkMismatchError if what it copied does not have
// the SHA-256 want.
//
// The bytes are hashed in buf on their way to dst, which is the only way to
// verify them without reading the chunk a second time. That is also why
// neither side of the copy is handed to io.CopyBuffer as an *os.File: it
// would then copy with copy_file_range, inside the kernel, where nothing can
// hash the bytes, and which on a filesystem that shares extents may not read
// them at all. Giving that up is what the check costs: every chunk byte is
// read into user space, hashed and written back out.
//
// The bytes are written to dst before the hash is checked, so on a mismatch
// dst holds them; the caller removes it.
func appendChunk(dst io.Writer, chunkPath, want string, buf []byte) error {
	chunkFile, err := os.Open(chunkPath)
	if err != nil {
		return fmt.Errorf("failed to open chunk file: %v", err)
	}
	h := sha256.New()
	// The struct hides chunkFile's WriteTo method, and io.MultiWriter has no
	// ReadFrom method, so io.CopyBuffer copies through buf.
	n, copyErr := io.CopyBuffer(io.MultiWriter(dst, h), struct{ io.Reader }{chunkFile}, buf)
	closeErr := chunkFile.Close()
	if copyErr != nil {
		return fmt.Errorf("failed to append chunk to artifact: %w", copyErr)
	}
	if closeErr != nil {
		return fmt.Errorf("failed to close chunk file: %w", closeErr)
	}
	if got := hex.EncodeToString(h.Sum(nil)); got != want {
		return &ChunkMismatchError{Path: chunkPath, Want: want, Got: got, Size: n}
	}
	return nil
}

// appendChunkUnverified copies the chunk file at chunkPath to the end of dst,
// without checking what it copies.
//
// Both sides of the copy are *os.File, so on Linux io.Copy hands it to
// copy_file_range: the kernel copies the bytes, or shares the extents on a
// filesystem that can, without reading them into user space.
func appendChunkUnverified(dst *os.File, chunkPath string) error {
	chunkFile, err := os.Open(chunkPath)
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
