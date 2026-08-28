package download

import (
	"context"
	"io"
	"strconv"
	"sync"

	"google.golang.org/grpc"
	"google.golang.org/grpc/metadata"
)

// TrailerWarmBytes is the gRPC response trailer key for the number of bytes served from proxy disk cache.
const TrailerWarmBytes = "x-cas-warm-bytes"

// TrailerWarmCount is the gRPC response trailer key for the number of blobs served from proxy disk cache.
const TrailerWarmCount = "x-cas-warm-count"

// Tracker tracks warm cache statistics (bytes and blob count) received via gRPC response trailers.
type Tracker struct {
	mu        sync.Mutex
	warmBytes int64
	warmCount int
}

// NewTracker creates a new Tracker instance.
func NewTracker() *Tracker {
	return &Tracker{}
}

// AddWarm records warm bytes and increments the warm files/blobs count.
func (t *Tracker) AddWarm(bytes int64, count int) {
	if t == nil {
		return
	}
	t.mu.Lock()
	defer t.mu.Unlock()
	t.warmBytes += bytes
	t.warmCount += count
}

// Reset resets all tracked warm bytes and counts to zero.
func (t *Tracker) Reset() {
	if t == nil {
		return
	}
	t.mu.Lock()
	defer t.mu.Unlock()
	t.warmBytes = 0
	t.warmCount = 0
}

// WarmBytes returns the total number of warm bytes recorded from proxy cache hits.
func (t *Tracker) WarmBytes() int64 {
	if t == nil {
		return 0
	}
	t.mu.Lock()
	defer t.mu.Unlock()
	return t.warmBytes
}

// WarmCount returns the total number of warm files/blobs recorded from proxy cache hits.
func (t *Tracker) WarmCount() int {
	if t == nil {
		return 0
	}
	t.mu.Lock()
	defer t.mu.Unlock()
	return t.warmCount
}

// StreamInterceptor returns a grpc.StreamClientInterceptor that captures x-cas-warm-bytes and x-cas-warm-count response trailers on streaming RPCs (e.g. ByteStream.Read).
func (t *Tracker) StreamInterceptor() grpc.StreamClientInterceptor {
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
	tracker *Tracker
}

func (s *trackedClientStream) RecvMsg(m any) error {
	err := s.ClientStream.RecvMsg(m)
	if err == io.EOF {
		if s.trailer != nil && s.tracker != nil {
			if vals := s.trailer.Get(TrailerWarmBytes); len(vals) > 0 {
				if warmBytes, parseErr := strconv.ParseInt(vals[0], 10, 64); parseErr == nil && warmBytes > 0 {
					warmCount := 1
					if countVals := s.trailer.Get(TrailerWarmCount); len(countVals) > 0 {
						if c, err := strconv.Atoi(countVals[0]); err == nil && c > 0 {
							warmCount = c
						}
					}
					s.tracker.AddWarm(warmBytes, warmCount)
				}
			}
			s.trailer = nil
		}
	}
	return err
}

// UnaryInterceptor returns a grpc.UnaryClientInterceptor that captures x-cas-warm-bytes and x-cas-warm-count response trailers on unary RPCs (e.g. CAS.BatchReadBlobs).
func (t *Tracker) UnaryInterceptor() grpc.UnaryClientInterceptor {
	return func(ctx context.Context, method string, req, reply any, cc *grpc.ClientConn, invoker grpc.UnaryInvoker, opts ...grpc.CallOption) error {
		var trailer metadata.MD
		opts = append(opts, grpc.Trailer(&trailer))
		err := invoker(ctx, method, req, reply, cc, opts...)
		if err == nil && t != nil {
			if vals := trailer.Get(TrailerWarmBytes); len(vals) > 0 {
				if warmBytes, parseErr := strconv.ParseInt(vals[0], 10, 64); parseErr == nil && warmBytes > 0 {
					warmCount := 1
					if countVals := trailer.Get(TrailerWarmCount); len(countVals) > 0 {
						if c, err := strconv.Atoi(countVals[0]); err == nil && c > 0 {
							warmCount = c
						}
					}
					t.AddWarm(warmBytes, warmCount)
				}
			}
		}
		return err
	}
}
