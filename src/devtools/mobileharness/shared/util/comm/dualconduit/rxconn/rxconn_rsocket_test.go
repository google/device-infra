package rxconn

// The tests in this file run Conns over rsocket-go itself, not over a fake
// peer. They cover how a stream opens, how refused streams behave, and what
// happens to the streams of a connection that is lost.

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"io"
	"net"
	"sync"
	"sync/atomic"
	"syscall"
	"testing"
	"time"

	rsockettransport "github.com/rsocket/rsocket-go/core/transport"
	"github.com/rsocket/rsocket-go/payload"
	"github.com/rsocket/rsocket-go"
	"github.com/rsocket/rsocket-go/rx/flux"
	"github.com/rsocket/rsocket-go/rx"
)

// link is an RSocket connection that a test can cut. The Requester side is
// client; the Responder side answers every stream with handler.
type link struct {
	client rsocket.Client
	silent atomic.Bool

	mu    sync.Mutex
	conns []net.Conn
}

// silence makes the link drop everything that is sent over it from now on,
// without closing it, as a network path that has died does. Neither side
// notices until cut.
func (l *link) silence() { l.silent.Store(true) }

// relay copies src to dst until src ends, or drops what it reads once the
// link is silent.
func (l *link) relay(dst, src net.Conn) {
	buf := make([]byte, 32<<10)
	for {
		n, err := src.Read(buf)
		if n > 0 && !l.silent.Load() {
			if _, err := dst.Write(buf[:n]); err != nil {
				return
			}
		}
		if err != nil {
			return
		}
	}
}

// cut closes the TCP connection under the RSocket connection, so that both
// sides lose it at the same time.
func (l *link) cut() {
	l.mu.Lock()
	defer l.mu.Unlock()
	for _, c := range l.conns {
		c.Close()
	}
}

// startLink starts an RSocket server on an already-bound TCP listener and
// connects a client to it through a TCP relay.
func startLink(ctx context.Context, t *testing.T, handler func(incoming flux.Flux) flux.Flux) *link {
	t.Helper()
	serverLis, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("net.Listen unexpected error: %v", err)
	}
	serverAddr := serverLis.Addr().String()
	serverTp := func(context.Context) (rsockettransport.ServerTransport, error) {
		return rsockettransport.NewTCPServerTransport(func(context.Context) (net.Listener, error) {
			return serverLis, nil
		}), nil
	}
	go func() {
		_ = rsocket.Receive().
			Acceptor(func(context.Context, payload.SetupPayload, rsocket.CloseableRSocket) (rsocket.RSocket, error) {
				return rsocket.NewAbstractSocket(rsocket.RequestChannel(handler)), nil
			}).
			Transport(serverTp).
			Serve(ctx)
	}()

	l := &link{}
	relay, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		serverLis.Close()
		t.Fatalf("net.Listen unexpected error: %v", err)
	}
	stopAll := func() {
		relay.Close()
		serverLis.Close()
		l.cut()
	}
	stopAfter := context.AfterFunc(ctx, stopAll)
	t.Cleanup(func() {
		stopAfter()
		stopAll()
	})

	go func() {
		for {
			down, err := relay.Accept()
			if err != nil {
				return
			}
			up, err := net.Dial("tcp", serverAddr)
			if err != nil {
				down.Close()
				continue
			}
			l.mu.Lock()
			l.conns = append(l.conns, down, up)
			l.mu.Unlock()
			go l.relay(up, down)
			go l.relay(down, up)
		}
	}()

	client, err := rsocket.Connect().
		SetupPayload(payload.New([]byte("test"), nil)).
		Transport(rsocket.TCPClient().SetAddr(relay.Addr().String()).Build()).
		Start(ctx)
	if err != nil {
		t.Fatalf("rsocket.Connect() unexpected error: %v", err)
	}
	stopClient := context.AfterFunc(ctx, func() { _ = client.Close() })
	t.Cleanup(func() {
		stopClient()
		_ = client.Close()
	})
	l.client = client
	return l
}

