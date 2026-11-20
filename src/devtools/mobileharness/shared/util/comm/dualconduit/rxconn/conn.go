// Package rxconn joins one local TCP connection to one RSocket stream so that
// the client and the destination behave as if one TCP connection joined them.
//
// The two sides of a stream start differently and then run the same Conn.
// Open starts the side that creates the stream (the Requester), Accept the
// side that receives it (the Responder). A Conn moves through five states
// (INIT, ESTABLISHED, HALF_CLOSED_LOCAL, HALF_CLOSED_REMOTE, CLOSED) and
// syncs with its peer using empty-data marker payloads ("dcon:fin" and
// "dcon:rst") followed by Complete when closed.
//
// The package is split by concern: state.go holds the transition table,
// outbox.go the queue of signals to send, rxshield.go the calls into
// rsocket-go that need a workaround, and this file the Conn that joins them
// to the socket.
package rxconn

import (
	"bytes"
	"context"
	"errors"
	"io"
	"log/slog"
	"net"
	"sync"
	"sync/atomic"
	"syscall"

	"github.com/rsocket/rsocket-go/payload"
	"github.com/rsocket/rsocket-go/rx/flux"
)

// Markers are payloads with no data whose metadata names a DualConduit event.
// They stand in for the RSocket CANCEL and ERROR frames, which rsocket-go does
// not handle safely on a stream that both sides send on.
var (
	// markerFIN: the sender's local peer finished sending (L_FIN).
	markerFIN = []byte("dcon:fin")
	// markerRST: the sender's local connection was reset or failed (L_RST).
	markerRST = []byte("dcon:rst")
	// markerPrefix starts the metadata of every marker. A receiver ignores a
	// marker it does not know.
	markerPrefix = []byte("dcon:")
)

const readBufferSize = 32 * 1024

var errNilConn = errors.New("rxconn: nil conn")

// invalidEvents counts events that arrived in a state in which they cannot
// occur. They are logged and ignored; tests check that there are none.
var invalidEvents atomic.Int64

// InvalidEvents returns how many events arrived in a state in which they
// cannot occur, across all Conns in this process.
func InvalidEvents() int64 { return invalidEvents.Load() }

// Option configures a Conn.
type Option func(*Conn)

// WithConduit names the conduit of the stream in the Conn's log lines.
func WithConduit(id string) Option {
	return func(c *Conn) { c.conduit = id }
}

// Conn joins one local TCP connection to one RSocket stream. It is the same
// on both sides of the stream: only Open and Accept, which start it, differ.
type Conn struct {
	conn    net.Conn
	conduit string
	// Protocol 0 support: see protocol0.go.
	p0 protocol0

	// mu guards the fields below. It is not held during socket or Flux calls.
	mu    sync.Mutex
	state State
	sink  flux.Sink
	// outFinished and inFinished: the direction will use neither the Flux nor
	// the socket again.
	outFinished bool
	inFinished  bool

	// out holds the signals that wait to be sent to the remote side.
	out *outbox
	// inMu is held while an event of the inbound Flux is being handled.
	inMu sync.Mutex

	// sinkReady: the outgoing Flux has a subscriber.
	sinkReady *latch
	// closed: the state is CLOSED.
	closed *latch
	// ended: the stream has ended. The Conn is CLOSED and both directions
	// have finished.
	ended   *latch
	invalid atomic.Int64
}

func newConn(conn net.Conn, state State, opts []Option) *Conn {
	c := &Conn{
		conn:      conn,
		state:     state,
		out:       newOutbox(),
		sinkReady: newLatch(),
		closed:    newLatch(),
		ended:     newLatch(),
	}
	for _, opt := range opts {
		opt(c)
	}
	return c
}

