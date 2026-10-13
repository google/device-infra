package storage

import (
	"context"
	"fmt"
	"os"
	"path/filepath"
	"testing"
	"time"
)

// progressiveTestBlobSize is a multiple of 10 and of 9, so that 90% of a cap
// made of whole blobs is a whole number of blobs.
const progressiveTestBlobSize = 9000

// newProgressiveStorage builds an empty 256-bucket Storage with an evictor
// that sees statfs and reads at least 64 buckets before evicting, as
// casdownloader's does.
func newProgressiveStorage(t *testing.T, statfs StatfsFunc) *Storage {
	t.Helper()
	s, err := New(t.TempDir(), WithLayout(Layout256))
	if err != nil {
		t.Fatalf("New failed: %v", err)
	}
	cfg := oneshotTestConfig()
	cfg.SampleBuckets = 64
	ev, err := NewEvictor(s.RootDir(), cfg, statfs)
	if err != nil {
		t.Fatalf("Failed to create the evictor: %v", err)
	}
	s.SetEvictor(ev)
	return s
}

// seedGroup writes n blobs of size bytes, aged age, and returns their hashes.
// The first byte of the hash is i%256, so that n blobs, for n a multiple of
// 256, put the same number in every bucket, and any k buckets estimate the
// cache's size exactly. group goes into the hash, so that groups do not
// collide.
func seedGroup(t *testing.T, s *Storage, group, n, size int, age time.Duration) []string {
	t.Helper()
	data := make([]byte, size)
	mtime := time.Now().Add(-age)
	hashes := make([]string, n)
	for i := range hashes {
		hashes[i] = fmt.Sprintf("%02x%02x%060x", i%256, group, i)
		if err := s.Write(hashes[i], data); err != nil {
			t.Fatalf("Failed to seed blob %d of group %d: %v", i, group, err)
		}
		if err := os.Chtimes(s.blobPath(hashes[i]), mtime, mtime); err != nil {
			t.Fatalf("Failed to set the mtime of blob %d of group %d: %v", i, group, err)
		}
	}
	return hashes
}

// countPresent returns how many of hashes are still cached.
func countPresent(s *Storage, hashes []string) int {
	n := 0
	for _, h := range hashes {
		if s.Has(h) {
			n++
		}
	}
	return n
}

// capCheckTime returns the mtime of s's cap-check marker, or the zero time if
// there is none.
func capCheckTime(t *testing.T, s *Storage) time.Time {
	t.Helper()
	info, err := os.Stat(filepath.Join(s.RootDir(), CapCheckName))
	if os.IsNotExist(err) {
		return time.Time{}
	}
	if err != nil {
		t.Fatalf("Failed to stat %s: %v", CapCheckName, err)
	}
	return info.ModTime()
}

// TestCheckCap_StopsOnlyWhenClearlyUnderTheCap covers the steps: with every
// bucket holding the same bytes, each step's estimate is exact, so the step
// the check stops at depends only on the size's ratio to the cap. Below the
// cap it stops at the first step whose allowance clears it; at or over the
// cap it never stops early, and its exact size gives size - 0.9 * cap.
func TestCheckCap_StopsOnlyWhenClearlyUnderTheCap(t *testing.T) {
	const size = 512 * progressiveTestBlobSize
	for _, tc := range []struct {
		ratio       float64 // size / cap
		wantBuckets int
	}{
		{ratio: 0.5, wantBuckets: 32},  // At most 80% of the cap.
		{ratio: 0.82, wantBuckets: 64}, // At most 85%.
		{ratio: 0.9, wantBuckets: 128}, // At most 92%.
		{ratio: 0.95, wantBuckets: 256},
		{ratio: 1.0, wantBuckets: 256},
		{ratio: 1.25, wantBuckets: 256},
	} {
		t.Run(fmt.Sprint(tc.ratio), func(t *testing.T) {
			s := newProgressiveStorage(t, fixedDisk(100<<20, 50<<20))
			seedGroup(t, s, 0, 512, progressiveTestBlobSize, 200*time.Hour)
			maxSize := int64(float64(size) / tc.ratio)

			p := s.Evictor().newProgressiveSample(10 * time.Minute)
			excess, err := p.checkCap(context.Background(), maxSize)
			if err != nil {
				t.Fatalf("checkCap failed: %v", err)
			}
			if p.stats.bucketCount != tc.wantBuckets {
				t.Errorf("checkCap read %d buckets, want %d", p.stats.bucketCount, tc.wantBuckets)
			}
			wantExcess := int64(0)
			if tc.wantBuckets == 256 {
				wantExcess = max(size-(maxSize-maxSize/10), 0)
			}
			if excess != wantExcess {
				t.Errorf("checkCap = %d, want %d", excess, wantExcess)
			}
		})
	}
}

