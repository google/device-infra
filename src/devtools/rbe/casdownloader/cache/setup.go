package cache

import (
	"context"
	"fmt"
	"time"

	log "github.com/golang/glog"
	"github.com/google/device-infra/src/devtools/rbe/common/storage"
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
	// MaxSize bounds the logical size of the lock-based cache. For the
	// lock-free cache it is the advisory cap, which a trim checks and
	// enforces; see storage.Storage.TrimWith. Lock applies only to the
	// lock-based cache; the lock-free cache takes no lock.
	MaxSize int64
	Lock    bool
	// MinFreeSpace is -cache-min-free-space and TargetFreeSpace is
	// -cache-target-free-space, as given; empty selects the mode's default.
	// The lock-based cache takes a byte count and reads only MinFreeSpace.
	// The lock-free cache also takes a percentage of the volume; see
	// lockFreeWatermarks.
	MinFreeSpace    string
	TargetFreeSpace string
	UseHardlink     bool
	// Overrides are the eviction settings the caller set explicitly. Only the
	// lock-free cache reads them.
	Overrides EvictorOverrides
	// CapCheckInterval is -cache-cap-check-interval; zero or less means
	// DefaultCapCheckInterval. Only the lock-free cache's open-time check
	// reads it.
	CapCheckInterval time.Duration
	// StartTrims enables the lock-free cache's open-time check: when the
	// cache is opened and needs a trim, a casdownloader -trim-only process
	// is started in the background for it. See maybeStartTrim. It also
	// enables the ENOSPC hook, which starts the same process when the
	// download runs out of space; see HandleOutOfSpace. Downloads set it;
	// it is off otherwise, so that opening a cache for any other reason,
	// such as in a test, never starts a process.
	StartTrims bool
	// Streamz is -enable-streamz and MurdockAddr the murdockd address the
	// download resolved, from -murdock-addr or MURDOCK_ADDR; empty means the
	// default. A trim the download starts reports to the same place, so that
	// the evictor's metrics are on whenever the download's are.
	Streamz     bool
	MurdockAddr string

	// statfs and spawn replace the real statfs and the real process start
	// in the open-time check, for tests.
	statfs storage.StatfsFunc
	spawn  trimSpawner
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

	localCache, err := openLocal(opts)
	if err != nil {
		return nil, fmt.Errorf("failed to create local cache: %v", err)
	}
	return localCache, nil
}

func openLocal(opts Options) (*LocalCache, error) {
	minFreeSpace, err := parseLegacyMinFreeSpace(opts.MinFreeSpace)
	if err != nil {
		return nil, err
	}
	return NewLocalCache(opts.Dir, opts.MaxSize, minFreeSpace, opts.Lock, opts.UseHardlink)
}

func openLockFree(opts Options) (Cache, error) {
	cfg, err := lockFreeEvictorConfig(opts.Dir, opts.MinFreeSpace, opts.TargetFreeSpace, "-cache-target-free-space", opts.Overrides, opts.statfs)
	if err != nil {
		return nil, fmt.Errorf("failed to create lock-free cache: %v", err)
	}

	lockFreeCache, err := NewLockFreeCache(opts.Dir, cfg, opts.UseHardlink)
	if err != nil {
		return nil, fmt.Errorf("failed to create lock-free cache: %v", err)
	}
	if opts.StartTrims {
		root := lockFreeCache.store.RootDir()
		maybeStartTrim(opts, cfg, root)
		// The ENOSPC hook starts the same trim, without the open-time
		// check; see HandleOutOfSpace.
		lockFreeCache.startEvictor = func(why string) trimStart {
			return startTrim(opts, root, why)
		}
	}
	return lockFreeCache, nil
}
