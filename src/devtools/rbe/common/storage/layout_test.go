package storage

import (
	"context"
	"fmt"
	"math"
	"math/rand"
	"os"
	"path/filepath"
	"regexp"
	"runtime"
	"slices"
	"sort"
	"sync"
	"testing"
	"time"
)

const sampleSHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

func TestShardedLayout65536_BlobPath(t *testing.T) {
	for _, tc := range []struct {
		hash, want string
	}{
		{sampleSHA256, filepath.Join("e3", "b0", sampleSHA256)},
		// Short inputs fall back to a flat name rather than panicking.
		{"abc", "abc"},
		{"", ""},
	} {
		if got := ShardedLayout65536.BlobPath(tc.hash); got != tc.want {
			t.Errorf("ShardedLayout65536.BlobPath(%q) = %q, want %q", tc.hash, got, tc.want)
		}
	}
}

func TestLayout256_BlobPath(t *testing.T) {
	for _, tc := range []struct {
		hash, want string
	}{
		{sampleSHA256, filepath.Join("e3", sampleSHA256)},
		{"ab", filepath.Join("ab", "ab")},
		{"a", "a"},
	} {
		if got := Layout256.BlobPath(tc.hash); got != tc.want {
			t.Errorf("Layout256.BlobPath(%q) = %q, want %q", tc.hash, got, tc.want)
		}
	}
}

// allBuckets returns every bucket of l, in index order.
func allBuckets(l Layout) []string {
	buckets := make([]string, l.NumBuckets())
	for i := range buckets {
		buckets[i] = l.Bucket(i)
	}
	return buckets
}

// TestLayouts_Buckets checks that each layout enumerates exactly the leaf
// directories its BlobPath writes into: every bucket once, nothing else. A
// bucket missing from the list is a directory the evictor never visits, so its
// blobs could never be reclaimed.
func TestLayouts_Buckets(t *testing.T) {
	rng := rand.New(rand.NewSource(1))
	for _, tc := range []struct {
		name   string
		layout Layout
		count  int
		format *regexp.Regexp
	}{
		{"ShardedLayout65536", ShardedLayout65536, 65536, regexp.MustCompile(`^[0-9a-f]{2}/[0-9a-f]{2}$`)},
		{"Layout256", Layout256, 256, regexp.MustCompile(`^[0-9a-f]{2}$`)},
	} {
		t.Run(tc.name, func(t *testing.T) {
			if got := tc.layout.NumBuckets(); got != tc.count {
				t.Fatalf("NumBuckets() = %d, want %d", got, tc.count)
			}
			buckets := allBuckets(tc.layout)
			seen := make(map[string]bool, len(buckets))
			for _, b := range buckets {
				if !tc.format.MatchString(filepath.ToSlash(b)) {
					t.Errorf("Bucket %q does not match %v", b, tc.format)
				}
				if seen[b] {
					t.Errorf("Bucket %q is listed twice", b)
				}
				seen[b] = true
			}
			for i := 0; i < 1000; i++ {
				hash := fmt.Sprintf("%016x%016x%016x%016x", rng.Uint64(), rng.Uint64(), rng.Uint64(), rng.Uint64())
				if dir := filepath.Dir(tc.layout.BlobPath(hash)); !seen[dir] {
					t.Fatalf("BlobPath(%q) is in %q, which no Bucket(i) names", hash, dir)
				}
			}
		})
	}
}

// TestLayouts_BucketIsTheLexicalEnumeration pins the name and order of every
// bucket to the list the layouts used to build: each level's 256 bytes as
// two hex digits, the first level outermost, in lexical order.
func TestLayouts_BucketIsTheLexicalEnumeration(t *testing.T) {
	hexBytes := make([]string, 256)
	for b := range hexBytes {
		hexBytes[b] = fmt.Sprintf("%02x", b)
	}
	var twoLevels []string
	for _, a := range hexBytes {
		for _, b := range hexBytes {
			twoLevels = append(twoLevels, filepath.Join(a, b))
		}
	}
	for _, tc := range []struct {
		layout Layout
		want   []string
	}{
		{Layout256, hexBytes},
		{ShardedLayout65536, twoLevels},
	} {
		got := allBuckets(tc.layout)
		if len(got) != len(tc.want) {
			t.Errorf("%v has %d buckets, want %d", tc.layout, len(got), len(tc.want))
			continue
		}
		for i := range got {
			if got[i] != tc.want[i] {
				t.Errorf("%v.Bucket(%d) = %q, want %q", tc.layout, i, got[i], tc.want[i])
				break
			}
		}
	}
}

