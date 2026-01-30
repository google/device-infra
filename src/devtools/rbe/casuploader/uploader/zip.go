package uploader

import (
	"archive/zip"
	"encoding/binary"
	"encoding/hex"
	"fmt"
	"io"
	"io/fs"
	"os"
	"path/filepath"
	"strings"
	"time"

	log "github.com/golang/glog"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/digest"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/filemetadata"
	"github.com/pkg/xattr"
)

const (
	// Sha256HeaderID is a custom Header ID for the `extra` field in the file header to store the SHA
	// checksum. It is defined in build/soong/zip/zip.go
	Sha256HeaderID = 0x4967

	// Sha256HeaderSignature is the signature to verify that the extra data block is used to store the
	// SHA checksum. It is defined in build/soong/zip/zip.go
	Sha256HeaderSignature = 0x9514

	// XattrDigestName is the xattr name for the object digest. It is used by Remote API Sdks to get
	// the file digest without loading actual file content.
	XattrDigestName = "user.digest.sha256"

	// XattrSrcZipPath is the xattr name for the path in zip file. It maps the extracted file to the
	// compressed file inside the original zip.
	XattrSrcZipPath = "user.zip_src"
)

// ZipUploader is the uploader to uploader the a zip
type ZipUploader struct {
	CommonConfig
	zipPath string
}

// NewZipUploader creates
func NewZipUploader(config *CommonConfig, zipPath string) Uploader {
	return &ZipUploader{
		CommonConfig: *config,
		zipPath:      zipPath,
	}
}

// DoUpload uploads the unarchived zip file to CAS, and returns the digest of the root directory.
func (zu *ZipUploader) DoUpload() (digest.Digest, error) {
	// Set the digest xattr key name to filemetadata
	filemetadata.XattrDigestName = XattrDigestName

	targetDir := createTmpDir()
	defer func() {
		if err := os.RemoveAll(targetDir); err != nil {
			log.Errorf("Failed to remove tmp dir: %v\n", err)
		}
	}()

	log.Infof("Extracting %s to %s with digests\n", zu.zipPath, targetDir)

	unarchiver, err := newZipUnarchiver(zu.zipPath, targetDir)
	if err != nil {
		return digest.Digest{}, fmt.Errorf("failed to create zip unarchiver for %s: %v", zu.zipPath, err)
	}
	defer unarchiver.Close()

	start := time.Now()
	err = unarchiver.extractAll(extractOptions{
		// For in-zip chunking, the entire zip file needs to be decompressed.
		skipIfDigestExists: zu.CommonConfig.chunk == false,
	})
	if err != nil {
		return digest.Digest{}, fmt.Errorf("failed to extract %s to %s: %v", zu.zipPath, targetDir, err)
	}
	zu.CommonConfig.metrics.UnzipTimeMs = time.Since(start).Milliseconds()

	du := NewDirUploader(&zu.CommonConfig, targetDir, &zipFileLoader{Unarchiver: unarchiver})
	rootDigest, err := du.DoUpload()
	if err != nil {
		return rootDigest, fmt.Errorf("failed to upload the directory %s for zip %s: %v", targetDir, zu.zipPath, err)
	}
	return rootDigest, nil
}

func secureJoin(dstRoot, name string) (string, error) {
	cleanedDstRoot := filepath.Clean(dstRoot)
	filePath := filepath.Join(cleanedDstRoot, name)
	if !strings.HasPrefix(filePath, cleanedDstRoot+string(os.PathSeparator)) && filePath != cleanedDstRoot {
		return "", fmt.Errorf("invalid path: %q attempts to write outside of %q", name, cleanedDstRoot)
	}
	return filePath, nil
}

type zipUnarchiver struct {
	zipPath  string
	dstRoot  string
	zr       *zip.ReadCloser
	zfByName map[string]*zip.File
}

func newZipUnarchiver(zipPath string, dstRoot string) (*zipUnarchiver, error) {
	zipReader, err := zip.OpenReader(zipPath)
	if err != nil {
		return nil, fmt.Errorf("failed to open zip archive: %v", err)
	}
	return &zipUnarchiver{
		zipPath: zipPath,
		dstRoot: dstRoot,
		zr:      zipReader,
	}, nil
}

func (zu *zipUnarchiver) Close() error {
	return zu.zr.Close()
}

// extractAll extracts all files from the zip file to the destination directory. If skipFileWithDigest
// is true, for files with SHA256 value stored in the zip file header, this extractor will only
// create an empty file, and set the digest to xattr values.
func (zu *zipUnarchiver) extractAll(o extractOptions) error {
	for _, f := range zu.zr.File {
		if f.FileHeader.Mode().IsDir() {
			if err := zu.extractDir(f); err != nil {
				return fmt.Errorf("failed to extract directory %s: %v", f.Name, err)
			}
			continue
		}
		if err := zu.extractFile(f, o); err != nil {
			return fmt.Errorf("failed to extract file %s: %v", f.Name, err)
		}
	}
	return nil
}

func (zu *zipUnarchiver) extractDir(zf *zip.File) error {
	filePath, err := secureJoin(zu.dstRoot, zf.Name)
	if err != nil {
		return err
	}
	if err := os.MkdirAll(filePath, zf.Mode()); err != nil {
		return fmt.Errorf("failed to extract directory %s: %v", filePath, err)
	}
	return nil
}

type extractOptions struct {
	// If true, create a 0 size file with setting xattr value to the digest, if the zip file header contains a digest.
	skipIfDigestExists bool
	// If true, follow sym links and write file contents into the file. A symlink is created otherwise.
	followSymLinks bool
}

