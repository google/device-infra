package conduit

import (
	"context"
	"log/slog"
	"net"

	"github.com/rsocket/rsocket-go/payload"
	"github.com/rsocket/rsocket-go/rx/flux"
	"github.com/rsocket/rsocket-go/rx"

	"github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/rxconn"
)

// StartListeningLoop manages the Requester role: accepts connections on the listener, and routes them to Responder.
func StartListeningLoop(c *Conduit, listen func() (net.Listener, error)) error {
	lis, err := listen()
	if err != nil {
		return err
	}

	defer lis.Close()
	// Shut down the listener if the conduit context cancels.
	go func() {
		<-c.Context().Done()
		lis.Close()
	}()

	for {
		conn, err := lis.Accept()
		if err != nil {
			// Stop loop if listening socket closed.
			select {
			case <-c.Context().Done():
				return nil
			default:
			}
			slog.Error("StartListeningLoop Accept error", "error", err)
			continue
		}

		c.activeConnections.Add(1)
		go func() {
			defer c.activeConnections.Done()
			handleIngressConnection(c, conn)
		}()
	}
}

func handleIngressConnection(c *Conduit, conn net.Conn) {
	var remoteAddr string
	if conn != nil {
		remoteAddr = conn.RemoteAddr().String()
	} else {
		remoteAddr = "<nil>"
	}
	slog.Info("Conduit handling ingress connection", "id", c.ID, "remote_addr", remoteAddr)
	rc, err := rxconn.New(conn)
	if err != nil {
		slog.Error("rxconn.New error", "id", c.ID, "error", err)
		return
	}

	streamCtx, cancel := context.WithCancel(c.Context())
	defer cancel()

	// Ensure connection is closed when context is cancelled to unblock Wait and Read
	go func() {
		<-streamCtx.Done()
		conn.Close()
	}()

	// 1. Wrap network into Flux.
	upstream := rc.ToFlux(streamCtx)

	// 2. Initiate the RequestChannel over RSocket.
	downstream := c.rsocket.RequestChannel(upstream)

	// 3. Send downstream flux to TCP socket.
	rc.FromFlux(streamCtx, downstream)

	// Wait until both pumps finish.
	rc.Wait()
	slog.Info("Conduit ingress connection finished", "id", c.ID, "remote_addr", remoteAddr)
}

// AcceptStream handles an incoming RSocket channel, dials the target service, and bridges the streams.
// This implements the Responder Role.
func AcceptStream(c *Conduit, destEndpoint string, downstream flux.Flux) flux.Flux {
	slog.Info("Conduit AcceptStream: Dialing backend", "id", c.ID, "backend", destEndpoint)
	conn, err := net.Dial("tcp", destEndpoint)
	if err != nil {
		slog.Error("Conduit AcceptStream: Dial backend failed", "id", c.ID, "backend", destEndpoint, "error", err)
		return flux.Create(func(ctx context.Context, s flux.Sink) {
			// Subscribe and drain downstream Flux to activate RSocket's internal DoFinally
			// and unblock the unicast processor's Complete() call on Dialer's completion.
			downstream.Subscribe(ctx,
				rx.OnNext(func(p payload.Payload) error {
					return nil // Discard incoming frames
				}),
				rx.OnComplete(func() {}),
				rx.OnError(func(e error) {}),
			)
			s.Error(err) // Propagate the dial error immediately
		})
	}
	slog.Info("Conduit AcceptStream: Connected to backend", "id", c.ID, "backend", destEndpoint)

	rc, err := rxconn.New(conn)
	if err != nil {
		slog.Error("Conduit AcceptStream: rxconn.New failed", "id", c.ID, "error", err)
		conn.Close()
		return flux.Error(err)
	}

	streamCtx, cancel := context.WithCancel(c.Context())

	// 1. Read from RSocket and write to backend TCP.
	rc.FromFlux(streamCtx, downstream)

	// 2. Read from backend TCP and send to RSocket.
	upstream := rc.ToFlux(streamCtx)

	// Clean up TCP connection when streaming finishes.
	c.activeConnections.Add(1)
	go func() {
		slog.Debug("Conduit AcceptStream pump started", "id", c.ID, "backend", destEndpoint)
		defer c.activeConnections.Done()
		rc.Wait()
		slog.Debug("Conduit AcceptStream pump finished, closing connection", "id", c.ID, "backend", destEndpoint)
		cancel()
		conn.Close()
	}()

	return upstream
}
