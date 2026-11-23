package rxconn

import (
	"bytes"
	"context"
	"errors"
	"io"
	"net"
	"os"
	"strings"
	"sync"
	"syscall"
	"testing"
	"time"

	"github.com/rsocket/rsocket-go/payload"
	"github.com/rsocket/rsocket-go/rx/flux"
	"github.com/rsocket/rsocket-go/rx"
)

const (
	// waitTime is how long a test waits for something that must happen.
	waitTime = 5 * time.Second
	// quietTime is how long a test watches for something that must not happen.
	quietTime = 50 * time.Millisecond
)

// fakePeer plays the remote side of a Conn: rsocket-go and the Conn behind it.
type fakePeer struct {
	t *testing.T
	// in carries what the remote side sends to the Conn.
	in flux.Processor
	// sent receives what the Conn sends to the remote side: "data:<bytes>",
	// "FIN", "RST", "marker:<metadata>" or "complete".
	sent chan string
	// opened is closed when a Requester Conn creates the stream.
	opened chan struct{}
	// requested receives each request of the Conn for inbound payloads.
	requested chan int
	// inFinal receives the DoFinally signal of the inbound Flux.
	inFinal chan rx.SignalType
	su      rx.Subscription
}

func newFakePeer(t *testing.T) *fakePeer {
	return &fakePeer{
		t:         t,
		in:        flux.CreateProcessor(),
		sent:      make(chan string, 4096),
		opened:    make(chan struct{}),
		requested: make(chan int, 16),
		inFinal:   make(chan rx.SignalType, 4),
	}
}

// incoming returns the Flux the Conn receives from.
func (p *fakePeer) incoming() flux.Flux {
	return p.in.
		DoOnRequest(func(n int) { p.requested <- n }).
		DoFinally(func(sig rx.SignalType) { p.inFinal <- sig })
}

// subscribe subscribes to the outgoing Flux of the Conn and requests n
// payloads.
func (p *fakePeer) subscribe(out flux.Flux, n int) {
	out.Subscribe(p.t.Context(),
		rx.OnSubscribe(func(_ context.Context, su rx.Subscription) {
			p.su = su
			if n > 0 {
				su.Request(n)
			}
		}),
		rx.OnNext(func(pl payload.Payload) error {
			p.sent <- describe(pl)
			return nil
		}),
		rx.OnComplete(func() { p.sent <- "complete" }),
		rx.OnError(func(err error) { p.sent <- "error:" + err.Error() }),
	)
}

func describe(pl payload.Payload) string {
	if data := pl.Data(); len(data) > 0 {
		return "data:" + string(data)
	}
	meta, _ := pl.Metadata()
	switch string(meta) {
	case "dcon:fin":
		return "FIN"
	case "dcon:rst":
		return "RST"
	}
	return "marker:" + string(meta)
}

// open is the function a Requester Conn creates its stream with. Like
// rsocket-go it requests one payload, the one that opens the stream. confirm
// requests the rest.
func (p *fakePeer) open(out flux.Flux) flux.Flux {
	close(p.opened)
	p.subscribe(out, 1)
	return p.incoming()
}

func (p *fakePeer) isOpened() bool {
	select {
	case <-p.opened:
		return true
	default:
		return false
	}
}

// confirm plays the first REQUEST_N of the Responder. The first payload must
// have been received before.
func (p *fakePeer) confirm() {
	p.su.Request(rx.RequestMax)
}

func (p *fakePeer) send(data string) {
	p.in.Next(payload.New([]byte(data), nil))
}

func (p *fakePeer) sendMarker(metadata string) {
	p.in.Next(payload.New(nil, []byte(metadata)))
}

// nextSent returns the next signal sequence of length len(want) and any
// unexpected extra signal observed within quietTime.
func (p *fakePeer) checkSent(want ...string) (got []string, extra string, timedOut bool) {
	for range want {
		select {
		case s := <-p.sent:
			got = append(got, s)
		case <-time.After(waitTime):
			return got, "", true
		}
	}
	select {
	case s := <-p.sent:
		return got, s, false
	case <-time.After(quietTime):
		return got, "", false
	}
}

func (p *fakePeer) mustReceive(want ...string) {
	p.t.Helper()
	got, extra, timedOut := p.checkSent(want...)
	if timedOut {
		p.t.Fatalf("peer received %v before timeout (%v), want %v", got, waitTime, want)
	}
	for i := range want {
		if got[i] != want[i] {
			p.t.Fatalf("peer received[%d] = %.40q, want %q", i, got[i], want[i])
		}
	}
	if extra != "" {
		p.t.Fatalf("peer received unexpected trailing signal %.40q after %v", extra, want)
	}
}

// harness is a Conn between a real TCP connection and a fakePeer.
type harness struct {
	t    *testing.T
	conn *Conn
	// local is the local peer's end of the TCP connection, and sock is the
	// end the Conn owns.
	local, sock *net.TCPConn
	peer        *fakePeer
	// cancel ends the context of the stream: C_CLOSE.
	cancel context.CancelFunc
	ctx    context.Context
	// wantInvalid is how many impossible events the test causes on purpose.
	wantInvalid int64
	// requester: the Conn was started with Open.
	requester bool
}

