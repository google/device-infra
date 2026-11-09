package download

import (
	"errors"
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"syscall"
	"time"

	log "github.com/golang/glog"
)

// ChunkTrashDirName is the name a DownloadJob's ChunkTrashDir must have.
// RemoveChunkTrash refuses to empty a directory with any other name.
const ChunkTrashDirName = ".chunk-trash"

// maxTrashPasses bounds how many times one removal re-reads trashDir. The
// first pass removes what was there when it started; the second catches
// entries that other downloads sharing the cache moved in while it ran,
// which matters because a download that finds a removal already running
// does not start one of its own (see ChunkTrashRemovalInProgress).
//
// The bound means that on a busy host, where downloads keep committing while
// a removal runs, an entry can arrive after the second pass and wait for the
// next download's removal, or the next trim, rather than being removed at
// the first opportunity. Without the bound a removal would instead hold the
// lock, and keep working, for as long as entries kept arriving.
//
// Leaving an entry a while longer costs little. Its files are hardlinks to
// cache blobs, so it occupies no disk blocks of its own while those blobs are
// cached; only the directory entries and the few blocks that hold them. It
// does, however, keep the blobs it links to from being evicted until it is
// removed, so what a waiting entry delays is not space, but the evictor's
// freedom to reclaim its blobs -- for the time until the next download or
// trim on the host, which on a busy host is soon.
const maxTrashPasses = 2

// ErrChunkTrashBusy reports that another process is already emptying the
// chunk trash.
var ErrChunkTrashBusy = errors.New("another process is already emptying the chunk trash")

// StuckChunkTrashAge is how old an entry a removal fails to remove must be
// before the failure is logged as an error rather than a warning. Every
// removal retries every entry, so an entry this old has survived many of
// them: something is wrong with it, not with the timing, and until it is
// removed its hardlinks pin their cache blobs against eviction.
const StuckChunkTrashAge = 24 * time.Hour

// ChunkTrashBacklog reports what is in trashDir: how many entries, and how
// long ago the oldest was moved there (its mtime, which the rename does not
// change, so this is when the chunk data was written, a little before it was
// trashed). A missing trashDir has no backlog.
//
// Measured before a download moves its own chunk data in, it is what earlier
// runs' removals have left behind: normally nothing, or the one entry a
// removal still in progress is working on. A count that grows run over run,
// or an age of hours, means an entry that removals keep failing on.
func ChunkTrashBacklog(trashDir string) (count int, oldestAge time.Duration, err error) {
	entries, err := os.ReadDir(trashDir)
	if errors.Is(err, fs.ErrNotExist) {
		return 0, 0, nil
	}
	if err != nil {
		return 0, 0, fmt.Errorf("failed to read %s: %w", trashDir, err)
	}
	now := time.Now()
	for _, e := range entries {
		count++
		info, infoErr := e.Info()
		if infoErr != nil {
			// Still counted; it just contributes no age.
			continue
		}
		if age := now.Sub(info.ModTime()); age > oldestAge {
			oldestAge = age
		}
	}
	return count, oldestAge, nil
}

// RemoveChunkTrash empties trashDir, a ChunkTrashDir that downloads move
// chunk data into: what the download that started it moved there, and
// anything earlier ones left behind, for instance because they were killed
// before it was removed. It is meant to run in a process of its own, which a
// download starts through StartChunkTrashRemoval once it has committed.
//
// It waits for the advisory lock on trashDir that serializes removals (see
// TryRemoveChunkTrash), which does not block downloads moving chunk data into
// trashDir. trashDir itself is kept, since other downloads may be moving
// chunk data into it.
func RemoveChunkTrash(trashDir string) error {
	return removeChunkTrash(trashDir, true)
}

// TryRemoveChunkTrash is RemoveChunkTrash without the wait: if another
// process is already emptying trashDir, it returns ErrChunkTrashBusy at
// once rather than queueing behind it. It is what a trim calls before
// evicting, since a removal already in progress is doing the unpinning the
// trim wants.
func TryRemoveChunkTrash(trashDir string) error {
	return removeChunkTrash(trashDir, false)
}

