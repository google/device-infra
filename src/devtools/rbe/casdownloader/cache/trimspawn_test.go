package cache

import (
	"context"
	"errors"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"slices"
	"strconv"
	"strings"
	"syscall"
	"testing"
	"time"

	"github.com/google/device-infra/src/devtools/rbe/common/storage"
)

// fakeSpawner records the trims the open-time check starts instead of
// starting them.
type fakeSpawner struct {
	calls []spawnCall
	err   error
}

type spawnCall struct {
	args    []string
	logPath string
}

func (f *fakeSpawner) spawn(args []string, logPath string) error {
	f.calls = append(f.calls, spawnCall{args: args, logPath: logPath})
	return f.err
}

// trimCheckOptions describes a lock-free cache in a fresh directory whose
// open-time check sees a 100 MiB volume with free bytes free and records what
// it would start in spawner. The default watermarks are 20% and 25%.
func trimCheckOptions(t *testing.T, free int64, spawner *fakeSpawner) Options {
	t.Helper()
	return Options{
		LockFree:   true,
		Dir:        filepath.Join(t.TempDir(), "cache"),
		StartTrims: true,
		statfs:     fixedVolume(100*mib, free),
		spawn:      spawner.spawn,
	}
}

// writeCapCheckFile records a cap check for the cache in cacheDir as if it
// had run age ago. The open-time check only stats it.
func writeCapCheckFile(t *testing.T, cacheDir string, age time.Duration) {
	t.Helper()
	root := filepath.Join(cacheDir, wantLayoutDir)
	if err := os.MkdirAll(root, 0755); err != nil {
		t.Fatalf("Failed to create %s: %v", root, err)
	}
	path := filepath.Join(root, ".cap_check")
	if err := os.WriteFile(path, nil, 0644); err != nil {
		t.Fatalf("Failed to write %s: %v", path, err)
	}
	when := time.Now().Add(-age)
	if err := os.Chtimes(path, when, when); err != nil {
		t.Fatalf("Failed to backdate %s: %v", path, err)
	}
}

func openAndClose(t *testing.T, opts Options) {
	t.Helper()
	c, err := Open(opts)
	if err != nil {
		t.Fatalf("Open failed: %v", err)
	}
	c.Close()
}

// TestOpen_StartsATrimOnlyWhenNeeded covers the open-time check: a trim is
// started when free space is below -cache-min-free-space or when a cap is set
// and its last check is missing or at least -cache-cap-check-interval old,
// and not otherwise.
func TestOpen_StartsATrimOnlyWhenNeeded(t *testing.T) {
	const noCheck = time.Duration(-1 << 63)
	for _, tc := range []struct {
		name     string
		free     int64
		maxSize  int64
		checkAge time.Duration
		interval time.Duration
		want     bool
	}{
		{name: "plenty of free space and no cap", free: 50 * mib, checkAge: noCheck, want: false},
		{name: "free space at the minimum", free: 20 * mib, checkAge: noCheck, want: false},
		{name: "free space below the minimum", free: 20*mib - 1, checkAge: noCheck, want: true},
		{name: "cap never checked", free: 50 * mib, maxSize: 1 << 30, checkAge: noCheck, want: true},
		{name: "cap checked recently", free: 50 * mib, maxSize: 1 << 30, checkAge: time.Hour, want: false},
		{name: "cap checked too long ago", free: 50 * mib, maxSize: 1 << 30, checkAge: 7 * time.Hour, want: true},
		{name: "cap checked longer ago than a shorter interval", free: 50 * mib, maxSize: 1 << 30, checkAge: 2 * time.Hour, interval: time.Hour, want: true},
		// A check time ahead of the clock means the clock was set back,
		// and says nothing about when the cap was last checked.
		{name: "cap checked in the future", free: 50 * mib, maxSize: 1 << 30, checkAge: -time.Hour, want: true},
		// The check only matters with a cap: it is how the cap is enforced.
		{name: "old check without a cap", free: 50 * mib, checkAge: 7 * time.Hour, want: false},
	} {
		t.Run(tc.name, func(t *testing.T) {
			spawner := &fakeSpawner{}
			opts := trimCheckOptions(t, tc.free, spawner)
			opts.MaxSize = tc.maxSize
			opts.CapCheckInterval = tc.interval
			if tc.checkAge != noCheck {
				writeCapCheckFile(t, opts.Dir, tc.checkAge)
			}

			openAndClose(t, opts)

			if got := len(spawner.calls) == 1; got != tc.want || len(spawner.calls) > 1 {
				t.Errorf("Open started %d trims, want a trim: %v", len(spawner.calls), tc.want)
			}
		})
	}
}

