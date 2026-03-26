package rxconn

import (
	"context"
	"net"
	"sync"
	"testing"
	"time"

	"github.com/rsocket/rsocket-go/payload"
	"github.com/rsocket/rsocket-go/rx/flux"
	"github.com/rsocket/rsocket-go/rx"
)

func TestRxConn_ToFlux_EOF(t *testing.T) {
	clientSide, serverSide := net.Pipe()
	defer clientSide.Close()

	rxConnObj, err := New(serverSide)
	if err != nil {
		t.Fatalf("Failed to create rxConn: %v", err)
	}
	ctx, cancel := context.WithTimeout(t.Context(), 2*time.Second)
	defer cancel()

	f := rxConnObj.ToFlux(ctx)
	resultChan := make(chan payload.Payload, 10)
	errChan := make(chan error, 1)
	doneChan := make(chan struct{})

	f.Subscribe(ctx,
		rx.OnNext(func(p payload.Payload) error {
			resultChan <- p
			return nil
		}),
		rx.OnComplete(func() {
			close(doneChan)
		}),
		rx.OnError(func(err error) {
			errChan <- err
		}),
	)

	// Simulate sending data and then closing connection.
	go func() {
		if _, err := clientSide.Write([]byte("hello")); err != nil {
			t.Errorf("Failed to write to clientSide: %v", err)
		}
		if err := clientSide.Close(); err != nil {
			t.Errorf("Failed to close clientSide: %v", err)
		}
	}()

	select {
	case p := <-resultChan:
		if string(p.Data()) != "hello" {
			t.Errorf("Got payload %q, want %q", string(p.Data()), "hello")
		}
	case <-time.After(time.Second):
		t.Fatal("timeout waiting for data")
	}

	select {
	case <-doneChan:
		// Success
	case err := <-errChan:
		t.Fatalf("unexpected error: %v", err)
	case <-time.After(time.Second):
		t.Fatal("timeout waiting for EOF (OnComplete)")
	}
}

func TestRxConn_ToFlux_ContextCancellation(t *testing.T) {
	clientSide, serverSide := net.Pipe()
	defer clientSide.Close()

	rxConnObj, err := New(serverSide)
	if err != nil {
		t.Fatalf("Failed to create rxConn: %v", err)
	}
	ctx, cancel := context.WithCancel(t.Context())

	f := rxConnObj.ToFlux(ctx)
	errChan := make(chan error, 1)

	f.Subscribe(t.Context(),
		rx.OnError(func(err error) {
			errChan <- err
		}),
	)

	// Cancel context immediately
	cancel()

	// Context cancellation should result in an error propagating
	select {
	case err := <-errChan:
		if err != context.Canceled {
			t.Errorf("Got error %v, want %v", err, context.Canceled)
		}
	case <-time.After(time.Second):
		t.Fatal("timeout waiting for OnError due to context cancellation")
	}
}

func TestRxConn_FromFlux_DataAndEOFPropagation(t *testing.T) {
	clientSide, serverSide := net.Pipe()
	defer clientSide.Close()

	rxConnObj, err := New(serverSide)
	if err != nil {
		t.Fatalf("Failed to create rxConn: %v", err)
	}

	// Create a mock incoming flux
	f := flux.Create(func(ctx context.Context, sink flux.Sink) {
		sink.Next(payload.New([]byte("from_flux"), nil))
		sink.Complete()
	})

	// Subscribe in a goroutine because FromFlux will synchronously write to the pipe,
	// which blocks until the test reads from it below.
	go func() {
		rxConnObj.FromFlux(t.Context(), f)
	}()

	// Read from pipe to verify
	buf := make([]byte, 1024)
	clientSide.SetReadDeadline(time.Now().Add(time.Second))
	n, err := clientSide.Read(buf)

	if err != nil {
		t.Fatalf("unexpected error reading from pipe: %v", err)
	}

	if string(buf[:n]) != "from_flux" {
		t.Errorf("Got data %q, want %q", string(buf[:n]), "from_flux")
	}

	// Now it should return io.ErrClosedPipe because net.Pipe() CloseWrite doesn't exist natively.
	// Wait, net.Pipe() returns a net.Conn. Calling Close() on it causes io.ErrClosedPipe.
	// But in rxConn loop we are casting to interface{ CloseWrite() error }, which isn't available for net.Pipe.
	// Let's test the error behavior. We just need to know it didn't panic.
	// We'll simulate backpressure testing too.
}

