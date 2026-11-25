package cache

import (
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/bazelbuild/remote-apis-sdks/go/pkg/client"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/digest"
)

// evictorImpls are the caches that implement CorruptBlobEvictor, with where
// each keeps a blob on disk.
var evictorImpls = []struct {
	name     string
	lockFree bool
	lock     bool
	blobPath func(cacheDir, hash string) string
}{
	{name: "lockfree", lockFree: true, blobPath: blobPath},
	{name: "luci", blobPath: luciBlobPath},
	{name: "luci_locked", lock: true, blobPath: luciBlobPath},
}

// luciBlobPath is where LocalCache keeps a blob: flat in its directory,
// named by its hash.
func luciBlobPath(cacheDir, hash string) string {
	return filepath.Join(cacheDir, hash)
}

// openEvictor opens the cache in cacheDir that opts describes.
func openEvictor(t *testing.T, opts Options) Cache {
	t.Helper()
	c, err := Open(opts)
	if err != nil {
		t.Fatalf("Open(%+v) failed: %v", opts, err)
	}
	t.Cleanup(func() { c.Close() })
	return c
}

// cacheAndPull pushes content to c as a downloaded file would be, then pulls
// it to a new path under dir, as a later download would, and returns the
// pulled item.
func cacheAndPull(t *testing.T, c Cache, dir, content string) *client.TreeOutput {
	t.Helper()
	ctx := t.Context()
	_, pushed := stage(t, dir, "download/file.bin", content, false)
	if err := c.Push(ctx, map[digest.Digest]*client.TreeOutput{pushed.Digest: pushed}); err != nil {
		t.Fatalf("Push failed: %v", err)
	}
	pulled := &client.TreeOutput{Digest: pushed.Digest, Path: filepath.Join(dir, "pull", "file.bin")}
	if err := os.MkdirAll(filepath.Dir(pulled.Path), 0755); err != nil {
		t.Fatalf("Failed to create %s: %v", filepath.Dir(pulled.Path), err)
	}
	cached, _, err := c.Pull(ctx, []*client.TreeOutput{pulled})
	if err != nil || len(cached) != 1 {
		t.Fatalf("Pull of a blob just pushed = %d cached, %v; want 1 cached", len(cached), err)
	}
	return pulled
}

// flipBit flips one bit in the middle of the file at path, in place, as a RAM
// or page cache error would. Every hard link to the file sees it.
func flipBit(t *testing.T, path string) {
	t.Helper()
	b, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("Failed to read %s: %v", path, err)
	}
	b[len(b)/2] ^= 0x08
	if err := os.WriteFile(path, b, 0640); err != nil {
		t.Fatalf("Failed to write %s: %v", path, err)
	}
}

// TestEvictCorruptBlob_RemovesCorruptBlob corrupts a cached blob after it was
// pulled, as a bit flip in the page cache would, and reports the pulled file
// corrupt. Whether the file is a hard link to the blob or a copy of it, the
// blob must be removed, so that the next Pull is a miss and the blob is
// downloaded again.
func TestEvictCorruptBlob_RemovesCorruptBlob(t *testing.T) {
	for _, impl := range evictorImpls {
		for _, useHardlink := range []bool{true, false} {
			name := impl.name + "_copy"
			if useHardlink {
				name = impl.name + "_hardlink"
			}
			t.Run(name, func(t *testing.T) {
				ctx := t.Context()
				dir := t.TempDir()
				opts := Options{LockFree: impl.lockFree, Lock: impl.lock, Dir: filepath.Join(dir, "cache"), UseHardlink: useHardlink}
				c := openEvictor(t, opts)
				const content = "cached content 1111"
				pulled := cacheAndPull(t, c, dir, content)
				blob := impl.blobPath(opts.Dir, pulled.Digest.Hash)
				flipBit(t, blob)
				if !useHardlink {
					// The pulled copy was made before the flip; corrupt it
					// too, as it was when the caller found it corrupt.
					flipBit(t, pulled.Path)
				}

				// DoDownload closes the cache before it restores files and
				// reports a corrupt chunk, so evict from a closed cache too.
				// A fresh instance below then reads the luci index from
				// disk, which checks that EvictCorruptBlob itself wrote the
				// eviction to it.
				c.Close()
				removed, err := c.(CorruptBlobEvictor).EvictCorruptBlob(pulled.Digest, pulled.Path)
				if err != nil || !removed {
					t.Fatalf("EvictCorruptBlob(%v) of a corrupt blob = %v, %v; want true, nil", pulled.Digest, removed, err)
				}
				if _, err := os.Lstat(blob); !os.IsNotExist(err) {
					t.Errorf("Corrupt blob %s is still in the cache after EvictCorruptBlob (lstat: %v)", blob, err)
				}
				if _, err := os.Lstat(pulled.Path); err != nil {
					t.Errorf("EvictCorruptBlob removed the caller's file %s, want it left to the caller: %v", pulled.Path, err)
				}

				again := &client.TreeOutput{Digest: pulled.Digest, Path: filepath.Join(dir, "again.bin")}
				cached, missed, err := openEvictor(t, opts).Pull(ctx, []*client.TreeOutput{again})
				if err != nil || len(cached) != 0 || len(missed) != 1 {
					t.Errorf("Pull after EvictCorruptBlob = %d cached, %d missed, %v; want 0 cached, 1 missed", len(cached), len(missed), err)
				}
			})
		}
	}
}