// reserveClosedPort binds an ephemeral IPv4 TCP socket without calling listen,
// holding the port for the lifetime of t while deterministically refusing dials.
func reserveClosedPort(t *testing.T) string {
	t.Helper()
	fd, err := syscall.Socket(syscall.AF_INET, syscall.SOCK_STREAM, 0)
	if err != nil {
		t.Fatalf("syscall.Socket unexpected error: %v", err)
	}
	t.Cleanup(func() { _ = syscall.Close(fd) })
	addr := &syscall.SockaddrInet4{Addr: [4]byte{127, 0, 0, 1}}
	if err := syscall.Bind(fd, addr); err != nil {
		t.Fatalf("syscall.Bind unexpected error: %v", err)
	}
	sa, err := syscall.Getsockname(fd)
	if err != nil {
		t.Fatalf("syscall.Getsockname unexpected error: %v", err)
	}
	return fmt.Sprintf("127.0.0.1:%d", sa.(*syscall.SockaddrInet4).Port)
}

// startDestination starts a TCP server that runs handle for each connection
// and returns its address.
func startDestination(t *testing.T, handle func(conn *net.TCPConn)) string {
	t.Helper()
	lis, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("net.Listen unexpected error: %v", err)
	}
	t.Cleanup(func() { lis.Close() })
	go func() {
		for {
			conn, err := lis.Accept()
			if err != nil {
				return
			}
			go func() {
				defer conn.Close()
				handle(conn.(*net.TCPConn))
			}()
		}
	}()
	return lis.Addr().String()
}

// conns collects the Conns of a test.
type conns struct {
	mu      sync.Mutex
	all     []*Conn
	refused atomic.Int32
}

func (cs *conns) add(c *Conn) {
	cs.mu.Lock()
	defer cs.mu.Unlock()
	cs.all = append(cs.all, c)
}

func (cs *conns) checkAllEnded() error {
	cs.mu.Lock()
	all := append([]*Conn(nil), cs.all...)
	cs.mu.Unlock()
	for _, c := range all {
		if !waitReturns(c, waitTime) {
			return fmt.Errorf("Wait() of Conn %v in state %v did not return within %v", c.conn.LocalAddr(), c.State(), waitTime)
		}
		if n := c.InvalidEvents(); n != 0 {
			return fmt.Errorf("Conn.InvalidEvents() of Conn %v = %d, want 0", c.conn.LocalAddr(), n)
		}
	}
	return nil
}

// responder returns a stream handler that joins each stream to a new
// connection to destination, as a conduit does.
func responder(ctx context.Context, destination string, cs *conns) func(flux.Flux) flux.Flux {
	return func(incoming flux.Flux) flux.Flux {
		done := func() { cs.refused.Add(1) }
		conn, err := net.Dial("tcp", destination)
		if err != nil {
			return Refuse(ctx, incoming, done)
		}
		rc, out, err := Accept(ctx, conn, incoming)
		if err != nil {
			conn.Close()
			return Refuse(ctx, incoming, done)
		}
		cs.add(rc)
		return out
	}
}

// requester joins a new client connection to a new stream of l, as a conduit
// does, and returns the client's end.
func requester(ctx context.Context, t *testing.T, l *link, cs *conns) *net.TCPConn {
	t.Helper()
	client, sock := tcpPair(t)
	rc, err := Open(ctx, sock, l.client.RequestChannel)
	if err != nil {
		t.Fatalf("Open() unexpected error: %v", err)
	}
	cs.add(rc)
	return client
}

// TestRSocket_TransferBothWays sends data in both directions through two
// Conns and rsocket-go, and ends the stream cleanly from the client.
func TestRSocket_TransferBothWays(t *testing.T) {
	ctx, cancel := context.WithCancel(t.Context())
	defer cancel()
	destination := startDestination(t, func(conn *net.TCPConn) {
		io.Copy(conn, conn)
		conn.CloseWrite()
	})
	var cs conns
	l := startLink(ctx, t, responder(ctx, destination, &cs))
	client := requester(ctx, t, l, &cs)

	body := make([]byte, 4<<20)
	for i := range body {
		body[i] = byte(i * 7)
	}
	go func() {
		client.Write(body)
		client.CloseWrite()
	}()
	client.SetReadDeadline(time.Now().Add(4 * waitTime))
	got, err := io.ReadAll(client)

	if err != nil {
		t.Fatalf("io.ReadAll(client) unexpected error after %d bytes: %v", len(got), err)
	}
	if !bytes.Equal(got, body) {
		t.Errorf("io.ReadAll(client) len = %d, want %d matching bytes", len(got), len(body))
	}
	if err := cs.checkAllEnded(); err != nil {
		t.Error(err)
	}
}

