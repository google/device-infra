package transport

import (
	"context"
	"errors"
	"fmt"
	"io"
	"net"
	"runtime"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/nettest"
	"github.com/rsocket/rsocket-go/core"
	"github.com/rsocket/rsocket-go/core/framing"
	rsockettransport "github.com/rsocket/rsocket-go/core/transport"
	"github.com/rsocket/rsocket-go/payload"
	"github.com/rsocket/rsocket-go"
)

// freePort returns a TCP port that was free when the function returned.
func freePort(t *testing.T) int {
	t.Helper()
	lis, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("net.Listen got unexpected error: %v", err)
	}
	defer lis.Close()
	return lis.Addr().(*net.TCPAddr).Port
}

// startWebSocketServer serves RSocket over WebSocket on the port and accepts every setup.
func startWebSocketServer(ctx context.Context, t *testing.T, port int) {
	t.Helper()
	serverTransport, err := CreateServerTransport(ServerConfig{Port: port, TransportType: WebSocket})
	if err != nil {
		t.Fatalf("CreateServerTransport got unexpected error: %v", err)
	}
	go func() {
		_ = rsocket.Receive().
			Acceptor(func(ctx context.Context, setup payload.SetupPayload, rs rsocket.CloseableRSocket) (rsocket.RSocket, error) {
				return rsocket.NewAbstractSocket(), nil
			}).
			Transport(serverTransport).
			Serve(ctx)
	}()
	addr := fmt.Sprintf("127.0.0.1:%d", port)
	for deadline := time.Now().Add(10 * time.Second); time.Now().Before(deadline); time.Sleep(20 * time.Millisecond) {
		if conn, err := net.Dial("tcp", addr); err == nil {
			conn.Close()
			return
		}
	}
	t.Fatalf("server on %v did not start", addr)
}

// TestClientTransport_SlowDialLongerThanKeepAlive covers a connection to the
// acceptor that takes longer to establish than the keep-alive interval. The
// keep-alive timer starts before the dial, so a keep-alive frame is already due
// when the connection opens and is written at the same time as the SETUP frame.
// The client must survive that. A failure here is a panic in the test binary.
func TestClientTransport_SlowDialLongerThanKeepAlive(t *testing.T) {
	const (
		keepAlive = 20 * time.Millisecond
		dialDelay = 3 * keepAlive
		budget    = 20 * time.Second
	)
	ctx, cancel := context.WithCancel(t.Context())
	defer cancel()

	port := freePort(t)
	startWebSocketServer(ctx, t, port)

	dial, err := CreateClientTransport(ClientConfig{Target: fmt.Sprintf("ws://127.0.0.1:%d", port)})
	if err != nil {
		t.Fatalf("CreateClientTransport got unexpected error: %v", err)
	}
	slowDial := func(ctx context.Context) (*rsockettransport.Transport, error) {
		time.Sleep(dialDelay)
		return dial(ctx)
	}

	// The overlap of the two writes depends on scheduling, so connect repeatedly.
	// A connection may be refused when the keep-alive frame is sent first; that
	// is retried in production and is not what this test is about.
	attempts, connected := 0, 0
	for start := time.Now(); time.Since(start) < budget; attempts++ {
		client, err := rsocket.Connect().
			KeepAlive(keepAlive, 15*time.Second, 3).
			SetupPayload(payload.New([]byte("test"), nil)).
			Transport(slowDial).
			Start(ctx)
		if err != nil {
			continue
		}
		connected++
		client.Close()
	}
	if connected == 0 {
		t.Errorf("none of %d connection attempts succeeded", attempts)
	}
	t.Logf("%d of %d connection attempts succeeded, without a crash", connected, attempts)
}

