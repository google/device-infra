package dialer

import (
	"context"
	"errors"
	"fmt"
	"net"
	"runtime"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/nettest"
	dconpb "github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/proto/dconpb"
	"github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/session"
	dcontransport "github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/transport"
	rsockettransport "github.com/rsocket/rsocket-go/core/transport"
	"github.com/rsocket/rsocket-go/payload"
	"github.com/rsocket/rsocket-go"
	"github.com/rsocket/rsocket-go/rx/flux"
	"github.com/rsocket/rsocket-go/rx/mono"
	"github.com/rsocket/rsocket-go/rx"
	"go.opentelemetry.io/otel/trace"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/proto"
)

func TestConnectTimeoutFor(t *testing.T) {
	tests := []struct {
		name      string
		keepAlive time.Duration
		want      time.Duration
	}{
		{name: "default keep-alive", keepAlive: 20 * time.Second, want: 10 * time.Second},
		{name: "long keep-alive", keepAlive: 60 * time.Second, want: 10 * time.Second},
		{name: "short keep-alive", keepAlive: 4 * time.Second, want: 2 * time.Second},
		{name: "unset keep-alive", keepAlive: 0, want: 10 * time.Second},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			s := &Service{}
			if got := s.connectTimeoutFor(tc.keepAlive); got != tc.want {
				t.Errorf("connectTimeoutFor(%v) = %v, want %v", tc.keepAlive, got, tc.want)
			}
		})
	}
}

// TestCheckConnection_DialNeverCompletes covers an acceptor that cannot be
// reached: every dial hangs until its context ends. Each attempt must be given
// up after the connect timeout instead of waiting for the transport's own,
// much longer, handshake timeout.
func TestCheckConnection_DialNeverCompletes(t *testing.T) {
	ctx, cancel := context.WithTimeout(t.Context(), 30*time.Second)
	defer cancel()

	hangingDial := func(ctx context.Context) (*rsockettransport.Transport, error) {
		<-ctx.Done()
		return nil, ctx.Err()
	}
	s, err := New(ctx, "test-host", "127.0.0.1", func() (rsockettransport.ClientTransporter, error) {
		return hangingDial, nil
	}, RetryPolicy{}, 0)
	if err != nil {
		t.Fatalf("New() got unexpected error: %v", err)
	}
	s.connectTimeout = 50 * time.Millisecond

	start := time.Now()
	err = s.CheckConnection(ctx)
	elapsed := time.Since(start)
	if err == nil {
		t.Fatal("CheckConnection() succeeded, want an error")
	}
	// Three attempts with 1s and 2s backoff in between take a little over 3s.
	if elapsed > 10*time.Second {
		t.Errorf("CheckConnection() took %v, want it to give up within 10s", elapsed)
	}
}

// TestCloseTrackingRSocket_OnClose checks that a callback is called with the
// close error whether it is registered before or after the connection
// closed. An acceptor that rejects the setup closes the connection before the
// conduit registers its callback.
func TestCloseTrackingRSocket_OnClose(t *testing.T) {
	closeErr := errors.New("rejected setup")
	for _, tc := range []struct {
		name          string
		registerFirst bool
	}{
		{name: "registered_before_close", registerFirst: true},
		{name: "registered_after_close", registerFirst: false},
	} {
		t.Run(tc.name, func(t *testing.T) {
			r := &closeTrackingRSocket{}
			got := make(chan error, 1)
			onClose := func(err error) { got <- err }
			if tc.registerFirst {
				r.OnClose(onClose)
				r.handleClose(closeErr)
			} else {
				r.handleClose(closeErr)
				r.OnClose(onClose)
			}

			select {
			case err := <-got:
				if !errors.Is(err, closeErr) {
					t.Errorf("OnClose callback got %v, want %v", err, closeErr)
				}
			case <-time.After(5 * time.Second):
				t.Errorf("OnClose callback was not called within 5s")
			}
		})
	}
}

// TestNew_KeepAliveInterval covers the keep-alive interval a dialer starts
// with. Without an interval it uses one that fits the keep-alive lifetime,
// and an interval that does not fit is refused: with it, an idle conduit
// would be closed whenever one keep-alive frame is late.
func TestNew_KeepAliveInterval(t *testing.T) {
	newTransporter := func() (rsockettransport.ClientTransporter, error) {
		return nil, errors.New("not used")
	}
	tests := []struct {
		name     string
		interval time.Duration
		// want is the interval of the Service. 0 means New must fail.
		want time.Duration
	}{
		{name: "unset uses the default", interval: 0, want: 20 * time.Second},
		{name: "default of the dialer binary", interval: 20 * time.Second, want: 20 * time.Second},
		{name: "short", interval: time.Second, want: time.Second},
		{name: "just below half the lifetime", interval: keepAliveLifetime/2 - time.Millisecond, want: keepAliveLifetime/2 - time.Millisecond},
		{name: "half the lifetime", interval: keepAliveLifetime / 2},
		{name: "longer than the lifetime", interval: 60 * time.Second},
		{name: "negative", interval: -time.Second},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			s, err := New(t.Context(), "test-host", "127.0.0.1", newTransporter, RetryPolicy{}, tc.interval)
			if tc.want == 0 {
				if err == nil {
					t.Fatalf("New() with keep-alive interval %v succeeded, want an error (lifetime %v)", tc.interval, keepAliveLifetime)
				}
				return
			}
			if err != nil {
				t.Fatalf("New() with keep-alive interval %v got unexpected error: %v", tc.interval, err)
			}
			if s.KeepAliveTickPeriod != tc.want {
				t.Errorf("New() with keep-alive interval %v: KeepAliveTickPeriod = %v, want %v", tc.interval, s.KeepAliveTickPeriod, tc.want)
			}
		})
	}
}

// goroutinesIn returns how many goroutines of the process have function in
// their stack.
func goroutinesIn(function string) int {
	buf := make([]byte, 1<<20)
	for {
		n := runtime.Stack(buf, true)
		if n < len(buf) {
			buf = buf[:n]
			break
		}
		buf = make([]byte, 2*len(buf))
	}
	count := 0
	for _, g := range strings.Split(string(buf), "\n\n") {
		if strings.Contains(g, function) {
			count++
		}
	}
	return count
}