func newHarness(t *testing.T) *harness {
	t.Helper()
	local, sock := tcpPair(t)
	ctx, cancel := context.WithCancel(t.Context())
	h := &harness{t: t, local: local, sock: sock, peer: newFakePeer(t), cancel: cancel, ctx: ctx}
	t.Cleanup(func() {
		cancel()
		if h.conn != nil {
			_ = waitReturns(h.conn, waitTime)
			if got := h.conn.InvalidEvents(); got != h.wantInvalid {
				t.Errorf("Conn.InvalidEvents() = %d, want %d", got, h.wantInvalid)
			}
		}
	})
	return h
}

// newRequester returns a harness whose Conn is a Requester in INIT.
func newRequester(t *testing.T) *harness {
	t.Helper()
	h := newHarness(t)
	conn, err := Open(h.ctx, h.sock, h.peer.open, WithConduit("test"))
	if err != nil {
		t.Fatalf("Open() unexpected error: %v", err)
	}
	h.conn = conn
	h.requester = true
	return h
}

// newResponder returns a harness whose Conn is a Responder in ESTABLISHED.
func newResponder(t *testing.T) *harness {
	t.Helper()
	h := newHarness(t)
	conn, out, err := Accept(h.ctx, h.sock, h.peer.incoming(), WithConduit("test"))
	if err != nil {
		t.Fatalf("Accept() unexpected error: %v", err)
	}
	h.conn = conn
	// rsocket-go subscribes with the request of the Requester, which travels
	// with the first payload.
	h.peer.subscribe(out, rx.RequestMax)
	return h
}

func (h *harness) write(data string) {
	h.t.Helper()
	if _, err := h.local.Write([]byte(data)); err != nil {
		h.t.Fatalf("local.Write(%q) unexpected error: %v", data, err)
	}
}

// reach brings the Conn into state s during test setup.
func (h *harness) reach(s State) {
	h.t.Helper()
	if s == StateInit {
		return
	}
	if h.requester {
		h.write("hello")
		h.peer.mustReceive("data:hello")
		h.peer.confirm()
	}
	switch s {
	case StateHalfClosedLocal:
		if err := h.local.CloseWrite(); err != nil {
			h.t.Fatalf("local.CloseWrite() unexpected error: %v", err)
		}
		h.peer.mustReceive("FIN")
	case StateHalfClosedRemote:
		// The local peer does not read "tail" yet.
		h.peer.send("tail")
		h.peer.sendMarker("dcon:fin")
	case StateClosed:
		h.peer.sendMarker("dcon:rst")
		h.peer.mustReceive("complete")
	}
	if got := waitState(h.conn, s); got != s {
		h.t.Fatalf("reach(%v): State() = %v, want %v", s, got, s)
	}
}

// inject makes ev happen.
func (h *harness) inject(ev event) {
	h.t.Helper()
	switch ev {
	case evLData:
		h.write("more")
	case evLFin:
		if err := h.local.CloseWrite(); err != nil {
			h.t.Fatalf("local.CloseWrite() unexpected error: %v", err)
		}
	case evLRst:
		if err := h.local.SetLinger(0); err != nil {
			h.t.Fatalf("local.SetLinger(0) unexpected error: %v", err)
		}
		h.local.Close()
	case evRData:
		h.peer.send("resp")
	case evRFin:
		h.peer.sendMarker("dcon:fin")
	case evRRst:
		h.peer.sendMarker("dcon:rst")
	case evRFail:
		h.peer.in.Error(errors.New("connection lost"))
	case evCClose:
		h.cancel()
	}
}

func waitState(conn *Conn, want State) State {
	var got State
	for deadline := time.Now().Add(waitTime); time.Now().Before(deadline); time.Sleep(time.Millisecond) {
		if got = conn.State(); got == want {
			return got
		}
	}
	return got
}

func readLocal(conn *net.TCPConn, wantLen int, readToEnd bool) (got string, gotEnd string, err error) {
	conn.SetReadDeadline(time.Now().Add(waitTime))
	defer conn.SetReadDeadline(time.Time{})
	if !readToEnd {
		buf := make([]byte, wantLen)
		_, err := io.ReadFull(conn, buf)
		return string(buf), "", err
	}
	b, err := io.ReadAll(conn)
	end := "eof"
	switch {
	case err == nil:
	case errors.Is(err, syscall.ECONNRESET):
		end = "reset"
	default:
		end = err.Error()
	}
	return string(b), end, nil
}

// waitReturns reports whether conn.Wait returns within d.
func waitReturns(conn *Conn, d time.Duration) bool {
	done := make(chan struct{})
	go func() {
		conn.Wait()
		close(done)
	}()
	select {
	case <-done:
		return true
	case <-time.After(d):
		return false
	}
}

