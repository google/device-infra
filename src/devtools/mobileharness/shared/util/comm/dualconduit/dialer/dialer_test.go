package dialer

import (
	"context"
	"testing"
	"time"

	rsockettransport "github.com/rsocket/rsocket-go/core/transport"
)

func TestConnectTimeoutFor(t *testing.T) {
	tests := []struct {
		name      string
		keepAlive time.Duration
		want      time.Duration
	}{
		{name: "default keep-alive", keepAlive: 20 * time.Second, want: 10 * time.Second},
		{name: "long keep-alive", keepAlive: 60 * time.Second, want: 10 * time.Second},
		{name: "short keep-alive", keepAlive: 4 * time.Second, want: 2 * time.Second},
		{name: "unset keep-alive", keepAlive: 0, want: 10 * time.Second},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			s := &Service{}
			if got := s.connectTimeoutFor(tc.keepAlive); got != tc.want {
				t.Errorf("connectTimeoutFor(%v) = %v, want %v", tc.keepAlive, got, tc.want)
			}
		})
	}
}

// TestCheckConnection_DialNeverCompletes covers an acceptor that cannot be
// reached: every dial hangs until its context ends. Each attempt must be given
// up after the connect timeout instead of waiting for the transport's own,
// much longer, handshake timeout.
func TestCheckConnection_DialNeverCompletes(t *testing.T) {
	ctx, cancel := context.WithTimeout(t.Context(), 30*time.Second)
	defer cancel()

	hangingDial := func(ctx context.Context) (*rsockettransport.Transport, error) {
		<-ctx.Done()
		return nil, ctx.Err()
	}
	s := New(ctx, "test-host", "127.0.0.1", func() (rsockettransport.ClientTransporter, error) {
		return hangingDial, nil
	}, RetryPolicy{}, 0)
	s.connectTimeout = 50 * time.Millisecond

	start := time.Now()
	err := s.CheckConnection(ctx)
	elapsed := time.Since(start)
	if err == nil {
		t.Fatal("CheckConnection() succeeded, want an error")
	}
	// Three attempts with 1s and 2s backoff in between take a little over 3s.
	if elapsed > 10*time.Second {
		t.Errorf("CheckConnection() took %v, want it to give up within 10s", elapsed)
	}
}