// TestFailedDials_LeaveNoGoroutine covers a dialer that cannot reach its
// acceptor, as during a network outage. A dial that fails must leave nothing
// behind, for a conduit as for the pre-flight and health check.
//
// In version 0.2.0 and older each failed dial left one goroutine for good:
// the scavenger (purgePeriodically) of a pool that rsocket-go creates before
// it dials. A worker that had lost its acceptor for a few hours had hundreds
// of them.
func TestFailedDials_LeaveNoGoroutine(t *testing.T) {
	const (
		dials = 20
		// scavenger is the goroutine of each pool of rsocket-go.
		scavenger = "purgePeriodically"
	)
	refusedDial := func(context.Context) (*rsockettransport.Transport, error) {
		return nil, errors.New("connection refused")
	}
	s, err := New(t.Context(), "test-host", "127.0.0.1", func() (rsockettransport.ClientTransporter, error) {
		return refusedDial, nil
	}, RetryPolicy{}, 0)
	if err != nil {
		t.Fatalf("New() got unexpected error: %v", err)
	}
	establish := func() {
		t.Helper()
		_, err := s.EstablishConduit(t.Context(), &dconpb.EstablishConduitRequest{
			Type:                dconpb.EstablishConduitRequest_CONDUIT_TYPE_FORWARD,
			EntryPort:           1,
			DestinationEndpoint: "127.0.0.1:1",
		})
		if err == nil {
			t.Fatalf("EstablishConduit() succeeded although every dial is refused")
		}
	}

	// The first dial starts what the process keeps for good.
	establish()
	before := goroutinesIn(scavenger)

	for range dials {
		establish()
	}
	// The pre-flight and health check makes three attempts.
	if err := s.CheckConnection(t.Context()); err == nil {
		t.Fatalf("CheckConnection() succeeded although every dial is refused")
	}

	if after := goroutinesIn(scavenger); after > before {
		t.Errorf("%d failed dials left %d more %s goroutines (%d before, %d after), want none", dials+3, after-before, scavenger, before, after)
	}
}

// TestEstablishConduit_AcceptorSilentAfterSetup covers a path to the acceptor
// that dies right after the connection has opened: the SETUP frame is sent,
// and nothing ever comes back. The dialer must close such a conduit when its
// keep-alive lifetime has run out, so that its session can replace it.
//
// In version 0.2.0 and older no lifetime ran before the first frame arrived,
// and the conduit stayed until the operating system gave up on the
// connection, after about 15 minutes.
func TestEstablishConduit_AcceptorSilentAfterSetup(t *testing.T) {
	const (
		lifetime = 600 * time.Millisecond
		limit    = 5 * time.Second
	)
	// An acceptor that reads what it is sent and answers nothing.
	lis, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("net.Listen got unexpected error: %v", err)
	}
	defer lis.Close()
	go func() {
		for {
			conn, err := lis.Accept()
			if err != nil {
				return
			}
			go func() {
				defer conn.Close()
				buf := make([]byte, 4096)
				for {
					if _, err := conn.Read(buf); err != nil {
						return
					}
				}
			}()
		}
	}()
	entry, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("net.Listen got unexpected error: %v", err)
	}
	entryPort := entry.Addr().(*net.TCPAddr).Port
	entry.Close()

	s, err := New(t.Context(), "test-host", "127.0.0.1", func() (rsockettransport.ClientTransporter, error) {
		return rsocket.TCPClient().SetAddr(lis.Addr().String()).Build(), nil
	}, RetryPolicy{}, 0)
	if err != nil {
		t.Fatalf("New() got unexpected error: %v", err)
	}
	defer s.Manager.Shutdown()
	s.keepAliveLifetime = lifetime

	start := time.Now()
	resp, err := s.EstablishConduit(t.Context(), &dconpb.EstablishConduitRequest{
		Type:                dconpb.EstablishConduitRequest_CONDUIT_TYPE_FORWARD,
		EntryPort:           int32(entryPort),
		DestinationEndpoint: "127.0.0.1:1",
	})
	if err != nil {
		t.Fatalf("EstablishConduit() got unexpected error: %v", err)
	}

	for time.Since(start) < limit {
		if _, err := s.Manager.Conduit(resp.ConduitId); err != nil {
			if got := time.Since(start); got < lifetime/2 {
				t.Errorf("conduit closed after %v, want not before the lifetime of %v", got, lifetime)
			}
			return
		}
		time.Sleep(10 * time.Millisecond)
	}
	t.Errorf("conduit to an acceptor that never answers is still open after %v, want it closed after the lifetime of %v", limit, lifetime)
}

// stalledRSocket is a connection whose send queue is full: MetadataPush does
// not return until the connection is closed.
type stalledRSocket struct {
	pushing chan struct{}
	closed  chan struct{}
}

func (r stalledRSocket) MetadataPush(payload.Payload) {
	close(r.pushing)
	<-r.closed
}

// TestSendCloseSignal_DoesNotWaitForStalledConnection covers the CLOSE signal
// of a reverse conduit on a connection that has stalled. Sending it must not
// hold up closing: a conduit that does not finish closing is never replaced.
func TestSendCloseSignal_DoesNotWaitForStalledConnection(t *testing.T) {
	rs := stalledRSocket{pushing: make(chan struct{}), closed: make(chan struct{})}
	defer close(rs.closed)
	returned := make(chan struct{})
	start := time.Now()
	go func() {
		sendCloseSignal(t.Context(), rs, "stalled")
		close(returned)
	}()

	select {
	case <-returned:
		if got := time.Since(start); got < reverseConduitCloseSignalDelay {
			t.Errorf("sendCloseSignal() returned after %v, want it to pause for %v", got, reverseConduitCloseSignalDelay)
		}
	case <-time.After(5 * time.Second):
		t.Fatalf("sendCloseSignal() did not return within 5s on a connection that has stalled")
	}
	select {
	case <-rs.pushing:
	case <-time.After(5 * time.Second):
		t.Errorf("sendCloseSignal() did not send the signal")
	}
}