// The size the cap is checked against is every byte in the cache: blobs that
// are pinned by other links or still in their grace period take up the
// space all the same, even though eviction cannot take them.
func TestCheckCap_CountsPinnedAndYoungBlobs(t *testing.T) {
	s := newProgressiveStorage(t, fixedDisk(100<<20, 50<<20))
	seedGroup(t, s, 0, 256, progressiveTestBlobSize, 200*time.Hour)
	pinAllBlobs(t, s)
	seedGroup(t, s, 1, 256, progressiveTestBlobSize, 0)
	const size = 512 * progressiveTestBlobSize

	p := s.Evictor().newProgressiveSample(10 * time.Minute)
	excess, err := p.checkCap(context.Background(), size)
	if err != nil {
		t.Fatalf("checkCap failed: %v", err)
	}
	if want := int64(size / 10); excess != want || p.stats.bucketCount != 256 {
		t.Errorf("checkCap = %d after %d buckets, want %d after all 256", excess, p.stats.bucketCount, want)
	}
}

// TestTrimWith_EvictsTheExcessOverNinetyPercentOfTheCap: free space is well
// above the target, so only the cap can make this pass evict. It reads every
// bucket to find the cache over the cap, and the cutoff from that full read
// takes the oldest blobs first.
func TestTrimWith_EvictsTheExcessOverNinetyPercentOfTheCap(t *testing.T) {
	s := newProgressiveStorage(t, fixedDisk(100<<20, 50<<20))
	old := seedGroup(t, s, 0, 256, progressiveTestBlobSize, 200*time.Hour)
	recent := seedGroup(t, s, 1, 256, progressiveTestBlobSize, 50*time.Hour)
	// The cache holds 512 blobs; 90% of the cap is 384 of them.
	const maxSize = 384 * progressiveTestBlobSize * 10 / 9

	res, err := s.TrimWith(context.Background(), TrimOptions{MaxSize: maxSize})
	if err != nil {
		t.Fatalf("TrimWith failed: %v", err)
	}
	if res.Outcome != TrimOutcomeEvicted || res.FilesDeleted != 128 || res.BytesReclaimed != 128*progressiveTestBlobSize {
		t.Errorf("TrimWith = %+v, want %q with 128 files and %d bytes", res, TrimOutcomeEvicted, 128*progressiveTestBlobSize)
	}
	if got := countPresent(s, recent); got != len(recent) {
		t.Errorf("%d of the %d recent blobs remain, want all", got, len(recent))
	}
	if got := countPresent(s, old); got != 128 {
		t.Errorf("%d of the old blobs remain, want 128", got)
	}
	if capCheckTime(t, s).IsZero() {
		t.Errorf("TrimWith with a cap left no %s", CapCheckName)
	}
}

// A cache well under its cap is left alone after the first step, and the
// check is recorded all the same, so that downloads stop asking for one.
func TestTrimWith_RecordsACapCheckThatStopsEarly(t *testing.T) {
	s := newProgressiveStorage(t, fixedDisk(100<<20, 50<<20))
	blobs := seedGroup(t, s, 0, 512, progressiveTestBlobSize, 200*time.Hour)
	// An old marker, as left by the previous check.
	marker := filepath.Join(s.RootDir(), CapCheckName)
	if err := os.WriteFile(marker, nil, 0644); err != nil {
		t.Fatalf("Failed to write %s: %v", CapCheckName, err)
	}
	old := time.Now().Add(-7 * time.Hour)
	if err := os.Chtimes(marker, old, old); err != nil {
		t.Fatalf("Failed to backdate %s: %v", CapCheckName, err)
	}

	before := time.Now().Add(-time.Second)
	res, err := s.TrimWith(context.Background(), TrimOptions{MaxSize: 2 * 512 * progressiveTestBlobSize})
	if err != nil || res.Outcome != TrimOutcomeNoop {
		t.Errorf("TrimWith = %+v, %v; want a noop", res, err)
	}
	if got := countPresent(s, blobs); got != len(blobs) {
		t.Errorf("%d blobs remain, want all %d", got, len(blobs))
	}
	if got := capCheckTime(t, s); got.Before(before) {
		t.Errorf("%s mtime = %v, want it refreshed by this pass (after %v)", CapCheckName, got, before)
	}
}

