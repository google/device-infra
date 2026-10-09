// Package cache is a utility to support file caching.
package cache

import (
	"context"
	"os"

	"github.com/bazelbuild/remote-apis-sdks/go/pkg/client"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/digest"
	"github.com/google/device-infra/src/devtools/rbe/common/storage"
)

// Cache supports caching files with underlying various cache solution.
type Cache interface {
	Push(context.Context, map[digest.Digest]*client.TreeOutput) error
	Pull(context.Context, []*client.TreeOutput) ([]*client.TreeOutput, []*client.TreeOutput, error)
	Close() error
}

// HeadroomReserver is implemented by caches that can make room on the
// filesystem before a download, rather than reacting once it has landed.
//
// It is deliberately separate from Cache. The two are not the same concern:
// Cache is about storing and retrieving blobs, while this is about the disk
// they share with the download directory, and only some implementations have
// any control over it. Keeping it out of Cache lets a caller opt in by type
// assertion and lets implementations that cannot honor it simply not claim to.
type HeadroomReserver interface {
	// EnsureHeadroom evicts if needed so that an upcoming write of
	// requiredBytes can complete without exhausting the filesystem. It returns
	// an error only when the write cannot possibly succeed.
	EnsureHeadroom(ctx context.Context, requiredBytes int64) error
}

// CorruptBlobReporter is implemented by caches that check each hit and discard
// cached blobs that no longer match their digest. Like HeadroomReserver it is
// separate from Cache, so that a caller finds it by type assertion and
// LocalCache, which does no such check, does not have to claim it.
type CorruptBlobReporter interface {
	// CorruptBlobsQuarantined returns how many hits this cache has found
	// corrupt and turned into misses since it was created.
	CorruptBlobsQuarantined() int64
}

// ModeCopyStats counts the hits whose cached blob did not carry the canonical
// permission bits the tree node calls for, by what the cache did about it.
type ModeCopyStats struct {
	// ExecMismatchCopies are hits on a blob whose executable bit differs from
	// the tree node's, which cost a private copy of the blob: the same
	// content is used as executable in one tree and not in another, or a
	// consumer flipped the bit through a hard link.
	ExecMismatchCopies int64
	ExecMismatchBytes  int64
	// PermDriftHits are hits on a blob whose executable bit matches but whose
	// other permission bits are not canonical, which only happens when
	// something chmod'ed a materialized file and, through the shared inode,
	// the cached blob with it. They are linked as is, without a copy.
	PermDriftHits int64
	// UnreadableRepairs are hits on a blob its owner could not read, chmod'ed
	// back to the canonical mode in place before use.
	UnreadableRepairs int64
}

// ModeCopyReporter is implemented by caches that give a hit its own inode when
// the cached blob's mode does not match. Like CorruptBlobReporter it is found
// by type assertion, so LocalCache, which chmods the shared inode instead, does
// not have to claim it.
type ModeCopyReporter interface {
	// ModeCopies returns the mode-mismatch copies made since the cache was
	// created.
	ModeCopies() ModeCopyStats
}

// StorageStatsReporter is implemented by caches built on common/storage. It
// exposes that package's counters, chiefly copy fallbacks and headroom
// eviction, which is how a cache shared by many processes on one disk shows
// whether it is keeping up.
type StorageStatsReporter interface {
	// StorageStats returns the storage counters accumulated since the cache
	// was created.
	StorageStats() storage.Stats
}

// Cache implementations as reported by Impl. They are values of a metric
// field, so an existing value should not be renamed.
const (
	ImplNone     = "none"
	ImplLuci     = "luci"
	ImplLockFree = "lockfree"
	ImplOther    = "other"
)

// Impl names the implementation of c, or ImplNone when there is no cache.
func Impl(c Cache) string {
	switch c.(type) {
	case nil:
		return ImplNone
	case *LocalCache:
		return ImplLuci
	case *LockFreeCache:
		return ImplLockFree
	default:
		return ImplOther
	}
}

// The canonical modes of casdownloader's files, by the executable bit of their
// tree node. REAPI specifies only that bit, so these fix the rest: every file
// casdownloader materializes or caches gets exactly one of them, on every path,
// independent of the umask. They are the modes LocalCache has always given
// files, so switching between the two caches does not change what a
// destination looks like, and both share this one definition.
const (
	ExecutableMode = os.FileMode(0o750)
	RegularMode    = os.FileMode(0o640)
)

// FileMode returns the canonical mode of a file whose tree node has the given
// executable bit.
func FileMode(executable bool) os.FileMode {
	if executable {
		return ExecutableMode
	}
	return RegularMode
}

// IsExecutableMode reports whether perm marks a file executable in the sense
// CAS trees use: the REAPI SDK sets a FileNode's is_executable from the owner
// execute bit when it builds a tree (filemetadata.Compute), so that is the bit
// that has to agree. It is also the bit that matters when, as in the lab,
// every process runs as the owner.
func IsExecutableMode(perm os.FileMode) bool {
	return perm&0o100 != 0
}

func fileMode(output *client.TreeOutput) os.FileMode {
	return FileMode(output.IsExecutable)
}
