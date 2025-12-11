// Package download is the library for downloading files and directories from RBE CAS.
package download

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"regexp"
	"runtime"
	"sort"
	"time"

	log "github.com/golang/glog"

	"github.com/bazelbuild/remote-apis-sdks/go/pkg/client"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/digest"
	repb "github.com/bazelbuild/remote-apis/build/bazel/remote/execution/v2"
	"github.com/google/device-infra/src/devtools/rbe/casdownloader/cache"
	"golang.org/x/sync/errgroup"

	"github.com/google/device-infra/src/devtools/rbe/casuploader/chunkerutil"
	"go.chromium.org/luci/common/data/text/units"
)

type DownloadJob struct {
	Client   *client.Client
	Digest   string
	Dir      string
	DumpJSON string
	Cache    cache.Cache
	// Filters applied to files to download
	IncludeFilters []string
	ExcludeFilters []string
	downloadStats  *downloadStats
	KeepChunks     bool
	ChunksOnly     bool
}

type downloadStats struct {
	SizeCold           int64  `json:"size_cold"`
	SizeHot            int64  `json:"size_hot"`
	CountCold          int    `json:"count_cold"`
	CountHot           int    `json:"count_hot"`
	E2eTimeMs          int64  `json:"e2e_time_ms"`
	DirRetrieveTimeMs  int64  `json:"dir_retrieve_time_ms"`
	DirPrepareTimeMs   int64  `json:"dir_prepare_time_ms"`
	FileDownloadTimeMs int64  `json:"file_download_time_ms"`
	ChunkRestoreTimeMs int64  `json:"chunk_restore_time_ms"`
	DownloadError      string `json:"download_error"`
	Notes              string `json:"notes"`
}

// prepareSymLinksAndDirs creates directories and symbolic links. It is executed before checking
// with cache or downloading files from remote since the TreeOutput contains information of
// directories and symbolic links. It returns a new list of *client.TreeOutput excluding created
// directories and symbolic links.
func prepareSymLinksAndDirs(root string, outputs []*client.TreeOutput) ([]*client.TreeOutput, error) {
	unresolved := make([]*client.TreeOutput, 0)
	dirSet := make(map[string]bool)

	for _, output := range outputs {
		var dir string
		if output.IsEmptyDirectory {
			dir = output.Path
		} else {
			dir = filepath.Dir(output.Path)
		}
		dirSet[dir] = true
	}

	if err := os.MkdirAll(root, 0o700); err != nil {
		return nil, fmt.Errorf("failed to create the root directory: %v", err)
	}

	for dir := range dirSet {
		if err := os.MkdirAll(dir, 0o700); err != nil && !os.IsExist(err) {
			return nil, fmt.Errorf("failed to create directory: %v", err)
		}
	}

	numEmptyDir, numSymLink := 0, 0
	for _, output := range outputs {
		if output.IsEmptyDirectory {
			numEmptyDir++
			continue
		}
		if output.SymlinkTarget != "" {
			if err := os.Symlink(output.SymlinkTarget, output.Path); err != nil {
				return nil, fmt.Errorf("failed to create symlink to %s: %v", output.Path, err)
			}
			numSymLink++
			continue
		}
		unresolved = append(unresolved, output)
	}
	log.Infof("created %d directories (%d empty directories) and %d symlinks. %d files are unresolved.",
		len(dirSet), numEmptyDir, numSymLink, len(unresolved))

	return unresolved, nil
}

func copyFile(dstPath string, srcPath string, mode os.FileMode) error {
	src, err := os.Open(srcPath)
	if err != nil {
		return err
	}
	defer src.Close()

	dst, err := os.OpenFile(dstPath, os.O_CREATE|os.O_EXCL|os.O_WRONLY, mode)
	if err != nil {
		return err
	}
	defer dst.Close()

	_, err = io.Copy(dst, src)
	return err
}

func copyFiles(ctx context.Context, dsts []*client.TreeOutput, srcs map[digest.Digest]*client.TreeOutput) error {
	eg, _ := errgroup.WithContext(ctx)

	// limit the number of concurrent I/O operations.
	ch := make(chan struct{}, runtime.NumCPU())

	for _, dst := range dsts {
		dst := dst
		src := srcs[dst.Digest]
		ch <- struct{}{}
		eg.Go(func() (err error) {
			defer func() { <-ch }()
			if fileMode(dst) == fileMode(src) {
				// Create a hard link if file mode matches.
				if err := os.Link(src.Path, dst.Path); err == nil {
					return nil
				}
				log.Infof("failed to link file from '%s' to '%s': %v", src.Path, dst.Path, err)
				// Fall back to copy the file.
			}
			if err := copyFile(dst.Path, src.Path, fileMode(dst)); err != nil {
				return fmt.Errorf("failed to copy file from '%s' to '%s': %v", src.Path, dst.Path, err)
			}
			return nil
		})
	}
	return eg.Wait()
}