// tcpPair returns the two ends of a TCP connection over loopback.
func tcpPair(t *testing.T) (client, server *net.TCPConn) {
	t.Helper()
	lis, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("net.Listen unexpected error: %v", err)
	}
	defer lis.Close()
	accepted := make(chan net.Conn, 1)
	go func() {
		c, err := lis.Accept()
		if err != nil {
			close(accepted)
			return
		}
		accepted <- c
	}()
	c, err := net.Dial("tcp", lis.Addr().String())
	if err != nil {
		t.Fatalf("net.Dial(%v) unexpected error: %v", lis.Addr(), err)
	}
	s, ok := <-accepted
	if !ok {
		t.Fatal("lis.Accept() failed")
	}
	t.Cleanup(func() {
		c.Close()
		s.Close()
	})
	return c.(*net.TCPConn), s.(*net.TCPConn)
}

// TestTransitions covers every cell of the state transition table that can be
// reached from outside: the Conn is brought into the state, the event is made
// to happen, and the test checks what the Conn sends, what the local peer
// sees, whether the Conn closed its own socket on CLOSED, and the next state.
func TestTransitions(t *testing.T) {
	const (
		ini = StateInit
		est = StateEstablished
		hcl = StateHalfClosedLocal
		hcr = StateHalfClosedRemote
		clo = StateClosed
	)
	cells := []struct {
		state               State
		ev                  event
		sent                []string
		localData, localEnd string
		next                State
	}{
		{state: ini, ev: evLData, sent: []string{"data:more"}, next: est},
		{state: ini, ev: evLFin, localEnd: "eof", next: clo},
		{state: ini, ev: evLRst, next: clo},
		{state: ini, ev: evCClose, localEnd: "reset", next: clo},

		{state: est, ev: evLData, sent: []string{"data:more"}, next: est},
		{state: est, ev: evLFin, sent: []string{"FIN"}, next: hcl},
		{state: est, ev: evLRst, sent: []string{"RST", "complete"}, next: clo},
		{state: est, ev: evRData, localData: "resp", next: est},
		{state: est, ev: evRFin, localEnd: "eof", next: hcr},
		{state: est, ev: evRRst, sent: []string{"complete"}, localEnd: "reset", next: clo},
		{state: est, ev: evRFail, localEnd: "reset", next: clo},
		{state: est, ev: evCClose, localEnd: "reset", next: clo},

		{state: hcl, ev: evLRst, sent: []string{"RST", "complete"}, next: clo},
		{state: hcl, ev: evRData, localData: "resp", next: hcl},
		{state: hcl, ev: evRFin, sent: []string{"complete"}, localEnd: "eof", next: clo},
		{state: hcl, ev: evRRst, sent: []string{"complete"}, localEnd: "reset", next: clo},
		{state: hcl, ev: evRFail, localEnd: "reset", next: clo},
		{state: hcl, ev: evCClose, localEnd: "reset", next: clo},

		// In HALF_CLOSED_REMOTE the local peer has "tail" unread and has
		// already received a FIN; TestHalfClosedRemote_ResetVsCleanClose
		// distinguishes localReset (evRRst) from localClose (evRFail, evCClose).
		{state: hcr, ev: evLData, sent: []string{"data:more"}, next: hcr},
		{state: hcr, ev: evLFin, sent: []string{"FIN", "complete"}, localData: "tail", localEnd: "eof", next: clo},
		{state: hcr, ev: evLRst, sent: []string{"RST", "complete"}, next: clo},
		{state: hcr, ev: evRRst, sent: []string{"complete"}, localData: "tail", localEnd: "eof", next: clo},
		{state: hcr, ev: evRFail, localData: "tail", localEnd: "eof", next: clo},
		{state: hcr, ev: evCClose, localData: "tail", localEnd: "eof", next: clo},

		// In CLOSED, onNext drops R_DATA/R_FIN/R_RST before handle; direct
		// handle calls in CLOSED are covered by TestClosed_HandleDirect.
		{state: clo, ev: evRData, next: clo},
		{state: clo, ev: evRFin, next: clo},
		{state: clo, ev: evRRst, next: clo},
		{state: clo, ev: evRFail, next: clo},
		{state: clo, ev: evCClose, next: clo},
	}

	roles := []struct {
		name string
		new  func(*testing.T) *harness
	}{
		{"requester", newRequester},
		{"responder", newResponder},
	}
	for _, role := range roles {
		for _, c := range cells {
			if c.state == StateInit && role.name == "responder" {
				continue
			}
			subName := role.name + "_" + c.state.String() + "_" + c.ev.String()
			t.Run(subName, func(t *testing.T) {
				t.Parallel()
				h := role.new(t)
				h.reach(c.state)

				h.inject(c.ev)

				gotSent, extra, timedOut := h.peer.checkSent(c.sent...)
				if timedOut || extra != "" || !equalStrings(gotSent, c.sent) {
					t.Errorf("peer received (%v, extra=%q, timeout=%t), want %v", gotSent, extra, timedOut, c.sent)
				}
				if c.localData != "" || c.localEnd != "" {
					gotData, gotEnd, err := readLocal(h.local, len(c.localData), c.localEnd != "")
					if err != nil || gotData != c.localData || gotEnd != c.localEnd {
						t.Errorf("readLocal() = (%q, %q, %v), want (%q, %q, nil)", gotData, gotEnd, err, c.localData, c.localEnd)
					}
				}
				if got := waitState(h.conn, c.next); got != c.next {
					t.Fatalf("State() = %v, want %v", got, c.next)
				}
				if c.state == StateInit && c.next == StateClosed && h.peer.isOpened() {
					t.Errorf("peer.isOpened() = true in INIT->CLOSED, want false")
				}
				if c.next != StateClosed {
					return
				}
				if h.peer.isOpened() || !h.requester {
					h.peer.in.Complete()
				}
				if !waitReturns(h.conn, waitTime) {
					t.Fatalf("Wait() did not return within %v", waitTime)
				}
				// Every transition into CLOSED must close the Conn's own socket.
				if _, err := h.sock.Write([]byte("z")); !errors.Is(err, net.ErrClosed) {
					t.Errorf("sock.Write after CLOSED returned %v, want %v", err, net.ErrClosed)
				}
			})
		}
	}
}

