package cache

import (
	"context"
	"crypto"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"syscall"
	"time"

	log "github.com/golang/glog"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/client"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/digest"
	lucicache "go.chromium.org/luci/common/data/caching/cache"
	"go.chromium.org/luci/common/data/text/units"
)

// LocalCache uses a directory to do caching.
type LocalCache struct {
	cacheClient   *lucicache.Cache
	cachePolicies lucicache.Policies
	cacheDir      string
	cacheLock     cacheLock
	// If true, use hardlink to push/pull items to/from cache. Otherwise, use copy.
	// When there are too many hard links, it may fall back to copy.
	useHardlink bool
}

type cacheLock struct {
	lockPath string
	lockFile *os.File
}

func (c *LocalCache) initCache() (*lucicache.Cache, error) {
	start := time.Now()
	client, err := lucicache.New(c.cachePolicies, c.cacheDir, crypto.SHA256)
	log.Infof("initialized local cache, took %s", time.Since(start))
	return client, err
}

func (c *LocalCache) closeCache() error {
	start := time.Now()
	if c.cacheClient != nil {
		if err := c.cacheClient.Close(); err != nil {
			return fmt.Errorf("failed to close local cache: %v", err)
		}
	}
	log.Infof("closed local cache, took %s", time.Since(start))
	return nil
}

func (c *LocalCache) lockAndInitCache() error {
	start := time.Now()
	log.Infof("local cache lock: obtaining lock for local cache %s", c.cacheDir)
	lockFile, err := os.OpenFile(c.cacheLock.lockPath, os.O_RDWR|os.O_CREATE, 0755)
	if err != nil {
		return fmt.Errorf("failed to open cache lock file %s: %v", c.cacheLock.lockPath, err)
	}
	if err := syscall.Flock(int(lockFile.Fd()), syscall.LOCK_EX); err != nil {
		return fmt.Errorf("failed to lock local cache %s: %v", c.cacheDir, err)
	}
	log.Infof("local cache lock: lock obtained, took %v", time.Since(start))

	client, err := c.initCache()
	if err != nil {
		return fmt.Errorf("failed to initialize local cache: %v", err)
	}

	c.cacheClient = client
	c.cacheLock.lockFile = lockFile
	return nil
}

func (c *LocalCache) unlockAndCloseCache() error {
	if err := c.closeCache(); err != nil {
		return fmt.Errorf("failed to close local cache: %v", err)
	}
	c.cacheClient = nil

	if err := syscall.Flock(int(c.cacheLock.lockFile.Fd()), syscall.LOCK_UN); err != nil {
		return fmt.Errorf("failed to unlock local cache %s: %v", c.cacheDir, err)
	}
	c.cacheLock.lockFile.Close()

	log.Infof("local cache lock: lock released")
	c.cacheLock.lockFile = nil
	return nil
}

// Push a list of items into the cache.
func (c *LocalCache) Push(ctx context.Context, all map[digest.Digest]*client.TreeOutput) error {
	if c.useHardlink {
		return c.pushByHardlink(ctx, all)
	}
	return c.pushByCopy(ctx, all)
}

// Push a list of items into the cache by hardlink.
func (c *LocalCache) pushByHardlink(ctx context.Context, all map[digest.Digest]*client.TreeOutput) error {
	if c.cacheLock.lockPath != "" {
		if err := c.lockAndInitCache(); err != nil {
			return err
		}
		defer c.unlockAndCloseCache()
	}

	for _, item := range all {
		if err := c.cacheClient.AddFileWithoutValidation(
			ctx, lucicache.HexDigest(item.Digest.Hash), item.Path); err != nil {
			// Fall back to copy if hardlink fails. Do not log the link error as it can be spammy.
			if err := c.pushItemByCopy(ctx, item); err != nil {
				return err
			}
		}
	}
	return nil
}

// Push a list of items into the cache by copy.
func (c *LocalCache) pushByCopy(ctx context.Context, all map[digest.Digest]*client.TreeOutput) error {
	if c.cacheLock.lockPath != "" {
		if err := c.lockAndInitCache(); err != nil {
			return err
		}
		defer c.unlockAndCloseCache()
	}

	for _, item := range all {
		if err := c.pushItemByCopy(ctx, item); err != nil {
			return err
		}
	}
	return nil
}

func (c *LocalCache) pushItemByCopy(ctx context.Context, item *client.TreeOutput) error {
	file, err := os.Open(item.Path)
	if err != nil {
		return fmt.Errorf("failed to open file %s : %v", item.Path, err)
	}
	defer file.Close()

	if err := c.cacheClient.Add(
		ctx, lucicache.HexDigest(item.Digest.Hash), file); err != nil {
		return fmt.Errorf("failed to push (path=%s digest=%s) to cache: %v", item.Path, item.Digest, err)
	}
	return nil
}

