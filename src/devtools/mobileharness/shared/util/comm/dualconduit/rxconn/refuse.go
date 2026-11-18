package rxconn

import (
	"context"
	"sync/atomic"

	"github.com/rsocket/rsocket-go/payload"
	"github.com/rsocket/rsocket-go/rx/flux"
)

// Refuse answers a stream for which no Conn can be created, for example
// because the destination cannot be reached. It requests the inbound
// direction, drops what arrives, and returns a Flux that sends the RST marker
// and completes. Nothing is sent if ctx has ended or incoming has failed.
// done, if not nil, is called once when the inbound Flux or ctx has ended.
func Refuse(ctx context.Context, incoming flux.Flux, done func()) flux.Flux {
	requested, finished := newLatch(), newLatch()
	var failed atomic.Bool
	safeSubscribe(ctx, incoming, inboundHandler{
		onRequested: requested.Close,
		onNext:      func(payload.Payload) {},
		onComplete:  finished.Close,
		onError: func(error) {
			failed.Store(true)
			finished.Close()
		},
	})
	if done != nil {
		go func() {
			select {
			case <-ctx.Done():
			case <-finished.Wait():
			}
			done()
		}()
	}

	return safeOutFlux(func(sink flux.Sink) {
		go func() {
			// The request goes first; see Accept.
			select {
			case <-requested.Wait():
			case <-finished.Wait():
			case <-ctx.Done():
			}
			if failed.Load() || ctx.Err() != nil {
				return
			}
			safeNext(ctx, sink, payload.New(nil, markerRST))
			safeComplete(ctx, sink)
		}()
	}, nil)
}
