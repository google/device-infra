package cache

import (
	"context"
	"fmt"

	log "github.com/golang/glog"
)

// Options is the set of settings that decide which cache gets built.
//
// These arrived as positional parameters, which stopped being readable once
// three of them were adjacent booleans: a transposed pair would compile, and
// the resulting cache would be wrong in a way nothing would report.
type Options struct {
	// Disabled selects no cache at all. Open returns a nil Cache, which every
	// caller already has to handle.
	Disabled bool
	// LockFree selects the lock-free cache over the lock-based one.
	LockFree bool
	Dir      string
	// MaxSize and Lock apply only to the lock-based cache; the lock-free cache
	// is bounded by free space rather than by a byte cap, and takes no lock.
	MaxSize      int64
	Lock         bool
	MinFreeSpace int64
	UseHardlink  bool
	// Overrides are the eviction settings the caller set explicitly. Only the
	// lock-free cache reads them.
	Overrides EvictorOverrides
}

// OpenOrDegrade builds the cache described by opts, falling back to no cache
// rather than failing when it cannot be built.
//
// A cache that cannot be set up is a reason to download without one, not a
// reason not to download. Everything that can fail here is local to this host
// and unrelated to the artifacts being fetched: a cache directory that cannot
// be created, eviction settings the evictor rejects, a tuning file someone got
// wrong. Refusing to run leaves the caller with nothing, over a facility whose
// only job is to make the next run faster.
//
// A nil cache is what Disabled already produces, so this degrades onto a path
// the binary already takes rather than into a new one.
//
// attempt names the download this cache was for, and appears in both the log
// line and the note. The returned note is empty when the cache was built;
// otherwise the returned Cache is nil and the note is what the run should
// report about it, so that a degraded run is not silently indistinguishable
// from a healthy one.
func OpenOrDegrade(ctx context.Context, opts Options, attempt string) (Cache, string) {
	c, err := Open(opts)
	if err == nil {
		return c, ""
	}
	log.WarningContextf(ctx, "Failed to set up the local cache for %s: %v. Continuing without it.", attempt, err)
	return nil, fmt.Sprintf("Local cache unavailable for %s, downloading without it: %v", attempt, err)
}

// Open builds the cache described by opts. It returns a nil Cache and a nil
// error when opts.Disabled is set, since "no cache" is a valid configuration
// rather than a failure to produce one.
func Open(opts Options) (Cache, error) {
	if opts.Disabled {
		return nil, nil
	}
	if opts.LockFree {
		return openLockFree(opts)
	}

	localCache, err := NewLocalCache(opts.Dir, opts.MaxSize, opts.MinFreeSpace, opts.Lock, opts.UseHardlink)
	if err != nil {
		return nil, fmt.Errorf("failed to create local cache: %v", err)
	}
	return localCache, nil
}

func openLockFree(opts Options) (Cache, error) {
	cfg := NewEvictorConfig(opts.MinFreeSpace, opts.Overrides)

	lockFreeCache, err := NewLockFreeCache(opts.Dir, cfg, opts.UseHardlink)
	if err != nil {
		return nil, fmt.Errorf("failed to create lock-free cache: %v", err)
	}
	return lockFreeCache, nil
}