func equalStrings(a, b []string) bool {
	if len(a) != len(b) {
		return false
	}
	for i := range a {
		if a[i] != b[i] {
			return false
		}
	}
	return true
}

// TestClosed_HandleDirect checks every cell of StateClosed in the transition
// table directly, including events that onNext short-circuits in production.
func TestClosed_HandleDirect(t *testing.T) {
	h := newResponder(t)
	h.reach(StateClosed)
	h.peer.in.Complete()
	if !waitReturns(h.conn, waitTime) {
		t.Fatalf("Wait() did not return within %v", waitTime)
	}
	for ev := evLData; ev < numEvents; ev++ {
		st := h.conn.handle(t.Context(), ev, []byte("ignored"))
		if !st.valid || st.next != StateClosed || st.out != outNone || st.complete || st.local != localNone {
			t.Errorf("handle(CLOSED, %v) = %+v, want valid no-op in CLOSED", ev, st)
		}
	}
}

// TestImpossibleEventIsCountedAndIgnored covers a remote side that breaks the
// protocol by sending data after its FIN marker.
func TestImpossibleEventIsCountedAndIgnored(t *testing.T) {
	t.Parallel()
	h := newResponder(t)
	h.wantInvalid = 1
	h.reach(StateHalfClosedRemote)

	h.peer.send("late")

	for deadline := time.Now().Add(waitTime); h.conn.InvalidEvents() == 0 && time.Now().Before(deadline); time.Sleep(time.Millisecond) {
	}
	if got := h.conn.InvalidEvents(); got != 1 {
		t.Errorf("Conn.InvalidEvents() = %d, want 1", got)
	}
	if got := waitState(h.conn, StateHalfClosedRemote); got != StateHalfClosedRemote {
		t.Errorf("State() = %v, want %v", got, StateHalfClosedRemote)
	}
	h.inject(evLFin)
	h.peer.mustReceive("FIN", "complete")
	if gotData, gotEnd, err := readLocal(h.local, 0, true); err != nil || gotData != "tail" || gotEnd != "eof" {
		t.Errorf("readLocal() = (%q, %q, %v), want (\"tail\", \"eof\", nil)", gotData, gotEnd, err)
	}
}

// TestRequester_DoesNotReadUntilConfirmed covers the time between the first
// payload and the first REQUEST_N of the Responder.
func TestRequester_DoesNotReadUntilConfirmed(t *testing.T) {
	t.Parallel()
	h := newRequester(t)
	_ = h.local.SetWriteBuffer(16 << 10)
	_ = h.sock.SetReadBuffer(16 << 10)

	h.write("first")
	h.peer.mustReceive("data:first")

	rest := bytes.Repeat([]byte("x"), 4<<20)
	h.local.SetWriteDeadline(time.Now().Add(200 * time.Millisecond))
	written, err := h.local.Write(rest)
	h.local.SetWriteDeadline(time.Time{})
	if !errors.Is(err, os.ErrDeadlineExceeded) {
		t.Fatalf("local.Write() err = %v (written=%d), want %v", err, written, os.ErrDeadlineExceeded)
	}
	if written <= 0 || written >= len(rest) {
		t.Fatalf("local.Write() written = %d, want in (0, %d)", written, len(rest))
	}
	if got := h.conn.out.Len(); got != 0 {
		t.Errorf("Conn.out.Len() = %d while unconfirmed, want 0", got)
	}
	h.peer.mustReceive()

	h.peer.confirm()

	got := 0
	for got < written {
		select {
		case s := <-h.peer.sent:
			data, ok := strings.CutPrefix(s, "data:")
			if !ok || strings.Trim(data, "x") != "" {
				t.Fatalf("peer received %.40q after confirmation, want data bytes", s)
			}
			got += len(data)
		case <-time.After(waitTime):
			t.Fatalf("peer received %d bytes after confirmation, want %d", got, written)
		}
	}
	if got != written {
		t.Errorf("peer received %d bytes after confirmation, want %d", got, written)
	}
}

