package download

import (
	"context"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"testing"

	"github.com/bazelbuild/remote-apis-sdks/go/pkg/client"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/digest"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/fakes"
	repb "github.com/bazelbuild/remote-apis/build/bazel/remote/execution/v2"
	"github.com/google/device-infra/src/devtools/rbe/casdownloader/cache"
	"google.golang.org/protobuf/proto"
)

// remoteFailedFixture is a fake CAS holding a root directory with two files
// that share one blob, so that a download fetches a.txt and then has to copy
// or link it to b.txt locally.
type remoteFailedFixture struct {
	client     *client.Client
	rootDigest string
	blobSize   int64
}

func newRemoteFailedFixture(t *testing.T) *remoteFailedFixture {
	return newRemoteFailedFixtureWithBlob(t, true)
}

// newRemoteFailedFixtureWithBlob is newRemoteFailedFixture, except that with
// uploadBlob false the tree is served but the file blob it names is not, so
// the download fails on the fetch itself rather than on the tree.
func newRemoteFailedFixtureWithBlob(t *testing.T, uploadBlob bool) *remoteFailedFixture {
	t.Helper()
	ctx := context.Background()
	fakeServer, err := fakes.NewServer(t)
	if err != nil {
		t.Fatalf("Failed to create fake RBE server: %v", err)
	}
	t.Cleanup(fakeServer.Stop)

	contents := []byte("shared contents")
	dFile := digest.NewFromBlob(contents)
	if uploadBlob {
		fakeServer.CAS.Put(contents)
	}
	fileDigest := &repb.Digest{Hash: dFile.Hash, SizeBytes: dFile.Size}
	rootBytes, err := proto.Marshal(&repb.Directory{
		Files: []*repb.FileNode{
			{Name: "a.txt", Digest: fileDigest},
			{Name: "b.txt", Digest: fileDigest},
		},
	})
	if err != nil {
		t.Fatal(err)
	}
	dRoot := fakeServer.CAS.Put(rootBytes)

	c, err := fakeServer.NewTestClient(ctx)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { c.Close() })

	return &remoteFailedFixture{
		client:     c,
		rootDigest: fmt.Sprintf("%s/%d", dRoot.Hash, dRoot.Size),
		blobSize:   dFile.Size,
	}
}

// missingCache misses on everything and accepts whatever is pushed, recording
// that it was. It has no say over disk space, so the download goes ahead.
type missingCache struct {
	pushed bool
}

func (m *missingCache) Pull(ctx context.Context, all []*client.TreeOutput) ([]*client.TreeOutput, []*client.TreeOutput, error) {
	return nil, all, nil
}

func (m *missingCache) Push(ctx context.Context, all map[digest.Digest]*client.TreeOutput) error {
	m.pushed = true
	return nil
}

func (m *missingCache) Close() error { return nil }

// fullCache reports every file as already cached, so nothing is fetched.
type fullCache struct{ missingCache }

func (f *fullCache) Pull(ctx context.Context, all []*client.TreeOutput) ([]*client.TreeOutput, []*client.TreeOutput, error) {
	return all, nil, nil
}

// noSpaceCache misses on everything and then refuses to reserve room for the
// fetch, which is how the cache reports a full disk.
type noSpaceCache struct{}

func (noSpaceCache) Pull(ctx context.Context, all []*client.TreeOutput) ([]*client.TreeOutput, []*client.TreeOutput, error) {
	return nil, all, nil
}

func (noSpaceCache) Push(ctx context.Context, all map[digest.Digest]*client.TreeOutput) error {
	return nil
}

func (noSpaceCache) Close() error { return nil }

func (noSpaceCache) EnsureHeadroom(ctx context.Context, requiredBytes int64) error {
	return errors.New("simulated: free space below the requested size")
}

// TestDoDownload_RemoteFailed_WhenRootIsMissing covers the case the fallback in
// main exists for: the remote could not serve the tree. That is the only kind
// of failure a different remote might fix.
func TestDoDownload_RemoteFailed_WhenRootIsMissing(t *testing.T) {
	f := newRemoteFailedFixture(t)
	missing := digest.NewFromBlob([]byte("never uploaded"))

	job := DownloadJob{
		Client: f.client,
		Digest: fmt.Sprintf("%s/%d", missing.Hash, missing.Size),
		Dir:    t.TempDir(),
	}
	if err := job.DoDownload(context.Background()); err == nil {
		t.Fatal("DoDownload of a digest absent from CAS succeeded")
	}
	if !job.RemoteFailed() {
		t.Error("RemoteFailed() = false after the remote failed to serve the root directory, want true: main would skip the fallback that could have recovered")
	}
}

// TestDoDownload_NotRemoteFailed_WhenDiskIsFull covers a local failure before
// any blob is fetched. Switching remotes cannot create disk space.
func TestDoDownload_NotRemoteFailed_WhenDiskIsFull(t *testing.T) {
	f := newRemoteFailedFixture(t)

	job := DownloadJob{
		Client: f.client,
		Digest: f.rootDigest,
		Dir:    t.TempDir(),
		Cache:  noSpaceCache{},
	}
	if err := job.DoDownload(context.Background()); err == nil {
		t.Fatal("DoDownload succeeded despite the cache refusing to reserve space")
	}
	if job.RemoteFailed() {
		t.Error("RemoteFailed() = true for a disk-space failure, want false: main would retry against CAS remote onto the same full disk")
	}
}

