// Package download is the library for downloading files and directories from RBE CAS.
package download

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"regexp"
	"runtime"
	"sort"
	"strings"
	"time"

	log "github.com/golang/glog"

	"github.com/bazelbuild/remote-apis-sdks/go/pkg/client"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/digest"
	repb "github.com/bazelbuild/remote-apis/build/bazel/remote/execution/v2"
	"github.com/google/device-infra/src/devtools/rbe/casdownloader/cache"
	"golang.org/x/sync/errgroup"

	"github.com/google/device-infra/src/devtools/rbe/casuploader/chunkerutil"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"go.chromium.org/luci/common/data/text/units"
)

// DownloadJob is one download: the tree to fetch, where to put it, and the
// client, cache and limits to do it with. The caller fills it in and hands it
// to DoDownload, which may consult it more than once -- a proxy failure is
// retried directly -- so it describes the work rather than the attempt.
type DownloadJob struct {
	Client   *client.Client
	Digest   string
	Dir      string
	DumpJSON string
	Cache    cache.Cache
	// Filters applied to files to download
	IncludeFilters  []string
	ExcludeFilters  []string
	DownloadStats   *Stats
	KeepChunks      bool
	ChunksOnly      bool
	MinDownloadMbps int64
	DownloadTimeout time.Duration
	CASProxyStatus  string
	// UseProxy reports whether Client is pointed at a casproxy rather than at
	// CAS remote. It decides which side of the WAN the bytes this job had to
	// fetch are attributed to, so it must agree with the address Client dialed.
	UseProxy bool
	Tracker  *ProxyHitTracker
	// Notes carries remarks the caller established before the download began,
	// which is where anything about setting the job up has to be reported from:
	// the stats do not exist yet at that point, and on a fallback attempt they
	// are replaced. Seeded into every attempt's stats so that a condition that
	// is still true on the retry is still reported on the retry.
	Notes []string

	// remoteFailed records whether the last DoDownload failed in a call to
	// Client, as opposed to in local work before or after the fetch. Callers
	// read it through the [DownloadJob.RemoteFailed] method, whose comment
	// explains why the distinction matters.
	remoteFailed bool
}

// RemoteFailed reports whether the last DoDownload failed while talking to
// Client: reading the root directory, walking the tree, or fetching blobs.
//
// Only such a failure can be blamed on casproxy, so only such a failure
// justifies retrying against CAS remote. Everything else DoDownload does --
// pulling from and pushing to the local cache, reserving disk space, copying
// duplicates, restoring chunks -- is local, and switching remotes cannot fix
// it. Worse, a retry after Push has run finds casproxy's bytes in the local
// cache and books them as local hits, erasing the proxy tiers from the stats.
func (d *DownloadJob) RemoteFailed() bool {
	return d.remoteFailed
}

// Stats holds the telemetry data for a download job.
//
// The five size fields partition the tree: every byte the job needed is
// attributed to exactly one of them, and together they sum to the tree's
// logical size. The counts partition the file list the same way.
//
//   - Hot: served by the local cache. Never left the host.
//   - Dedup: a blob the tree references from more than one path. Only the first
//     reference is fetched; the rest are linked or copied locally. These bytes
//     were spared a download, but they say nothing about how well the local
//     cache is working, which is why they are not folded into Hot.
//   - ProxyHot: served from casproxy's own disk cache. Crossed the lab, not
//     the WAN. This is the traffic casproxy exists to eliminate.
//   - ProxyCold: fetched through casproxy, which did not have the blob. An
//     upper bound on WAN traffic rather than a measurement of it: casproxy
//     coalesces concurrent misses for the same blob, so several clients can
//     each book a blob cold that crossed the WAN exactly once. casproxy's own
//     wan_bytes metric is the authoritative number.
//   - Cold: fetched straight from CAS remote with no casproxy in the path,
//     because none was configured, it was unreachable, or the job fell back to
//     a direct connection mid-run.
//
// Hit rates stay binary per cache, as they should: the local cache's is
// Hot/total, and casproxy's, as seen by this client, is
// ProxyHot/(ProxyHot+ProxyCold).
type Stats struct {
	SizeHot             int64  `json:"size_hot"`
	SizeDedup           int64  `json:"size_dedup"`
	SizeProxyHot        int64  `json:"size_proxy_hot"`
	SizeProxyCold       int64  `json:"size_proxy_cold"`
	SizeCold            int64  `json:"size_cold"`
	CountHot            int    `json:"count_hot"`
	CountDedup          int    `json:"count_dedup"`
	CountProxyHot       int    `json:"count_proxy_hot"`
	CountProxyCold      int    `json:"count_proxy_cold"`
	CountCold           int    `json:"count_cold"`
	E2ETimeMS           int64  `json:"e2e_time_ms"`
	DirRetrieveTimeMS   int64  `json:"dir_retrieve_time_ms"`
	DirPrepareTimeMS    int64  `json:"dir_prepare_time_ms"`
	FileDownloadTimeMS  int64  `json:"file_download_time_ms"`
	ChunkRestoreTimeMS  int64  `json:"chunk_restore_time_ms"`
	CacheLockWaitTimeMS int64  `json:"cache_lock_wait_time_ms"`
	DownloadError       string `json:"download_error,omitempty"`
	Notes               string `json:"notes,omitempty"`
	CASProxy            string `json:"casproxy,omitempty"`
}

