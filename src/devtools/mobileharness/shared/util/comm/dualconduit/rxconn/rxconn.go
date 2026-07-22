// Package rxconn provides reactive bridging over standard network connections utilizing rsocket-go.
package rxconn

import (
	"context"
	"errors"
	"io"
	"log/slog"
	"net"
	"sync"
	"sync/atomic"

	"github.com/rsocket/rsocket-go/payload"
	"github.com/rsocket/rsocket-go/rx/flux"
	"github.com/rsocket/rsocket-go/rx"
)

// Conn bridges a standard network connection with reactive streams.
type Conn struct {
	conn       net.Conn
	wg         sync.WaitGroup
	subscribed atomic.Bool // Guard to enforce the single-subscriber policy
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
//
// ToFlux enforces a single-subscriber policy using an atomic guard to prevent
// multiple subscribers from corrupting the stream (net.Conn is stateful and
// cannot be shared by multiple Read Pumps).
func (c *Conn) ToFlux(ctx context.Context) flux.Flux {
	slog.DebugContext(ctx, "rxconn.ToFlux called", "local", c.conn.LocalAddr(), "remote", c.conn.RemoteAddr())
	if c.subscribed.Swap(true) {
		slog.WarnContext(ctx, "rxconn.ToFlux subscription rejected: already subscribed")
		return flux.Error(errors.New("ToFlux allows only one subscriber"))
	}

	c.wg.Add(1)
	var fluxSubscribed atomic.Bool
	return flux.Create(func(subscriberCtx context.Context, sink flux.Sink) {
		slog.DebugContext(subscriberCtx, "rxconn.ToFlux subscribed", "local", c.conn.LocalAddr(), "remote", c.conn.RemoteAddr())
		if fluxSubscribed.Swap(true) {
			sink.Error(errors.New("ToFlux allows only one subscriber"))
			return
		}

		// Lazy Pumping: Initialize channels and start goroutines ONLY when
		// a subscriber is ready.
		upstreamChan := make(chan []byte, 32)
		errChan := make(chan error, 1)

		// 1. The TCP Read Pump
		go func() {
			slog.DebugContext(subscriberCtx, "rxconn.ToFlux TCP Read Pump started", "local", c.conn.LocalAddr(), "remote", c.conn.RemoteAddr())
			defer func() {
				slog.DebugContext(subscriberCtx, "rxconn.ToFlux TCP Read Pump exiting", "local", c.conn.LocalAddr(), "remote", c.conn.RemoteAddr())
				close(upstreamChan)
			}()
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
						slog.DebugContext(ctx, "rxconn.ToFlux TCP Read Pump ctx cancelled", "error", ctx.Err())
						errChan <- ctx.Err()
						return
					// subscriberCtx: The internal context managed by RSocket for this subscriber. Tells us if the subscriber unsubscribed.
					case <-subscriberCtx.Done():
						slog.DebugContext(subscriberCtx, "rxconn.ToFlux TCP Read Pump subscriberCtx cancelled", "error", subscriberCtx.Err())
						errChan <- subscriberCtx.Err()
						return
					}
				}
				if err != nil {
					if errors.Is(err, io.EOF) {
						slog.InfoContext(subscriberCtx, "rxconn.ToFlux TCP Read Pump connection closed gracefully", "local", c.conn.LocalAddr(), "remote", c.conn.RemoteAddr())
					} else {
						slog.ErrorContext(subscriberCtx, "rxconn.ToFlux TCP Read Pump read error", "local", c.conn.LocalAddr(), "remote", c.conn.RemoteAddr(), "error", err)
					}
					errChan <- err
					return
				}
			}
		}()

		// 2. RSocket Flux Generator
		go func() {
			slog.DebugContext(subscriberCtx, "rxconn.ToFlux Flux Generator started", "local", c.conn.LocalAddr(), "remote", c.conn.RemoteAddr())
			defer func() {
				slog.DebugContext(subscriberCtx, "rxconn.ToFlux Flux Generator exiting", "local", c.conn.LocalAddr(), "remote", c.conn.RemoteAddr())
				c.wg.Done()
			}()
			for {
				select {
				case <-ctx.Done():
					slog.DebugContext(ctx, "rxconn.ToFlux Flux Generator: outer ctx done", "error", ctx.Err())
					sink.Complete()
					return
				case <-subscriberCtx.Done():
					slog.DebugContext(subscriberCtx, "rxconn.ToFlux Flux Generator: subscriberCtx done", "error", subscriberCtx.Err())
					sink.Complete()
					return
				case data, ok := <-upstreamChan:
					if !ok {
						// upstreamChan closed string, check errors
						select {
						case err := <-errChan:
							slog.DebugContext(subscriberCtx, "rxconn.ToFlux Flux Generator: upstreamChan closed, got error from read pump", "error", err)
							// Treat ALL read errors as clean completion to avoid sending ERROR frames
							// that cause panics in rsocket-go Responder side.
							sink.Complete()
						default:
							slog.DebugContext(subscriberCtx, "rxconn.ToFlux Flux Generator: upstreamChan closed, no error")
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
func (c *Conn) FromFlux(ctx context.Context, incoming flux.Flux) {
	slog.DebugContext(ctx, "rxconn.FromFlux subscribing", "local", c.conn.LocalAddr(), "remote", c.conn.RemoteAddr())
	c.wg.Add(1)
	incoming.Subscribe(ctx,
		rx.OnNext(func(p payload.Payload) error {
			data := p.Data()
			// Write in a loop to ensure all bytes are flushed
			for len(data) > 0 {
				n, err := c.conn.Write(data)
				if err != nil {
					slog.ErrorContext(ctx, "rxconn.FromFlux write error", "local", c.conn.LocalAddr(), "remote", c.conn.RemoteAddr(), "error", err)
					return err // Connection dropped locally, triggers OnError implicitly
				}
				data = data[n:]
			}
			// RSocket-Go waits for this to return before it consumes more of its Request-N quota
			return nil
		}),
		rx.OnComplete(func() {
			slog.DebugContext(ctx, "rxconn.FromFlux OnComplete received", "local", c.conn.LocalAddr(), "remote", c.conn.RemoteAddr())
			defer func() {
				slog.DebugContext(ctx, "rxconn.FromFlux OnComplete exiting", "local", c.conn.LocalAddr(), "remote", c.conn.RemoteAddr())
				c.wg.Done()
			}()
			// Remote sent EOF via RSocket. We mirror the FIN locally.
			if cw, ok := c.conn.(interface{ CloseWrite() error }); ok {
				slog.DebugContext(ctx, "rxconn.FromFlux calling CloseWrite", "local", c.conn.LocalAddr(), "remote", c.conn.RemoteAddr())
				_ = cw.CloseWrite()
			}
		}),
		rx.OnError(func(err error) {
			slog.ErrorContext(ctx, "rxconn.FromFlux OnError received", "local", c.conn.LocalAddr(), "remote", c.conn.RemoteAddr(), "error", err)
			defer func() {
				slog.DebugContext(ctx, "rxconn.FromFlux OnError exiting", "local", c.conn.LocalAddr(), "remote", c.conn.RemoteAddr())
				c.wg.Done()
			}()
			// Remote dropped abruptly. Kill the local socket.
			slog.InfoContext(ctx, "rxconn.FromFlux closing connection due to remote error", "local", c.conn.LocalAddr(), "remote", c.conn.RemoteAddr())
			_ = c.conn.Close()
		}),
	)
}

// Wait blocks until both the ToFlux and FromFlux pipes have completed operations.
func (c *Conn) Wait() {
	c.wg.Wait()
}