// TestEvictCorruptBlob_KeepsIntactBlob reports a pulled copy corrupt when only
// the copy is: the cached blob still matches its digest. The blob must be
// kept, since evicting it would only cost every later run a download.
func TestEvictCorruptBlob_KeepsIntactBlob(t *testing.T) {
	for _, impl := range evictorImpls {
		t.Run(impl.name, func(t *testing.T) {
			dir := t.TempDir()
			opts := Options{LockFree: impl.lockFree, Lock: impl.lock, Dir: filepath.Join(dir, "cache"), UseHardlink: false}
			c := openEvictor(t, opts)
			pulled := cacheAndPull(t, c, dir, "cached content 1111")
			flipBit(t, pulled.Path)

			removed, err := c.(CorruptBlobEvictor).EvictCorruptBlob(pulled.Digest, pulled.Path)
			if err != nil || removed {
				t.Fatalf("EvictCorruptBlob(%v) with an intact blob = %v, %v; want false, nil", pulled.Digest, removed, err)
			}
			if _, err := os.Lstat(impl.blobPath(opts.Dir, pulled.Digest.Hash)); err != nil {
				t.Errorf("Intact blob was removed by EvictCorruptBlob: %v", err)
			}
		})
	}
}

// TestEvictCorruptBlob_AbsentBlob reports a blob corrupt that the cache does
// not hold, as when it was evicted, or never cached because it was over
// CacheMaxBlobSize. There is nothing to remove, and that is not an error.
func TestEvictCorruptBlob_AbsentBlob(t *testing.T) {
	for _, impl := range evictorImpls {
		t.Run(impl.name, func(t *testing.T) {
			dir := t.TempDir()
			c := openEvictor(t, Options{LockFree: impl.lockFree, Lock: impl.lock, Dir: filepath.Join(dir, "cache"), UseHardlink: true})
			path, out := stage(t, dir, "file.bin", "never cached", false)

			removed, err := c.(CorruptBlobEvictor).EvictCorruptBlob(out.Digest, path)
			if err != nil || removed {
				t.Errorf("EvictCorruptBlob(%v) of an uncached blob = %v, %v; want false, nil", out.Digest, removed, err)
			}
		})
	}
}

// TestLockFreeCache_EvictCorruptBlobIsCounted checks that a blob removed by
// EvictCorruptBlob is counted with the corrupt blobs Pull finds, which is how
// a host whose cache keeps going bad shows up in the storage metrics.
func TestLockFreeCache_EvictCorruptBlobIsCounted(t *testing.T) {
	dir := t.TempDir()
	cacheDir := filepath.Join(dir, "cache")
	c := newTestCache(t, cacheDir, true)
	pulled := cacheAndPull(t, c, dir, "cached content 1111")
	flipBit(t, blobPath(cacheDir, pulled.Digest.Hash))

	if removed, err := c.EvictCorruptBlob(pulled.Digest, pulled.Path); err != nil || !removed {
		t.Fatalf("EvictCorruptBlob(%v) of a corrupt blob = %v, %v; want true, nil", pulled.Digest, removed, err)
	}
	if got := c.CorruptBlobsQuarantined(); got != 1 {
		t.Errorf("CorruptBlobsQuarantined() after evicting one blob = %d, want 1", got)
	}
}

// TestEvictCorruptBlob_RejectsInvalidDigest reports blobs corrupt under
// digests that are not valid SHA-256 digests. Tree digests reach the cache
// unvalidated, and a hash such as "../victim" would name a file outside the
// cache, here one that is also the caller's "corrupt" file, so that nothing
// short of validation keeps it from being removed. Each must be refused and
// the file left alone.
func TestEvictCorruptBlob_RejectsInvalidDigest(t *testing.T) {
	const victimContent = "victim"
	for _, impl := range evictorImpls {
		for _, tc := range []struct {
			name string
			dg   digest.Digest
		}{
			{name: "traversal", dg: digest.Digest{Hash: "../victim", Size: int64(len(victimContent))}},
			{name: "uppercase_hex", dg: digest.Digest{Hash: strings.ToUpper(hashOf(victimContent)), Size: int64(len(victimContent))}},
			{name: "negative_size", dg: digest.Digest{Hash: hashOf(victimContent), Size: -1}},
		} {
			t.Run(impl.name+"_"+tc.name, func(t *testing.T) {
				dir := t.TempDir()
				// Both layouts resolve "../victim" to dir/victim: luci from
				// dir/cache, the lock-free cache from its bucket in dir/cache/v2.
				victim := filepath.Join(dir, "victim")
				if err := os.WriteFile(victim, []byte(victimContent), 0640); err != nil {
					t.Fatalf("Failed to write %s: %v", victim, err)
				}
				c := openEvictor(t, Options{LockFree: impl.lockFree, Lock: impl.lock, Dir: filepath.Join(dir, "cache"), UseHardlink: true})

				removed, err := c.(CorruptBlobEvictor).EvictCorruptBlob(tc.dg, victim)
				if err == nil || removed {
					t.Errorf("EvictCorruptBlob(%v) = %v, %v; want false and an error", tc.dg, removed, err)
				}
				if got, err := os.ReadFile(victim); err != nil || string(got) != victimContent {
					t.Errorf("File outside the cache = %q, %v after EvictCorruptBlob(%v), want it unchanged as %q", got, err, tc.dg, victimContent)
				}
			})
		}
	}
}
