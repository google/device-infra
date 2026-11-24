// Package transport provides transport utilities for Dual Conduit, supporting TCP and WebSocket.
package transport

import (
	"context"
	"fmt"
	"log/slog"
	"net/http"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/auth"
	"github.com/rsocket/rsocket-go/core"
	"github.com/rsocket/rsocket-go/core/framing"
	"github.com/rsocket/rsocket-go/core/transport"
	"github.com/rsocket/rsocket-go"
)

// Transport represents the transport type.
type Transport string

const (
	// TCP is the transport type for TCP.
	TCP Transport = "tcp"
	// WebSocket is the transport type for WebSocket.
	WebSocket Transport = "websocket"
)

// ClientConfig holds configuration for creating a client transporter.
type ClientConfig struct {
	Target        string
	TransportType Transport
	UseSAToken    bool
	SAKeyFile     string
	Aud           string
}

// ServerConfig holds configuration for creating a server transporter.
type ServerConfig struct {
	Port          int
	TransportType Transport
}

// lockedConn serializes writes to the underlying connection.
//
// rsocket-go writes to a connection from more than one goroutine while a client
// is being set up: the SETUP frame is written by the caller, and a keep-alive
// frame may be written by the write loop at the same time. The connections it
// provides are not safe for concurrent writes, and the WebSocket one panics.
type lockedConn struct {
	transport.Conn
	mu sync.Mutex
}

// Write writes a frame while holding the write lock.
func (c *lockedConn) Write(frame core.WriteableFrame) error {
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.Conn.Write(frame)
}

// Flush flushes buffered frames while holding the write lock.
func (c *lockedConn) Flush() error {
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.Conn.Flush()
}

// Addr returns the address of the underlying connection, if it has one.
func (c *lockedConn) Addr() string {
	if ac, ok := c.Conn.(transport.AddrConn); ok {
		return ac.Addr()
	}
	return ""
}

// withLockedWrites returns a ClientTransporter whose connections serialize writes.
func withLockedWrites(dial transport.ClientTransporter) transport.ClientTransporter {
	return func(ctx context.Context) (*transport.Transport, error) {
		tp, err := dial(ctx)
		if err != nil {
			return nil, err
		}
		return transport.NewTransport(&lockedConn{Conn: tp.Connection()}), nil
	}
}

// CreateClientTransport creates a ClientTransporter based on the config.
// If cfg.UseSAToken is true, a new token is generated every time this function is called.
func CreateClientTransport(cfg ClientConfig) (transport.ClientTransporter, error) {
	var trans transport.ClientTransporter
	isWS := cfg.TransportType == WebSocket || strings.HasPrefix(cfg.Target, "ws://") || strings.HasPrefix(cfg.Target, "wss://")

	var token string
	if cfg.UseSAToken {
		var err error
		token, err = auth.GenerateSelfSignedJWT(cfg.SAKeyFile, cfg.Aud)
		if err != nil {
			return nil, fmt.Errorf("failed to generate SA token: %v", err)
		}
	}

	if isWS {
		wsBuilder := rsocket.WebsocketClient().SetURL(cfg.Target)
		if token != "" {
			header := http.Header{}
			header.Set("Authorization", "Bearer "+token)
			wsBuilder = wsBuilder.SetHeader(header)
		}
		trans = wsBuilder.Build()
	} else {
		trans = rsocket.TCPClient().SetAddr(cfg.Target).Build()
	}
	return withLockedWrites(trans), nil
}

// monotonicBase anchors monotonicNow. time.Since reads Go's monotonic clock,
// so the times that an idleWatch compares do not jump when the wall clock is
// set.
var monotonicBase = time.Now()

// monotonicNow returns the time that has passed since monotonicBase.
func monotonicNow() time.Duration {
	return time.Since(monotonicBase)
}

// idleWatch calls onTimeout when nothing has touched it for its lifetime. It
// knows nothing about connections or RSocket.
//
// Touch takes no lock and may be called from any goroutine. Start, Stop and
// the expiry of the timer are serialized by mu. Whichever of Stop and the
// expiry comes first sets done, and from then on the watch does nothing: it
// calls onTimeout at most once, and never after Stop has returned true.
type idleWatch struct {
	// onTimeout is called with the lifetime that has run out, on the
	// goroutine of the timer and without mu held. It may be nil.
	onTimeout func(lifetime time.Duration)
	// lastTouch is when the watch was last touched, in nanoseconds since
	// monotonicBase.
	lastTouch atomic.Int64

	mu       sync.Mutex
	lifetime time.Duration
	timer    *time.Timer
	// done: the watch was stopped or has timed out.
	done bool
}