func fileMode(output *client.TreeOutput) os.FileMode {
	if output.IsExecutable {
		return os.FileMode(0o700)
	}
	return os.FileMode(0o600)
}

func (d *DownloadJob) updateDownloadStats(all []*client.TreeOutput, downloaded map[digest.Digest]*client.TreeOutput) {
	var sizeTotal, sizeCold int64
	countCold := len(downloaded)
	countHot := len(all) - countCold
	for _, output := range all {
		sizeTotal += output.Digest.Size
	}
	for _, output := range downloaded {
		sizeCold += output.Digest.Size
	}
	sizeHot := sizeTotal - sizeCold

	log.Infof("Stats of cache: SizeCold: %v, SizeHot: %v, CountCold: %d, CountHot: %d",
		units.Size(sizeCold), units.Size(sizeHot), countCold, countHot)

	d.downloadStats.SizeCold = sizeCold
	d.downloadStats.SizeHot = sizeHot
	d.downloadStats.CountCold = countCold
	d.downloadStats.CountHot = countHot
}

func dumpStats(path string, stats *downloadStats) error {
	statsJSON, err := json.Marshal(stats)

	if err != nil {
		return fmt.Errorf("failed to marshal stats json: %v", err)
	}
	if err := os.WriteFile(path, statsJSON, 0600); err != nil {
		return fmt.Errorf("failed to write stats json: %v", err)
	}
	return nil
}

func (d *DownloadJob) filterFiles(fullSet map[string]*client.TreeOutput) (map[string]*client.TreeOutput, error) {
	includePatterns := []*regexp.Regexp{}
	excludePatterns := []*regexp.Regexp{}

	for _, filter := range d.IncludeFilters {
		p, err := regexp.Compile(filter)
		if err != nil {
			return nil, fmt.Errorf("fail to compile filter %s: %v", filter, err)
		}
		includePatterns = append(includePatterns, p)
	}
	for _, filter := range d.ExcludeFilters {
		p, err := regexp.Compile(filter)
		if err != nil {
			return nil, fmt.Errorf("fail to compile filter %s: %v", filter, err)
		}
		excludePatterns = append(excludePatterns, p)
	}

	matchedSet := make(map[string]*client.TreeOutput)
	for path, output := range fullSet {
		relativePath, err := filepath.Rel(d.Dir, path)
		if err != nil {
			log.Warningf("failed to get relative path of %s, skip", path)
			continue
		}

		// If no includeFilters is specified, the element is considered as MATCHED by default,
		// and will check excludeFilters only.
		matched := len(includePatterns) == 0
		for _, ip := range includePatterns {
			if ip.MatchString(relativePath) {
				matched = true
				break
			}
		}
		if !matched {
			continue
		}
		for _, ep := range excludePatterns {
			if ep.MatchString(relativePath) {
				matched = false
				break
			}
		}
		if matched {
			matchedSet[path] = output
		}
	}
	log.Infof("applied include/exclude-filters on %d files, will partially download %d files",
		len(fullSet), len(matchedSet))
	return matchedSet, nil
}

// convertTreeOutputListToMap converts a list of client.TreeOutput instances to a map from the
// digest to the client.TreeOutput instance. Meanwhile, it will also returns a list of instances
// whose digest is duplicate with a instance already in the map.
// In the downloader use case, usually, files have the same digest are downloaded only once, and
// will copy duplicated files later.
func convertTreeOutputListToMap(inputs []*client.TreeOutput) (digestMap map[digest.Digest]*client.TreeOutput, dups []*client.TreeOutput) {
	digestMap = make(map[digest.Digest]*client.TreeOutput)

	for _, input := range inputs {
		if _, ok := digestMap[input.Digest]; ok {
			dups = append(dups, input)
		} else {
			digestMap[input.Digest] = input
		}
	}
	return digestMap, dups
}

// removeLeftOverFiles removes the files if they exist.
func removeLeftOverFiles(files []*client.TreeOutput) {
	log.Infof("Cleanup on error: remove %d files.", len(files))
	for _, item := range files {
		if err := os.Remove(item.Path); err != nil && !os.IsNotExist(err) {
			// Ignore the error if the file does not exist.
			log.Errorf("failed to remove file %s: %v", item.Path, err)
		}
	}
}