// TestRSocket_RefusedStream covers a destination that cannot be reached: the
// client must see a reset, and the stream must end on both sides.
func TestRSocket_RefusedStream(t *testing.T) {
	const streams = 100
	ctx, cancel := context.WithCancel(t.Context())
	defer cancel()
	unreachable := reserveClosedPort(t)
	var cs conns
	l := startLink(ctx, t, responder(ctx, unreachable, &cs))

	for i := range streams {
		client := requester(ctx, t, l, &cs)
		if _, err := client.Write([]byte("hello")); err != nil {
			t.Fatalf("client %d Write() unexpected error: %v", i, err)
		}
		client.SetReadDeadline(time.Now().Add(waitTime))
		if _, err := io.ReadAll(client); !errors.Is(err, syscall.ECONNRESET) {
			t.Fatalf("client %d ReadAll() err = %v, want %v", i, err, syscall.ECONNRESET)
		}
	}
	if err := cs.checkAllEnded(); err != nil {
		t.Error(err)
	}
	for deadline := time.Now().Add(waitTime); cs.refused.Load() < streams && time.Now().Before(deadline); time.Sleep(5 * time.Millisecond) {
	}
	if got := cs.refused.Load(); got != streams {
		t.Errorf("Responder Refuse done count = %d, want %d", got, streams)
	}
}

// TestRSocket_RefuseWithCanceledContextDoesNotPanicRequester checks that a
// Responder calling Refuse with an already-canceled context does not emit an
// RSocket CANCEL frame (which would panic rsocket-go on the Requester).
func TestRSocket_RefuseWithCanceledContextDoesNotPanicRequester(t *testing.T) {
	ctx, cancel := context.WithCancel(t.Context())
	defer cancel()
	canceledCtx, cancelNow := context.WithCancel(ctx)
	cancelNow()

	var refuseFirst atomic.Bool
	refuseFirst.Store(true)
	refusedDone := make(chan struct{})
	destination := startDestination(t, func(conn *net.TCPConn) {
		io.Copy(conn, conn)
		conn.CloseWrite()
	})
	var cs conns
	normal := responder(ctx, destination, &cs)
	l := startLink(ctx, t, func(incoming flux.Flux) flux.Flux {
		if refuseFirst.Swap(false) {
			return Refuse(canceledCtx, incoming, func() { close(refusedDone) })
		}
		return normal(incoming)
	})

	streamCtx, cancelStream := context.WithCancel(ctx)
	firstClient := requester(streamCtx, t, l, &cs)
	if _, err := firstClient.Write([]byte("hi")); err != nil {
		t.Fatalf("firstClient.Write unexpected error: %v", err)
	}
	select {
	case <-refusedDone:
	case <-time.After(waitTime):
		t.Fatalf("Refuse(canceledCtx) did not finish within %v", waitTime)
	}
	cancelStream()

	// The RSocket connection and Requester process must still be healthy.
	second := requester(ctx, t, l, &cs)
	if _, err := second.Write([]byte("ping")); err != nil {
		t.Fatalf("second.Write unexpected error: %v", err)
	}
	_ = second.CloseWrite()
	second.SetReadDeadline(time.Now().Add(waitTime))
	got, err := io.ReadAll(second)
	if err != nil || string(got) != "ping" {
		t.Errorf("second stream ReadAll() = (%q, %v), want (\"ping\", nil)", got, err)
	}
	if err := cs.checkAllEnded(); err != nil {
		t.Error(err)
	}
}

