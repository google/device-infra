package uploader

import (
	"archive/zip"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"io"
	"os"
	"path"
	"path/filepath"
	"strings"
	"testing"
)

const (
	testZip = "test_data/partial_zip.zip"
)

type fileVerification struct {
	path     string
	size     int64
	checksum string
	linkTo   string
}

func TestExtractAll(t *testing.T) {
	testCases := []struct {
		name           string
		extractOptions extractOptions
		want           []fileVerification
	}{
		{
			name: "skip_no_follow",
			extractOptions: extractOptions{
				skipIfDigestExists: true,
			},
			want: []fileVerification{
				{
					path: "empty/empty_file",
					size: 0,
				},
				{
					path: "large_text/file.txt",
					size: 0,
				},
				{
					path: "read_only/readonly_file",
					size: 0,
				},
				{
					path:   "symlinks/large_text_derived/file.txt",
					linkTo: "../../large_text/file.txt",
				},
				{
					path:   "symlinks/large_text_derived2/file_derived_2.txt",
					linkTo: "../large_text_derived/file.txt",
				},
			},
		},
		{
			name: "no_skip_with_follow",
			extractOptions: extractOptions{
				followSymLinks: true,
			},
			want: []fileVerification{
				{
					path:     "empty/empty_file",
					size:     0,
					checksum: "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
				},
				{
					path:     "large_text/file.txt",
					size:     802816,
					checksum: "d8c076a86a7f2bb3cc87d6e632d9d8e8268a5bfbf68395413a5bfde19de52d1d",
				},
				{
					path:     "read_only/readonly_file",
					size:     24,
					checksum: "7036bbbfdf466127c759c7f388b8d1283356925b83dce60296e8de378a1d4338",
				},
				{
					path:     "symlinks/large_text_derived/file.txt",
					size:     802816,
					checksum: "d8c076a86a7f2bb3cc87d6e632d9d8e8268a5bfbf68395413a5bfde19de52d1d",
				},
				{
					path:     "symlinks/large_text_derived2/file_derived_2.txt",
					size:     802816,
					checksum: "d8c076a86a7f2bb3cc87d6e632d9d8e8268a5bfbf68395413a5bfde19de52d1d",
				},
			},
		},
		{
			name: "skip_with_follow",
			extractOptions: extractOptions{
				skipIfDigestExists: true,
				followSymLinks:     true,
			},
			want: []fileVerification{
				{ // skipped because digest exists
					path: "large_text/file.txt",
					size: 0,
				},
				{ // followed because no digest for symlinks
					path:     "symlinks/large_text_derived2/file_derived_2.txt",
					size:     802816,
					checksum: "d8c076a86a7f2bb3cc87d6e632d9d8e8268a5bfbf68395413a5bfde19de52d1d",
				},
			},
		},
		{
			name:           "no_skip_no_follow",
			extractOptions: extractOptions{},
			want: []fileVerification{
				{
					path:     "large_text/file.txt",
					size:     802816,
					checksum: "d8c076a86a7f2bb3cc87d6e632d9d8e8268a5bfbf68395413a5bfde19de52d1d",
				},
				{
					path:   "symlinks/large_text_derived/file.txt",
					linkTo: "../../large_text/file.txt",
				},
				{
					path:   "symlinks/large_text_derived2/file_derived_2.txt",
					linkTo: "../large_text_derived/file.txt",
				},
			},
		},
	}

	for _, tc := range testCases {
		t.Run(tc.name, func(t *testing.T) {
			targetDir := t.TempDir()
			unarchiver, err := newZipUnarchiver(testZip, targetDir)
			if err != nil {
				t.Fatalf("newZipUnarchiver() failed: %v", err)
			}
			err = unarchiver.extractAll(tc.extractOptions)
			if err != nil {
				t.Fatalf("extractAll() failed: %v", err)
			}
			for _, want := range tc.want {
				err := verifyExtractedFile(targetDir, want)
				if err != nil {
					t.Errorf("verifyExtractedFile(%s) failed: %v", want.path, err)
				}
			}
		})
	}
}

func TestResolve(t *testing.T) {
	testcases := []struct {
		name           string
		file           string
		followSymLinks bool
		want           []fileVerification
	}{
		{
			name: "normal_file",
			file: "large_text/file.txt",
			want: []fileVerification{
				{
					path:     "large_text/file.txt",
					size:     802816,
					checksum: "d8c076a86a7f2bb3cc87d6e632d9d8e8268a5bfbf68395413a5bfde19de52d1d",
				},
			},
		},
		{
			name: "link_no_follow",
			file: "symlinks/large_text_derived/file.txt",
			want: []fileVerification{
				{
					path:   "symlinks/large_text_derived/file.txt",
					linkTo: "../../large_text/file.txt",
				},
			},
		},
		{
			name:           "link_with_follow",
			followSymLinks: true,
			file:           "symlinks/large_text_derived2/file_derived_2.txt",
			want: []fileVerification{
				{
					path:     "symlinks/large_text_derived2/file_derived_2.txt",
					size:     802816,
					checksum: "d8c076a86a7f2bb3cc87d6e632d9d8e8268a5bfbf68395413a5bfde19de52d1d",
				},
			},
		},
	}

	for _, tc := range testcases {
		t.Run(tc.name, func(t *testing.T) {
			targetDir := t.TempDir()
			unarchiver, err := newZipUnarchiver(testZip, targetDir)
			if err != nil {
				t.Fatalf("newZipUnarchiver() failed: %v", err)
			}

			if err = unarchiver.resolve(findZipFile(unarchiver, tc.file), filepath.Join(targetDir, tc.file), tc.followSymLinks); err != nil {
				t.Fatalf("resolve() failed: %v", err)
			}

			verified := make(map[string]bool)
			for _, want := range tc.want {
				err = verifyExtractedFile(targetDir, want)
				if err != nil {
					t.Errorf("verifyExtractedFile(%s) failed: %v", want.path, err)
				}
				verified[want.path] = true
			}
			err = filepath.Walk(targetDir, func(path string, info os.FileInfo, err error) error {
				if err != nil {
					return err
				}
				if info.IsDir() {
					return nil
				}
				rel, err := filepath.Rel(targetDir, path)
				if err != nil {
					return err
				}
				if !verified[rel] {
					return fmt.Errorf("unexpected file extracted: %s", path)
				}
				return nil
			})
			if err != nil {
				t.Errorf("check extracted directory failed: %v", err)
			}
		})
	}
}

