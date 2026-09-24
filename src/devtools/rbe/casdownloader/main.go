// package main is the downloader to download files and directories from RBE CAS
package main

import (
	"context"
	"errors"
	"fmt"
	"math"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"runtime/debug"
	"strings"
	"syscall"
	"time"

	_ "embed"

	"flag"
	
	log "github.com/golang/glog"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/client"
	repb "github.com/bazelbuild/remote-apis/build/bazel/remote/execution/v2"
	"github.com/google/device-infra/src/devtools/rbe/casdownloader/cache"
	"github.com/google/device-infra/src/devtools/rbe/casdownloader/download"
	"github.com/google/device-infra/src/devtools/rbe/common"
	"github.com/google/device-infra/src/devtools/rbe/common/monitoring"
	"github.com/google/device-infra/src/devtools/rbe/rbeclient"
	"google.golang.org/grpc"
	"google.golang.org/grpc/metadata"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/proto"
)

//go:embed adc_credentials.sh
var adcCredentialsScriptContent []byte

var (
	version = "dev"
)

const (
	// The headers key of our RequestMetadata.
	remoteHeadersKey = "build.bazel.remote.execution.v2.requestmetadata-bin"
	// RBECASConcurrency is the default maximum number of concurrent upload and download operations for RBE clients.
	RBECASConcurrency = 25 // Reduced from the default of 500 to avoid unexpected traffic spikes.
	// DefaultRPCTimeout is the default RPC timeout for per-rpc deadline.
	DefaultRPCTimeout = 60 * time.Second // 60s to match AB (CAS default is 20s)
	// DefaultGetCapabilitiesTimeout is the default RPC timeout for GetCapabilities.
	DefaultGetCapabilitiesTimeout = 5 * time.Second // CAS default is 5s
	// DefaultBatchUpdateBlobsTimeout is the default RPC timeout for BatchUpdateBlobs.
	DefaultBatchUpdateBlobsTimeout = time.Minute // 1m to match current CAS default.
	// DefaultBatchReadBlobsTimeout is the default RPC timeout for BatchReadBlobs.
	DefaultBatchReadBlobsTimeout = time.Minute // 1m to match current CAS default.
	// DefaultGetTreeTimeout is the default RPC timeout for GetTree.
	DefaultGetTreeTimeout = time.Minute // 1m to match current CAS default.
	// DefaultCacheMinFreeSpace is the default amount of free space, in bytes, kept available on the
	// filesystem holding the local cache.
	DefaultCacheMinFreeSpace = 1024 * 1024 * 1024 // 1GB
)

