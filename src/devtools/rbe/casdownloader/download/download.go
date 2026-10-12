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
	"slices"
	"sort"
	"strings"
	"syscall"
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
	IncludeFilters []string
	ExcludeFilters []string
	// CopyFilters are regular expressions on paths relative to Dir. Downloaded
	// files that match are left as private copies rather than hardlinks, so
	// callers can modify them in place without corrupting the cache.
	//
	// That covers links to other files in Dir as well as to the cache: a blob
	// that appears at several paths with the same mode is fetched once and
	// hardlinked to the rest (see materializeDuplicate), whether or not there
	// is a cache and whatever the cache's hardlink setting. A matched file
	// gets its own inode in either case.
	CopyFilters   []string
	DownloadStats *Stats
	ChunksOnly    bool
	// DisableOverwrite fails the download if a file it would write, other
	// than chunk data, is already in Dir, rather than replace it. Either way,
	// what is in Dir is left alone if the download fails before it commits.
	DisableOverwrite bool
	MinDownloadMbps  int64
	DownloadTimeout  time.Duration
	CASProxyStatus   string
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
	Notes []Note

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
	// CorruptBlobsQuarantined is how many local cache hits were found to be
	// the wrong size, removed from the cache, and downloaded again, so they
	// are not counted in Hot. Zero for a cache that does not check, such as
	// LocalCache.
	CorruptBlobsQuarantined int64 `json:"corrupt_blobs_quarantined"`
	// ExecMismatchCopies and PermDriftCopies count local cache hits that
	// could not share the cached inode because its mode did not match the
	// tree node, and were copied instead; the Bytes fields are what those
	// copies wrote. See cache.ModeCopyStats for what separates the two. They
	// are counted in Hot: the bytes still came from the cache. Zero for a
	// cache that does not copy on a mismatch, such as LocalCache.
	ExecMismatchCopies int64 `json:"exec_mismatch_copies"`
	ExecMismatchBytes  int64 `json:"exec_mismatch_bytes"`
	PermDriftCopies    int64 `json:"perm_drift_copies"`
	PermDriftBytes     int64 `json:"perm_drift_bytes"`
	// NoteReasons classifies Notes, one entry per note in the same order. It
	// is exported as a metric rather than written to the JSON: the note text
	// already reaches AnTS, and CF, which does not read the JSON, needs the
	// metric.
	NoteReasons []NoteReason `json:"-"`
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

// NoteReason classifies a note with a bounded code that can be used as a metric
// field. The note text cannot be: it embeds paths and raw OS errors, so its
// cardinality is unbounded, and it can carry details that should stay on the
// host.
//
// Every note has exactly one reason, assigned where the note is written. Do not
// recover a reason from the text afterwards; that silently reclassifies the day
// a message changes.
type NoteReason string

// Reasons for notes. Adding one is cheap, but each is a value in a metric
// field that dashboards and alerts may key on, so an existing value should not
// be renamed.
const (
	// NoteCacheSetupFailed means the local cache could not be opened, so the run
	// downloaded without one.
	NoteCacheSetupFailed NoteReason = "cache_setup_failed"
	// NoteCacheWriteFailed means the download succeeded but its files could not be
	// pushed to the local cache, so they will be fetched again next time.
	NoteCacheWriteFailed NoteReason = "cache_write_failed"
	// NoteChunksIndexMoveFailed means the chunks index could not be moved to its
	// primary location.
	NoteChunksIndexMoveFailed NoteReason = "chunks_index_move_failed"
	// NoteLegacyChunksIndexMoved means the chunks index was found in its legacy
	// location and moved. Not a degradation; counted so that it is visible when
	// no build needs that code path any more.
	NoteLegacyChunksIndexMoved NoteReason = "legacy_chunks_index_moved"
	// NoteProxyOverreportClamped means casproxy reported serving more than was
	// downloaded, and the proxy tiers were clamped.
	NoteProxyOverreportClamped NoteReason = "proxy_overreport_clamped"
	// NoteExistingFilesReplaced means the download replaced files that were
	// already in Dir. Not a degradation; counted to show how often
	// DisableOverwrite would have failed a run.
	NoteExistingFilesReplaced NoteReason = "existing_files_replaced"
)

