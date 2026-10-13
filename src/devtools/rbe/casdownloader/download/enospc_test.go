package download

import (
	"context"
	"errors"
	"fmt"
	"os"
	"strings"
	"syscall"
	"testing"
	"time"

	"github.com/bazelbuild/remote-apis-sdks/go/pkg/client"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/digest"
	"github.com/google/device-infra/src/devtools/rbe/casdownloader/cache"
	"github.com/google/device-infra/src/devtools/rbe/casuploader/chunkerutil"
)

// outOfSpaceCache misses on everything, like missingCache, and records what
// the ENOSPC hook hands it instead of starting a trim.
type outOfSpaceCache struct {
	missingCache
	got []error
}

func (c *outOfSpaceCache) OnOutOfSpace(err error) { c.got = append(c.got, err) }

// diskFullErr is a write failing on a full disk, as the fetch or the restore
// would report it.
func diskFullErr() error {
	return &os.PathError{Op: "write", Path: "/tmp/dir/a.txt", Err: syscall.ENOSPC}
}

// failFetch makes every fetch from CAS fail with err.
func failFetch(t *testing.T, err error) {
	t.Helper()
	orig := downloadFiles
	t.Cleanup(func() { downloadFiles = orig })
	downloadFiles = func(*client.Client, context.Context, string, map[digest.Digest]*client.TreeOutput) (*client.MovedBytesMetadata, error) {
		return nil, err
	}
}

// A fetch that runs out of space tells the cache once, so that it can start a
// trim, and fails at once with the error it got: nothing is pushed, and main
// is not told to retry against CAS remote, since the disk would be just as
// full for it. Any other fetch failure starts nothing and is still remote.
func TestDoDownload_FetchOutOfSpace(t *testing.T) {
	for _, tc := range []struct {
		name       string
		err        error
		wantHook   bool
		wantRemote bool
	}{
		{name: "ENOSPC", err: diskFullErr(), wantHook: true},
		{name: "flattened to text", err: fmt.Errorf("failed to write a.txt: %v", diskFullErr()), wantHook: true},
		{name: "another failure", err: errors.New("connection reset by peer"), wantRemote: true},
	} {
		t.Run(tc.name, func(t *testing.T) {
			f := newRemoteFailedFixture(t)
			failFetch(t, tc.err)
			c := &outOfSpaceCache{}
			job := DownloadJob{Client: f.client, Digest: f.rootDigest, Dir: t.TempDir(), Cache: c}

			err := job.DoDownload(context.Background())
			if err == nil {
				t.Fatal("DoDownload succeeded despite the fetch failing")
			}
			if !strings.Contains(err.Error(), tc.err.Error()) {
				t.Errorf("DoDownload = %v, want the fetch's error %q", err, tc.err)
			}
			if errors.Is(tc.err, syscall.ENOSPC) && !errors.Is(err, syscall.ENOSPC) {
				t.Errorf("DoDownload = %v, want it to wrap ENOSPC", err)
			}
			if gotHook := len(c.got) == 1; gotHook != tc.wantHook || len(c.got) > 1 {
				t.Errorf("The cache was told of %d out-of-space errors (%v), want it told once: %v", len(c.got), c.got, tc.wantHook)
			}
			if job.RemoteFailed() != tc.wantRemote {
				t.Errorf("RemoteFailed() = %v, want %v", job.RemoteFailed(), tc.wantRemote)
			}
			if c.pushed {
				t.Error("Push ran after the fetch failed, want the download to fail at once")
			}
		})
	}
}

// Without a cache there is nothing to trim, but the failure is still counted,
// and still local.
func TestDoDownload_FetchOutOfSpaceWithoutCache(t *testing.T) {
	f := newRemoteFailedFixture(t)
	failFetch(t, diskFullErr())
	job := DownloadJob{Client: f.client, Digest: f.rootDigest, Dir: t.TempDir()}

	before := cache.OutOfSpace().Errors
	if err := job.DoDownload(context.Background()); !errors.Is(err, syscall.ENOSPC) {
		t.Fatalf("DoDownload = %v, want the fetch's ENOSPC", err)
	}
	if got := cache.OutOfSpace().Errors - before; got != 1 {
		t.Errorf("Out-of-space errors counted = %d, want 1", got)
	}
	if job.RemoteFailed() {
		t.Error("RemoteFailed() = true for a fetch that ran out of space, want false: main would retry against CAS remote onto the same full disk")
	}
}

// A chunk restore that runs out of space tells the cache once and fails with
// its error. The cache is closed by then, which does not matter to the hook.
func TestDoDownload_RestoreOutOfSpace(t *testing.T) {
	tree := chunkedArtifact(t, time.Date(2020, 1, 2, 3, 4, 5, 0, time.UTC), map[string][]string{"a.img": {"content"}})
	job := newTreeJob(t, tree)
	c := &outOfSpaceCache{}
	job.Cache = c
	orig := restoreFilesTo
	t.Cleanup(func() { restoreFilesTo = orig })
	restoreFilesTo = func(string, string, func(string) string) ([]chunkerutil.Restored, error) {
		return nil, diskFullErr()
	}

	err := job.DoDownload(context.Background())
	if !errors.Is(err, syscall.ENOSPC) {
		t.Fatalf("DoDownload = %v, want the restore's ENOSPC", err)
	}
	if len(c.got) != 1 || !errors.Is(c.got[0], syscall.ENOSPC) {
		t.Errorf("The cache was told of %v, want the restore's ENOSPC once", c.got)
	}
	if job.RemoteFailed() {
		t.Error("RemoteFailed() = true for a restore that ran out of space, want false")
	}
	assertNoTmpFiles(t, job.Dir)
}