// TestRequester_LocalEventsWaitForConfirmation covers a client that sends its
// request and finishes sending before the Responder has answered.
func TestRequester_LocalEventsWaitForConfirmation(t *testing.T) {
	t.Parallel()
	h := newRequester(t)
	h.write("first")
	h.inject(evLFin)

	h.peer.mustReceive("data:first")
	if got := h.conn.State(); got != StateEstablished {
		t.Errorf("State() = %v while unconfirmed, want %v", got, StateEstablished)
	}

	h.peer.confirm()

	h.peer.mustReceive("FIN")
	if got := waitState(h.conn, StateHalfClosedLocal); got != StateHalfClosedLocal {
		t.Errorf("State() = %v, want %v", got, StateHalfClosedLocal)
	}
}

// TestRequester_EndsWhileUnconfirmed covers R_FAIL and C_CLOSE before the
// Responder has confirmed the stream.
func TestRequester_EndsWhileUnconfirmed(t *testing.T) {
	for _, ev := range []event{evRFail, evCClose} {
		t.Run(ev.String(), func(t *testing.T) {
			t.Parallel()
			h := newRequester(t)
			h.write("first")
			h.peer.mustReceive("data:first")

			h.inject(ev)

			h.peer.mustReceive()
			if gotData, gotEnd, err := readLocal(h.local, 0, true); err != nil || gotData != "" || gotEnd != "reset" {
				t.Errorf("readLocal() = (%q, %q, %v), want (\"\", \"reset\", nil)", gotData, gotEnd, err)
			}
			if got := waitState(h.conn, StateClosed); got != StateClosed {
				t.Errorf("State() = %v, want %v", got, StateClosed)
			}
			if !waitReturns(h.conn, waitTime) {
				t.Errorf("Wait() did not return within %v", waitTime)
			}
		})
	}
}

// scriptConn replays a fixed sequence of Read results on top of net.Conn.
type scriptConn struct {
	net.Conn
	mu      sync.Mutex
	reads   []scriptRead
	writeEr error
}

type scriptRead struct {
	data string
	err  error
}

func (s *scriptConn) Read(b []byte) (int, error) {
	s.mu.Lock()
	if len(s.reads) == 0 {
		s.mu.Unlock()
		return s.Conn.Read(b)
	}
	r := s.reads[0]
	s.reads = s.reads[1:]
	s.mu.Unlock()
	n := copy(b, r.data)
	return n, r.err
}

func (s *scriptConn) Write(b []byte) (int, error) {
	s.mu.Lock()
	err := s.writeEr
	s.mu.Unlock()
	if err != nil {
		return 0, err
	}
	return s.Conn.Write(b)
}

// TestReadOnce_DataAndTerminationInSingleCall covers a Read that returns bytes
// and EOF in the same call.
func TestReadOnce_DataAndTerminationInSingleCall(t *testing.T) {
	t.Run("data_and_eof_then_confirmed", func(t *testing.T) {
		t.Parallel()
		_, sock := tcpPair(t)
		sc := &scriptConn{Conn: sock, reads: []scriptRead{{data: "abc", err: io.EOF}}}
		p := newFakePeer(t)
		conn, err := Open(t.Context(), sc, p.open)
		if err != nil {
			t.Fatalf("Open() unexpected error: %v", err)
		}

		p.mustReceive("data:abc")
		p.confirm()
		p.mustReceive("FIN")
		p.sendMarker("dcon:fin")
		p.mustReceive("complete")
		p.in.Complete()
		if !waitReturns(conn, waitTime) {
			t.Errorf("Wait() did not return within %v", waitTime)
		}
	})
}

// TestSilenceWakesUnconfirmedRequester covers a Requester that closed with RST
// and Complete still queued, and then lost the stream before the Responder
// confirmed it. Nothing will ever request the queued signals, so they must be
// dropped and Wait must return, without the context of the stream ending.
func TestSilenceWakesUnconfirmedRequester(t *testing.T) {
	tests := []struct {
		name string
		// subscribeOut: the remote side subscribes to the outgoing Flux, so
		// that the Requester gets past waiting for its sink.
		subscribeOut bool
	}{
		{name: "waiting_for_confirmation", subscribeOut: true},
		{name: "waiting_for_sink", subscribeOut: false},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			t.Parallel()
			_, sock := tcpPair(t)
			sc := &scriptConn{Conn: sock, reads: []scriptRead{{data: "abc", err: errors.New("boom")}}}
			p := newFakePeer(t)
			conn, err := Open(t.Context(), sc, func(out flux.Flux) flux.Flux {
				if tc.subscribeOut {
					p.subscribe(out, 1)
				}
				return p.incoming()
			})
			if err != nil {
				t.Fatalf("Open() unexpected error: %v", err)
			}
			if tc.subscribeOut {
				p.mustReceive("data:abc")
			}
			if got := waitState(conn, StateClosed); got != StateClosed {
				t.Fatalf("State() = %v, want %v", got, StateClosed)
			}

			p.in.Error(errors.New("connection lost"))

			p.mustReceive()
			if !waitReturns(conn, waitTime) {
				t.Errorf("Wait() did not return within %v after R_FAIL", waitTime)
			}
		})
	}
}