// removePulledFiles removes the files pulled from cache.
func (c *LocalCache) removePulledFiles(cached []*client.TreeOutput) {
	log.Infof("Cleanup on error: remove %d files pulled from cache", len(cached))
	for _, item := range cached {
		if err := os.Remove(item.Path); err != nil {
			log.Errorf("failed to remove file %s: %v", item.Path, err)
		}
	}
}

// Pull the items from cache to the target locations.
//
// It returns a list of items successfully pulled from local cache and a list of missed items, and
// any error encountered. Pulled files are removed on error.
func (c *LocalCache) Pull(ctx context.Context, all []*client.TreeOutput) (
	cached, missed []*client.TreeOutput, err error) {
	if c.useHardlink {
		cached, missed, err = c.pullByHardlink(ctx, all)
		if err != nil {
			c.removePulledFiles(cached)
			return
		}
		return
	}
	cached, missed, err = c.pullByCopy(ctx, all)
	if err != nil {
		c.removePulledFiles(cached)
		return
	}
	return
}

// Hardlink the items from cache to the target locations.
//
// It returns a list of items successfully pulled from local cache and a list of missed items, and
// any error encountered.
func (c *LocalCache) pullByHardlink(ctx context.Context, all []*client.TreeOutput) (
	cached, missed []*client.TreeOutput, err error) {
	if c.cacheLock.lockPath != "" {
		if err := c.lockAndInitCache(); err != nil {
			return nil, nil, err
		}
		defer c.unlockAndCloseCache()
	}

	// Hard link items from cache to the target location if the item is in cache.
	for _, item := range all {
		if c.cacheClient.Touch(lucicache.HexDigest(item.Digest.Hash)) {
			if err := c.cacheClient.Hardlink(lucicache.HexDigest(item.Digest.Hash), item.Path, fileMode(item)); err != nil {
				if !errors.Is(err, syscall.EMLINK) {
					return nil, nil, fmt.Errorf("failed to hard link from cache to %s: %v", item.Path, err)
				}
				// When it fails for too many hard links, we fall back to copy.
				if err := c.copyItemFromCache(ctx, item); err != nil {
					return nil, nil, err
				}
			}
			cached = append(cached, item)
			continue
		}
		missed = append(missed, item)
	}
	return cached, missed, nil
}

// Copy the items from cache to the target locations.
//
// It returns a list of items successfully pulled from local cache and a list of missed items, and
// any error encountered.
func (c *LocalCache) pullByCopy(ctx context.Context, all []*client.TreeOutput) (
	cached, missed []*client.TreeOutput, err error) {
	if c.cacheLock.lockPath != "" {
		if err := c.lockAndInitCache(); err != nil {
			return nil, nil, err
		}
		defer c.unlockAndCloseCache()
	}

	// Copy items from cache to the target location if the item is in cache.
	for _, item := range all {
		if c.cacheClient.Touch(lucicache.HexDigest(item.Digest.Hash)) {
			if err := c.copyItemFromCache(ctx, item); err != nil {
				return nil, nil, err
			}
			cached = append(cached, item)
			continue
		}
		missed = append(missed, item)
	}
	return cached, missed, nil
}

func (c *LocalCache) copyItemFromCache(ctx context.Context, item *client.TreeOutput) error {
	reader, err := c.cacheClient.Read(lucicache.HexDigest(item.Digest.Hash))
	if err != nil {
		return fmt.Errorf("failed to read file from cache %s", err)
	}
	defer reader.Close()

	destFile, err := os.Create(item.Path)
	if err != nil {
		return fmt.Errorf("failed to create file %s: %v", item.Path, err)
	}
	defer destFile.Close()

	_, err = io.Copy(destFile, reader)
	if err != nil {
		return fmt.Errorf("failed to copy from cache to %s: %v", item.Path, err)
	}
	return nil
}

// Close closes and cleans up the cache.
func (c *LocalCache) Close() error {
	return c.closeCache()
}

// NewLocalCache creates a new LocalCache instance.
func NewLocalCache(cacheDir string, cacheMaxSize int64, enableLock bool, useHardlink bool) (*LocalCache, error) {
	os.MkdirAll(cacheDir, 0755)
	cachePolicies := lucicache.Policies{
		MaxSize: units.Size(cacheMaxSize),
	}

	c := &LocalCache{
		cacheClient:   nil,
		cachePolicies: cachePolicies,
		cacheDir:      cacheDir,
		cacheLock:     cacheLock{},
		useHardlink:   useHardlink,
	}
	if enableLock {
		c.cacheLock.lockPath = filepath.Join(cacheDir, "state.lock")
	}

	// Only initialize the cache if enableLock is false. When enableLock is true, the cache will be
	// initialized and closed inside push/pull operation.
	if !enableLock {
		var err error
		c.cacheClient, err = lucicache.New(cachePolicies, cacheDir, crypto.SHA256)
		if err != nil {
			return nil, fmt.Errorf("failed to initialize local cache: %v", err)
		}
	}

	return c, nil
}
