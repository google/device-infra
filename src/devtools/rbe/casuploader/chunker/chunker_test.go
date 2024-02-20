package chunker

import (
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"io"
	"io/ioutil"
	"math/rand"
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/google/uuid"
)

func TestChunkFile(t *testing.T) {
	const fileSizeKB = 2 * 1024

	tests := []struct {
		name             string
		duplicateContent bool
	}{
		{"regluar", false},
		{"duplicateContent", true},
	}

	for _, test := range tests {
		targetDir := filepath.Join(os.TempDir(), "chunker_tmp", uuid.New().String())
		os.MkdirAll(targetDir, 0755)
		defer func() {
			if err := os.RemoveAll(targetDir); err != nil {
				fmt.Printf("failed to remove tmp dir: %v", err)
			}
		}()

		chunksDir := filepath.Join(targetDir, "chunks")
		os.MkdirAll(chunksDir, 0755)

		seed := time.Now().UnixNano()
		fmt.Printf("Seed: %d\n", seed)

		sourcePath := filepath.Join(targetDir, test.name+"_source")
		if err := createRandomFile(sourcePath, fileSizeKB*1024, seed, test.duplicateContent); err != nil {
			t.Fatalf("Failed to create random file: %v", err)
		}

		avgChunkSizeKB := fileSizeKB / 10
		chunks, err := ChunkFile(sourcePath, chunksDir, avgChunkSizeKB)
		if err != nil {
			t.Fatalf("Failed to chunk file: %v", err)
		}

		restoredPath := filepath.Join(targetDir, test.name+"_restored")
		fmt.Printf("Restoring file %s from dir: %s\n", restoredPath, chunksDir)
		if err := RestoreFile(restoredPath, chunksDir, chunks); err != nil {
			t.Fatalf("Failed to restore file: %v", err)
		}

		if matched, err := compareFilesByHash(sourcePath, restoredPath); err != nil {
			t.Fatalf("Failed to compare files by hash: %v", err)
		} else if !matched {
			t.Fatalf("The hashes for the source and restored file do not match")
		}
	}
}

func createRandomFile(path string, size int, seed int64, duplicateContent bool) error {
	fmt.Printf("Creating a random file %s of size %d using seed %d\n", path, size, seed)

	// Generate seeded random data
	rng := rand.New(rand.NewSource(seed))

	// To generate a duplicateContent file, the file size is doubled and
	// the content of the second half is a duplicate of the first half.
	// This can be useful for testing the files that result in duplicated chunks.
	fileSize := size
	if duplicateContent {
		fileSize += size
	}

	randomData := make([]byte, fileSize)
	for i := range randomData {
		if i < size {
			randomData[i] = byte(rng.Intn(256))
		} else {
			randomData[i] = randomData[i-size]
		}
	}

	// Write the generated data to the file.
	if err := ioutil.WriteFile(path, randomData, 0644); err != nil {
		fmt.Printf("Error writing to file %s, %s\n", path, err)
		return err
	}

	return nil
}

func compareFilesByHash(path1, path2 string) (bool, error) {
	f1, err := os.Open(path1)
	if err != nil {
		return false, err
	}
	defer f1.Close()

	f2, err := os.Open(path2)
	if err != nil {
		return false, err
	}
	defer f2.Close()

	h1 := sha256.New()
	if _, err := io.Copy(h1, f1); err != nil {
		return false, err
	}

	h2 := sha256.New()
	if _, err := io.Copy(h2, f2); err != nil {
		return false, err
	}

	return hex.EncodeToString(h1.Sum(nil)) == hex.EncodeToString(h2.Sum(nil)), nil
}