// TestLocalWriteErrorSendsRST covers a failed write to the local socket in
// onNext: the Conn must treat it as L_RST and send RST + Complete.
func TestLocalWriteErrorSendsRST(t *testing.T) {
	t.Parallel()
	_, sock := tcpPair(t)
	sc := &scriptConn{Conn: sock, writeEr: errors.New("broken pipe")}
	p := newFakePeer(t)
	conn, out, err := Accept(t.Context(), sc, p.incoming())
	if err != nil {
		t.Fatalf("Accept() unexpected error: %v", err)
	}
	p.subscribe(out, rx.RequestMax)

	p.send("payload")

	p.mustReceive("RST", "complete")
	p.in.Complete()
	if !waitReturns(conn, waitTime) {
		t.Errorf("Wait() did not return within %v", waitTime)
	}
}

// TestRequester_StreamRefused covers a Responder that cannot reach its
// destination and answers with REQUEST_N, the RST marker, and Complete.
func TestRequester_StreamRefused(t *testing.T) {
	t.Parallel()
	h := newRequester(t)
	h.write("first")
	h.peer.mustReceive("data:first")

	h.peer.confirm()
	h.peer.sendMarker("dcon:rst")
	h.peer.in.Complete()

	h.peer.mustReceive("complete")
	if gotData, gotEnd, err := readLocal(h.local, 0, true); err != nil || gotData != "" || gotEnd != "reset" {
		t.Errorf("readLocal() = (%q, %q, %v), want (\"\", \"reset\", nil)", gotData, gotEnd, err)
	}
	if !waitReturns(h.conn, waitTime) {
		t.Errorf("Wait() did not return within %v", waitTime)
	}
}

// TestLocalResetAfterFIN covers a local peer that closes its connection while
// it has nothing unread, then resets when a payload is written to it.
func TestLocalResetAfterFIN(t *testing.T) {
	t.Parallel()
	h := newResponder(t)
	h.write("req")
	if err := h.local.Close(); err != nil {
		t.Fatalf("local.Close() unexpected error: %v", err)
	}
	h.peer.mustReceive("data:req", "FIN")

	h.peer.send("one small payload")

	h.peer.mustReceive("RST", "complete")
	if got := waitState(h.conn, StateClosed); got != StateClosed {
		t.Errorf("State() = %v, want %v", got, StateClosed)
	}
	h.peer.in.Complete()
	if !waitReturns(h.conn, waitTime) {
		t.Errorf("Wait() did not return within %v", waitTime)
	}
}

// TestCompleteWithoutMarker covers a remote side that completes its direction
// without a FIN or RST marker.
func TestCompleteWithoutMarker(t *testing.T) {
	t.Run("ESTABLISHED", func(t *testing.T) {
		t.Parallel()
		h := newResponder(t)

		h.peer.in.Complete()

		if gotData, gotEnd, err := readLocal(h.local, 0, true); err != nil || gotData != "" || gotEnd != "eof" {
			t.Errorf("readLocal() = (%q, %q, %v), want (\"\", \"eof\", nil)", gotData, gotEnd, err)
		}
		if got := waitState(h.conn, StateHalfClosedRemote); got != StateHalfClosedRemote {
			t.Errorf("State() = %v, want %v", got, StateHalfClosedRemote)
		}
		h.peer.mustReceive()
	})
	t.Run("HALF_CLOSED_LOCAL", func(t *testing.T) {
		t.Parallel()
		h := newResponder(t)
		h.reach(StateHalfClosedLocal)

		h.peer.in.Complete()

		h.peer.mustReceive("complete")
		if gotData, gotEnd, err := readLocal(h.local, 0, true); err != nil || gotData != "" || gotEnd != "eof" {
			t.Errorf("readLocal() = (%q, %q, %v), want (\"\", \"eof\", nil)", gotData, gotEnd, err)
		}
		if got := waitState(h.conn, StateClosed); got != StateClosed {
			t.Errorf("State() = %v, want %v", got, StateClosed)
		}
		if !waitReturns(h.conn, waitTime) {
			t.Errorf("Wait() did not return within %v", waitTime)
		}
	})
}

// TestMarkers_UnknownAndNonEmptyDataIgnored checks that unknown dcon: markers,
// empty non-dcon payloads, and malformed dcon: markers carrying data are
// ignored without writing to the local socket.
func TestMarkers_UnknownAndNonEmptyDataIgnored(t *testing.T) {
	t.Parallel()
	h := newResponder(t)

	h.peer.sendMarker("dcon:window")
	h.peer.sendMarker("not a marker")
	h.peer.in.Next(payload.New([]byte("corrupt"), []byte("dcon:fin")))
	h.peer.in.Next(payload.New([]byte("corrupt"), []byte("dcon:rst")))
	h.peer.send("resp")

	if gotData, _, err := readLocal(h.local, len("resp"), false); err != nil || gotData != "resp" {
		t.Errorf("readLocal() = (%q, %v), want (\"resp\", nil)", gotData, err)
	}
	if got := h.conn.State(); got != StateEstablished {
		t.Errorf("State() = %v, want %v", got, StateEstablished)
	}
	h.peer.mustReceive()
}

