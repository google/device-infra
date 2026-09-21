package storage

import (
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"syscall"
	"time"
)

// linkFile is an indirection over os.Link so that tests can inject filesystem
// errors (EMLINK, EXDEV, EPERM) that are impractical to reproduce in a hermetic
// unit test. Production code always uses os.Link.
var linkFile = os.Link

// isCopyFallbackErr reports whether a failed link(2) should degrade to a byte
// copy rather than propagate as an error.
//
// Hard linking is an optimization, not a requirement: every condition below
// describes a filesystem that is healthy but structurally unable to create the
// link. Copying is always correct, just slower and it consumes extra space.
//
//   - EMLINK: the source inode has hit the per-inode link ceiling (65,000 on
//     ext4, 2^31 on XFS). A single popular blob fanned out into thousands of
//     workspaces can genuinely reach this on ext4.
//   - EXDEV: the cache root and the destination live on different mounts. This
//     is routine when the cache is on a dedicated data volume and the caller
//     materializes into a tmpfs or an overlayfs upper layer.
//   - EPERM: the kernel refuses the link even though the caller can read the
//     source. Linux returns this for /proc/sys/fs/protected_hardlinks=1 when
//     the caller does not own the source inode, and overlayfs returns it for
//     links that would cross the upper/lower boundary.
//   - EOPNOTSUPP / ENOSYS: the filesystem has no link support at all (some
//     FUSE and network filesystems).
//
// Anything else (ENOSPC, EIO, EROFS, EACCES on the destination directory)
// signals a genuine problem that a copy would not fix, so it is propagated.
func isCopyFallbackErr(err error) bool {
	return errors.Is(err, syscall.EMLINK) ||
		errors.Is(err, syscall.EXDEV) ||
		errors.Is(err, syscall.EPERM) ||
		errors.Is(err, syscall.EOPNOTSUPP) ||
		errors.Is(err, syscall.ENOSYS)
}

// HardlinkTo materializes the cached blob identified by hash at destPath.
//
// It reports hit=true when destPath was populated from the cache and
// hit=false, err=nil when the blob is not cached. A missing blob is never an
// error: the cache is advisory, and the caller is expected to fall back to
// fetching the blob from upstream.
//
// Materialization strategy:
//
//  1. link(2) from the sharded blob path to destPath. This is O(1) regardless
//     of blob size, consumes no additional disk space, and shares the page
//     cache with every other consumer of the same blob. It is the reason a
//     CAS cache can serve multi-gigabyte artifacts to dozens of concurrent
//     workspaces on a single host without multiplying disk usage.
//  2. If the link fails for a structural reason (see isCopyFallbackErr), fall
//     back to an atomic copy so the caller still gets its file.
//
// Concurrency and TOCTOU:
//
// HardlinkTo deliberately performs no stat-then-link sequence. The evictor
// unlinks blobs concurrently and without coordination, so any "does it exist?"
// check would be stale the instant it returned. Instead the link syscall
// itself is the existence check: the kernel resolves the source path and
// creates the link in one atomic operation, so a blob evicted a nanosecond
// earlier surfaces as ENOENT and is reported as an ordinary cache miss. A blob
// evicted a nanosecond later is harmless because the new link keeps the inode
// and its data alive until every link is gone.
//
// If destPath already exists it is replaced. Parent directories of destPath
// are created as needed.
//
// HardlinkTo does not update the blob's mtime. Recency tracking is the
// caller's decision, because the right cadence depends on the access pattern;
// callers that want LRU credit should invoke TouchIfOlderThan separately.
//
// # WARNING: destPath shares one inode with the cache blob
//
// On the hard link path destPath is not a copy, it is a second name for the
// cached blob. Writing to it, truncating it, or chmod'ing it mutates the blob
// itself and therefore every other consumer of that digest on the host, whose
// content will silently stop matching its hash. Deleting destPath is fine, and
// so is replacing it with a fresh file, since both only drop this one link.
//
// Note that a caller cannot detect which path it got: the copy fallback
// produces an independent inode, so the same call is sometimes sharing and
// sometimes not. Treat materialized files as read-only unconditionally. The
// sole exception is a Storage built WithoutHardlinks, which never shares.
func (s *Storage) HardlinkTo(hash, destPath string) (hit bool, err error) {
	src := s.blobPath(hash)
	if err := os.MkdirAll(filepath.Dir(destPath), 0755); err != nil {
		return false, fmt.Errorf("failed to create destination directory for %s: %w", destPath, err)
	}

	if s.hardlinkDisabled {
		return s.copyTo(src, destPath)
	}

	linkErr := linkFile(src, destPath)
	if linkErr != nil && errors.Is(linkErr, os.ErrExist) {
		// Replace a pre-existing destination. Unlinking first (rather than
		// renaming over it) is safe here because destPath belongs to the
		// caller's workspace, not to the shared cache.
		if rmErr := os.Remove(destPath); rmErr != nil && !errors.Is(rmErr, os.ErrNotExist) {
			return false, fmt.Errorf("failed to replace existing destination %s: %w", destPath, rmErr)
		}
		linkErr = linkFile(src, destPath)
	}

	switch {
	case linkErr == nil:
		s.counters.hardlinkHits.Add(1)
		return true, nil
	case errors.Is(linkErr, os.ErrNotExist):
		// Either the blob was never cached or it was evicted mid-flight. Both
		// are plain cache misses.
		s.counters.hardlinkMisses.Add(1)
		return false, nil
	case !isCopyFallbackErr(linkErr):
		return false, fmt.Errorf("failed to hard link %s to %s: %w", src, destPath, linkErr)
	}

	// TODO: amortize the EMLINK fallback with a per-run overflow copy.
	// casdownloader materializes the undeduped output list, so a blob that has
	// reached the ext4 link ceiling and appears at N paths in one artifact set
	// costs N full copies today; this has been observed on lab test hosts.
	// Copying it once to a temporary sibling, linking the remaining destinations
	// from that copy, and unlinking the temporary name at the end of the run
	// would cost one copy instead, since the destination links keep the inode
	// alive after its name is gone. Two things to watch: a temporary sibling is
	// invisible to the evictor (isCandidateBlob skips dotted names), so a crash
	// mid-run leaks a full-size file and the change needs a sweeper; and the
	// overflow copy can itself reach the ceiling, so minting another has to be a
	// loop rather than a one-shot. Whether casproxy hosts need this is still
	// open. Defer until the current implementation plan is finished, and use the
	// CopyFallbackEMLINK counter to confirm it is worth the complexity.
	hit, copyErr := s.copyTo(src, destPath)
	if copyErr != nil {
		return false, fmt.Errorf("failed to hard link %s to %s (%v) and copy fallback failed: %w", src, destPath, linkErr, copyErr)
	}
	if hit {
		// Counted only now that a copy has actually landed, so the counter and
		// its one-time warning never describe work that did not happen.
		s.noteCopyFallback(linkErr, src, destPath)
	}
	return hit, nil
}

