// Package cache is a utility to support file caching.
package cache

import (
	"context"
	"os"

	"github.com/bazelbuild/remote-apis-sdks/go/pkg/client"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/digest"
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

// ModeCopyStats counts the hits that could not share the cached inode because
// its permissions did not match what the tree node asked for, and so cost a
// private copy of the blob. Each such hit is counted under exactly one cause.
type ModeCopyStats struct {
	// ExecMismatchCopies are hits on a blob that carried a canonical mode
	// for the other executability: the same content is used as executable
	// in one tree and not in another.
	ExecMismatchCopies int64
	ExecMismatchBytes  int64
	// PermDriftCopies are hits on a blob whose mode was not canonical at
	// all, which only happens when something chmod'ed a materialized file
	// and, through the shared inode, the cached blob with it.
	PermDriftCopies int64
	PermDriftBytes  int64
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

func fileMode(output *client.TreeOutput) os.FileMode {
	if output.IsExecutable {
		return os.FileMode(0o750)
	}
	return os.FileMode(0o640)
}
