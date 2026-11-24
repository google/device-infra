package download

import (
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"os"
	"path/filepath"
	"sync"
	"testing"
	"time"

	"github.com/bazelbuild/remote-apis-sdks/go/pkg/client"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/digest"
	"github.com/google/device-infra/src/devtools/rbe/casdownloader/cache"
	"github.com/google/device-infra/src/devtools/rbe/common/chunker"
)

// flipBitInPlace flips one bit in the middle of the file at path without
// replacing its inode, as a RAM or page cache error would. Every hard link to
// the file sees the change.
func flipBitInPlace(t *testing.T, path string) {
	t.Helper()
	b, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("Failed to read %s: %v", path, err)
	}
	b[len(b)/2] ^= 0x08
	if err := os.WriteFile(path, b, 0o640); err != nil {
		t.Fatalf("Failed to write %s: %v", path, err)
	}
}

// cachedBlob returns the path of the blob named hash under cacheDir, in
// whichever layout the cache uses, or "" if there is none.
func cachedBlob(t *testing.T, cacheDir, hash string) string {
	t.Helper()
	var found string
	err := filepath.WalkDir(cacheDir, func(path string, d os.DirEntry, err error) error {
		if err != nil {
			return err
		}
		if !d.IsDir() && d.Name() == hash {
			found = path
		}
		return nil
	})
	if err != nil && !os.IsNotExist(err) {
		t.Fatalf("Failed to walk %s: %v", cacheDir, err)
	}
	return found
}

func sha256Hex(s string) string {
	sum := sha256.Sum256([]byte(s))
	return hex.EncodeToString(sum[:])
}

// integrityCacheModes are the real local caches, in each way casdownloader
// can materialize a hit: as a hard link to the cached blob or as a copy.
var integrityCacheModes = []struct {
	name string
	opts cache.Options
}{
	{name: "luci_hardlink", opts: cache.Options{UseHardlink: true}},
	{name: "luci_hardlink_locked", opts: cache.Options{UseHardlink: true, Lock: true}},
	{name: "luci_copy", opts: cache.Options{}},
	{name: "lockfree_hardlink", opts: cache.Options{LockFree: true, UseHardlink: true}},
	{name: "lockfree_copy", opts: cache.Options{LockFree: true}},
}

// TestDoDownload_CorruptCachedChunkIsEvictedAndRefetched is the self-healing
// path for a chunk poisoned in the local cache, as by a bit flip on a host
// without ECC memory after the chunk was downloaded and verified, when
// restored chunks are verified.
//
// The download that is served the bad chunk must fail rather than restore a
// corrupt file, leave nothing behind, and remove the chunk from the cache.
// The next download must then fetch the chunk again and succeed, instead of
// failing on the same bytes until the blob happens to be evicted.
func TestDoDownload_CorruptCachedChunkIsEvictedAndRefetched(t *testing.T) {
	const (
		firstChunk  = "first chunk 1111 "
		secondChunk = "second chunk 1111"
		otherChunk  = "other image 1111"
	)
	modTime := time.Date(2020, 1, 2, 3, 4, 5, 0, time.UTC)
	tree := chunkedArtifact(t, modTime, map[string][]string{
		"multi.img": {firstChunk, secondChunk},
		"other.img": {otherChunk},
	})
	wantFiles := map[string]string{"multi.img": firstChunk + secondChunk, "other.img": otherChunk}
	for _, mode := range integrityCacheModes {
		t.Run(mode.name, func(t *testing.T) {
			job := newTreeJob(t, tree)
			job.VerifyRestoredChunks = true
			opts := mode.opts
			opts.Dir = t.TempDir()
			download := func() error {
				t.Helper()
				// DoDownload closes the cache, so each run opens its own.
				c, err := cache.Open(opts)
				if err != nil {
					t.Fatalf("cache.Open failed: %v", err)
				}
				job.Cache = c
				job.Dir = t.TempDir()
				return job.DoDownload(t.Context())
			}

			if err := download(); err != nil {
				t.Fatalf("First DoDownload failed: %v", err)
			}
			bad := sha256Hex(secondChunk)
			blob := cachedBlob(t, opts.Dir, bad)
			if blob == "" {
				t.Fatalf("Chunk %s is not in the cache after the first download", bad)
			}
			flipBitInPlace(t, blob)

			err := download()
			var mismatch *chunker.ChunkMismatchError
			if !errors.As(err, &mismatch) || mismatch.Want != bad {
				t.Fatalf("DoDownload served a corrupt chunk = %v, want a *chunker.ChunkMismatchError for chunk %s", err, bad)
			}
			if got := job.Stats().CorruptChunks; got != 1 {
				t.Errorf("Stats().CorruptChunks = %d, want 1", got)
			}
			if job.RemoteFailed() {
				t.Error("RemoteFailed() = true for a chunk corrupted in the local cache, want false: CAS remote would not help")
			}
			if exists(t, filepath.Join(job.Dir, "multi.img")) || exists(t, filepath.Join(job.Dir, "_chunks")) {
				t.Errorf("The failed download left files in %s", job.Dir)
			}
			assertNoTmpFiles(t, job.Dir)
			if p := cachedBlob(t, opts.Dir, bad); p != "" {
				t.Errorf("Corrupt chunk %s is still in the cache at %s", bad, p)
			}
			if cachedBlob(t, opts.Dir, sha256Hex(firstChunk)) == "" {
				t.Errorf("Intact chunk %s was removed from the cache along with the corrupt one", sha256Hex(firstChunk))
			}

			if err := download(); err != nil {
				t.Fatalf("DoDownload after the corrupt chunk was evicted failed: %v", err)
			}
			for path, want := range wantFiles {
				if got := readFile(t, filepath.Join(job.Dir, path)); got != want {
					t.Errorf("After re-fetching the chunk, %s = %q, want %q", path, got, want)
				}
			}
			if p := cachedBlob(t, opts.Dir, bad); p == "" || readFile(t, p) != secondChunk {
				t.Errorf("Chunk %s was not cached again intact after it was re-fetched (path %q)", bad, p)
			}
		})
	}
}

