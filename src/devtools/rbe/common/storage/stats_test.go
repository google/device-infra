package storage

import (
	"os"
	"path/filepath"
	"strings"
	"sync"
	"syscall"
	"testing"
)

func TestOnceFlag(t *testing.T) {
	var f onceFlag
	if !f.first() {
		t.Fatal("first() on a fresh onceFlag = false, want true")
	}
	for i := 0; i < 5; i++ {
		if f.first() {
			t.Fatalf("first() returned true again on call %d; the gate must fire exactly once", i+2)
		}
	}
}

func TestOnceFlagIsRaceFree(t *testing.T) {
	// The gate is read from every blob operation, so concurrent callers must
	// still see exactly one winner.
	var (
		f    onceFlag
		mu   sync.Mutex
		wins int
		wg   sync.WaitGroup
	)
	for i := 0; i < 64; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			if f.first() {
				mu.Lock()
				wins++
				mu.Unlock()
			}
		}()
	}
	wg.Wait()
	if wins != 1 {
		t.Errorf("Concurrent first() produced %d winners, want exactly 1", wins)
	}
}

func TestNoteCopyFallbackClassifiesErrno(t *testing.T) {
	tests := []struct {
		name                        string
		err                         error
		wantLink, wantDev, wantElse int64
	}{
		{"EMLINK", syscall.EMLINK, 1, 0, 0},
		{"EXDEV", syscall.EXDEV, 0, 1, 0},
		{"EPERM", syscall.EPERM, 0, 0, 1},
		{"EOPNOTSUPP", syscall.EOPNOTSUPP, 0, 0, 1},
		{"wrapped EXDEV", &os.LinkError{Op: "link", Err: syscall.EXDEV}, 0, 1, 0},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			s := setupTestStorage(t)
			s.noteCopyFallback(tc.err, "/src", "/dst")

			got := s.Stats()
			if got.CopyFallbackEMLINK != tc.wantLink {
				t.Errorf("CopyFallbackEMLINK = %d, want %d", got.CopyFallbackEMLINK, tc.wantLink)
			}
			if got.CopyFallbackEXDEV != tc.wantDev {
				t.Errorf("CopyFallbackEXDEV = %d, want %d", got.CopyFallbackEXDEV, tc.wantDev)
			}
			if got.CopyFallbackOther != tc.wantElse {
				t.Errorf("CopyFallbackOther = %d, want %d", got.CopyFallbackOther, tc.wantElse)
			}
		})
	}
}

// TestEMLINKIsCountedButNeverLogged pins the behavior this whole mechanism
// exists for.
//
// Android artifacts embed the same shared objects (libc++.so, liblog.so) in
// nearly every test, so those inodes routinely exhaust the ext4 link ceiling
// and every consumer of them takes the copy fallback. Logging even once per
// process would put a warning in essentially every casdownloader run, and
// logging once per blob would bury the run's real output entirely.
//
// The gate is the observable proxy for "did this log?": EMLINK must leave it
// unfired so that a later, genuinely actionable EXDEV still gets its one line.
func TestEMLINKIsCountedButNeverLogged(t *testing.T) {
	s := setupTestStorage(t)

	const emlinks = 1000
	for i := 0; i < emlinks; i++ {
		s.noteCopyFallback(syscall.EMLINK, "/cache/libc++.so", "/out/libc++.so")
	}

	if got := s.Stats().CopyFallbackEMLINK; got != emlinks {
		t.Errorf("CopyFallbackEMLINK = %d, want %d", got, emlinks)
	}
	if s.counters.firstStructuralFallback.fired.Load() {
		t.Error("EMLINK consumed the structural-fallback warning gate; it must never log")
	}

	// A real misconfiguration arriving later must still get its line.
	s.noteCopyFallback(syscall.EXDEV, "/cache/blob", "/out/blob")
	if !s.counters.firstStructuralFallback.fired.Load() {
		t.Error("EXDEV did not fire the structural-fallback warning gate")
	}
}

func TestStructuralFallbackWarnsOnlyOnce(t *testing.T) {
	s := setupTestStorage(t)

	const n = 50
	for i := 0; i < n; i++ {
		s.noteCopyFallback(syscall.EXDEV, "/cache/blob", "/out/blob")
	}

	if got := s.Stats().CopyFallbackEXDEV; got != n {
		t.Errorf("CopyFallbackEXDEV = %d, want %d", got, n)
	}
	// The gate having fired once is what bounds the log output; the counter is
	// what preserves the magnitude that the suppressed lines would have shown.
	if s.counters.firstStructuralFallback.first() {
		t.Error("The structural-fallback gate was still unfired after 50 occurrences")
	}
}