// ChunkTrashRemovalInProgress reports whether another process holds the lock
// on trashDir, that is, is emptying it. A missing trashDir is not in
// progress.
func ChunkTrashRemovalInProgress(trashDir string) (bool, error) {
	dirFile, err := os.Open(trashDir)
	if errors.Is(err, fs.ErrNotExist) {
		return false, nil
	}
	if err != nil {
		return false, fmt.Errorf("failed to open %s: %w", trashDir, err)
	}
	defer dirFile.Close()
	err = flockExclusive(dirFile, false)
	if errors.Is(err, syscall.EWOULDBLOCK) {
		return true, nil
	}
	if err != nil {
		return false, fmt.Errorf("failed to probe the lock on %s: %w", trashDir, err)
	}
	return false, nil
}

func removeChunkTrash(trashDir string, wait bool) error {
	if filepath.Base(trashDir) != ChunkTrashDirName {
		return fmt.Errorf("refusing to empty %s: chunk trash directories are named %s", trashDir, ChunkTrashDirName)
	}
	dirFile, err := os.Open(trashDir)
	if errors.Is(err, fs.ErrNotExist) {
		return nil
	}
	if err != nil {
		return fmt.Errorf("failed to open %s: %w", trashDir, err)
	}
	defer dirFile.Close()

	if err := flockExclusive(dirFile, wait); err != nil {
		if !wait && errors.Is(err, syscall.EWOULDBLOCK) {
			return ErrChunkTrashBusy
		}
		return fmt.Errorf("failed to lock %s: %w", trashDir, err)
	}

	start := time.Now()
	var errs []error
	failed := make(map[string]bool)
	removed := 0
	total := 0
	for pass := 0; pass < maxTrashPasses; pass++ {
		entries, err := os.ReadDir(trashDir)
		if errors.Is(err, fs.ErrNotExist) {
			break
		}
		if err != nil {
			return fmt.Errorf("failed to read %s: %w", trashDir, err)
		}
		progress := false
		for _, e := range entries {
			if failed[e.Name()] {
				continue
			}
			total++
			path := filepath.Join(trashDir, e.Name())
			if err := removeAllTrashEntry(path); err != nil {
				errs = append(errs, err)
				failed[e.Name()] = true
				logStuckEntry(path, e, err)
				continue
			}
			removed++
			progress = true
		}
		if !progress {
			break
		}
	}
	if len(errs) > 0 {
		log.Warningf("removed %d of %d entries in %s, took %s; %d could not be removed and will be retried by the next removal", removed, total, trashDir, time.Since(start), len(errs))
	} else {
		log.Infof("removed %d of %d entries in %s, took %s", removed, total, trashDir, time.Since(start))
	}
	return errors.Join(errs...)
}

// logStuckEntry logs a failure to remove a trash entry: as an error, naming
// it, if it is older than StuckChunkTrashAge, since by then removals have
// failed on it many times over, and otherwise as a warning.
func logStuckEntry(path string, e fs.DirEntry, err error) {
	if info, statErr := e.Info(); statErr == nil {
		if age := time.Since(info.ModTime()); age > StuckChunkTrashAge {
			log.Errorf("Failed to remove %s, which has been in the chunk trash for %s; its hardlinks pin their cache blobs against eviction until it is removed: %v", path, age.Truncate(time.Minute), err)
			return
		}
	}
	log.Warningf("Failed to remove %s: %v", path, err)
}

// flockExclusive takes an exclusive advisory lock on f, waiting for it if
// wait is set and otherwise failing with EWOULDBLOCK if it is held. The
// kernel drops the lock when f is closed, including when the process dies.
func flockExclusive(f *os.File, wait bool) error {
	how := syscall.LOCK_EX
	if !wait {
		how |= syscall.LOCK_NB
	}
	for {
		err := syscall.Flock(int(f.Fd()), how)
		if !errors.Is(err, syscall.EINTR) {
			return err
		}
	}
}

// removeAllTrashEntry removes path with os.RemoveAll, retrying transient
// ENOTEMPTY errors that can occur if another process (such as a failed
// download's abort) unlinks entries inside path concurrently. It is a
// variable so that tests can observe the passes.
var removeAllTrashEntry = func(path string) error {
	for range 3 {
		err := os.RemoveAll(path)
		if !errors.Is(err, syscall.ENOTEMPTY) {
			return err
		}
	}
	return os.RemoveAll(path)
}