// A restore failing for another reason starts nothing.
func TestDoDownload_RestoreFailureOtherThanSpace(t *testing.T) {
	tree := chunkedArtifact(t, time.Date(2020, 1, 2, 3, 4, 5, 0, time.UTC), map[string][]string{"a.img": {"content"}})
	job := newTreeJob(t, tree)
	c := &outOfSpaceCache{}
	job.Cache = c
	orig := restoreFilesTo
	t.Cleanup(func() { restoreFilesTo = orig })
	restoreFilesTo = func(string, string, func(string) string) ([]chunkerutil.Restored, error) {
		return nil, &os.PathError{Op: "open", Path: "/tmp/dir/a.img", Err: syscall.EACCES}
	}

	if err := job.DoDownload(context.Background()); !errors.Is(err, syscall.EACCES) {
		t.Fatalf("DoDownload = %v, want the restore's EACCES", err)
	}
	if len(c.got) != 0 {
		t.Errorf("The cache was told of %v, want nothing for a failure other than space", c.got)
	}
}

// failDuplicatesWith makes materializing a duplicate fail with err for the
// rest of the test.
func failDuplicatesWith(t *testing.T, err error) {
	t.Helper()
	orig := materializeDuplicate
	t.Cleanup(func() { materializeDuplicate = orig })
	materializeDuplicate = func(dst, src *client.TreeOutput) error { return err }
}

// Copying a duplicated blob to its other paths can fill the disk too. That
// tells the cache once, with or without a local cache in the way, and the
// download fails with the error.
func TestDoDownload_DuplicateCopyOutOfSpace(t *testing.T) {
	for _, withCache := range []bool{false, true} {
		t.Run(fmt.Sprintf("withCache=%v", withCache), func(t *testing.T) {
			job, _ := newDuplicateDigestsJob(t, t.TempDir())
			c := &outOfSpaceCache{}
			if withCache {
				job.Cache = c
			}
			failDuplicatesWith(t, diskFullErr())

			before := cache.OutOfSpace().Errors
			if err := job.DoDownload(context.Background()); !errors.Is(err, syscall.ENOSPC) {
				t.Fatalf("DoDownload = %v, want the copy's ENOSPC", err)
			}
			if got := cache.OutOfSpace().Errors - before; got != 1 {
				t.Errorf("Out-of-space errors counted = %d, want 1", got)
			}
			if withCache && (len(c.got) != 1 || !errors.Is(c.got[0], syscall.ENOSPC)) {
				t.Errorf("The cache was told of %v, want the copy's ENOSPC once", c.got)
			}
		})
	}
}

// A duplicate that fails to copy for another reason starts nothing.
func TestDoDownload_DuplicateCopyFailureOtherThanSpace(t *testing.T) {
	job, _ := newDuplicateDigestsJob(t, t.TempDir())
	c := &outOfSpaceCache{}
	job.Cache = c
	failDuplicatesWith(t, &os.PathError{Op: "link", Path: "/tmp/dir/second.txt", Err: syscall.EACCES})

	if err := job.DoDownload(context.Background()); !errors.Is(err, syscall.EACCES) {
		t.Fatalf("DoDownload = %v, want the copy's EACCES", err)
	}
	if len(c.got) != 0 {
		t.Errorf("The cache was told of %v, want nothing for a failure other than space", c.got)
	}
}

// outOfSpaceHardlinkCache links blobs like hardlinkCache, so that a file has
// another link for -copy-filters to break, and records what the ENOSPC hook
// hands it.
type outOfSpaceHardlinkCache struct {
	hardlinkCache
	got []error
}

func (c *outOfSpaceHardlinkCache) OnOutOfSpace(err error) { c.got = append(c.got, err) }

// Replacing a matched file's hardlink with a private copy takes new space,
// and on a full disk that tells the cache once and fails the download.
func TestDoDownload_CopyFilterOutOfSpace(t *testing.T) {
	c := &outOfSpaceHardlinkCache{hardlinkCache: hardlinkCache{dir: t.TempDir()}}
	job := newCopyFiltersJob(t, &c.hardlinkCache, map[string][]byte{"vbmeta.img": []byte("vbmeta")})
	job.Cache = c
	job.CopyFilters = []string{`^vbmeta\.img$`}
	orig := replaceLinkWithCopy
	t.Cleanup(func() { replaceLinkWithCopy = orig })
	calls := 0
	replaceLinkWithCopy = func(string, os.FileMode) error {
		calls++
		return diskFullErr()
	}

	err := job.DoDownload(context.Background())
	if !errors.Is(err, syscall.ENOSPC) {
		t.Fatalf("DoDownload = %v, want the copy's ENOSPC", err)
	}
	if calls == 0 {
		t.Fatal("No file was replaced with a copy, so this test proves nothing")
	}
	if len(c.got) != 1 || !errors.Is(c.got[0], syscall.ENOSPC) {
		t.Errorf("The cache was told of %v, want the copy's ENOSPC once", c.got)
	}
}