// startServer serves RSocket with serverTransport on the port, accepts
// every setup, and reports on the returned channel when the server side of a
// connection closes. It fails the test if the server does not start.
func startServer(ctx context.Context, t *testing.T, serverTransport rsockettransport.ServerTransporter, port int) <-chan struct{} {
	t.Helper()
	closed := make(chan struct{}, 16)
	stopped := make(chan error, 1)
	go func() {
		stopped <- rsocket.Receive().
			Acceptor(func(ctx context.Context, setup payload.SetupPayload, rs rsocket.CloseableRSocket) (rsocket.RSocket, error) {
				rs.OnClose(func(error) { closed <- struct{}{} })
				return rsocket.NewAbstractSocket(), nil
			}).
			Transport(serverTransport).
			Serve(ctx)
	}()
	addr := fmt.Sprintf("127.0.0.1:%d", port)
	for deadline := time.Now().Add(10 * time.Second); time.Now().Before(deadline); time.Sleep(20 * time.Millisecond) {
		select {
		case err := <-stopped:
			t.Fatalf("server on %v stopped before it was ready: %v", addr, err)
		default:
		}
		if conn, err := net.Dial("tcp", addr); err == nil {
			conn.Close()
			return closed
		}
	}
	t.Fatalf("server on %v did not start", addr)
	return nil
}

// startLifetimeServer serves RSocket on the port, with connections that apply
// the keep-alive lifetime of their client.
func startLifetimeServer(ctx context.Context, t *testing.T, typ Transport, port int) <-chan struct{} {
	t.Helper()
	serverTransport, err := CreateServerTransport(ServerConfig{Port: port, TransportType: typ})
	if err != nil {
		t.Fatalf("CreateServerTransport got unexpected error: %v", err)
	}
	return startServer(ctx, t, WithClientLifetime(serverTransport), port)
}

// TestServerTransport_ClientLifetime covers a connection that dies without
// being closed. The server must close its side when it has received nothing
// for the keep-alive lifetime that the client named in SETUP, also when
// nothing at all follows the SETUP frame. Without WithClientLifetime the
// server keeps the first connection for 90 s from the last frame, and the
// second one for good.
func TestServerTransport_ClientLifetime(t *testing.T) {
	const (
		// The lifetime is long enough for a loaded machine to deliver the
		// keep-alive frames in time while the connection works.
		lifetime = 3 * time.Second
		// limit is far below the 90 s that rsocket-go applies by itself.
		limit = 20 * time.Second
	)
	for _, typ := range []Transport{TCP, WebSocket} {
		clientTransport := func(t *testing.T, relayAddr string) rsockettransport.ClientTransporter {
			target := relayAddr
			if typ == WebSocket {
				target = "ws://" + relayAddr
			}
			dial, err := CreateClientTransport(ClientConfig{Target: target, TransportType: typ})
			if err != nil {
				t.Fatalf("CreateClientTransport got unexpected error: %v", err)
			}
			return dial
		}

		t.Run(string(typ)+"/frames stop", func(t *testing.T) {
			ctx, cancel := context.WithCancel(t.Context())
			defer cancel()
			port := freePort(t)
			closed := startLifetimeServer(ctx, t, typ, port)
			relay := nettest.StartRelay(t, fmt.Sprintf("127.0.0.1:%d", port))
			client, err := rsocket.Connect().
				KeepAlive(lifetime/12, lifetime/3, 3).
				SetupPayload(payload.New([]byte("test"), nil)).
				Transport(clientTransport(t, relay.Addr)).
				Start(ctx)
			if err != nil {
				t.Fatalf("rsocket.Connect() got unexpected error: %v", err)
			}
			defer client.Close()

			// While keep-alive frames arrive, the connection stays.
			select {
			case <-closed:
				t.Fatalf("server closed a connection whose keep-alive frames arrive")
			case <-time.After(lifetime + lifetime/2):
			}

			relay.Silence()
			start := time.Now()
			select {
			case <-closed:
				t.Logf("server closed the connection %v after the last frame (lifetime %v)", time.Since(start).Round(time.Millisecond), lifetime)
			case <-time.After(limit):
				t.Errorf("server kept a silent connection for %v, want it closed after the client's lifetime of %v", limit, lifetime)
			}
		})

		t.Run(string(typ)+"/nothing after SETUP", func(t *testing.T) {
			ctx, cancel := context.WithCancel(t.Context())
			defer cancel()
			port := freePort(t)
			closed := startLifetimeServer(ctx, t, typ, port)
			relay := nettest.StartRelay(t, fmt.Sprintf("127.0.0.1:%d", port))
			// The keep-alive interval is so long that the client sends
			// nothing after SETUP.
			start := time.Now()
			client, err := rsocket.Connect().
				KeepAlive(time.Hour, lifetime/3, 3).
				SetupPayload(payload.New([]byte("test"), nil)).
				Transport(clientTransport(t, relay.Addr)).
				Start(ctx)
			if err != nil {
				t.Fatalf("rsocket.Connect() got unexpected error: %v", err)
			}
			defer client.Close()

			select {
			case <-closed:
				if got := time.Since(start); got < lifetime/2 {
					t.Errorf("server closed the connection after %v, want not before the lifetime of %v", got, lifetime)
				}
			case <-time.After(limit):
				t.Errorf("server kept a connection with nothing after SETUP for %v, want it closed after the client's lifetime of %v", limit, lifetime)
			}
		})
	}
}