// downloadFilesWithAbsolutePath takes a map of digests to TreeOutput with absolute paths,
// converts these paths to be relative to d.Dir, and then calls d.Client.DownloadFiles.
func (d *DownloadJob) downloadFilesWithAbsolutePath(ctx context.Context, toDownload map[digest.Digest]*client.TreeOutput) error {
	toDownloadRelative := make(map[digest.Digest]*client.TreeOutput, len(toDownload))
	for dg, output := range toDownload {
		// Convert absolute output.Path to be relative to d.Dir
		relPath, err := filepath.Rel(d.Dir, output.Path)
		if err != nil {
			return fmt.Errorf("failed to make path relative for %s: %v", output.Path, err)
		}
		// Create a new TreeOutput with the relative path
		relOutput := *output // Shallow copy
		relOutput.Path = relPath
		toDownloadRelative[dg] = &relOutput
	}

	// Call d.Client.DownloadFiles with d.Dir as destDir and relative paths.
	// We ignore the returned map as it's not used by the callers.
	_, err := d.Client.DownloadFiles(ctx, d.Dir, toDownloadRelative)
	return err
}

func (d *DownloadJob) downloadWithoutLocalCache(ctx context.Context, outputs []*client.TreeOutput) error {
	toDownload := make(map[digest.Digest]*client.TreeOutput)
	for _, output := range outputs {
		toDownload[output.Digest] = output
	}
	start := time.Now()
	if err := d.downloadFilesWithAbsolutePath(ctx, toDownload); err != nil {
		removeLeftOverFiles(outputs)
		return fmt.Errorf("failed to download files: %v", err)
	}
	log.Infof("finished downloading %d files from CAS without local cache, took %s", len(toDownload), time.Since(start))

	return nil
}

func (d *DownloadJob) downloadWithLocalCache(ctx context.Context, cache cache.Cache, outputs []*client.TreeOutput) error {
	start := time.Now()
	cached, missed, err := cache.Pull(ctx, outputs)
	if err != nil {
		return fmt.Errorf("failed to pull files from cache: %v", err)
	}
	log.Infof("finished pulling %d files from cache, took %s", len(cached), time.Since(start))

	if len(missed) <= 0 {
		log.Infof("All files in cache. Skip downloading files.")
		return nil
	}

	toDownload, dups := convertTreeOutputListToMap(missed)

	var sumSize int64
	for _, output := range toDownload {
		sumSize += output.Digest.Size
	}
	log.Infof("start downloading %d files, estimated size %v", len(toDownload), units.Size(sumSize))

	start = time.Now()
	if err := d.downloadFilesWithAbsolutePath(ctx, toDownload); err != nil {
		removeLeftOverFiles(outputs)
		return fmt.Errorf("failed to download files: %v", err)
	}
	log.Infof("finished downloading %d files from CAS, took %s", len(toDownload), time.Since(start))

	// Push downloaded files to local cache
	start = time.Now()
	if err := cache.Push(ctx, toDownload); err != nil {
		removeLeftOverFiles(outputs)
		return err
	}
	log.Infof("finished pushing %d files to local cache, took %s", len(toDownload), time.Since(start))

	if len(dups) > 0 {
		// Copy duplicates files to the target location
		start = time.Now()
		if err := copyFiles(ctx, dups, toDownload); err != nil {
			removeLeftOverFiles(outputs)
			return err
		}
		log.Infof("finished copying/hard-linking %d duplicated files, took %s", len(dups), time.Since(start))
	}

	if d.DumpJSON != "" {
		d.updateDownloadStats(outputs, toDownload)
	}

	return nil
}

// DoDownload downloads a root directory from RBE CAS with the given digest.
// It follows the workflow:
//   - Retrieve the directory tree structure through RBE CAS API
//   - Create all directories
//   - Check with local cache and hard-link cached files to target locations
//   - Download uncached files from remote CAS
//   - Push the uncached files to local cache
//   - Copy duplicates files to target locations
//   - Dump downloadStats
func (d *DownloadJob) DoDownload(ctx context.Context) error {
	d.downloadStats = &downloadStats{}
	start := time.Now()
	err := d.doDownloadInternal(ctx)
	d.downloadStats.E2eTimeMs = time.Since(start).Milliseconds()
	if err != nil {
		d.downloadStats.DownloadError = err.Error()
	}

	if d.DumpJSON != "" {
		if dumpErr := dumpStats(d.DumpJSON, d.downloadStats); dumpErr != nil {
			log.Errorf("failed to dump stats to file: %v", dumpErr)
		}
	}
	return err
}