func TestZipSlip(t *testing.T) {
	outsideDir := t.TempDir()
	outsidePath := filepath.Join(outsideDir, "outside.txt")

	testCases := []struct {
		name      string
		fileName  func(t *testing.T, targetDir string) string
		wantError bool
	}{
		{
			name: "normal file inside the target dir",
			fileName: func(t *testing.T, targetDir string) string {
				return "text.txt"
			},
			wantError: false,
		},
		{
			name: "relative path traversal to outside file",
			fileName: func(t *testing.T, targetDir string) string {
				rel, _ := filepath.Rel(targetDir, outsidePath)
				return rel
			},
			wantError: true,
		},
	}

	for _, tc := range testCases {
		t.Run(tc.name, func(t *testing.T) {
			os.Remove(outsidePath)
			targetDir := t.TempDir()

			zipPath := filepath.Join(targetDir, "test.zip")
			fileNameInZip := tc.fileName(t, targetDir)
			createZipWithFiles(t, zipPath, []string{fileNameInZip})

			unarchiver, err := newZipUnarchiver(zipPath, targetDir)
			if err != nil {
				t.Errorf("newZipUnarchiver(%q, %q): %v", zipPath, targetDir, err)
			}
			defer unarchiver.Close()

			if err := unarchiver.extractAll(extractOptions{}); tc.wantError && err == nil {
				t.Errorf("extractAll succeeded with malicious zip entry %q, want error", fileNameInZip)
			}

			if _, err := os.Stat(outsidePath); !os.IsNotExist(err) {
				t.Errorf("extractAll() wrote to outside file %q, but should not have", outsidePath)
			}
		})
	}
}

func createZipWithFiles(t *testing.T, zipPath string, fileNames []string) {
	t.Helper()
	z, err := os.Create(zipPath)
	if err != nil {
		t.Fatalf("create zip: %v", err)
	}
	defer z.Close()
	zw := zip.NewWriter(z)
	for _, fileName := range fileNames {
		_, err = zw.Create(fileName)
		if err != nil {
			t.Fatalf("create file %q inside zip: %v", fileName, err)
		}
	}
	if err := zw.Close(); err != nil {
		t.Fatalf("close writer: %v", err)
	}
}

// fileChecksum computes the checksum value (SHA256) of a file.
func fileChecksum(filePath string) (string, error) {
	file, err := os.Open(filePath)
	if err != nil {
		return "", fmt.Errorf("failed to open file %s: %v", filePath, err)
	}
	defer file.Close()

	hasher := sha256.New()
	if _, err := io.Copy(hasher, file); err != nil {
		return "", fmt.Errorf("failed to read file %s: %v", filePath, err)
	}
	return hex.EncodeToString(hasher.Sum(nil)), nil
}

func findZipFile(u *zipUnarchiver, path string) *zip.File {
	for _, f := range u.zr.File {
		if f.Name == path {
			return f
		}
	}
	return nil
}

func verifyExtractedFile(targetDir string, want fileVerification) error {
	path := path.Join(targetDir, want.path)
	if want.linkTo != "" {
		linkTo, err := os.Readlink(path)
		if err != nil {
			return fmt.Errorf("os.Readlink(%s) failed: %v", want.path, err)
		}
		if linkTo != want.linkTo {
			return fmt.Errorf("os.Readlink(%s) = %s, want %s", want.path, linkTo, want.linkTo)
		}
		return nil // size and checksum are irrelevant for links
	}

	var errors []string
	size, err := os.Stat(path)
	if err != nil {
		return fmt.Errorf("os.Stat(%s) failed: %v", want.path, err)
	}
	if size.Size() != want.size {
		errors = append(errors, fmt.Sprintf("actual size of %q = %d, want %d", want.path, size.Size(), want.size))
	}

	if want.checksum != "" {
		checksum, err := fileChecksum(path)
		if err != nil {
			return fmt.Errorf("fileChecksum(%s) failed: %v", want.path, err)
		}
		if checksum != want.checksum {
			errors = append(errors, fmt.Sprintf("fileChecksum(%s) = %s, want %s", want.path, checksum, want.checksum))
		}
	}

	if len(errors) > 0 {
		return fmt.Errorf("verifyExtractedFile(%s) failed: %s", want.path, strings.Join(errors, "; "))
	}
	return nil
}
