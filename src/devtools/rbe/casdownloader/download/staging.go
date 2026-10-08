package download

import (
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	log "github.com/golang/glog"

	"github.com/bazelbuild/remote-apis-sdks/go/pkg/client"
	"github.com/google/device-infra/src/devtools/rbe/casuploader/chunkerutil"
)

// maxNameLen is the longest file name, NAME_MAX, that Linux filesystems
// accept.
const maxNameLen = 255

// stagedFile is a file or symlink written under a temporary name, to be
// renamed to its final path once the whole download has succeeded.
type stagedFile struct {
	tmp   string
	final string
}

// staging tracks what one DoDownload attempt writes into Dir, so that a
// failed attempt can remove all of it, leaving whatever was in Dir before
// untouched, and a successful one can put it in place.
//
// Files and symlinks are written under temporary names, unique to the
// attempt, next to their final paths, and renamed into place by commit. Until
// then nothing at a final path has been touched: not truncated, not written
// through (it may be a hard link to a cache blob), not removed.
//
// The chunk data of a chunked artifact, _chunks and the legacy root
// _chunks_index.json, keeps its real names: casuploader reserves them, so
// they are not part of what the caller keeps, and chunkerutil finds them
// there. Instead the attempt claims them by creating _chunks, and fails if
// either already exists. Only an earlier -chunks-only run, whose output is
// meant to be mounted rather than downloaded into again, or a run that
// crashed before it could clean up, leaves them behind.
type staging struct {
	dir   string
	runID string
	files []stagedFile
	// committed counts the files commit has renamed into place.
	committed int
	// createdDirs lists the directories this attempt created, each after
	// its parent.
	createdDirs []string
	// chunksClaimed is set once this attempt has created Dir/_chunks.
	chunksClaimed bool
	// disableOverwrite makes commit fail on a final path that is taken,
	// rather than replace what is there.
	disableOverwrite bool
	// replaced lists the final paths at which commit replaced something.
	replaced []string
}

func newStaging(dir string) (*staging, error) {
	b := make([]byte, 6)
	if _, err := rand.Read(b); err != nil {
		return nil, fmt.Errorf("failed to generate a run ID: %w", err)
	}
	// Cleaned, since paths derived from it, such as those mkdirAll records,
	// are compared with it: with "out/", abort would otherwise remove "out".
	return &staging{dir: filepath.Clean(dir), runID: hex.EncodeToString(b)}, nil
}

// tmpName returns the temporary name, in the same directory, under which this
// attempt writes the file that belongs at final.
//
// The run ID keeps attempts on the same Dir, concurrent or one after a
// crashed one, from colliding. A name that would be too long uses a hash of
// the base name instead.
func (s *staging) tmpName(final string) string {
	base := filepath.Base(final)
	name := ".tmp." + s.runID + "." + base
	if len(name) > maxNameLen {
		sum := sha256.Sum256([]byte(base))
		name = ".tmp." + s.runID + "." + hex.EncodeToString(sum[:16])
	}
	return filepath.Join(filepath.Dir(final), name)
}

// isChunkData reports whether path is chunk data reserved by casuploader.
func (s *staging) isChunkData(path string) bool {
	return path == filepath.Join(s.dir, chunkerutil.ChunksIndexFileName) ||
		strings.HasPrefix(path, filepath.Join(s.dir, chunkerutil.ChunksDirName)+string(filepath.Separator))
}

// stage points every file and symlink in outputs at its temporary name and
// records it to commit. Directories and chunk data keep their paths. It
// reports whether outputs hold chunk data, which the attempt must claim
// before writing any.
func (s *staging) stage(outputs []*client.TreeOutput) (hasChunkData bool) {
	for _, o := range outputs {
		switch {
		case o.IsEmptyDirectory:
		case s.isChunkData(o.Path):
			hasChunkData = true
		default:
			tmp := s.tmpName(o.Path)
			s.files = append(s.files, stagedFile{tmp: tmp, final: o.Path})
			o.Path = tmp
		}
	}
	return hasChunkData
}

// add records a file written at tmp, outside stage, to commit to final.
func (s *staging) add(tmp, final string) {
	s.files = append(s.files, stagedFile{tmp: tmp, final: final})
}

// mkdirAll is os.MkdirAll, recording the directories it creates so that a
// failed attempt can remove them.
func (s *staging) mkdirAll(dir string) error {
	var missing []string
	for d := dir; ; {
		if _, err := os.Lstat(d); !errors.Is(err, os.ErrNotExist) {
			break
		}
		missing = append(missing, d)
		parent := filepath.Dir(d)
		if parent == d {
			break
		}
		d = parent
	}
	err := os.MkdirAll(dir, 0o700)
	// Record what exists now, even after an error part way.
	for i := len(missing) - 1; i >= 0; i-- {
		if _, statErr := os.Lstat(missing[i]); statErr == nil {
			s.createdDirs = append(s.createdDirs, missing[i])
		}
	}
	return err
}