// noteSeparator joins the notes of a single run into the one string the stats
// carry. Chosen over a newline because the field ends up in a JSON blob that is
// read by eye as often as by machine. sanitizeNote guarantees no entry contains
// its "|", so splitting on "|" recovers exactly the entries.
const noteSeparator = " | "

// noteSanitizer replaces what could make one note read as several: the
// separator's "|", and line breaks, which would also split the field across
// lines in the stats JSON and in AnTS. "_" is used for "|" because it is ASCII
// and cannot be mistaken for a separator or for path structure.
var noteSanitizer = strings.NewReplacer("|", "_", "\r\n", " ", "\r", " ", "\n", " ")

// sanitizeNote makes msg safe to join with noteSeparator.
func sanitizeNote(msg string) string {
	return noteSanitizer.Replace(msg)
}

// addNote records something the operator should know about a run that is going
// to succeed anyway.
//
// It is the counterpart to DownloadError, which is for the reason a run failed.
// Anything that degrades a download without invalidating it belongs here: the
// bytes are correct and the caller is not going to be told otherwise, so
// without a note the only evidence is a log line on one host among thousands.
//
// Notes accumulate rather than overwrite. There can be more than one thing
// worth saying about a run, and the second one is not more important than the
// first. The log line keeps the message verbatim; only the copy in the stats is
// sanitized.
func (d *DownloadJob) addNote(format string, args ...any) {
	msg := fmt.Sprintf(format, args...)
	log.Warningf("%s", msg)
	if d.DownloadStats == nil {
		return
	}
	msg = sanitizeNote(msg)
	if d.DownloadStats.Notes == "" {
		d.DownloadStats.Notes = msg
		return
	}
	d.DownloadStats.Notes += noteSeparator + msg
}

