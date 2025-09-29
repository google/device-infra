package uploader

import (
	"encoding/json"
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"strings"
	"time"

	log "github.com/golang/glog"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/command"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/digest"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/filemetadata"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/uploadinfo"
	repb "github.com/bazelbuild/remote-apis/build/bazel/remote/execution/v2"
	"github.com/google/device-infra/src/devtools/rbe/casuploader/chunkerutil"
	"github.com/google/device-infra/src/devtools/rbe/casuploader/metrics"
	"google.golang.org/protobuf/proto"
)

const (
	allEntriesMessage     = "all entries in the directory"
	missingEntriesMessage = "missing entries in remote server"
)

// fileLoader is the common interface to load content of files.
type fileLoader interface {
	// LoadFiles loads the files specified in paths.
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
		// Append the root directory path to the beginning of regular expression.
		ef = fmt.Sprintf("%s/%s", du.dirPath, ef)
		inputSpec.InputExclusions = append(
			inputSpec.InputExclusions, &command.InputExclusion{Regex: ef})
	}
	return inputSpec
}

// DoUpload uploads the given directories to CAS and returns the digest of the root directory.
func (du *DirUploader) DoUpload() (digest.Digest, error) {
	if du.CommonConfig.chunk {
		// If a chunks index file is not found, it implies this is a source directory
		// that needs to be chunked first.
		if _, err := chunkerutil.FindChunksIndex(du.dirPath); err != nil {
			return du.chunkAndUpload()
		}
	}

	inputSpec := du.inputSpec()
	rootDigest, uploadEntries, _, err := du.client.ComputeMerkleTree(
		du.ctx, du.dirPath, "", "", &inputSpec, filemetadata.NewNoopCache())
	if err != nil {
		return digest.Digest{}, fmt.Errorf("failed to compute merkle tree: %w", err)
	}
	printEntriesStats(uploadEntries, allEntriesMessage, du.CommonConfig.metrics)
	if err := du.exportUploadFilesDetails(rootDigest, uploadEntries); err != nil {
		log.Warningf("failed to export upload files info: %v", err)
	}

	// Check with CAS to find files that do not exist remotely and need to be uploaded.
	missingEntries, err := du.findMissing(uploadEntries)
	if err != nil {
		return digest.Digest{}, fmt.Errorf("failed to find missing blobs: %w", err)
	}

	if du.fileLoader != nil {
		paths := make([]string, 0, len(missingEntries))
		for _, entry := range missingEntries {
			if entry.IsFile() && entry.Path != "" {
				paths = append(paths, entry.Path)
			}
		}
		if err := du.fileLoader.LoadFiles(paths); err != nil {
			return digest.Digest{}, fmt.Errorf("failed to load missing files: %w", err)
		}
	}

	if err := du.upload(missingEntries); err != nil {
		return digest.Digest{}, fmt.Errorf("failed to upload blobs: %w", err)
	}

	return rootDigest, nil
}

func (du *DirUploader) chunkAndUpload() (digest.Digest, error) {
	targetDir, err := os.MkdirTemp("", "casuploader-chunks-*")
	if err != nil {
		return digest.Digest{}, fmt.Errorf("failed to create temp dir: %w", err)
	}
	defer func() {
		if err := os.RemoveAll(targetDir); err != nil {
			log.Errorf("Failed to remove tmp dir %q: %v", targetDir, err)
		}
	}()

	chunksDir := filepath.Join(targetDir, chunkerutil.ChunksDirName)
	if err := os.MkdirAll(chunksDir, 0755); err != nil {
		return digest.Digest{}, fmt.Errorf("failed to create chunks dir: %w", err)
	}

	// Compile the list of files to chunk and upload.
	var paths []string
	err = fs.WalkDir(os.DirFS(du.dirPath), ".", func(path string, d fs.DirEntry, err error) error {
		if err != nil {
			return err
		}
		// TODO: apply excludeFilter
		if !d.IsDir() {
			filePath := filepath.Join(du.dirPath, path)
			paths = append(paths, filePath)
		}
		return nil
	})
	if err != nil {
		return digest.Digest{}, fmt.Errorf("error walking the directory %q: %w", du.dirPath, err)
	}

	chunksIndexEntries, err := du.chunkFiles(chunksDir, paths)
	if err != nil {
		return digest.Digest{}, err
	}

	if err := chunkerutil.CreateIndexFile(targetDir, chunksIndexEntries); err != nil {
		return digest.Digest{}, err
	}

	newDu := NewDirUploader(&du.CommonConfig, targetDir, nil)
	rootDigest, err := newDu.DoUpload()
	if err != nil {
		return rootDigest, fmt.Errorf("failed to upload chunked directory %q for source %q: %w", targetDir, du.dirPath, err)
	}
	return rootDigest, nil
}

func (du *DirUploader) chunkFiles(chunksDir string, paths []string) ([]chunkerutil.ChunksIndex, error) {
	start := time.Now()
	chunksIndexEntries := make([]chunkerutil.ChunksIndex, 0, len(paths))
	for _, path := range paths {
		relPath, err := filepath.Rel(du.dirPath, path)
		if err != nil {
			return nil, fmt.Errorf("failed to get relative path of file %q: %w", path, err)
		}
		chunksIndex, err := chunkerutil.ChunkFile(path, relPath, chunksDir, du.CommonConfig.avgChunkSize)
		if err != nil {
			return nil, err
		}
		chunksIndexEntries = append(chunksIndexEntries, chunksIndex)
	}
	elapsedTime := time.Since(start)
	du.CommonConfig.metrics.ChunkTimeMs = elapsedTime.Milliseconds()
	log.Infof("Chunked %d files. Elapsed time: %v", len(paths), elapsedTime)
	return chunksIndexEntries, nil
}