// TestDoDownload_CorruptCachedChunkIsNotVerifiedByDefault pins what a
// download does with a chunk poisoned in the local cache when restored chunks
// are not verified, which is the default: chunks are copied without being
// read into user space, so the download succeeds, restores the corrupt bytes,
// counts no corrupt chunk and leaves the blob in the cache.
func TestDoDownload_CorruptCachedChunkIsNotVerifiedByDefault(t *testing.T) {
	const firstChunk, secondChunk = "first chunk 1111 ", "second chunk 1111"
	tree := chunkedArtifact(t, time.Date(2020, 1, 2, 3, 4, 5, 0, time.UTC), map[string][]string{
		"multi.img": {firstChunk, secondChunk},
	})
	for _, mode := range integrityCacheModes {
		t.Run(mode.name, func(t *testing.T) {
			job := newTreeJob(t, tree)
			opts := mode.opts
			opts.Dir = t.TempDir()
			download := func() error {
				t.Helper()
				// DoDownload closes the cache, so each run opens its own.
				c, err := cache.Open(opts)
				if err != nil {
					t.Fatalf("cache.Open failed: %v", err)
				}
				job.Cache = c
				job.Dir = t.TempDir()
				return job.DoDownload(t.Context())
			}

			if err := download(); err != nil {
				t.Fatalf("First DoDownload failed: %v", err)
			}
			bad := sha256Hex(secondChunk)
			blob := cachedBlob(t, opts.Dir, bad)
			if blob == "" {
				t.Fatalf("Chunk %s is not in the cache after the first download", bad)
			}
			flipBitInPlace(t, blob)
			corrupted := readFile(t, blob)

			if err := download(); err != nil {
				t.Fatalf("DoDownload without VerifyRestoredChunks served a corrupt chunk = %v, want it to restore the chunk unchecked", err)
			}
			if got, want := readFile(t, filepath.Join(job.Dir, "multi.img")), firstChunk+corrupted; got != want {
				t.Errorf("DoDownload without VerifyRestoredChunks restored multi.img = %q, want the cached chunks' bytes %q", got, want)
			}
			if got := job.Stats().CorruptChunks; got != 0 {
				t.Errorf("Stats().CorruptChunks = %d without VerifyRestoredChunks, want 0", got)
			}
			if cachedBlob(t, opts.Dir, bad) == "" {
				t.Errorf("Chunk %s was removed from the cache without VerifyRestoredChunks", bad)
			}
		})
	}
}

