package uploader

import (
	"context"
	"fmt"
	"os"
	"time"

	log "github.com/golang/glog"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/client"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/command"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/digest"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/filemetadata"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/uploadinfo"
)

// DirUploader is the uploader to upload a directory to CAS.
type DirUploader struct {
	uploaderConfig
	dirPath     string
	digestCache filemetadata.Cache
}

// NewDirUploader creates a new directory uploader to upload a directory to CAS.
func NewDirUploader(ctx context.Context, client *client.Client, dirPath string,
	excludeFilters []string, digestCache filemetadata.Cache) Uploader {
	if digestCache == nil {
		digestCache = filemetadata.NewSingleFlightCache()
	}
	return &DirUploader{
		uploaderConfig: uploaderConfig{ctx: ctx, client: client, excludeFilters: excludeFilters},
		dirPath:        dirPath,
		digestCache:    digestCache,
	}
}

func (du *DirUploader) inputSpec() command.InputSpec {
	inputSpec := command.InputSpec{Inputs: []string{"."}}
	for _, ef := range du.excludeFilters {
		// Append the root directory path to the beginning of regular expression
		ef = fmt.Sprintf("%s/%s", du.dirPath, ef)
		inputSpec.InputExclusions = append(
			inputSpec.InputExclusions, &command.InputExclusion{Regex: ef})
	}
	return inputSpec
}

// DoUpload uploads the given directories to CAS, and returns the digest of the root directory.
func (du *DirUploader) DoUpload() (digest.Digest, error) {
	rootDigest, uploadEntries := du.getEntriesToUpload()
	printEntriesStats(uploadEntries, "all entries in the dir")

	// Check with CAS service to find out all files that do not exist remotely, and only load these
	// files from local disk.
	missingEntries, err := du.findMissing(uploadEntries)
	if err != nil {
		return digest.Digest{}, fmt.Errorf("failed to find missing blobs: %v", err)
	}

	if err := du.loadFiles(missingEntries); err != nil {
		return digest.Digest{}, fmt.Errorf("failed to load missing files: %v", err)
	}

	if err := du.upload(missingEntries); err != nil {
		return digest.Digest{}, fmt.Errorf("failed to upload blobs: %v", err)
	}

	return rootDigest, nil
}

func (du *DirUploader) findMissing(uploadInfos []*uploadinfo.Entry) ([]*uploadinfo.Entry, error) {
	digests := make([]digest.Digest, len(uploadInfos))
	for i, entry := range uploadInfos {
		digests[i] = entry.Digest
	}

	missingBlobs, err := du.client.MissingBlobs(du.ctx, digests)
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

func (du *DirUploader) loadFiles(entries []*uploadinfo.Entry) error {
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
		case <-du.ctx.Done():
			return fmt.Errorf("failed to load files, context cancelled")
		default:
		}
	}
	log.Infof("Loaded %d files, %d bytes. Time: %v\n", count, size, time.Since(start))
	return nil
}

func (du *DirUploader) getEntriesToUpload() (rootDigest digest.Digest, uploadEntries []*uploadinfo.Entry) {
	rootDigestSet := make(map[digest.Digest]bool)
	inputSpec := du.inputSpec()
	tryCount := 0
	// b/271174764 go-fuse is flaky to list files when heavy read load. Use retry to get the correct
	// root digest and upload entries.
	// See https://github.com/hanwen/go-fuse/issues/391 for the information of the bug inside go-fuse.
	for tryCount < 20 {
		tryCount++
		rootDigest, uploadEntries, _, _ = du.client.ComputeMerkleTree(du.dirPath, "", "", &inputSpec, du.digestCache)
		if rootDigestSet[rootDigest] {
			break
		} else {
			rootDigestSet[rootDigest] = true
		}
	}
	log.Infof("Tried %d times to get correct root digest and upload entries", tryCount)
	return rootDigest, uploadEntries
}

func (du *DirUploader) upload(uploadInfos []*uploadinfo.Entry) error {
	start := time.Now()
	digests, size, err := du.client.UploadIfMissing(du.ctx, uploadInfos...)
	if err == nil {
		log.Infof("Uploaded %d blobs, %d bytes. Elapsed time: %v\n", len(digests), size, time.Since(start))
	}
	return err
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