// Note is a remark about a run, with the reason that classifies it.
type Note struct {
	Reason  NoteReason
	Message string
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
func (d *DownloadJob) addNote(reason NoteReason, format string, args ...any) {
	msg := fmt.Sprintf(format, args...)
	log.Warningf("%s", msg)
	if d.DownloadStats == nil {
		return
	}
	d.DownloadStats.appendNote(Note{Reason: reason, Message: msg})
}

// appendNote adds note to the stats' notes and reasons.
func (s *Stats) appendNote(note Note) {
	s.NoteReasons = append(s.NoteReasons, note.Reason)
	msg := sanitizeNote(note.Message)
	if s.Notes == "" {
		s.Notes = msg
		return
	}
	s.Notes += noteSeparator + msg
}

// Stats returns the download stats for the job.
func (d *DownloadJob) Stats() *Stats {
	return d.DownloadStats
}

// prepareSymLinksAndDirs creates directories and symbolic links. It is executed before checking
// with cache or downloading files from remote since the TreeOutput contains information of
// directories and symbolic links. It returns a new list of *client.TreeOutput excluding created
// directories and symbolic links.
//
// Symbolic links are created at the temporary names s.stage gave them, so a
// name that is taken is an error, not something to replace: the existing
// path is replaced when s commits.
func prepareSymLinksAndDirs(s *staging, outputs []*client.TreeOutput) ([]*client.TreeOutput, error) {
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

	if err := s.mkdirAll(s.dir); err != nil {
		return nil, fmt.Errorf("failed to create the root directory: %w", err)
	}

	for dir := range dirSet {
		if err := s.mkdirAll(dir); err != nil {
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

	// The mode passed to OpenFile is filtered through the umask. Set it
	// explicitly so a copied duplicate carries exactly the canonical mode,
	// as a linked one does.
	if err := dst.Chmod(mode); err != nil {
		return err
	}
	_, err = io.Copy(dst, src)
	return err
}

// materializeDuplicate writes dst, whose blob was already fetched to src, by
// hard linking src if the modes match and copying it otherwise. It is a
// variable so that tests can make it fail.
var materializeDuplicate = func(dst, src *client.TreeOutput) error {
	if fileMode(dst) == fileMode(src) {
		// Create a hard link if file mode matches.
		err := os.Link(src.Path, dst.Path)
		if err == nil {
			return nil
		}
		log.Infof("failed to link file from '%s' to '%s': %v", src.Path, dst.Path, err)
		// Fall back to copy the file.
	}
	if err := copyFile(dst.Path, src.Path, fileMode(dst)); err != nil {
		return fmt.Errorf("failed to copy file from '%s' to '%s': %w", src.Path, dst.Path, err)
	}
	return nil
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
			return materializeDuplicate(dst, src)
		})
	}
	return eg.Wait()
}

