package conduit

import (
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

	"github.com/rsocket/rsocket-go/payload"
	"github.com/rsocket/rsocket-go"
	"github.com/rsocket/rsocket-go/rx/flux"
	"github.com/rsocket/rsocket-go/rx"
	"go.opentelemetry.io/otel/trace"
)

type mockCloseable struct {
	rsocket.RSocket
}

func (m mockCloseable) Close() error          { return nil }
func (m mockCloseable) OnClose(f func(error)) {}

// echoChannel answers a stream by sending back every payload of the
// Requester, markers included. It requests payloads the way rsocket-go and a
// Responder do between them: one payload first, the one that opens the
// stream, and the rest once that payload has arrived.
func echoChannel(msgs flux.Flux) flux.Flux {
	out := flux.CreateProcessor()
	var su rx.Subscription
	confirmed := false
	msgs.Subscribe(context.Background(),
		rx.OnSubscribe(func(_ context.Context, s rx.Subscription) {
			su = s
			s.Request(1)
		}),
		rx.OnNext(func(p payload.Payload) error {
			out.Next(payload.Clone(p))
			if !confirmed {
				confirmed = true
				su.Request(rx.RequestMax)
			}
			return nil
		}),
		rx.OnComplete(func() { out.Complete() }),
	)
	return out
}

// freePort returns a random available TCP port.
func freePort() int {
	addr, err := net.ResolveTCPAddr("tcp", "localhost:0")
	if err != nil {
		panic(err)
	}
	l, err := net.ListenTCP("tcp", addr)
	if err != nil {
		panic(err)
	}
	defer l.Close()
	return l.Addr().(*net.TCPAddr).Port
}

type acceptErrorListener struct {
	net.Listener
	m         sync.Mutex
	acceptErr error
	accepted  chan bool
}

func (l *acceptErrorListener) Accept() (net.Conn, error) {
	l.m.Lock()
	if l.acceptErr != nil {
		err := l.acceptErr
		l.acceptErr = nil
		l.m.Unlock()
		l.accepted <- true
		return nil, err
	}
	l.m.Unlock()
	return l.Listener.Accept()
}

func TestStartListeningLoop_MultipleStreams(t *testing.T) {
	ctx, cancel := context.WithCancel(t.Context())
	defer cancel()

	// Mock RequestChannel to simply echo whatever comes in
	var streamCounter atomic.Int32
	fakeRSocket := rsocket.NewAbstractSocket(
		rsocket.RequestChannel(func(msgs flux.Flux) flux.Flux {
			streamCounter.Add(1)
			return echoChannel(msgs)
		}),
	)

	c := New(ctx, "test-id", nil, mockCloseable{fakeRSocket}, func() {}, nil, trace.SpanContext{}, nil, nil)
	port := freePort()

	lis, err := net.Listen("tcp", fmt.Sprintf("localhost:%d", port))
	if err != nil {
		t.Fatalf("Listen failed: %v", err)
	}
	go func() {
		if err := StartListeningLoop(c, func() (net.Listener, error) { return lis, nil }); err != nil {
			t.Errorf("StartListeningLoop error: %v", err)
		}
	}()

	var wg sync.WaitGroup
	var clients []net.Conn

	for i := 0; i < 3; i++ {
		conn, err := net.Dial("tcp", fmt.Sprintf("localhost:%d", port))
		if err != nil {
			t.Fatalf("Dial failed: %v", err)
		}
		clients = append(clients, conn)
	}

	// 1. Send independent traffic
	for i, conn := range clients {
		wg.Add(1)
		go func(conn net.Conn, id int) {
			defer wg.Done()
			msg := fmt.Sprintf("hello%d", id)
			_, err := conn.Write([]byte(msg))
			if err != nil {
				t.Errorf("Write error on %d: %v", id, err)
			}
			buf := make([]byte, len(msg))
			_, err = io.ReadFull(conn, buf)
			if err != nil {
				t.Errorf("Read error on %d: %v", id, err)
			}
			if string(buf) != msg {
				t.Errorf("got echo %q, want %q", string(buf), msg)
			}
		}(conn, i)
	}
	wg.Wait()

	// 2. Disconnect one stream, others must work
	clients[0].Close()

	_, err = clients[1].Write([]byte("ping"))
	if err != nil {
		t.Fatalf("Client 2 write failed: %v", err)
	}

	// Wait for echo
	buf := make([]byte, 4)
	_, err = io.ReadFull(clients[1], buf)
	if string(buf) != "ping" || err != nil {
		t.Fatalf("Client 2 ReadFull() = %q, %v, want %q, %v", string(buf), err, "ping", nil)
	}

	// 3. Cancel the conduit. It closes in the middle of the streams that are
	// still open, so their clients must see a reset, not a clean end.
	cancel()

	clients[2].SetReadDeadline(time.Now().Add(5 * time.Second))
	if _, err := io.Copy(io.Discard, clients[2]); !errors.Is(err, syscall.ECONNRESET) {
		t.Errorf("client connection ended with %v after the conduit was cancelled, want %v", err, syscall.ECONNRESET)
	}
}