var (
	printVersion = flag.Bool("version", false, "Print version information")

	// Flags for download jobs
	rootDigest    = flag.String("digest", "", `Digest of root directory proto "<digest hash>/<size bytes>".`)
	dir           = flag.String("dir", "", "Directory to download the tree. Files in this directory will be overwritten and will not be restored on errors.")
	dumpJSON      = flag.String("dump-json", "", "Dump download stats to json file.")
	enableStreamz = flag.Bool("enable-streamz", false, "Enable direct Streamz metrics collection via murdockd.")
	murdockAddr   = flag.String("murdock-addr", "", "Address (host:port) of murdockd daemon. If empty, defaults to localhost:2444, or the MURDOCK_ADDR environment variable if set.")

	// Flags for local cache
	disableCache    = flag.Bool("disable-cache", false, "Disable local cache.")
	cacheDir        = flag.String("cache-dir", "", "Cache directory to store downloaded files.")
	cacheMaxSize    = flag.Int64("cache-max-size", 0, "Cache is trimmed if the cache gets larger than this value. If 0, the cache is effectively a leak.")
	enableCacheLock = flag.Bool("cache-lock", false,
		"Enable cache lock. When using local cache (-cache-dir is set) and enable cache lock, the downloader will add lock when it changes cache, so you can safely run multiple downloader instances simultaneously.")
	useHardlink = flag.Bool("use-hardlink", true, "By default local cache will use hardlink when push and pull files.")
	// This defaults to a non-zero value because -cache-max-size defaults to 0 (unbounded), and even
	// when it is set it only bounds the logical size of the cache, so neither bound on its own
	// prevents the volume from filling up.
	cacheMinFreeSpace = flag.Int64("cache-min-free-space", DefaultCacheMinFreeSpace,
		"Cache is trimmed if free space on the filesystem holding the cache drops below this value, in bytes. If 0, free space is not enforced.")
	enableLockFreeCache = flag.Bool("enable-lock-free-cache", false,
		"Use the lock-free local cache instead of the default one. The default cache serializes every downloader on the host behind a single file lock and only trims after files are written; the lock-free cache needs no lock and makes room before the download. Blobs are kept in a separate subdirectory of -cache-dir, so switching starts from a cold cache. -cache-max-size and -cache-lock have no effect when this is set.")
	// Eviction tuning for the lock-free cache. Each defaults to a value derived
	// from -cache-min-free-space or to the shared evictor's default, and each is
	// ignored unless -enable-lock-free-cache is set.
	//
	// TODO: plumb these through CasOptions so Tradefed hosts can be
	// tuned per cluster. They are individual scalars rather than a config file
	// precisely so that each maps onto one @Option.
	cacheTargetFreeSpace = flag.Int64("cache-target-free-space", 0,
		"Free space, in bytes, at which eviction stops. Must exceed -cache-min-free-space. If 0, defaults to twice -cache-min-free-space, with a 1GiB floor on the gap between them.")
	cacheSampleBuckets = flag.Int("cache-eviction-sample-buckets", 0,
		"Number of random leaf directories sampled to estimate the eviction cutoff age (safe range: 16-64). If 0, the shared default is used. Leave unset unless optimizing.")
	cacheBatchSize = flag.Int("cache-eviction-batch-size", 0,
		"Number of file unlinks per batch before yielding (safe range: 200-2000). If 0, the shared default is used. Leave unset unless optimizing.")
	cacheBatchDelay = flag.Duration("cache-eviction-batch-delay", 0,
		"Cooperative pause between unlink batches (safe range: 500us-2ms). If 0, the shared default is used. Leave unset unless optimizing.")
	cacheLazyTouchInterval = flag.Duration("cache-lazy-touch-interval", 0,
		"Minimum age before a cache hit refreshes a blob's mtime. If 0, the shared default is used. Larger values mean fewer writes and coarser recency.")
	cacheMinBlobAge = flag.Duration("cache-min-blob-age", 0,
		"Grace period during which a newly written blob is exempt from eviction. If 0, the shared default is used.")

	// Flags for RBE CAS configurations
	casInstance    = flag.String("cas-instance", "", "RBE instance")
	casAddr        = flag.String("cas-addr", "remotebuildexecution.googleapis.com:443", "RBE server addr")
	casProxyAddr   = flag.String("cas-proxy-addr", "", "Local proxy address of the remote execution CAS server. If specified, dialing will use plaintext (insecure) gRPC bypass.")
	serviceAccount = flag.String("service-account-json", "", "Path to JSON file with service account credentials to use.")
	useADC         = flag.Bool("use-adc", false, "True to use Application Default Credentials (ADC).")

	// Flags for metadata
	invocationID = flag.String("invocation-id", "", "comma-separated list of key-value pairs, like 'bid=<build-id>,branch=<branch>,flavor=<flavor>'.")

	// Flags for chunked version of artifacts.
	keepChunks = flag.Bool("keep-chunks", false, "Keep chunk files and the index file around for chunked version of artifacts.")
	chunksOnly = flag.Bool("chunks-only", false, "Only download chunk files and the index file (skip file restoration) for chunked version of artifacts.")

	// Flags for concurrency (affects peak memory), specify 0 for default.
	casConcurrency = flag.Int("cas-concurrency", RBECASConcurrency, "the maximum number of concurrent download operations.")

	// Report memory status and set memory limit.
	memoryLimit = flag.Int64("memory-limit", 0, "Memory limit in MiB.")
	// Flag for minimum download speed.
	minDownloadMbps = flag.Int64("min-download-mbps", 0, "Minimum download speed in megabytes per second. If set, an effective timeout will be calculated and applied to the download.")
	// Flag for fixed download timeout.
	downloadTimeout = flag.Duration("download-timeout", 0, "Fixed timeout for the entire download process. If set, the download will fail if it takes longer than this duration, but stats will still be dumped.")

	// Flag for the RPC timeout for per-rpc deadline. Specify 0 to use default value.
	rpcTimeout              = flag.Duration("rpc-timeout", DefaultRPCTimeout, "Default RPC timeout as duration, like 20s, 1m etc.")
	getCapabilitesTimeout   = flag.Duration("get-capabilities-timeout", DefaultGetCapabilitiesTimeout, "RPC timeout for GetCapabilities, like 20s, 1m etc.")
	batchUpdateBlobsTimeout = flag.Duration("batch-update-blobs-timeout", DefaultBatchUpdateBlobsTimeout, "RPC timeout for BatchUpdateBlobs, like 2m, 5m etc.")
	batchReadBlobsTimeout   = flag.Duration("batch-read-blobs-timeout", DefaultBatchReadBlobsTimeout, "RPC timeout for BatchReadBlobs, like 2m, 5m etc.")
	getTreeTimeout          = flag.Duration("get-tree-timeout", DefaultGetTreeTimeout, "RPC timeout for GetTree, like 2m, 5m etc.")

	excludeFilters common.MultiStringFlag
	includeFilters common.MultiStringFlag
)