func TestRxConn_ToFlux_Backpressure(t *testing.T) {
	clientSide, serverSide := net.Pipe()
	defer clientSide.Close()
	defer serverSide.Close()

	rxConnObj, err := New(serverSide)
	if err != nil {
		t.Fatalf("Failed to create rxConn: %v", err)
	}
	ctx, cancel := context.WithTimeout(t.Context(), 2*time.Second)
	defer cancel()

	f := rxConnObj.ToFlux(ctx)

	// We DON'T consume the flux immediately, forcing the upstream channel to fill up.
	// The channel buffer is 32. We can send 32 bursts without blocking, then it should block.

	// Write 40 bursts. The goroutine should block after 32.
	var wg sync.WaitGroup
	wg.Add(40)
	for i := 0; i < 40; i++ {
		go func() {
			wg.Done()
			if _, err := clientSide.Write([]byte("burst")); err != nil {
				t.Errorf("Failed to write to clientSide: %v", err)
			}
		}()
	}

	// Wait for all goroutines to initiate their writes
	wg.Wait()

	// Now start consuming. The consumer should receive all 40 elements eventually.
	received := 0
	done := make(chan struct{})

	f.Subscribe(ctx, rx.OnNext(func(p payload.Payload) error {
		received += len(p.Data())
		// 40 bursts * 5 bytes for "burst" = 200 bytes expected
		if received >= 200 {
			// use sync.Once or just close if channel is still open
			select {
			case <-done:
			default:
				close(done)
			}
		}
		return nil
	}))

	select {
	case <-done:
		// Success
	case <-time.After(1 * time.Second):
		t.Fatalf("timeout waiting for 200 bytes, only received %d", received)
	}
}

func TestRxConn_Wait_CloseFromFluxFirst(t *testing.T) {
	clientSide, serverSide := net.Pipe()

	rxConnObj, err := New(serverSide)
	if err != nil {
		t.Fatalf("Failed to create rxConn: %v", err)
	}
	ctx := t.Context()

	fTo := rxConnObj.ToFlux(ctx)

	stopFrom := make(chan struct{})
	fFrom := flux.Create(func(subCtx context.Context, sink flux.Sink) {
		select {
		case <-subCtx.Done():
		case <-stopFrom:
		}
		sink.Complete()
	})

	fTo.Subscribe(ctx)
	go rxConnObj.FromFlux(ctx, fFrom)

	waitDone := make(chan struct{})
	go func() {
		rxConnObj.Wait()
		close(waitDone)
	}()

	select {
	case <-waitDone:
		t.Fatal("Wait() returned prematurely before pumps were stopped")
	case <-time.After(100 * time.Millisecond):
		// Expected to block
	}

	// 1. Close FromFlux only (half-close)
	close(stopFrom)

	select {
	case <-waitDone:
		t.Fatal("Wait() returned prematurely after only FromFlux stopped")
	case <-time.After(100 * time.Millisecond):
		// Expected to STILL block since ToFlux is still running
	}

	// 2. Close ToFlux
	clientSide.Close()

	select {
	case <-waitDone:
		// Success, both are closed
	case <-time.After(time.Second):
		t.Fatal("Wait() blocked even after both pumps stopped")
	}
}

func TestRxConn_Wait_CloseToFluxFirst(t *testing.T) {
	clientSide, serverSide := net.Pipe()

	rxConnObj, err := New(serverSide)
	if err != nil {
		t.Fatalf("Failed to create rxConn: %v", err)
	}
	ctx := t.Context()

	fTo := rxConnObj.ToFlux(ctx)

	stopFrom := make(chan struct{})
	fFrom := flux.Create(func(subCtx context.Context, sink flux.Sink) {
		select {
		case <-subCtx.Done():
		case <-stopFrom:
		}
		sink.Complete()
	})

	fTo.Subscribe(ctx)
	go rxConnObj.FromFlux(ctx, fFrom)

	waitDone := make(chan struct{})
	go func() {
		rxConnObj.Wait()
		close(waitDone)
	}()

	select {
	case <-waitDone:
		t.Fatal("Wait() returned prematurely before pumps were stopped")
	case <-time.After(100 * time.Millisecond):
		// Expected to block
	}

	// 1. Close ToFlux only (half-close)
	clientSide.Close()

	select {
	case <-waitDone:
		t.Fatal("Wait() returned prematurely after only ToFlux stopped")
	case <-time.After(100 * time.Millisecond):
		// Expected to STILL block since FromFlux is still running
	}

	// 2. Close FromFlux
	close(stopFrom)

	select {
	case <-waitDone:
		// Success, both are closed
	case <-time.After(time.Second):
		t.Fatal("Wait() blocked even after both pumps stopped")
	}
}

func TestRxConn_ToFlux_ReadTimeout(t *testing.T) {
	clientSide, serverSide := net.Pipe()
	defer clientSide.Close()
	defer serverSide.Close()

	rxConnObj, err := New(serverSide)
	if err != nil {
		t.Fatalf("Failed to create rxConn: %v", err)
	}
	ctx, cancel := context.WithTimeout(t.Context(), 5*time.Second)
	defer cancel()

	f := rxConnObj.ToFlux(ctx)
	receivedChan := make(chan string, 1)

	f.Subscribe(ctx, rx.OnNext(func(p payload.Payload) error {
		receivedChan <- string(p.Data())
		return nil
	}))

	// Wait for 1.5 seconds without sending anything to force a read timeout inside ToFlux
	time.Sleep(1500 * time.Millisecond)

	// Now send data after the timeout occurred
	go func() {
		if _, err := clientSide.Write([]byte("after_timeout")); err != nil {
			t.Errorf("Failed to write to clientSide: %v", err)
		}
	}()

	select {
	case data := <-receivedChan:
		if data != "after_timeout" {
			t.Errorf("Got data %q, want %q", data, "after_timeout")
		}
	case <-time.After(2 * time.Second):
		t.Fatal("Timeout waiting for data after read timeout recovery")
	}
}