// Only a pass with a cap checks it, and only a pass that checks it touches the
// marker.
func TestTrimWith_LeavesTheMarkerAloneWithoutACap(t *testing.T) {
	s := newProgressiveStorage(t, fixedDisk(100<<20, 20<<20))
	seedGroup(t, s, 0, 256, progressiveTestBlobSize, 200*time.Hour)

	if _, err := s.TrimWith(context.Background(), TrimOptions{}); err != nil {
		t.Fatalf("TrimWith failed: %v", err)
	}
	if got := capCheckTime(t, s); !got.IsZero() {
		t.Errorf("TrimWith without a cap wrote %s at %v, want none", CapCheckName, got)
	}
}

// A pass that finds another evictor at work does nothing, cap check
// included: that evictor runs the check.
func TestTrimWith_BusyPassChecksNothing(t *testing.T) {
	s := newProgressiveStorage(t, fixedDisk(100<<20, 50<<20))
	seedGroup(t, s, 0, 256, progressiveTestBlobSize, 200*time.Hour)
	lock, err := tryLockEviction(s.RootDir())
	if err != nil {
		t.Fatalf("Failed to take the lock: %v", err)
	}
	defer lock.release()

	res, err := s.TrimWith(context.Background(), TrimOptions{MaxSize: 1})
	if err != nil || res.Outcome != TrimOutcomeBusy {
		t.Errorf("TrimWith = %+v, %v; want %q", res, err, TrimOutcomeBusy)
	}
	if got := capCheckTime(t, s); !got.IsZero() {
		t.Errorf("A busy pass wrote %s at %v, want none", CapCheckName, got)
	}
}

// A marker that cannot be written costs the pass nothing but a warning: the
// cap is still enforced, and the next download starts another check.
func TestTrimWith_SurvivesAMarkerItCannotWrite(t *testing.T) {
	s := newProgressiveStorage(t, fixedDisk(100<<20, 50<<20))
	seedGroup(t, s, 0, 512, progressiveTestBlobSize, 200*time.Hour)
	if err := os.Mkdir(filepath.Join(s.RootDir(), CapCheckName), 0755); err != nil {
		t.Fatalf("Failed to block %s: %v", CapCheckName, err)
	}

	res, err := s.TrimWith(context.Background(), TrimOptions{MaxSize: 384 * progressiveTestBlobSize * 10 / 9})
	if err != nil || res.FilesDeleted != 128 {
		t.Errorf("TrimWith = %+v, %v; want 128 files", res, err)
	}
}

// TestSampleForTrim_ReadsAtLeastSampleBucketsBeforeEvicting covers what the
// pass has read when the flight starts: whatever started the eviction, at
// least SampleBuckets (64 here), and every bucket the cap check read.
func TestSampleForTrim_ReadsAtLeastSampleBucketsBeforeEvicting(t *testing.T) {
	const total, size = 100 << 20, 512 * progressiveTestBlobSize
	for _, tc := range []struct {
		name        string
		free        int64
		maxSize     int64
		wantBuckets int // Zero for a pass with nothing to evict.
		wantExcess  int64
	}{
		{name: "free space short, no cap", free: 20 << 20, wantBuckets: 64},
		{name: "free space short, well under the cap", free: 20 << 20, maxSize: 2 * size, wantBuckets: 64},
		{name: "free space fine, well under the cap", free: 50 << 20, maxSize: 2 * size, wantBuckets: 0},
		{name: "free space fine, no cap", free: 50 << 20, wantBuckets: 0},
		{name: "free space fine, over the cap", free: 50 << 20, maxSize: size, wantBuckets: 256, wantExcess: size / 10},
	} {
		t.Run(tc.name, func(t *testing.T) {
			s := newProgressiveStorage(t, fixedDisk(total, tc.free))
			seedGroup(t, s, 0, 512, progressiveTestBlobSize, 200*time.Hour)
			e := s.Evictor()

			p, excess, err := e.sampleForTrim(context.Background(), e.snapshot(DiskStats{TotalBytes: total, FreeBytes: tc.free}), tc.free, tc.maxSize)
			if err != nil {
				t.Fatalf("sampleForTrim failed: %v", err)
			}
			gotBuckets := 0
			if p != nil {
				gotBuckets = p.stats.bucketCount
			}
			if gotBuckets != tc.wantBuckets || excess != tc.wantExcess {
				t.Errorf("sampleForTrim read %d buckets for an excess of %d, want %d buckets and %d", gotBuckets, excess, tc.wantBuckets, tc.wantExcess)
			}
		})
	}
}

