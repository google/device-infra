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

func fileMode(output *client.TreeOutput) os.FileMode {
	if output.IsExecutable {
		return os.FileMode(0o750)
	}
	return os.FileMode(0o640)
}