// Open starts the Conn of the side that opens the stream (the Requester) and
// returns at once. conn is the connection of the client. It should be a
// *net.TCPConn (or implement CloseWrite, SetLinger, and syscall.Conn) so
// half-close and TCP reset propagation take effect.
//
// The RSocket stream is created with open only when the client sends its
// first bytes; open gets the outgoing Flux and returns the incoming one. ctx
// is the context of the stream: when it ends, the stream ends (C_CLOSE).
func Open(ctx context.Context, conn net.Conn, open func(flux.Flux) flux.Flux, opts ...Option) (*Conn, error) {
	if conn == nil {
		return nil, errNilConn
	}
	c := newConn(conn, StateInit, opts)
	go c.watchContext(ctx)
	go func() {
		// INIT: wait for the first bytes. Until then nothing but this Conn
		// knows the stream.
		more := c.readOnce(ctx)
		if c.out.Len() == 0 {
			// The Conn closed in INIT: there is no stream and nothing is sent.
			c.finishInbound()
			c.finishOutbound()
			return
		}
		defer c.finishOutbound()

		// On the Requester the first request comes from rsocket-go itself
		// (n == 1) and covers the payload that opens the stream. Once the
		// requested total reaches 2 (the Responder's REQUEST_N, or a single
		// unlimited request in tests), the stream is confirmed.
		confirmed := newLatch()
		var demand atomic.Int64
		out := c.outFlux(func(n int) {
			if n > 0 && demand.Add(int64(n)) >= 2 {
				confirmed.Close()
			}
		})
		c.subscribe(ctx, open(out), nil)
		if !c.waitSink() {
			return
		}
		// Unconfirmed: exactly one payload is handed to RSocket. The local
		// socket is not read until the remote side has requested more, so
		// that nothing waits inside rsocket-go, where it is not safe.
		c.flush(ctx, 1)
		select {
		case <-confirmed.Wait():
		case <-c.out.Silenced():
			return
		}
		c.pump(ctx, more)
	}()
	return c, nil
}

// Accept starts the Conn of the side that accepted the stream (the
// Responder). conn is the connection to the destination and has the same TCP
// socket expectations as in Open. Accept subscribes to incoming, the Flux of
// the stream that arrived, and returns the outgoing Flux. ctx is the context
// of the stream: when it ends, the stream ends (C_CLOSE).
func Accept(ctx context.Context, conn net.Conn, incoming flux.Flux, opts ...Option) (*Conn, flux.Flux, error) {
	if conn == nil {
		return nil, nil, errNilConn
	}
	c := newConn(conn, StateEstablished, opts)
	requested := newLatch()
	go c.watchContext(ctx)
	c.subscribe(ctx, incoming, requested.Close)
	out := c.outFlux(nil)
	go func() {
		defer c.finishOutbound()
		if !c.waitSink() {
			return
		}
		// The Responder requests before it sends anything, so that the
		// Requester is confirmed before any other signal of the stream
		// reaches it. Subscribing first is not enough: rsocket-go runs the
		// subscription of a Responder on another goroutine. If the Complete
		// of a short stream overtook the request, rsocket-go on the
		// Requester would forget the stream, drop the request, and the
		// Requester would stay unconfirmed.
		select {
		case <-requested.Wait():
		case <-c.out.Silenced():
			return
		}
		c.pump(ctx, true)
	}()
	return c, out, nil
}

// State returns the current state.
func (c *Conn) State() State {
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.state
}

// InvalidEvents returns how many events arrived on c in a state in which they
// cannot occur.
func (c *Conn) InvalidEvents() int64 { return c.invalid.Load() }

// Wait blocks until the stream has ended: the Conn is CLOSED, its outbound
// direction is finished, and its inbound Flux has ended.
func (c *Conn) Wait() {
	<-c.ended.Wait()
}

// handle applies ev to the state machine: it changes the state, queues the
// signals for the remote side and applies the change to the local socket,
// except for writing a payload, which the caller does. It returns the cell of
// the transition table that was applied.
func (c *Conn) handle(ctx context.Context, ev event, data []byte) step {
	c.mu.Lock()
	from := c.state
	st := transitions[from][ev]
	if !st.valid {
		c.mu.Unlock()
		c.invalid.Add(1)
		invalidEvents.Add(1)
		slog.ErrorContext(ctx, "rxconn: event cannot occur in this state; ignored", "event", ev.String(), "state", from.String(), "conduit", c.conduit)
		return step{next: from}
	}
	c.state = st.next
	if st.silent {
		c.out.Silence()
	} else {
		c.out.Push(st.signals(data)...)
	}
	justClosed := st.next == StateClosed && from != StateClosed
	if justClosed {
		c.closed.Close()
	}
	c.mu.Unlock()

	switch st.local {
	case localShutWrite:
		if cw, ok := c.conn.(interface{ CloseWrite() error }); ok {
			_ = cw.CloseWrite()
		} else {
			slog.WarnContext(ctx, "rxconn: local connection does not support CloseWrite", "conduit", c.conduit)
		}
	case localClose:
		_ = c.conn.Close()
	case localReset:
		// Linger 0 makes Close send a reset, so that the local peer can tell
		// the failure from a clean end.
		if lc, ok := c.conn.(interface{ SetLinger(sec int) error }); ok {
			_ = lc.SetLinger(0)
		}
		_ = c.conn.Close()
	}
	if justClosed {
		c.logClosed(ctx, ev, from)
	}
	if ev == evLFin && st.next == StateHalfClosedLocal {
		go c.watchLocalReset(ctx)
	}
	return st
}