func TestShardedLayout_BucketPanicsOutOfRange(t *testing.T) {
	for _, tc := range []struct {
		layout Layout
		i      int
	}{
		{Layout256, -1},
		{Layout256, 256},
		{ShardedLayout65536, 65536},
	} {
		func() {
			defer func() {
				if recover() == nil {
					t.Errorf("%v.Bucket(%d) did not panic", tc.layout, tc.i)
				}
			}()
			tc.layout.Bucket(tc.i)
		}()
	}
}

// TestStorage_DefaultLayoutIsUnchanged pins casproxy's on-disk layout, which
// existing caches depend on.
func TestStorage_DefaultLayoutIsUnchanged(t *testing.T) {
	s := setupTestStorage(t)
	if err := s.Write(sampleSHA256, []byte("x")); err != nil {
		t.Fatalf("Write failed: %v", err)
	}
	if _, err := os.Stat(filepath.Join(s.RootDir(), "e3", "b0", sampleSHA256)); err != nil {
		t.Errorf("Blob is not at <root>/e3/b0/<hash>: %v", err)
	}
}

func TestStorage_WithLayout(t *testing.T) {
	s, err := New(t.TempDir(), WithLayout(Layout256))
	if err != nil {
		t.Fatalf("New failed: %v", err)
	}
	if err := s.Write(sampleSHA256, []byte("payload")); err != nil {
		t.Fatalf("Write failed: %v", err)
	}
	if _, err := os.Stat(filepath.Join(s.RootDir(), "e3", sampleSHA256)); err != nil {
		t.Errorf("Blob is not at <root>/e3/<hash>: %v", err)
	}
	if !s.Has(sampleSHA256) {
		t.Errorf("Has(%q) = false after Write", sampleSHA256)
	}
	if got, err := s.Read(sampleSHA256); err != nil || string(got) != "payload" {
		t.Errorf("Read(%q) = (%q, %v), want (\"payload\", nil)", sampleSHA256, got, err)
	}
}

func TestStorage_WithNilLayoutKeepsTheDefault(t *testing.T) {
	s, err := New(t.TempDir(), WithLayout(nil))
	if err != nil {
		t.Fatalf("New failed: %v", err)
	}
	if s.layout != ShardedLayout65536 {
		t.Errorf("layout = %v, want ShardedLayout65536", s.layout)
	}
}

// TestEvictor_AdoptsTheStorageLayout checks every way an evictor is attached
// to a Storage. An evictor walking a different layout from the one the
// storage writes would never find a blob to evict.
func TestEvictor_AdoptsTheStorageLayout(t *testing.T) {
	cfg := oneshotTestConfig()
	for _, tc := range []struct {
		name   string
		attach func(*testing.T, *Storage) error
	}{
		{"ConfigureEvictor", func(_ *testing.T, s *Storage) error { return s.ConfigureEvictor(cfg) }},
		{"SetEvictor", func(_ *testing.T, s *Storage) error {
			ev, err := NewEvictor(s.RootDir(), cfg, nil)
			if err != nil {
				return err
			}
			s.SetEvictor(ev)
			return nil
		}},
		{"StartEvictor", func(t *testing.T, s *Storage) error {
			if err := s.StartEvictor(context.Background(), cfg); err != nil {
				return err
			}
			t.Cleanup(s.StopEvictor)
			return nil
		}},
	} {
		t.Run(tc.name, func(t *testing.T) {
			s, err := New(t.TempDir(), WithLayout(Layout256))
			if err != nil {
				t.Fatalf("New failed: %v", err)
			}
			if err := tc.attach(t, s); err != nil {
				t.Fatalf("Attaching the evictor failed: %v", err)
			}
			if got := s.Evictor().bucketLayout(); got != Layout256 {
				t.Errorf("Evictor layout = %v, want Layout256", got)
			}
		})
	}

	ev, err := NewEvictor(t.TempDir(), cfg, nil)
	if err != nil {
		t.Fatalf("NewEvictor failed: %v", err)
	}
	if got := ev.bucketLayout(); got != ShardedLayout65536 {
		t.Errorf("Unattached evictor layout = %v, want ShardedLayout65536", got)
	}
}

