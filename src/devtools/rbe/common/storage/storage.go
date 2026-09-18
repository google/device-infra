// Package storage provides lock-free local disk storage and caching for CAS blobs.
package storage

import (
	"context"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"sync/atomic"
	"time"
)

var (
	processEpoch   = time.Now().UnixNano()
	tmpFileCounter uint64
)

// Storage manages local disk caching of CAS blobs.
// Because CAS is strictly Write-Once-Read-Many (WORM) and uses atomic POSIX renames,
// all read and write operations are lock-free and thread-safe without mutexes.
type Storage struct {
	rootDir string
	evictor *Evictor
}

// New creates a new Storage instance rooted at rootDir.
func New(rootDir string) (*Storage, error) {
	if err := os.MkdirAll(rootDir, 0755); err != nil {
		return nil, fmt.Errorf("failed to create storage root directory %s: %w", rootDir, err)
	}
	return &Storage{rootDir: rootDir}, nil
}

// blobPath returns the sharded filepath for a given hash.
//
// Sharding Theory & Filesystem Rationale:
// Linux filesystems (e.g. ext4, XFS) store directory entries using index trees (HTrees/B-Trees).
// Storing hundreds of thousands or millions of CAS blobs in a single flat directory causes:
//  1. Increased directory inode block size and lock contention during concurrent lookups/creates.
//  2. Slower directory traversals and higher cache-miss rates in the VFS dentry cache.
//
// To prevent flat directory bloat, blobPath shards paths using a 2-level directory hierarchy
// based on the first 4 hexadecimal characters of the hash:
//   - Level 1: 256 subdirectories (hash[:2], "00" through "ff")
//   - Level 2: 256 subdirectories (hash[2:4], "00" through "ff")
//
// This creates 65,536 (256 x 256) leaf buckets. For example, a cache storing 1,000,000 blobs
// distributes them across the buckets with an average of only ~15 files per leaf directory,
// fitting neatly within a single filesystem cache page for optimal O(1) open/stat performance.
//
// Examples:
//   - Standard SHA-256 (64 hex characters, len >= 4):
//     "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
//     -> "/rootDir/e3/b0/e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
//   - Defensive fallback for short/synthetic inputs (len < 4):
//     "abc"
//     -> "/rootDir/abc"
//
// Note: Production CAS digests always use 64-character hex strings for SHA-256 (and are validated
// at the API boundary). The len < 4 condition is purely a defensive bounds check to prevent
// slice bounds out-of-range panics on malformed inputs or synthetic unit test strings.
func (s *Storage) blobPath(hash string) string {
	if len(hash) < 4 {
		return filepath.Join(s.rootDir, hash)
	}
	return filepath.Join(s.rootDir, hash[:2], hash[2:4], hash)
}

// Has checks if a blob with the given hash exists in local storage.
func (s *Storage) Has(hash string) bool {
	info, err := os.Stat(s.blobPath(hash))
	return err == nil && !info.IsDir()
}

// Read reads and returns the full blob bytes for the given hash.
func (s *Storage) Read(hash string) ([]byte, error) {
	return os.ReadFile(s.blobPath(hash))
}

// Open opens the blob file for streaming reads.
func (s *Storage) Open(hash string) (*os.File, error) {
	return os.Open(s.blobPath(hash))
}

// tmpPath returns a collision-free temporary file path alongside targetPath.
//
// Temp File Architecture & Pro/Con Analysis of Alternatives:
//
// 1. Alternative Considered: Dedicated Top-Level Temp Directory (e.g. "rootDir/.tmp/")
//   - Pros: Isolates in-flight writes from leaf directories; allows trivial one-shot cleanup
//     of abandoned temp files on server startup via os.RemoveAll("rootDir/.tmp").
//   - Cons: Cross-directory os.Rename modifies and journals TWO separate directory inodes
//     ("rootDir/.tmp/" and "rootDir/xx/yy/") instead of one. Under heavy multi-stream concurrency
//     (e.g., 25+ concurrent downloads), "rootDir/.tmp/" becomes a severe write hot spot where all
//     threads contend on the same directory inode lock (i_rwsem) and repeatedly dirty the same
//     journal block, increasing SSD Write Amplification (WAF) and accelerating drive wear.
//
// 2. Alternative Considered: Trailing Suffix in Same Leaf Directory (e.g. "targetPath.tmp.epoch.seq")
//   - Pros: Retains same-directory atomic rename benefits.
//   - Cons: Checking for temp files during eviction sweeps requires substring searches
//     (strings.Contains(name, ".tmp.")), consuming unnecessary CPU cycles across millions of blobs.
//
// 3. Adopted Solution: Leading Dot Prefix in Same Leaf Directory (e.g. "rootDir/xx/yy/.tmp.hash.epoch.seq")
//   - Pro 1 (Minimal SSD Wear): Renaming within the same leaf directory modifies only ONE directory
//     inode, halving metadata journal writes compared to a dedicated temp directory.
//   - Pro 2 (Zero Hot-Spotting): Ingest writes and renames are distributed evenly across 65,536
//     independent leaf directory inodes.
//   - Pro 3 (Sub-Nanosecond Eviction Filtering): Eviction sweeps can skip in-flight temp files with
//     a single-byte check (entry.Name()[0] == '.') or fixed 64-char length check (len(name) != 64),
//     executing in a single CPU instruction without any memory allocation.
func (s *Storage) tmpPath(targetPath string) string {
	dir, base := filepath.Split(targetPath)
	return filepath.Join(dir, fmt.Sprintf(".tmp.%s.%d.%d", base, processEpoch, atomic.AddUint64(&tmpFileCounter, 1)))
}