// testAcceptor is an RSocket server that accepts every connection, as an
// acceptor does for a forward conduit, and answers the probe of the health
// check. A test can make it lose connections.
type testAcceptor struct {
	addr string
	// accepted counts the TCP connections that the server has accepted.
	accepted atomic.Int32
	// dying is how many of the next connections are lost right after they
	// have opened.
	dying atomic.Int32
	// pushLocator makes the server send a locator on every conduit, as an
	// acceptor does for a reverse conduit.
	pushLocator atomic.Bool

	// answered counts the streams whose answer has left the server.
	answered atomic.Int32

	pushMu sync.Mutex
	// onPush is called with the metadata of each METADATA_PUSH frame that a
	// dialer sends.
	onPush func(metadata string)

	mu   sync.Mutex
	open map[net.Conn]bool
}

// trackingListener hands the connections it accepts to its testAcceptor.
type trackingListener struct {
	net.Listener
	acc *testAcceptor
}

type trackedConn struct {
	net.Conn
	acc *testAcceptor
}

func (c trackedConn) Close() error {
	c.acc.mu.Lock()
	delete(c.acc.open, c.Conn)
	c.acc.mu.Unlock()
	return c.Conn.Close()
}

func (l trackingListener) Accept() (net.Conn, error) {
	conn, err := l.Listener.Accept()
	if err != nil {
		return nil, err
	}
	n := l.acc.accepted.Add(1)
	l.acc.mu.Lock()
	l.acc.open[conn] = true
	l.acc.mu.Unlock()
	if l.acc.dying.Add(-1) >= 0 {
		// The delay differs from one connection to the next, so that the
		// loss hits the dialer at different points of its work.
		time.AfterFunc(time.Duration(n%20)*25*time.Microsecond, func() { conn.Close() })
	}
	return trackedConn{Conn: conn, acc: l.acc}, nil
}

// setOnPush sets the function that is called with the metadata of each
// METADATA_PUSH frame that a dialer sends.
func (a *testAcceptor) setOnPush(fn func(metadata string)) {
	a.pushMu.Lock()
	defer a.pushMu.Unlock()
	a.onPush = fn
}

func (a *testAcceptor) pushed(msg payload.Payload) {
	metadata, _ := msg.Metadata()
	a.pushMu.Lock()
	fn := a.onPush
	a.pushMu.Unlock()
	if fn != nil {
		fn(string(metadata))
	}
}

// answerBytes is how much the server answers to every stream: more than the
// buffers of a local connection take.
const answerBytes = 32 << 20

// answer answers a stream with answerBytes of data, as a destination does
// that sends a file.
func (a *testAcceptor) answer(incoming flux.Flux) flux.Flux {
	incoming.Subscribe(context.Background(),
		rx.OnSubscribe(func(_ context.Context, su rx.Subscription) { su.Request(rx.RequestMax) }))
	return flux.Create(func(_ context.Context, sink flux.Sink) {
		go func() {
			// The connection may close under the stream.
			defer func() { _ = recover() }()
			chunk := make([]byte, 64<<10)
			for sent := 0; sent < answerBytes; sent += len(chunk) {
				sink.Next(payload.New(chunk, nil))
			}
			a.answered.Add(1)
		}()
	})
}

// loseAll loses every open connection, as a network failure does.
func (a *testAcceptor) loseAll() {
	a.mu.Lock()
	defer a.mu.Unlock()
	for conn := range a.open {
		conn.Close()
	}
}

// openConns returns the number of connections that the server has not closed
// and that have not been lost.
func (a *testAcceptor) openConns() int {
	a.mu.Lock()
	defer a.mu.Unlock()
	return len(a.open)
}

func startTestAcceptor(t *testing.T) *testAcceptor {
	t.Helper()
	lis, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("net.Listen got unexpected error: %v", err)
	}
	acc := &testAcceptor{addr: lis.Addr().String(), open: make(map[net.Conn]bool)}
	locator, err := proto.Marshal(&dconpb.EstablishConduitResponse{
		ServiceLocator: &dconpb.ServiceLocator{Locator: &dconpb.ServiceLocator_Sni{Sni: "svc.test.dcon"}},
	})
	if err != nil {
		t.Fatalf("proto.Marshal got unexpected error: %v", err)
	}
	go func() {
		_ = rsocket.Receive().
			Acceptor(func(_ context.Context, setup payload.SetupPayload, rs rsocket.CloseableRSocket) (rsocket.RSocket, error) {
				if setup.DataUTF8() != probeSignal {
					if acc.pushLocator.Load() {
						go rs.MetadataPush(payload.New(nil, locator))
					}
					return rsocket.NewAbstractSocket(rsocket.MetadataPush(acc.pushed), rsocket.RequestChannel(acc.answer)), nil
				}
				return rsocket.NewAbstractSocket(rsocket.RequestResponse(func(msg payload.Payload) mono.Mono {
					return mono.Just(payload.New([]byte("ACK "+msg.DataUTF8()), nil))
				})), nil
			}).
			Transport(func(context.Context) (rsockettransport.ServerTransport, error) {
				return rsockettransport.NewTCPServerTransport(func(context.Context) (net.Listener, error) {
					return trackingListener{Listener: lis, acc: acc}, nil
				}), nil
			}).
			Serve(t.Context())
	}()
	t.Cleanup(func() { lis.Close() })
	return acc
}

func freeTCPPort(t *testing.T) int {
	t.Helper()
	lis, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("net.Listen got unexpected error: %v", err)
	}
	defer lis.Close()
	return lis.Addr().(*net.TCPAddr).Port
}

func forwardSessionRequest(entryPort int) *dconpb.EstablishSessionRequest {
	return &dconpb.EstablishSessionRequest{
		EstablishConduitRequest: &dconpb.EstablishConduitRequest{
			Type:                dconpb.EstablishConduitRequest_CONDUIT_TYPE_FORWARD,
			EntryPort:           int32(entryPort),
			DestinationEndpoint: "127.0.0.1:1",
		},
		AutoReconnect: true,
	}
}