// TestClientTransport_LifetimeFromOpen covers a server that accepts the
// connection and then never sends a frame, as a network path does that dies
// right after the connection has opened. The client must give the connection
// up after its keep-alive lifetime. Without WithLifetimeFromOpen it never
// does: rsocket-go counts the lifetime only from the first frame it handles.
func TestClientTransport_LifetimeFromOpen(t *testing.T) {
	const (
		lifetime = 600 * time.Millisecond
		limit    = 10 * time.Second
	)
	for _, typ := range []Transport{TCP, WebSocket} {
		t.Run(string(typ), func(t *testing.T) {
			ctx, cancel := context.WithCancel(t.Context())
			defer cancel()
			// A server that applies no lifetime, and that sends nothing as
			// long as the client sends nothing.
			port := freePort(t)
			serverTransport, err := CreateServerTransport(ServerConfig{Port: port, TransportType: typ})
			if err != nil {
				t.Fatalf("CreateServerTransport got unexpected error: %v", err)
			}
			startServer(ctx, t, serverTransport, port)
			target := fmt.Sprintf("127.0.0.1:%d", port)
			if typ == WebSocket {
				target = "ws://" + target
			}
			dial, err := CreateClientTransport(ClientConfig{Target: target, TransportType: typ})
			if err != nil {
				t.Fatalf("CreateClientTransport got unexpected error: %v", err)
			}

			closed, dead := make(chan error, 1), make(chan struct{})
			start := time.Now()
			// The keep-alive interval is so long that the client sends
			// nothing after SETUP.
			client, err := rsocket.Connect().
				OnClose(func(err error) {
					select {
					case closed <- err:
					default:
					}
				}).
				KeepAlive(time.Hour, lifetime/3, 3).
				SetupPayload(payload.New([]byte("test"), nil)).
				Transport(WithLifetimeFromOpen(dial, lifetime, func() { close(dead) })).
				Start(ctx)
			if err != nil {
				t.Fatalf("rsocket.Connect() got unexpected error: %v", err)
			}
			defer client.Close()

			select {
			case <-closed:
				if got := time.Since(start); got < lifetime/2 {
					t.Errorf("client closed the connection after %v, want not before the lifetime of %v", got, lifetime)
				}
			case <-time.After(limit):
				t.Fatalf("client kept a connection whose server never answers for %v, want it closed after the lifetime of %v", limit, lifetime)
			}
			select {
			case <-dead:
			case <-time.After(5 * time.Second):
				t.Errorf("the owner of the connection was not told that the connection was given up")
			}
		})
	}
}

// readResult is what one Read of a fakeConn returns.
type readResult struct {
	frame core.BufferedFrame
	err   error
}