// A cap-check marker that cannot be stat'ed is logged and gives no reason to
// trim: a trim could not record its check there either.
func TestOpen_IgnoresACapCheckItCannotStat(t *testing.T) {
	spawner := &fakeSpawner{}
	opts := trimCheckOptions(t, 50*mib, spawner)
	opts.MaxSize = 1 << 30
	root := filepath.Join(opts.Dir, wantLayoutDir)
	if err := os.MkdirAll(root, 0755); err != nil {
		t.Fatalf("Failed to create %s: %v", root, err)
	}
	// A symlink to itself fails the stat with ELOOP.
	if err := os.Symlink(".cap_check", filepath.Join(root, ".cap_check")); err != nil {
		t.Fatalf("Failed to create a symlink loop: %v", err)
	}

	openAndClose(t, opts)
	if len(spawner.calls) != 0 {
		t.Errorf("Open started %d trims, want none", len(spawner.calls))
	}
}

// A trim already running is doing the work, so a download that finds the
// cache short of space leaves it to that one rather than starting another.
func TestOpen_SkipsTheTrimWhileTheLockIsHeld(t *testing.T) {
	spawner := &fakeSpawner{}
	opts := trimCheckOptions(t, 1*mib, spawner)
	root := filepath.Join(opts.Dir, wantLayoutDir)
	if err := os.MkdirAll(root, 0755); err != nil {
		t.Fatalf("Failed to create %s: %v", root, err)
	}
	f, err := os.OpenFile(filepath.Join(root, ".evict.lock"), os.O_CREATE|os.O_RDWR, 0644)
	if err != nil {
		t.Fatalf("Failed to open the lock file: %v", err)
	}
	defer f.Close()
	if err := syscall.Flock(int(f.Fd()), syscall.LOCK_EX|syscall.LOCK_NB); err != nil {
		t.Fatalf("Failed to take the lock: %v", err)
	}

	openAndClose(t, opts)
	if len(spawner.calls) != 0 {
		t.Errorf("Open started %d trims while another process holds the lock, want none", len(spawner.calls))
	}

	// Once the peer is done, the next download starts one.
	if err := syscall.Flock(int(f.Fd()), syscall.LOCK_UN); err != nil {
		t.Fatalf("Failed to release the lock: %v", err)
	}
	openAndClose(t, opts)
	if len(spawner.calls) != 1 {
		t.Errorf("Open started %d trims after the lock was released, want 1", len(spawner.calls))
	}
}

// Only downloads start trims. Opening the cache for anything else, as most
// tests do, must not start a process.
func TestOpen_NeverStartsATrimWithoutStartTrims(t *testing.T) {
	spawner := &fakeSpawner{}
	opts := trimCheckOptions(t, 1*mib, spawner)
	opts.StartTrims = false

	openAndClose(t, opts)
	if len(spawner.calls) != 0 {
		t.Errorf("Open started %d trims without StartTrims, want none", len(spawner.calls))
	}
}