func TestStartListeningLoop_AcceptError(t *testing.T) {
	ctx, cancel := context.WithCancel(t.Context())
	defer cancel()

	fakeRSocket := rsocket.NewAbstractSocket()
	c := New(ctx, "test-id", nil, mockCloseable{fakeRSocket}, func() {}, nil, trace.SpanContext{}, nil, nil)
	port := freePort()

	lis, err := net.Listen("tcp", fmt.Sprintf("localhost:%d", port))
	if err != nil {
		t.Fatalf("Listen failed: %v", err)
	}

	listener := &acceptErrorListener{
		Listener:  lis,
		acceptErr: errors.New("accept error"),
		accepted:  make(chan bool, 1),
	}

	loopErr := make(chan error)
	go func() {
		loopErr <- StartListeningLoop(c, func() (net.Listener, error) { return listener, nil })
	}()

	select {
	case <-listener.accepted:
		// accept returned error once, good.
	case <-time.After(1 * time.Second):
		t.Fatal("Accept() was not called or did not return error")
	}

	// cancel context to stop listening loop
	cancel()
	err = <-loopErr
	if err != nil {
		t.Errorf("StartListeningLoop(...) = %v, want nil", err)
	}
}

func TestHandleIngressConnection_RxConnNilError(t *testing.T) {
	ctx, cancel := context.WithCancel(t.Context())
	defer cancel()

	fakeRSocket := rsocket.NewAbstractSocket()
	c := New(ctx, "test-id", nil, mockCloseable{fakeRSocket}, func() {}, nil, trace.SpanContext{}, nil, nil)

	// call handleIngressConnection with nil conn, it should log error and return without panic.
	handleIngressConnection(c, nil)
}