func (c *Conn) logClosed(ctx context.Context, cause event, from State) {
	level, how := slog.LevelWarn, "failed"
	switch {
	case from == StateInit && (cause == evLFin || cause == evLRst):
		level, how = slog.LevelDebug, "unopened"
	case cause == evLFin || cause == evRFin:
		level, how = slog.LevelDebug, "clean"
	}
	slog.Log(ctx, level, "rxconn: stream ended", "how", how, "cause", cause.String(), "state", from.String(),
		"conduit", c.conduit, "local", c.conn.LocalAddr(), "remote", c.conn.RemoteAddr())
}

// watchContext turns the end of the stream's context into C_CLOSE.
func (c *Conn) watchContext(ctx context.Context) {
	select {
	case <-ctx.Done():
		c.handle(ctx, evCClose, nil)
		// Closing the socket has released a write that was blocked. Once the
		// payload in hand is done, nothing uses the inbound Flux any more.
		c.inMu.Lock()
		c.finishInbound()
		c.inMu.Unlock()
	case <-c.ended.Wait():
	}
}

// watchLocalReset reports a reset that arrives after the local peer's FIN.
// A peer that closed its connection with nothing unread sends the same FIN as
// one that only finished sending. The difference shows when data is written
// to it: the write succeeds and a reset comes back later. After a FIN a read
// keeps returning EOF, so the error state of the socket is watched instead.
// watchLocalReset returns when it has seen the reset or the socket is closed.
func (c *Conn) watchLocalReset(ctx context.Context) {
	sc, ok := c.conn.(syscall.Conn)
	if !ok {
		return
	}
	raw, err := sc.SyscallConn()
	if err != nil {
		return
	}
	sockErr := 0
	// The function runs each time the socket reports an event. Returning
	// false waits for the next one.
	err = raw.Read(func(fd uintptr) bool {
		v, err := syscall.GetsockoptInt(int(fd), syscall.SOL_SOCKET, syscall.SO_ERROR)
		if err != nil {
			return true
		}
		sockErr = v
		return v != 0
	})
	if err == nil && sockErr != 0 {
		c.handle(ctx, evLRst, nil)
	}
}

// ---- outbound: local socket to remote side ----

// outFlux returns the outgoing Flux. Its only job is to hand the sink to the
// goroutine that Open or Accept started, which is the only one that uses it.
// onRequest, if not nil, is called with the count of each request for
// payloads.
func (c *Conn) outFlux(onRequest func(n int)) flux.Flux {
	return safeOutFlux(func(sink flux.Sink) {
		c.mu.Lock()
		if c.sink != nil {
			c.mu.Unlock()
			// The Flux has one subscriber. Another one gets nothing.
			safeComplete(context.Background(), sink)
			return
		}
		c.sink = sink
		c.mu.Unlock()
		c.sinkReady.Close()
	}, onRequest)
}

// waitSink waits until the outgoing Flux has a subscriber. It reports false
// if the stream was silenced first: then nothing will be sent.
func (c *Conn) waitSink() bool {
	select {
	case <-c.sinkReady.Wait():
		return true
	case <-c.out.Silenced():
		return false
	}
}

// pump reads the local socket and sends the queued signals to the remote
// side until the Conn is CLOSED. more is false if the local peer has already
// finished sending.
func (c *Conn) pump(ctx context.Context, more bool) {
	c.flush(ctx, 0)
	for more && !c.closed.IsClosed() {
		more = c.readOnce(ctx)
		c.flush(ctx, 0)
	}
	// The local peer has finished sending. What is still to be sent is queued
	// by the event that closes the Conn.
	<-c.closed.Wait()
	c.flush(ctx, 0)
}

// readOnce reads from the local socket once and applies the result. It
// reports whether there is more to read.
func (c *Conn) readOnce(ctx context.Context) bool {
	buf := make([]byte, readBufferSize)
	n, err := c.conn.Read(buf)
	if n > 0 {
		c.handle(ctx, evLData, buf[:n])
	}
	if err != nil {
		if errors.Is(err, io.EOF) {
			c.handle(ctx, evLFin, nil)
		} else {
			c.handle(ctx, evLRst, nil)
		}
		return false
	}
	return true
}

