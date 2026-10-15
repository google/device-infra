package cache

import (
	"context"
	"fmt"
	"time"

	log "github.com/golang/glog"
	"github.com/google/device-infra/src/devtools/rbe/common/storage"
)

// DefaultTrimMaxDuration bounds a Trim when TrimOptions.MaxDuration is not
// set by the caller's flag. A full pass over a 174k-blob cache takes seconds,
// so this is a backstop against a pathological volume rather than a budget a
// healthy trim comes near.
const DefaultTrimMaxDuration = 5 * time.Minute

// TrimOptions configures Trim. The string fields are the cache flags as
// given; empty selects the defaults, as for Options.
type TrimOptions struct {
	// Dir is -cache-dir. The lock-free cache is under Dir/LayoutVersionDir.
	Dir string
	// MinFreeSpace and TargetFreeSpace are -cache-min-free-space and
	// -cache-target-free-space.
	MinFreeSpace    string
	TargetFreeSpace string
	// TrimTargetFree is -trim-target-free. If set, it replaces
	// TargetFreeSpace for this run.
	TrimTargetFree string
	// MaxDuration is -trim-max-duration. The pass stops where it is once it
	// has run this long. Zero or less means no limit.
	MaxDuration time.Duration
	// Overrides are the eviction tuning flags the caller set explicitly.
	Overrides EvictorOverrides
	// MaxSize is -cache-max-size, the advisory cap on the cache's size in
	// bytes. Zero or less means no cap.
	MaxSize int64

	// statfs replaces the real statfs, for tests.
	statfs storage.StatfsFunc
}

// Trim runs one eviction pass over the lock-free cache under opts.Dir, and
// reports what it did. It is what casdownloader -trim-only runs, as a
// short-lived process of its own, so that eviction never runs on a
// download's path.
//
// The pass takes Dir/v2/.evict.lock without blocking, and returns at once
// with TrimOutcomeBusy if another process holds it: that process is already
// doing the work. Otherwise it computes need = target - free, where target is
// the trim's target free space; does nothing if need is not positive; and
// otherwise finds a cutoff age and unlinks the blobs older than it until about
// need bytes are reclaimed or free space reaches the target. The pass stops
// where it is after opts.MaxDuration, which is safe because the cache is
// content-addressed: a blob deleted mid-run is just a miss for the next
// download.
//
// With opts.MaxSize set, every pass first checks the cache against the cap by
// reading buckets progressively, records in v2/.cap_check that it started, and
// raises need to the cache's excess over 90% of the cap if that is more. See
// storage.Storage.TrimWith.
//
// The error is non-nil only for TrimOutcomeError. A busy lock, a pass with
// nothing to do and a pass stopped by MaxDuration are all successes, and so is
// a pass whose free-space flags could not be used as given: it works toward
// the defaults or the nearest legal values instead, as lockFreeWatermarks
// logs. A -trim-target-free that does not parse is ignored, with a warning,
// so the pass works toward -cache-target-free-space, as without it.
func Trim(ctx context.Context, opts TrimOptions) (storage.TrimResult, error) {
	targetValue, targetFlag := opts.TargetFreeSpace, "-cache-target-free-space"
	if opts.TrimTargetFree != "" {
		if _, err := storage.ParseSpaceThreshold(opts.TrimTargetFree); err != nil {
			log.WarningContextf(ctx, "Ignoring -trim-target-free: %v. Using -cache-target-free-space instead.", err)
		} else {
			targetValue, targetFlag = opts.TrimTargetFree, "-trim-target-free"
		}
	}

	store, err := newLockFreeStorage(opts.Dir)
	if err != nil {
		return storage.TrimResult{Outcome: storage.TrimOutcomeError}, err
	}
	root := store.RootDir()
	// The warnings are logged, and a trim has no notes to report them in.
	cfg, _ := lockFreeEvictorConfig(root, opts.MinFreeSpace, targetValue, targetFlag, opts.Overrides, opts.statfs)
	ev, err := storage.NewEvictor(root, cfg, opts.statfs)
	if err != nil {
		return storage.TrimResult{Outcome: storage.TrimOutcomeError}, fmt.Errorf("failed to configure evictor for %s: %w", root, err)
	}
	store.SetEvictor(ev)

	if opts.MaxDuration > 0 {
		var cancel context.CancelFunc
		ctx, cancel = context.WithTimeout(ctx, opts.MaxDuration)
		defer cancel()
	}

	trimOpts := storage.TrimOptions{MaxSize: max(opts.MaxSize, 0)}
	log.InfoContextf(ctx, "Trimming the lock-free cache in %s toward %s free (trim starts below %s), %s, %s.",
		root, cfg.TargetFreeSpace, cfg.MinFreeSpace, capString(trimOpts), maxDurationString(opts.MaxDuration))
	res, err := store.TrimWith(ctx, trimOpts)
	switch res.Outcome {
	case storage.TrimOutcomeBusy:
		log.InfoContextf(ctx, "Another process is already trimming %s; leaving it to that one.", root)
	case storage.TrimOutcomeError:
		log.ErrorContextf(ctx, "Trimming %s failed after %v, having reclaimed %d bytes across %d files: %v",
			root, res.Duration, res.BytesReclaimed, res.FilesDeleted, err)
	default:
		log.InfoContextf(ctx, "Trimmed %s: outcome %s, reclaimed %d bytes across %d files in %v; left %d bytes that other links still hold.",
			root, res.Outcome, res.BytesReclaimed, res.FilesDeleted, res.Duration, res.SkippedLinked)
	}
	return res, err
}

func maxDurationString(d time.Duration) string {
	if d <= 0 {
		return "with no time limit"
	}
	return "for at most " + d.String()
}

func capString(o storage.TrimOptions) string {
	if o.MaxSize <= 0 {
		return "with no cap on its size"
	}
	return fmt.Sprintf("with a cap of %d bytes", o.MaxSize)
}