func fileInfo(path string) (os.FileInfo, error) {
	info, err := os.Stat(path)
	if os.IsNotExist(err) {
		mkdirErr := os.MkdirAll(path, 0755)
		if mkdirErr != nil {
			return nil, fmt.Errorf("failed to create directory %s: %v", path, mkdirErr)
		}
		info, err = os.Stat(path)
	}
	if err != nil {
		return nil, fmt.Errorf("failed to stat directory %s: %v", path, err)
	}
	return info, nil
}

func isSameFilesystem(path1, path2 string) (bool, error) {
	info1, err := fileInfo(path1)
	if err != nil {
		return false, err
	}
	info2, err := fileInfo(path2)
	if err != nil {
		return false, err
	}
	return info1.Sys().(*syscall.Stat_t).Dev == info2.Sys().(*syscall.Stat_t).Dev, nil
}

func checkFlags() error {
	if *disableCache == false && *cacheDir == "" {
		return errors.New("-cache-dir must be specified")
	}
	if *rootDigest == "" {
		return errors.New("-digest must be specified")
	}
	if *dir == "" {
		return errors.New("-dir must be specified")
	}
	if *casInstance == "" {
		return errors.New("-cas-instance must be specified")
	}

	authMethodsCount := 0
	if *useADC {
		authMethodsCount++
	}
	if *serviceAccount != "" {
		authMethodsCount++
	}

	if authMethodsCount != 1 {
		authErrStr := "exactly one of -use-adc or -service-account-json must be specified"
		return errors.New(authErrStr)
	}

	if isSameFilesystem, err := isSameFilesystem(*cacheDir, *dir); err == nil {
		if *useHardlink && !isSameFilesystem {
			log.Warningf("Hardlink will not be used as cache dir %s and download dir %s are not in the same filesystem.", *cacheDir, *dir)
			*useHardlink = false
		}
	}
	if *chunksOnly == true && *keepChunks == false {
		log.Warningf("-chunks-only implies -keep-chunks.")
		*keepChunks = true
	}
	if !*disableCache {
		warnAboutIgnoredCacheFlags(explicitFlags(), *enableLockFreeCache)
		if *enableLockFreeCache {
			warnAboutLeftoverStandardCache()
		}
	}
	return nil
}