// TestRSocket_ConnectionLostUnderLiveStreams cuts the connection while
// streams send in both directions. Every Conn must end without panicking, and
// every local peer on both sides must see a reset.
func TestRSocket_ConnectionLostUnderLiveStreams(t *testing.T) {
	const (
		rounds  = 20
		streams = 8
	)
	chunk := bytes.Repeat([]byte("z"), 16<<10)
	send := func(conn *net.TCPConn) error {
		for {
			if _, err := conn.Write(chunk); err != nil {
				return err
			}
		}
	}
	type ioEnd struct{ read, write error }
	destConnected := make(chan struct{}, rounds*streams)
	destEnds := make(chan ioEnd, rounds*streams)
	destination := startDestination(t, func(conn *net.TCPConn) {
		destConnected <- struct{}{}
		writeErr := make(chan error, 1)
		go func() { writeErr <- send(conn) }()
		conn.SetReadDeadline(time.Now().Add(4 * waitTime))
		_, err := io.Copy(io.Discard, conn)
		destEnds <- ioEnd{read: err, write: <-writeErr}
	})

	for round := range rounds {
		ctx, cancel := context.WithCancel(t.Context())
		var cs conns
		l := startLink(ctx, t, responder(ctx, destination, &cs))
		clientEnds := make(chan ioEnd, streams)
		for range streams {
			client := requester(ctx, t, l, &cs)
			go func() {
				writeErr := make(chan error, 1)
				go func() { writeErr <- send(client) }()
				client.SetReadDeadline(time.Now().Add(4 * waitTime))
				_, err := io.Copy(io.Discard, client)
				clientEnds <- ioEnd{read: err, write: <-writeErr}
			}()
		}
		// Wait until all 8 streams have connected to the destination.
		for range streams {
			select {
			case <-destConnected:
			case <-time.After(waitTime):
				cancel()
				t.Fatalf("round %d: timed out waiting for streams to reach destination", round)
			}
		}
		time.Sleep(time.Duration(5+2*round) * time.Millisecond)

		l.cut()

		for range streams {
			if e := <-clientEnds; !errors.Is(e.read, syscall.ECONNRESET) && !errors.Is(e.write, syscall.ECONNRESET) {
				t.Errorf("round %d: client ended with (read=%v, write=%v), want ECONNRESET", round, e.read, e.write)
			}
			if e := <-destEnds; !errors.Is(e.read, syscall.ECONNRESET) && !errors.Is(e.write, syscall.ECONNRESET) {
				t.Errorf("round %d: destination ended with (read=%v, write=%v), want ECONNRESET", round, e.read, e.write)
			}
		}
		if err := cs.checkAllEnded(); err != nil {
			t.Errorf("round %d: %v", round, err)
		}
		cancel()
		if t.Failed() {
			break
		}
	}
}

// TestRSocket_StreamOpenedOnSilentConnection opens streams on a connection
// that has died without either side noticing. Each client sends several
// chunks, and then the connection closes, as it does when the keepalive of
// the Requester times out. Every Conn must end, and every client must see a
// reset.
//
// In versions 0.1.x the chunks after the first waited inside rsocket-go for a
// REQUEST_N that could not arrive, and the Complete of such a stream never
// returned once the connection had closed. The conduit waited for the stream
// and its port stayed closed (b/586742408).
func TestRSocket_StreamOpenedOnSilentConnection(t *testing.T) {
	const (
		rounds  = 10
		streams = 4
		chunks  = 4
	)
	chunk := bytes.Repeat([]byte("u"), 16<<10)
	destination := startDestination(t, func(conn *net.TCPConn) {
		_, _ = io.Copy(io.Discard, conn)
	})

	for round := range rounds {
		ctx, cancel := context.WithCancel(t.Context())
		var cs conns
		l := startLink(ctx, t, responder(ctx, destination, &cs))
		l.silence()

		clientEnds := make(chan error, streams)
		for range streams {
			client := requester(ctx, t, l, &cs)
			go func() {
				for range chunks {
					if _, err := client.Write(chunk); err != nil {
						clientEnds <- err
						return
					}
				}
				client.SetReadDeadline(time.Now().Add(4 * waitTime))
				_, err := io.Copy(io.Discard, client)
				clientEnds <- err
			}()
		}
		// The streams open with the first chunk of each client. The link
		// drops them, so no Responder ever answers.
		cs.mu.Lock()
		opened := append([]*Conn(nil), cs.all...)
		cs.mu.Unlock()
		for _, c := range opened {
			if got := waitState(c, StateEstablished); got != StateEstablished {
				cancel()
				t.Fatalf("round %d: Conn state = %v, want %v", round, got, StateEstablished)
			}
		}
		time.Sleep(time.Duration(5+2*round) * time.Millisecond)

		l.cut()

		for range streams {
			if err := <-clientEnds; !errors.Is(err, syscall.ECONNRESET) {
				t.Errorf("round %d: client ended with %v, want ECONNRESET", round, err)
			}
		}
		if err := cs.checkAllEnded(); err != nil {
			t.Errorf("round %d: %v", round, err)
		}
		cancel()
		if t.Failed() {
			break
		}
	}
}

