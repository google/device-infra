package cache

import (
	"context"
	"fmt"
	"io"
	"os"
	"path/filepath"

	log "github.com/golang/glog"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/client"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/digest"
	"github.com/google/device-infra/src/devtools/rbe/common/storage"
)

// LayoutVersionDir is the subdirectory of -cache-dir that holds the lock-free
// cache.
//
// The two cache implementations disagree about on-disk layout: LocalCache
// stores blobs flat as <cache-dir>/<hash> alongside a state.json index, while
// common/storage sharded them as <cache-dir>/ab/cd/<hash> and keeps no index.
// Sharing one root would not corrupt anything, but it would be worse than that
// in a quiet way: the sharded evictor only ever walks the two-level ??/?? bucket
// permutation, so flat blobs left by LocalCache are never enumerated and can
// never be reclaimed. A host that switched implementations would carry its old
// cache as dead weight forever, on exactly the disks this change exists to keep
// from filling up.
//
// Keeping each layout in its own directory also makes the flag a real toggle:
// flipping it back and forth picks up a cold cache, not a corrupted one.
const LayoutVersionDir = "v2"

// Both are compile-time assertions rather than documentation. The caller finds
// EnsureHeadroom by type assertion, which fails silently: a signature that
// drifted would not break the build, it would just quietly stop reserving space
// and leave the ENOSPC this cache exists to prevent.
var (
	_ Cache            = (*LockFreeCache)(nil)
	_ HeadroomReserver = (*LockFreeCache)(nil)
)

// LockFreeCache caches downloaded blobs using common/storage.
//
// It exists to replace the whole-cache flock that LocalCache takes around every
// Push and Pull. That lock serializes every casdownloader on a host against
// every other one, and the critical section is not small: it spans loading and
// rewriting luci's state.json index, which is O(cache entries) work performed
// once per operation. common/storage needs no such lock. Content addressing
// makes blobs immutable once published, and publication is a single atomic
// link(2) or rename(2), so readers and writers never need to exclude each other.
type LockFreeCache struct {
	store *storage.Storage
	// sharesInodes records whether a materialized destination can be the same
	// inode as the cached blob. It is false for a store built
	// WithoutHardlinks, which always copies, and that is what lets
	// applyFileMode fix a mode in place instead of re-materializing the file.
	sharesInodes bool
}

// NewLockFreeCache creates a LockFreeCache rooted under cacheDir.
//
// useHardlink reports whether the cache directory and the download directory
// are on the same filesystem. It is a statement of fact about the deployment
// rather than a preference: passing false when they are on different devices
// skips a link(2) per blob that is guaranteed to fail with EXDEV, and keeps the
// copy-fallback counters meaningful. Correctness does not depend on it, because
// both directions fall back to copying on their own.
//
// cfg tunes eviction. The evictor is configured but not started: casdownloader
// is a one-shot process, so a background polling loop would outlive the process
// by microseconds. Eviction happens on demand through EnsureHeadroom.
func NewLockFreeCache(cacheDir string, cfg storage.EvictorConfig, useHardlink bool) (*LockFreeCache, error) {
	root := filepath.Join(cacheDir, LayoutVersionDir)

	var opts []storage.Option
	if !useHardlink {
		opts = append(opts, storage.WithoutHardlinks())
	}

	store, err := storage.New(root, opts...)
	if err != nil {
		return nil, fmt.Errorf("failed to create lock-free cache at %s: %w", root, err)
	}
	if err := store.ConfigureEvictor(cfg); err != nil {
		return nil, fmt.Errorf("failed to configure evictor for %s: %w", root, err)
	}

	return &LockFreeCache{store: store, sharesInodes: useHardlink}, nil
}

// EnsureHeadroom evicts, if needed, so that an upcoming write of requiredBytes
// can land without driving the filesystem below its low watermark.
//
// This is deliberately not part of the Cache interface. The bytes that exhaust
// the disk are written by the downloader into its output directory, before Push
// is ever reached, so a cache that only made room during Push would be
// repeating the mistake this cache was built to fix: luci trims from Add, which
// is to say after the files are already on disk. The caller therefore has to
// invoke this itself, ahead of the download, and discovers it via an optional
// interface assertion. LocalCache does not implement it and keeps its old
// behavior.
func (c *LockFreeCache) EnsureHeadroom(ctx context.Context, requiredBytes int64) error {
	return c.store.EnsureHeadroom(ctx, requiredBytes)
}

// Pull materializes the requested blobs from the cache into their target paths.
//
// It returns the items it satisfied and the items that were not cached. Files
// already written are removed if the call fails partway, so a failed Pull
// leaves no partial tree behind.
func (c *LockFreeCache) Pull(ctx context.Context, all []*client.TreeOutput) (cached, missed []*client.TreeOutput, err error) {
	for _, item := range all {
		hit, err := c.store.HardlinkTo(item.Digest.Hash, item.Path)
		if err != nil {
			c.removePulledFiles(cached)
			return nil, nil, fmt.Errorf("failed to materialize %s from cache: %w", item.Path, err)
		}
		if !hit {
			missed = append(missed, item)
			continue
		}
		// Record the item before adjusting its mode, not after. The file
		// exists on disk as of HardlinkTo above, so from here on it is
		// something the cleanup path has to remove; applyFileMode only
		// changes how it got there, not whether it is there.
		cached = append(cached, item)
		if err := c.applyFileMode(item); err != nil {
			c.removePulledFiles(cached)
			return nil, nil, err
		}
	}
	return cached, missed, nil
}