// explicitFlags reports which flags the caller actually passed, as opposed to
// which ones merely have a value.
//
// The difference matters twice over: a flag left at its default must not be
// reported as an operator's choice, and a flag deliberately set to its default
// must not be treated as absent when it decides precedence against the
// configuration file.
func explicitFlags() map[string]bool {
	set := make(map[string]bool)
	flag.Visit(func(f *flag.Flag) {
		set[f.Name] = true
	})
	return set
}

// lockFreeOnlyFlags tune the lock-free cache's evictor and do nothing else.
var lockFreeOnlyFlags = []string{
	"cache-target-free-space",
	"cache-eviction-sample-buckets",
	"cache-eviction-batch-size",
	"cache-eviction-batch-delay",
	"cache-lazy-touch-interval",
	"cache-min-blob-age",
}

// standardOnlyFlags are honored by the default cache and by nothing else.
var standardOnlyFlags = []string{
	"cache-max-size",
	"cache-lock",
}

// warnAboutIgnoredCacheFlags reports, once per run, which of the caller's cache
// flags the selected implementation will not read.
//
// Silence is the failure mode worth guarding against here: a flag that stops
// taking effect looks exactly like a flag that worked. These arguments are
// baked into launcher scripts, Borg configs and CasOptions defaults that nobody
// re-reads, so whichever way -enable-lock-free-cache is set, the operator
// deserves to be told which of their arguments just became inert.
//
// One warning, not one per flag: the useful unit is "here is what this run
// ignored", and a list stays readable where six consecutive lines would not.
//
// TODO: gate CasFileDownloader's -cache-max-size and -cache-lock on
// the same CasOptions setting that enables the lock-free cache. Both are
// hardcoded into the argument list it builds, so until then this warning would
// fire on every Tradefed invocation, naming two flags the operator reading the
// log cannot remove. That change is a no-op while the option is off, so it can
// ship on any lab release; once it has, enabling the cache flips both sides at
// once.
func warnAboutIgnoredCacheFlags(explicit map[string]bool, lockFree bool) {
	candidates, advice := standardOnlyFlags, "The lock-free cache bounds free space on the volume rather than the logical size of the cache, and needs no lock to run concurrently. Use -cache-min-free-space instead of -cache-max-size."
	if !lockFree {
		candidates, advice = lockFreeOnlyFlags, "These tune the lock-free cache's evictor. Set -enable-lock-free-cache to use them."
	}

	var ignored []string
	for _, name := range candidates {
		if explicit[name] {
			ignored = append(ignored, "-"+name)
		}
	}
	if len(ignored) == 0 {
		return
	}
	log.Warningf("%s %s is ignored with -enable-lock-free-cache=%t. %s",
		strings.Join(ignored, ", "), pluralVerb(len(ignored)), lockFree, advice)
}

// warnAboutLeftoverStandardCache points at the standard cache's files, if any
// remain, once the lock-free cache has taken over.
//
// They are left in place rather than deleted so that turning
// -enable-lock-free-cache back off resumes from a warm cache. That costs disk
// on hosts already short of it, which is the whole reason the lock-free cache
// exists, so the operator is told where the space went and that it is theirs
// to reclaim.
func warnAboutLeftoverStandardCache() {
	if _, err := os.Stat(filepath.Join(*cacheDir, "state.json")); err != nil {
		return
	}
	log.Warningf("A cache from the default implementation remains in %s and is no longer read or trimmed. It is kept so that turning -enable-lock-free-cache back off resumes from a warm cache; delete its contents (not the directory) once the rollout has settled.", *cacheDir)
}

func pluralVerb(n int) string {
	if n == 1 {
		return "is"
	}
	return "are"
}

