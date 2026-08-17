package main_test

import (
	"os"
	"path/filepath"
	"testing"
)

func envelopeExistsAt(paths []string) bool {
	for _, path := range paths {
		if info, err := os.Stat(path); err == nil {
			if info.Mode().IsRegular() && (info.Mode().Perm()&0111 != 0) {
				return true
			}
		}
	}
	return false
}

func TestEnvelopeExistsAt(t *testing.T) {
	tmpDir := t.TempDir()

	// 1. Nonexistent paths return false
	if envelopeExistsAt([]string{filepath.Join(tmpDir, "nonexistent")}) {
		t.Errorf("expected nonexistent path to return false")
	}

	// 2. Directory path returns false
	subDir := filepath.Join(tmpDir, "dir")
	if err := os.Mkdir(subDir, 0755); err != nil {
		t.Fatal(err)
	}
	if envelopeExistsAt([]string{subDir}) {
		t.Errorf("expected directory path to return false")
	}

	// 3. Regular file without executable bits returns false
	nonExecFile := filepath.Join(tmpDir, "nonexec")
	if err := os.WriteFile(nonExecFile, []byte("#!/bin/sh\n"), 0644); err != nil {
		t.Fatal(err)
	}
	if envelopeExistsAt([]string{nonExecFile}) {
		t.Errorf("expected non-executable file to return false")
	}

	// 4. Regular executable file returns true
	execFile := filepath.Join(tmpDir, "exec")
	if err := os.WriteFile(execFile, []byte("#!/bin/sh\n"), 0755); err != nil {
		t.Fatal(err)
	}
	if !envelopeExistsAt([]string{execFile}) {
		t.Errorf("expected executable file to return true")
	}

	// 5. Fallback list with first invalid and second valid returns true
	if !envelopeExistsAt([]string{nonExecFile, execFile}) {
		t.Errorf("expected fallback to second valid executable path to return true")
	}
}
