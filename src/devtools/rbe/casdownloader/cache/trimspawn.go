package cache

import (
	"errors"
	"fmt"
	"io/fs"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
	"syscall"
	"time"

	log "github.com/golang/glog"
	"github.com/google/device-infra/src/devtools/rbe/common/storage"
)

// DefaultCapCheckInterval is the default -cache-cap-check-interval: how old
// the lock-free cache's last cap check may be before a download that enforces
// -cache-max-size starts a trim to run a new one. The value is the one
// proposed in b/572891754 and is still to be confirmed during the canary.
const DefaultCapCheckInterval = 6 * time.Hour

// trimLogName is the file in the lock-free cache's root that a detached
// evictor's stderr, and so its log, goes to. It is truncated each time one is
// started, so it holds the last run. Its leading dot and root-level placement
// keep it out of the evictor's way.
const trimLogName = ".trim.log"

// capCheckInterval resolves -cache-cap-check-interval: zero or less means the
// default.
func capCheckInterval(d time.Duration) time.Duration {
	if d <= 0 {
		return DefaultCapCheckInterval
	}
	return d
}

// trimSpawner starts a detached `casdownloader args...` whose stderr goes to
// logPath, and returns once it has started. Tests replace it.
type trimSpawner func(args []string, logPath string) error

// spawnSelf is the trimSpawner downloads use: it starts this binary again.
func spawnSelf(args []string, logPath string) error {
	exe, err := os.Executable()
	if err != nil {
		return fmt.Errorf("failed to find this binary: %w", err)
	}
	_, _, err = startDetached(exe, args, logPath)
	return err
}

// startDetached starts path with args as a process of its own, and returns
// its pid and a channel that is closed once it has exited and been reaped.
//
// The child gets a session of its own (setsid), so it outlives the download
// that started it without taking the download's terminal or process group
// with it, and a signal sent to the download's group does not stop a trim
// halfway. Its stdin and stdout are /dev/null, which exec arranges for nil
// Stdin and Stdout, and its stderr is logPath, truncated first.
//
// The caller never waits for the child. A goroutine does, so that a child
// that exits while the caller is still running is reaped rather than left a
// zombie; one that is still running when the caller exits is re-parented to
// init, or the container's PID 1, which reaps it then (see b/572783501 for
// containers whose PID 1 does not). Because stderr is an *os.File, exec
// hands the child the descriptor itself rather than copying through a pipe,
// so nothing in this process waits on the child's output either.
func startDetached(path string, args []string, logPath string) (pid int, reaped <-chan struct{}, err error) {
	// O_APPEND as well as O_TRUNC: if two downloads start a trim at once,
	// the one that loses the lock and exits appends its line rather than
	// writing over the winner's from offset zero.
	logFile, err := os.OpenFile(logPath, os.O_WRONLY|os.O_CREATE|os.O_TRUNC|os.O_APPEND, 0644)
	if err != nil {
		return 0, nil, fmt.Errorf("failed to open %s for the trim's log: %w", logPath, err)
	}
	// The child has its own copy of the descriptor once started.
	defer logFile.Close()

	cmd := exec.Command(path, args...)
	cmd.Stderr = logFile
	cmd.SysProcAttr = &syscall.SysProcAttr{Setsid: true}
	if err := cmd.Start(); err != nil {
		return 0, nil, fmt.Errorf("failed to start %s: %w", path, err)
	}

	done := make(chan struct{})
	go func() {
		defer close(done)
		if err := cmd.Wait(); err != nil {
			log.Warningf("The detached trim (pid %d) failed: %v. See %s.", cmd.Process.Pid, err, logPath)
		}
	}()
	return cmd.Process.Pid, done, nil
}

// trimArgs is the command line of the -trim-only process a download starts
// for the cache opts describes: -cache-dir, and every eviction flag the
// download was given, so that the trim works toward the same watermarks and
// cap with the same tuning. A flag the download did not set is left out, and
// the trim applies the same default. -cache-cap-check-interval is left out
// too: it only decides whether a download starts a trim, and a trim with a
// cap always checks it.
//
// The flag names are main's; they are spelled out here because this is the
// only place that turns Options back into flags.
func trimArgs(opts Options) []string {
	args := []string{"-trim-only", "-cache-dir=" + opts.Dir}
	if opts.MinFreeSpace != "" {
		args = append(args, "-cache-min-free-space="+opts.MinFreeSpace)
	}
	if opts.TargetFreeSpace != "" {
		args = append(args, "-cache-target-free-space="+opts.TargetFreeSpace)
	}
	if opts.MaxSize > 0 {
		args = append(args, "-cache-max-size="+strconv.FormatInt(opts.MaxSize, 10))
	}
	o := opts.Overrides
	if o.SampleBuckets != nil {
		args = append(args, "-cache-eviction-sample-buckets="+strconv.Itoa(*o.SampleBuckets))
	}
	if o.BatchSize != nil {
		args = append(args, "-cache-eviction-batch-size="+strconv.Itoa(*o.BatchSize))
	}
	if o.BatchDelay != nil {
		args = append(args, "-cache-eviction-batch-delay="+o.BatchDelay.String())
	}
	if o.LazyTouchInterval != nil {
		args = append(args, "-cache-lazy-touch-interval="+o.LazyTouchInterval.String())
	}
	if o.MinBlobAge != nil {
		args = append(args, "-cache-min-blob-age="+o.MinBlobAge.String())
	}
	return args
}