// fakeLayout is a Layout with an arbitrary, small set of buckets, to show that
// the evictor relies on the interface alone and not on any naming scheme.
type fakeLayout struct{ buckets []string }

func (l fakeLayout) BlobPath(hash string) string {
	return filepath.Join(l.buckets[int(hash[0])%len(l.buckets)], hash)
}

func (l fakeLayout) NumBuckets() int { return len(l.buckets) }

func (l fakeLayout) Bucket(i int) string { return l.buckets[i] }

func TestEvictor_PickRandomBuckets_ThroughTheLayout(t *testing.T) {
	root := t.TempDir()
	for _, tc := range []struct {
		name   string
		layout Layout
		k      int
		want   int
	}{
		{"Layout256", Layout256, 16, 16},
		{"Layout256 asked for more than it has", Layout256, 1000, 256},
		{"ShardedLayout65536", ShardedLayout65536, 64, 64},
		{"fake", fakeLayout{[]string{"x/1", "x/2", "y"}}, 16, 3},
		{"none asked for", Layout256, 0, 0},
	} {
		t.Run(tc.name, func(t *testing.T) {
			ev := &Evictor{rootDir: root, layout: tc.layout, rand: rand.New(rand.NewSource(7))}
			valid := make(map[string]bool)
			for _, b := range allBuckets(tc.layout) {
				valid[filepath.Join(root, b)] = true
			}

			got := ev.pickRandomBuckets(tc.k)
			if len(got) != tc.want {
				t.Fatalf("pickRandomBuckets(%d) returned %d buckets, want %d", tc.k, len(got), tc.want)
			}
			seen := make(map[string]bool)
			for _, p := range got {
				if !valid[p] {
					t.Errorf("pickRandomBuckets returned %q, which is not one of the layout's buckets", p)
				}
				if seen[p] {
					t.Errorf("pickRandomBuckets returned %q twice", p)
				}
				seen[p] = true
			}
		})
	}
}

// TestEvictor_PickRandomBuckets_MatchesAFullShuffle pins the sampling
// distribution: from the same random source, the sample is exactly the one a
// partial Fisher-Yates shuffle of the whole bucket list draws.
func TestEvictor_PickRandomBuckets_MatchesAFullShuffle(t *testing.T) {
	for _, tc := range []struct {
		layout Layout
		k      int
	}{
		{Layout256, 16},
		{Layout256, 256},
		{ShardedLayout65536, 64},
	} {
		const seed = 11
		buckets := allBuckets(tc.layout)
		rng := rand.New(rand.NewSource(seed))
		for i := 0; i < tc.k; i++ {
			j := i + rng.Intn(len(buckets)-i)
			buckets[i], buckets[j] = buckets[j], buckets[i]
		}
		var want []string
		for _, b := range buckets[:tc.k] {
			want = append(want, filepath.Join("/cache", b))
		}

		ev := &Evictor{rootDir: "/cache", layout: tc.layout, rand: rand.New(rand.NewSource(seed))}
		if got := ev.pickRandomBuckets(tc.k); !slices.Equal(got, want) {
			t.Errorf("%v: pickRandomBuckets(%d) = %q, want %q", tc.layout, tc.k, got, want)
		}
	}
}

func TestEvictor_PickRandomBuckets_IsUniform(t *testing.T) {
	ev := &Evictor{layout: Layout256, rand: rand.New(rand.NewSource(3))}
	hits := make(map[string]int)
	const rounds = 4000
	for i := 0; i < rounds; i++ {
		for _, p := range ev.pickRandomBuckets(16) {
			hits[p]++
		}
	}
	// Each bucket is expected rounds*16/256 = 250 times. A biased sampler,
	// such as one that favors low indexes, lands far outside this band.
	for _, b := range allBuckets(Layout256) {
		if n := hits[b]; n < 175 || n > 325 {
			t.Errorf("Bucket %q sampled %d times, want about 250", b, n)
		}
	}
}