// corruptAfterFetch makes every fetch write the blob with digest dg as
// corrupt would have it, after the SDK has written it, until the returned
// function is called or the test ends. It stands in for a BatchReadBlobs
// response corrupted before it reached the disk, which the SDK does not
// check.
func corruptAfterFetch(t *testing.T, dg digest.Digest, corrupt func([]byte) []byte) (stop func()) {
	t.Helper()
	orig := downloadFiles
	stop = func() { downloadFiles = orig }
	t.Cleanup(stop)
	downloadFiles = func(c *client.Client, ctx context.Context, outDir string, outputs map[digest.Digest]*client.TreeOutput) (*client.MovedBytesMetadata, error) {
		stats, err := orig(c, ctx, outDir, outputs)
		if err != nil {
			return stats, err
		}
		if out, ok := outputs[dg]; ok {
			path := filepath.Join(outDir, out.Path)
			b, err := os.ReadFile(path)
			if err != nil {
				return nil, err
			}
			if err := os.WriteFile(path, corrupt(b), 0o640); err != nil {
				return nil, err
			}
		}
		return stats, nil
	}
	return stop
}

// blobCorruptions are the ways a batched blob can reach the disk wrong: a
// flipped bit, and a size that differs from its digest's.
var blobCorruptions = []struct {
	name    string
	corrupt func([]byte) []byte
}{
	{"bit_flip", func(b []byte) []byte {
		b = bytes.Clone(b)
		b[len(b)/2] ^= 0x08
		return b
	}},
	{"truncated", func(b []byte) []byte { return b[:len(b)-1] }},
	{"extra_byte", func(b []byte) []byte { return append(bytes.Clone(b), 'x') }},
}

// TestDoDownload_CorruptBatchedBlobIsRejected corrupts a small blob between
// the fetch and the disk, where the SDK's batch path does not look. The
// download must fail with a digest mismatch before anything is committed or
// pushed to the local cache, so that neither the caller's tree nor the cache
// holds the bad bytes, and must report the failure as remote, so that main
// retries it against CAS remote rather than casproxy. A download that is not
// corrupted must then succeed and cache the blob.
func TestDoDownload_CorruptBatchedBlobIsRejected(t *testing.T) {
	const small, other = "a small blob 1111", "another small blob"
	dSmall := digest.NewFromBlob([]byte(small))
	variants := []struct {
		name string
		opts *cache.Options // nil means no local cache.
	}{
		{name: "no_cache"},
		{name: "luci_hardlink", opts: &cache.Options{UseHardlink: true}},
		{name: "luci_copy", opts: &cache.Options{}},
		{name: "lockfree_hardlink", opts: &cache.Options{LockFree: true, UseHardlink: true}},
		{name: "lockfree_copy", opts: &cache.Options{LockFree: true}},
	}
	for _, v := range variants {
		for _, bc := range blobCorruptions {
			t.Run(v.name+"_"+bc.name, func(t *testing.T) {
				job := newTreeJob(t, map[string][]byte{"small.txt": []byte(small), "other.txt": []byte(other)})
				cacheDir := t.TempDir()
				download := func() error {
					t.Helper()
					job.Cache = nil
					if v.opts != nil {
						opts := *v.opts
						opts.Dir = cacheDir
						c, err := cache.Open(opts)
						if err != nil {
							t.Fatalf("cache.Open failed: %v", err)
						}
						job.Cache = c
					}
					job.Dir = t.TempDir()
					return job.DoDownload(t.Context())
				}
				stop := corruptAfterFetch(t, dSmall, bc.corrupt)

				err := download()
				if !errors.Is(err, errBlobDigestMismatch) {
					t.Fatalf("DoDownload of a corrupted batched blob = %v, want an error wrapping %v", err, errBlobDigestMismatch)
				}
				if !job.RemoteFailed() {
					t.Error("RemoteFailed() = false for a corrupt blob from the remote, want true so that main retries against CAS remote")
				}
				if exists(t, filepath.Join(job.Dir, "small.txt")) || exists(t, filepath.Join(job.Dir, "other.txt")) {
					t.Errorf("The failed download committed files to %s", job.Dir)
				}
				assertNoTmpFiles(t, job.Dir)
				for _, content := range []string{small, other} {
					if p := cachedBlob(t, cacheDir, digest.NewFromBlob([]byte(content)).Hash); p != "" {
						t.Errorf("Blob of %q was pushed to the local cache, at %s, by a download that failed verification", content, p)
					}
				}

				stop()
				if err := download(); err != nil {
					t.Fatalf("DoDownload without corruption failed: %v", err)
				}
				if got := readFile(t, filepath.Join(job.Dir, "small.txt")); got != small {
					t.Errorf("small.txt = %q, want %q", got, small)
				}
				if v.opts != nil && cachedBlob(t, cacheDir, dSmall.Hash) == "" {
					t.Error("Blob of small.txt was not cached by the download that succeeded")
				}
			})
		}
	}
}