// ContextWithMetadata attaches metadata to the passed-in context, returning a new context. It uses
// the already created context to generate a new one containing the metadata header.
func ContextWithMetadata(ctx context.Context) (context.Context, error) {
	meta := &repb.RequestMetadata{
		ToolDetails: &repb.ToolDetails{
			ToolName:    "casdownloader",
			ToolVersion: version,
		},
	}
	meta.ToolInvocationId = *invocationID

	// Marshal the proto to a binary buffer
	buf, err := proto.Marshal(meta)
	if err != nil {
		return nil, err
	}

	// metadata package converts the binary buffer to a base64 string, so no need to encode before
	// sending.
	mdPair := metadata.Pairs(remoteHeadersKey, string(buf))
	return metadata.NewOutgoingContext(ctx, mdPair), nil
}

var envelopeCandidatePaths = []string{
	"/usr/envelope/start_envelope",
	"/google/data/ro/teams/envelope/start_envelope",
}

// envelopeExists checks if the envelope executable physically exists and is executable.
// This is used to determine if envelope should be enabled by default.
func envelopeExists() bool {
	return envelopeExistsAt(envelopeCandidatePaths)
}

func envelopeExistsAt(paths []string) bool {
	for _, path := range paths {
		if info, err := os.Stat(path); err == nil {
			if info.Mode().IsRegular() && (info.Mode().Perm()&0111 != 0) {
				return true
			}
		}
	}
	return false
}

func main() {
	os.Exit(runMain())
}

func runMain() int {
	flag.Var(&excludeFilters, "exclude-filters", "Regular expression of paths to be excluded from uploading.")
	flag.Var(&includeFilters, "include-filters", "Regular expression of paths to be excluded from uploading.")

	flag.Set("silent_init", "true")
	flag.Set("logtostderr", "true")
	flag.Set("stderrthreshold", "INFO")
	flag.Set("logtostderr", "true")
	// Disable envelope and svelte by default when the envelope binary is not available
	// on the host to avoid startup crashes during `flag.Parse()`.
	if !envelopeExists() {
		if f := flag.Lookup("envelope_enabled"); f != nil {
			f.Value.Set("false")
		}
		if f := flag.Lookup("disable_svelte"); f != nil {
			f.Value.Set("true")
		}
	}
	flag.Parse()

	if *printVersion == true {
		fmt.Printf("version: %s\n", version)
		return 0
	}

	// Initialize unified metrics collection.
	if *enableStreamz {
		mAddr := *murdockAddr
		if mAddr == "" {
			mAddr = os.Getenv("MURDOCK_ADDR")
		}
		var opts []monitoring.Option
		if mAddr != "" {
			opts = append(opts, monitoring.WithMurdockAddr(mAddr))
		}
		monitoring.Init("casdownloader", opts...)
		defer monitoring.Shutdown()
	}

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	if ctxMd, err := ContextWithMetadata(ctx); err != nil {
		log.InfoContextf(ctx, "Failed to add metadata to context: %v", err)
	} else {
		ctx = ctxMd
	}

	var success bool
	var exitCode int
	defer func() {
		if *enableStreamz {
			monitoring.RecordUsage(success, exitCode)
		}
	}()

	if err := run(ctx); err != nil {
		log.ErrorContextf(ctx, "casdownloader execution failed: %v", err)
		exitCode = 1
		return 1
	}

	success = true
	return 0
}