// Write atomically saves blob bytes to local storage via a unique temp file.
func (s *Storage) Write(hash string, data []byte) error {
	p := s.blobPath(hash)
	if err := os.MkdirAll(filepath.Dir(p), 0755); err != nil {
		return err
	}

	tmpFile := s.tmpPath(p)
	defer os.Remove(tmpFile)
	if err := os.WriteFile(tmpFile, data, 0644); err != nil {
		return err
	}
	return os.Rename(tmpFile, p)
}

// StoreFromReader atomically writes streamed data to local storage.
func (s *Storage) StoreFromReader(hash string, r io.Reader) error {
	p := s.blobPath(hash)
	if err := os.MkdirAll(filepath.Dir(p), 0755); err != nil {
		return err
	}

	tmpFile := s.tmpPath(p)
	f, err := os.OpenFile(tmpFile, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, 0644)
	if err != nil {
		return err
	}
	defer f.Close()
	defer os.Remove(tmpFile)

	if _, err := io.Copy(f, r); err != nil {
		return err
	}
	if err := f.Close(); err != nil {
		return err
	}
	return os.Rename(tmpFile, p)
}

// StoreFileFunc creates a unique temporary file alongside the target blob path,
// invokes writeFn(tmpPath), and upon success atomically renames the temporary
// file to the destination blob path. If writeFn returns an error, the temporary file
// is removed.
func (s *Storage) StoreFileFunc(hash string, writeFn func(tmpPath string) error) error {
	p := s.blobPath(hash)
	if err := os.MkdirAll(filepath.Dir(p), 0755); err != nil {
		return err
	}

	tmpFile := s.tmpPath(p)
	defer os.Remove(tmpFile)

	if err := writeFn(tmpFile); err != nil {
		return err
	}
	return os.Rename(tmpFile, p)
}

// TouchIfOlderThan updates the modification time of a blob to now if its current
// modification time is older than the specified interval.
//
// This implements "Lazy Touch" to preserve LRU recency while drastically reducing
// SSD Write Amplification (WAF). If multiple concurrent clients stream chunks of the
// same blob over hours, only the first access beyond the interval incurs an inode write;
// all subsequent reads are pure in-memory/flash read hits with 0 journal commits.
//
// If the blob does not exist or was concurrently unlinked, it safely returns false without error.
func (s *Storage) TouchIfOlderThan(hash string, interval time.Duration) bool {
	p := s.blobPath(hash)
	info, err := os.Stat(p)
	if err != nil {
		return false
	}
	if time.Since(info.ModTime()) < interval {
		return false
	}
	now := time.Now()
	_ = os.Chtimes(p, now, now)
	return true
}

// RootDir returns the root storage directory path.
func (s *Storage) RootDir() string {
	return s.rootDir
}

// SetEvictor sets or injects the Evictor instance on Storage for testing or custom configurations.
func (s *Storage) SetEvictor(ev *Evictor) {
	s.evictor = ev
}

// StartEvictor initializes and launches the background eviction worker.
// If an Evictor was already injected via SetEvictor, it starts the injected instance;
// otherwise it constructs a new Evictor with real statfs.
func (s *Storage) StartEvictor(ctx context.Context, cfg EvictorConfig) error {
	if s.evictor == nil {
		ev, err := NewEvictor(s.rootDir, cfg, nil)
		if err != nil {
			return err
		}
		s.evictor = ev
	}
	s.evictor.Start(ctx)
	return nil
}

// StopEvictor stops the background eviction worker if running.
func (s *Storage) StopEvictor() {
	if s.evictor != nil {
		s.evictor.Stop()
	}
}

// Evictor returns the storage evictor instance (or nil if not set).
func (s *Storage) Evictor() *Evictor {
	return s.evictor
}

// DiskUsage holds filesystem capacity and effective cache space metrics.
type DiskUsage struct {
	TotalBytes          int64
	FreeBytes           int64
	ReservedBytes       int64
	EffectiveTotalBytes int64
	EffectiveFreeBytes  int64
	FreePercent         float64
}

// DiskUsage returns the current disk space metrics for the cache root directory.
func (s *Storage) DiskUsage() (DiskUsage, error) {
	statfs := RealStatfs
	if s.evictor != nil && s.evictor.statfs != nil {
		statfs = s.evictor.statfs
	}
	stats, err := statfs(s.rootDir)
	if err != nil {
		return DiskUsage{}, err
	}
	reservedBytes := int64(0)
	if s.evictor != nil {
		s.evictor.configMu.RLock()
		reservedBytes = s.evictor.reservedBytes
		s.evictor.configMu.RUnlock()
	}
	effTotal, effFree, freePct := ComputeEffectiveSpace(stats, reservedBytes)
	return DiskUsage{
		TotalBytes:          stats.TotalBytes,
		FreeBytes:           stats.FreeBytes,
		ReservedBytes:       reservedBytes,
		EffectiveTotalBytes: effTotal,
		EffectiveFreeBytes:  effFree,
		FreePercent:         freePct,
	}, nil
}

// UpdateEvictorConfig dynamically updates the active evictor configuration without restarting.
func (s *Storage) UpdateEvictorConfig(cfg EvictorConfig) error {
	if s.evictor == nil {
		return fmt.Errorf("evictor is not running")
	}
	return s.evictor.UpdateConfig(cfg)
}

// EvictorConfig returns the active evictor configuration (or error if not running).
func (s *Storage) EvictorConfig() (EvictorConfig, error) {
	if s.evictor == nil {
		return EvictorConfig{}, fmt.Errorf("evictor is not running")
	}
	return s.evictor.Config(), nil
}