// recordVerified records, for the rest of the test, the digests whose
// downloaded files are verified.
func recordVerified(t *testing.T) func() map[digest.Digest]bool {
	t.Helper()
	var mu sync.Mutex
	verified := make(map[digest.Digest]bool)
	orig := verifyDownloadedBlob
	t.Cleanup(func() { verifyDownloadedBlob = orig })
	verifyDownloadedBlob = func(path string, dg digest.Digest) error {
		mu.Lock()
		verified[dg] = true
		mu.Unlock()
		return orig(path, dg)
	}
	return func() map[digest.Digest]bool {
		mu.Lock()
		defer mu.Unlock()
		got := verified
		verified = make(map[digest.Digest]bool)
		return got
	}
}

// TestDoDownload_VerifiesOnlyBatchEligibleDownloads pins what the batch check
// reads. It checks the blobs the SDK may have batched, which are those no
// larger than Client.MaxBatchSize, including an empty one. It leaves blobs
// over the limit, which the SDK streams and verifies itself, and local cache
// hits, which are linked in O(1), unread.
func TestDoDownload_VerifiesOnlyBatchEligibleDownloads(t *testing.T) {
	const maxBatchSize = 1024
	small := []byte("a small blob 1111")
	atLimit := bytes.Repeat([]byte("l"), maxBatchSize)
	large := bytes.Repeat([]byte("L"), maxBatchSize+1)
	files := map[string][]byte{
		"empty.txt":    {},
		"small.txt":    small,
		"dup.txt":      small,
		"at_limit.bin": atLimit,
		"large.bin":    large,
	}
	job := newTreeJob(t, files)
	job.Client.MaxBatchSize = maxBatchSize
	verified := recordVerified(t)
	opts := cache.Options{LockFree: true, UseHardlink: true, Dir: t.TempDir()}

	for i, want := range []map[digest.Digest]bool{
		{digest.Empty: true, digest.NewFromBlob(small): true, digest.NewFromBlob(atLimit): true},
		// The second download is served entirely by the cache.
		{},
	} {
		c, err := cache.Open(opts)
		if err != nil {
			t.Fatalf("cache.Open failed: %v", err)
		}
		job.Cache = c
		job.Dir = t.TempDir()
		if err := job.DoDownload(t.Context()); err != nil {
			t.Fatalf("DoDownload #%d failed: %v", i+1, err)
		}
		for path, content := range files {
			if got := readFile(t, filepath.Join(job.Dir, path)); got != string(content) {
				t.Errorf("After DoDownload #%d, %s holds %d bytes that differ from the %d expected", i+1, path, len(got), len(content))
			}
		}
		got := verified()
		if len(got) != len(want) {
			t.Errorf("DoDownload #%d verified %v, want %v", i+1, got, want)
			continue
		}
		for dg := range want {
			if !got[dg] {
				t.Errorf("DoDownload #%d verified %v, want %v", i+1, got, want)
				break
			}
		}
	}
}

// TestDoDownload_CorruptChunkWithoutCacheFails corrupts a chunk too large for
// the batch check after it is fetched, with no local cache, and restored
// chunks verified. The restore is the last line of defense: it must fail the
// download and count the chunk, with nothing to evict.
func TestDoDownload_CorruptChunkWithoutCacheFails(t *testing.T) {
	chunk := string(bytes.Repeat([]byte("c"), 64))
	tree := chunkedArtifact(t, time.Date(2020, 1, 2, 3, 4, 5, 0, time.UTC), map[string][]string{"a.img": {chunk, "tail 1111"}})
	job := newTreeJob(t, tree)
	job.VerifyRestoredChunks = true
	// Every blob of the tree is over the limit, so none is checked after the
	// fetch, as for a chunk the SDK streamed.
	job.Client.MaxBatchSize = 1
	corruptAfterFetch(t, digest.NewFromBlob([]byte(chunk)), blobCorruptions[0].corrupt)

	err := job.DoDownload(t.Context())
	var mismatch *chunker.ChunkMismatchError
	if !errors.As(err, &mismatch) || mismatch.Want != sha256Hex(chunk) {
		t.Fatalf("DoDownload with a corrupt chunk = %v, want a *chunker.ChunkMismatchError for chunk %s", err, sha256Hex(chunk))
	}
	if got := job.Stats().CorruptChunks; got != 1 {
		t.Errorf("Stats().CorruptChunks = %d, want 1", got)
	}
	if exists(t, filepath.Join(job.Dir, "a.img")) {
		t.Error("a.img was restored from a corrupt chunk")
	}
	assertNoTmpFiles(t, job.Dir)
}