func run(ctx context.Context) error {
	if err := checkFlags(); err != nil {
		return err
	}

	if *casProxyAddr != "" {
		ctx = metadata.AppendToOutgoingContext(ctx, "x-cas-upstream-address", *casAddr)
		ctx = metadata.AppendToOutgoingContext(ctx, "x-cas-upstream-instance", *casInstance)
	}

	setMemoryLimit(*memoryLimit)

	rpcTimeouts := map[string]time.Duration{
		"default":          *rpcTimeout,
		"GetCapabilities":  *getCapabilitesTimeout,
		"BatchUpdateBlobs": *batchUpdateBlobsTimeout,
		"BatchReadBlobs":   *batchReadBlobsTimeout,
		"GetTree":          *getTreeTimeout,
	}

	if *casConcurrency != RBECASConcurrency {
		log.InfoContextf(ctx, "casConcurrency: %v\n", *casConcurrency)
	}

	clientOpts := rbeclient.Opts{
		Instance:              *casInstance,
		ServiceAddress:        *casAddr,
		ProxyAddress:          *casProxyAddr,
		ServiceAccountJSON:    *serviceAccount,
		UseApplicationDefault: *useADC,
		CASConcurrency:        *casConcurrency,
		RPCTimeouts:           rpcTimeouts,
	}

	tracker := download.NewProxyHitTracker()
	clientOpts.DialOpts = append(clientOpts.DialOpts,
		grpc.WithChainStreamInterceptor(tracker.StreamInterceptor()),
		grpc.WithChainUnaryInterceptor(tracker.UnaryInterceptor()),
	)

	useProxy := *casProxyAddr != ""
	proxyStatus := ""
	var rbeClient *client.Client
	var err error

	// 1. Try to connect to CAS proxy first if configured
	if useProxy {
		log.InfoContextf(ctx, "Attempting to connect to CAS proxy at %s...", *casProxyAddr)

		rbeClient, err = rbeclient.New(ctx, clientOpts)

		if err != nil {
			log.WarningContextf(ctx, "Failed to connect to CAS proxy at %s: %v. Falling back to direct RBE connection...", *casProxyAddr, err)
			if rbeClient != nil {
				rbeClient.Close()
				rbeClient = nil
			}
			useProxy = false // Disable proxy usage for the rest of the run
			proxyStatus = fmt.Sprintf("unavailable: %v", err)
		}
	}

	// 2. Direct connection (Fallback / Default path if proxy was not set or failed to connect)
	if !useProxy {
		clientOpts.ProxyAddress = "" // Force direct RBE connection
		rbeClient, err = rbeclient.New(ctx, clientOpts)
		if err != nil {
			if strings.Contains(err.Error(), "rpc error: code = PermissionDenied") && *useADC == true {
				logAdcCredentials()
			}
			return err
		}
	}

	defer func() {
		if rbeClient != nil {
			rbeClient.Close()
		}
	}()

	var jobNotes []string
	localCache, cacheNote := cache.OpenOrDegrade(ctx, cacheFlagValues(), "the download")
	if cacheNote != "" {
		jobNotes = append(jobNotes, cacheNote)
	}

	d := download.DownloadJob{
		Client:          rbeClient,
		Digest:          *rootDigest,
		Dir:             *dir,
		DumpJSON:        *dumpJSON,
		Cache:           localCache,
		CASProxyStatus:  proxyStatus,
		IncludeFilters:  includeFilters,
		ExcludeFilters:  excludeFilters,
		KeepChunks:      *keepChunks,
		ChunksOnly:      *chunksOnly,
		MinDownloadMbps: *minDownloadMbps,
		DownloadTimeout: *downloadTimeout,
		UseProxy:        useProxy,
		Tracker:         tracker,
		Notes:           jobNotes,
	}
	reportMemoryStats()

	start := time.Now()
	err = d.DoDownload(ctx)

	// 3. Download-time fallback: If download fails and we were actively using the proxy
	//
	// Only a failure talking to casproxy warrants it. A local failure -- disk
	// full, a bad chunk, a timeout while restoring -- would recur against CAS
	// remote too, and by then Push may already have put casproxy's bytes into
	// the local cache, where the retry would book them as local hits. Leaving
	// it alone reports the real error and keeps the proxy tiers accurate.
	if err != nil && useProxy && !d.RemoteFailed() {
		log.WarningContextf(ctx, "Download failed locally, not in CAS proxy; not falling back to direct RBE CAS: %v", err)
	} else if err != nil && useProxy {
		proxyErr := err
		log.WarningContextf(ctx, "Download failed mid-run using CAS proxy: %v. Falling back to direct RBE CAS connection...", proxyErr)

		// Close dead proxy client
		rbeClient.Close()
		rbeClient = nil

		// NOTE: When falling back from proxy to direct RBE, DoDownload applies a new per-attempt
		// timeout. Under worst-case proxy failure near the deadline, total elapsed time may reach
		// up to ~2x DownloadTimeout. We preserve this behavior for simplicity during fallback.

		// Re-initialize direct RBE client (ignoring proxy)
		clientOpts.ProxyAddress = ""
		rbeClient, err = rbeclient.New(ctx, clientOpts)
		if err != nil {
			return fmt.Errorf("direct RBE fallback client initialization failed: %w", err)
		}

		// Ensure the old cache is closed to release any file locks before re-creating.
		if d.Cache != nil {
			_ = d.Cache.Close()
		}

		// Re-initialize cache since the previous attempt closed it.
		localCache, cacheNote := cache.OpenOrDegrade(ctx, cacheFlagValues(), "the direct RBE retry")
		if cacheNote != "" {
			d.Notes = append(d.Notes, cacheNote)
		}

		// The retry talks to CAS remote, so neither the hits recorded against
		// casproxy nor the proxied attribution apply to it any more.
		tracker.Reset()
		// Reassign client, cache, and updated proxy status to download job
		d.Client = rbeClient
		d.Cache = localCache
		d.UseProxy = false
		d.CASProxyStatus = fmt.Sprintf("fallback: %v", proxyErr)

		err = d.DoDownload(ctx)
	}
	duration := time.Since(start)

	downloadSuccess := (err == nil)
	rbeStatusStr := "OK"
	if err != nil {
		rbeStatusStr = status.Code(err).String()
	}

	if *enableStreamz {
		recordDownloadMetrics(downloadSuccess, rbeStatusStr, duration, &d, err)
	}

	reportMemoryStats()
	return err
}