// TestEvictor_PickRandomBuckets_AllocatesForTheSampleOnly checks that sampling
// a few of 65,536 buckets costs memory in proportion to the sample, not the
// layout. Merely listing every bucket name takes a 16-byte string header per
// bucket, 1 MiB in all.
func TestEvictor_PickRandomBuckets_AllocatesForTheSampleOnly(t *testing.T) {
	ev := &Evictor{rootDir: "/cache", layout: ShardedLayout65536, rand: rand.New(rand.NewSource(1))}
	defer runtime.GOMAXPROCS(runtime.GOMAXPROCS(1))
	const runs = 20
	var before, after runtime.MemStats
	runtime.ReadMemStats(&before)
	for range runs {
		ev.pickRandomBuckets(16)
	}
	runtime.ReadMemStats(&after)
	if perRun := (after.TotalAlloc - before.TotalAlloc) / runs; perRun > 64<<10 {
		t.Errorf("pickRandomBuckets(16) allocated %d bytes per call, want a small fraction of the 1 MiB that listing every bucket takes", perRun)
	}
}

func TestEvictor_BucketPermutation_VisitsEveryBucketOnce(t *testing.T) {
	for _, n := range []int{0, 1, 3, 256, 65536} {
		ev := &Evictor{rand: rand.New(rand.NewSource(5))}
		perm := ev.bucketPermutation(n)
		if n >= 256 && slices.IsSorted(perm) {
			t.Errorf("bucketPermutation(%d) is in index order, want it shuffled", n)
		}
		sorted := slices.Clone(perm)
		slices.Sort(sorted)
		if len(sorted) != n {
			t.Errorf("bucketPermutation(%d) has %d entries, want %d", n, len(sorted), n)
			continue
		}
		for i, b := range sorted {
			if b != uint32(i) {
				t.Errorf("bucketPermutation(%d) does not list every index in [0, %d) exactly once", n, n)
				break
			}
		}
	}
}

// recordingLayout records the index of every bucket the evictor names.
type recordingLayout struct {
	Layout
	mu    sync.Mutex
	named []int
}

func (l *recordingLayout) Bucket(i int) string {
	l.mu.Lock()
	l.named = append(l.named, i)
	l.mu.Unlock()
	return l.Layout.Bucket(i)
}

// TestEvictStream_VisitsEveryBucketOnce checks the full eviction walk: with a
// target it never reaches, it visits every bucket of the layout exactly once,
// in shuffled order.
func TestEvictStream_VisitsEveryBucketOnce(t *testing.T) {
	for _, base := range []Layout{Layout256, ShardedLayout65536} {
		t.Run(fmt.Sprint(base), func(t *testing.T) {
			layout := &recordingLayout{Layout: base}
			ev, err := NewEvictor(t.TempDir(), oneshotTestConfig(), fixedDisk(1000, 0))
			if err != nil {
				t.Fatalf("NewEvictor failed: %v", err)
			}
			ev.layout = layout

			f := ev.newFlight(ev.snapshot())
			if _, _, err := f.evictStream(context.Background(), time.Now(), math.MaxInt64, false); err != nil {
				t.Fatalf("evictStream failed: %v", err)
			}
			if slices.IsSorted(layout.named) {
				t.Errorf("The walk visited buckets in index order, want a shuffled order")
			}
			visited := slices.Clone(layout.named)
			slices.Sort(visited)
			if len(visited) != base.NumBuckets() {
				t.Fatalf("The walk visited %d buckets, want %d", len(visited), base.NumBuckets())
			}
			for i, b := range visited {
				if b != i {
					t.Fatalf("The walk did not visit every bucket exactly once: bucket %d is missing or repeated", i)
				}
			}
		})
	}
}