// claimChunkData reserves Dir/_chunks and the legacy Dir/_chunks_index.json
// for this attempt, failing if either already exists. Creating the directory
// is atomic, so of two attempts on the same Dir only one can claim it.
func (s *staging) claimChunkData() error {
	if err := s.mkdirAll(s.dir); err != nil {
		return fmt.Errorf("failed to create the root directory: %w", err)
	}
	legacyIndex := filepath.Join(s.dir, chunkerutil.ChunksIndexFileName)
	if _, err := os.Lstat(legacyIndex); err == nil {
		return fmt.Errorf("%s already exists; it is reserved for chunk data, which an earlier run, such as one with -chunks-only, left behind", legacyIndex)
	} else if !errors.Is(err, os.ErrNotExist) {
		return fmt.Errorf("failed to check for %s: %w", legacyIndex, err)
	}
	chunksDir := filepath.Join(s.dir, chunkerutil.ChunksDirName)
	if err := os.Mkdir(chunksDir, 0o700); err != nil {
		if errors.Is(err, os.ErrExist) {
			return fmt.Errorf("%s already exists; it is reserved for chunk data, which an earlier run, such as one with -chunks-only, left behind", chunksDir)
		}
		return fmt.Errorf("failed to create %s: %w", chunksDir, err)
	}
	s.chunksClaimed = true
	return nil
}

// deleteChunkData removes the chunk data this attempt claimed, if any.
func (s *staging) deleteChunkData() error {
	if !s.chunksClaimed {
		return nil
	}
	return chunkerutil.DeleteChunkFilesAndIndex(s.dir)
}

// existsError is the error for a final path that is already taken when
// overwriting is disabled.
func existsError(final string) error {
	return fmt.Errorf("%s already exists, and overwriting it is disabled by -disable-overwrite", final)
}

// checkNoneExist fails if anything is at the final path of a staged file, so
// that with overwriting disabled a conflict fails the attempt before it
// fetches anything. It is advisory: something can appear at a final path
// later, which commit detects.
func (s *staging) checkNoneExist() error {
	for _, f := range s.files {
		if _, err := os.Lstat(f.final); err == nil {
			return existsError(f.final)
		}
	}
	return nil
}

// linkNoReplace is os.Link, replaceable by tests.
var linkNoReplace = os.Link

// moveIntoPlace moves tmp to final. If something is already at final, it
// replaces it, and reports that it did, unless s.disableOverwrite is set, in
// which case it fails and leaves both in place.
//
// Whether final exists is decided atomically by hard linking tmp to it, which
// fails if final exists; renameat2(RENAME_NOREPLACE) would do the same in one
// step, but it is specific to Linux and not in package syscall. Where linking
// fails for another reason, such as a filesystem without hard links, or a tmp
// that is a cache blob with as many links as the filesystem allows, it falls
// back to checking final first, which a concurrent writer can race.
func (s *staging) moveIntoPlace(tmp, final string) (replaced bool, err error) {
	err = linkNoReplace(tmp, final)
	switch {
	case err == nil:
		// final is now another name for tmp.
		return false, os.Remove(tmp)
	case errors.Is(err, os.ErrExist):
		replaced = true
	default:
		if _, statErr := os.Lstat(final); statErr == nil {
			replaced = true
		}
	}
	if replaced && s.disableOverwrite {
		return false, existsError(final)
	}
	if err := os.Rename(tmp, final); err != nil {
		return false, err
	}
	// rename(2) does nothing, successfully, when both names are links to the
	// same inode, as when a re-download links the same cache blob that is
	// already at the final path. The temporary name is then still there;
	// after an effective rename it is not.
	os.Remove(tmp)
	return replaced, nil
}

// commit moves every staged file to its final path. What is already at a
// final path is replaced, and recorded in s.replaced, unless
// s.disableOverwrite is set, in which case commit fails on it. Each file is
// moved atomically, but the files are not moved together: if one fails, the
// files already moved stay in place.
func (s *staging) commit() error {
	for i := s.committed; i < len(s.files); i++ {
		f := s.files[i]
		replaced, err := s.moveIntoPlace(f.tmp, f.final)
		if err != nil {
			return fmt.Errorf("failed to move %s into place, after moving %d of %d files: %w", f.final, s.committed, len(s.files), err)
		}
		if replaced {
			s.replaced = append(s.replaced, f.final)
		}
		s.committed++
	}
	log.Infof("moved %d downloaded files into place, replacing %d existing files", len(s.files), len(s.replaced))
	return nil
}

// abort removes what this attempt wrote and has not committed: staged files,
// the chunk data it claimed, and the directories it created that are now
// empty, other than Dir itself. Removal is best effort.
func (s *staging) abort() {
	removed := 0
	for _, f := range s.files[s.committed:] {
		if err := os.Remove(f.tmp); err == nil {
			removed++
		} else if !errors.Is(err, os.ErrNotExist) {
			log.Errorf("failed to remove %s: %v", f.tmp, err)
		}
	}
	if err := s.deleteChunkData(); err != nil {
		log.Errorf("failed to remove chunk data: %v", err)
	}
	// Children were recorded after their parents. A directory that is not
	// empty, because something else has written into it, stays.
	for i := len(s.createdDirs) - 1; i >= 0; i-- {
		if d := s.createdDirs[i]; d != s.dir {
			os.Remove(d)
		}
	}
	log.Infof("Cleanup on error: removed %d staged files", removed)
}