// Starting the trim is best effort: a download never fails because of it.
func TestOpen_ATrimThatFailsToStartDoesNotFailTheCache(t *testing.T) {
	spawner := &fakeSpawner{err: errors.New("fork failed")}
	opts := trimCheckOptions(t, 1*mib, spawner)

	c, note := OpenOrDegrade(context.Background(), opts, "the download")
	if c == nil || note != "" {
		t.Fatalf("OpenOrDegrade = %v, %q; want a cache and no note", c, note)
	}
	c.Close()
	if len(spawner.calls) != 1 {
		t.Errorf("Open tried to start %d trims, want 1", len(spawner.calls))
	}
}

// The trim works toward the download's own watermarks and cap, with its
// tuning, and logs to v2/.trim.log. The cap-check interval stays with the
// download: the trim checks the cap whatever its interval.
func TestOpen_StartsTheTrimWithTheDownloadsEvictionFlags(t *testing.T) {
	spawner := &fakeSpawner{}
	opts := trimCheckOptions(t, 1*mib, spawner)
	sampleBuckets, minBlobAge := 32, 20*time.Minute
	opts.MinFreeSpace = "30%"
	opts.TargetFreeSpace = "40%"
	opts.MaxSize = 5 << 30
	opts.CapCheckInterval = time.Hour
	opts.Overrides = EvictorOverrides{SampleBuckets: &sampleBuckets, MinBlobAge: &minBlobAge}

	openAndClose(t, opts)

	if len(spawner.calls) != 1 {
		t.Fatalf("Open started %d trims, want 1", len(spawner.calls))
	}
	want := []string{
		"-trim-only",
		"-cache-dir=" + opts.Dir,
		"-cache-min-free-space=30%",
		"-cache-target-free-space=40%",
		"-cache-max-size=5368709120",
		"-cache-eviction-sample-buckets=32",
		"-cache-min-blob-age=20m0s",
	}
	if got := spawner.calls[0].args; !slices.Equal(got, want) {
		t.Errorf("Trim args = %q, want %q", got, want)
	}
	if got, want := spawner.calls[0].logPath, filepath.Join(opts.Dir, wantLayoutDir, ".trim.log"); got != want {
		t.Errorf("Trim log = %q, want %q", got, want)
	}
}

// The flags a download leaves unset are left out, so that the trim applies
// the same defaults rather than a zero.
func TestTrimArgs_LeavesOutUnsetFlags(t *testing.T) {
	got := trimArgs(Options{Dir: "/tmp/cas_cache"})
	if want := []string{"-trim-only", "-cache-dir=/tmp/cas_cache"}; !slices.Equal(got, want) {
		t.Errorf("trimArgs = %q, want %q", got, want)
	}
}

// Every eviction tuning flag a download was given reaches the trim.
func TestTrimArgs_ForwardsEveryOverride(t *testing.T) {
	sampleBuckets, batchSize := 16, 300
	batchDelay, lazyTouch, minBlobAge := time.Millisecond, 2*time.Hour, 5*time.Minute
	got := trimArgs(Options{Dir: "/tmp/cas_cache", Overrides: EvictorOverrides{
		SampleBuckets:     &sampleBuckets,
		BatchSize:         &batchSize,
		BatchDelay:        &batchDelay,
		LazyTouchInterval: &lazyTouch,
		MinBlobAge:        &minBlobAge,
	}})
	want := []string{
		"-trim-only",
		"-cache-dir=/tmp/cas_cache",
		"-cache-eviction-sample-buckets=16",
		"-cache-eviction-batch-size=300",
		"-cache-eviction-batch-delay=1ms",
		"-cache-lazy-touch-interval=2h0m0s",
		"-cache-min-blob-age=5m0s",
	}
	if !slices.Equal(got, want) {
		t.Errorf("trimArgs = %q, want %q", got, want)
	}
}

