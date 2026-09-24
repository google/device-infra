package download

import (
	"testing"

	"google.golang.org/grpc/metadata"
)

// TestProxyHitTracker_Record pins how casproxy's trailers are read.
//
// Anything malformed must leave the totals alone rather than guess: a trailer
// that inflated proxy_hot would make casproxy look better than it is, and one
// that recorded a non-positive count would unbalance the tier partition.
func TestProxyHitTracker_Record(t *testing.T) {
	tests := []struct {
		name      string
		trailer   metadata.MD
		wantBytes int64
		wantCount int
	}{
		{
			name:    "no trailers, as on a casproxy miss or from CAS remote",
			trailer: metadata.MD{},
		},
		{
			name:    "bytes not a number",
			trailer: metadata.Pairs(TrailerProxyHitBytes, "lots"),
		},
		{
			name:    "zero bytes",
			trailer: metadata.Pairs(TrailerProxyHitBytes, "0"),
		},
		{
			name:    "negative bytes",
			trailer: metadata.Pairs(TrailerProxyHitBytes, "-5"),
		},
		{
			name:      "bytes without a count is one blob",
			trailer:   metadata.Pairs(TrailerProxyHitBytes, "100"),
			wantBytes: 100,
			wantCount: 1,
		},
		{
			name:      "bytes with a count",
			trailer:   metadata.Pairs(TrailerProxyHitBytes, "100", TrailerProxyHitCount, "3"),
			wantBytes: 100,
			wantCount: 3,
		},
		{
			name:      "count not a number falls back to one blob",
			trailer:   metadata.Pairs(TrailerProxyHitBytes, "100", TrailerProxyHitCount, "several"),
			wantBytes: 100,
			wantCount: 1,
		},
		{
			name:      "zero count falls back to one blob",
			trailer:   metadata.Pairs(TrailerProxyHitBytes, "100", TrailerProxyHitCount, "0"),
			wantBytes: 100,
			wantCount: 1,
		},
		{
			name:      "negative count falls back to one blob",
			trailer:   metadata.Pairs(TrailerProxyHitBytes, "100", TrailerProxyHitCount, "-2"),
			wantBytes: 100,
			wantCount: 1,
		},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			tracker := NewProxyHitTracker()
			tracker.record(tc.trailer)
			if got := tracker.HitBytes(); got != tc.wantBytes {
				t.Errorf("HitBytes() = %d, want %d", got, tc.wantBytes)
			}
			if got := tracker.HitCount(); got != tc.wantCount {
				t.Errorf("HitCount() = %d, want %d", got, tc.wantCount)
			}
		})
	}
}

// TestProxyHitTracker_NilIsInert covers a job built without a tracker, which
// is every job not pointed at a casproxy. Recording into it must not panic.
func TestProxyHitTracker_NilIsInert(t *testing.T) {
	var tracker *ProxyHitTracker
	tracker.record(metadata.Pairs(TrailerProxyHitBytes, "100"))
	if got := tracker.HitBytes(); got != 0 {
		t.Errorf("HitBytes() on a nil tracker = %d, want 0", got)
	}
}