// Stats returns the download stats for the job.
func (d *DownloadJob) Stats() *Stats {
	return d.DownloadStats
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
		return nil, fmt.Errorf("failed to create the root directory: %w", err)
	}

	for dir := range dirSet {
		if err := os.MkdirAll(dir, 0o700); err != nil && !os.IsExist(err) {
			return nil, fmt.Errorf("failed to create directory: %w", err)
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
				if os.IsExist(err) {
					_ = os.Remove(output.Path)
					if err := os.Symlink(output.SymlinkTarget, output.Path); err == nil {
						numSymLink++
						continue
					}
				}
				return nil, fmt.Errorf("failed to create symlink to %s: %w", output.Path, err)
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
				return fmt.Errorf("failed to copy file from '%s' to '%s': %w", src.Path, dst.Path, err)
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

// updateDownloadStats attributes every byte of the tree to exactly one tier.
// See Stats for what the tiers mean.
//
// all is the full file list, including each path a duplicated blob appears at;
// downloaded is keyed by digest and so holds one entry per blob actually
// fetched; dups is the remainder, the paths that were folded away.
func (d *DownloadJob) updateDownloadStats(all []*client.TreeOutput, downloaded map[digest.Digest]*client.TreeOutput, dups []*client.TreeOutput) {
	var sizeTotal int64
	for _, output := range all {
		sizeTotal += output.Digest.Size
	}

	var sizeDownloaded int64
	for _, output := range downloaded {
		sizeDownloaded += output.Digest.Size
	}

	var sizeDedup int64
	for _, output := range dups {
		sizeDedup += output.Digest.Size
	}

	// Whatever was neither fetched nor deduplicated came out of the local cache.
	sizeHot := sizeTotal - sizeDownloaded - sizeDedup
	countHot := len(all) - len(downloaded) - len(dups)

	// What casproxy reported serving from its own disk, via response trailers.
	var sizeProxyHot int64
	var countProxyHot int
	if d.Tracker != nil {
		sizeProxyHot = d.Tracker.HitBytes()
		countProxyHot = d.Tracker.HitCount()
		// A blob re-read after a stream failure can be reported twice. Clamping
		// keeps the partition adding up, but over-reporting is the signature of
		// a bug, and left silent it looks exactly like a perfect proxy hit rate.
		if sizeProxyHot > sizeDownloaded || countProxyHot > len(downloaded) {
			d.addNote("casproxy reported serving more than was downloaded (%v in %d blobs reported, %v in %d downloaded); clamping, proxy hit rate is understated",
				units.Size(sizeProxyHot), countProxyHot, units.Size(sizeDownloaded), len(downloaded))
			if sizeProxyHot > sizeDownloaded {
				sizeProxyHot = sizeDownloaded
			}
			if countProxyHot > len(downloaded) {
				countProxyHot = len(downloaded)
			}
		}
	}

	// Everything fetched that casproxy did not serve came over the WAN. Which
	// side of it the job pulled from is decided once, when the client dials: a
	// mid-run fallback to CAS remote resets the tracker and restarts the
	// download, so a single set of stats never mixes the two paths.
	sizeMissed := sizeDownloaded - sizeProxyHot
	countMissed := len(downloaded) - countProxyHot
	var sizeProxyCold, sizeCold int64
	var countProxyCold, countCold int
	if d.UseProxy {
		sizeProxyCold, countProxyCold = sizeMissed, countMissed
	} else {
		sizeCold, countCold = sizeMissed, countMissed
	}

	log.Infof("Stats of cache: hot: %v (%d), dedup: %v (%d), proxy-hot: %v (%d), proxy-cold: %v (%d), cold: %v (%d)",
		units.Size(sizeHot), countHot,
		units.Size(sizeDedup), len(dups),
		units.Size(sizeProxyHot), countProxyHot,
		units.Size(sizeProxyCold), countProxyCold,
		units.Size(sizeCold), countCold)

	d.DownloadStats.SizeHot = sizeHot
	d.DownloadStats.SizeDedup = sizeDedup
	d.DownloadStats.SizeProxyHot = sizeProxyHot
	d.DownloadStats.SizeProxyCold = sizeProxyCold
	d.DownloadStats.SizeCold = sizeCold
	d.DownloadStats.CountHot = countHot
	d.DownloadStats.CountDedup = len(dups)
	d.DownloadStats.CountProxyHot = countProxyHot
	d.DownloadStats.CountProxyCold = countProxyCold
	d.DownloadStats.CountCold = countCold
}

func dumpStats(path string, stats *Stats) error {
	statsJSON, err := json.Marshal(stats)

	if err != nil {
		return fmt.Errorf("failed to marshal stats json: %w", err)
	}
	if err := os.WriteFile(path, statsJSON, 0600); err != nil {
		return fmt.Errorf("failed to write stats json: %w", err)
	}
	return nil
}

func (d *DownloadJob) filterFiles(fullSet map[string]*client.TreeOutput) (map[string]*client.TreeOutput, error) {
	var includePatterns []*regexp.Regexp
	var excludePatterns []*regexp.Regexp

	for _, filter := range d.IncludeFilters {
		p, err := regexp.Compile(filter)
		if err != nil {
			return nil, fmt.Errorf("fail to compile filter %s: %w", filter, err)
		}
		includePatterns = append(includePatterns, p)
	}
	for _, filter := range d.ExcludeFilters {
		p, err := regexp.Compile(filter)
		if err != nil {
			return nil, fmt.Errorf("fail to compile filter %s: %w", filter, err)
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
			return fmt.Errorf("failed to make path relative for %s: %w", output.Path, err)
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

// CalculateTimeout calculates the effective download timeout based on minDownloadMbps and size.
func CalculateTimeout(minDownloadMbps int64, size int64) time.Duration {
	const minTimeout = 10 * time.Second
	if minDownloadMbps <= 0 || size <= 0 {
		return minTimeout // Never return 0 duration.
	}
	// size is in bytes, minDownloadMbps is in megabytes per second.
	// Convert minDownloadMbps to bytes per second for calculation.
	bps := minDownloadMbps * 1024 * 1024
	seconds := float64(size) / float64(bps)
	// Use time.Duration(seconds*float64(time.Second)) to convert seconds to Duration.
	// Add minTimeout to ensure a base timeout.
	// Cap the calculated duration to avoid overflow if seconds is very large.
	// MaxInt64 / time.Second is roughly 292 years, which is a sufficiently large timeout.
	maxDuration := time.Duration(1<<63 - 1)
	calculatedDuration := time.Duration(seconds * float64(time.Second))
	if calculatedDuration < 0 || calculatedDuration > maxDuration-minTimeout { // Check for overflow
		return maxDuration
	}
	return minTimeout + calculatedDuration
}

func calculateAndLogTimeout(ctx context.Context, downloadTimeout time.Duration, minDownloadMbps int64, size int64) time.Duration {
	calculatedTimeout := CalculateTimeout(minDownloadMbps, size)
	log.InfoContextf(ctx, "Calculated dynamic timeout %v based on size %v and min-download-mbps %v", calculatedTimeout.Truncate(time.Millisecond), units.Size(size), minDownloadMbps)

	if parentDeadline, ok := ctx.Deadline(); ok {
		parentRemaining := time.Until(parentDeadline)
		if parentRemaining < calculatedTimeout {
			log.InfoContextf(ctx, "The effective timeout is constrained by download-timeout: %v (%v remaining)", downloadTimeout.Truncate(time.Millisecond), parentRemaining.Truncate(time.Millisecond))
		}
	}
	return calculatedTimeout
}

func (d *DownloadJob) downloadWithoutLocalCache(ctx context.Context, outputs []*client.TreeOutput) error {
	// A blob referenced from several paths is worth fetching only once, but the
	// paths that were folded away still have to be materialized afterwards.
	// Building the map by hand here would silently drop them.
	toDownload, dups := convertTreeOutputListToMap(outputs)

	var sumSize int64
	for _, output := range toDownload {
		sumSize += output.Digest.Size
	}

	var cancel context.CancelFunc = func() {}
	if d.MinDownloadMbps > 0 { // only set timeout if minDownloadMbps is positive.
		timeout := calculateAndLogTimeout(ctx, d.DownloadTimeout, d.MinDownloadMbps, sumSize)
		// Use a child context with the speed-based timeout. Go's context.WithTimeout ensures
		// that the child's deadline is no later than the parent's deadline (the fixed timeout),
		// so the shorter of the two will be used.
		ctx, cancel = context.WithTimeout(ctx, timeout)
	}
	defer cancel()

	start := time.Now()
	if err := d.downloadFilesWithAbsolutePath(ctx, toDownload); err != nil {
		d.remoteFailed = true
		removeLeftOverFiles(outputs)
		if ctx.Err() == context.DeadlineExceeded {
			return context.DeadlineExceeded
		}
		return fmt.Errorf("failed to download files: %w", err)
	}
	log.InfoContextf(ctx, "finished downloading %d files from CAS without local cache, took %s", len(toDownload), time.Since(start))

	if len(dups) > 0 {
		start = time.Now()
		if err := copyFiles(ctx, dups, toDownload); err != nil {
			removeLeftOverFiles(outputs)
			if ctx.Err() == context.DeadlineExceeded {
				return context.DeadlineExceeded
			}
			return fmt.Errorf("failed to copy duplicated files: %w", err)
		}
		log.InfoContextf(ctx, "finished copying/hard-linking %d duplicated files, took %s", len(dups), time.Since(start))
	}

	d.updateDownloadStats(outputs, toDownload, dups)

	return nil
}

func (d *DownloadJob) downloadWithLocalCache(ctx context.Context, c cache.Cache, outputs []*client.TreeOutput) error {
	start := time.Now()
	cached, missed, err := c.Pull(ctx, outputs)
	if err != nil {
		if ctx.Err() == context.DeadlineExceeded {
			return context.DeadlineExceeded
		}
		return fmt.Errorf("failed to pull files from cache: %w", err)
	}
	log.InfoContextf(ctx, "finished pulling %d files from cache, took %s", len(cached), time.Since(start))

	if len(missed) <= 0 {
		log.InfoContextf(ctx, "All files in cache. Skip downloading files.")
		d.updateDownloadStats(outputs, nil, nil)
		return nil
	}

	toDownload, dups := convertTreeOutputListToMap(missed)

	var sumSize int64
	for _, output := range toDownload {
		sumSize += output.Digest.Size
	}
	log.InfoContextf(ctx, "start downloading %d files, estimated size %v", len(toDownload), units.Size(sumSize))

	// Make room before fetching rather than after.
	//
	// These bytes land in the output directory, and the cache is only offered
	// them afterwards, at Push. A cache that evicted on ingest would therefore
	// always be reacting to a disk that had already filled: that is precisely
	// how the production ENOSPC arose, since luci's cache trims from Add. The
	// check belongs here, where the size of what is about to be written is
	// known and nothing has been written yet.
	//
	// Reserving is an optional capability rather than part of Cache, so
	// LocalCache keeps its existing behavior untouched while the lock-free
	// implementation rolls out.
	//
	// sumSize is a floor, not the true cost: it counts blob contents and not
	// the filesystem's block rounding, which for a tree of many small files is
	// not negligible. EnsureHeadroom absorbs some of that slack by also
	// insisting the low watermark survive the write.
	if reserver, ok := c.(cache.HeadroomReserver); ok {
		// A failure here is fatal, because by contract it means the write
		// cannot fit: falling short of the low watermark is a warning and
		// returns nil, so an error says free space is genuinely below what we
		// are about to fetch. The download would then hit ENOSPC on the same
		// volume moments later. Stopping costs nothing that proceeding would
		// have saved, and it reports the real reason, before the bytes rather
		// than after -- which is the whole point of checking here.
		if err := reserver.EnsureHeadroom(ctx, sumSize); err != nil {
			removeLeftOverFiles(outputs)
			return fmt.Errorf("not enough disk space to download %v: %w", units.Size(sumSize), err)
		}
	}

	var cancel context.CancelFunc = func() {}
	if d.MinDownloadMbps > 0 { // only set timeout if minDownloadMbps is positive.
		timeout := calculateAndLogTimeout(ctx, d.DownloadTimeout, d.MinDownloadMbps, sumSize)
		// Use a child context with the speed-based timeout. Go's context.WithTimeout ensures
		// that the child's deadline is no later than the parent's deadline (the fixed timeout),
		// so the shorter of the two will be used.
		ctx, cancel = context.WithTimeout(ctx, timeout)
	}
	defer cancel()

	start = time.Now()
	if err := d.downloadFilesWithAbsolutePath(ctx, toDownload); err != nil {
		d.remoteFailed = true
		removeLeftOverFiles(outputs)
		if ctx.Err() == context.DeadlineExceeded {
			return context.DeadlineExceeded
		}
		return fmt.Errorf("failed to download files: %w", err)
	}
	log.InfoContextf(ctx, "finished downloading %d files from CAS, took %s", len(toDownload), time.Since(start))

	d.updateDownloadStats(outputs, toDownload, dups)

	// Push downloaded files to local cache
	start = time.Now()
	if err := c.Push(ctx, toDownload); err != nil {
		// A cache write failure is not a download failure. Every file the
		// caller asked for is already on disk, complete, and carrying the
		// mode the SDK gave it; all that has been lost is the copy that
		// would have saved a fetch next time. Deleting the tree and failing,
		// which is what this did before, escalated a local and self-healing
		// condition -- a full disk, a cache directory someone made read-only,
		// an evictor that could not keep up -- into a failed test run, and
		// did so on precisely the hosts least able to afford one.
		//
		// A partially ingested cache is fine and needs no unwinding: it is
		// content-addressed, so every blob that did land is independently
		// valid and the rest are ordinary misses.
		//
		// A context error is the exception. There the job itself is over, the
		// tree is not going to be used by anyone, and the usual cleanup and
		// failure are still what the caller expects.
		if ctxErr := ctx.Err(); ctxErr != nil {
			removeLeftOverFiles(outputs)
			if ctxErr == context.DeadlineExceeded {
				return context.DeadlineExceeded
			}
			return fmt.Errorf("failed to push files to cache: %w", err)
		}
		d.addNote("Failed to cache %d downloaded files, so they will be fetched again next time: %v", len(toDownload), err)
	} else {
		log.InfoContextf(ctx, "finished pushing %d files to local cache, took %s", len(toDownload), time.Since(start))
	}

	if len(dups) > 0 {
		// Copy duplicates files to the target location
		start = time.Now()
		if err := copyFiles(ctx, dups, toDownload); err != nil {
			removeLeftOverFiles(outputs)
			if ctx.Err() == context.DeadlineExceeded {
				return context.DeadlineExceeded
			}
			return fmt.Errorf("failed to copy duplicated files: %w", err)
		}
		log.InfoContextf(ctx, "finished copying/hard-linking %d duplicated files, took %s", len(dups), time.Since(start))
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
	// Notes seeded from the caller do not pass through addNote, so they are
	// sanitized here to keep the separator unambiguous.
	seeded := make([]string, len(d.Notes))
	for i, note := range d.Notes {
		seeded[i] = sanitizeNote(note)
	}
	d.DownloadStats = &Stats{
		CASProxy: d.CASProxyStatus,
		Notes:    strings.Join(seeded, noteSeparator),
	}
	d.remoteFailed = false
	if d.DownloadTimeout > 0 {
		var cancel context.CancelFunc
		// Apply the fixed download timeout as a parent context.
		ctx, cancel = context.WithTimeout(ctx, d.DownloadTimeout)
		defer cancel()
		log.InfoContextf(ctx, "Applying fixed download timeout: %v", d.DownloadTimeout)
	}

	start := time.Now()
	err := d.doDownloadInternal(ctx)
	d.DownloadStats.E2ETimeMS = time.Since(start).Milliseconds()

	// We check the context state before the library error.
	// If the context is done, it is a timeout (or cancel), regardless of the returned err.
	if ctx.Err() != nil {
		d.DownloadStats.DownloadError = fmt.Sprintf("TIMED_OUT: download-timeout=%v", d.DownloadTimeout)
		err = context.DeadlineExceeded
	} else if errors.Is(err, context.DeadlineExceeded) {
		d.DownloadStats.DownloadError = fmt.Sprintf("TIMED_OUT: min-download-mbps=%v", d.MinDownloadMbps)
	} else if err != nil {
		// Context is still healthy, so this is a genuine library or network failure.
		d.DownloadStats.DownloadError = err.Error()
		// Fallback: check gRPC status code if the library returned a remote timeout.
		if status.Code(err) == codes.DeadlineExceeded {
			d.DownloadStats.DownloadError = fmt.Sprintf("TIMED_OUT: %v", err)
			err = context.DeadlineExceeded
		}
	}

	if d.DumpJSON != "" {
		if dumpErr := dumpStats(d.DumpJSON, d.DownloadStats); dumpErr != nil {
			log.ErrorContextf(ctx, "failed to dump stats to file: %v", dumpErr)
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
		d.remoteFailed = true
		return fmt.Errorf("failed to read root directory proto: %v", err)
	}

	dirs, err := c.GetDirectoryTree(ctx, rootDigest.ToProto())
	if err != nil {
		d.remoteFailed = true
		return fmt.Errorf("failed to get directory tree from RBE: %v", err)
	}
	log.InfoContextf(ctx, "Finished GetDirectoryTree")

	t := &repb.Tree{
		Root:     rootDir,
		Children: dirs,
	}

	flattenTreeOutputs, err := c.FlattenTree(t, d.Dir)
	if err != nil {
		return fmt.Errorf("failed to flatten tree: %v", err)
	}
	log.InfoContextf(ctx, "Finished FlattenTree")

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
	log.InfoContextf(ctx, "finished retriving directory tree from RBE, took %s", dirRetrieveTime)
	d.DownloadStats.DirRetrieveTimeMS = dirRetrieveTime.Milliseconds()

	start = time.Now()
	outputs, err = prepareSymLinksAndDirs(d.Dir, outputs)
	if err != nil {
		return err
	}
	dirPrepareTime := time.Since(start)
	log.InfoContextf(ctx, "finished preparing directories, took %s", dirPrepareTime)
	d.DownloadStats.DirPrepareTimeMS = dirPrepareTime.Milliseconds()

	start = time.Now()
	if d.Cache == nil {
		if err := d.downloadWithoutLocalCache(ctx, outputs); err != nil {
			return err
		}
	} else {
		err = d.downloadWithLocalCache(ctx, d.Cache, outputs)
		if lc, ok := d.Cache.(interface{ LockWaitTimeMS() int64 }); ok {
			d.DownloadStats.CacheLockWaitTimeMS = lc.LockWaitTimeMS()
		}
		d.Cache.Close()
		if err != nil {
			return err
		}
	}

	if err := d.moveChunksIndexFileIfNeeded(); err != nil {
		// Optional, so it does not fail the download, but a run that could not
		// place its chunks index is not quite the run that was asked for.
		d.addNote("Failed to move the chunks index file: %v", err)
	}

	fileDownloadTime := time.Since(start)
	log.InfoContextf(ctx, "finished downloading files, took %s", fileDownloadTime)
	d.DownloadStats.FileDownloadTimeMS = fileDownloadTime.Milliseconds()

	if d.ChunksOnly {
		log.InfoContextf(ctx, "Skipping restoring chunked files since chunks-only is true.")
		d.DownloadStats.ChunkRestoreTimeMS = 0
	} else {
		start = time.Now()
		if err := chunkerutil.RestoreFiles(d.Dir, d.Dir, d.KeepChunks); err != nil {
			return err
		}
		chunkRestoreTime := time.Since(start)
		log.InfoContextf(ctx, "finished restoring chunked files, took %s", chunkRestoreTime)
		d.DownloadStats.ChunkRestoreTimeMS = chunkRestoreTime.Milliseconds()
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

	d.addNote("Chunks index file moved from %s to %s.", secondaryIndexFile, primaryIndexFile)

	return nil
}

// TransferredSize returns the bytes that had to come over the network, whether
// casproxy served them or not.
func (d *DownloadJob) TransferredSize() int64 {
	if d.DownloadStats == nil {
		return 0
	}
	return d.DownloadStats.SizeProxyHot + d.DownloadStats.SizeProxyCold + d.DownloadStats.SizeCold
}

// WANSize returns the bytes that had to be fetched from CAS remote, either by
// this client or by casproxy on its behalf. It is an upper bound: see Stats.
func (d *DownloadJob) WANSize() int64 {
	if d.DownloadStats == nil {
		return 0
	}
	return d.DownloadStats.SizeProxyCold + d.DownloadStats.SizeCold
}

// TotalSize returns the sum of all file sizes in the tree.
func (d *DownloadJob) TotalSize() int64 {
	if d.DownloadStats == nil {
		return 0
	}
	s := d.DownloadStats
	return s.SizeHot + s.SizeDedup + s.SizeProxyHot + s.SizeProxyCold + s.SizeCold
}

// ProxyHotSize returns the bytes casproxy served out of its own disk cache.
func (d *DownloadJob) ProxyHotSize() int64 {
	if d.DownloadStats == nil {
		return 0
	}
	return d.DownloadStats.SizeProxyHot
}

// HotSize returns the bytes served by the worker's local directory cache.
func (d *DownloadJob) HotSize() int64 {
	if d.DownloadStats == nil {
		return 0
	}
	return d.DownloadStats.SizeHot
}