// fileMode is the mode a downloaded file gets: the cache package's canonical
// mode for its tree node, so that a file comes out the same whether it was
// downloaded, pulled from the cache or copied from a duplicate.
func fileMode(output *client.TreeOutput) os.FileMode {
	return cache.FileMode(output.IsExecutable)
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
			d.addNote(NoteProxyOverreportClamped, "casproxy reported serving more than was downloaded (%v in %d blobs reported, %v in %d downloaded); clamping, proxy hit rate is understated",
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

func compileCopyFilters(filters []string) ([]*regexp.Regexp, error) {
	var patterns []*regexp.Regexp
	for _, f := range filters {
		p, err := regexp.Compile(f)
		if err != nil {
			return nil, fmt.Errorf("invalid -copy-filters pattern %q: %w", f, err)
		}
		patterns = append(patterns, p)
	}
	return patterns, nil
}

// applyCopyFilters replaces each hardlinked file in files whose final path
// relative to d.Dir matches patterns with a private copy, so modifying it in
// place cannot alter the cache or other files. It works on the temporary
// files, before they are committed.
func (d *DownloadJob) applyCopyFilters(ctx context.Context, patterns []*regexp.Regexp, files []stagedFile) error {
	if len(patterns) == 0 {
		return nil
	}
	for _, f := range files {
		if err := ctx.Err(); err != nil {
			return err
		}
		rel, err := filepath.Rel(d.Dir, f.final)
		if err != nil || !slices.ContainsFunc(patterns, func(p *regexp.Regexp) bool { return p.MatchString(filepath.ToSlash(rel)) }) {
			continue
		}
		info, err := os.Lstat(f.tmp)
		if err == nil && !info.Mode().IsRegular() {
			continue // e.g. a symlink.
		}
		if err != nil {
			return err
		}
		if info.Sys().(*syscall.Stat_t).Nlink <= 1 {
			continue
		}
		if err := replaceWithCopy(f.tmp, info.Mode()); err != nil {
			return fmt.Errorf("failed to replace hardlink %s with a copy: %w", f.final, err)
		}
	}
	return nil
}

// replaceWithCopy atomically replaces path with a copy of itself on a new inode.
func replaceWithCopy(path string, mode os.FileMode) error {
	src, err := os.OpenFile(path, os.O_RDONLY|syscall.O_NOFOLLOW, 0)
	if err != nil {
		return err
	}
	defer src.Close()
	tmp, err := os.CreateTemp(filepath.Dir(path), "."+filepath.Base(path)+".copy.*")
	if err != nil {
		return err
	}
	defer os.Remove(tmp.Name()) // No-op after a successful rename.
	defer tmp.Close()
	if _, err := io.Copy(tmp, src); err != nil {
		return err
	}
	if err := tmp.Chmod(mode); err != nil {
		return err
	}
	if err := tmp.Close(); err != nil {
		return err
	}
	return os.Rename(tmp.Name(), path)
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
//
// It is only given outputs that staging has pointed at this attempt's own
// temporary names or at chunk data the attempt claimed, so it never removes a
// file that was in Dir before the run. staging.abort cleans up the rest.
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
	if _, err := d.Client.DownloadFiles(ctx, d.Dir, toDownloadRelative); err != nil {
		return err
	}
	return normalizeModes(toDownload)
}

// normalizeModes gives each downloaded file the canonical mode of its tree
// node.
//
// The SDK's own modes depend on how it fetched a blob: it creates batched
// files with ExecutableMode or RegularMode filtered through the umask, but
// chmods streamed executables to ExecutableMode exactly, so a large
// executable comes out 0777 and a small one 0755. Normalizing here makes the
// result the same on every path -- with or without a cache, and whether or
// not Push succeeds -- and independent of the umask. On the cache path Push
// sets the same mode again before sharing the file, which costs nothing
// further.
func normalizeModes(outputs map[digest.Digest]*client.TreeOutput) error {
	for _, output := range outputs {
		if err := os.Chmod(output.Path, fileMode(output)); err != nil {
			return fmt.Errorf("failed to set mode of downloaded file %s: %w", output.Path, err)
		}
	}
	return nil
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
		d.addNote(NoteCacheWriteFailed, "Failed to cache %d downloaded files, so they will be fetched again next time: %v", len(toDownload), err)
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
//   - Claim the chunk data paths, if the tree has chunk data
//   - Create all directories
//   - Check with local cache and hard-link cached files to target locations
//   - Download uncached files from remote CAS
//   - Push the uncached files to local cache
//   - Copy duplicates files to target locations
//   - Restore chunked files and apply copy filters
//   - Move every file into place
//   - Dump downloadStats
//
// Files and symlinks are written under temporary names and only renamed to
// their paths in the tree once everything else has succeeded. If the
// download fails or is cancelled, what it wrote is removed, and files that
// were in Dir before are left untouched. See staging.
//
// A file already at a path the download writes is replaced, and a note
// records that it was, unless DisableOverwrite is set, in which case the
// download fails instead.
func (d *DownloadJob) DoDownload(ctx context.Context) error {
	d.DownloadStats = &Stats{CASProxy: d.CASProxyStatus}
	// Notes seeded from the caller were logged where they were written, so
	// they go straight into the stats rather than through addNote.
	for _, note := range d.Notes {
		d.DownloadStats.appendNote(note)
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
	if errors.Is(ctx.Err(), context.Canceled) {
		// Cancelled by the caller, as on a signal, rather than timed out. A
		// cancellation after the download committed does not undo it.
		if err != nil {
			d.DownloadStats.DownloadError = fmt.Sprintf("CANCELLED: %v", context.Cause(ctx))
			err = fmt.Errorf("download cancelled: %w", context.Cause(ctx))
		}
	} else if ctx.Err() != nil {
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

	copyPatterns, err := compileCopyFilters(d.CopyFilters)
	if err != nil {
		return err
	}

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

	// Write everything under temporary names, and put it in place only once
	// all of it has succeeded, so a failed or cancelled attempt leaves Dir as
	// it found it.
	s, err := newStaging(d.Dir)
	if err != nil {
		return err
	}
	s.disableOverwrite = d.DisableOverwrite
	committed := false
	defer func() {
		if !committed {
			s.abort()
		}
	}()
	hasChunkData := s.stage(outputs)
	if d.DisableOverwrite {
		// Fail before fetching anything. Files restored from chunk data are
		// not known yet; commit checks those.
		if err := s.checkNoneExist(); err != nil {
			return err
		}
	}
	if hasChunkData {
		if err := s.claimChunkData(); err != nil {
			return err
		}
	}
	// The tree's own files, before restored files are added.
	treeFiles := s.files

	start = time.Now()
	outputs, err = prepareSymLinksAndDirs(s, outputs)
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
		if r, ok := d.Cache.(cache.CorruptBlobReporter); ok {
			d.DownloadStats.CorruptBlobsQuarantined = r.CorruptBlobsQuarantined()
		}
		if r, ok := d.Cache.(cache.ModeCopyReporter); ok {
			m := r.ModeCopies()
			d.DownloadStats.ExecMismatchCopies = m.ExecMismatchCopies
			d.DownloadStats.ExecMismatchBytes = m.ExecMismatchBytes
			d.DownloadStats.PermDriftCopies = m.PermDriftCopies
			d.DownloadStats.PermDriftBytes = m.PermDriftBytes
		}
		d.Cache.Close()
		if err != nil {
			return err
		}
	}

	if s.chunksClaimed {
		if err := d.moveChunksIndexFileIfNeeded(); err != nil {
			// Optional, so it does not fail the download, but a run that could not
			// place its chunks index is not quite the run that was asked for.
			d.addNote(NoteChunksIndexMoveFailed, "Failed to move the chunks index file: %v", err)
		}
	}

	fileDownloadTime := time.Since(start)
	log.InfoContextf(ctx, "finished downloading files, took %s", fileDownloadTime)
	d.DownloadStats.FileDownloadTimeMS = fileDownloadTime.Milliseconds()

	if d.ChunksOnly {
		log.InfoContextf(ctx, "Skipping restoring chunked files since chunks-only is true.")
		d.DownloadStats.ChunkRestoreTimeMS = 0
	} else {
		// For a chunked artifact, -copy-filters applies to the files restored
		// from it; otherwise, to the files of the tree. Restoring is decided
		// from this download's tree rather than from Dir, which it has
		// checked holds no chunk data of an earlier run.
		copyCandidates := treeFiles
		if s.chunksClaimed {
			// Restoring does not watch ctx, and can take a while.
			if err := ctx.Err(); err != nil {
				return err
			}
			start = time.Now()
			restored, err := chunkerutil.RestoreFilesTo(d.Dir, d.Dir, func(final string) string {
				// Create the parent here, so that a failed attempt removes
				// it; RestoreFilesTo would otherwise create it untracked. If
				// this fails, so does the restore, with the reason.
				s.mkdirAll(filepath.Dir(final))
				return s.tmpName(final)
			})
			if err != nil {
				return err
			}
			copyCandidates = make([]stagedFile, 0, len(restored))
			for _, r := range restored {
				s.add(r.TmpPath, r.Path)
				copyCandidates = append(copyCandidates, stagedFile{tmp: r.TmpPath, final: r.Path})
			}
			chunkRestoreTime := time.Since(start)
			log.InfoContextf(ctx, "finished restoring %d chunked files, took %s", len(restored), chunkRestoreTime)
			d.DownloadStats.ChunkRestoreTimeMS = chunkRestoreTime.Milliseconds()
		}

		if err := d.applyCopyFilters(ctx, copyPatterns, copyCandidates); err != nil {
			return err
		}
		// The chunk data has served its purpose. Remove it before
		// committing, so that failing to leaves Dir untouched.
		if err := s.deleteChunkData(); err != nil {
			return err
		}
	}

	// The local steps above do not all watch ctx, so a cancelled attempt can
	// get this far. It must not change Dir. Once commit has started, it runs
	// to the end, which is quick.
	if err := ctx.Err(); err != nil {
		return err
	}
	err = s.commit()
	// Noted even if commit failed part way: what it replaced is gone.
	d.noteReplaced(s.replaced)
	if err != nil {
		return err
	}
	committed = true
	return nil
}

// maxReplacedExamples is how many replaced paths noteReplaced names.
const maxReplacedExamples = 3

// noteReplaced records a note, if any files were replaced, with how many and
// a few of their paths relative to Dir.
func (d *DownloadJob) noteReplaced(replaced []string) {
	if len(replaced) == 0 {
		return
	}
	examples := make([]string, 0, maxReplacedExamples)
	for _, p := range replaced[:min(len(replaced), maxReplacedExamples)] {
		if rel, err := filepath.Rel(d.Dir, p); err == nil {
			p = rel
		}
		examples = append(examples, p)
	}
	d.addNote(NoteExistingFilesReplaced, "Replaced %d existing files in %s, such as %s; -disable-overwrite would have failed this run", len(replaced), d.Dir, strings.Join(examples, ", "))
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

	d.addNote(NoteLegacyChunksIndexMoved, "Chunks index file moved from %s to %s.", secondaryIndexFile, primaryIndexFile)

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
