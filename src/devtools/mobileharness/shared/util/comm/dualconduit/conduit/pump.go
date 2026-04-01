package conduit

import (
	"context"
	"log"
	"net"

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
			log.Printf("StartListeningLoop Accept error: %v", err)
			continue
		}

		go handleIngressConnection(c, conn)
	}
}

func handleIngressConnection(c *Conduit, conn net.Conn) {
	rc, err := rxconn.New(conn)
	if err != nil {
		log.Printf("rxconn.New error: %v", err)
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
}

// AcceptStream handles an incoming RSocket channel, dials the target service, and bridges the streams.
// This implements the Responder Role.
func AcceptStream(c *Conduit, destEndpoint string, incoming flux.Flux) flux.Flux {
	conn, err := net.Dial("tcp", destEndpoint)
	if err != nil {
		return flux.Error(err)
	}

	rc, err := rxconn.New(conn)
	if err != nil {
		conn.Close()
		return flux.Error(err)
	}

	streamCtx, cancel := context.WithCancel(c.Context())

	// Read from RSocket and write to backend TCP.
	rc.FromFlux(streamCtx, incoming)

	// Clean up TCP connection when streaming finishes.
	go func() {
		rc.Wait()
		cancel()
		conn.Close()
	}()

	// Read from backend TCP and send to RSocket.
	return rc.ToFlux(streamCtx)
}