// TestHalfClosedRemote_ResetVsCleanClose checks that R_FAIL and C_CLOSE in
// HALF_CLOSED_REMOTE close the socket normally (delivering unread bytes),
// whereas R_RST resets the socket (discarding unread bytes).
func TestHalfClosedRemote_ResetVsCleanClose(t *testing.T) {
	for _, tc := range []struct {
		ev        event
		wantClean bool
	}{
		{ev: evRFail, wantClean: true},
		{ev: evCClose, wantClean: true},
		{ev: evRRst, wantClean: false},
	} {
		t.Run(tc.ev.String(), func(t *testing.T) {
			t.Parallel()
			h := newResponder(t)
			_ = h.sock.SetWriteBuffer(512 << 10)
			_ = h.local.SetReadBuffer(8 << 10)

			body := strings.Repeat("y", 256<<10)
			h.peer.send(body)
			h.peer.sendMarker("dcon:fin")
			if got := waitState(h.conn, StateHalfClosedRemote); got != StateHalfClosedRemote {
				t.Fatalf("State() = %v, want %v", got, StateHalfClosedRemote)
			}

			h.inject(tc.ev)
			if tc.ev == evRRst {
				h.peer.mustReceive("complete")
				h.peer.in.Complete()
			}

			if got := waitState(h.conn, StateClosed); got != StateClosed {
				t.Fatalf("State() = %v, want %v", got, StateClosed)
			}
			h.local.SetReadDeadline(time.Now().Add(waitTime))
			got, err := io.ReadAll(h.local)
			clean := err == nil && len(got) == len(body)
			if clean != tc.wantClean {
				t.Errorf("io.ReadAll(local) after %v: len=%d, err=%v (clean=%t), want clean=%t (full len=%d)", tc.ev, len(got), err, clean, tc.wantClean, len(body))
			}
			if !waitReturns(h.conn, waitTime) {
				t.Errorf("Wait() did not return within %v", waitTime)
			}
		})
	}
}

// TestRequestsOnceUnlimited checks that a Conn requests the inbound payloads
// once, with the unlimited count.
func TestRequestsOnceUnlimited(t *testing.T) {
	roles := []struct {
		name string
		new  func(*testing.T) *harness
	}{
		{"requester", newRequester},
		{"responder", newResponder},
	}
	for _, role := range roles {
		t.Run(role.name, func(t *testing.T) {
			t.Parallel()
			h := role.new(t)
			h.reach(StateEstablished)
			for range 3 {
				h.peer.send("resp")
				if gotData, _, err := readLocal(h.local, len("resp"), false); err != nil || gotData != "resp" {
					t.Fatalf("readLocal() = (%q, %v), want (\"resp\", nil)", gotData, err)
				}
			}

			select {
			case n := <-h.peer.requested:
				if n != rx.RequestMax {
					t.Errorf("requested = %d, want %d", n, rx.RequestMax)
				}
			case <-time.After(waitTime):
				t.Fatalf("no request received within %v", waitTime)
			}
			select {
			case n := <-h.peer.requested:
				t.Errorf("unexpected second request = %d, want none", n)
			case <-time.After(quietTime):
			}
		})
	}
}

// TestRefuse covers normal refusal, an already-canceled context (no SignalCancel
// on incoming and nothing sent), and an inbound failure.
func TestRefuse(t *testing.T) {
	t.Run("sends_rst_and_waits_for_inbound_complete", func(t *testing.T) {
		t.Parallel()
		p := newFakePeer(t)
		done := make(chan struct{})

		out := Refuse(t.Context(), p.incoming(), func() { close(done) })
		p.subscribe(out, rx.RequestMax)

		select {
		case n := <-p.requested:
			if n != rx.RequestMax {
				t.Errorf("Refuse() requested = %d, want %d", n, rx.RequestMax)
			}
		case <-time.After(waitTime):
			t.Fatalf("Refuse() did not request within %v", waitTime)
		}
		p.mustReceive("RST", "complete")

		p.send("first")
		select {
		case <-done:
			t.Errorf("Refuse() called done before inbound Flux ended")
		case <-time.After(quietTime):
		}
		p.in.Complete()
		select {
		case <-done:
		case <-time.After(waitTime):
			t.Errorf("Refuse() did not call done within %v", waitTime)
		}
	})

	t.Run("canceled_ctx_does_not_cancel_inbound_or_send", func(t *testing.T) {
		t.Parallel()
		p := newFakePeer(t)
		ctx, cancel := context.WithCancel(t.Context())
		cancel()
		done := make(chan struct{})

		out := Refuse(ctx, p.incoming(), func() { close(done) })
		p.subscribe(out, rx.RequestMax)

		select {
		case <-done:
		case <-time.After(waitTime):
			t.Fatalf("Refuse() did not call done within %v on canceled ctx", waitTime)
		}
		p.mustReceive()
		select {
		case sig := <-p.inFinal:
			if sig == rx.SignalCancel {
				t.Errorf("Refuse() cancelled inbound subscription (%v), which sends CANCEL and panics Requester", sig)
			}
		case <-time.After(quietTime):
		}
	})

	t.Run("inbound_error_silences_outbound_and_calls_done", func(t *testing.T) {
		t.Parallel()
		p := newFakePeer(t)
		done := make(chan struct{})

		out := Refuse(t.Context(), flux.Error(errors.New("boom")), func() { close(done) })
		p.subscribe(out, rx.RequestMax)

		select {
		case <-done:
		case <-time.After(waitTime):
			t.Fatalf("Refuse() did not call done within %v on inbound error", waitTime)
		}
		p.mustReceive()
	})
}