// newIdleWatch returns a watch that is not started.
func newIdleWatch(onTimeout func(lifetime time.Duration)) *idleWatch {
	return &idleWatch{onTimeout: onTimeout}
}

// Touch restarts the lifetime.
func (w *idleWatch) Touch() {
	w.lastTouch.Store(int64(monotonicNow()))
}

// Start starts the lifetime: from now on the watch times out when it has not
// been touched for lifetime. It does nothing if lifetime is not positive, or
// if the watch was started or stopped before.
func (w *idleWatch) Start(lifetime time.Duration) {
	if lifetime <= 0 {
		return
	}
	w.Touch()
	w.mu.Lock()
	defer w.mu.Unlock()
	if w.done || w.timer != nil {
		return
	}
	w.lifetime = lifetime
	w.timer = time.AfterFunc(lifetime, w.check)
}

// Stop stops the watch. It reports whether it was the one to end the watch:
// if it returns true, onTimeout has not been called and never will be. It
// returns false if the watch has timed out or was stopped before.
func (w *idleWatch) Stop() bool {
	w.mu.Lock()
	defer w.mu.Unlock()
	if w.done {
		return false
	}
	w.done = true
	if w.timer != nil {
		w.timer.Stop()
	}
	return true
}

// check runs when the timer expires. It times the watch out if nothing has
// touched it for the lifetime, and looks again later if not.
func (w *idleWatch) check() {
	w.mu.Lock()
	if w.done {
		w.mu.Unlock()
		return
	}
	idle := monotonicNow() - time.Duration(w.lastTouch.Load())
	if idle < w.lifetime {
		w.timer.Reset(w.lifetime - idle)
		w.mu.Unlock()
		return
	}
	w.done = true
	lifetime := w.lifetime
	w.mu.Unlock()
	if w.onTimeout != nil {
		w.onTimeout(lifetime)
	}
}

// watchedConn is a connection that is given up when nothing has been read
// from it for the keep-alive lifetime. Giving it up closes the connection and
// tells its owner. It is the same for a dialer and an acceptor; they differ
// only in when they call Watch.
//
// rsocket-go has a lifetime of its own, with three gaps. It starts to count
// only when it has handled a frame. On a server it counts its default of
// 90 s, not the lifetime that the client names in SETUP. And when the
// lifetime has run out, it closes the connection only after the goroutines
// that handle its frames have returned. A frame handler that waits to send
// does not return while a write is blocked in the kernel, as it is on a path
// that has died while data was flowing: nothing would then close the
// connection until the operating system gives up on the write. Closing the
// connection here ends the blocked write, and telling the owner lets it close
// what it holds for the connection.
type watchedConn struct {
	transport.Conn
	// addr is the address of the connection, if it has one. It is read
	// once, before rsocket-go has the connection: Addr of rsocket-go's
	// TCPConn copies the whole struct, which the client setup writes to.
	addr  string
	watch *idleWatch
}

// newWatchedConn wraps conn. It must be called before rsocket-go has conn.
//
// When the connection is given up, onDead, if not nil, is called once, on a
// goroutine of its own. It is not called when the connection is closed with
// Close before that.
func newWatchedConn(conn transport.Conn, onDead func()) *watchedConn {
	c := &watchedConn{Conn: conn}
	if ac, ok := conn.(transport.AddrConn); ok {
		c.addr = ac.Addr()
	}
	addr := c.addr
	c.watch = newIdleWatch(func(lifetime time.Duration) {
		slog.WarnContext(context.Background(), "Nothing was received on the connection for the keep-alive lifetime, closing it", "lifetime", lifetime, "addr", addr)
		_ = conn.Close()
		if onDead != nil {
			go onDead()
		}
	})
	return c
}

// Watch starts the lifetime: from now on the connection is given up when
// nothing has been read from it for lifetime.
func (c *watchedConn) Watch(lifetime time.Duration) {
	c.watch.Start(lifetime)
}

// Read reads the next frame.
func (c *watchedConn) Read() (core.BufferedFrame, error) {
	f, err := c.Conn.Read()
	if err == nil {
		c.watch.Touch()
	}
	return f, err
}