// TestSessionReconnect_ConnectionThatDiesAtOnceIsReplaced covers a network
// that flaps: the connection of a forward session is lost, and each new one
// is lost again right after it has opened, many times over. When the network
// is back, the session must have a conduit again.
//
// In version 0.2.0 and older a new conduit was recorded in its session only
// after it had been established. If its connection was lost in between,
// nobody was told, the session kept the ID of a conduit that no longer
// existed, and its port stayed dead until the dialer was restarted.
func TestSessionReconnect_ConnectionThatDiesAtOnceIsReplaced(t *testing.T) {
	const dying = 500
	acc := startTestAcceptor(t)
	s, err := New(t.Context(), "test-host", "127.0.0.1", func() (rsockettransport.ClientTransporter, error) {
		return rsocket.TCPClient().SetAddr(acc.addr).Build(), nil
	}, RetryPolicy{Strategy: &ConstantBackoff{Interval: time.Millisecond}}, 0)
	if err != nil {
		t.Fatalf("New() got unexpected error: %v", err)
	}
	defer s.Manager.Shutdown()
	entryPort := freeTCPPort(t)
	if _, err := s.EstablishSession(t.Context(), forwardSessionRequest(entryPort)); err != nil {
		t.Fatalf("EstablishSession() got unexpected error: %v", err)
	}
	if err := s.CheckSessions(); err != nil {
		t.Fatalf("CheckSessions() after EstablishSession() got unexpected error: %v", err)
	}

	acc.dying.Store(dying)
	acc.loseAll()

	// The network is back when the acceptor has lost the last of these
	// connections. From then on the session must get a conduit whose entry
	// port works.
	const limit = time.Minute
	working := func() error {
		if left := acc.dying.Load(); left > 0 {
			return fmt.Errorf("the session stopped reconnecting: the acceptor has seen %d connections and would still lose %d", acc.accepted.Load(), left)
		}
		if err := s.CheckSessions(); err != nil {
			return err
		}
		conn, err := net.DialTimeout("tcp", fmt.Sprintf("127.0.0.1:%d", entryPort), time.Second)
		if err != nil {
			return fmt.Errorf("entry port: %v", err)
		}
		return conn.Close()
	}
	for deadline := time.Now().Add(limit); time.Now().Before(deadline); time.Sleep(10 * time.Millisecond) {
		if working() == nil {
			return
		}
	}
	t.Errorf("%v after the connection was lost, the session has no working conduit: %v", limit, working())
}

// TestTeardownSession_ReconnectInFlightLeavesNoConduit covers a session that
// is torn down while it reconnects, with a connection attempt in flight that
// completes only afterwards. The conduit of that attempt belongs to no
// session, so it must be closed again: nobody else would ever close it, and
// it would keep the entry port.
func TestTeardownSession_ReconnectInFlightLeavesNoConduit(t *testing.T) {
	acc := startTestAcceptor(t)
	var slow atomic.Bool
	dialing, gate := make(chan struct{}, 16), make(chan struct{})
	var gateOnce sync.Once
	openGate := func() { gateOnce.Do(func() { close(gate) }) }
	t.Cleanup(openGate)
	s, err := New(t.Context(), "test-host", "127.0.0.1", func() (rsockettransport.ClientTransporter, error) {
		dial := rsocket.TCPClient().SetAddr(acc.addr).Build()
		return func(ctx context.Context) (*rsockettransport.Transport, error) {
			if slow.Load() {
				// A dial that completes late, whatever happens to its
				// context meanwhile.
				dialing <- struct{}{}
				<-gate
				ctx = context.WithoutCancel(ctx)
			}
			return dial(ctx)
		}, nil
	}, RetryPolicy{Strategy: &ConstantBackoff{Interval: time.Millisecond}}, 0)
	if err != nil {
		t.Fatalf("New() got unexpected error: %v", err)
	}
	defer s.Manager.Shutdown()
	entryPort := freeTCPPort(t)
	resp, err := s.EstablishSession(t.Context(), forwardSessionRequest(entryPort))
	if err != nil {
		t.Fatalf("EstablishSession() got unexpected error: %v", err)
	}

	// The connection is lost, and the session starts to reconnect. The
	// acceptor can only lose a connection that it has accepted.
	for deadline := time.Now().Add(5 * time.Second); acc.openConns() == 0 && time.Now().Before(deadline); time.Sleep(time.Millisecond) {
	}
	slow.Store(true)
	acc.loseAll()
	select {
	case <-dialing:
	case <-time.After(5 * time.Second):
		t.Fatalf("the session did not start to reconnect within 5s after its connection was lost")
	}

	if _, err := s.TeardownSession(t.Context(), &dconpb.TeardownSessionRequest{SessionId: resp.SessionId}); err != nil {
		t.Fatalf("TeardownSession() got unexpected error: %v", err)
	}
	// Only now does the attempt that was in flight get its connection.
	openGate()

	const limit = 5 * time.Second
	entryFree := func() bool {
		lis, err := net.Listen("tcp", fmt.Sprintf("127.0.0.1:%d", entryPort))
		if err != nil {
			return false
		}
		lis.Close()
		return true
	}
	for deadline := time.Now().Add(limit); time.Now().Before(deadline); time.Sleep(10 * time.Millisecond) {
		if acc.accepted.Load() >= 2 && acc.openConns() == 0 && entryFree() {
			return
		}
	}
	t.Errorf("%v after TeardownSession(): the acceptor has seen %d connections and %d are still open, entry port free = %t; want the late connection closed and the entry port free", limit, acc.accepted.Load(), acc.openConns(), entryFree())
}

