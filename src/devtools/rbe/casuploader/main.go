// Uploader for optimized upload of a zip archive to RBE CAS.
package main

import (
	"archive/zip"
	"context"
	"encoding/binary"
	"encoding/hex"
	"errors"
	"fmt"
	"os"
	"os/signal"
	"path"
	"path/filepath"
	"syscall"
	"time"

	"flag"
	
	log "github.com/golang/glog"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/client"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/command"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/digest"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/filemetadata"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/uploadinfo"
	"github.com/google/device-infra/src/devtools/rbe/rbeclient"
	"github.com/hanwen/go-fuse/v2/fs"
	"github.com/hanwen/go-fuse/v2/fuse"
	"github.com/hanwen/go-fuse/v2/zipfs"
	"github.com/google/uuid"
)

// Sha256HeaderID is a custom Header ID for the `extra` field in the file header to store the SHA
// checksum. It is defined in build/soong/zip/zip.go
const Sha256HeaderID = 0x4967

// Sha256HeaderSignature is the signature to verify that the extra data block is used to store the
// SHA checksum. It is defined in build/soong/zip/zip.go
const Sha256HeaderSignature = 0x9514

// readSHA256 reads SHA256 checksum from the `extra` field in the file header.
func readSHA256(fhExtra []byte) (string, error) {
	input := fhExtra
	for len(input) >= 4 {
		headerID := binary.LittleEndian.Uint16(input[0:])
		size := binary.LittleEndian.Uint16(input[2:])
		if int(size) > len(input)-4 {
			return "", fmt.Errorf("Error to read extra field. The size %d exceed block size", size)
		}

		if headerID == Sha256HeaderID && size >= 2 {
			signature := binary.LittleEndian.Uint16(input[4:])
			if signature == Sha256HeaderSignature {
				return hex.EncodeToString(input[6 : 4+size]), nil
			}
		}
		input = input[4+size:]
	}
	return "", nil
}

func createCacheFromZip(rootDir string, zipPath string) (filemetadata.Cache, error) {
	archive, err := zip.OpenReader(zipPath)
	if err != nil {
		return nil, fmt.Errorf("failed to open zip archive: %v", err)
	}
	defer archive.Close()

	cache := filemetadata.NewSingleFlightCache()
	cacheCount := 0
	dupCount := 0
	dup := make(map[string]bool)
	for _, f := range archive.File {
		fh := f.FileHeader
		if fh.Mode().IsDir() || len(fh.Extra) == 0 {
			continue
		}
		sha256, err := readSHA256(fh.Extra)
		if err != nil {
			log.Warningf("Failed to read SHA256 of file %s, skip. Error: %v\n", f.Name, err)
			continue
		}
		if sha256 == "" {
			continue
		}

		cacheCount++
		if dup[sha256] == true {
			dupCount++
		}
		dup[sha256] = true

		cache.Update(path.Join(rootDir, f.Name), &filemetadata.Metadata{
			Digest: digest.Digest{
				Hash: sha256,
				Size: int64(fh.UncompressedSize64),
			},
			IsExecutable: fh.Mode()&0111 == 0111,
			MTime:        fh.ModTime(),
		})
	}
	return cache, nil
}

func findMissing(ctx context.Context, client *client.Client, uploadInfos []*uploadinfo.Entry) ([]*uploadinfo.Entry, error) {
	digests := make([]digest.Digest, len(uploadInfos))
	for i, entry := range uploadInfos {
		digests[i] = entry.Digest
	}

	missingBlobs, err := client.MissingBlobs(ctx, digests)
	if err != nil {
		return nil, err
	}

	missingMap := make(map[digest.Digest]bool)
	for _, blob := range missingBlobs {
		missingMap[blob] = true
	}

	missingEntries := []*uploadinfo.Entry{}
	for _, entry := range uploadInfos {
		if missingMap[entry.Digest] == true {
			missingEntries = append(missingEntries, entry)
		}
	}
	printEntriesStats(missingEntries, "missing entries in remote server")
	return missingEntries, nil
}