// TestEvictor_WalksOnlyTheLayoutsBuckets runs a whole flight against a layout
// with unconventional bucket names. Every stale blob in those buckets goes;
// a stale blob in a directory the layout does not list is never seen.
func TestEvictor_WalksOnlyTheLayoutsBuckets(t *testing.T) {
	layout := fakeLayout{[]string{filepath.Join("x", "1"), filepath.Join("x", "2")}}
	s, err := New(t.TempDir(), WithLayout(layout))
	if err != nil {
		t.Fatalf("New failed: %v", err)
	}
	stale := time.Now().Add(-48 * time.Hour)
	var hashes []string
	for i := 0; i < 20; i++ {
		hash := fmt.Sprintf("%02x%062x", i, i)
		if err := s.Write(hash, []byte("stale")); err != nil {
			t.Fatalf("Write failed: %v", err)
		}
		if err := os.Chtimes(s.blobPath(hash), stale, stale); err != nil {
			t.Fatalf("Chtimes failed: %v", err)
		}
		hashes = append(hashes, hash)
	}
	outside := filepath.Join(s.RootDir(), "y", sampleSHA256)
	if err := os.MkdirAll(filepath.Dir(outside), 0755); err != nil {
		t.Fatalf("MkdirAll failed: %v", err)
	}
	if err := os.WriteFile(outside, []byte("stale"), 0644); err != nil {
		t.Fatalf("WriteFile failed: %v", err)
	}
	if err := os.Chtimes(outside, stale, stale); err != nil {
		t.Fatalf("Chtimes failed: %v", err)
	}

	// Free space never reaches the target, so the flight walks every bucket.
	ev, err := NewEvictor(s.RootDir(), oneshotTestConfig(), func(string) (DiskStats, error) {
		return DiskStats{TotalBytes: 1000, FreeBytes: 0}, nil
	})
	if err != nil {
		t.Fatalf("NewEvictor failed: %v", err)
	}
	s.SetEvictor(ev)

	if _, deleted, err := ev.EvictOnce(context.Background()); err != nil || deleted != len(hashes) {
		t.Errorf("EvictOnce() = (%d deleted, %v), want (%d, nil)", deleted, err, len(hashes))
	}
	for _, h := range hashes {
		if s.Has(h) {
			t.Errorf("Blob %s in a layout bucket survived the flight", h)
		}
	}
	if _, err := os.Stat(outside); err != nil {
		t.Errorf("Blob outside the layout's buckets was touched: %v", err)
	}
}

// fixtureBlob is one blob of the realistic eviction fixture.
type fixtureBlob struct {
	hash string
	size int64
	age  time.Duration
}

// realisticFixture returns n blobs whose sizes follow the heavy tail measured
// on test hosts (b/572891754): 94% at most 1 MiB, 0.1% between 64 and 512 MiB,
// the rest in between. Ages are spread evenly over two weeks.
func realisticFixture(n int, rng *rand.Rand) []fixtureBlob {
	logUniform := func(lo, hi float64) int64 {
		return int64(math.Exp(math.Log(lo) + rng.Float64()*(math.Log(hi)-math.Log(lo))))
	}
	const mib = 1 << 20
	blobs := make([]fixtureBlob, n)
	for i := range blobs {
		var size int64
		switch r := rng.Float64(); {
		case r < 0.94:
			size = 4096 + rng.Int63n(mib-4096)
		case r < 0.999:
			size = logUniform(mib, 64*mib)
		default:
			size = logUniform(64*mib, 512*mib)
		}
		blobs[i] = fixtureBlob{
			hash: fmt.Sprintf("%016x%016x%016x%016x", rng.Uint64(), rng.Uint64(), rng.Uint64(), rng.Uint64()),
			size: size,
			age:  time.Hour + time.Duration(rng.Int63n(int64(14*24*time.Hour))),
		}
	}
	return blobs
}