// fakeConn is a connection that reads what a test has queued and counts how
// often it is closed.
type fakeConn struct {
	// reads holds the results of the next Read calls. Read returns io.EOF
	// when it is empty.
	reads  chan readResult
	closes atomic.Int32
}

func newFakeConn() *fakeConn {
	return &fakeConn{reads: make(chan readResult, 8)}
}

func (c *fakeConn) Close() error {
	c.closes.Add(1)
	return nil
}

func (c *fakeConn) SetDeadline(time.Time) error { return nil }

func (c *fakeConn) SetCounter(*core.TrafficCounter) {}

func (c *fakeConn) Read() (core.BufferedFrame, error) {
	select {
	case r := <-c.reads:
		return r.frame, r.err
	default:
		return nil, io.EOF
	}
}

func (c *fakeConn) Write(core.WriteableFrame) error { return nil }

func (c *fakeConn) Flush() error { return nil }

// fakeAddrConn is a fakeConn that has an address.
type fakeAddrConn struct {
	*fakeConn
	addr string
}

func (c *fakeAddrConn) Addr() string { return c.addr }

// Both wrappers keep the address of the connection that they wrap.
var (
	_ rsockettransport.AddrConn = (*watchedConn)(nil)
	_ rsockettransport.AddrConn = (*setupLifetimeConn)(nil)
)

// started reports whether w has been started, and with which lifetime.
func started(w *idleWatch) (time.Duration, bool) {
	w.mu.Lock()
	defer w.mu.Unlock()
	return w.lifetime, w.timer != nil
}

// setupFrame returns a SETUP frame that names the keep-alive lifetime.
func setupFrame(t *testing.T, lifetime time.Duration) *framing.SetupFrame {
	t.Helper()
	mime := []byte("application/binary")
	f := framing.NewSetupFrame(core.DefaultVersion, lifetime/3, lifetime, nil, mime, mime, []byte("test"), nil, false)
	t.Cleanup(f.Release)
	return f
}

// keepaliveFrame returns a KEEPALIVE frame.
func keepaliveFrame(t *testing.T) *framing.KeepaliveFrame {
	t.Helper()
	f := framing.NewKeepaliveFrame(0, nil, true)
	t.Cleanup(f.Release)
	return f
}

func TestIdleWatch_FiresOnceAfterLifetime(t *testing.T) {
	const lifetime = 100 * time.Millisecond
	var fired atomic.Int32
	timedOut := make(chan time.Duration, 4)
	w := newIdleWatch(func(lifetime time.Duration) {
		fired.Add(1)
		timedOut <- lifetime
	})

	start := time.Now()
	w.Start(lifetime)
	select {
	case got := <-timedOut:
		if got != lifetime {
			t.Errorf("onTimeout called with lifetime %v after Start(%v), want %v", got, lifetime, lifetime)
		}
		if elapsed := time.Since(start); elapsed < lifetime {
			t.Errorf("onTimeout called %v after Start(%v), want not before the lifetime", elapsed, lifetime)
		}
	case <-time.After(10 * time.Second):
		t.Fatalf("onTimeout not called within 10s after Start(%v)", lifetime)
	}

	// The watch has ended: it does not fire again, and Stop was not the one
	// to end it.
	time.Sleep(3 * lifetime)
	if got := fired.Load(); got != 1 {
		t.Errorf("onTimeout called %d times after Start(%v), want once", got, lifetime)
	}
	if w.Stop() {
		t.Errorf("Stop() after the watch has timed out = true, want false")
	}
}