func (du *DirUploader) findMissing(uploadInfos []*uploadinfo.Entry) ([]*uploadinfo.Entry, error) {
	if len(uploadInfos) == 0 {
		return nil, nil
	}

	digests := make([]digest.Digest, len(uploadInfos))
	for i, entry := range uploadInfos {
		digests[i] = entry.Digest
	}

	missingBlobs, err := du.client.MissingBlobs(du.ctx, digests)
	if err != nil {
		return nil, fmt.Errorf("MissingBlobs RPC failed: %w", err)
	}

	if len(missingBlobs) == 0 {
		printEntriesStats(nil, missingEntriesMessage, du.CommonConfig.metrics)
		return nil, nil
	}

	missingSet := make(map[digest.Digest]struct{}, len(missingBlobs))
	for _, blob := range missingBlobs {
		missingSet[blob] = struct{}{}
	}

	missingEntries := make([]*uploadinfo.Entry, 0, len(missingBlobs))
	for _, entry := range uploadInfos {
		if _, ok := missingSet[entry.Digest]; ok {
			missingEntries = append(missingEntries, entry)
		}
	}

	printEntriesStats(missingEntries, missingEntriesMessage, du.CommonConfig.metrics)
	return missingEntries, nil
}

func (du *DirUploader) upload(uploadInfos []*uploadinfo.Entry) error {
	start := time.Now()
	digests, size, err := du.client.UploadIfMissing(du.ctx, uploadInfos...)
	if err != nil {
		return fmt.Errorf("UploadIfMissing failed: %w", err)
	}

	du.CommonConfig.metrics.UploadedSizeBytes = size
	du.CommonConfig.metrics.UploadedEntries = len(digests)
	log.Infof("Uploaded %d blobs, %d bytes. Elapsed time: %v", len(digests), size, time.Since(start))
	return nil
}

func printEntriesStats(entries []*uploadinfo.Entry, message string, metrics *metrics.Metrics) {
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

	switch message {
	case allEntriesMessage:
		metrics.SizeBytes = size
		metrics.Entries = len(entries)
	case missingEntriesMessage:
		metrics.UploadedSizeBytes = size
		metrics.UploadedEntries = len(entries)
	}

	log.Infof("Stats of %s. Size: %d bytes, count: %d, files: %d, blobs: %d",
		message, size, len(entries), numFiles, numBlobs)
}

type uploadEntryInfo struct {
	Path   string `json:"path"`
	Digest string `json:"digest"`
	Size   int64  `json:"size"`
}

// exportUploadFilesDetails outputs the detail information of upload files to the file specified in `dumpFileDetails`.
func (du *DirUploader) exportUploadFilesDetails(root digest.Digest, entries []*uploadinfo.Entry) error {
	path := du.dumpFileDetails
	if path == "" {
		return nil // It's not an error if no dump file is specified.
	}

	blobMap := make(map[string]*uploadinfo.Entry, len(entries))
	for _, entry := range entries {
		if entry.IsBlob() {
			blobMap[entry.Digest.Hash] = entry
		}
	}
	rootEntry, ok := blobMap[root.Hash]
	if !ok {
		return fmt.Errorf("cannot find the root entry of digest %s", root.Hash)
	}

	type uploadEntryTuple struct {
		Path  string
		Entry *uploadinfo.Entry
	}

	queue := []uploadEntryTuple{{Path: "", Entry: rootEntry}}
	files := make([]uploadEntryInfo, 0) // Pre-allocate with 0 length, will grow.

	for i := 0; i < len(queue); i++ {
		tuple := queue[i]
		currPath := tuple.Path
		currEntry := tuple.Entry

		var dir repb.Directory
		if err := proto.Unmarshal(currEntry.Contents, &dir); err != nil {
			return fmt.Errorf("failed to unmarshal blob content of digest %s: %w", currEntry.Digest, err)
		}

		for _, fileNode := range dir.GetFiles() {
			// Defensive check to prevent empty chunk files from being uploaded. Should never happen.
			if strings.HasSuffix(tuple.Path, chunkerutil.ChunksDirName) && fileNode.GetDigest().GetSizeBytes() == 0 {
				return fmt.Errorf("artifact corrupted: detected empty chunk file '%s'", filepath.Join(currPath, fileNode.GetName()))
			}
			files = append(files, uploadEntryInfo{
				Path:   filepath.Join(currPath, fileNode.GetName()),
				Digest: fileNode.GetDigest().GetHash(),
				Size:   fileNode.GetDigest().GetSizeBytes(),
			})
		}
		for _, dirNode := range dir.GetDirectories() {
			// Skip empty directories.
			if dirNode.GetDigest().GetSizeBytes() == 0 {
				continue
			}
			entry, ok := blobMap[dirNode.GetDigest().GetHash()]
			if !ok {
				return fmt.Errorf("cannot find entry for directory digest %s", dirNode.GetDigest().GetHash())
			}
			queue = append(queue, uploadEntryTuple{
				Path:  filepath.Join(currPath, dirNode.GetName()),
				Entry: entry,
			})
		}
	}

	outputContent, err := json.MarshalIndent(files, "", "  ")
	if err != nil {
		return fmt.Errorf("failed to marshal upload entries info: %w", err)
	}

	if err = os.WriteFile(path, outputContent, 0644); err != nil {
		return fmt.Errorf("failed to write upload entries info to %q: %w", path, err)
	}
	log.Infof("Exported %d file digests to %s", len(files), path)
	return nil
}