// The open-time check is best effort: whatever it cannot check, it logs, and
// starts no trim.
func TestMaybeStartTrim_StartsNothingOnErrors(t *testing.T) {
	failingStatfs := func(string) (storage.DiskStats, error) { return storage.DiskStats{}, errors.New("statfs failed") }
	for _, tc := range []struct {
		name   string
		cfg    storage.EvictorConfig
		statfs storage.StatfsFunc
		root   func(t *testing.T) string
	}{
		{
			name:   "unparsable watermark",
			cfg:    storage.EvictorConfig{MinFreeSpace: "lots"},
			statfs: fixedVolume(100*mib, 1*mib),
			root:   func(t *testing.T) string { return t.TempDir() },
		},
		{
			name:   "statfs fails",
			cfg:    storage.EvictorConfig{MinFreeSpace: "20%"},
			statfs: failingStatfs,
			root:   func(t *testing.T) string { return t.TempDir() },
		},
		{
			// Short of space, but the eviction lock cannot even be opened.
			name:   "lock cannot be probed",
			cfg:    storage.EvictorConfig{MinFreeSpace: "20%"},
			statfs: fixedVolume(100*mib, 1*mib),
			root:   func(t *testing.T) string { return filepath.Join(t.TempDir(), "missing") },
		},
	} {
		t.Run(tc.name, func(t *testing.T) {
			spawner := &fakeSpawner{}
			opts := Options{LockFree: true, StartTrims: true, statfs: tc.statfs, spawn: spawner.spawn}
			if maybeStartTrim(opts, tc.cfg, tc.root(t)) || len(spawner.calls) != 0 {
				t.Errorf("maybeStartTrim started %d trims, want none", len(spawner.calls))
			}
		})
	}
}

// startDetached reports a log it cannot open and a binary it cannot start,
// rather than starting a trim with no log or none at all.
func TestStartDetached_ReportsFailures(t *testing.T) {
	dir := t.TempDir()
	if _, _, err := startDetached(os.Args[0], helperArgs, filepath.Join(dir, "missing", ".trim.log")); err == nil {
		t.Error("startDetached with a log in a missing directory succeeded, want an error")
	}
	if _, _, err := startDetached(filepath.Join(dir, "no-such-binary"), nil, filepath.Join(dir, ".trim.log")); err == nil {
		t.Error("startDetached of a missing binary succeeded, want an error")
	}
}

// Trim, as -trim-only runs it, enforces -cache-max-size: with free space
// above the target, only the cap can make it evict. Every trim checks the
// cap and records it in v2/.cap_check, whether or not it evicts.
func TestTrim_EnforcesTheCap(t *testing.T) {
	cacheDir := filepath.Join(t.TempDir(), "cache")
	seedOldBlobs(t, cacheDir, trimBlobCount, trimBlobSize)
	// 90% of the cap is 1000 of the 1200 blobs.
	const maxSize = 1000 * trimBlobSize * 10 / 9
	trimOpts := TrimOptions{
		Dir:     cacheDir,
		MaxSize: maxSize,
		statfs:  fixedVolume(100*mib, 50*mib),
	}

	res, err := Trim(context.Background(), trimOpts)
	if err != nil {
		t.Fatalf("Trim failed: %v", err)
	}
	if res.Outcome != storage.TrimOutcomeEvicted || res.FilesDeleted != 200 {
		t.Errorf("Trim = %+v, want %q with 200 files", res, storage.TrimOutcomeEvicted)
	}
	marker := filepath.Join(cacheDir, wantLayoutDir, ".cap_check")
	if _, err := os.Stat(marker); err != nil {
		t.Errorf("Trim with a cap recorded no cap check: %v", err)
	}

	// The cache is at 90% of its cap now, so the next trim evicts nothing,
	// but it still checks, and records the check.
	old := time.Now().Add(-7 * time.Hour)
	if err := os.Chtimes(marker, old, old); err != nil {
		t.Fatalf("Failed to backdate %s: %v", marker, err)
	}
	res, err = Trim(context.Background(), trimOpts)
	if err != nil || res.Outcome != storage.TrimOutcomeNoop {
		t.Errorf("Trim at 90%% of the cap = %+v, %v; want a noop", res, err)
	}
	if info, err := os.Stat(marker); err != nil || !info.ModTime().After(old.Add(time.Hour)) {
		t.Errorf("After the second trim, stat(%s) = %v, %v; want it refreshed", marker, info, err)
	}
}