// TestDoDownload_NotRemoteFailed_WhenLocalCopyFailsAfterFetch covers a local
// failure after every blob has been fetched. The remote did its job; a retry
// against another one would re-fetch for nothing and, with a local cache,
// misbook the first attempt's bytes as local hits.
func TestDoDownload_NotRemoteFailed_WhenLocalCopyFailsAfterFetch(t *testing.T) {
	f := newRemoteFailedFixture(t)
	dir := t.TempDir()

	// b.txt is the duplicate, copied from a.txt after the fetch. Occupying its
	// path makes that copy fail.
	if err := os.WriteFile(filepath.Join(dir, "b.txt"), []byte("in the way"), 0o600); err != nil {
		t.Fatal(err)
	}

	job := DownloadJob{
		Client: f.client,
		Digest: f.rootDigest,
		Dir:    dir,
	}
	if err := job.DoDownload(context.Background()); err == nil {
		t.Fatal("DoDownload succeeded despite the duplicate's path being occupied")
	}
	if job.RemoteFailed() {
		t.Error("RemoteFailed() = true for a failure copying a duplicate after the fetch, want false")
	}
}

// TestDoDownload_RemoteFailedResetsOnRetry pins that the flag describes the
// last attempt only. main reuses the job for its fallback, and a stale true
// would misdescribe the retry.
func TestDoDownload_RemoteFailedResetsOnRetry(t *testing.T) {
	f := newRemoteFailedFixture(t)
	missing := digest.NewFromBlob([]byte("never uploaded"))

	job := DownloadJob{
		Client: f.client,
		Digest: fmt.Sprintf("%s/%d", missing.Hash, missing.Size),
		Dir:    t.TempDir(),
	}
	_ = job.DoDownload(context.Background())
	if !job.RemoteFailed() {
		t.Fatal("RemoteFailed() = false after the first attempt failed remotely; the rest of the test proves nothing")
	}

	job.Digest = f.rootDigest
	if err := job.DoDownload(context.Background()); err != nil {
		t.Fatalf("Retry DoDownload failed: %v", err)
	}
	if job.RemoteFailed() {
		t.Error("RemoteFailed() = true after a successful retry, want false")
	}
}

// TestDoDownload_RemoteFailed_WhenBlobIsMissing covers a remote that serves the
// tree but not the blobs in it, with and without a local cache. This is the
// fetch itself failing, the commonest way a casproxy dies mid-download.
func TestDoDownload_RemoteFailed_WhenBlobIsMissing(t *testing.T) {
	for _, tc := range []struct {
		name  string
		cache cache.Cache
	}{
		{name: "without local cache"},
		{name: "with local cache", cache: &missingCache{}},
	} {
		t.Run(tc.name, func(t *testing.T) {
			f := newRemoteFailedFixtureWithBlob(t, false)
			job := DownloadJob{
				Client: f.client,
				Digest: f.rootDigest,
				Dir:    t.TempDir(),
				Cache:  tc.cache,
			}
			if err := job.DoDownload(context.Background()); err == nil {
				t.Fatal("DoDownload of a tree whose blob is absent from CAS succeeded")
			}
			if !job.RemoteFailed() {
				t.Error("RemoteFailed() = false after the blob fetch failed, want true: main would skip the fallback that could have recovered")
			}
		})
	}
}

// TestDoDownload_NotRemoteFailed_WhenCachedDownloadSucceeds covers the ordinary
// run through a local cache: fetch, attribute, push. Nothing failed, so there
// is nothing to fall back from, and the fetched blob is booked as fetched.
func TestDoDownload_NotRemoteFailed_WhenCachedDownloadSucceeds(t *testing.T) {
	f := newRemoteFailedFixture(t)
	c := &missingCache{}
	job := DownloadJob{
		Client: f.client,
		Digest: f.rootDigest,
		Dir:    t.TempDir(),
		Cache:  c,
	}
	if err := job.DoDownload(context.Background()); err != nil {
		t.Fatalf("DoDownload failed: %v", err)
	}
	if job.RemoteFailed() {
		t.Error("RemoteFailed() = true after a successful download, want false")
	}
	if !c.pushed {
		t.Error("The fetched blob was never pushed to the local cache")
	}
	// a.txt was fetched straight from CAS remote; b.txt shares its blob.
	stats := job.Stats()
	if stats.SizeCold != f.blobSize || stats.CountCold != 1 {
		t.Errorf("Cold stats: got size=%d count=%d, want size=%d count=1", stats.SizeCold, stats.CountCold, f.blobSize)
	}
	if stats.SizeDedup != f.blobSize || stats.CountDedup != 1 {
		t.Errorf("Dedup stats: got size=%d count=%d, want size=%d count=1", stats.SizeDedup, stats.CountDedup, f.blobSize)
	}
}

// TestDoDownload_FullCacheHitIsAllHot covers a tree the local cache already
// holds in full. Nothing is fetched, but the stats must still account for every
// byte; left unset, the run would report a tree of size zero.
func TestDoDownload_FullCacheHitIsAllHot(t *testing.T) {
	f := newRemoteFailedFixture(t)
	job := DownloadJob{
		Client: f.client,
		Digest: f.rootDigest,
		Dir:    t.TempDir(),
		Cache:  &fullCache{},
	}
	if err := job.DoDownload(context.Background()); err != nil {
		t.Fatalf("DoDownload failed: %v", err)
	}
	stats := job.Stats()
	if want := 2 * f.blobSize; stats.SizeHot != want || stats.CountHot != 2 {
		t.Errorf("Hot stats: got size=%d count=%d, want size=%d count=2", stats.SizeHot, stats.CountHot, want)
	}
	if job.TransferredSize() != 0 {
		t.Errorf("TransferredSize() = %d, want 0 for a full cache hit", job.TransferredSize())
	}
}