// TestCheckSessions covers the part of the health check that looks at the
// forward ports: a forward session without an ACTIVE conduit is a port that
// does not work.
func TestCheckSessions(t *testing.T) {
	s, err := New(t.Context(), "test-host", "127.0.0.1", func() (rsockettransport.ClientTransporter, error) {
		return nil, errors.New("not used")
	}, RetryPolicy{}, 0)
	if err != nil {
		t.Fatalf("New() got unexpected error: %v", err)
	}
	if err := s.CheckSessions(); err != nil {
		t.Errorf("CheckSessions() without sessions got unexpected error: %v", err)
	}

	// A reverse session is not a forward port.
	reverse := &dconpb.EstablishSessionRequest{EstablishConduitRequest: &dconpb.EstablishConduitRequest{
		Type: dconpb.EstablishConduitRequest_CONDUIT_TYPE_REVERSE,
	}}
	if err := s.Sessions.Add(session.New(t.Context(), "reverse", reverse, []string{"gone"})); err != nil {
		t.Fatalf("Sessions.Add() got unexpected error: %v", err)
	}
	if err := s.CheckSessions(); err != nil {
		t.Errorf("CheckSessions() with a reverse session without conduit got unexpected error: %v", err)
	}

	// A forward session that is being established has had no conduit yet.
	if err := s.Sessions.Add(session.New(t.Context(), "new", forwardSessionRequest(8008), nil)); err != nil {
		t.Fatalf("Sessions.Add() got unexpected error: %v", err)
	}
	if err := s.CheckSessions(); err != nil {
		t.Errorf("CheckSessions() with a forward session that is being established got unexpected error: %v", err)
	}

	// A forward session whose conduit is missing, as while it reconnects.
	forward := forwardSessionRequest(8006)
	forward.AutoReconnect = false
	if err := s.Sessions.Add(session.New(t.Context(), "forward", forward, []string{"c1"})); err != nil {
		t.Fatalf("Sessions.Add() got unexpected error: %v", err)
	}
	if err := s.CheckSessions(); err == nil || !strings.Contains(err.Error(), "8006") {
		t.Errorf("CheckSessions() with a forward session whose conduit is missing = %v, want an error that names port 8006", err)
	}

	// The conduit exists and is ACTIVE.
	inSignal, release := make(chan struct{}), make(chan struct{})
	c, err := s.Manager.Add(t.Context(), "c1", forward.EstablishConduitRequest, nil, func() {
		close(inSignal)
		<-release
	}, trace.SpanContext{})
	if err != nil {
		t.Fatalf("Manager.Add() got unexpected error: %v", err)
	}
	if err := s.CheckSessions(); err != nil {
		t.Errorf("CheckSessions() with an ACTIVE conduit got unexpected error: %v", err)
	}

	// The conduit is closing: it no longer serves its port.
	c.BeginClose()
	waitClosed(t, inSignal, "the closing step of the conduit")
	if err := s.CheckSessions(); err == nil {
		t.Errorf("CheckSessions() with a conduit that is closing succeeded, want an error")
	}
	close(release)
	waitClosed(t, c.Done(), "the close of the conduit")
}

// TestCheckHealth_AcceptorAnswersButForwardPortIsDead covers the failure in
// which the acceptor can be reached and a forward port is dead all the same.
// The health check must report it.
//
// In version 0.2.0 and older the health check only probed the acceptor: a
// worker whose port 8006 was dead passed it every minute for four days.
func TestCheckHealth_AcceptorAnswersButForwardPortIsDead(t *testing.T) {
	acc := startTestAcceptor(t)
	s, err := New(t.Context(), "test-host", "127.0.0.1", func() (rsockettransport.ClientTransporter, error) {
		return rsocket.TCPClient().SetAddr(acc.addr).Build(), nil
	}, RetryPolicy{}, 0)
	if err != nil {
		t.Fatalf("New() got unexpected error: %v", err)
	}
	if err := s.CheckHealth(t.Context()); err != nil {
		t.Fatalf("CheckHealth() without sessions got unexpected error: %v", err)
	}

	forward := forwardSessionRequest(8006)
	forward.AutoReconnect = false
	if err := s.Sessions.Add(session.New(t.Context(), "forward", forward, []string{"gone"})); err != nil {
		t.Fatalf("Sessions.Add() got unexpected error: %v", err)
	}

	if err := s.CheckHealth(t.Context()); err == nil || !strings.Contains(err.Error(), "8006") {
		t.Errorf("CheckHealth() with a forward port without conduit = %v, want an error that names port 8006", err)
	}
}

// TestTeardownSession_EndsConnectionAttemptInFlight covers a session that is
// torn down while it reconnects and its connection attempt hangs, as it does
// when the acceptor cannot be reached. The attempt must end with the session,
// not run on until the connect timeout.
func TestTeardownSession_EndsConnectionAttemptInFlight(t *testing.T) {
	acc := startTestAcceptor(t)
	var hang atomic.Bool
	dialing, ended := make(chan struct{}, 16), make(chan error, 16)
	s, err := New(t.Context(), "test-host", "127.0.0.1", func() (rsockettransport.ClientTransporter, error) {
		dial := rsocket.TCPClient().SetAddr(acc.addr).Build()
		return func(ctx context.Context) (*rsockettransport.Transport, error) {
			if hang.Load() {
				dialing <- struct{}{}
				<-ctx.Done()
				ended <- ctx.Err()
				return nil, ctx.Err()
			}
			return dial(ctx)
		}, nil
	}, RetryPolicy{Strategy: &ConstantBackoff{Interval: time.Millisecond}}, 0)
	if err != nil {
		t.Fatalf("New() got unexpected error: %v", err)
	}
	defer s.Manager.Shutdown()
	resp, err := s.EstablishSession(t.Context(), forwardSessionRequest(freeTCPPort(t)))
	if err != nil {
		t.Fatalf("EstablishSession() got unexpected error: %v", err)
	}
	// The connection is lost, and the session starts to reconnect. The
	// acceptor can only lose a connection that it has accepted.
	for deadline := time.Now().Add(5 * time.Second); acc.openConns() == 0 && time.Now().Before(deadline); time.Sleep(time.Millisecond) {
	}
	hang.Store(true)
	acc.loseAll()
	select {
	case <-dialing:
	case <-time.After(5 * time.Second):
		t.Fatalf("the session did not start to reconnect within 5s after its connection was lost")
	}

	if _, err := s.TeardownSession(t.Context(), &dconpb.TeardownSessionRequest{SessionId: resp.SessionId}); err != nil {
		t.Fatalf("TeardownSession() got unexpected error: %v", err)
	}

	// The connect timeout of the attempt is 10 s.
	const limit = 2 * time.Second
	select {
	case <-ended:
	case <-time.After(limit):
		t.Errorf("the connection attempt is still running %v after TeardownSession(), want it ended with the session", limit)
	}
	// And no further attempt starts.
	select {
	case <-dialing:
		t.Errorf("a new connection attempt started after TeardownSession()")
	case <-time.After(300 * time.Millisecond):
	}
}