func logAdcCredentials() {
	tmpDir, err := os.MkdirTemp("", "mybashscript")
	if err != nil {
		log.Errorf("Failed to create temp dir: %v", err)
	}
	defer os.RemoveAll(tmpDir)

	scriptPath := filepath.Join(tmpDir, "adc_credentials.sh")

	if err := os.WriteFile(scriptPath, adcCredentialsScriptContent, 0700); err != nil {
		log.Errorf("Failed to write script to temp file: %v", err)
		return
	}

	log.Warningf("Attempting to log ADC credentials due to PermissionDenied...")
	cmd := exec.Command(scriptPath)
	output, err := cmd.CombinedOutput()
	if err != nil {
		log.Errorf("Failed to run adc_credentials.sh: %v", err)
	}
	log.Infof("adc_credentials.sh output: %s", output)
}

// cacheFlagValues snapshots the flags that configure the cache.
func cacheFlagValues() cache.Options {
	return cache.Options{
		Disabled:     *disableCache,
		LockFree:     *enableLockFreeCache,
		Dir:          *cacheDir,
		MaxSize:      *cacheMaxSize,
		MinFreeSpace: *cacheMinFreeSpace,
		Lock:         *enableCacheLock,
		UseHardlink:  *useHardlink,
		Overrides:    evictorOverrides(explicitFlags()),
	}
}

// evictorOverrides collects the eviction tuning flags the caller actually
// passed.
//
// Only flags present in explicit are forwarded. A flag left alone must not be
// forwarded as a zero, because zero is a legal value for several of these
// settings and would be indistinguishable from "use the default".
func evictorOverrides(explicit map[string]bool) cache.EvictorOverrides {
	var o cache.EvictorOverrides
	if explicit["cache-target-free-space"] {
		o.TargetFreeSpaceBytes = cacheTargetFreeSpace
	}
	if explicit["cache-eviction-sample-buckets"] {
		o.SampleBuckets = cacheSampleBuckets
	}
	if explicit["cache-eviction-batch-size"] {
		o.BatchSize = cacheBatchSize
	}
	if explicit["cache-eviction-batch-delay"] {
		o.BatchDelay = cacheBatchDelay
	}
	if explicit["cache-lazy-touch-interval"] {
		o.LazyTouchInterval = cacheLazyTouchInterval
	}
	if explicit["cache-min-blob-age"] {
		o.MinBlobAge = cacheMinBlobAge
	}
	return o
}