func upload(ctx context.Context, client *client.Client, uploadInfos []*uploadinfo.Entry) error {
	start := time.Now()
	digests, size, err := client.UploadIfMissing(ctx, uploadInfos...)
	if err == nil {
		log.Infof("Uploaded %d blobs, %d bytes. Elapsed time: %v\n", len(digests), size, time.Since(start))
	}
	return err
}

func mountZip(zipPath string) (string, *fuse.Server, error) {
	target := path.Join(os.TempDir(), "cas_uploader_mount", uuid.New().String())
	os.MkdirAll(target, 0755)
	root, err := zipfs.NewArchiveFileSystem(zipPath)
	if err != nil {
		return "", nil, err
	}

	server, err := fs.Mount(target, root, nil)
	if err != nil {
		return "", nil, err
	}
	log.Infof("Mounted %s to %s\n", zipPath, target)
	return target, server, err
}

func loadFiles(ctx context.Context, root string, entries []*uploadinfo.Entry) error {
	start := time.Now()
	var count int
	var size int64
	for _, entry := range entries {
		if entry.Path != "" {
			entry.Contents, _ = os.ReadFile(entry.Path)
			count++
			size += int64(len(entry.Contents))
		}
		select {
		case <-ctx.Done():
			return fmt.Errorf("failed to load files, context cancelled")
		default:
		}
	}
	log.Infof("Loaded %d files, %d bytes. Time: %v\n", count, size, time.Since(start))
	return nil
}

func printEntriesStats(entries []*uploadinfo.Entry, message string) {
	var size int64
	var numFiles, numBlobs int
	for _, entry := range entries {
		size += entry.Digest.Size
		if entry.IsBlob() {
			numBlobs++
		}
		if entry.IsFile() {
			numFiles++
		}
	}
	log.Infof("Stats of %s. Size: %d bytes, count: %d, files: %d, blobs: %d\n",
		message, size, len(entries), numFiles, numBlobs)
}

func getEntriesToUpload(c *client.Client, path string, is command.InputSpec, cache filemetadata.Cache) (
	rootDigest digest.Digest, uploadEntries []*uploadinfo.Entry) {
	rootDigestSet := make(map[digest.Digest]bool)
	tryCount := 0
	// b/271174764 go-fuse is flaky to list files when heavy read load. Use retry to get the correct
	// root digest and upload entries.
	// See https://github.com/hanwen/go-fuse/issues/391 for the information of the bug inside go-fuse.
	for tryCount < 20 {
		tryCount++
		rootDigest, uploadEntries, _, _ = c.ComputeMerkleTree(path, "", "", &is, cache)
		if rootDigestSet[rootDigest] {
			break
		} else {
			rootDigestSet[rootDigest] = true
		}
	}
	log.Infof("Tried %d times to get correct root digest and upload entries", tryCount)
	return rootDigest, uploadEntries
}

// DoUpload uploads the zip archive to RBE CAS.
func DoUpload(ctx context.Context, client *client.Client, mountPath string, zipPath string, excludeFilters []string) (digest.Digest, error) {

	// Create a cache by reading the zip file header.
	cache, err := createCacheFromZip(mountPath, zipPath)
	if err != nil {
		return digest.Digest{}, fmt.Errorf("failed to create cache: %v", err)
	}

	inputSpec := command.InputSpec{Inputs: []string{"."}}
	for _, ef := range excludeFilters {
		// Append the root directory path to the beginning of regular expression
		ef = fmt.Sprintf("%s/%s", mountPath, ef)
		inputSpec.InputExclusions = append(
			inputSpec.InputExclusions, &command.InputExclusion{Regex: ef})
	}

	rootDigest, uploadEntries := getEntriesToUpload(client, mountPath, inputSpec, cache)
	printEntriesStats(uploadEntries, "all entries in zip archive")

	// Check with CAS service to find out all files that do not exist remotely, and only load these
	// files from the ZIP archive.
	missingEntries, err := findMissing(ctx, client, uploadEntries)
	if err != nil {
		return digest.Digest{}, fmt.Errorf("failed to find missing blobs: %v", err)
	}
	_ = missingEntries

	loadFiles(ctx, filepath.Dir(mountPath), missingEntries)

	err = upload(ctx, client, missingEntries)
	if err != nil {
		return digest.Digest{}, fmt.Errorf("failed to upload blobs: %v", err)
	}

	return rootDigest, nil
}

