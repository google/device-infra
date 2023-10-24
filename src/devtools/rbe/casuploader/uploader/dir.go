package uploader

import (
	"encoding/json"
	"fmt"
	"io/ioutil"
	"path/filepath"
	"time"

	log "github.com/golang/glog"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/command"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/digest"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/filemetadata"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/uploadinfo"
)

// fileLoader is the common interface to load content of files
type fileLoader interface {
	// LoadFiles loads the files specified in paths
	LoadFiles(paths []string) error
}

// DirUploader is the uploader to upload a directory to CAS.
type DirUploader struct {
	CommonConfig
	dirPath string
	// fileLoader specifies how to load a list of files from the file system.
	fileLoader fileLoader
}

// NewDirUploader creates a new directory uploader to upload a directory to CAS.
func NewDirUploader(config *CommonConfig, dirPath string, fileLoader fileLoader) Uploader {
	return &DirUploader{
		CommonConfig: *config,
		dirPath:      dirPath,
		fileLoader:   fileLoader,
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
	inputSpec := du.inputSpec()
	rootDigest, uploadEntries, _, err := du.client.ComputeMerkleTree(
		du.ctx, du.dirPath, "", "", &inputSpec, filemetadata.NewNoopCache())
	if err != nil {
		return digest.Digest{}, fmt.Errorf("failed to compute merkle tree: %v", err)
	}
	printEntriesStats(uploadEntries, "all entries in the directory")
	du.exportEntriesInfo(uploadEntries)

	// Check with CAS service to find out all files that do not exist remotely, and only load these
	// files from local disk.
	missingEntries, err := du.findMissing(uploadEntries)
	if err != nil {
		return digest.Digest{}, fmt.Errorf("failed to find missing blobs: %v", err)
	}

	if du.fileLoader != nil {
		paths := []string{}
		for _, entry := range missingEntries {
			if entry.IsFile() && entry.Path != "" {
				paths = append(paths, entry.Path)
			}
		}
		if err := du.fileLoader.LoadFiles(paths); err != nil {
			return digest.Digest{}, fmt.Errorf("failed to load missing files: %v", err)
		}
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

type uploadEntryInfo struct {
	Path   string `json:"path"`
	Digest string `json:"digest"`
	Size   int64  `json:"size"`
}

// exportEntriesInfo outputs the information of upload entries to the file path specified in `dumpFileDetails`
func (du *DirUploader) exportEntriesInfo(entries []*uploadinfo.Entry) {
	path := du.dumpFileDetails
	if path == "" {
		return
	}
	var infoList []uploadEntryInfo
	for _, entry := range entries {
		// Skip directories
		if entry.Path == "" {
			continue
		}
		relPath, _ := filepath.Rel(du.dirPath, entry.Path)
		infoList = append(infoList, uploadEntryInfo{
			Path:   relPath,
			Digest: entry.Digest.Hash,
			Size:   entry.Digest.Size,
		})
	}
	outputContent, err := json.MarshalIndent(infoList, "", "  ")
	if err != nil {
		log.Warningf("failed to export upload entries info: %v", err)
		return
	}

	log.Infof("exporting file digests to %s", path)
	err = ioutil.WriteFile(path, outputContent, 0644)
	if err != nil {
		log.Warningf("failed to export upload entries info to %s: %v", path, err)
	}
}
