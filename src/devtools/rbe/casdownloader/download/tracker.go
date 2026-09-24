package download

import (
	"context"
	"io"
	"strconv"
	"sync"

	"google.golang.org/grpc"
	"google.golang.org/grpc/metadata"
)

// TrailerProxyHitBytes is the gRPC response trailer key for the number of bytes
// casproxy served out of its own disk cache.
const TrailerProxyHitBytes = "x-cas-proxy-hit-bytes"

// TrailerProxyHitCount is the gRPC response trailer key for the number of blobs
// casproxy served out of its own disk cache.
const TrailerProxyHitCount = "x-cas-proxy-hit-count"

// ProxyHitTracker accumulates what casproxy reported serving from its own disk
// cache, which it attaches to responses as trailers. Bytes a casproxy did not
// have came from CAS remote, so what this tracker does not see is what the
// download cost in WAN traffic.
//
// A client talking straight to CAS remote simply never sees the trailers and
// leaves the tracker at zero.
type ProxyHitTracker struct {
	mu       sync.Mutex
	hitBytes int64
	hitCount int
}

// NewProxyHitTracker creates a new ProxyHitTracker instance.
func NewProxyHitTracker() *ProxyHitTracker {
	return &ProxyHitTracker{}
}

// AddHit records bytes and blobs served from casproxy's disk cache.
func (t *ProxyHitTracker) AddHit(bytes int64, count int) {
	if t == nil {
		return
	}
	t.mu.Lock()
	defer t.mu.Unlock()
	t.hitBytes += bytes
	t.hitCount += count
}

// Reset resets all tracked bytes and counts to zero. It must be called when a
// download is restarted against a different endpoint, so that hits recorded
// against a casproxy are not attributed to a direct CAS remote connection.
func (t *ProxyHitTracker) Reset() {
	if t == nil {
		return
	}
	t.mu.Lock()
	defer t.mu.Unlock()
	t.hitBytes = 0
	t.hitCount = 0
}

// HitBytes returns the total bytes casproxy served from its disk cache.
func (t *ProxyHitTracker) HitBytes() int64 {
	if t == nil {
		return 0
	}
	t.mu.Lock()
	defer t.mu.Unlock()
	return t.hitBytes
}

// HitCount returns the total blobs casproxy served from its disk cache.
func (t *ProxyHitTracker) HitCount() int {
	if t == nil {
		return 0
	}
	t.mu.Lock()
	defer t.mu.Unlock()
	return t.hitCount
}

// record parses a set of response trailers and accumulates whatever casproxy
// reported. Trailers are absent on a cache miss and on any server that does not
// speak them, both of which correctly leave the totals untouched.
func (t *ProxyHitTracker) record(trailer metadata.MD) {
	if t == nil {
		return
	}
	vals := trailer.Get(TrailerProxyHitBytes)
	if len(vals) == 0 {
		return
	}
	hitBytes, err := strconv.ParseInt(vals[0], 10, 64)
	if err != nil || hitBytes <= 0 {
		return
	}
	hitCount := 1
	if countVals := trailer.Get(TrailerProxyHitCount); len(countVals) > 0 {
		if c, err := strconv.Atoi(countVals[0]); err == nil && c > 0 {
			hitCount = c
		}
	}
	t.AddHit(hitBytes, hitCount)
}

// StreamInterceptor returns a grpc.StreamClientInterceptor that captures the
// casproxy hit trailers on streaming RPCs (e.g. ByteStream.Read).
func (t *ProxyHitTracker) StreamInterceptor() grpc.StreamClientInterceptor {
	return func(ctx context.Context, desc *grpc.StreamDesc, cc *grpc.ClientConn, method string, streamer grpc.Streamer, opts ...grpc.CallOption) (grpc.ClientStream, error) {
		var trailer metadata.MD
		opts = append(opts, grpc.Trailer(&trailer))
		cs, err := streamer(ctx, desc, cc, method, opts...)
		if err != nil {
			return cs, err
		}
		return &trackedClientStream{
			ClientStream: cs,
			trailer:      &trailer,
			tracker:      t,
		}, nil
	}
}

type trackedClientStream struct {
	grpc.ClientStream
	trailer *metadata.MD
	tracker *ProxyHitTracker
}

func (s *trackedClientStream) RecvMsg(m any) error {
	err := s.ClientStream.RecvMsg(m)
	if err == io.EOF {
		if s.trailer != nil && s.tracker != nil {
			s.tracker.record(*s.trailer)
		}
		// Clearing the trailer keeps a second RecvMsg from counting the same
		// stream twice.
		s.trailer = nil
	}
	return err
}

// UnaryInterceptor returns a grpc.UnaryClientInterceptor that captures the
// casproxy hit trailers on unary RPCs (e.g. CAS.BatchReadBlobs).
func (t *ProxyHitTracker) UnaryInterceptor() grpc.UnaryClientInterceptor {
	return func(ctx context.Context, method string, req, reply any, cc *grpc.ClientConn, invoker grpc.UnaryInvoker, opts ...grpc.CallOption) error {
		var trailer metadata.MD
		opts = append(opts, grpc.Trailer(&trailer))
		err := invoker(ctx, method, req, reply, cc, opts...)
		if err == nil {
			t.record(trailer)
		}
		return err
	}
}