// copyTo materializes the blob at src onto destPath by copying, and records
// the cache hit or miss.
//
// It deliberately does not record a copy fallback. Whether copying was a
// surprise worth reporting or simply the configuration the caller declared via
// WithoutHardlinks is not something this function can know, so that judgment
// stays with its callers.
func (s *Storage) copyTo(src, destPath string) (hit bool, err error) {
	if err := copyFileAtomic(src, destPath); err != nil {
		if errors.Is(err, os.ErrNotExist) {
			// The blob is not cached, or was evicted before the copy could
			// open it. Either way nothing was copied and this is an ordinary
			// miss rather than an error.
			s.counters.hardlinkMisses.Add(1)
			return false, nil
		}
		return false, err
	}
	s.counters.hardlinkHits.Add(1)
	return true, nil
}

// HardlinkFrom ingests the file at srcPath into the cache under hash.
//
// It is the write-side counterpart to HardlinkTo and is intended for callers
// that have just produced a blob on disk (a completed download, an extracted
// archive member) and want it cached without paying to write the bytes twice.
// If the blob is already cached the call is a no-op.
//
// The link is created directly at the final blob path. No temporary file is
// involved, because none is needed: link(2) publishes the directory entry in a
// single atomic step, and srcPath is already a complete file by the time a
// caller has a digest for it. A concurrent reader therefore sees either no
// entry at all or the finished blob, never a prefix. The copy fallback below
// is the only path that needs a temporary name, since it materializes bytes
// incrementally and a reader could otherwise observe a truncated file.
//
// Losing a race to another process is not an error. If the blob path already
// exists, some other ingester published the same digest first; content
// addressing makes their entry interchangeable with ours, so the call reports
// success and leaves their inode in place rather than replacing it.
//
// The caller is responsible for having verified that the contents of srcPath
// actually hash to hash. HardlinkFrom does not re-digest the file.
//
// # WARNING: the cache blob and srcPath share one inode
//
// After a successful link the cache entry and the caller's file are the same
// inode, not two copies. Two consequences follow, and callers must be able to
// live with both:
//
//   - Mutating srcPath in place silently corrupts the cache. Every future
//     reader of hash will observe the mutated bytes even though they no longer
//     match the digest. Only ingest files that are immutable from this point
//     on. Callers that cannot guarantee immutability must copy instead.
//   - HardlinkFrom bumps srcPath's mtime to the current time. This is
//     deliberate and unavoidable: mtime is a property of the shared inode, and
//     the cache uses mtime as its LRU timestamp. Skipping the update would let
//     a file with an old mtime (an artifact restored from a build cache, a
//     checked-out source file) enter the cache already older than the
//     evictor's cutoff and be deleted on the very next sweep, which would make
//     ingestion pure waste. Callers whose build system infers staleness from
//     the mtime of the ingested file should copy rather than ingest.
//
// When the filesystem cannot create the link (see isCopyFallbackErr) the file
// is copied instead, which sidesteps both caveats at the cost of the extra
// I/O. A Storage built WithoutHardlinks always takes that path, so neither
// caveat applies to it.
func (s *Storage) HardlinkFrom(hash, srcPath string) error {
	// A cheap pre-check that skips the mkdir and the syscall in the common
	// case. It is only an optimization; the link below re-detects an existing
	// blob via EEXIST, so a blob that appears after this returns false is
	// still handled correctly.
	if s.Has(hash) {
		s.counters.ingestDeduped.Add(1)
		return nil
	}

	blob := s.blobPath(hash)
	if err := os.MkdirAll(filepath.Dir(blob), 0755); err != nil {
		return fmt.Errorf("failed to create cache directory for %s: %w", hash, err)
	}

	// copyIntoCache publishes srcPath at the blob path by copying. It is the
	// one path that materializes the blob incrementally, so it writes to a
	// temporary name and renames it into place; without that, a concurrent
	// reader could open the blob path mid-copy and read a truncated prefix as
	// if it were the whole digest.
	copyIntoCache := func() error {
		return s.StoreFileFunc(hash, func(tmpPath string) error {
			return copyFile(srcPath, tmpPath)
		})
	}

	if s.hardlinkDisabled {
		if err := copyIntoCache(); err != nil {
			return fmt.Errorf("failed to copy %s into cache: %w", srcPath, err)
		}
		// A fresh copy already carries the current time as its mtime.
		s.counters.ingested.Add(1)
		return nil
	}

	linkErr := linkFile(srcPath, blob)
	switch {
	case linkErr == nil:
		// Reset the LRU timestamp on the shared inode. A failure here is not
		// fatal: the blob is still valid and correct, it just risks being
		// evicted sooner than it deserves.
		now := time.Now()
		_ = os.Chtimes(blob, now, now)

	case errors.Is(linkErr, os.ErrExist):
		// Another ingester published this digest between the Has check and
		// the link. Their blob is byte-identical to ours, so leave it alone.
		s.counters.ingestDeduped.Add(1)
		return nil

	case !isCopyFallbackErr(linkErr):
		return fmt.Errorf("failed to hard link %s into cache: %w", srcPath, linkErr)

	default:
		if err := copyIntoCache(); err != nil {
			return fmt.Errorf("failed to hard link %s into cache (%v) and copy fallback failed: %w", srcPath, linkErr, err)
		}
		// Counted only now that a copy has actually landed, so the counter and
		// its one-time warning never describe work that did not happen.
		s.noteCopyFallback(linkErr, srcPath, blob)
		// A fresh copy already carries the current time as its mtime.
	}

	s.counters.ingested.Add(1)
	return nil
}