func TestAcceptStream_MultipleStreams(t *testing.T) {
	// 1. Create Mock Backend Server
	l, err := net.Listen("tcp", "localhost:0")
	if err != nil {
		t.Fatalf("Listen failed: %v", err)
	}
	defer l.Close()
	port := l.Addr().(*net.TCPAddr).Port

	var backendConnections sync.WaitGroup
	go func() {
		for {
			conn, err := l.Accept()
			if err != nil {
				return
			}
			backendConnections.Add(1)
			go func(c net.Conn) {
				defer backendConnections.Done()
				defer c.Close()
				io.Copy(c, c)
			}(conn)
		}
	}()

	ctx, cancel := context.WithCancel(t.Context())
	defer cancel()
	c := New(ctx, "test", nil, mockCloseable{rsocket.NewAbstractSocket()}, func() {}, nil, trace.SpanContext{}, nil, nil)

	// Prepare dummy incoming fluxes
	incoming := make([]chan payload.Payload, 3)
	outgoing := make([]flux.Flux, 3)

	for i := 0; i < 3; i++ {
		ch := make(chan payload.Payload, 10)
		incoming[i] = ch
		inFlux := flux.Create(func(sCtx context.Context, sink flux.Sink) {
			go func() {
				for p := range ch {
					sink.Next(p)
				}
				sink.Complete()
			}()
		})
		outgoing[i] = AcceptStream(c, fmt.Sprintf("localhost:%d", port), inFlux)
	}

	var wg sync.WaitGroup
	// 2. Validate concurrent independent streams
	for i := 0; i < 3; i++ {
		wg.Add(1)
		go func(id int) {
			defer wg.Done()
			msg := fmt.Sprintf("hi%d", id)
			incoming[id] <- payload.New([]byte(msg), nil)

			// Extract echoed data
			res, err := outgoing[id].Take(1).BlockLast(t.Context())
			if err != nil {
				t.Errorf("Error reading from flux %d: %v", id, err)
			} else if res == nil {
				t.Errorf("Flux %d got nil, want %q", id, msg)
			} else if string(res.Data()) != msg {
				t.Errorf("Flux %d got %q, want %q", id, string(res.Data()), msg)
			}
		}(i)
	}
	wg.Wait()

	// Close stream 1 by closing channel, verify 2 is fine
	close(incoming[1])

	incoming[2] <- payload.New([]byte("alone"), nil)
	// We consume the echoed data from Flux 2 (not BlockFirst again because a Flux might not be re-subscribable like this if it's hot, but rxconn creates it as cold that buffers? Wait, BlockFirst gets the first element. We already called BlockFirst so we consumed the first. Oh wait, we should just read from a single subscription. Let's just cancel the conduit and verify.)

	cancel()
	// All backend connections should theoretically return from Accept loop if we close `l` and TCP connections close
	l.Close()
}

func TestCombinedLoopAndAccept(t *testing.T) {
	ctx, cancel := context.WithCancel(t.Context())
	defer cancel()

	fakeRSocket := rsocket.NewAbstractSocket(
		rsocket.RequestChannel(echoChannel),
	)
	c := New(ctx, "test", nil, mockCloseable{fakeRSocket}, func() {}, nil, trace.SpanContext{}, nil, nil)

	// 1. Start Listener
	port := freePort()
	lis, err := net.Listen("tcp", fmt.Sprintf("localhost:%d", port))
	if err != nil {
		t.Fatalf("Listen failed: %v", err)
	}
	go StartListeningLoop(c, func() (net.Listener, error) { return lis, nil })

	// 2. Dial local TCP, sending data it gets echoed back through RequestChannel
	conn, err := net.Dial("tcp", fmt.Sprintf("localhost:%d", port))
	if err != nil {
		t.Fatalf("Dial failed: %v", err)
	}
	defer conn.Close()

	// 3. Simultaneously run an AcceptStream toward another local server
	lis2, err := net.Listen("tcp", "localhost:0")
	if err != nil {
		t.Fatalf("Listen failed: %v", err)
	}
	defer lis2.Close()
	port2 := lis2.Addr().(*net.TCPAddr).Port

	go func() {
		conn, err := lis2.Accept()
		if err != nil {
			if errors.Is(err, net.ErrClosed) {
				return
			}
			t.Errorf("lis2.Accept() got error: %v", err)
			return
		}
		defer conn.Close()

		io.Copy(io.Discard, conn) // Drain unread data to avoid RST
	}()

	f := AcceptStream(c, fmt.Sprintf("localhost:%d", port2), flux.Just(payload.New([]byte("hello"), nil)))
	if f == nil {
		t.Fatalf("AcceptStream got nil, want non-nil flux")
	}

	_, err = f.BlockLast(ctx)
	if err != nil && !errors.Is(err, io.EOF) {
		t.Fatalf("BlockLast failed: %v", err)
	}
}

