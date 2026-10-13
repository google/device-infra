package cache

import (
	"context"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"slices"
	"syscall"
	"testing"
	"time"

	"github.com/bazelbuild/remote-apis-sdks/go/pkg/client"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/digest"
	"github.com/google/device-infra/src/devtools/rbe/common/storage"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

// resetOutOfSpace gives the test a process in which the ENOSPC hook has not
// fired yet, and leaves one behind.
func resetOutOfSpace(t *testing.T) {
	t.Helper()
	reset := func() {
		outOfSpace.errors.Store(0)
		outOfSpace.trimClaimed.Store(false)
		outOfSpace.trimStarted.Store(false)
		outOfSpace.trimBusy.Store(false)
	}
	reset()
	t.Cleanup(reset)
}

// diskFull is a write failing on a full disk, as the fetch, the restore or
// Push would report it.
func diskFull(path string) error {
	return &os.PathError{Op: "write", Path: path, Err: syscall.ENOSPC}
}

// openForDownload opens the lock-free cache opts describes, as a download
// does, and checks that the open-time check started nothing, so that every
// trim the test sees came from the ENOSPC hook.
func openForDownload(t *testing.T, opts Options, spawner *fakeSpawner) *LockFreeCache {
	t.Helper()
	before := len(spawner.calls)
	c, err := Open(opts)
	if err != nil {
		t.Fatalf("Open failed: %v", err)
	}
	t.Cleanup(func() { c.Close() })
	if started := len(spawner.calls) - before; started != 0 {
		t.Fatalf("Open started %d trims, want none, or this test proves nothing", started)
	}
	return c.(*LockFreeCache)
}

// holdEvictionLock takes the eviction lock of the lock-free cache in
// cacheDir, as a running evictor holds it, and returns a func that releases
// it. The lock is also released when the test ends.
func holdEvictionLock(t *testing.T, cacheDir string) (release func()) {
	t.Helper()
	root := filepath.Join(cacheDir, wantLayoutDir)
	if err := os.MkdirAll(root, 0755); err != nil {
		t.Fatalf("Failed to create %s: %v", root, err)
	}
	f, err := os.OpenFile(filepath.Join(root, ".evict.lock"), os.O_CREATE|os.O_RDWR, 0644)
	if err != nil {
		t.Fatalf("Failed to open the lock file: %v", err)
	}
	if err := syscall.Flock(int(f.Fd()), syscall.LOCK_EX|syscall.LOCK_NB); err != nil {
		f.Close()
		t.Fatalf("Failed to take the lock: %v", err)
	}
	t.Cleanup(func() { f.Close() })
	return func() {
		if err := syscall.Flock(int(f.Fd()), syscall.LOCK_UN); err != nil {
			t.Fatalf("Failed to release the lock: %v", err)
		}
	}
}

func TestIsOutOfSpace(t *testing.T) {
	serverFull := status.Error(codes.Internal, "write /cache/ab/abcd: no space left on device")
	for _, tc := range []struct {
		name string
		err  error
		want bool
	}{
		{name: "nil", err: nil, want: false},
		{name: "ENOSPC", err: syscall.ENOSPC, want: true},
		{name: "wrapped ENOSPC", err: fmt.Errorf("failed to cache a: %w", &os.LinkError{Op: "link", Old: "a", New: "b", Err: syscall.ENOSPC}), want: true},
		{name: "flattened to text", err: fmt.Errorf("failed to restore a.img: %v", diskFull("/tmp/a.img")), want: true},
		{name: "text in another case", err: errors.New("write /tmp/a: No space left on device"), want: true},
		{name: "another errno", err: &os.PathError{Op: "open", Path: "/tmp/a", Err: syscall.EACCES}, want: false},
		{name: "out of quota", err: syscall.EDQUOT, want: false},
		{name: "deadline", err: context.DeadlineExceeded, want: false},
		// A full disk on the server is a remote failure.
		{name: "a server out of space", err: serverFull, want: false},
		{name: "a wrapped server out of space", err: fmt.Errorf("failed to download files: %w", serverFull), want: false},
	} {
		t.Run(tc.name, func(t *testing.T) {
			if got := IsOutOfSpace(tc.err); got != tc.want {
				t.Errorf("IsOutOfSpace(%v) = %v, want %v", tc.err, got, tc.want)
			}
		})
	}
}

// The hook starts the evictor although the open-time check would not: free
// space is above -cache-min-free-space, and the cap was checked recently. It
// starts the same plain -trim-only, with the same flags, as the open-time
// check would, and only once per process, however many paths of the
// download, and however many caches it opens, run out of space after the
// first. Each call reports the error as out of space so that the caller
// returns it at once.
func TestHandleOutOfSpace_StartsTheEvictorOncePerProcessWithoutTheOpenTimeCheck(t *testing.T) {
	resetOutOfSpace(t)
	spawner := &fakeSpawner{}
	opts := trimCheckOptions(t, 50*mib, spawner)
	opts.MinFreeSpace = "10%"
	opts.TargetFreeSpace = "15%"
	opts.MaxSize = 1 << 30
	writeCapCheckFile(t, opts.Dir, time.Hour)
	c := openForDownload(t, opts, spawner)

	// The fetch runs out of space.
	if !HandleOutOfSpace(c, fmt.Errorf("failed to download files: %w", diskFull("/tmp/dir/a"))) {
		t.Fatal("HandleOutOfSpace = false for ENOSPC, want true")
	}
	if len(spawner.calls) != 1 {
		t.Fatalf("The hook started %d trims, want 1", len(spawner.calls))
	}
	want := []string{
		"-trim-only",
		"-cache-dir=" + opts.Dir,
		"-cache-min-free-space=10%",
		"-cache-target-free-space=15%",
		"-cache-max-size=1073741824",
	}
	if got := spawner.calls[0].args; !slices.Equal(got, want) || !slices.Equal(got, trimArgs(opts)) {
		t.Errorf("Trim args = %q, want %q, as the open-time check passes", got, want)
	}
	if got, want := spawner.calls[0].logPath, filepath.Join(opts.Dir, wantLayoutDir, ".trim.log"); got != want {
		t.Errorf("Trim log = %q, want %q", got, want)
	}

	// Then the restore, and a second cache, as main opens for its retry.
	if !HandleOutOfSpace(c, diskFull("/tmp/dir/a.img")) {
		t.Error("HandleOutOfSpace = false for the second ENOSPC, want true")
	}
	c2 := openForDownload(t, opts, spawner)
	if !HandleOutOfSpace(c2, errors.New("write /tmp/dir/b: no space left on device")) {
		t.Error("HandleOutOfSpace = false for an ENOSPC flattened to text, want true")
	}
	if len(spawner.calls) != 1 {
		t.Errorf("The hook started %d trims for three ENOSPC errors, want 1", len(spawner.calls))
	}
	if got, want := OutOfSpace(), (OutOfSpaceStats{Errors: 3, TrimStarted: true}); got != want {
		t.Errorf("OutOfSpace() = %+v, want %+v", got, want)
	}
}

// failIngest makes every blob Push ingests fail with err, and counts the
// attempts.
func failIngest(t *testing.T, err error) *int {
	t.Helper()
	orig := ingestBlob
	t.Cleanup(func() { ingestBlob = orig })
	calls := 0
	ingestBlob = func(*storage.Storage, string, string) error {
		calls++
		return err
	}
	return &calls
}

// stagePushBatch writes n downloaded files and returns them as Push takes
// them.
func stagePushBatch(t *testing.T, n int) map[digest.Digest]*client.TreeOutput {
	t.Helper()
	dir := t.TempDir()
	batch := make(map[digest.Digest]*client.TreeOutput, n)
	for i := range n {
		_, out := stage(t, dir, fmt.Sprintf("work/f%d", i), fmt.Sprintf("content %d", i), false)
		batch[out.Digest] = out
	}
	return batch
}

// Push that runs out of space starts the evictor and returns the error at
// once, without trying the rest of its files.
func TestPush_OutOfSpaceStartsTheEvictorAndReturnsTheError(t *testing.T) {
	resetOutOfSpace(t)
	spawner := &fakeSpawner{}
	opts := trimCheckOptions(t, 50*mib, spawner)
	c := openForDownload(t, opts, spawner)
	attempts := failIngest(t, &os.LinkError{Op: "link", Old: "/tmp/dir/f", New: "/cache/v2/ab/abcd", Err: syscall.ENOSPC})

	err := c.Push(context.Background(), stagePushBatch(t, 3))
	if !errors.Is(err, syscall.ENOSPC) {
		t.Fatalf("Push = %v, want its ENOSPC", err)
	}
	if *attempts != 1 {
		t.Errorf("Push tried to ingest %d files, want it to stop at the first that ran out of space", *attempts)
	}
	if len(spawner.calls) != 1 || !slices.Equal(spawner.calls[0].args, trimArgs(opts)) {
		t.Errorf("Push started %+v, want one trim with %q", spawner.calls, trimArgs(opts))
	}
	if got, want := OutOfSpace(), (OutOfSpaceStats{Errors: 1, TrimStarted: true}); got != want {
		t.Errorf("OutOfSpace() = %+v, want %+v", got, want)
	}
}

// Only running out of space starts a trim.
func TestHandleOutOfSpace_IgnoresOtherErrors(t *testing.T) {
	resetOutOfSpace(t)
	spawner := &fakeSpawner{}
	opts := trimCheckOptions(t, 50*mib, spawner)
	c := openForDownload(t, opts, spawner)

	for _, err := range []error{
		nil,
		errors.New("connection reset by peer"),
		&os.PathError{Op: "open", Path: "/tmp/a", Err: syscall.EACCES},
		status.Error(codes.Internal, "no space left on device"),
	} {
		if HandleOutOfSpace(c, err) {
			t.Errorf("HandleOutOfSpace(%v) = true, want false", err)
		}
	}
	failIngest(t, &os.LinkError{Op: "link", Old: "/tmp/dir/f", New: "/cache/v2/ab/abcd", Err: syscall.EACCES})
	if err := c.Push(context.Background(), stagePushBatch(t, 1)); !errors.Is(err, syscall.EACCES) {
		t.Errorf("Push = %v, want its EACCES", err)
	}

	if len(spawner.calls) != 0 {
		t.Errorf("Errors other than ENOSPC started %d trims, want none", len(spawner.calls))
	}
	if got := OutOfSpace(); got != (OutOfSpaceStats{}) {
		t.Errorf("OutOfSpace() = %+v, want nothing counted", got)
	}
}

// Only a download starts processes, here as at open time. A cache opened for
// anything else counts the error, starts nothing, and does not use up the
// process's one try.
func TestHandleOutOfSpace_StartsNothingWithoutStartTrims(t *testing.T) {
	resetOutOfSpace(t)
	spawner := &fakeSpawner{}
	opts := trimCheckOptions(t, 50*mib, spawner)
	opts.StartTrims = false
	c := openForDownload(t, opts, spawner)

	if !HandleOutOfSpace(c, diskFull("/tmp/dir/a")) {
		t.Error("HandleOutOfSpace = false for ENOSPC, want true")
	}
	if !HandleOutOfSpace(nil, diskFull("/tmp/dir/a")) {
		t.Error("HandleOutOfSpace = false for ENOSPC without a cache, want true")
	}
	if len(spawner.calls) != 0 {
		t.Errorf("A cache opened without StartTrims started %d trims, want none", len(spawner.calls))
	}
	if got, want := OutOfSpace(), (OutOfSpaceStats{Errors: 2}); got != want {
		t.Errorf("OutOfSpace() = %+v, want %+v", got, want)
	}

	opts.StartTrims = true
	HandleOutOfSpace(openForDownload(t, opts, spawner), diskFull("/tmp/dir/a"))
	if len(spawner.calls) != 1 {
		t.Errorf("A download's cache started %d trims afterwards, want 1", len(spawner.calls))
	}
}

// An evictor already running is doing the work, and starting another would
// only wipe its log, so the hook records that it found the cache busy and
// starts nothing. That was the process's one try: a later ENOSPC, even once
// the evictor is done, starts nothing either.
func TestHandleOutOfSpace_RecordsBusyAndStartsNothingWhileTheLockIsHeld(t *testing.T) {
	resetOutOfSpace(t)
	spawner := &fakeSpawner{}
	opts := trimCheckOptions(t, 50*mib, spawner)
	c := openForDownload(t, opts, spawner)
	release := holdEvictionLock(t, opts.Dir)

	if !HandleOutOfSpace(c, diskFull("/tmp/dir/a")) {
		t.Error("HandleOutOfSpace = false for ENOSPC, want true")
	}
	if len(spawner.calls) != 0 {
		t.Errorf("The hook started %d trims while another process holds the lock, want none", len(spawner.calls))
	}
	if got, want := OutOfSpace(), (OutOfSpaceStats{Errors: 1, TrimBusy: true}); got != want {
		t.Errorf("OutOfSpace() = %+v, want %+v", got, want)
	}

	release()
	if !HandleOutOfSpace(c, diskFull("/tmp/dir/b")) {
		t.Error("HandleOutOfSpace = false for the second ENOSPC, want true")
	}
	if len(spawner.calls) != 0 {
		t.Errorf("The hook started %d trims after its one try, want none", len(spawner.calls))
	}
	if got, want := OutOfSpace(), (OutOfSpaceStats{Errors: 2, TrimBusy: true}); got != want {
		t.Errorf("OutOfSpace() = %+v, want %+v", got, want)
	}
}

// Starting the evictor is best effort, and tried once: a failure is logged,
// the caller still gets its error back, and the next ENOSPC does not try
// again.
func TestHandleOutOfSpace_AnEvictorThatFailsToStartIsNotRetried(t *testing.T) {
	resetOutOfSpace(t)
	spawner := &fakeSpawner{err: errors.New("fork failed")}
	opts := trimCheckOptions(t, 50*mib, spawner)
	c := openForDownload(t, opts, spawner)

	for range 2 {
		if !HandleOutOfSpace(c, diskFull("/tmp/dir/a")) {
			t.Error("HandleOutOfSpace = false for ENOSPC, want true")
		}
	}
	if len(spawner.calls) != 1 {
		t.Errorf("The hook tried to start %d trims, want 1", len(spawner.calls))
	}
	if got, want := OutOfSpace(), (OutOfSpaceStats{Errors: 2}); got != want {
		t.Errorf("OutOfSpace() = %+v, want %+v", got, want)
	}
}

// The error comes back while the evictor the hook started is still running:
// the hook starts it and returns, and never waits for it to free space. The
// spawner stands in for the evictor by taking the eviction lock, as the real
// one does first, and keeping it.
func TestHandleOutOfSpace_ReturnsWithoutWaitingForTheEvictor(t *testing.T) {
	for _, tc := range []struct {
		name string
		// hit runs a hook site into ENOSPC and returns the error it got.
		hit func(t *testing.T, c *LockFreeCache) error
	}{
		{
			name: "HandleOutOfSpace",
			hit: func(t *testing.T, c *LockFreeCache) error {
				err := diskFull("/tmp/dir/a")
				if !HandleOutOfSpace(c, err) {
					t.Error("HandleOutOfSpace = false for ENOSPC, want true")
				}
				return err
			},
		},
		{
			name: "Push",
			hit: func(t *testing.T, c *LockFreeCache) error {
				failIngest(t, &os.LinkError{Op: "link", Old: "/tmp/dir/f", New: "/cache/v2/ab/abcd", Err: syscall.ENOSPC})
				return c.Push(context.Background(), stagePushBatch(t, 1))
			},
		},
	} {
		t.Run(tc.name, func(t *testing.T) {
			resetOutOfSpace(t)
			spawner := &fakeSpawner{}
			opts := trimCheckOptions(t, 50*mib, spawner)
			opts.spawn = func(args []string, logPath string) error {
				holdEvictionLock(t, opts.Dir)
				return spawner.spawn(args, logPath)
			}
			c := openForDownload(t, opts, spawner)

			if err := tc.hit(t, c); !errors.Is(err, syscall.ENOSPC) {
				t.Errorf("Got %v, want the ENOSPC", err)
			}
			if len(spawner.calls) != 1 {
				t.Fatalf("The hook started %d trims, want 1", len(spawner.calls))
			}
			busy, err := storage.EvictionInProgress(filepath.Join(opts.Dir, wantLayoutDir))
			if err != nil || !busy {
				t.Errorf("EvictionInProgress = %v, %v after the hook returned; want the evictor still running", busy, err)
			}
			if got, want := OutOfSpace(), (OutOfSpaceStats{Errors: 1, TrimStarted: true}); got != want {
				t.Errorf("OutOfSpace() = %+v, want %+v", got, want)
			}
		})
	}
}
