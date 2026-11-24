package conduit

import (
	"context"
	"log/slog"
	"net"
	"time"

	"github.com/rsocket/rsocket-go/rx/flux"

	"github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/rxconn"
)

// StartListeningLoop manages the Requester role: accepts connections on the listener, and routes them to Responder.
func StartListeningLoop(c *Conduit, listen func() (net.Listener, error)) error {
	lis, err := listen()
	if err != nil {
		return err
	}

	defer lis.Close()
	// The listener closes in the step of closing that ends the context of
	// the conduit, not on a goroutine that runs some time later.
	c.AtEntryClose(func() { lis.Close() })

	for {
		conn, err := lis.Accept()
		if err != nil {
			// Stop loop if listening socket closed.
			select {
			case <-c.Context().Done():
				return nil
			default:
			}
			slog.ErrorContext(c.Context(), "StartListeningLoop Accept error", "error", err)
			continue
		}

		if !c.streamStarted() {
			resetConn(conn)
			return nil
		}
		go func() {
			defer c.streamFinished()
			handleIngressConnection(c, conn)
		}()
	}
}

func resetConn(conn net.Conn) {
	if lc, ok := conn.(interface{ SetLinger(sec int) error }); ok {
		_ = lc.SetLinger(0)
	}
	_ = conn.Close()
}

// streamOptions returns the options for the Conn of every stream of c.
func (c *Conduit) streamOptions() []rxconn.Option {
	return []rxconn.Option{
		rxconn.WithConduit(c.ID),
		// Protocol 0 support.
		rxconn.WithPeerProtocol(c.peerProtocol.Load()),
	}
}

func handleIngressConnection(c *Conduit, conn net.Conn) {
	streamCtx, cancel := context.WithCancel(c.Context())
	defer cancel()

	// The RSocket stream is created only when the client sends its first
	// bytes. When the conduit closes, its context ends the stream.
	rc, err := rxconn.Open(streamCtx, conn, c.rsocket.RequestChannel, c.streamOptions()...)
	if err != nil {
		slog.ErrorContext(c.Context(), "rxconn.Open error", "id", c.ID, "error", err)
		return
	}
	defer conn.Close()
	rc.Wait()
}

// endedContext is a context that has ended.
var endedContext = func() context.Context {
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	return ctx
}()

// destinationDialTimeout limits the dial of the destination of a stream.
// rsocket-go hands the Responder the frames of all streams of a conduit on
// one goroutine, and the dial runs on it, so that a stream sends only its
// first payload until its destination is connected. A dial that hangs holds
// up the other streams of the conduit for this long at most.
const destinationDialTimeout = 10 * time.Second

// dialDestination connects to the destination of a stream. The dial ends
// when its time limit runs out or the conduit begins to close.
func (c *Conduit) dialDestination(address string) (net.Conn, error) {
	ctx, cancel := context.WithTimeout(c.Context(), c.dialTimeout)
	defer cancel()
	return c.dial(ctx, "tcp", address)
}

// AcceptStream handles an incoming RSocket channel, dials the target service, and bridges the streams.
// This implements the Responder Role.
func AcceptStream(c *Conduit, destEndpoint string, downstream flux.Flux) flux.Flux {
	if c.State() != StateActive {
		// A stream that arrives while its conduit is closing is not
		// answered and its destination is not dialled: the Requester ends
		// it when it closes its own side. Subscribing still requests, so a
		// REQUEST_N frame is sent.
		return rxconn.Refuse(endedContext, downstream, nil)
	}
	conn, err := c.dialDestination(destEndpoint)
	if err != nil {
		slog.WarnContext(c.Context(), "Conduit AcceptStream: destination cannot be reached, refusing the stream", "id", c.ID, "backend", destEndpoint, "error", err)
		return refuseStream(c, downstream)
	}

	if !c.streamStarted() {
		// The conduit began to close during the dial. Its context may not
		// have ended yet, and the stream is not answered either way.
		resetConn(conn)
		return rxconn.Refuse(endedContext, downstream, nil)
	}
	streamCtx, cancel := context.WithCancel(c.Context())
	rc, upstream, err := rxconn.Accept(streamCtx, conn, downstream, c.streamOptions()...)
	if err != nil {
		slog.ErrorContext(c.Context(), "Conduit AcceptStream: rxconn.Accept failed", "id", c.ID, "error", err)
		cancel()
		conn.Close()
		return rxconn.Refuse(c.Context(), downstream, c.streamFinished)
	}
	go func() {
		defer c.streamFinished()
		rc.Wait()
		cancel()
		conn.Close()
	}()
	return upstream
}

// refuseStream answers an incoming RSocket channel that cannot be served, in
// the way a destination that cannot be reached is answered: with the RST
// marker. While the conduit is open, the stream is counted until the Requester
// has completed it. A stream whose conduit has begun to close is not answered.
func refuseStream(c *Conduit, downstream flux.Flux) flux.Flux {
	if !c.streamStarted() {
		return rxconn.Refuse(endedContext, downstream, nil)
	}
	return rxconn.Refuse(c.Context(), downstream, c.streamFinished)
}