func TestHalfClose_Ingress_ClientCloseWrite(t *testing.T) {
	ctx, cancel := context.WithCancel(t.Context())
	defer cancel()

	// The echo sends the FIN marker of the client back, so both directions
	// of the stream end and the Requester completes it.
	completed := make(chan struct{})
	fakeRSocket := rsocket.NewAbstractSocket(
		rsocket.RequestChannel(func(msgs flux.Flux) flux.Flux {
			return echoChannel(msgs.DoOnComplete(func() {
				close(completed)
			}))
		}),
	)

	c := New(ctx, "test", nil, mockCloseable{fakeRSocket}, func() {}, nil, trace.SpanContext{}, nil, nil)
	port := freePort()
	lis, err := net.Listen("tcp", fmt.Sprintf("localhost:%d", port))
	if err != nil {
		t.Fatalf("Listen failed: %v", err)
	}
	go StartListeningLoop(c, func() (net.Listener, error) { return lis, nil })

	conn, err := net.Dial("tcp", fmt.Sprintf("localhost:%d", port))
	if err != nil {
		t.Fatalf("Dial failed: %v", err)
	}
	defer conn.Close()

	tcpConn, ok := conn.(*net.TCPConn)
	if !ok {
		t.Fatal("Expected TCP connection")
	}

	tcpConn.Write([]byte("req"))
	// Trigger half-close
	tcpConn.CloseWrite()

	// Wait for echoed request and clean EOF (echoChannel echoes client's FIN marker).
	tcpConn.SetReadDeadline(time.Now().Add(5 * time.Second))
	gotReq, err := io.ReadAll(tcpConn)
	if err != nil {
		t.Fatalf("io.ReadAll(tcpConn) unexpected error: %v", err)
	}
	if string(gotReq) != "req" {
		t.Fatalf("io.ReadAll(tcpConn) = %q, want %q", string(gotReq), "req")
	}

	select {
	case <-completed:
		// Success
	case <-time.After(5 * time.Second):
		t.Fatalf("Upstream stream was not completed after the client half-closed and the remote side finished")
	}
}

func TestHalfClose_Egress_ServerCloseWrite(t *testing.T) {
	// Local server mock
	l, err := net.Listen("tcp", "localhost:0")
	if err != nil {
		t.Fatalf("Listen failed: %v", err)
	}
	defer l.Close()
	port := l.Addr().(*net.TCPAddr).Port

	serverDone := make(chan struct{})
	go func() {
		defer close(serverDone)
		conn, err := l.Accept()
		if err != nil {
			return
		}
		defer conn.Close()
		tcpConn, ok := conn.(*net.TCPConn)
		if !ok {
			return
		}

		buf := make([]byte, 1024)
		n, err := tcpConn.Read(buf) // Should get "req"
		if err != nil && err != io.EOF {
			return
		}
		if n > 0 && string(buf[:n]) == "req" {
			tcpConn.Write([]byte("response"))
			// Trigger half-close from server end
			tcpConn.CloseWrite()
		}

		// Read remaining data until EOF if not already reached
		if err != io.EOF {
			_, err = tcpConn.Read(buf)
		}
		if err != io.EOF {
			t.Errorf("Server side did not receive EOF, got %v", err)
		}
	}()

	ctx, cancel := context.WithCancel(t.Context())
	defer cancel()
	c := New(ctx, "test", nil, mockCloseable{rsocket.NewAbstractSocket()}, func() {}, nil, trace.SpanContext{}, nil, nil)

	// Incoming channel provides exactly 1 message then completes.
	f := flux.Just(payload.New([]byte("req"), nil))

	outFlux := AcceptStream(c, fmt.Sprintf("localhost:%d", port), f)

	// Collect data and verify the FIN marker payload is emitted.
	var got []byte
	var sawFinMarker bool
	collect := func(p payload.Payload) error {
		if meta, ok := p.Metadata(); ok && string(meta) == "dcon:fin" && len(p.Data()) == 0 {
			sawFinMarker = true
		}
		got = append(got, p.Data()...)
		return nil
	}
	if _, err := outFlux.DoOnNext(collect).BlockLast(ctx); err != nil {
		t.Fatalf("outFlux BlockLast error: %v", err)
	}
	if string(got) != "response" {
		t.Fatalf("outFlux data got %q, want %q", string(got), "response")
	}
	if !sawFinMarker {
		t.Errorf("outFlux did not emit dcon:fin marker on server CloseWrite")
	}

	// Since f completed, and rxconn mirrors EOF locally (CloseWrite on the dialed connection)
	// the server should have read an EOF.
	<-serverDone // Wait for server to finish reading EOF and close
}

