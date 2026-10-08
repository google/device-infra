// Package chunkerutil provides utility functions for chunking files.
package chunkerutil

import (
	"crypto/sha256"
	"encoding/hex"
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
	// snippetSize is the size of the snippet to log when logging file snippets.
	snippetSize = 1024
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
		// Creating a backup index file is redundant, so we don't fail the upload if this fails.
		log.Errorf("failed to hardlink chunk index file: %v", err)
	}

	hash := sha256.Sum256(outputContent)
	log.Infof("hash of index file: %s", hex.EncodeToString(hash[:]))

	return nil
}

func logFileSnippets(filepath string, content []byte) {
	if len(content) == 0 {
		log.Warningf("File %s is empty.", filepath)
		return
	}

	log.Infof("File %s size: %d bytes", filepath, len(content))

	if len(content) <= 2*snippetSize {
		log.Infof("File content snippet:\n%s", string(content))
		return
	}
	log.Infof("File content snippet (first %d bytes):\n%s", snippetSize, string(content[:snippetSize]))
	log.Infof("File content snippet (last %d bytes):\n%s", snippetSize, string(content[len(content)-snippetSize:]))
}

// chtimes and chmod are indirections over os.Chtimes and os.Chmod so that
// tests can make them fail, which they never do on a file the restore has
// just created.
var (
	chtimes = os.Chtimes
	chmod   = os.Chmod
)

// RestoreFiles restores files to dstDir with chunks index file and chunks file in srcDir.
func RestoreFiles(srcDir string, dstDir string, keepChunks bool) error {
	chunksIndexEntries, found, err := readChunksIndex(srcDir)
	if err != nil || !found {
		return err
	}

	if _, err := restoreEntries(srcDir, dstDir, chunksIndexEntries, nil); err != nil {
		return err
	}

	if keepChunks {
		log.Infof("Skipping deletion of chunk files and index file since keep-chunks is true.")
		return nil
	}

	err = DeleteChunkFilesAndIndex(srcDir)
	log.Infof("restored %d chunked files", len(chunksIndexEntries))
	return err
}

// Restored is a file restored by RestoreFilesTo.
type Restored struct {
	// TmpPath is where the file was restored, as named by tmpName.
	TmpPath string
	// Path is where the file belongs: dstDir joined with its path in the
	// chunks index.
	Path string
}

// RestoreFilesTo restores the files in the chunks index in srcDir without
// putting them in place. Each file is restored, with its mode and mtime, to
// tmpName(path), where path is its final path under dstDir, and the
// (temporary, final) pairs are returned for the caller to rename into place.
//
// tmpName must return a path, in an existing directory or one that can be
// created, that is not otherwise in use, since a failed call removes the
// files it restored. Unlike RestoreFiles, RestoreFilesTo does not delete the
// chunks or the index. It returns nothing if srcDir has no chunks index.
//
// Every path in the index must be local, as defined by filepath.IsLocal, or
// nothing is restored.
func RestoreFilesTo(srcDir, dstDir string, tmpName func(path string) string) ([]Restored, error) {
	chunksIndexEntries, found, err := readChunksIndex(srcDir)
	if err != nil || !found {
		return nil, err
	}

	restored, err := restoreEntries(srcDir, dstDir, chunksIndexEntries, tmpName)
	if err != nil {
		for _, r := range restored {
			if rmErr := os.Remove(r.TmpPath); rmErr != nil && !errors.Is(rmErr, os.ErrNotExist) {
				log.Warningf("failed to remove %s after a failed restore: %v", r.TmpPath, rmErr)
			}
		}
		return nil, err
	}
	log.Infof("restored %d chunked files", len(restored))
	return restored, nil
}

// readChunksIndex reads the chunks index in dir. found is false, with no
// error, if dir has none.
func readChunksIndex(dir string) (entries []ChunksIndex, found bool, err error) {
	indexPath, err := FindChunksIndex(dir)
	if err != nil {
		log.Infof("no chunk index file found, skip restoring chunked files")
		return nil, false, nil
	}

	index, err := os.ReadFile(indexPath)
	if err != nil {
		return nil, false, fmt.Errorf("can't read chunk index file: %v", err)
	}

	hash := sha256.Sum256(index)
	log.Infof("hash of index file: %s", hex.EncodeToString(hash[:]))

	if err := json.Unmarshal(index, &entries); err != nil {
		logFileSnippets(indexPath, index)
		return nil, false, fmt.Errorf("can't unmarshal chunk index file: %v", err)
	}
	return entries, true, nil
}

// restoreEntries restores each of chunksIndexEntries to tmpName(path), or to
// path if tmpName is nil, where path is its final path under dstDir. It
// returns the files restored so far, including when it fails.
func restoreEntries(srcDir, dstDir string, chunksIndexEntries []ChunksIndex, tmpName func(string) string) ([]Restored, error) {
	// Check every path before restoring anything, so a bad index leaves no
	// files behind. A path that is absolute or climbs out with ".." would
	// otherwise be written outside dstDir.
	for _, chunksIndex := range chunksIndexEntries {
		if !filepath.IsLocal(chunksIndex.Path) {
			return nil, fmt.Errorf("chunk index entry path %q is not a local path", chunksIndex.Path)
		}
	}

	chunksDir := filepath.Join(srcDir, ChunksDirName)
	restored := make([]Restored, 0, len(chunksIndexEntries))
	for _, chunksIndex := range chunksIndexEntries {
		finalPath := filepath.Join(dstDir, chunksIndex.Path)
		dstPath := finalPath
		if tmpName != nil {
			dstPath = tmpName(finalPath)
		}
		if chunksIndex.ModTime.IsZero() { // for backward compatibility
			// Nothing is applied to the restored file afterwards, so it may
			// share an inode with its chunk.
			if err := chunker.RestoreFile(dstPath, chunksDir, chunksIndex.Chunks); err != nil {
				return restored, err
			}
			restored = append(restored, Restored{TmpPath: dstPath, Path: finalPath})
			continue
		}
		// The mode and times below must land on a private inode. A hard link
		// to the chunk would share it with the chunk, which casdownloader
		// links into its shared cache, so the chmod and chtimes would rewrite
		// the cached blob's mode for every user and backdate its mtime so the
		// evictor drops it early.
		if err := chunker.RestoreFileCopy(dstPath, chunksDir, chunksIndex.Chunks); err != nil {
			return restored, err
		}
		restored = append(restored, Restored{TmpPath: dstPath, Path: finalPath})
		// Set the times while the file is still writable: once a read-only
		// mode is applied, utimensat can fail on some filesystems, e.g. NFS.
		if err := chtimes(dstPath, chunksIndex.ModTime, chunksIndex.ModTime); err != nil {
			return restored, fmt.Errorf("failed to set times of restored file %s: %w", dstPath, err)
		}
		if err := chmod(dstPath, chunksIndex.Mode); err != nil {
			return restored, fmt.Errorf("failed to set mode of restored file %s: %w", dstPath, err)
		}
	}
	return restored, nil
}

// FindChunksIndex returns the path of ChunksIndex file in the dir.
func FindChunksIndex(dir string) (string, error) {
	indexPath := filepath.Join(dir, ChunksDirName, ChunksIndexFileName)
	if _, err := os.Stat(indexPath); err != nil {
		indexPath = filepath.Join(dir, ChunksIndexFileName)
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
