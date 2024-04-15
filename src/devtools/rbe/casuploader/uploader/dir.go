package uploader

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"io/ioutil"
	"os"
	"path/filepath"
	"sync"
	"time"

	log "github.com/golang/glog"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/command"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/digest"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/filemetadata"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/uploadinfo"
	repb "github.com/bazelbuild/remote-apis/build/bazel/remote/execution/v2"
	"google.golang.org/protobuf/proto"
	"github.com/pkg/xattr"
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
	fileLoader                   fileLoader
	preCalculateDigests          bool
	preCalculateDigestsMaxWorker int
}

// DirUploaderOpts is the option to create a DirUploader.
type DirUploaderOpts func(*DirUploader)

// WithFileLoader specifies the file loader to load files from the file system.
func WithFileLoader(fileLoader fileLoader) DirUploaderOpts {
	return func(du *DirUploader) {
		du.fileLoader = fileLoader
	}
}

// WithPreCalculateDigests specifies whether to pre-calculate digests for all files in the directory.
func WithPreCalculateDigests(preCalculateDigests bool, maxWorker int) DirUploaderOpts {
	return func(du *DirUploader) {
		du.preCalculateDigests = preCalculateDigests
		du.preCalculateDigestsMaxWorker = maxWorker
	}
}

// NewDirUploader creates a new directory uploader to upload a directory to CAS.
func NewDirUploader(config *CommonConfig, dirPath string, opts ...DirUploaderOpts) Uploader {
	du := &DirUploader{
		CommonConfig: *config,
		dirPath:      dirPath,
	}
	for _, opt := range opts {
		opt(du)
	}
	return du
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

	if du.preCalculateDigests {
		log.Infof("Begin calculating digests for all files in directory %s with %d workers", du.dirPath, du.preCalculateDigestsMaxWorker)
		err := preCalculateDigests(du.dirPath, du.preCalculateDigestsMaxWorker)
		if err != nil {
			return digest.Digest{}, fmt.Errorf("failed to pre-calculate digests: %v", err)
		}
	}

	filemetadata.XattrDigestName = XattrDigestName
	log.Infof("Begin computing merkle tree for directory %s", du.dirPath)
	rootDigest, uploadEntries, _, err := du.client.ComputeMerkleTree(
		du.ctx, du.dirPath, "", "", &inputSpec, filemetadata.NewNoopCache())
	if err != nil {
		return digest.Digest{}, fmt.Errorf("failed to compute merkle tree: %v", err)
	}
	printEntriesStats(uploadEntries, "all entries in the directory")
	err = du.exportUploadFilesDetails(rootDigest, uploadEntries)
	if err != nil {
		log.Warningf("failed to export upload files info: %v", err)
	}

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

// exportUploadFilesInfo outputs the detail information of upload files to the file specified in `dumpFileDetails`
func (du *DirUploader) exportUploadFilesDetails(root digest.Digest, entries []*uploadinfo.Entry) error {
	path := du.dumpFileDetails
	if path == "" {
		return fmt.Errorf("the path of dumpFileDetails is empty")
	}

	blobMap := make(map[string]*uploadinfo.Entry)
	for _, entry := range entries {
		if entry.IsBlob() {
			blobMap[entry.Digest.Hash] = entry
		}
	}
	rootEntry, ok := blobMap[root.Hash]
	if !ok {
		return fmt.Errorf("cannot to find the root entry of digest %s", rootEntry.Digest)
	}

	type uploadEntryTuple struct {
		Path  string
		Entry *uploadinfo.Entry
	}
	queue := []uploadEntryTuple{uploadEntryTuple{"", rootEntry}}
	files := []uploadEntryInfo{}
	for len(queue) > 0 {
		currPath := queue[0].Path
		currEntry := queue[0].Entry
		queue = queue[1:]
		var dir = repb.Directory{}
		if err := proto.Unmarshal(currEntry.Contents, &dir); err != nil {
			return fmt.Errorf("failed to unmarshal blob content of digest %s: %v", currEntry.Digest, err)
		}
		for _, fileNode := range dir.GetFiles() {
			files = append(files, uploadEntryInfo{
				Path:   filepath.Join(currPath, fileNode.GetName()),
				Digest: fileNode.GetDigest().GetHash(),
				Size:   fileNode.GetDigest().GetSizeBytes(),
			})
		}
		for _, dirNode := range dir.GetDirectories() {
			// Skip the empty directory. Both an empty directory and an empty file have the same digest,
			// therefore, if there is an empty file exists in the file tree, the `uploadinfo.Entry` of an
			// empty directory may be omitted.
			if dirNode.GetDigest().GetSizeBytes() == 0 {
				continue
			}
			entry, ok := blobMap[dirNode.GetDigest().GetHash()]
			if !ok {
				return fmt.Errorf("cannot find the entry of digest %s", dirNode.GetDigest().GetHash())
			}
			queue = append(queue, uploadEntryTuple{
				Path:  filepath.Join(currPath, dirNode.GetName()),
				Entry: entry,
			})
		}
	}

	outputContent, err := json.MarshalIndent(files, "", "  ")
	if err != nil {
		return fmt.Errorf("failed to export upload entries info: %v", err)
	}

	if err = ioutil.WriteFile(path, outputContent, 0644); err != nil {
		return fmt.Errorf("failed to export upload entries info to %s: %v", path, err)
	}
	log.Infof("exported file digests to %s, size of files: %d", path, len(files))
	return nil
}

func preCalculateDigests(root string, maxWorkers int) error {
	var wg sync.WaitGroup
	filePaths := make(chan string)

	wg.Add(maxWorkers)
	for i := 0; i < maxWorkers; i++ {
		go func() {
			defer wg.Done()
			for path := range filePaths {
				hash, err := calculateSHA256(path)
				if err != nil {
					log.Warningf("Error calculating hash for file %s: %v", path, err)
					continue
				}
				xattr.Set(path, XattrDigestName, []byte(hex.EncodeToString(hash)))
			}
		}()
	}

	err := filepath.Walk(root, func(path string, info os.FileInfo, err error) error {
		if err != nil {
			return fmt.Errorf("failed to visit path %s: %v", path, err)
		}
		if !info.IsDir() {
			filePaths <- path
		}
		return nil
	})
	close(filePaths)

	if err != nil {
		return fmt.Errorf("failed to walk path %s: %v", root, err)
	}
	wg.Wait()
	return nil
}

func calculateSHA256(filePath string) ([]byte, error) {
	file, err := os.Open(filePath)
	if err != nil {
		return nil, err
	}
	defer file.Close()

	hash := sha256.New()
	if _, err := io.Copy(hash, file); err != nil {
		return nil, err
	}

	return hash.Sum(nil), nil
}