// applyFileMode gives a materialized file the permissions its tree node calls
// for, without ever chmod'ing a blob that other paths may share.
//
// Permissions are a property of the inode, but executability in REAPI is a
// property of the tree node: the same blob can legitimately appear as
// executable in one directory and not in another, which is not a rare corner
// once you consider how many trees contain an empty file. A hard link cannot
// represent both at once.
//
// LocalCache resolved this by calling os.Chmod on the link it had just created,
// which silently rewrote the mode of the cached blob and of every other path
// already linked to it. Last writer won, and earlier destinations changed under
// their owners' feet. That is not an option here, both because the cache is
// shared across processes and because Android artifacts are to be treated as
// read-only.
//
// So the mismatch is resolved by giving this destination an inode of its own.
// The common case costs one stat and nothing else: Push normalizes permissions
// before ingesting, so a blob used consistently one way always already carries
// the right mode.
//
// A store that cannot hard link is exempt from all of this. There HardlinkTo
// materialized the destination by copying, so it is already an inode nobody
// else refers to and a chmod changes nothing but this path. Taking the general
// route anyway would copy the file a second time to arrive at a file it already
// had, which on the cross-device deployment is the whole reason the first copy
// was expensive.
func (c *LockFreeCache) applyFileMode(item *client.TreeOutput) error {
	want := fileMode(item)

	info, err := os.Lstat(item.Path)
	if err != nil {
		return fmt.Errorf("failed to stat %s after pulling it from cache: %w", item.Path, err)
	}
	if info.Mode().Perm() == want.Perm() {
		return nil
	}

	if !c.sharesInodes {
		if err := os.Chmod(item.Path, want); err != nil {
			return fmt.Errorf("failed to set mode %#o on %s: %w", want.Perm(), item.Path, err)
		}
		return nil
	}

	if err := replaceWithPrivateCopy(item.Path, want); err != nil {
		return fmt.Errorf("failed to re-materialize %s with mode %#o: %w", item.Path, want.Perm(), err)
	}
	return nil
}

// replaceWithPrivateCopy swaps path for an independent copy of itself carrying
// perm, leaving any other links to the original inode untouched.
//
// The copy is read from path rather than from the cache, which keeps the
// operation independent of whether the blob is still cached: eviction only
// unlinks the cache's directory entry, and the file in front of us holds its
// own reference to the bytes. Writing a temporary sibling and renaming it into
// place means path is never absent, and never a truncated prefix, at any point
// a concurrent reader could observe.
func replaceWithPrivateCopy(path string, perm os.FileMode) error {
	src, err := os.Open(path)
	if err != nil {
		return err
	}
	defer src.Close()

	dir, base := filepath.Dir(path), filepath.Base(path)
	tmp, err := os.CreateTemp(dir, "."+base+".mode.*")
	if err != nil {
		return err
	}
	tmpName := tmp.Name()
	defer func() {
		tmp.Close()
		os.Remove(tmpName)
	}()

	// CreateTemp forces 0600, so set the permissions we actually want. Chmod
	// on the descriptor is not filtered through the umask the way the mode
	// argument to OpenFile would be.
	if err := tmp.Chmod(perm); err != nil {
		return err
	}
	if _, err := io.Copy(tmp, src); err != nil {
		return err
	}
	if err := tmp.Close(); err != nil {
		return err
	}
	return os.Rename(tmpName, path)
}

// removePulledFiles undoes a partially completed Pull.
func (c *LockFreeCache) removePulledFiles(cached []*client.TreeOutput) {
	if len(cached) == 0 {
		return
	}
	log.Infof("Cleanup on error: remove %d files pulled from cache", len(cached))
	for _, item := range cached {
		if err := os.Remove(item.Path); err != nil && !os.IsNotExist(err) {
			log.Errorf("failed to remove file %s: %v", item.Path, err)
		}
	}
}

// Push ingests freshly downloaded files into the cache.
//
// Ingestion is by hard link where the filesystem allows it, so the bytes are
// not written twice. That makes each cached blob and its downloaded file one
// inode, which is sound here because casdownloader's outputs are read-only
// artifacts, but it is also why permissions are normalized first: once the link
// exists the mode is shared, and it is too late to set it for this file alone.
func (c *LockFreeCache) Push(ctx context.Context, all map[digest.Digest]*client.TreeOutput) error {
	for _, item := range all {
		// Normalize before sharing. The downloaded file is still private at
		// this point, so this is the last moment at which its mode can be
		// fixed without touching anyone else's view of the blob.
		want := fileMode(item)
		if err := os.Chmod(item.Path, want); err != nil {
			return fmt.Errorf("failed to set mode %#o on %s before caching it: %w", want.Perm(), item.Path, err)
		}
		if err := c.store.HardlinkFrom(item.Digest.Hash, item.Path); err != nil {
			return fmt.Errorf("failed to cache %s: %w", item.Path, err)
		}
	}
	return nil
}

// Close reports what the cache did. There is no index to flush.
func (c *LockFreeCache) Close() error {
	log.Infof("lock-free cache: %s", c.store.Stats().Summary())
	return nil
}