// TestEstablishSession_FailureLeavesNothingBehind covers a session of two
// conduits of which one is established and the other fails. EstablishSession
// fails, and it must leave neither the session nor the conduit that was
// established, and must not start to reconnect.
func TestEstablishSession_FailureLeavesNothingBehind(t *testing.T) {
	acc := startTestAcceptor(t)
	acc.pushLocator.Store(true)
	var dials atomic.Int32
	failSecond := make(chan struct{})
	s, err := New(t.Context(), "test-host", "127.0.0.1", func() (rsockettransport.ClientTransporter, error) {
		dial := rsocket.TCPClient().SetAddr(acc.addr).Build()
		return func(ctx context.Context) (*rsockettransport.Transport, error) {
			if dials.Add(1) == 2 {
				// The second conduit fails once the first is established.
				<-failSecond
				return nil, errors.New("connection refused")
			}
			return dial(ctx)
		}, nil
	}, RetryPolicy{Strategy: &ConstantBackoff{Interval: time.Millisecond}}, 0)
	if err != nil {
		t.Fatalf("New() got unexpected error: %v", err)
	}
	defer s.Manager.Shutdown()

	result := make(chan error, 1)
	go func() {
		_, err := s.EstablishSession(t.Context(), reverseSessionRequest(2))
		result <- err
	}()
	// Wait until the first conduit is established and in its session.
	var established []string
	for deadline := time.Now().Add(5 * time.Second); len(established) == 0 && time.Now().Before(deadline); time.Sleep(time.Millisecond) {
		for _, sess := range s.Sessions.Sessions() {
			established = sess.Conduits()
		}
	}
	if len(established) != 1 {
		t.Fatalf("the session has the conduits %v 5s after EstablishSession() began, want one", established)
	}

	close(failSecond)

	select {
	case err := <-result:
		if err == nil {
			t.Fatalf("EstablishSession() succeeded although one of its conduits failed")
		}
	case <-time.After(5 * time.Second):
		t.Fatalf("EstablishSession() did not return within 5s after one of its conduits failed")
	}
	if got := s.Sessions.Sessions(); len(got) != 0 {
		t.Errorf("after EstablishSession() failed, the dialer still has %d session(s), want none", len(got))
	}
	if _, err := s.Manager.Conduit(established[0]); err == nil {
		t.Errorf("after EstablishSession() failed, the conduit that was established is still in the manager")
	}
	for deadline := time.Now().Add(5 * time.Second); acc.openConns() != 0 && time.Now().Before(deadline); time.Sleep(10 * time.Millisecond) {
	}
	if got := acc.openConns(); got != 0 {
		t.Errorf("after EstablishSession() failed, the acceptor still has %d open connection(s), want none", got)
	}
	// Nothing reconnects.
	time.Sleep(300 * time.Millisecond)
	if got := dials.Load(); got != 2 {
		t.Errorf("the dialer made %d connection attempts, want 2: nothing may reconnect after EstablishSession() failed", got)
	}
}

// waitClosed fails the test if ch is not closed within 5 s.
func waitClosed(t *testing.T, ch <-chan struct{}, what string) {
	t.Helper()
	select {
	case <-ch:
	case <-time.After(5 * time.Second):
		t.Fatalf("%s did not happen within 5s", what)
	}
}

// reverseSessionRequest returns the request for a session of reverse conduits.
func reverseSessionRequest(conduits int) *dconpb.EstablishSessionRequest {
	return &dconpb.EstablishSessionRequest{
		EstablishConduitRequest: &dconpb.EstablishConduitRequest{
			Type:                dconpb.EstablishConduitRequest_CONDUIT_TYPE_REVERSE,
			Protocol:            dconpb.EstablishConduitRequest_PROTOCOL_TCP,
			ServerName:          "svc",
			InstanceId:          "test",
			DestinationEndpoint: "127.0.0.1:1",
		},
		ReverseConduitCount: int32(conduits),
		AutoReconnect:       true,
	}
}

// TestTeardownSession_ReverseConduitSignalsBeforeItsStreamsEnd covers the
// teardown of a session of reverse conduits. The entry port of a reverse
// conduit is on the acceptor, so the dialer must send the CLOSE signal while
// the context of the conduit is still alive, and end the streams only after
// it: otherwise the acceptor sends clients into a conduit whose streams the
// dialer has already reset.
func TestTeardownSession_ReverseConduitSignalsBeforeItsStreamsEnd(t *testing.T) {
	acc := startTestAcceptor(t)
	acc.pushLocator.Store(true)
	s, err := New(t.Context(), "test-host", "127.0.0.1", func() (rsockettransport.ClientTransporter, error) {
		return rsocket.TCPClient().SetAddr(acc.addr).Build(), nil
	}, RetryPolicy{Strategy: &ConstantBackoff{Interval: time.Millisecond}}, 0)
	if err != nil {
		t.Fatalf("New() got unexpected error: %v", err)
	}
	defer s.Manager.Shutdown()
	resp, err := s.EstablishSession(t.Context(), reverseSessionRequest(1))
	if err != nil {
		t.Fatalf("EstablishSession() got unexpected error: %v", err)
	}
	con, err := s.Manager.Conduit(resp.EstablishConduitResponses[0].ConduitId)
	if err != nil {
		t.Fatalf("Manager.Conduit() got unexpected error: %v", err)
	}
	// What the context of the conduit says when the signal reaches the
	// acceptor. The dialer pauses for 100 ms after it has sent the signal.
	contextAtSignal := make(chan error, 1)
	acc.setOnPush(func(metadata string) {
		if metadata == closeSignal {
			select {
			case contextAtSignal <- con.Context().Err():
			default:
			}
		}
	})

	if _, err := s.TeardownSession(t.Context(), &dconpb.TeardownSessionRequest{SessionId: resp.SessionId}); err != nil {
		t.Fatalf("TeardownSession() got unexpected error: %v", err)
	}

	select {
	case err := <-contextAtSignal:
		if err != nil {
			t.Errorf("the context of the conduit had ended (%v) when the CLOSE signal reached the acceptor, want the streams ended only after the signal", err)
		}
	case <-time.After(5 * time.Second):
		t.Errorf("the acceptor got no CLOSE signal within 5s after TeardownSession()")
	}
}