const (
	// detachedHelperEnv selects what TestDetachedHelper does in a re-executed
	// test binary.
	detachedHelperEnv = "CASDOWNLOADER_DETACHED_HELPER"
	// detachedHelperLog is the log the "spawn" mode passes on.
	detachedHelperLog = "CASDOWNLOADER_DETACHED_HELPER_LOG"
)

// helperArgs re-runs this test binary as TestDetachedHelper.
var helperArgs = []string{"-test.run=^TestDetachedHelper$"}

// TestDetachedHelper is not a test. It is the child half of the
// startDetached tests, re-executed from the same test binary, and it is
// skipped during an ordinary run.
func TestDetachedHelper(t *testing.T) {
	switch os.Getenv(detachedHelperEnv) {
	case "":
		t.Skip("helper process for the startDetached tests")
	case "report":
		// Describe how startDetached set this process up, then exit.
		null, _ := os.Stat(os.DevNull)
		isNull := func(f *os.File) bool {
			st, err := f.Stat()
			return err == nil && null != nil && os.SameFile(st, null)
		}
		pgid, _ := syscall.Getpgid(0)
		fmt.Fprintf(os.Stderr, "detached-helper: stdin-null=%v stdout-null=%v own-group=%v\n",
			isNull(os.Stdin), isNull(os.Stdout), pgid == os.Getpid())
		os.Exit(0)
	case "spawn":
		// Start a long-running child the way a download starts a trim,
		// report its pid, and exit at once.
		os.Setenv(detachedHelperEnv, "sleep")
		pid, _, err := startDetached(os.Args[0], helperArgs, os.Getenv(detachedHelperLog))
		if err != nil {
			fmt.Printf("error: %v\n", err)
			os.Exit(1)
		}
		fmt.Printf("pid=%d\n", pid)
		os.Exit(0)
	case "sleep":
		// The backstop keeps a failed test from leaving this behind for long.
		time.Sleep(2 * time.Minute)
		os.Exit(0)
	}
}

func processExists(pid int) bool {
	return syscall.Kill(pid, 0) == nil
}

// A child that exits while the download is still running is reaped by the
// background Wait rather than left a zombie, and it ran detached: in a
// session of its own, with stdin and stdout on /dev/null and stderr in the
// log, which was truncated first.
func TestStartDetached_ReapsAChildThatExitsFirst(t *testing.T) {
	logPath := filepath.Join(t.TempDir(), ".trim.log")
	if err := os.WriteFile(logPath, []byte("the previous run's log\n"), 0644); err != nil {
		t.Fatalf("Failed to write the old log: %v", err)
	}
	t.Setenv(detachedHelperEnv, "report")

	pid, reaped, err := startDetached(os.Args[0], helperArgs, logPath)
	if err != nil {
		t.Fatalf("startDetached failed: %v", err)
	}
	select {
	case <-reaped:
	case <-time.After(60 * time.Second):
		syscall.Kill(pid, syscall.SIGKILL)
		t.Fatal("The child was not reaped within a minute")
	}
	// A zombie still answers signal 0; a reaped process does not.
	if processExists(pid) {
		t.Errorf("Process %d still exists after it exited; want it reaped", pid)
	}

	data, err := os.ReadFile(logPath)
	if err != nil {
		t.Fatalf("Failed to read the log: %v", err)
	}
	got := string(data)
	if strings.Contains(got, "previous run") {
		t.Errorf("The log kept the previous run's contents; want it truncated:\n%s", got)
	}
	if want := "detached-helper: stdin-null=true stdout-null=true own-group=true"; !strings.Contains(got, want) {
		t.Errorf("The log does not contain %q:\n%s", want, got)
	}
}