// TestProgressiveCutoff_TakesTheShareOfTheBucketsRead: with half the buckets
// read, the histogram holds about half of what eviction can reclaim, so the
// cutoff accumulates half of need from the oldest bin.
func TestProgressiveCutoff_TakesTheShareOfTheBucketsRead(t *testing.T) {
	now := time.Date(2026, 10, 1, 12, 0, 0, 0, time.UTC)
	p := &progressiveSample{
		buckets:    make([]string, 256),
		now:        now,
		minBlobAge: 10 * time.Minute,
		stats:      newSampleStats(1),
	}
	p.stats.bucketCount = 128
	p.stats.histogram[10], p.stats.histogram[7], p.stats.histogram[2] = 100, 50, 25
	for _, tc := range []struct {
		need int64
		want time.Time
	}{
		{need: 1, want: now.Add(-10 * time.Hour)},
		{need: 200, want: now.Add(-10 * time.Hour)},
		{need: 201, want: now.Add(-7 * time.Hour)},
		{need: 350, want: now.Add(-2 * time.Hour)},
		// More than the buckets read suggest there is: everything past
		// the grace period.
		{need: 351, want: now.Add(-10 * time.Minute)},
	} {
		if got := p.cutoff(context.Background(), tc.need); !got.Equal(tc.want) {
			t.Errorf("cutoff(%d) = %v, want %v", tc.need, got, tc.want)
		}
	}
}

// The flight takes its cutoff from the buckets the pass already read rather
// than sampling again. The planted histogram says every evictable byte is
// older than any blob on disk, so its cutoff spares them all; a fresh sample
// would have found them and evicted.
func TestTrimFlight_TakesTheCutoffFromTheBucketsAlreadyRead(t *testing.T) {
	s := newProgressiveStorage(t, fixedDisk(100<<20, 20<<20))
	blobs := seedGroup(t, s, 0, 256, progressiveTestBlobSize, 50*time.Hour)
	e := s.Evictor()
	p := e.newProgressiveSample(10 * time.Minute)
	p.stats.bucketCount = len(p.buckets)
	p.stats.histogram[300] = 1 << 30
	p.stats.totalCount = 1

	f := e.newFlight(e.snapshot())
	f.stopAtReclaimTarget = true
	f.progressive = p
	if r := f.execute(context.Background()); r.err != nil || r.deleted != 0 {
		t.Errorf("Flight deleted %d files (err %v), want none: the planted cutoff is older than every blob", r.deleted, r.err)
	}
	if got := countPresent(s, blobs); got != len(blobs) {
		t.Errorf("%d of %d blobs remain, want all", got, len(blobs))
	}
}

// The early exit for an unreachable target applies to the progressive pass
// too: over the cap, but with every blob pinned, there is nothing to do.
func TestTrimWith_ExitsEarlyWhenEverythingIsPinned(t *testing.T) {
	s := newProgressiveStorage(t, fixedDisk(100<<20, 50<<20))
	blobs := seedGroup(t, s, 0, 256, progressiveTestBlobSize, 200*time.Hour)
	pinAllBlobs(t, s)

	res, err := s.TrimWith(context.Background(), TrimOptions{MaxSize: 1})
	if err != nil || res.Outcome != TrimOutcomeNoop {
		t.Errorf("TrimWith = %+v, %v; want a noop", res, err)
	}
	if want := int64(256 * progressiveTestBlobSize); res.SkippedLinked != want {
		t.Errorf("SkippedLinked = %d, want %d", res.SkippedLinked, want)
	}
	if got := countPresent(s, blobs); got != len(blobs) {
		t.Errorf("%d blobs remain, want all %d", got, len(blobs))
	}
}

