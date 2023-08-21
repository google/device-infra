package uploader

import (
	"archive/zip"
	"context"
	"encoding/binary"
	"encoding/hex"
	"fmt"
	"os"
	"os/exec"
	"path"

	log "github.com/golang/glog"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/client"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/digest"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/filemetadata"
	"github.com/hanwen/go-fuse/v2/fs"
	"github.com/hanwen/go-fuse/v2/fuse"
	"github.com/hanwen/go-fuse/v2/zipfs"
)

const (
	// Sha256HeaderID is a custom Header ID for the `extra` field in the file header to store the SHA
	// checksum. It is defined in build/soong/zip/zip.go
	Sha256HeaderID = 0x4967

	// Sha256HeaderSignature is the signature to verify that the extra data block is used to store the
	// SHA checksum. It is defined in build/soong/zip/zip.go
	Sha256HeaderSignature = 0x9514
)

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

// ZipUploader is the uploader to uploader the a zip
type ZipUploader struct {
	uploaderConfig
}

// NewZipUploader creates
func NewZipUploader(ctx context.Context, client *client.Client, zipPath string, excludeFilters []string) Uploader {
	return &ZipUploader{
		uploaderConfig: uploaderConfig{ctx: ctx, client: client, path: zipPath, excludeFilters: excludeFilters},
	}
}

func (zu *ZipUploader) mountZip(zipPath string) (string, *fuse.Server, error) {
	target := createTmpDir()
	root, err := zipfs.NewArchiveFileSystem(zipPath)
	if err != nil {
		return "", nil, err
	}

	server, err := fs.Mount(target, root, nil)
	if err != nil {
		return "", nil, err
	}
	log.Infof("Mounted %s to %s\n", zipPath, target)
	return target, server, nil
}

func (zu *ZipUploader) hasCachedDigest() (bool, error) {
	archive, err := zip.OpenReader(zu.path)
	if err != nil {
		return false, fmt.Errorf("failed to open zip archive: %v", err)
	}
	defer archive.Close()

	for _, f := range archive.File {
		sha256, _ := readSHA256(f.FileHeader.Extra)
		if sha256 != "" {
			return true, nil
		}
	}
	return false, nil
}

func (zu *ZipUploader) createCacheFromZip(rootDir string, zipPath string) (filemetadata.Cache, error) {
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

// DoUpload uploads the unarchived zip file to CAS, and returns the digest of the root directory.
func (zu *ZipUploader) DoUpload() (digest.Digest, error) {
	hasCachedDigest, err := zu.hasCachedDigest()
	if err != nil {
		return digest.Digest{}, err
	}
	if hasCachedDigest {
		rootDigest, err := zu.mountAndUpload()
		return rootDigest, err
	}
	rootDigest, err := zu.unzipAndUpload()
	return rootDigest, err
}

func (zu *ZipUploader) mountAndUpload() (digest.Digest, error) {
	// Mount the zip archive as a FUSE file system for building the Merkle tree with the full
	// directory structure and extracting only parts of the archive.
	mountPath, fuseServer, err := zu.mountZip(zu.path)
	if err != nil {
		return digest.Digest{}, fmt.Errorf("Failed to mount %s to %s: %v", zu.path, mountPath, err)
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

	// Create a cache by reading the zip file header.
	digestCache, err := zu.createCacheFromZip(mountPath, zu.path)
	if err != nil {
		return digest.Digest{}, fmt.Errorf("failed to create digest from zip: %v", err)
	}

	du := NewDirUploader(zu.ctx, zu.client, mountPath, zu.excludeFilters, digestCache)
	rootDigest, err := du.DoUpload()
	if err != nil {
		return rootDigest, fmt.Errorf("failed to upload mounted zip path %s: %v", mountPath, err)
	}
	return rootDigest, nil
}

func (zu *ZipUploader) unzipAndUpload() (digest.Digest, error) {
	targetDir := createTmpDir()
	defer func() {
		if err := os.RemoveAll(targetDir); err != nil {
			log.Errorf("Failed to clean up target dir %s: %v", targetDir, err)
		}
	}()

	cmd := exec.Command("unzip", zu.path, "-d", targetDir)
	output, err := cmd.CombinedOutput()
	if err != nil {
		return digest.Digest{}, fmt.Errorf("failed to unzip %s to %s: %v\n%s",
			zu.path, targetDir, err, string(output))
	}

	du := NewDirUploader(zu.ctx, zu.client, targetDir, zu.excludeFilters, nil)
	rootDigest, err := du.DoUpload()
	if err != nil {
		return rootDigest, fmt.Errorf("failed to upload the unzipped directory %s: %v", targetDir, err)
	}
	return rootDigest, nil
}