// multiStringFlag is a slice of strings for parsing command flags into a string list.
type multiStringFlag []string

func (f *multiStringFlag) String() string {
	return fmt.Sprintf("%v", *f)
}

func (f *multiStringFlag) Set(val string) error {
	*f = append(*f, val)
	return nil
}

func (f *multiStringFlag) Get() any {
	return []string(*f)
}

var (
	zipPath        = flag.String("zip-path", "", "zip path")
	casInstance    = flag.String("cas-instance", "", "RBE instance")
	casAddr        = flag.String("cas-addr", "remotebuildexecution.googleapis.com:443", "RBE server addr")
	serviceAccount = flag.String("service-account-json", "", "Path to JSON file with service account credentials to use.")
	useADC         = flag.Bool("use-adc", false, "True to use Application Default Credentials (ADC).")
	dumpDigest     = flag.String("dump-digest", "", "Output the digest to file")
	excludeFilters multiStringFlag
)

func checkFlags() error {
	if *serviceAccount == "" && *useADC == false {
		return errors.New("Either -use-adc must be true or -service-account-json must be specified")
	}
	if *serviceAccount != "" && *useADC == true {
		return errors.New("-use-adc and -service-account-json must not be set together")
	}
	return nil
}

func main() {
	flag.Var(&excludeFilters, "exclude-filters",
		"Regular expression of paths to be excluded from uploading. The regex will implicitly "+
			"append the root directory path to the beginning, so DO NOT use \"^\" in the regex.")
	flag.Set("silent_init", "true")
	flag.Set("logtostderr", "true")
	flag.Set("stderrthreshold", "INFO")
	flag.Parse()

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	if err := checkFlags(); err != nil {
		log.Exit(err)
	}

	start := time.Now()

	// Create a new RBE client.
	client, err := rbeclient.New(ctx, rbeclient.Opts{*casInstance, *casAddr, *serviceAccount, *useADC})
	if err != nil {
		log.Exit(err)
	}
	defer client.Close()

	// Mount the zip archive as a FUSE file system for building the Merkle tree with the full
	// directory structure and extracting only parts of the archive.
	mountPath, fuseServer, err := mountZip(*zipPath)
	if err != nil {
		log.Exitf("Failed to mount %s to %s: %v", *zipPath, mountPath, err)
	}
	defer func() {
		if err := fuseServer.Unmount(); err != nil {
			log.Errorf("Unable to unmount: %v\n", err)
		} else {
			log.Infoln("Successfully unmount Fuse Server")
		}
		if err := os.RemoveAll(mountPath); err != nil {
			log.Errorf("Failed to remove mounted path: %v\n", err)
		} else {
			log.Infof("Successfully remove mounted path: %s\n", mountPath)
		}
	}()

	// Handle interruption and SIGTERM to cancel read operations, so that the FUSE directory can be unmounted safely.
	go func() {
		sigChan := make(chan os.Signal, 1)
		signal.Notify(sigChan, os.Interrupt, syscall.SIGTERM)
		<-sigChan
		log.Infoln("Interrupted, exiting...")
		cancel()
		time.Sleep(3 * time.Second)
		os.Exit(1)
	}()

	rootDigest, err := DoUpload(ctx, client, mountPath, *zipPath, excludeFilters)
	if err != nil {
		log.Exitf("Failed to upload the zip archive to CAS: %v", err)
	}

	output := fmt.Sprintf("%s/%d", rootDigest.Hash, rootDigest.Size)
	os.WriteFile(*dumpDigest, []byte(output), 0644)
	log.Infof("Uploaded %s to RBE instance %s, root digest: %s. E2E time: %v\n", *zipPath, *casInstance, output, time.Since(start))
}