func TestIdleWatch_TouchExtendsDeadline(t *testing.T) {
	const (
		lifetime = 800 * time.Millisecond
		// touchAfter leaves a wide margin before the first deadline, for a
		// loaded machine.
		touchAfter = lifetime / 4
	)
	timedOut := make(chan time.Time, 1)
	w := newIdleWatch(func(time.Duration) { timedOut <- time.Now() })

	w.Start(lifetime)
	time.Sleep(touchAfter)
	touched := time.Now()
	w.Touch()
	select {
	case at := <-timedOut:
		if got := at.Sub(touched); got < 0 {
			t.Fatalf("onTimeout called before Touch(): the test slept %v instead of %v", time.Since(touched)+touchAfter, touchAfter)
		} else if got < lifetime {
			t.Errorf("onTimeout called %v after Touch(), want not before the lifetime of %v", got, lifetime)
		}
	case <-time.After(10 * time.Second):
		t.Fatalf("onTimeout not called within 10s after Touch() with a lifetime of %v", lifetime)
	}
}

func TestIdleWatch_StopPreventsTimeout(t *testing.T) {
	const lifetime = 50 * time.Millisecond
	var fired atomic.Int32
	w := newIdleWatch(func(time.Duration) { fired.Add(1) })

	w.Start(lifetime)
	if !w.Stop() {
		t.Errorf("Stop() of a started watch = false, want true")
	}
	if w.Stop() {
		t.Errorf("second Stop() = true, want false")
	}
	time.Sleep(4 * lifetime)
	if got := fired.Load(); got != 0 {
		t.Errorf("onTimeout called %d times after Stop(), want never", got)
	}
}

func TestIdleWatch_StopBeforeStartPreventsStart(t *testing.T) {
	const lifetime = 50 * time.Millisecond
	var fired atomic.Int32
	w := newIdleWatch(func(time.Duration) { fired.Add(1) })

	if !w.Stop() {
		t.Errorf("Stop() of a watch that was not started = false, want true")
	}
	w.Start(lifetime)
	if _, ok := started(w); ok {
		t.Errorf("Start(%v) after Stop() armed the watch, want it left alone", lifetime)
	}
	time.Sleep(4 * lifetime)
	if got := fired.Load(); got != 0 {
		t.Errorf("onTimeout called %d times after Stop() and Start(%v), want never", got, lifetime)
	}
}

func TestIdleWatch_NonPositiveLifetimeDoesNotStart(t *testing.T) {
	for _, lifetime := range []time.Duration{0, -time.Second} {
		w := newIdleWatch(nil)
		w.Start(lifetime)
		if _, ok := started(w); ok {
			t.Errorf("Start(%v) armed the watch, want it left alone", lifetime)
		}
	}
}

// TestIdleWatch_ConcurrentTouchStopAndTimeout lets Touch, two Stop calls and
// the expiry of the timer meet. Each watch must end exactly once: either one
// Stop returns true and onTimeout is never called, or no Stop returns true
// and onTimeout is called once.
func TestIdleWatch_ConcurrentTouchStopAndTimeout(t *testing.T) {
	const rounds = 300
	fired := make([]atomic.Int32, rounds)
	stopped := make([]atomic.Int32, rounds)
	for i := range rounds {
		// The lifetime and the time of the Stop calls are close together
		// and differ from round to round.
		lifetime := time.Duration(1+i%5) * 100 * time.Microsecond
		timedOut := make(chan struct{}, 2)
		w := newIdleWatch(func(time.Duration) {
			fired[i].Add(1)
			timedOut <- struct{}{}
		})
		w.Start(lifetime)

		var wg sync.WaitGroup
		wg.Add(3)
		go func() {
			defer wg.Done()
			for range 20 {
				w.Touch()
				runtime.Gosched()
			}
		}()
		for range 2 {
			go func() {
				defer wg.Done()
				time.Sleep(lifetime)
				if w.Stop() {
					stopped[i].Add(1)
				}
			}()
		}
		returned := make(chan struct{})
		go func() {
			wg.Wait()
			close(returned)
		}()
		select {
		case <-returned:
		case <-time.After(30 * time.Second):
			t.Fatalf("round %d: Touch() and Stop() calls have not returned after 30s", i)
		}
		if stopped[i].Load() == 0 {
			// The watch timed out before both Stop calls.
			select {
			case <-timedOut:
			case <-time.After(10 * time.Second):
				t.Fatalf("round %d: both Stop() calls returned false, and onTimeout was not called within 10s", i)
			}
		}
	}

	// Time for an onTimeout that should not have been called.
	time.Sleep(50 * time.Millisecond)
	for i := range rounds {
		if gotFired, gotStopped := fired[i].Load(), stopped[i].Load(); gotFired+gotStopped != 1 {
			t.Errorf("round %d: onTimeout called %d times and Stop() returned true %d times, want exactly one of the two once", i, gotFired, gotStopped)
		}
	}
}