func (d *DownloadJob) doDownloadInternal(ctx context.Context) error {
	c := d.Client

	start := time.Now()
	rootDigest, err := digest.NewFromString(d.Digest)
	if err != nil {
		return fmt.Errorf("failed to parse root digest %s: %v", rootDigest, err)
	}

	rootDir := &repb.Directory{}
	if _, err := c.ReadProto(ctx, rootDigest, rootDir); err != nil {
		return fmt.Errorf("failed to read root directory proto: %v", err)
	}

	dirs, err := c.GetDirectoryTree(ctx, rootDigest.ToProto())
	if err != nil {
		return fmt.Errorf("failed to get directory tree from RBE: %v", err)
	}
	log.Infof("Finished GetDirectoryTree")

	t := &repb.Tree{
		Root:     rootDir,
		Children: dirs,
	}

	flattenTreeOutputs, err := c.FlattenTree(t, d.Dir)
	if err != nil {
		return fmt.Errorf("failed to flatten tree: %v", err)
	}
	log.Infof("Finished FlattenTree")

	if len(d.IncludeFilters) > 0 || len(d.ExcludeFilters) > 0 {
		flattenTreeOutputs, err = d.filterFiles(flattenTreeOutputs)
		if err != nil {
			return fmt.Errorf("failed to filter files/directories: %v", err)
		}
	}

	outputs := make([]*client.TreeOutput, 0, len(flattenTreeOutputs))
	for _, output := range flattenTreeOutputs {
		outputs = append(outputs, output)
	}
	sort.Slice(outputs, func(i, j int) bool {
		return outputs[i].Path < outputs[j].Path
	})
	dirRetrieveTime := time.Since(start)
	log.Infof("finished retriving directory tree from RBE, took %s", dirRetrieveTime)
	d.downloadStats.DirRetrieveTimeMs = dirRetrieveTime.Milliseconds()

	start = time.Now()
	outputs, err = prepareSymLinksAndDirs(d.Dir, outputs)
	if err != nil {
		return err
	}
	dirPrepareTime := time.Since(start)
	log.Infof("finished preparing directories, took %s", dirPrepareTime)
	d.downloadStats.DirPrepareTimeMs = dirPrepareTime.Milliseconds()

	start = time.Now()
	if d.Cache == nil {
		if err := d.downloadWithoutLocalCache(ctx, outputs); err != nil {
			return err
		}
	} else {
		err = d.downloadWithLocalCache(ctx, d.Cache, outputs)
		d.Cache.Close()
		if err != nil {
			return err
		}
	}

	if err := d.moveChunksIndexFileIfNeeded(); err != nil {
		// This is optional and should not fail the download. Just log it.
		log.Error(err)
	}

	fileDownloadTime := time.Since(start)
	log.Infof("finished downloading files, took %s", fileDownloadTime)
	d.downloadStats.FileDownloadTimeMs = fileDownloadTime.Milliseconds()

	if d.ChunksOnly {
		log.Infof("Skipping restoring chunked files since chunks-only is true.")
		d.downloadStats.ChunkRestoreTimeMs = 0
	} else {
		start = time.Now()
		if err := chunkerutil.RestoreFiles(d.Dir, d.Dir, d.KeepChunks); err != nil {
			return err
		}
		chunkRestoreTime := time.Since(start)
		log.Infof("finished restoring chunked files, took %s", chunkRestoreTime)
		d.downloadStats.ChunkRestoreTimeMs = chunkRestoreTime.Milliseconds()
	}

	return nil
}

// Moves the index file to its primary location if not already there. Needed for very old builds.
func (d *DownloadJob) moveChunksIndexFileIfNeeded() error {
	chunkDir := filepath.Join(d.Dir, chunkerutil.ChunksDirName)
	if _, err := os.Stat(chunkDir); err != nil {
		return nil // skip if chunkDir does not exist.
	}
	primaryIndexFile := filepath.Join(chunkDir, chunkerutil.ChunksIndexFileName)
	if _, err := os.Stat(primaryIndexFile); err == nil {
		return nil // skip if primaryIndexFile already exists.
	}
	secondaryIndexFile := filepath.Join(d.Dir, chunkerutil.ChunksIndexFileName)
	if _, err := os.Stat(secondaryIndexFile); err != nil {
		return fmt.Errorf("no chunks index file found")
	}
	if err := os.Rename(secondaryIndexFile, primaryIndexFile); err != nil {
		return fmt.Errorf("failed to move chunks index file: %v", err)
	}

	msg := fmt.Sprintf("Chunks index file moved from %s to %s.", secondaryIndexFile, primaryIndexFile)
	log.Infof("%s", msg)
	d.downloadStats.Notes = msg

	return nil
}
