package common

import (
	"os"
	"path/filepath"
	"testing"

	"flag"
)

func TestEnvelopeExistsAt(t *testing.T) {
	tmpDir := t.TempDir()

	// 1. Nonexistent paths return false
	if EnvelopeExistsAt([]string{filepath.Join(tmpDir, "nonexistent")}) {
		t.Errorf("expected nonexistent path to return false")
	}

	// 2. Directory path returns false
	subDir := filepath.Join(tmpDir, "dir")
	if err := os.Mkdir(subDir, 0755); err != nil {
		t.Fatal(err)
	}
	if EnvelopeExistsAt([]string{subDir}) {
		t.Errorf("expected directory path to return false")
	}

	// 3. Regular file without executable bits returns false
	nonExecFile := filepath.Join(tmpDir, "nonexec")
	if err := os.WriteFile(nonExecFile, []byte("#!/bin/sh\n"), 0644); err != nil {
		t.Fatal(err)
	}
	if EnvelopeExistsAt([]string{nonExecFile}) {
		t.Errorf("expected non-executable file to return false")
	}

	// 4. Regular executable file returns true
	execFile := filepath.Join(tmpDir, "exec")
	if err := os.WriteFile(execFile, []byte("#!/bin/sh\n"), 0755); err != nil {
		t.Fatal(err)
	}
	if !EnvelopeExistsAt([]string{execFile}) {
		t.Errorf("expected executable file to return true")
	}

	// 5. Fallback list with first invalid and second valid returns true
	if !EnvelopeExistsAt([]string{nonExecFile, execFile}) {
		t.Errorf("expected fallback to second valid executable path to return true")
	}
}

func TestDisableEnvelopeIfAbsent(t *testing.T) {
	if f := flag.Lookup("envelope_enabled"); f == nil {
		_ = flag.String("envelope_enabled", "true", "test flag")
	} else {
		_ = f.Value.Set("true")
	}

	if f := flag.Lookup("disable_svelte"); f == nil {
		_ = flag.String("disable_svelte", "false", "test flag")
	} else {
		_ = f.Value.Set("false")
	}

	// Temporarily override envelopeCandidatePaths with nonexistent path to trigger disable.
	origPaths := envelopeCandidatePaths
	defer func() { envelopeCandidatePaths = origPaths }()
	envelopeCandidatePaths = []string{filepath.Join(t.TempDir(), "nonexistent")}

	origEnv, hadEnv := os.LookupEnv("ENVELOPE_OPT_OUT")
	defer func() {
		if hadEnv {
			_ = os.Setenv("ENVELOPE_OPT_OUT", origEnv)
		} else {
			_ = os.Unsetenv("ENVELOPE_OPT_OUT")
		}
	}()
	_ = os.Unsetenv("ENVELOPE_OPT_OUT")

	DisableEnvelopeIfAbsent()

	if f := flag.Lookup("envelope_enabled"); f == nil || f.Value.String() != "false" {
		t.Errorf("envelope_enabled flag = %v, want false", f)
	}
	if f := flag.Lookup("disable_svelte"); f == nil || f.Value.String() != "true" {
		t.Errorf("disable_svelte flag = %v, want true", f)
	}
	if val := os.Getenv("ENVELOPE_OPT_OUT"); val != "1" {
		t.Errorf("ENVELOPE_OPT_OUT = %q, want 1", val)
	}
}
