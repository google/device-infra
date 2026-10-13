package cache

import (
	"errors"
	"fmt"
	"strings"
	"sync/atomic"
	"syscall"

	log "github.com/golang/glog"
	"google.golang.org/grpc/status"
)

// outOfSpaceText is how ENOSPC reads once an error has been flattened into
// text: syscall.ENOSPC prints as "no space left on device".
const outOfSpaceText = "no space left on device"

// IsOutOfSpace reports whether err says that this host's disk is full.
//
// That is ENOSPC anywhere in err's chain or, because not every error on the
// download path keeps its chain, "no space left on device" in its text: the
// libraries the fetch and the restore go through wrap with %v in places, or
// rebuild errors from strings.
//
// An error that carries a gRPC status is matched by its chain only, never by
// its text: text in a status came from the server, and a server that is out
// of space is a remote failure, which another remote may well get past.
func IsOutOfSpace(err error) bool {
	if err == nil {
		return false
	}
	if errors.Is(err, syscall.ENOSPC) {
		return true
	}
	if _, ok := status.FromError(err); ok {
		return false
	}
	return strings.Contains(strings.ToLower(err.Error()), outOfSpaceText)
}

// OutOfSpaceHandler is implemented by caches that can free disk space when a
// download runs out of it. Like CorruptBlobReporter it is found by type
// assertion, so a cache that cannot, such as LocalCache, does not have to
// claim it.
type OutOfSpaceHandler interface {
	// OnOutOfSpace is told that the download failed with err, which
	// IsOutOfSpace has matched. It must return at once and cannot fail: the
	// caller returns err right after it.
	OnOutOfSpace(err error)
}

// OutOfSpaceStats is what HandleOutOfSpace has done in this process, for the
// metrics b/572891754 adds in a later change.
type OutOfSpaceStats struct {
	// Errors counts the out-of-space errors HandleOutOfSpace was given.
	Errors int64
	// TrimStarted reports whether a lock-free cache started the detached
	// evictor for one of them. The hook tries at most once per process.
	TrimStarted bool
	// TrimBusy reports whether the hook's one try found another process
	// trimming the cache already, holding its eviction lock, and so
	// started nothing.
	TrimBusy bool
}

// outOfSpace is HandleOutOfSpace's state. It is per process rather than per
// cache: a run can open its cache twice, as main does to retry directly after
// casproxy fails, and the hook still tries to start the evictor only once.
var outOfSpace struct {
	errors atomic.Int64
	// trimClaimed is set by the first OnOutOfSpace that tries to start the
	// evictor, whatever came of it, so that no later one tries again.
	trimClaimed atomic.Bool
	trimStarted atomic.Bool
	trimBusy    atomic.Bool
}

// OutOfSpace returns what HandleOutOfSpace has done in this process so far.
func OutOfSpace() OutOfSpaceStats {
	return OutOfSpaceStats{
		Errors:      outOfSpace.errors.Load(),
		TrimStarted: outOfSpace.trimStarted.Load(),
		TrimBusy:    outOfSpace.trimBusy.Load(),
	}
}

// HandleOutOfSpace is the ENOSPC hook. The download calls it with the error
// of a failed fetch or chunk restore, and LockFreeCache.Push with its own. It
// reports whether err is an out-of-space error (see IsOutOfSpace) and, if it
// is, counts it and hands it to c if c is an OutOfSpaceHandler. c may be nil.
// For a lock-free cache opened by a download, that starts the detached
// evictor, the same casdownloader -trim-only the open-time check starts,
// without asking whether a trim is needed; see LockFreeCache.OnOutOfSpace.
// The evictor measures the cache itself, and evicts toward the target if free
// space is below it when it looks.
//
// Known gap, accepted for now: the evictor may find nothing to do. When a
// fetch or restore fails, the download deletes its partial files right after
// this hook returns, which usually gives back the space that ran out. If free
// space was at or above the target before the download, as when a very large
// artifact, several concurrent downloads or the tests filled the disk, the
// evictor then sees a healthy volume and evicts nothing, and the Android
// Build fallback may hit ENOSPC on the same disk. A failed Push is not
// affected: its files stay. If metrics show this matters, the hook can raise
// the target for that one run with -trim-target-free (b/572891754).
//
// It never waits and never fails. The caller returns err unchanged right
// after, so that the download fails at once and Tradefed falls back to
// Android Build. There is no retry, no trim in this process, and no waiting
// for a trim, because:
//
//   - ENOSPC is rare, fewer than 20 times a week as measured for
//     b/572891754, and Tradefed's fallback already recovers from it.
//   - Starting the evictor reuses the one eviction path the cache has, with
//     its lock, its time bound and its log, instead of adding a second one
//     on a download's path.
//   - It can only be best effort. Space freed now cannot help the download
//     that failed: casdownloader does not retry it, and Tradefed moves on to
//     Android Build. Nor is recovery guaranteed: blobs can be pinned by
//     links outside the cache, the space can be used by something else, and
//     the fallback writes to the same disk. The evictor only aims to keep
//     later downloads from failing the same way.
func HandleOutOfSpace(c Cache, err error) bool {
	if !IsOutOfSpace(err) {
		return false
	}
	outOfSpace.errors.Add(1)
	if h, ok := c.(OutOfSpaceHandler); ok {
		h.OnOutOfSpace(err)
	}
	return true
}

// OnOutOfSpace starts the detached evictor for the cache through startTrim,
// unless the hook already tried in this process, and records whether it
// started one or found one running. A cache opened without
// Options.StartTrims starts nothing, like its open-time check. See
// HandleOutOfSpace.
func (c *LockFreeCache) OnOutOfSpace(err error) {
	if c.startEvictor == nil {
		return
	}
	if outOfSpace.trimClaimed.Swap(true) {
		log.Infof("The download ran out of space again (%v); the evictor was already asked for in this process.", err)
		return
	}
	switch c.startEvictor(fmt.Sprintf("the download ran out of space: %v", err)) {
	case trimStarted:
		outOfSpace.trimStarted.Store(true)
	case trimBusy:
		outOfSpace.trimBusy.Store(true)
	}
}