// TestSessionReconnect_ConnectionLostBeforeHandOver covers a new conduit of a
// session whose connection is lost after the conduit has been established and
// before it is handed to the session. The attempt must count as failed, and
// the session must try again and get a conduit that works.
//
// In version 0.2.0 and older the session recorded the ID of the conduit that
// was already gone. Nobody had been told of its end, so nothing replaced it,
// and the port stayed dead until the dialer was restarted.
func TestSessionReconnect_ConnectionLostBeforeHandOver(t *testing.T) {
	acc := startTestAcceptor(t)
	// loseNext makes the next conduit that is about to be handed over lose
	// its connection first.
	var loseNext atomic.Bool
	lost := make(chan error, 1)
	// dialed counts the connections that the dialer has opened.
	var dialed atomic.Int32
	s, err := New(t.Context(), "test-host", "127.0.0.1", func() (rsockettransport.ClientTransporter, error) {
		dial := rsocket.TCPClient().SetAddr(acc.addr).Build()
		return func(ctx context.Context) (*rsockettransport.Transport, error) {
			tp, err := dial(ctx)
			if err == nil {
				dialed.Add(1)
			}
			return tp, err
		}, nil
	}, RetryPolicy{Strategy: &ConstantBackoff{Interval: time.Millisecond}}, 0)
	if err != nil {
		t.Fatalf("New() got unexpected error: %v", err)
	}
	defer s.Manager.Shutdown()
	s.beforeHandOver = func(id string) {
		if !loseNext.Swap(false) {
			return
		}
		con, err := s.Manager.Conduit(id)
		if err != nil {
			lost <- fmt.Errorf("the conduit to be handed over is not in the manager: %v", err)
			return
		}
		// The acceptor can only lose a connection that it has accepted, so
		// wait until it has accepted every connection that the dialer has
		// opened. This one need not be the second: an attempt before it
		// may have failed after it had connected.
		for deadline := time.Now().Add(5 * time.Second); acc.accepted.Load() < dialed.Load() && time.Now().Before(deadline); time.Sleep(time.Millisecond) {
		}
		acc.loseAll()
		select {
		case <-con.Done():
			lost <- nil
		case <-time.After(5 * time.Second):
			lost <- errors.New("the conduit is still open 5s after its connection was lost")
		}
	}
	entryPort := freeTCPPort(t)
	if _, err := s.EstablishSession(t.Context(), forwardSessionRequest(entryPort)); err != nil {
		t.Fatalf("EstablishSession() got unexpected error: %v", err)
	}

	// The connection is lost, and so is the first one that replaces it.
	for deadline := time.Now().Add(5 * time.Second); acc.openConns() == 0 && time.Now().Before(deadline); time.Sleep(time.Millisecond) {
	}
	loseNext.Store(true)
	acc.loseAll()
	select {
	case err := <-lost:
		if err != nil {
			t.Fatal(err)
		}
	case <-time.After(10 * time.Second):
		t.Fatalf("the session did not establish a new conduit within 10s after its connection was lost")
	}

	const limit = 10 * time.Second
	working := func() error {
		if err := s.CheckSessions(); err != nil {
			return err
		}
		conn, err := net.DialTimeout("tcp", fmt.Sprintf("127.0.0.1:%d", entryPort), time.Second)
		if err != nil {
			return fmt.Errorf("entry port: %v", err)
		}
		return conn.Close()
	}
	for deadline := time.Now().Add(limit); time.Now().Before(deadline); time.Sleep(10 * time.Millisecond) {
		if working() == nil {
			return
		}
	}
	t.Errorf("%v after a new conduit lost its connection before it was handed to its session, the session has no working conduit: %v", limit, working())
}

// TestWatchHealth_ReportsForwardPortWithoutConduit checks that the periodic
// health check is the one that covers the forward ports.
func TestWatchHealth_ReportsForwardPortWithoutConduit(t *testing.T) {
	acc := startTestAcceptor(t)
	s, err := New(t.Context(), "test-host", "127.0.0.1", func() (rsockettransport.ClientTransporter, error) {
		return rsocket.TCPClient().SetAddr(acc.addr).Build(), nil
	}, RetryPolicy{}, 0)
	if err != nil {
		t.Fatalf("New() got unexpected error: %v", err)
	}
	ctx, cancel := context.WithCancel(t.Context())
	reports, returned := make(chan error, 256), make(chan struct{})
	go func() {
		defer close(returned)
		s.WatchHealth(ctx, 20*time.Millisecond, func(err error) {
			select {
			case reports <- err:
			default:
			}
		})
	}()
	// nextReport returns the first report for which want holds.
	nextReport := func(what string, want func(error) bool) {
		t.Helper()
		timeout := time.After(10 * time.Second)
		for {
			select {
			case err := <-reports:
				if want(err) {
					return
				}
			case <-timeout:
				t.Fatalf("WatchHealth() did not report %s within 10s", what)
			}
		}
	}
	nextReport("a dialer that serves", func(err error) bool { return err == nil })

	forward := forwardSessionRequest(8006)
	forward.AutoReconnect = false
	if err := s.Sessions.Add(session.New(t.Context(), "forward", forward, []string{"gone"})); err != nil {
		t.Fatalf("Sessions.Add() got unexpected error: %v", err)
	}

	nextReport("the forward port 8006 without conduit", func(err error) bool {
		return err != nil && strings.Contains(err.Error(), "8006")
	})
	cancel()
	waitClosed(t, returned, "the return of WatchHealth() after its context ended")
}

