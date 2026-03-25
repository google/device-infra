// Package rxconn provides reactive bridging over standard network connections utilizing rsocket-go.
package rxconn

import (
	"context"
	"io"
	"net"
	"sync"

	"github.com/rsocket/rsocket-go/payload"
	"github.com/rsocket/rsocket-go/rx/flux"
	"github.com/rsocket/rsocket-go/rx"
)

// Conn bridges a standard network connection with reactive streams.
type Conn struct {
	conn net.Conn
	wg   sync.WaitGroup
}

// New creates a new Conn bridging the active network connection to reactive fluxes.
func New(conn net.Conn) (*Conn, error) {
	if conn == nil {
		return nil, io.ErrUnexpectedEOF
	}
	return &Conn{conn: conn}, nil
}

// ToFlux returns a Flux that reads from the underlying active socket.
// The context controls pump lifecycle: cancellation tears down background pumps.
// It may be used after ToFlux returns, as pumps are run in background goroutines.
func (c *Conn) ToFlux(ctx context.Context) flux.Flux {
	upstreamChan := make(chan []byte, 32)
	errChan := make(chan error, 1)

	c.wg.Add(1)
	return flux.Create(func(subscriberCtx context.Context, sink flux.Sink) {
		// 1. The TCP Read Pump
		go func() {
			defer close(upstreamChan)
			for {
				buf := make([]byte, 32*1024)
				// Note: net.Conn.Read can block forever if no data and no deadline.
				// In a real implementation, we might set read deadlines periodically to check subscriberCtx.Done()
				n, err := c.conn.Read(buf)
				if n > 0 {
					select {
					case upstreamChan <- buf[:n]:
					// ctx: The outer context passed to ToFlux. Tells us if the application cancelled the operation.
					case <-ctx.Done():
						errChan <- ctx.Err()
						return
					// subscriberCtx: The internal context managed by RSocket for this subscriber. Tells us if the subscriber unsubscribed.
					case <-subscriberCtx.Done():
						errChan <- subscriberCtx.Err()
						return
					}
				}
				if err != nil {
					errChan <- err
					return
				}
			}
		}()

		// 2. RSocket Flux Generator
		go func() {
			defer c.wg.Done()
			for {
				select {
				case <-ctx.Done():
					sink.Error(ctx.Err())
					return
				case <-subscriberCtx.Done():
					sink.Error(subscriberCtx.Err())
					return
				case data, ok := <-upstreamChan:
					if !ok {
						// upstreamChan closed string, check errors
						select {
						case err := <-errChan:
							if err == io.EOF {
								sink.Complete()
							} else {
								sink.Error(err)
							}
						default:
							sink.Complete()
						}
						return
					}
					sink.Next(payload.New(data, nil))
				}
			}
		}()
	})
}

// FromFlux subscribes to the Flux and writes to the active socket.
func (c *Conn) FromFlux(ctx context.Context, incomingFlux flux.Flux) {
	c.wg.Add(1)
	// The incomingFlux is provided by the RSocket RequestChannel handler
	incomingFlux.Subscribe(ctx,
		rx.OnNext(func(p payload.Payload) error {
			data := p.Data()
			// Write in a loop to ensure all bytes are flushed
			for len(data) > 0 {
				n, err := c.conn.Write(data)
				if err != nil {
					return err // Connection dropped locally, triggers OnError implicitly
				}
				data = data[n:]
			}
			// RSocket-Go waits for this to return before it consumes more of its Request-N quota
			return nil
		}),
		rx.OnComplete(func() {
			defer c.wg.Done()
			// Remote sent EOF via RSocket. We mirror the FIN locally.
			if cw, ok := c.conn.(interface{ CloseWrite() error }); ok {
				_ = cw.CloseWrite()
			}
		}),
		rx.OnError(func(err error) {
			defer c.wg.Done()
			// Remote dropped abruptly. Kill the local socket.
			_ = c.conn.Close()
		}),
	)
}

// Wait blocks until both the ToFlux and FromFlux pipes have completed operations.
func (c *Conn) Wait() {
	c.wg.Wait()
}