// Close closes the connection. A connection that is closed this way is not
// given up any more, so its owner is not told.
func (c *watchedConn) Close() error {
	c.watch.Stop()
	return c.Conn.Close()
}

// Addr returns the address of the underlying connection, if it has one.
func (c *watchedConn) Addr() string {
	return c.addr
}

// WithLifetimeFromOpen returns a ClientTransporter whose connections are
// given up when nothing has been received on them for the keep-alive
// lifetime, counted from the moment they open. A connection that is given up
// is closed, and onDead, if not nil, is called on a goroutine of its own. See
// watchedConn for what rsocket-go leaves open.
func WithLifetimeFromOpen(dial transport.ClientTransporter, lifetime time.Duration, onDead func()) transport.ClientTransporter {
	return func(ctx context.Context) (*transport.Transport, error) {
		tp, err := dial(ctx)
		if err != nil {
			return nil, err
		}
		wc := newWatchedConn(tp.Connection(), onDead)
		wc.Watch(lifetime)
		return transport.NewTransport(wc), nil
	}
}

// setupLifetimeConn is the connection of a server. Its lifetime is the one
// that the client names in the SETUP frame, which is the first frame of a
// connection, and it starts when that frame arrives.
type setupLifetimeConn struct {
	*watchedConn
	// awaitingSetup: no frame has been read yet. Only the goroutine that
	// reads the connection uses it.
	awaitingSetup bool
}

// Read reads the next frame. If it is the first frame of the connection and
// a SETUP frame, the lifetime that it names starts.
func (c *setupLifetimeConn) Read() (core.BufferedFrame, error) {
	f, err := c.watchedConn.Read()
	if err != nil || !c.awaitingSetup {
		return f, err
	}
	c.awaitingSetup = false
	if setup, ok := f.(*framing.SetupFrame); ok {
		c.Watch(setup.MaxLifetime())
	}
	return f, nil
}

// clientLifetimeServerTransport is a ServerTransport whose connections apply
// the lifetime of their client. See WithClientLifetime.
type clientLifetimeServerTransport struct {
	transport.ServerTransport
}

// Accept registers acceptor for the incoming connections.
func (s *clientLifetimeServerTransport) Accept(acceptor transport.ServerTransportAcceptor) {
	s.ServerTransport.Accept(func(ctx context.Context, tp *transport.Transport, onClose func(*transport.Transport)) {
		// The context of the connection. rsocket-go hands it to the handler
		// of the SETUP frame and runs the write loop of the connection
		// under it.
		connCtx, cancel := context.WithCancel(ctx)
		defer cancel()
		conn := &setupLifetimeConn{watchedConn: newWatchedConn(tp.Connection(), cancel), awaitingSetup: true}
		// The server transport keeps track of tp, the transport it created.
		acceptor(connCtx, transport.NewTransport(conn), func(*transport.Transport) { onClose(tp) })
	})
}

// WithClientLifetime returns a ServerTransporter whose connections are given
// up when nothing has been received on them for the keep-alive lifetime that
// their client names in SETUP, counted from the moment the SETUP frame
// arrives. See watchedConn for what rsocket-go leaves open.
//
// Each connection has a context of its own, which ends when the connection
// is given up or its handling has returned. It is the context that the
// handler of the SETUP frame is called with.
func WithClientLifetime(serve transport.ServerTransporter) transport.ServerTransporter {
	return func(ctx context.Context) (transport.ServerTransport, error) {
		st, err := serve(ctx)
		if err != nil {
			return nil, err
		}
		return &clientLifetimeServerTransport{ServerTransport: st}, nil
	}
}

// CreateServerTransport creates a ServerTransporter based on config.
func CreateServerTransport(cfg ServerConfig) (transport.ServerTransporter, error) {
	var serverTransport transport.ServerTransporter
	switch cfg.TransportType {
	case TCP:
		serverTransport = rsocket.TCPServer().SetHostAndPort("0.0.0.0", cfg.Port).Build()
	case WebSocket:
		serverTransport = rsocket.WebsocketServer().SetAddr(fmt.Sprintf("0.0.0.0:%d", cfg.Port)).Build()
	default:
		return nil, fmt.Errorf("unknown transport: %s", cfg.TransportType)
	}
	return serverTransport, nil
}