// TestConduit_SilentConnectionClosedWhileClientDoesNotRead covers a
// connection that dies while a stream is writing to a client that does not
// read: the goroutine that handles the frames of the connection is blocked
// in that write. The dialer must still close the conduit when its keep-alive
// lifetime has run out, so that its session can replace it. rsocket-go by
// itself closes a connection only when its frame handlers have returned.
func TestConduit_SilentConnectionClosedWhileClientDoesNotRead(t *testing.T) {
	const (
		lifetime = 3 * time.Second
		limit    = 30 * time.Second
	)
	acc := startTestAcceptor(t)
	relay := nettest.StartRelay(t, acc.addr)
	s, err := New(t.Context(), "test-host", "127.0.0.1", func() (rsockettransport.ClientTransporter, error) {
		return dcontransport.CreateClientTransport(dcontransport.ClientConfig{
			Target:        relay.Addr,
			TransportType: dcontransport.TCP,
		})
	}, RetryPolicy{}, lifetime/12)
	if err != nil {
		t.Fatalf("New() got unexpected error: %v", err)
	}
	defer s.Manager.Shutdown()
	s.keepAliveLifetime = lifetime
	entryPort := freeTCPPort(t)
	resp, err := s.EstablishConduit(t.Context(), &dconpb.EstablishConduitRequest{
		Type:                dconpb.EstablishConduitRequest_CONDUIT_TYPE_FORWARD,
		EntryPort:           int32(entryPort),
		DestinationEndpoint: "127.0.0.1:1",
	})
	if err != nil {
		t.Fatalf("EstablishConduit() got unexpected error: %v", err)
	}
	// A client that sends a request and never reads the answer. The
	// acceptor answers every stream with more than the client's buffers take.
	client, err := net.DialTimeout("tcp", fmt.Sprintf("127.0.0.1:%d", entryPort), 5*time.Second)
	if err != nil {
		t.Fatalf("net.DialTimeout() to the entry port got unexpected error: %v", err)
	}
	defer client.Close()
	if _, err := client.Write([]byte("go")); err != nil {
		t.Fatalf("client.Write() got unexpected error: %v", err)
	}
	for deadline := time.Now().Add(20 * time.Second); acc.answered.Load() == 0 && time.Now().Before(deadline); time.Sleep(10 * time.Millisecond) {
	}
	if acc.answered.Load() == 0 {
		t.Fatalf("the answer did not leave the acceptor within 20s")
	}
	// Let what is on its way arrive: the dialer is then blocked in its write
	// to the client.
	time.Sleep(time.Second)
	open := func() bool {
		_, err := s.Manager.Conduit(resp.ConduitId)
		return err == nil
	}
	if !open() {
		t.Fatalf("dialer has no conduit %q during the download", resp.ConduitId)
	}

	relay.Silence()
	start := time.Now()
	for open() && time.Since(start) < limit {
		time.Sleep(10 * time.Millisecond)
	}
	if open() {
		t.Errorf("dialer still has the conduit %v after its connection went silent during a download to a client that does not read, want it closed after the lifetime of %v", limit, lifetime)
	} else {
		t.Logf("dialer closed the conduit %v after the last frame (lifetime %v)", time.Since(start).Round(10*time.Millisecond), lifetime)
	}
}

// TestEstablishConduit_CallerGoneBeforeHandOverLeavesNoConduit covers a
// caller that gives up, by cancelling or by its deadline, while its conduit
// is being established. It never learns the ID of the conduit, so it can
// never tear it down: the dialer must not keep the conduit.
//
// Before, the conduit of such a caller stayed, with its entry port, until
// the dialer was restarted.
func TestEstablishConduit_CallerGoneBeforeHandOverLeavesNoConduit(t *testing.T) {
	acc := startTestAcceptor(t)
	s, err := New(t.Context(), "test-host", "127.0.0.1", func() (rsockettransport.ClientTransporter, error) {
		return rsocket.TCPClient().SetAddr(acc.addr).Build(), nil
	}, RetryPolicy{Strategy: &ConstantBackoff{Interval: time.Millisecond}}, 0)
	if err != nil {
		t.Fatalf("New() got unexpected error: %v", err)
	}
	defer s.Manager.Shutdown()
	ctx, cancel := context.WithCancel(t.Context())
	defer cancel()
	// The caller goes away when its conduit is established and about to be
	// handed over.
	established := make(chan string, 1)
	s.beforeHandOver = func(id string) {
		established <- id
		cancel()
	}
	entryPort := freeTCPPort(t)
	req := forwardSessionRequest(entryPort).GetEstablishConduitRequest()

	resp, err := s.EstablishConduit(ctx, req)
	if err == nil {
		t.Fatalf("EstablishConduit() of a caller that has gone = %v, want an error", resp)
	}
	if got := status.Code(err); got != codes.Canceled {
		t.Errorf("EstablishConduit() of a caller that has gone: error code %v, want %v; error: %v", got, codes.Canceled, err)
	}
	var id string
	select {
	case id = <-established:
	default:
		t.Fatalf("EstablishConduit() failed before its conduit was established: %v", err)
	}
	if _, err := s.Manager.Conduit(id); err == nil {
		t.Errorf("the dialer still has the conduit of a caller that has gone")
	}
	lis, err := net.Listen("tcp", fmt.Sprintf("127.0.0.1:%d", entryPort))
	if err != nil {
		t.Errorf("entry port %d is still in use after its caller has gone: %v", entryPort, err)
	} else {
		lis.Close()
	}
	for deadline := time.Now().Add(5 * time.Second); acc.openConns() != 0 && time.Now().Before(deadline); time.Sleep(10 * time.Millisecond) {
	}
	if got := acc.openConns(); got != 0 {
		t.Errorf("the acceptor still has %d open connection(s) 5s after the caller has gone, want none", got)
	}
}

// TestForwardConduit_EntryPortIsFreeWhenCloseReturns covers a forward conduit
// that closes right after it was established, as when its connection is lost
// at once. When Close has returned, the entry port must be free: the session
// opens the same port again at once for the conduit that replaces it.
//
// Before, the listening loop registered the close of the entry port on its
// own goroutine. A conduit that closed before that goroutine had run
// returned from Close with the port still bound, and the next conduit on the
// port failed once with "address already in use".
func TestForwardConduit_EntryPortIsFreeWhenCloseReturns(t *testing.T) {
	acc := startTestAcceptor(t)
	s, err := New(t.Context(), "test-host", "127.0.0.1", func() (rsockettransport.ClientTransporter, error) {
		return rsocket.TCPClient().SetAddr(acc.addr).Build(), nil
	}, RetryPolicy{Strategy: &ConstantBackoff{Interval: time.Millisecond}}, 0)
	if err != nil {
		t.Fatalf("New() got unexpected error: %v", err)
	}
	defer s.Manager.Shutdown()
	req := forwardSessionRequest(freeTCPPort(t)).GetEstablishConduitRequest()

	// Each conduit is closed as soon as its entry port is open, and the next
	// one opens the same port at once. The window is a few microseconds
	// wide, so the test goes through it many times.
	const attempts = 1000
	for i := range attempts {
		ctx, cancel := context.WithCancel(t.Context())
		// The caller gives up when the conduit is established, which closes
		// the conduit before EstablishConduit returns.
		s.beforeHandOver = func(string) { cancel() }
		_, err := s.EstablishConduit(ctx, req)
		cancel()
		if got := status.Code(err); got != codes.Canceled {
			t.Fatalf("attempt %d of %d: EstablishConduit() on the entry port of a conduit that has just closed: error %v, want code %v: the port must be free", i+1, attempts, err, codes.Canceled)
		}
	}
}