// A download that starts a trim exits when it is done, however long the trim
// runs: the parent below exits right after starting a child that sleeps, and
// its stdout reaches EOF, which it would not if the child had inherited it.
func TestStartDetached_NeverDelaysTheParentsExit(t *testing.T) {
	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()
	parent := exec.CommandContext(ctx, os.Args[0], helperArgs...)
	parent.Env = append(os.Environ(),
		detachedHelperEnv+"=spawn",
		detachedHelperLog+"="+filepath.Join(t.TempDir(), ".trim.log"))

	out, err := parent.Output()
	if err != nil {
		t.Fatalf("The parent did not exit cleanly within a minute: %v (output %q)", err, out)
	}
	// The test binary may print lines of its own, such as "=== RUN".
	pid := 0
	for _, line := range strings.Split(string(out), "\n") {
		if text, ok := strings.CutPrefix(strings.TrimSpace(line), "pid="); ok {
			pid, err = strconv.Atoi(text)
		}
	}
	if pid <= 0 || err != nil {
		t.Fatalf("The parent reported %q, want a line pid=<child>", out)
	}
	t.Cleanup(func() { syscall.Kill(pid, syscall.SIGKILL) })

	if !processExists(pid) {
		t.Errorf("The child %d is gone already; want it still running after its parent exited, or this test proves nothing", pid)
	}
}

// spawnRealGuardEnv is set in the environment of the processes the tests below
// start for real, so that a re-executed test binary that ran them anyway would
// not start more.
const spawnRealGuardEnv = "CASDOWNLOADER_SPAWN_REAL_GUARD"

// waitForLog waits up to a minute for the file at path to contain want, and
// returns what it last held.
func waitForLog(t *testing.T, path, want string) (string, bool) {
	t.Helper()
	deadline := time.Now().Add(60 * time.Second)
	for {
		data, _ := os.ReadFile(path)
		if strings.Contains(string(data), want) {
			return string(data), true
		}
		if time.Now().After(deadline) {
			return string(data), false
		}
		time.Sleep(50 * time.Millisecond)
	}
}

// spawnSelf, the spawner downloads use, starts this very binary, detached,
// with the args it is given and its stderr in the log.
func TestSpawnSelf_StartsThisBinaryDetached(t *testing.T) {
	if os.Getenv(spawnRealGuardEnv) != "" {
		t.Skip("started by another test")
	}
	t.Setenv(spawnRealGuardEnv, "1")
	t.Setenv(detachedHelperEnv, "report")
	logPath := filepath.Join(t.TempDir(), ".trim.log")

	if err := spawnSelf(helperArgs, logPath); err != nil {
		t.Fatalf("spawnSelf failed: %v", err)
	}
	want := "detached-helper: stdin-null=true stdout-null=true own-group=true"
	if got, ok := waitForLog(t, logPath, want); !ok {
		t.Errorf("The log does not contain %q within a minute:\n%s", want, got)
	}
}

// Without the test seams, the open-time check measures the real volume and
// starts this binary as the trim. A cap never checked yet makes a trim
// due whatever the volume's free space.
func TestMaybeStartTrim_DefaultsToTheRealVolumeAndThisBinary(t *testing.T) {
	if os.Getenv(spawnRealGuardEnv) != "" {
		t.Skip("started by another test")
	}
	t.Setenv(spawnRealGuardEnv, "1")
	root := t.TempDir()
	opts := Options{LockFree: true, Dir: t.TempDir(), StartTrims: true, MaxSize: 1 << 30}

	if !maybeStartTrim(opts, storage.EvictorConfig{MinFreeSpace: "0%"}, root) {
		t.Fatal("maybeStartTrim started no trim, want one for the due cap check")
	}
	// The test binary does not know -trim-only, and says so in the log: it
	// was this binary, started with the trim's command line.
	logPath := filepath.Join(root, trimLogName)
	if got, ok := waitForLog(t, logPath, "trim-only"); !ok {
		t.Errorf("The trim's log does not mention -trim-only within a minute:\n%s", got)
	}
}