// TestVerifyBatchEligibleBlobs_CanceledContextFails checks a batch-eligible
// blob with a context that is already done. The check stops without reading
// the blob, so it must fail with the context's error rather than report the
// blob as verified.
func TestVerifyBatchEligibleBlobs_CanceledContextFails(t *testing.T) {
	const content = "a small blob 1111"
	job := newTreeJob(t, map[string][]byte{"small.txt": []byte(content)})
	dg := digest.NewFromBlob([]byte(content))
	path := filepath.Join(t.TempDir(), "blob")
	writeFile(t, path, content, 0o640)
	ctx, cancel := context.WithCancel(t.Context())
	cancel()

	downloaded := map[digest.Digest]*client.TreeOutput{dg: {Digest: dg, Path: path}}
	if err := job.verifyBatchEligibleBlobs(ctx, downloaded); !errors.Is(err, context.Canceled) {
		t.Errorf("verifyBatchEligibleBlobs(canceled context, %v) = %v, want an error wrapping %v", dg, err, context.Canceled)
	}
}

// TestDoDownload_CanceledAfterFetchCachesNothing cancels a download as the
// fetch returns, before the batch check has read what was fetched. The
// download must fail with the cancellation, and must neither push the
// unverified blob to the local cache nor commit it.
func TestDoDownload_CanceledAfterFetchCachesNothing(t *testing.T) {
	const small = "a small blob 1111"
	dSmall := digest.NewFromBlob([]byte(small))
	for _, mode := range integrityCacheModes {
		t.Run(mode.name, func(t *testing.T) {
			job := newTreeJob(t, map[string][]byte{"small.txt": []byte(small)})
			opts := mode.opts
			opts.Dir = t.TempDir()
			c, err := cache.Open(opts)
			if err != nil {
				t.Fatalf("cache.Open failed: %v", err)
			}
			job.Cache = c
			ctx, cancel := context.WithCancel(t.Context())
			defer cancel()
			orig := downloadFiles
			t.Cleanup(func() { downloadFiles = orig })
			downloadFiles = func(c *client.Client, ctx context.Context, outDir string, outputs map[digest.Digest]*client.TreeOutput) (*client.MovedBytesMetadata, error) {
				stats, err := orig(c, ctx, outDir, outputs)
				cancel()
				return stats, err
			}

			if err := job.DoDownload(ctx); !errors.Is(err, context.Canceled) {
				t.Errorf("DoDownload canceled after the fetch = %v, want an error wrapping %v", err, context.Canceled)
			}
			if p := cachedBlob(t, opts.Dir, dSmall.Hash); p != "" {
				t.Errorf("Unverified blob %s was pushed to the local cache, at %s, by a canceled download", dSmall, p)
			}
			if exists(t, filepath.Join(job.Dir, "small.txt")) {
				t.Errorf("The canceled download committed small.txt to %s", job.Dir)
			}
		})
	}
}

func TestVerifyDownloadedBlob(t *testing.T) {
	const content = "downloaded content"
	dg := digest.NewFromBlob([]byte(content))
	tests := []struct {
		name         string
		onDisk       string
		missing      bool
		wantErr      bool
		wantMismatch bool
	}{
		{name: "match", onDisk: content},
		{name: "bit_flip", onDisk: "downloaded cOntent", wantErr: true, wantMismatch: true},
		{name: "truncated", onDisk: content[:len(content)-1], wantErr: true, wantMismatch: true},
		{name: "extra_byte", onDisk: content + "x", wantErr: true, wantMismatch: true},
		{name: "missing", missing: true, wantErr: true},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			path := filepath.Join(t.TempDir(), "blob")
			if !tc.missing {
				writeFile(t, path, tc.onDisk, 0o640)
			}
			err := verifyDownloadedBlob(path, dg)
			if (err != nil) != tc.wantErr || errors.Is(err, errBlobDigestMismatch) != tc.wantMismatch {
				t.Errorf("verifyDownloadedBlob(file holding %q (missing: %v), %v) = %v, want error: %v, digest mismatch: %v", tc.onDisk, tc.missing, dg, err, tc.wantErr, tc.wantMismatch)
			}
		})
	}
}
