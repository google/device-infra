// package main is the downloader to download files and directories from RBE CAS
package main

import (
	"context"
	"errors"
	"fmt"
	"math"
	"os"
	"runtime"
	"runtime/debug"
	"syscall"
	"time"

	"flag"
	
	log "github.com/golang/glog"
	repb "github.com/bazelbuild/remote-apis/build/bazel/remote/execution/v2"
	"github.com/google/device-infra/src/devtools/rbe/casdownloader/cache"
	"github.com/google/device-infra/src/devtools/rbe/casdownloader/download"
	"github.com/google/device-infra/src/devtools/rbe/common"
	"github.com/google/device-infra/src/devtools/rbe/rbeclient"
	"google.golang.org/grpc/metadata"
	"google.golang.org/protobuf/proto"
)

const (
	version = "1.22"
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
)

var (
	printVersion = flag.Bool("version", false, "Print version information")

	// Flags for download jobs
	rootDigest = flag.String("digest", "", `Digest of root directory proto "<digest hash>/<size bytes>".`)
	dir        = flag.String("dir", "", "Directory to download the tree. Files in this directory will be overwritten and will not be restored on errors.")
	dumpJSON   = flag.String("dump-json", "", "Dump download stats to json file.")

	// Flags for local cache
	disableCache    = flag.Bool("disable-cache", false, "Disable local cache.")
	cacheDir        = flag.String("cache-dir", "", "Cache directory to store downloaded files.")
	cacheMaxSize    = flag.Int64("cache-max-size", 0, "Cache is trimmed if the cache gets larger than this value. If 0, the cache is effectively a leak.")
	enableCacheLock = flag.Bool("cache-lock", false,
		"Enable cache lock. When using local cache (-cache-dir is set) and enable cache lock, the downloader will add lock when it changes cache, so you can safely run multiple downloader instances simultaneously.")
	useHardlink = flag.Bool("use-hardlink", true, "By default local cache will use hardlink when push and pull files.")

	// Flags for RBE CAS configurations
	casInstance    = flag.String("cas-instance", "", "RBE instance")
	casAddr        = flag.String("cas-addr", "remotebuildexecution.googleapis.com:443", "RBE server addr")
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
	if *serviceAccount == "" && *useADC == false {
		return errors.New("Either -use-adc must be true or -service-account-json must be specified")
	}
	if *serviceAccount != "" && *useADC == true {
		return errors.New("-use-adc and -service-account-json must not be set together")
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
	return nil
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

func main() {
	flag.Var(&excludeFilters, "exclude-filters", "Regular expression of paths to be excluded from uploading.")
	flag.Var(&includeFilters, "include-filters", "Regular expression of paths to be excluded from uploading.")

	flag.Set("silent_init", "true")
	flag.Set("logtostderr", "true")
	flag.Set("stderrthreshold", "INFO")
	flag.Set("logtostderr", "true")
	flag.Parse()

	if *printVersion == true {
		fmt.Printf("version: %s\n", version)
		os.Exit(0)
	}

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	if ctxMd, err := ContextWithMetadata(ctx); err != nil {
		log.Infof("Failed to add metadata to context: %v", err)
	} else {
		ctx = ctxMd
	}

	if err := checkFlags(); err != nil {
		log.Exit(err)
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
		log.Infof("casConcurrency: %v\n", *casConcurrency)
	}
	client, err := rbeclient.New(ctx, rbeclient.Opts{Instance: *casInstance, ServiceAddress: *casAddr, ServiceAccountJSON: *serviceAccount, UseApplicationDefault: *useADC, CASConcurrency: *casConcurrency, RPCTimeouts: rpcTimeouts})
	if err != nil {
		log.Exit(err)
	}
	defer client.Close()

	cache, err := createCache(*disableCache, *cacheDir, *cacheMaxSize, *enableCacheLock, *useHardlink)
	if err != nil {
		log.Exit(err)
	}

	d := download.DownloadJob{
		Client:         client,
		Digest:         *rootDigest,
		Dir:            *dir,
		DumpJSON:       *dumpJSON,
		Cache:          cache,
		IncludeFilters: includeFilters,
		ExcludeFilters: excludeFilters,
		KeepChunks:     *keepChunks,
		ChunksOnly:     *chunksOnly,
	}
	reportMemoryStats()
	if err = d.DoDownload(ctx); err != nil {
		log.Exit(err)
	}
	reportMemoryStats()
}

func createCache(disableCache bool, cacheDir string, cacheMaxSize int64, enableCacheLock bool, useHardlink bool) (cache.Cache, error) {
	if disableCache {
		return nil, nil
	}
	var localCache cache.Cache
	var err error
	localCache, err = cache.NewLocalCache(cacheDir, cacheMaxSize, enableCacheLock, useHardlink)
	if err != nil {
		return nil, fmt.Errorf("failed to create local cache: %v", err)
	}
	return localCache, err
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