func TestWatchedConn_CloseDisarmsWatch(t *testing.T) {
	const lifetime = 50 * time.Millisecond
	conn := newFakeConn()
	var dead atomic.Int32
	wc := newWatchedConn(conn, func() { dead.Add(1) })

	wc.Watch(lifetime)
	if err := wc.Close(); err != nil {
		t.Fatalf("Close() got unexpected error: %v", err)
	}
	time.Sleep(4 * lifetime)
	if got := dead.Load(); got != 0 {
		t.Errorf("onDead called %d times after Close(), want never", got)
	}
	if got := conn.closes.Load(); got != 1 {
		t.Errorf("underlying connection closed %d times after Close(), want once", got)
	}
}

func TestWatchedConn_IdleTimeoutClosesConnAndCallsOnDeadOnce(t *testing.T) {
	const lifetime = 50 * time.Millisecond
	conn := newFakeConn()
	var dead atomic.Int32
	called := make(chan struct{}, 4)
	wc := newWatchedConn(conn, func() {
		dead.Add(1)
		called <- struct{}{}
	})

	wc.Watch(lifetime)
	select {
	case <-called:
	case <-time.After(10 * time.Second):
		t.Fatalf("onDead not called within 10s after Watch(%v) on a connection that reads nothing", lifetime)
	}
	time.Sleep(3 * lifetime)
	if got := dead.Load(); got != 1 {
		t.Errorf("onDead called %d times after Watch(%v), want once", got, lifetime)
	}
	if got := conn.closes.Load(); got != 1 {
		t.Errorf("underlying connection closed %d times after the lifetime ran out, want once", got)
	}
}

func TestWatchedConn_IdleTimeoutWithoutOnDeadClosesConn(t *testing.T) {
	const lifetime = 50 * time.Millisecond
	conn := newFakeConn()
	wc := newWatchedConn(conn, nil)

	wc.Watch(lifetime)
	for deadline := time.Now().Add(10 * time.Second); conn.closes.Load() == 0 && time.Now().Before(deadline); time.Sleep(time.Millisecond) {
	}
	if got := conn.closes.Load(); got != 1 {
		t.Errorf("underlying connection closed %d times after Watch(%v) with no onDead, want once", got, lifetime)
	}
}

func TestWatchedConn_ReadTouchesWatchOnlyOnSuccess(t *testing.T) {
	conn := newFakeConn()
	wc := newWatchedConn(conn, nil)
	wc.Watch(time.Hour)
	t.Cleanup(func() { wc.Close() })

	wantErr := errors.New("read failed")
	conn.reads <- readResult{err: wantErr}
	before := wc.watch.lastTouch.Load()
	time.Sleep(time.Millisecond)
	if _, err := wc.Read(); !errors.Is(err, wantErr) {
		t.Fatalf("Read() got error %v, want %v", err, wantErr)
	}
	if got := wc.watch.lastTouch.Load(); got != before {
		t.Errorf("failed Read() moved the last touch of the watch by %v, want it unchanged", time.Duration(got-before))
	}

	want := keepaliveFrame(t)
	conn.reads <- readResult{frame: want}
	got, err := wc.Read()
	if err != nil {
		t.Fatalf("Read() got unexpected error: %v", err)
	}
	if got != core.BufferedFrame(want) {
		t.Errorf("Read() = %v, want the frame of the underlying connection %v", got, want)
	}
	if got := wc.watch.lastTouch.Load(); got <= before {
		t.Errorf("successful Read() left the last touch of the watch at %v, want it after %v", time.Duration(got), time.Duration(before))
	}
}

