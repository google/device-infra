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

func fileMode(output *client.TreeOutput) os.FileMode {
	if output.IsExecutable {
		return os.FileMode(0o700)
	}
	return os.FileMode(0o600)
}