func (zu *zipUnarchiver) extractFile(zf *zip.File, o extractOptions) error {
	filePath, err := secureJoin(zu.dstRoot, zf.Name)
	if err != nil {
		return err
	}
	if err := os.MkdirAll(filepath.Dir(filePath), os.ModePerm); err != nil {
		return fmt.Errorf("failed to create directory %s: %v", filepath.Dir(filePath), err)
	}

	if o.skipIfDigestExists {
		sha256, err := readSHA256(zf.FileHeader.Extra)
		if err != nil {
			log.Warningf("Failed to read SHA256 of file %s, skip. Error: %v\n", zf.Name, err)
		}
		// If the digest value exists in zip file header, only create an empty file and set xattr
		// values.
		if sha256 != "" {
			if _, err = os.Create(filePath); err != nil {
				return fmt.Errorf("failed to create file %s: %v", filePath, err)
			}
			if err := xattr.Set(filePath, XattrDigestName, []byte(
				fmt.Sprintf("%s/%d", sha256, zf.FileHeader.UncompressedSize64))); err != nil {
				return fmt.Errorf("failed to set xattr %s to %s: %v", XattrDigestName, filePath, err)
			}
			if err := xattr.Set(filePath, XattrSrcZipPath, []byte(zf.Name)); err != nil {
				return fmt.Errorf("failed to set xattr %s to %s: %v", XattrSrcZipPath, filePath, err)
			}
			if err := os.Chmod(filePath, zf.Mode()); err != nil {
				return fmt.Errorf("failed to set mode %s to file %s: %v", zf.Mode(), filePath, err)
			}
			if err := os.Chtimes(filePath, time.Time{}, zf.Modified); err != nil {
				return fmt.Errorf("failed to set modified time %s to file %s: %v", zf.Modified, filePath, err)
			}
			return nil
		}
	}

	if err := zu.resolve(zf, filePath, o.followSymLinks); err != nil {
		return fmt.Errorf("failed to resolve %s: %v", zf.Name, err)
	}

	return nil
}

func (zu *zipUnarchiver) resolve(zf *zip.File, filePath string, followSymLinks bool) error {
	if err := os.MkdirAll(filepath.Dir(filePath), os.ModePerm); err != nil {
		return fmt.Errorf("failed to create directory %s: %v", filepath.Dir(filePath), err)
	}

	src, err := zf.Open()
	if err != nil {
		return fmt.Errorf("failed to open file %s in archive: %v", zf.Name, err)
	}
	defer src.Close()

	if zf.FileHeader.Mode().Type()&fs.ModeSymlink == 0 {
		// Write the file content to the destination.
		dst, err := os.OpenFile(filePath, os.O_WRONLY|os.O_CREATE|os.O_TRUNC, zf.Mode())
		if err != nil {
			return fmt.Errorf("failed to open file %s: %v", filePath, err)
		}
		defer dst.Close()

		if _, err = io.Copy(dst, src); err != nil {
			return fmt.Errorf("failed to extract file %s in archive: %v", zf.Name, err)
		}

		if err := os.Chmod(filePath, zf.Mode()); err != nil {
			return fmt.Errorf("failed to set mode %s to file %s: %v", zf.Mode(), filePath, err)
		}

		if err := os.Chtimes(filePath, time.Time{}, zf.Modified); err != nil {
			return fmt.Errorf("failed to set modified time %s to file %s: %v", zf.Modified, filePath, err)
		}

		return nil
	}

	// Handle symlinks.
	b, err := io.ReadAll(src)
	if err != nil {
		return fmt.Errorf("failed to read file %s in archive: %v", zf.Name, err)
	}

	target := string(b)
	if !followSymLinks {
		return os.Symlink(string(target), filePath)
	}

	if zu.zfByName == nil {
		zu.zfByName = make(map[string]*zip.File)
		for _, f := range zu.zr.File {
			zu.zfByName[f.Name] = f
		}
	}

	resolvedName := filepath.Clean(filepath.Join(filepath.Dir(zf.Name), target))
	resolvedZF, ok := zu.zfByName[resolvedName]
	if !ok {
		return fmt.Errorf("failed to resolve %s: no zip entry for %s", zf.Name, resolvedName)
	}
	return zu.resolve(resolvedZF, filePath, followSymLinks)
}

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

type zipFileLoader struct {
	Unarchiver *zipUnarchiver
}

func (zfl *zipFileLoader) LoadFiles(dstPaths []string) error {
	start := time.Now()
	paths := make(map[string]string)
	for _, path := range dstPaths {
		stat, err := os.Stat(path)
		if err != nil {
			return err
		}
		if stat.Size() > 0 {
			continue
		}
		srcPathXattr, err := xattr.Get(path, XattrSrcZipPath)
		if err != nil {
			return err
		}
		paths[string(srcPathXattr)] = path
	}

	var count int
	var size int64
	for _, f := range zfl.Unarchiver.zr.File {
		if targetPath, ok := paths[f.Name]; ok {
			if err := zfl.Unarchiver.extractFile(f, extractOptions{
				followSymLinks: true,
			}); err != nil {
				return fmt.Errorf("failed to load file content of %s from archive: %v", targetPath, err)
			}
			count++
			size += int64(f.UncompressedSize64)
		}
	}
	log.Infof("Loaded %d files, %d bytes. Time: %v\n", count, size, time.Since(start))
	return nil
}