// A pass that runs out of time while reading buckets is a bounded run, not a
// failure; one cancelled for any other reason is an error. Either way the cap
// check it started is recorded, so that a check that cannot finish within
// -trim-max-duration does not make every download start another evictor.
func TestTrimWith_StopsWhileReadingBuckets(t *testing.T) {
	for _, tc := range []struct {
		name        string
		ctx         func() (context.Context, context.CancelFunc)
		wantOutcome string
		wantErr     bool
	}{
		{
			name: "deadline",
			ctx: func() (context.Context, context.CancelFunc) {
				return context.WithDeadline(context.Background(), time.Now().Add(-time.Second))
			},
			wantOutcome: TrimOutcomeTimedOut,
		},
		{
			name: "cancelled",
			ctx: func() (context.Context, context.CancelFunc) {
				ctx, cancel := context.WithCancel(context.Background())
				cancel()
				return ctx, cancel
			},
			wantOutcome: TrimOutcomeError,
			wantErr:     true,
		},
	} {
		t.Run(tc.name, func(t *testing.T) {
			s := newProgressiveStorage(t, fixedDisk(100<<20, 20<<20))
			blobs := seedGroup(t, s, 0, 256, progressiveTestBlobSize, 200*time.Hour)
			ctx, cancel := tc.ctx()
			defer cancel()

			res, err := s.TrimWith(ctx, TrimOptions{MaxSize: 1})
			if res.Outcome != tc.wantOutcome || (err != nil) != tc.wantErr {
				t.Errorf("TrimWith = %+v, %v; want %q, error: %v", res, err, tc.wantOutcome, tc.wantErr)
			}
			if got := capCheckTime(t, s); got.IsZero() {
				t.Errorf("An unfinished check left no %s, want one written as it started", CapCheckName)
			}
			if got := countPresent(s, blobs); got != len(blobs) {
				t.Errorf("%d of %d blobs remain, want all", got, len(blobs))
			}
		})
	}
	// Without a cap, the pass stops while reading its 64 buckets.
	s := newProgressiveStorage(t, fixedDisk(100<<20, 20<<20))
	seedGroup(t, s, 0, 256, progressiveTestBlobSize, 200*time.Hour)
	ctx, cancel := context.WithDeadline(context.Background(), time.Now().Add(-time.Second))
	defer cancel()
	if res, err := s.TrimWith(ctx, TrimOptions{}); err != nil || res.Outcome != TrimOutcomeTimedOut {
		t.Errorf("TrimWith without a cap = %+v, %v; want %q", res, err, TrimOutcomeTimedOut)
	}
}

func TestEvictionInProgress(t *testing.T) {
	root := t.TempDir()
	if busy, err := EvictionInProgress(root); err != nil || busy {
		t.Fatalf("EvictionInProgress with nobody evicting = %v, %v; want false", busy, err)
	}
	lock, err := tryLockEviction(root)
	if err != nil {
		t.Fatalf("The probe left the lock held: %v", err)
	}
	if busy, err := EvictionInProgress(root); err != nil || !busy {
		t.Errorf("EvictionInProgress with the lock held = %v, %v; want true", busy, err)
	}
	lock.release()
	if busy, err := EvictionInProgress(root); err != nil || busy {
		t.Errorf("EvictionInProgress after release = %v, %v; want false", busy, err)
	}
	// A root that does not exist has no lock to probe.
	if _, err := EvictionInProgress(filepath.Join(root, "missing")); err == nil {
		t.Error("EvictionInProgress of a missing root succeeded, want an error")
	}
}

// A layout with no more buckets than the first step has no step to stop at:
// the check reads every bucket, and its size is exact. An unset age horizon
// gets the default one, as the sampler's does.
func TestCheckCap_ReadsASmallLayoutWhole(t *testing.T) {
	p := &progressiveSample{rootDir: t.TempDir(), stats: newSampleStats(0)}
	for i := range 16 {
		p.buckets = append(p.buckets, fmt.Sprintf("%02x", i))
	}
	if got, want := len(p.stats.histogram), defaultMaxAgeDays*24+1; got != want {
		t.Errorf("len(histogram) = %d, want %d", got, want)
	}

	excess, err := p.checkCap(context.Background(), 1)
	if err != nil || excess != 0 || p.stats.bucketCount != 16 {
		t.Errorf("checkCap = %d, %v after %d buckets; want 0 after all 16", excess, err, p.stats.bucketCount)
	}
}
