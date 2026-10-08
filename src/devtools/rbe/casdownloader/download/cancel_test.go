package download

import (
	"context"
	"errors"
	"fmt"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/bazelbuild/remote-apis-sdks/go/pkg/client"
)

// errSimulatedSignal stands in for the cause a signal gives the context.
var errSimulatedSignal = fmt.Errorf("simulated signal: %w", context.Canceled)

// cancelOnDuplicate makes materializing a duplicate, a local step that does
// not watch the context, cancel the download's context with
// errSimulatedSignal, and then succeed, as a signal arriving there would.
func cancelOnDuplicate(t *testing.T, cancel context.CancelCauseFunc) {
	t.Helper()
	orig := materializeDuplicate
	t.Cleanup(func() { materializeDuplicate = orig })
	materializeDuplicate = func(dst, src *client.TreeOutput) error {
		cancel(errSimulatedSignal)
		return orig(dst, src)
	}
}

// assertCancelled checks that err and the stats report the download as
// cancelled by errSimulatedSignal, not as timed out.
func assertCancelled(t *testing.T, job *DownloadJob, err error) {
	t.Helper()
	if !errors.Is(err, errSimulatedSignal) || !errors.Is(err, context.Canceled) {
		t.Errorf("DoDownload = %v, want it to wrap the cancellation cause", err)
	}
	if got := job.DownloadStats.DownloadError; !strings.HasPrefix(got, "CANCELLED: ") || !strings.Contains(got, "simulated signal") {
		t.Errorf("DownloadError = %q, want CANCELLED with the cause", got)
	}
}

// TestDoDownload_CancelledDuringLocalWork cancels a download while it
// materializes duplicates, after which nothing it does watches the context
// until commit. It must not commit: files that were in Dir keep their
// content, and what it wrote is removed.
func TestDoDownload_CancelledDuringLocalWork(t *testing.T) {
	for _, cv := range cacheVariants {
		t.Run(cv.name, func(t *testing.T) {
			job := newTreeJob(t, map[string][]byte{
				"a.txt":       []byte("same"),
				"new/dup.txt": []byte("same"),
			})
			job.Cache = cv.cache(t)
			writeFile(t, filepath.Join(job.Dir, "a.txt"), "old a", 0o600)
			ctx, cancel := context.WithCancelCause(context.Background())
			defer cancel(nil)
			cancelOnDuplicate(t, cancel)

			err := job.DoDownload(ctx)
			assertCancelled(t, job, err)
			if got := readFile(t, filepath.Join(job.Dir, "a.txt")); got != "old a" {
				t.Errorf("a.txt = %q, want it unchanged", got)
			}
			if exists(t, filepath.Join(job.Dir, "new")) {
				t.Error("Directory new, created by the cancelled download, was left behind")
			}
			assertNoTmpFiles(t, job.Dir)
		})
	}
}

// TestDoDownload_CancelledBeforeRestore cancels the download of a chunked
// artifact before it restores. It restores nothing and removes the chunk
// data.
func TestDoDownload_CancelledBeforeRestore(t *testing.T) {
	tree := chunkedArtifact(t, time.Date(2020, 1, 2, 3, 4, 5, 0, time.UTC), map[string][]string{"a.img": {"content"}})
	// Files outside the chunk data, so that there is a duplicate to cancel
	// on.
	tree["x.txt"] = []byte("same")
	tree["y.txt"] = []byte("same")
	job := newTreeJob(t, tree)
	ctx, cancel := context.WithCancelCause(context.Background())
	defer cancel(nil)
	cancelOnDuplicate(t, cancel)

	err := job.DoDownload(ctx)
	assertCancelled(t, job, err)
	for _, name := range []string{"a.img", "x.txt", "y.txt", "_chunks"} {
		if exists(t, filepath.Join(job.Dir, name)) {
			t.Errorf("%s exists after a cancelled download, want nothing written", name)
		}
	}
	assertNoTmpFiles(t, job.Dir)
}

// TestDoDownload_CancelledDuringCommit cancels the context once the download
// has started to commit, as a signal arriving then would. Commit runs to the
// end, and the download succeeded.
func TestDoDownload_CancelledDuringCommit(t *testing.T) {
	job := newTreeJob(t, map[string][]byte{"a.txt": []byte("a"), "b.txt": []byte("b")})
	ctx, cancel := context.WithCancelCause(context.Background())
	defer cancel(nil)
	orig := linkNoReplace
	t.Cleanup(func() { linkNoReplace = orig })
	linkNoReplace = func(oldname, newname string) error {
		cancel(errSimulatedSignal)
		return orig(oldname, newname)
	}

	if err := job.DoDownload(ctx); err != nil {
		t.Fatalf("DoDownload = %v, want success, since it had started to commit", err)
	}
	if got := job.DownloadStats.DownloadError; got != "" {
		t.Errorf("DownloadError = %q, want none", got)
	}
	for path, want := range map[string]string{"a.txt": "a", "b.txt": "b"} {
		if got := readFile(t, filepath.Join(job.Dir, path)); got != want {
			t.Errorf("%s = %q, want %q", path, got, want)
		}
	}
	assertNoTmpFiles(t, job.Dir)
}