// TestRSocket_RequestTravelsWithFirstPayload checks that the Responder may
// send as soon as the stream arrives: the request of the Requester for
// inbound payloads is part of the frame that opens the stream.
func TestRSocket_RequestTravelsWithFirstPayload(t *testing.T) {
	ctx, cancel := context.WithCancel(t.Context())
	defer cancel()
	requests := make(chan int, 16)
	l := startLink(ctx, t, func(incoming flux.Flux) flux.Flux {
		incoming.Subscribe(context.Background(), rx.OnSubscribe(func(_ context.Context, su rx.Subscription) { su.Request(rx.RequestMax) }))
		return flux.Create(func(_ context.Context, sink flux.Sink) {
			go func() {
				for _, s := range []string{"a", "b", "c"} {
					sink.Next(payload.New([]byte(s), nil))
				}
				sink.Complete()
			}()
		}).DoOnRequest(func(n int) { requests <- n })
	})

	out := flux.Create(func(_ context.Context, sink flux.Sink) {
		sink.Next(payload.New([]byte("first"), nil))
	})
	received := make(chan string, 16)
	l.client.RequestChannel(out).Subscribe(ctx,
		rx.OnSubscribe(func(_ context.Context, su rx.Subscription) { su.Request(rx.RequestMax) }),
		rx.OnNext(func(p payload.Payload) error {
			received <- string(p.Data())
			return nil
		}),
		rx.OnComplete(func() { close(received) }),
	)

	select {
	case n := <-requests:
		if n != rx.RequestMax {
			t.Errorf("Responder outgoing initial request = %d, want %d", n, rx.RequestMax)
		}
	case <-time.After(waitTime):
		t.Fatalf("Responder outgoing Flux not requested within %v", waitTime)
	}
	var got []string
	timeout := time.After(waitTime)
	for done := false; !done; {
		select {
		case s, ok := <-received:
			if !ok {
				done = true
				break
			}
			got = append(got, s)
		case <-timeout:
			t.Fatalf("Requester received %q before timeout (%v), want [a b c]", got, waitTime)
		}
	}
	if !equalStrings(got, []string{"a", "b", "c"}) {
		t.Errorf("Requester received %q, want [a b c]", got)
	}
}

