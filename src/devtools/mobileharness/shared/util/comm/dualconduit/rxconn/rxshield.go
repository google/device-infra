package rxconn

// This file holds every call into rsocket-go and reactor-go that needs a
// workaround, so that the rest of the package can use them as if they were
// safe.

import (
	"context"
	"fmt"
	"log/slog"

	"github.com/rsocket/rsocket-go/payload"
	"github.com/rsocket/rsocket-go/rx/flux"
	"github.com/rsocket/rsocket-go/rx"
)

// inboundHandler receives the events of an inbound Flux.
type inboundHandler struct {
	// onRequested is called when the request for inbound payloads has been
	// written to the connection. It may be nil.
	onRequested func()
	onNext      func(payload.Payload)
	onComplete  func()
	onError     func(error)
}

// safeSubscribe subscribes h to incoming and requests all its payloads.
//
// The subscription does not get the context of the stream: if that context
// has ended when reactor-go starts the subscription, reactor-go cancels it,
// rsocket-go sends a CANCEL frame, and the rsocket-go of a Requester panics
// on that frame.
//
// It requests once, with the unlimited count: reactor-go adds every request
// to a 32-bit counter, and a second one overflows it.
//
// rsocket-go panics in Subscribe when its connection has closed. That is
// reported to h.onError like any other failure of the Flux.
func safeSubscribe(ctx context.Context, incoming flux.Flux, h inboundHandler) {
	defer func() {
		if r := recover(); r != nil {
			slog.WarnContext(ctx, "rxconn: subscribing to the inbound Flux panicked", "panic", r)
			h.onError(fmt.Errorf("subscribe panic: %v", r))
		}
	}()
	incoming.Subscribe(context.Background(),
		rx.OnSubscribe(func(_ context.Context, su rx.Subscription) {
			// Request returns when rsocket-go has written the REQUEST_N frame.
			su.Request(rx.RequestMax)
			if h.onRequested != nil {
				h.onRequested()
			}
		}),
		rx.OnNext(func(p payload.Payload) error {
			h.onNext(p)
			// An error returned here would be passed to OnError, where it
			// would look like a failure of the remote side.
			return nil
		}),
		rx.OnComplete(h.onComplete),
		rx.OnError(h.onError),
	)
}

// safeOutFlux returns the outgoing Flux of a stream. It is the only place in
// the package that creates a Flux for rsocket-go to send from. onSink is
// called with the sink of the Flux when the Flux gets a subscriber. onRequest,
// if not nil, is called with the count of each request for payloads.
//
// The Flux gets no other hook, and callers must not add one. In particular it
// must not get a DoFinally. rsocket-go keeps the subscription of an outgoing
// Flux for as long as the stream is registered, and cancels it when the
// connection closes. With DoFinally that subscription is an object that
// reactor-go puts back into a pool, shared by all connections, as soon as the
// Flux has completed. If the stream is still receiving when its connection
// closes, the cancel then hits whatever stream has taken the object from the
// pool since. That stream is cancelled: its sink can then block in Complete
// for good, and if it is the inbound side of a Responder, rsocket-go sends a
// CANCEL frame that panics the Requester. The hook for requests is not
// implemented with a pooled object.
func safeOutFlux(onSink func(flux.Sink), onRequest func(n int)) flux.Flux {
	out := flux.Create(func(_ context.Context, sink flux.Sink) { onSink(sink) })
	if onRequest != nil {
		out = out.DoOnRequest(onRequest)
	}
	return out
}

// safeNext hands p to the sink. When the connection closes, rsocket-go
// cancels the Flux of every stream, and reactor-go then closes the channel
// that Next sends on: a Next that runs at that moment panics. The stream is
// over in that case, so the panic is contained here and the payload dropped.
func safeNext(ctx context.Context, sink flux.Sink, p payload.Payload) {
	defer func() {
		if r := recover(); r != nil {
			slog.WarnContext(ctx, "rxconn: payload dropped, the stream was cancelled while sending", "panic", r)
		}
	}()
	sink.Next(p)
}

// safeComplete completes the sink, with the same guard as safeNext.
func safeComplete(ctx context.Context, sink flux.Sink) {
	defer func() {
		if r := recover(); r != nil {
			slog.WarnContext(ctx, "rxconn: Complete dropped, the stream was cancelled while sending", "panic", r)
		}
	}()
	sink.Complete()
}