// flush sends the queued signals in order: at most max of them, or all of
// them if max is 0. It is called only once the outgoing Flux has a
// subscriber.
func (c *Conn) flush(ctx context.Context, max int) {
	c.mu.Lock()
	sink := c.sink
	c.mu.Unlock()
	for sent := 0; max == 0 || sent < max; sent++ {
		cmd, ok := c.out.Pop()
		if !ok {
			return
		}
		// Protocol 0 support: without it, sig is cmd.sig.
		sig, send := c.p0.translate(cmd.sig)
		if !send {
			continue
		}
		switch sig {
		case outData:
			safeNext(ctx, sink, payload.New(cmd.data, nil))
		case outFin:
			safeNext(ctx, sink, payload.New(nil, markerFIN))
		case outRst:
			safeNext(ctx, sink, payload.New(nil, markerRST))
		case outComplete:
			safeComplete(ctx, sink)
		}
	}
}

func (c *Conn) finishOutbound() {
	c.mu.Lock()
	c.outFinished = true
	c.mu.Unlock()
	c.maybeEnd()
}

// ---- inbound: remote side to local socket ----

// subscribe subscribes the Conn to the inbound Flux. onRequested, if not nil,
// is called when the request for inbound payloads has been written.
func (c *Conn) subscribe(ctx context.Context, incoming flux.Flux, onRequested func()) {
	safeSubscribe(ctx, incoming, inboundHandler{
		onRequested: onRequested,
		onNext:      func(p payload.Payload) { c.onNext(ctx, p) },
		onComplete:  func() { c.onComplete(ctx) },
		onError:     func(err error) { c.onError(ctx, err) },
	})
}

func (c *Conn) onNext(ctx context.Context, p payload.Payload) {
	c.inMu.Lock()
	defer c.inMu.Unlock()
	// After CLOSED, payloads can still arrive until the remote side has
	// reacted. They are dropped without being looked at: rsocket-go may
	// already have released them.
	if c.closed.IsClosed() {
		return
	}
	defer func() {
		// rsocket-go releases the payloads of a stream when its connection
		// closes, also one that is being handled, and using a released
		// payload panics.
		if r := recover(); r != nil {
			slog.WarnContext(ctx, "rxconn: payload dropped, it was released while being handled", "panic", r)
		}
	}()
	meta, hasMeta := p.Metadata()
	data := p.Data()
	if hasMeta && bytes.HasPrefix(meta, markerPrefix) {
		if len(data) != 0 {
			slog.WarnContext(ctx, "rxconn: marker with non-empty data ignored", "marker", string(meta), "bytes", len(data))
			return
		}
		switch {
		case bytes.Equal(meta, markerFIN):
			c.handle(ctx, evRFin, nil)
		case bytes.Equal(meta, markerRST):
			c.handle(ctx, evRRst, nil)
		default:
			slog.DebugContext(ctx, "rxconn: unknown marker ignored", "marker", string(meta))
		}
		return
	}
	if len(data) == 0 {
		return
	}
	if st := c.handle(ctx, evRData, nil); st.local != localWrite {
		return
	}
	for len(data) > 0 {
		n, err := c.conn.Write(data)
		if err != nil {
			// A failed write means the local connection is gone. If the Conn
			// itself closed the socket meanwhile, this is ignored in CLOSED.
			c.handle(ctx, evLRst, nil)
			return
		}
		data = data[n:]
	}
}

func (c *Conn) onComplete(ctx context.Context) {
	c.inMu.Lock()
	defer c.inMu.Unlock()
	// Complete is handled by the state of the Conn, not by the markers that
	// came before it. After a FIN or RST marker the Conn is in
	// HALF_CLOSED_REMOTE or CLOSED and nothing changes. Otherwise the remote
	// side ended its direction without a marker, which is taken as its FIN.
	// Only inbound events, which inMu serialises, lead to HALF_CLOSED_REMOTE.
	if s := c.State(); s != StateHalfClosedRemote && s != StateClosed {
		c.handle(ctx, evRFin, nil)
	}
	c.finishInbound()
}

func (c *Conn) onError(ctx context.Context, err error) {
	slog.DebugContext(ctx, "rxconn: inbound Flux failed", "error", err, "conduit", c.conduit)
	c.handle(ctx, evRFail, nil)
	// rsocket-go can call this while a payload is still being handled.
	// Closing the socket has released a write that was blocked. The inbound
	// direction is finished once that payload is done.
	c.inMu.Lock()
	c.finishInbound()
	c.inMu.Unlock()
}

func (c *Conn) finishInbound() {
	c.mu.Lock()
	c.inFinished = true
	c.mu.Unlock()
	c.maybeEnd()
}

// maybeEnd marks the stream ended once the Conn is CLOSED and both directions
// have finished.
func (c *Conn) maybeEnd() {
	c.mu.Lock()
	ready := c.state == StateClosed && c.outFinished && c.inFinished
	c.mu.Unlock()
	if ready {
		c.ended.Close()
	}
}