// TestRSocket_LateRequestDoesNotDeliverHeldPayload shows why a Responder
// cannot hold a stream back by requesting late, for example until its
// destination is connected. A Responder that has subscribed to the inbound
// Flux without requesting sends no REQUEST_N, is handed nothing, and does not
// hold up the other streams of the connection. But its later request only
// sends the REQUEST_N: the payload that opened the stream stays inside
// reactor-go until another frame of the stream arrives. A client whose whole
// request fits into that first payload would wait for an answer that never
// comes.
func TestRSocket_LateRequestDoesNotDeliverHeldPayload(t *testing.T) {
	ctx, cancel := context.WithCancel(t.Context())
	defer cancel()
	var first atomic.Bool
	first.Store(true)
	held := make(chan rx.Subscription, 1)
	heldReceived := make(chan string, 16)
	echo := func(incoming flux.Flux) flux.Flux {
		return flux.Create(func(_ context.Context, sink flux.Sink) {
			incoming.Subscribe(context.Background(),
				rx.OnSubscribe(func(_ context.Context, su rx.Subscription) { su.Request(rx.RequestMax) }),
				rx.OnNext(func(p payload.Payload) error {
					sink.Next(payload.New(bytes.Clone(p.Data()), nil))
					return nil
				}),
			)
		})
	}
	l := startLink(ctx, t, func(incoming flux.Flux) flux.Flux {
		if !first.Swap(false) {
			return echo(incoming)
		}
		// The stream that is held: subscribed, nothing requested.
		incoming.Subscribe(context.Background(),
			rx.OnSubscribe(func(_ context.Context, su rx.Subscription) { held <- su }),
			rx.OnNext(func(p payload.Payload) error {
				heldReceived <- string(p.Data())
				return nil
			}),
		)
		return flux.Create(func(context.Context, flux.Sink) {})
	})

	// The stream that is held. Its requests are those rsocket-go makes on the
	// Requester: one of its own for the payload that opens the stream, then
	// one for each REQUEST_N of the Responder.
	requests := make(chan int, 16)
	heldSink := make(chan flux.Sink, 1)
	heldOut := flux.Create(func(_ context.Context, sink flux.Sink) {
		sink.Next(payload.New([]byte("first"), nil))
		heldSink <- sink
	}).DoOnRequest(func(n int) { requests <- n })
	l.client.RequestChannel(heldOut).Subscribe(ctx,
		rx.OnSubscribe(func(_ context.Context, su rx.Subscription) { su.Request(rx.RequestMax) }))

	var su rx.Subscription
	select {
	case su = <-held:
	case <-time.After(waitTime):
		t.Fatalf("held stream did not reach the Responder within %v", waitTime)
	}
	select {
	case n := <-requests:
		if n != 1 {
			t.Fatalf("first request on the Requester = %d, want 1 (from rsocket-go itself)", n)
		}
	case <-time.After(waitTime):
		t.Fatalf("Requester's outgoing Flux not requested within %v", waitTime)
	}

	// Another stream of the same connection makes a round trip meanwhile.
	echoed := make(chan string, 1)
	otherOut := flux.Create(func(_ context.Context, sink flux.Sink) {
		sink.Next(payload.New([]byte("other"), nil))
	})
	l.client.RequestChannel(otherOut).Subscribe(ctx,
		rx.OnSubscribe(func(_ context.Context, su rx.Subscription) { su.Request(rx.RequestMax) }),
		rx.OnNext(func(p payload.Payload) error {
			echoed <- string(p.Data())
			return nil
		}),
	)
	select {
	case got := <-echoed:
		if got != "other" {
			t.Errorf("other stream echoed %q, want %q", got, "other")
		}
	case <-time.After(waitTime):
		t.Fatalf("other stream made no round trip within %v while the first stream was held", waitTime)
	}

	// Still nothing on the held stream.
	select {
	case n := <-requests:
		t.Fatalf("Requester got a request for %d before the Responder requested", n)
	case got := <-heldReceived:
		t.Fatalf("Responder was handed %q before it requested", got)
	case <-time.After(10 * quietTime):
	}

	su.Request(rx.RequestMax)

	// The REQUEST_N reaches the Requester...
	select {
	case n := <-requests:
		if n < 1 {
			t.Errorf("request on the Requester after the Responder's request = %d, want >= 1", n)
		}
	case <-time.After(waitTime):
		t.Fatalf("Requester got no request within %v of the Responder's request", waitTime)
	}
	// ...but the Responder is not handed the payload it held back.
	select {
	case got := <-heldReceived:
		t.Fatalf("Responder was handed %q on its late request; reactor-go has changed, and a Responder may now hold a stream back by requesting late", got)
	case <-time.After(20 * quietTime):
	}

	// The next frame of the stream releases it.
	(<-heldSink).Next(payload.New([]byte("second"), nil))
	for _, want := range []string{"first", "second"} {
		select {
		case got := <-heldReceived:
			if got != want {
				t.Errorf("Responder was handed %q, want %q", got, want)
			}
		case <-time.After(waitTime):
			t.Fatalf("Responder was not handed %q within %v of the second payload", want, waitTime)
		}
	}
}