func setMemoryLimit(limit int64) {
	var limitInBytes int64 = math.MaxInt64
	if limit > 0 {
		limitInBytes = 1024 * 1024 * limit
	}
	prevLimit := debug.SetMemoryLimit(limitInBytes)

	log.Infof("Memory limit set: %v (was %v)\n", limitInBytes, prevLimit)
}

func reportMemoryStats() {
	var memStats runtime.MemStats
	runtime.ReadMemStats(&memStats)
	log.Infof("Total memory allocated: %v, heap allocation: %v\n", memStats.TotalAlloc, memStats.HeapAlloc)
}

func parseInvocationID(invocationID string) (string, string, string, string) {
	caller := "unknown"
	bid := "unknown"
	branch := "unknown"
	flavor := "unknown"

	if invocationID == "" {
		return caller, bid, branch, flavor
	}
	parts := strings.Split(invocationID, ",")
	for _, part := range parts {
		kv := strings.SplitN(part, "=", 2)
		if len(kv) != 2 {
			continue
		}
		switch kv[0] {
		case "caller":
			caller = kv[1]
		case "bid":
			bid = kv[1]
		case "branch":
			branch = kv[1]
		case "flavor":
			flavor = kv[1]
		}
	}
	return caller, bid, branch, flavor
}

func recordDownloadMetrics(success bool, rbeStatus string, duration time.Duration, d *download.DownloadJob, err error) {
	// Record download latency.
	monitoring.RecordLatency(success, rbeStatus, duration)

	// Record the payload that had to come from CAS remote.
	if success {
		monitoring.RecordBytes(true, d.WANSize())
	} else {
		monitoring.RecordBytes(false, 0)
	}

	// Record detailed download stats.
	stats := d.Stats()
	if stats == nil {
		stats = &download.Stats{
			DownloadError: "unknown error (stats nil)",
		}
		if err != nil {
			stats.DownloadError = err.Error()
		}
	}
	caller, bid, branch, flavor := parseInvocationID(*invocationID)
	mStats := &monitoring.DownloadStats{
		SizeHot:            stats.SizeHot,
		SizeDedup:          stats.SizeDedup,
		SizeProxyHot:       stats.SizeProxyHot,
		SizeProxyCold:      stats.SizeProxyCold,
		SizeCold:           stats.SizeCold,
		CountHot:           stats.CountHot,
		CountDedup:         stats.CountDedup,
		CountProxyHot:      stats.CountProxyHot,
		CountProxyCold:     stats.CountProxyCold,
		CountCold:          stats.CountCold,
		E2ETimeMS:          stats.E2ETimeMS,
		DirRetrieveTimeMS:  stats.DirRetrieveTimeMS,
		DirPrepareTimeMS:   stats.DirPrepareTimeMS,
		FileDownloadTimeMS: stats.FileDownloadTimeMS,
		ChunkRestoreTimeMS: stats.ChunkRestoreTimeMS,
		DownloadError:      stats.DownloadError,
		Caller:             caller,
		Version:            version,
		BuildID:            bid,
		Branch:             branch,
		Flavor:             flavor,
	}
	// Whether the run had a cache, not whether one was asked for. These differ
	// whenever setup failed and the job degraded to downloading without one,
	// and the flag would then label a cacheless run as cached -- turning a
	// handful of hosts with a broken cache directory into an apparently poor
	// hit rate for the cache itself, which is the number this metric exists to
	// measure.
	monitoring.RecordDownloadStats(mStats, *casInstance, d.Cache != nil, *chunksOnly)
}