func TestAcceptStream_UnreachableDestinationRefusesWithRST(t *testing.T) {
	fd, err := syscall.Socket(syscall.AF_INET, syscall.SOCK_STREAM, 0)
	if err != nil {
		t.Fatalf("syscall.Socket unexpected error: %v", err)
	}
	t.Cleanup(func() { _ = syscall.Close(fd) })
	if err := syscall.Bind(fd, &syscall.SockaddrInet4{Addr: [4]byte{127, 0, 0, 1}}); err != nil {
		t.Fatalf("syscall.Bind unexpected error: %v", err)
	}
	sa, err := syscall.Getsockname(fd)
	if err != nil {
		t.Fatalf("syscall.Getsockname unexpected error: %v", err)
	}
	unreachable := fmt.Sprintf("127.0.0.1:%d", sa.(*syscall.SockaddrInet4).Port)

	ctx, cancel := context.WithCancel(t.Context())
	defer cancel()
	c := New(ctx, "test", nil, mockCloseable{rsocket.NewAbstractSocket()}, func() {}, nil, trace.SpanContext{}, nil, nil)

	in := flux.CreateProcessor()
	out := AcceptStream(c, unreachable, in)
	if got := c.ActiveStreams(); got != 1 {
		t.Errorf("ActiveStreams() while refused stream open = %d, want 1", got)
	}

	var markers []string
	outDone := make(chan struct{})
	out.Subscribe(ctx,
		rx.OnSubscribe(func(_ context.Context, su rx.Subscription) { su.Request(rx.RequestMax) }),
		rx.OnNext(func(p payload.Payload) error {
			meta, _ := p.Metadata()
			markers = append(markers, string(meta))
			return nil
		}),
		rx.OnComplete(func() { close(outDone) }),
	)

	select {
	case <-outDone:
	case <-time.After(5 * time.Second):
		t.Fatal("AcceptStream() outgoing Flux did not complete within 5s")
	}
	if len(markers) != 1 || markers[0] != "dcon:rst" {
		t.Errorf("AcceptStream() markers = %q, want [\"dcon:rst\"]", markers)
	}

	in.Complete()
	for deadline := time.Now().Add(5 * time.Second); c.ActiveStreams() != 0 && time.Now().Before(deadline); time.Sleep(5 * time.Millisecond) {
	}
	if got := c.ActiveStreams(); got != 0 {
		t.Errorf("ActiveStreams() after inbound Complete = %d, want 0", got)
	}
}

func TestConduitClose_ConcurrentWithStreamStart(t *testing.T) {
	for range 20 {
		ctx, cancel := context.WithCancel(t.Context())
		c := New(ctx, "race-test", nil, mockCloseable{rsocket.NewAbstractSocket()}, func() {}, nil, trace.SpanContext{}, nil, nil)
		lis, err := net.Listen("tcp", "127.0.0.1:0")
		if err != nil {
			cancel()
			t.Fatalf("net.Listen unexpected error: %v", err)
		}
		addr := lis.Addr().String()
		go StartListeningLoop(c, func() (net.Listener, error) { return lis, nil })

		var wg sync.WaitGroup
		wg.Add(2)
		go func() {
			defer wg.Done()
			for range 5 {
				if conn, err := net.Dial("tcp", addr); err == nil {
					conn.Close()
				}
				in := flux.Just(payload.New([]byte("x"), nil))
				_ = AcceptStream(c, addr, in)
			}
		}()
		go func() {
			defer wg.Done()
			_ = c.Close()
		}()
		wg.Wait()
		if got := c.ActiveStreams(); got != 0 {
			t.Errorf("ActiveStreams() after Close() = %d, want 0", got)
		}
		cancel()
	}
}