// copyFile copies srcPath onto destPath, creating or truncating destPath and
// mirroring the source's permission bits. destPath is written in place, so it
// must be a location where a partially written file is acceptable (such as a
// temporary path that a caller will later rename).
func copyFile(srcPath, destPath string) error {
	src, err := os.Open(srcPath)
	if err != nil {
		return err
	}
	defer src.Close()

	info, err := src.Stat()
	if err != nil {
		return err
	}

	dst, err := os.OpenFile(destPath, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, info.Mode().Perm())
	if err != nil {
		return err
	}
	defer dst.Close()

	// O_CREATE filters the mode through the process umask, so the bits above
	// are a request, not a guarantee. Set them explicitly: the hard link path
	// reproduces the source's permissions exactly, and a copy that silently
	// dropped group or other bits would make the result depend on which of the
	// two paths happened to run.
	if err := dst.Chmod(info.Mode().Perm()); err != nil {
		return err
	}

	if _, err := io.Copy(dst, src); err != nil {
		return err
	}
	return dst.Close()
}

// copyFileAtomic copies srcPath to a temporary sibling of destPath and renames
// it into place, so readers of destPath observe either the old file or the
// complete new one and never a truncated prefix.
//
// Note that once copyFile has opened the source the copy is immune to
// concurrent eviction: unlink(2) only removes the directory entry, and the
// kernel keeps the inode and its data alive for the lifetime of the open
// descriptor. A source that is already gone surfaces as os.ErrNotExist, which
// HardlinkTo reports as an ordinary cache miss.
func copyFileAtomic(srcPath, destPath string) error {
	// filepath.Dir yields "." for a bare filename, which keeps the temporary
	// file in the same directory as destPath and therefore keeps the rename
	// below on one filesystem.
	dir, base := filepath.Dir(destPath), filepath.Base(destPath)
	tmp, err := os.CreateTemp(dir, "."+base+".tmp.*")
	if err != nil {
		return err
	}
	tmpName := tmp.Name()
	// Only the name is needed from here on; copyFile opens the path itself and
	// fixes up the 0600 that CreateTemp forces.
	tmp.Close()
	defer os.Remove(tmpName)

	if err := copyFile(srcPath, tmpName); err != nil {
		return err
	}
	return os.Rename(tmpName, destPath)
}