// panicFlux is an inbound Flux whose Subscribe panics, as the one of rsocket-go
// does when its connection has closed.
type panicFlux struct{ flux.Flux }

func (panicFlux) Subscribe(context.Context, ...rx.SubscriberOption) {
	panic(errors.New("pool closed"))
}

// TestSubscribePanicEndsStream covers an inbound Flux whose Subscribe panics:
// the panic must not escape, and the stream must end as it does on R_FAIL.
func TestSubscribePanicEndsStream(t *testing.T) {
	t.Run("requester", func(t *testing.T) {
		t.Parallel()
		local, sock := tcpPair(t)
		p := newFakePeer(t)
		conn, err := Open(t.Context(), sock, func(out flux.Flux) flux.Flux {
			p.subscribe(out, 1)
			return panicFlux{}
		})
		if err != nil {
			t.Fatalf("Open() unexpected error: %v", err)
		}
		if _, err := local.Write([]byte("first")); err != nil {
			t.Fatalf("local.Write() unexpected error: %v", err)
		}

		if gotData, gotEnd, err := readLocal(local, 0, true); err != nil || gotData != "" || gotEnd != "reset" {
			t.Errorf("readLocal() = (%q, %q, %v), want (\"\", \"reset\", nil)", gotData, gotEnd, err)
		}
		if !waitReturns(conn, waitTime) {
			t.Errorf("Wait() did not return within %v", waitTime)
		}
	})

	t.Run("responder", func(t *testing.T) {
		t.Parallel()
		local, sock := tcpPair(t)
		p := newFakePeer(t)
		conn, out, err := Accept(t.Context(), sock, panicFlux{})
		if err != nil {
			t.Fatalf("Accept() unexpected error: %v", err)
		}
		p.subscribe(out, rx.RequestMax)

		if gotData, gotEnd, err := readLocal(local, 0, true); err != nil || gotData != "" || gotEnd != "reset" {
			t.Errorf("readLocal() = (%q, %q, %v), want (\"\", \"reset\", nil)", gotData, gotEnd, err)
		}
		if !waitReturns(conn, waitTime) {
			t.Errorf("Wait() did not return within %v", waitTime)
		}
		p.mustReceive()
	})

	t.Run("refuse", func(t *testing.T) {
		t.Parallel()
		p := newFakePeer(t)
		done := make(chan struct{})

		out := Refuse(t.Context(), panicFlux{}, func() { close(done) })
		p.subscribe(out, rx.RequestMax)

		select {
		case <-done:
		case <-time.After(waitTime):
			t.Fatalf("Refuse() did not call done within %v", waitTime)
		}
		p.mustReceive()
	})
}

// TestRequester_SingleUnlimitedRequestConfirms covers a subscriber of the
// outgoing Flux that requests everything at once instead of one payload first.
func TestRequester_SingleUnlimitedRequestConfirms(t *testing.T) {
	t.Parallel()
	local, sock := tcpPair(t)
	p := newFakePeer(t)
	c, err := Open(t.Context(), sock, func(out flux.Flux) flux.Flux {
		p.subscribe(out, rx.RequestMax)
		return p.incoming()
	})
	if err != nil {
		t.Fatalf("Open() unexpected error: %v", err)
	}
	if _, err := local.Write([]byte("one")); err != nil {
		t.Fatalf("local.Write unexpected error: %v", err)
	}
	p.mustReceive("data:one")
	if _, err := local.Write([]byte("two")); err != nil {
		t.Fatalf("local.Write unexpected error: %v", err)
	}
	p.mustReceive("data:two")
	_ = local.CloseWrite()
	p.mustReceive("FIN")
	p.sendMarker("dcon:fin")
	p.mustReceive("complete")
	p.in.Complete()
	if !waitReturns(c, waitTime) {
		t.Errorf("Wait() did not return within %v", waitTime)
	}
}

func TestOpenAccept_NilConn(t *testing.T) {
	if _, err := Open(t.Context(), nil, nil); err == nil {
		t.Errorf("Open(nil conn) = nil error, want non-nil error")
	}
	if _, _, err := Accept(t.Context(), nil, nil); err == nil {
		t.Errorf("Accept(nil conn) = nil error, want non-nil error")
	}
}