func TestStatsRecordsHardlinkOutcomes(t *testing.T) {
	s := setupTestStorage(t)
	destDir := t.TempDir()

	present := testHash("a1")
	if err := s.Write(present, []byte("cached blob")); err != nil {
		t.Fatalf("Write failed: %v", err)
	}

	// Two hits and one miss.
	for i, name := range []string{"hit1", "hit2"} {
		if hit, err := s.HardlinkTo(present, filepath.Join(destDir, name)); err != nil || !hit {
			t.Fatalf("HardlinkTo #%d: hit=%v err=%v, want hit=true err=nil", i, hit, err)
		}
	}
	if hit, err := s.HardlinkTo(testHash("bb"), filepath.Join(destDir, "absent")); err != nil || hit {
		t.Fatalf("HardlinkTo for an absent blob: hit=%v err=%v, want hit=false err=nil", hit, err)
	}

	// One fresh ingest, then a second ingest of the same digest which should
	// be recognized as already cached.
	src := filepath.Join(destDir, "produced.bin")
	if err := os.WriteFile(src, []byte("freshly downloaded"), 0644); err != nil {
		t.Fatalf("Failed to write source file: %v", err)
	}
	ingest := testHash("c2")
	if err := s.HardlinkFrom(ingest, src); err != nil {
		t.Fatalf("HardlinkFrom failed: %v", err)
	}
	if err := s.HardlinkFrom(ingest, src); err != nil {
		t.Fatalf("Second HardlinkFrom failed: %v", err)
	}

	got := s.Stats()
	if got.HardlinkHits != 2 {
		t.Errorf("HardlinkHits = %d, want 2", got.HardlinkHits)
	}
	if got.HardlinkMisses != 1 {
		t.Errorf("HardlinkMisses = %d, want 1", got.HardlinkMisses)
	}
	if got.Ingested != 1 {
		t.Errorf("Ingested = %d, want 1", got.Ingested)
	}
	if got.IngestDeduped != 1 {
		t.Errorf("IngestDeduped = %d, want 1", got.IngestDeduped)
	}
	if got.CopyFallbacks() != 0 {
		t.Errorf("CopyFallbacks() = %d, want 0 on a healthy filesystem", got.CopyFallbacks())
	}
}

func TestStatsCountsCopyFallbackThroughHardlinkTo(t *testing.T) {
	s := setupTestStorage(t)
	hash := testHash("d3")
	if err := s.Write(hash, []byte("blob")); err != nil {
		t.Fatalf("Write failed: %v", err)
	}
	stubLinkFailure(t, syscall.EMLINK)

	if hit, err := s.HardlinkTo(hash, filepath.Join(t.TempDir(), "out.bin")); err != nil || !hit {
		t.Fatalf("HardlinkTo: hit=%v err=%v, want hit=true err=nil", hit, err)
	}

	got := s.Stats()
	if got.CopyFallbackEMLINK != 1 {
		t.Errorf("CopyFallbackEMLINK = %d, want 1", got.CopyFallbackEMLINK)
	}
	// A copy fallback still satisfied the caller, so it counts as a hit.
	if got.HardlinkHits != 1 {
		t.Errorf("HardlinkHits = %d, want 1; a copy fallback still serves the blob", got.HardlinkHits)
	}
}

func TestSummaryOmitsZeroCounters(t *testing.T) {
	clean := Stats{HardlinkHits: 900, HardlinkMisses: 100, Ingested: 100}
	got := clean.Summary()

	if strings.Contains(got, "copy fallback") {
		t.Errorf("Summary of a clean run mentions copy fallbacks, want them omitted:\n%s", got)
	}
	if !strings.Contains(got, "900 hits") {
		t.Errorf("Summary is missing the hit count:\n%s", got)
	}
}

func TestSummaryReportsCopyFallbacks(t *testing.T) {
	s := Stats{HardlinkHits: 10, CopyFallbackEMLINK: 7, CopyFallbackEXDEV: 3}
	got := s.Summary()

	for _, want := range []string{"10 copy fallbacks", "EMLINK 7", "EXDEV 3"} {
		if !strings.Contains(got, want) {
			t.Errorf("Summary is missing %q:\n%s", want, got)
		}
	}
}

func TestCopyFallbacksTotal(t *testing.T) {
	s := Stats{CopyFallbackEMLINK: 3, CopyFallbackEXDEV: 4, CopyFallbackOther: 5}
	if got := s.CopyFallbacks(); got != 12 {
		t.Errorf("CopyFallbacks() = %d, want 12", got)
	}
}
