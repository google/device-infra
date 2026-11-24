// Package nettest provides a TCP relay for tests of DualConduit. The relay
// can lose what passes through it without closing its connections, as a
// network path that has died does.
package nettest

import (
	"errors"
	"net"
	"os"
	"sync"
	"sync/atomic"
	"testing"
	"time"
)

// Relay is a TCP relay in front of a server. Clients connect to Addr.
type Relay struct {
	// Addr is the address that the relay listens on.
	Addr string

	silent      atomic.Bool
	stalled     atomic.Bool
	stalledBoth atomic.Bool
	// released is closed when the test ends.
	released chan struct{}

	mu    sync.Mutex
	conns []net.Conn
}

// StartRelay starts a relay in front of the server at serverAddr. The relay
// stops when the test ends.
func StartRelay(t testing.TB, serverAddr string) *Relay {
	t.Helper()
	lis, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("net.Listen got unexpected error: %v", err)
	}
	r := &Relay{Addr: lis.Addr().String(), released: make(chan struct{})}
	t.Cleanup(func() {
		lis.Close()
		close(r.released)
		r.mu.Lock()
		defer r.mu.Unlock()
		for _, c := range r.conns {
			c.Close()
		}
	})
	go func() {
		for {
			down, err := lis.Accept()
			if err != nil {
				return
			}
			up, err := net.Dial("tcp", serverAddr)
			if err != nil {
				down.Close()
				continue
			}
			r.mu.Lock()
			r.conns = append(r.conns, down, up)
			r.mu.Unlock()
			go r.forward(up, down, false)
			go r.forward(down, up, true)
		}
	}()
	return r
}

// Silence makes the relay drop everything that is sent through it from now
// on, in both directions. Its connections stay open, and both sides can keep
// sending: neither notices anything but the silence.
func (r *Relay) Silence() {
	r.silent.Store(true)
}

// Stall makes the relay stop reading what the server sends, so that the
// writes of the server block once the buffers are full, as they do on a path
// that has died while data was flowing. What the clients send still reaches
// the server, unless bothWays is set.
func (r *Relay) Stall(bothWays bool) {
	r.stalledBoth.Store(bothWays)
	r.stalled.Store(true)
	// A read that is waiting for data ends at once, so that nothing that
	// arrives later is forwarded.
	r.mu.Lock()
	defer r.mu.Unlock()
	for _, c := range r.conns {
		c.SetReadDeadline(time.Now())
	}
}

// stalledFor reports whether the direction is stalled.
func (r *Relay) stalledFor(fromServer bool) bool {
	return r.stalled.Load() && (fromServer || r.stalledBoth.Load())
}

// forward copies src to dst until src ends.
func (r *Relay) forward(dst, src net.Conn, fromServer bool) {
	buf := make([]byte, 32<<10)
	for {
		if r.stalledFor(fromServer) {
			<-r.released
			return
		}
		n, err := src.Read(buf)
		if n > 0 && !r.silent.Load() && !r.stalledFor(fromServer) {
			if _, err := dst.Write(buf[:n]); err != nil {
				return
			}
		}
		if errors.Is(err, os.ErrDeadlineExceeded) {
			// Stall has ended the read.
			src.SetReadDeadline(time.Time{})
			continue
		}
		if err != nil {
			// The other connection stays open: the far side must not learn
			// from the relay that this side has gone.
			return
		}
	}
}