// TestCheckAndEvict_SampledCutoffAt256BucketsIsRoughlyOldestFirst runs a
// one-shot pass over a casdownloader-sized cache: 50k blobs with heavy-tailed
// sizes in the 256-bucket layout, sampling 64 buckets, with free space below
// the 5% emergency threshold and EmergencyLargeBlobFirst off.
//
// Sampled size estimates are noisy with a tail this heavy, so the pass is only
// expected to be approximately right: reclaim roughly what is needed, mostly
// from the oldest blobs, and nothing that an oldest-first eviction would keep
// for long.
func TestCheckAndEvict_SampledCutoffAt256BucketsIsRoughlyOldestFirst(t *testing.T) {
	if testing.Short() {
		t.Skip("writes 50k files")
	}
	blobs := realisticFixture(50000, rand.New(rand.NewSource(572891754)))

	s, err := New(t.TempDir(), WithLayout(Layout256))
	if err != nil {
		t.Fatalf("New failed: %v", err)
	}
	for _, b := range allBuckets(Layout256) {
		if err := os.MkdirAll(filepath.Join(s.RootDir(), b), 0755); err != nil {
			t.Fatalf("MkdirAll failed: %v", err)
		}
	}
	now := time.Now()
	var used int64
	for _, b := range blobs {
		// Sparse files: the evictor sees the size without the test writing
		// tens of gigabytes.
		p := s.blobPath(b.hash)
		f, err := os.Create(p)
		if err != nil {
			t.Fatalf("Create failed: %v", err)
		}
		if err := f.Truncate(b.size); err != nil {
			f.Close()
			t.Fatalf("Truncate failed: %v", err)
		}
		f.Close()
		mtime := now.Add(-b.age)
		if err := os.Chtimes(p, mtime, mtime); err != nil {
			t.Fatalf("Chtimes failed: %v", err)
		}
		used += b.size
	}

	// 4% free: below MinFreeSpace and below the emergency threshold.
	total := int64(float64(used) / 0.96)
	model := &diskModel{root: s.RootDir(), totalBytes: total}
	cfg := oneshotTestConfig()
	cfg.SampleBuckets = 64
	cfg.BatchSize = 2000
	cfg.EmergencyLargeBlobFirst = false
	ev, err := NewEvictor(s.RootDir(), cfg, model.statfs)
	if err != nil {
		t.Fatalf("NewEvictor failed: %v", err)
	}
	ev.rand = rand.New(rand.NewSource(1))
	s.SetEvictor(ev)

	stats, err := model.statfs("")
	if err != nil {
		t.Fatalf("statfs failed: %v", err)
	}
	snap := ev.snapshot(stats)
	need := snap.targetFreeBytes - stats.FreeBytes

	// The age above which an exact oldest-first eviction would delete
	// everything to reclaim need bytes.
	byAge := slices.Clone(blobs)
	sort.Slice(byAge, func(i, j int) bool { return byAge[i].age > byAge[j].age })
	var idealAge time.Duration
	var acc int64
	for _, b := range byAge {
		acc += b.size
		if acc >= need {
			idealAge = b.age
			break
		}
	}

	res, err := s.CheckAndEvict(context.Background())
	if err != nil {
		t.Fatalf("CheckAndEvict failed: %v", err)
	}
	if res.Outcome != TrimOutcomeEvicted {
		t.Fatalf("Outcome = %q, want %q", res.Outcome, TrimOutcomeEvicted)
	}
	t.Logf("need %d bytes (ideal age cutoff %v); reclaimed %d bytes across %d files", need, idealAge.Round(time.Hour), res.BytesReclaimed, res.FilesDeleted)

	if res.BytesReclaimed < need/2 || res.BytesReclaimed > 2*need {
		t.Errorf("Reclaimed %d bytes, want roughly the %d needed (within a factor of 2)", res.BytesReclaimed, need)
	}

	var evictedBytes, oldBytes, oldEvictedBytes int64
	var youngestEvicted time.Duration = math.MaxInt64
	for _, b := range blobs {
		_, err := os.Stat(s.blobPath(b.hash))
		evicted := os.IsNotExist(err)
		if evicted {
			evictedBytes += b.size
			youngestEvicted = min(youngestEvicted, b.age)
		}
		if b.age >= idealAge {
			oldBytes += b.size
			if evicted {
				oldEvictedBytes += b.size
			}
		}
	}
	if evictedBytes != res.BytesReclaimed {
		t.Errorf("BytesReclaimed = %d, but %d bytes of blobs disappeared", res.BytesReclaimed, evictedBytes)
	}
	// Large young blobs are exactly what emergency mode would have taken;
	// with it off, nothing much younger than the ideal cutoff may go.
	if youngestEvicted < idealAge/2 {
		t.Errorf("Evicted a blob %v old, but oldest-first eviction stops at %v", youngestEvicted.Round(time.Minute), idealAge.Round(time.Minute))
	}
	if oldEvictedBytes*2 < oldBytes {
		t.Errorf("Evicted %d of the %d bytes older than %v, want most of them", oldEvictedBytes, oldBytes, idealAge.Round(time.Hour))
	}
}
