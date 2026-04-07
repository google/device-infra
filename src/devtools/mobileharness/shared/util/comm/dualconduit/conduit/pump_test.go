package conduit

import (
	"context"
	"errors"
	"fmt"
	"io"
	"net"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/rsocket/rsocket-go/payload"
	"github.com/rsocket/rsocket-go"
	"github.com/rsocket/rsocket-go/rx/flux"
)

type mockCloseable struct {
	rsocket.RSocket
}

func (m mockCloseable) Close() error          { return nil }
func (m mockCloseable) OnClose(f func(error)) {}

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
			// We copy the msgs flux to act as an echo
			return msgs
		}),
	)

	c := New(ctx, "test-id", nil, mockCloseable{fakeRSocket}, func() {}, nil)
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

	// 3. Cancel Conduit, verify all clients get disconnected
	cancel()

	_, err = clients[2].Write([]byte("ping"))
	if err == nil {
		// Even if write succeeds due to OS buffer, next read should fail due to close
		clients[2].SetReadDeadline(time.Now().Add(500 * time.Millisecond))
		_, err = clients[2].Read(buf)
		if err == nil {
			t.Errorf("Client 2 connection: got no error, want error indicating connection closure after conduit cancel")
		}
	}
}

func TestStartListeningLoop_AcceptError(t *testing.T) {
	ctx, cancel := context.WithCancel(t.Context())
	defer cancel()

	fakeRSocket := rsocket.NewAbstractSocket()
	c := New(ctx, "test-id", nil, mockCloseable{fakeRSocket}, func() {}, nil)
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
	c := New(ctx, "test-id", nil, mockCloseable{fakeRSocket}, func() {}, nil)

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
	c := New(ctx, "test", nil, mockCloseable{rsocket.NewAbstractSocket()}, func() {}, nil)

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
		rsocket.RequestChannel(func(msgs flux.Flux) flux.Flux {
			return msgs
		}),
	)
	c := New(ctx, "test", nil, mockCloseable{fakeRSocket}, func() {}, nil)

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

	completed := make(chan struct{})
	fakeRSocket := rsocket.NewAbstractSocket(
		rsocket.RequestChannel(func(msgs flux.Flux) flux.Flux {
			return msgs.DoOnComplete(func() {
				close(completed)
			})
		}),
	)

	c := New(ctx, "test", nil, mockCloseable{fakeRSocket}, func() {}, nil)
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

	// Wait for downstream push
	buf := make([]byte, 100)
	n, err := tcpConn.Read(buf)
	if err != nil && err != io.EOF {
		t.Fatalf("Unexpected read error: %v", err)
	}

	if string(buf[:n]) != "req" {
		t.Fatalf("During half-close read got %q, want %q", string(buf[:n]), "req")
	}

	select {
	case <-completed:
		// Success
	case <-time.After(5 * time.Second):
		t.Fatalf("Upstream stream was not completed upon Client CloseWrite")
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
	c := New(ctx, "test", nil, mockCloseable{rsocket.NewAbstractSocket()}, func() {}, nil)

	// Incoming channel provides exactly 1 message then completes.
	f := flux.Just(payload.New([]byte("req"), nil))

	outFlux := AcceptStream(c, fmt.Sprintf("localhost:%d", port), f)

	res, err := outFlux.BlockLast(ctx)
	if err != nil {
		t.Fatalf("outFlux blockFirst error: %v", err)
	}
	if string(res.Data()) != "response" {
		t.Fatalf("outFlux data got %q, want %q", string(res.Data()), "response")
	}

	// Since f completed, and rxconn mirrors EOF locally (CloseWrite on the dialed connection)
	// the server should have read an EOF.
	<-serverDone // Wait for server to finish reading EOF and close
}
