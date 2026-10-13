package storage

import (
	"fmt"
	"path/filepath"
)

// Layout maps blob hashes to paths under a Storage root and enumerates the
// leaf directories ("buckets") those paths fall into.
//
// The layout is part of the on-disk contract of a cache directory: every
// process sharing a root must agree on it, and the evictor only ever looks
// inside the buckets it lists. It is therefore chosen in code, not by flag.
// Changing the layout of an existing cache means moving to a new directory.
//
// Buckets are addressed by index rather than listed, so that an evictor can
// sample or walk them without holding every bucket name in memory.
type Layout interface {
	// BlobPath returns the path of the blob with the given hash, relative to
	// the storage root.
	BlobPath(hash string) string
	// NumBuckets returns the number of leaf buckets.
	NumBuckets() int
	// Bucket returns the path of leaf bucket i, relative to the storage root,
	// for 0 <= i < NumBuckets().
	Bucket(i int) string
}

var (
	// ShardedLayout65536 shards blobs two levels deep, as ab/cd/<hash>, into
	// 65,536 buckets. It is the default, and the layout casproxy uses.
	//
	// Linux filesystems (e.g. ext4, XFS) store directory entries using index
	// trees (HTrees/B-Trees). Storing hundreds of thousands or millions of
	// CAS blobs in a single flat directory causes:
	//  1. Increased directory inode block size and lock contention during
	//     concurrent lookups/creates.
	//  2. Slower directory traversals and higher cache-miss rates in the VFS
	//     dentry cache.
	//
	// To prevent flat directory bloat, this layout shards paths using a
	// 2-level directory hierarchy based on the first 4 hexadecimal characters
	// of the hash:
	//   - Level 1: 256 subdirectories (hash[:2], "00" through "ff")
	//   - Level 2: 256 subdirectories (hash[2:4], "00" through "ff")
	//
	// For example, a cache storing 1,000,000 blobs distributes them across
	// the buckets with an average of only ~15 files per leaf directory,
	// fitting neatly within a single filesystem cache page for optimal O(1)
	// open/stat performance.
	ShardedLayout65536 Layout = newShardedLayout(2)

	// Layout256 shards blobs one level deep, as ab/<hash>, into 256 buckets.
	//
	// It is meant for small caches. casdownloader's caches hold roughly 8k to
	// 174k blobs, which spread over 65,536 buckets leaves 0-3 files in each,
	// so the evictor's sampling of a handful of buckets sees almost nothing.
	// Over 256 buckets the same caches hold about 30-680 files per bucket.
	Layout256 Layout = newShardedLayout(1)
)

// shardedLayout places a blob in a directory per leading byte of its hash,
// levels bytes deep.
type shardedLayout struct {
	levels int
}

func newShardedLayout(levels int) *shardedLayout {
	return &shardedLayout{levels: levels}
}

// BlobPath returns the sharded path of hash relative to the root.
//
// Examples, for the two-level layout:
//   - Standard SHA-256 (64 hex characters):
//     "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
//     -> "e3/b0/e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
//   - Defensive fallback for short/synthetic inputs:
//     "abc"
//     -> "abc"
//
// Production CAS digests always use 64-character hex strings for SHA-256 (and
// are validated at the API boundary). The length check is purely a defensive
// bounds check to prevent slice bounds out-of-range panics on malformed inputs
// or synthetic unit test strings.
func (l *shardedLayout) BlobPath(hash string) string {
	if len(hash) < 2*l.levels {
		return hash
	}
	parts := make([]string, 0, l.levels+1)
	for i := 0; i < l.levels; i++ {
		parts = append(parts, hash[2*i:2*i+2])
	}
	return filepath.Join(append(parts, hash)...)
}

// NumBuckets returns 256^levels.
func (l *shardedLayout) NumBuckets() int {
	return 1 << (8 * l.levels)
}

// Bucket returns leaf bucket i: the bytes of i, most significant first, as
// two-digit hex path components. For example, bucket 0xe3b0 of the two-level
// layout is "e3/b0". Buckets in index order are therefore in lexical order.
// Bucket panics if i is out of range, as indexing a slice would.
func (l *shardedLayout) Bucket(i int) string {
	if i < 0 || i >= l.NumBuckets() {
		panic(fmt.Sprintf("storage: bucket %d out of range [0, %d) of %v", i, l.NumBuckets(), l))
	}
	parts := make([]string, l.levels)
	for level := l.levels - 1; level >= 0; level-- {
		parts[level] = fmt.Sprintf("%02x", i&0xff)
		i >>= 8
	}
	return filepath.Join(parts...)
}

// String names the layout in logs and test failures.
func (l *shardedLayout) String() string {
	if l.levels == 1 {
		return "Layout256"
	}
	return fmt.Sprintf("ShardedLayout%d", 1<<(8*l.levels))
}