func TestWatchedConn_Addr(t *testing.T) {
	const addr = "192.0.2.1:7878"
	conn := &fakeAddrConn{fakeConn: newFakeConn(), addr: addr}
	wc := newWatchedConn(conn, nil)
	// The address is read once: rsocket-go may write to the connection
	// later.
	conn.addr = "changed"
	if got := wc.Addr(); got != addr {
		t.Errorf("Addr() = %q, want %q, the address at the time of wrapping", got, addr)
	}

	if got := newWatchedConn(newFakeConn(), nil).Addr(); got != "" {
		t.Errorf("Addr() of a connection without an address = %q, want %q", got, "")
	}
}

func TestSetupLifetimeConn_UnwatchedBeforeFirstFrame(t *testing.T) {
	conn := newFakeConn()
	c := &setupLifetimeConn{watchedConn: newWatchedConn(conn, nil), awaitingSetup: true}

	if _, ok := started(c.watch); ok {
		t.Errorf("the watch of a new connection is started, want it to wait for the SETUP frame")
	}
	// A read that fails is not the first frame.
	if _, err := c.Read(); !errors.Is(err, io.EOF) {
		t.Fatalf("Read() got error %v, want %v", err, io.EOF)
	}
	if _, ok := started(c.watch); ok {
		t.Errorf("failed Read() started the watch, want it to wait for the SETUP frame")
	}
	if !c.awaitingSetup {
		t.Errorf("awaitingSetup = false after a failed Read(), want true")
	}
}

func TestSetupLifetimeConn_SetupStartsWatchWithItsLifetime(t *testing.T) {
	const lifetime = 80 * time.Millisecond
	conn := newFakeConn()
	dead := make(chan struct{}, 1)
	c := &setupLifetimeConn{watchedConn: newWatchedConn(conn, func() { dead <- struct{}{} }), awaitingSetup: true}

	want := setupFrame(t, lifetime)
	conn.reads <- readResult{frame: want}
	got, err := c.Read()
	if err != nil {
		t.Fatalf("Read() got unexpected error: %v", err)
	}
	if got != core.BufferedFrame(want) {
		t.Errorf("Read() = %v, want the SETUP frame of the underlying connection %v", got, want)
	}
	if gotLifetime, ok := started(c.watch); !ok || gotLifetime != lifetime {
		t.Errorf("after Read() of a SETUP frame with lifetime %v: watch started = %t with lifetime %v, want true with %v", lifetime, ok, gotLifetime, lifetime)
	}

	// Nothing follows the SETUP frame, so the connection is given up.
	select {
	case <-dead:
	case <-time.After(10 * time.Second):
		t.Fatalf("onDead not called within 10s after a SETUP frame with lifetime %v and nothing after it", lifetime)
	}
	if got := conn.closes.Load(); got != 1 {
		t.Errorf("underlying connection closed %d times after the lifetime of the SETUP frame ran out, want once", got)
	}
}

func TestSetupLifetimeConn_FirstFrameNotSetupStaysUnwatched(t *testing.T) {
	conn := newFakeConn()
	c := &setupLifetimeConn{watchedConn: newWatchedConn(conn, nil), awaitingSetup: true}

	// Only the first frame of a connection can start the lifetime.
	conn.reads <- readResult{frame: keepaliveFrame(t)}
	conn.reads <- readResult{frame: setupFrame(t, 80*time.Millisecond)}
	for i := range 2 {
		if _, err := c.Read(); err != nil {
			t.Fatalf("Read() number %d got unexpected error: %v", i+1, err)
		}
		if _, ok := started(c.watch); ok {
			t.Errorf("Read() number %d started the watch, want it unwatched when the first frame is not a SETUP frame", i+1)
		}
	}
	if c.awaitingSetup {
		t.Errorf("awaitingSetup = true after the first frame, want false")
	}
}