// trimReasons is the open-time check: it reports why the lock-free cache
// rooted at root needs a trim, if it does, at the cost of a statfs and, with
// a cap, a stat.
//
// A trim is needed if free space on the volume is below minFree, or if
// maxSize is set and the last cap check is at least checkInterval old: the
// cap is enforced only by a trim, which checks it. Failures are logged and
// count as no reason, since a trim could not run where these syscalls fail
// either.
func trimReasons(root string, minFree storage.SpaceThreshold, maxSize int64, checkInterval time.Duration, statfs storage.StatfsFunc, now time.Time) []string {
	var reasons []string
	if st, err := statfs(root); err != nil {
		log.Warningf("Could not check free space for the lock-free cache in %s: %v.", root, err)
	} else if floor := minFree.Evaluate(st.TotalBytes); st.FreeBytes < floor {
		reasons = append(reasons, fmt.Sprintf("%d bytes free, below -cache-min-free-space=%s (%d bytes)",
			st.FreeBytes, thresholdString(minFree), floor))
	}
	if maxSize > 0 {
		due, err := capCheckDue(root, checkInterval, now)
		switch {
		case err != nil:
			log.Warningf("Could not check when the lock-free cache in %s was last checked against its cap: %v.", root, err)
		case due:
			reasons = append(reasons, fmt.Sprintf("a check against -cache-max-size=%d is due (none in the last %v)", maxSize, checkInterval))
		}
	}
	// TODO: Change 4 adds a third reason, legacy cleanup
	// pending: if the legacy luci layout is present, stat
	// <cache-dir>/state.json, and start a trim to clean it up once it has
	// not been modified for 24 hours.
	return reasons
}

// capCheckDue reports whether the cap check recorded under root
// (storage.CapCheckName) is missing or at least interval old at now. A check
// time in the future, as after the clock was set back, also makes one due.
// An error other than the file not existing is returned with false: whatever
// keeps the file from being stat'ed would also keep a trim from recording a
// check.
func capCheckDue(root string, interval time.Duration, now time.Time) (bool, error) {
	info, err := os.Stat(filepath.Join(root, storage.CapCheckName))
	if errors.Is(err, fs.ErrNotExist) {
		return true, nil
	}
	if err != nil {
		return false, err
	}
	age := now.Sub(info.ModTime())
	return age < 0 || age >= interval, nil
}

// maybeStartTrim runs the open-time check on the lock-free cache rooted at
// root, and if it needs a trim and no process is trimming it yet, starts
// casdownloader -trim-only for it in the background. It reports whether it
// started one.
//
// It is best effort: a download never waits for, or fails because of, a
// trim. Any error is logged, and the download goes on with the cache as it
// is.
func maybeStartTrim(opts Options, cfg storage.EvictorConfig, root string) bool {
	minFree, err := storage.ParseSpaceThreshold(cfg.MinFreeSpace)
	if err != nil {
		// cfg is ours, and the evictor has already accepted it.
		log.Warningf("Not checking whether the lock-free cache in %s needs a trim: %v.", root, err)
		return false
	}
	statfs := opts.statfs
	if statfs == nil {
		statfs = storage.RealStatfs
	}
	interval := capCheckInterval(opts.CapCheckInterval)
	reasons := trimReasons(root, minFree, opts.MaxSize, interval, statfs, time.Now())
	if len(reasons) == 0 {
		return false
	}

	busy, err := storage.EvictionInProgress(root)
	if err != nil {
		log.Warningf("The lock-free cache in %s needs a trim (%s), but its eviction lock could not be checked: %v. Not starting one.",
			root, strings.Join(reasons, "; "), err)
		return false
	}
	if busy {
		log.Infof("The lock-free cache in %s needs a trim (%s); another process is already trimming it.", root, strings.Join(reasons, "; "))
		return false
	}

	spawn := opts.spawn
	if spawn == nil {
		spawn = spawnSelf
	}
	args := trimArgs(opts)
	logPath := filepath.Join(root, trimLogName)
	if err := spawn(args, logPath); err != nil {
		log.Warningf("The lock-free cache in %s needs a trim (%s), but it could not be started: %v. Continuing without it.",
			root, strings.Join(reasons, "; "), err)
		return false
	}
	log.Infof("Started a trim of the lock-free cache in %s in the background (%s), logging to %s: %s",
		root, strings.Join(reasons, "; "), logPath, strings.Join(args, " "))
	return true
}
