package download

import (
	"context"
	"errors"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/bazelbuild/remote-apis-sdks/go/pkg/client"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/digest"
	"github.com/google/device-infra/src/devtools/rbe/casdownloader/cache"
)

// fakeCache reports every requested blob as a cache miss, which is what drives
// downloadWithLocalCache on to the download path under test.
type fakeCache struct {
	pushCalled bool
}

func (f *fakeCache) Pull(ctx context.Context, all []*client.TreeOutput) ([]*client.TreeOutput, []*client.TreeOutput, error) {
	return nil, all, nil
}

func (f *fakeCache) Push(ctx context.Context, all map[digest.Digest]*client.TreeOutput) error {
	f.pushCalled = true
	return nil
}

func (f *fakeCache) Close() error { return nil }

// reservingCache adds pre-flight eviction, and records what it was asked for.
type reservingCache struct {
	fakeCache
	gotBytes int64
	calls    int
	err      error
}

func (r *reservingCache) EnsureHeadroom(ctx context.Context, requiredBytes int64) error {
	r.calls++
	r.gotBytes = requiredBytes
	return r.err
}

var errNoSpace = errors.New("simulated: free space below the requested size")

// TestDownloadWithLocalCache_ReservesSpaceBeforeDownloading is the point of
// this change.
//
// The bytes that fill the disk are written into the output directory during the
// download, and the cache does not see them until Push afterwards. Reserving at
// Push would therefore always be reacting to a disk that had already filled,
// which is exactly how the production ENOSPC arose. The reservation has to
// happen here, before the fetch, and for the full size of the fetch.
//
// The fake refuses the reservation, which both proves the caller respects a
// refusal and keeps the test away from a real CAS backend: returning an error
// means the download is never attempted.
func TestDownloadWithLocalCache_ReservesSpaceBeforeDownloading(t *testing.T) {
	dir := t.TempDir()
	outputs := []*client.TreeOutput{
		{Digest: digest.Digest{Hash: "aa", Size: 100}, Path: filepath.Join(dir, "a")},
		{Digest: digest.Digest{Hash: "bb", Size: 250}, Path: filepath.Join(dir, "b")},
		{Digest: digest.Digest{Hash: "cc", Size: 30}, Path: filepath.Join(dir, "c")},
	}

	c := &reservingCache{err: errNoSpace}
	job := &DownloadJob{DownloadStats: &Stats{}, Tracker: NewProxyHitTracker()}

	err := job.downloadWithLocalCache(context.Background(), c, outputs)

	if err == nil {
		t.Fatal("downloadWithLocalCache succeeded despite the cache refusing to reserve space; the download would have proceeded onto a full disk")
	}
	if !errors.Is(err, errNoSpace) {
		t.Errorf("Error = %v, want it to wrap the reservation failure", err)
	}
	if c.calls != 1 {
		t.Errorf("EnsureHeadroom called %d times, want exactly 1", c.calls)
	}
	if want := int64(380); c.gotBytes != want {
		t.Errorf("Reserved %d bytes, want %d: the reservation must cover every blob about to be downloaded", c.gotBytes, want)
	}
	if c.pushCalled {
		t.Error("Push was reached after the reservation failed; the download should have been abandoned first")
	}
}

// TestDownloadWithLocalCache_ReservationFailureMentionsSpace keeps the operator
// facing message useful. A host that is simply too small should say so, rather
// than surfacing as a generic download failure.
func TestDownloadWithLocalCache_ReservationFailureMentionsSpace(t *testing.T) {
	dir := t.TempDir()
	outputs := []*client.TreeOutput{
		{Digest: digest.Digest{Hash: "aa", Size: 1 << 30}, Path: filepath.Join(dir, "a")},
	}

	err := (&DownloadJob{DownloadStats: &Stats{}, Tracker: NewProxyHitTracker()}).downloadWithLocalCache(
		context.Background(), &reservingCache{err: errNoSpace}, outputs)
	if err == nil {
		t.Fatal("Expected an error, got nil")
	}
	if !strings.Contains(err.Error(), "disk space") {
		t.Errorf("Error = %q, want it to name disk space as the problem", err)
	}
}

// TestDownloadWithLocalCache_RemovesPulledFilesWhenReservationFails covers the
// tree left behind when we give up. Pull has already materialized whatever was
// cached by this point, and leaving those files in place would hand the caller
// a directory that looks like a partial download.
func TestDownloadWithLocalCache_RemovesPulledFilesWhenReservationFails(t *testing.T) {
	dir := t.TempDir()

	// Stand in for a file Pull had already materialized before the miss list
	// was assembled.
	pulled := filepath.Join(dir, "already-here")
	if err := os.WriteFile(pulled, []byte("from cache"), 0644); err != nil {
		t.Fatalf("Failed to write %s: %v", pulled, err)
	}

	outputs := []*client.TreeOutput{
		{Digest: digest.Digest{Hash: "aa", Size: 10}, Path: pulled},
		{Digest: digest.Digest{Hash: "bb", Size: 10}, Path: filepath.Join(dir, "missing")},
	}

	if err := (&DownloadJob{DownloadStats: &Stats{}, Tracker: NewProxyHitTracker()}).downloadWithLocalCache(
		context.Background(), &reservingCache{err: errNoSpace}, outputs); err == nil {
		t.Fatal("Expected an error, got nil")
	}

	if _, err := os.Stat(pulled); !os.IsNotExist(err) {
		t.Errorf("File materialized before the failure was left behind at %s (stat err %v)", pulled, err)
	}
}

// TestDownloadWithLocalCache_CacheWithoutReservationIsNotRequiredToProvideOne
// pins the optionality. LocalCache has no say over the filesystem and does not
// implement HeadroomReserver; asking it to would be the only thing standing
// between this change and every existing deployment.
func TestDownloadWithLocalCache_CacheWithoutReservationIsNotRequiredToProvideOne(t *testing.T) {
	var plain cache.Cache = &fakeCache{}

	if _, ok := plain.(cache.HeadroomReserver); ok {
		t.Fatal("Test fake unexpectedly implements HeadroomReserver, so this test proves nothing")
	}

	// Everything cached means downloadWithLocalCache returns before any
	// download, which lets this exercise the skip without a CAS backend.
	dir := t.TempDir()
	outputs := []*client.TreeOutput{{Digest: digest.Digest{Hash: "aa", Size: 10}, Path: filepath.Join(dir, "a")}}
	allCached := &everythingCachedCache{}

	if err := (&DownloadJob{DownloadStats: &Stats{}, Tracker: NewProxyHitTracker()}).downloadWithLocalCache(
		context.Background(), allCached, outputs); err != nil {
		t.Errorf("downloadWithLocalCache with a cache that cannot reserve space failed: %v", err)
	}
}

// everythingCachedCache reports a full cache hit, so no download is needed.
type everythingCachedCache struct{ fakeCache }

func (e *everythingCachedCache) Pull(ctx context.Context, all []*client.TreeOutput) ([]*client.TreeOutput, []*client.TreeOutput, error) {
	return all, nil, nil
}
