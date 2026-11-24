package acceptor

import (
	"context"
	"errors"
	"fmt"
	"net"
	"runtime"
	"strings"
	"sync"
	"sync/atomic"
	"syscall"
	"testing"
	"time"

	"github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/conduit"
	"github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/mesh"
	"github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/nettest"
	dconpb "github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/proto/dconpb"
	dcontransport "github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/transport"
	"github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/version"
	"github.com/jjeffcaii/reactor-go/scheduler"
	rsockettransport "github.com/rsocket/rsocket-go/core/transport"
	"github.com/rsocket/rsocket-go/payload"
	"github.com/rsocket/rsocket-go"
	"github.com/rsocket/rsocket-go/rx/flux"
	"github.com/rsocket/rsocket-go/rx"
	"go.opentelemetry.io/otel/trace"
	"google.golang.org/protobuf/proto"
)

func startTestAcceptor(t *testing.T) (*Service, string) {
	t.Helper()
	return startTestAcceptorWith(t, nil)
}

// startTestAcceptorWith starts an acceptor. configure, if not nil, changes
// the Service before it runs.
func startTestAcceptorWith(t *testing.T, configure func(*Service)) (*Service, string) {
	t.Helper()
	return startTestAcceptorCtx(t.Context(), t, configure)
}

// startTestAcceptorCtx starts an acceptor that shuts down when ctx ends.
func startTestAcceptorCtx(ctx context.Context, t *testing.T, configure func(*Service)) (*Service, string) {
	t.Helper()
	ctx, cancel := context.WithCancel(ctx)
	lis, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("net.Listen unexpected error: %v", err)
	}
	t.Cleanup(func() {
		cancel()
		lis.Close()
	})
	meshSrv := mesh.New(ctx, 0, 0, "strict_dns")
	srv, err := New(func() (rsockettransport.ServerTransporter, error) {
		return func(context.Context) (rsockettransport.ServerTransport, error) {
			return rsockettransport.NewTCPServerTransport(func(context.Context) (net.Listener, error) {
				return lis, nil
			}), nil
		}, nil
	}, meshSrv, "127.0.0.1")
	if err != nil {
		t.Fatalf("New() unexpected error: %v", err)
	}
	if configure != nil {
		configure(srv)
	}
	go func() {
		_ = srv.Run(ctx)
	}()
	return srv, lis.Addr().String()
}

func mustMarshalProto(t *testing.T, msg proto.Message) []byte {
	t.Helper()
	b, err := proto.Marshal(msg)
	if err != nil {
		t.Fatalf("proto.Marshal(%T) unexpected error: %v", msg, err)
	}
	return b
}

func connectSetup(ctx context.Context, addr, id string, meta []byte, hasMeta bool) (rsocket.Client, error) {
	var setup payload.Payload
	if hasMeta {
		setup = payload.New([]byte(id), meta)
	} else {
		setup = payload.New([]byte(id), nil)
	}
	var (
		once    sync.Once
		closeCh = make(chan error, 1)
	)
	client, err := rsocket.Connect().
		OnClose(func(err error) { once.Do(func() { closeCh <- err }) }).
		SetupPayload(setup).
		Transport(rsocket.TCPClient().SetAddr(addr).Build()).
		Start(ctx)
	if err != nil {
		return nil, err
	}
	select {
	case closeErr := <-closeCh:
		_ = client.Close()
		return nil, closeErr
	case <-time.After(150 * time.Millisecond):
		return client, nil
	}
}

func TestService_ProtocolVersionAndSetupValidation(t *testing.T) {
	srv, addr := startTestAcceptor(t)
	validReq := &dconpb.EstablishConduitRequest{
		Type:                dconpb.EstablishConduitRequest_CONDUIT_TYPE_FORWARD,
		DestinationEndpoint: "127.0.0.1:9",
	}

	tests := []struct {
		name       string
		conduitID  string
		meta       []byte
		hasMeta    bool
		wantErrSub string
	}{
		{
			name:       "missing_metadata",
			conduitID:  "no-meta",
			hasMeta:    false,
			wantErrSub: "missing metadata",
		},
		{
			name:       "empty_metadata",
			conduitID:  "empty-meta",
			meta:       []byte{},
			hasMeta:    true,
			wantErrSub: "missing metadata",
		},
		{
			name:       "corrupt_metadata",
			conduitID:  "corrupt-meta",
			meta:       []byte{0xff, 0xff, 0xff},
			hasMeta:    true,
			wantErrSub: "REJECTED_SETUP",
		},
		{
			name:      "missing_request_in_envelope",
			conduitID: "nil-req",
			meta: mustMarshalProto(t, &dconpb.ConduitSetupMetadata{
				ProtocolVersion: version.ProtocolVersion,
			}),
			hasMeta:    true,
			wantErrSub: "missing request",
		},
		{
			// Protocol 0 support: a dialer older than 0.2.0 is served.
			// Refused, it would retry at once, without end.
			name:      "legacy_raw_establish_request_is_served",
			conduitID: "legacy-raw",
			meta:      mustMarshalProto(t, validReq),
			hasMeta:   true,
		},
		{
			// Protocol 0 support.
			name:      "protocol_0_is_served",
			conduitID: "proto-0",
			meta: mustMarshalProto(t, &dconpb.ConduitSetupMetadata{
				Request:         validReq,
				ProtocolVersion: 0,
			}),
			hasMeta: true,
		},
		{
			name:      "newer_protocol_version",
			conduitID: "proto-newer",
			meta: mustMarshalProto(t, &dconpb.ConduitSetupMetadata{
				Request:         validReq,
				ProtocolVersion: version.ProtocolVersion + 1,
			}),
			hasMeta:    true,
			wantErrSub: "upgrade the acceptor",
		},
		{
			name:      "matching_protocol_version",
			conduitID: "proto-ok",
			meta: mustMarshalProto(t, &dconpb.ConduitSetupMetadata{
				Request:         validReq,
				ProtocolVersion: version.ProtocolVersion,
			}),
			hasMeta: true,
		},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			client, err := connectSetup(t.Context(), addr, tc.conduitID, tc.meta, tc.hasMeta)
			if client != nil {
				defer client.Close()
			}
			if tc.wantErrSub != "" {
				if err == nil || !strings.Contains(err.Error(), tc.wantErrSub) {
					t.Errorf("connectSetup(%q) err = %v, want error containing %q", tc.conduitID, err, tc.wantErrSub)
				}
				if _, mgrErr := srv.Manager.Conduit(tc.conduitID); mgrErr == nil {
					t.Errorf("Manager.Conduit(%q) = nil error, want rejected conduit absent", tc.conduitID)
				}
				return
			}
			if err != nil {
				t.Fatalf("connectSetup(%q) unexpected error: %v", tc.conduitID, err)
			}
			if _, mgrErr := srv.Manager.Conduit(tc.conduitID); mgrErr != nil {
				t.Errorf("Manager.Conduit(%q) = %v, want nil", tc.conduitID, mgrErr)
			}
		})
	}
}

func TestService_ProbeAcceptedWithoutProtocolVersion(t *testing.T) {
	_, addr := startTestAcceptor(t)
	client, err := rsocket.Connect().
		SetupPayload(payload.New([]byte("PROBE"), nil)).
		Transport(rsocket.TCPClient().SetAddr(addr).Build()).
		Start(t.Context())
	if err != nil {
		t.Fatalf("rsocket.Connect(PROBE) unexpected error: %v", err)
	}
	defer client.Close()

	resp, err := client.RequestResponse(payload.New([]byte("worker-1"), nil)).Block(t.Context())
	if err != nil {
		t.Fatalf("RequestResponse(PROBE) unexpected error: %v", err)
	}
	if got := resp.DataUTF8(); got != "ACK worker-1" {
		t.Errorf("RequestResponse(PROBE) = %q, want %q", got, "ACK worker-1")
	}
}

// TestCloseSignalHandler_OnlyStartsClosing checks what the acceptor does with
// the METADATA_PUSH frames of a reverse conduit. The CLOSE signal starts
// closing the conduit, and the handler returns without waiting for it: it
// runs on a goroutine of rsocket-go. Any other frame changes nothing.
func TestCloseSignalHandler_OnlyStartsClosing(t *testing.T) {
	con, err := conduit.NewManager().Add(t.Context(), "c1", nil, nil, nil, trace.SpanContext{})
	if err != nil {
		t.Fatalf("Manager.Add() unexpected error: %v", err)
	}
	// Closing does not finish until release is closed.
	release := make(chan struct{})
	con.AtEntryClose(func() { <-release })
	var releaseOnce sync.Once
	goOn := func() { releaseOnce.Do(func() { close(release) }) }
	t.Cleanup(goOn)
	handler := closeSignalHandler(t.Context(), "c1", con)

	handler(payload.New(nil, []byte("NOT-CLOSE")))
	if got := con.State(); got != conduit.StateActive {
		t.Fatalf("State() after a frame that is not the CLOSE signal = %v, want %v", got, conduit.StateActive)
	}

	returned := make(chan struct{})
	go func() {
		handler(payload.New(nil, []byte("CLOSE")))
		close(returned)
	}()
	select {
	case <-returned:
	case <-time.After(5 * time.Second):
		t.Fatalf("the handler of the CLOSE signal did not return within 5s while the conduit was closing")
	}
	if got := con.State(); got != conduit.StateClosing {
		t.Errorf("State() after the CLOSE signal = %v, want %v", got, conduit.StateClosing)
	}

	goOn()
	waitClosed(t, con.Done(), "the close of the conduit")
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

// reverseSetup returns the SETUP metadata of a reverse conduit.
func reverseSetup(t *testing.T, entryPort int) []byte {
	t.Helper()
	return reverseSetupWith(t, entryPort, dconpb.EstablishConduitRequest_PROTOCOL_TCP)
}

// reverseSetupWith returns the SETUP metadata of a reverse conduit with the
// given entry port and protocol.
func reverseSetupWith(t *testing.T, entryPort int, protocol dconpb.EstablishConduitRequest_Protocol) []byte {
	t.Helper()
	return mustMarshalProto(t, &dconpb.ConduitSetupMetadata{
		Request: &dconpb.EstablishConduitRequest{
			Type:                dconpb.EstablishConduitRequest_CONDUIT_TYPE_REVERSE,
			Protocol:            protocol,
			ServerName:          "svc",
			InstanceId:          "worker-1",
			EntryPort:           int32(entryPort),
			DestinationEndpoint: "127.0.0.1:1",
		},
		ProtocolVersion: version.ProtocolVersion,
	})
}

// connectReverse sets up a reverse conduit as a dialer does and returns when
// the acceptor has sent the locator, which it does once the endpoint is
// registered in the mesh.
func connectReverse(t *testing.T, addr, conduitID string) rsocket.Client {
	t.Helper()
	locator := make(chan struct{}, 1)
	client, err := rsocket.Connect().
		SetupPayload(payload.New([]byte(conduitID), reverseSetup(t, 0))).
		Acceptor(func(context.Context, rsocket.RSocket) rsocket.RSocket {
			return rsocket.NewAbstractSocket(rsocket.MetadataPush(func(payload.Payload) {
				select {
				case locator <- struct{}{}:
				default:
				}
			}))
		}).
		Transport(rsocket.TCPClient().SetAddr(addr).Build()).
		Start(t.Context())
	if err != nil {
		t.Fatalf("rsocket.Connect() unexpected error: %v", err)
	}
	t.Cleanup(func() { client.Close() })
	select {
	case <-locator:
	case <-time.After(5 * time.Second):
		t.Fatalf("acceptor sent no locator for the reverse conduit within 5s")
	}
	return client
}

// TestReverseConduit_EndpointLeavesMeshWithinClosing checks that the acceptor
// takes the endpoint of a reverse conduit out of the mesh as a step of
// closing the conduit: closing does not go on, and the conduit is not
// removed, until the mesh no longer lists the endpoint. The call must have a
// time limit, because closing must always finish.
//
// In version 0.2.0 and older a goroutine deregistered the endpoint some time
// after the conduit had closed, with no time limit.
func TestReverseConduit_EndpointLeavesMeshWithinClosing(t *testing.T) {
	const conduitID = "reverse-1"
	deregistering, release := make(chan context.Context, 1), make(chan struct{})
	var releaseOnce sync.Once
	goOn := func() { releaseOnce.Do(func() { close(release) }) }
	t.Cleanup(goOn)
	deregisterErr, entryPortErr := make(chan error, 1), make(chan error, 1)
	srv, addr := startTestAcceptorWith(t, func(s *Service) {
		s.deregisterEndpoint = func(ctx context.Context, req *dconpb.EstablishConduitRequest, dynamicPort int) error {
			// The entry port is still open: it closes after the endpoint
			// has left the mesh, also when a client has come to it in the
			// meantime. The conduit resets the connection of that client,
			// which the client may see as early as in its connect call; a
			// closed port refuses the connection instead.
			var err error
			for range 2 {
				var conn net.Conn
				conn, err = net.DialTimeout("tcp", fmt.Sprintf("127.0.0.1:%d", dynamicPort), time.Second)
				if errors.Is(err, syscall.ECONNRESET) {
					err = nil
				} else if err != nil {
					break
				} else {
					conn.SetReadDeadline(time.Now().Add(200 * time.Millisecond))
					conn.Read(make([]byte, 1))
					conn.Close()
				}
				time.Sleep(20 * time.Millisecond)
			}
			entryPortErr <- err
			deregistering <- ctx
			<-release
			err = s.DeregisterEndpoint(ctx, req, dynamicPort)
			deregisterErr <- err
			return err
		}
	})
	client := connectReverse(t, addr, conduitID)
	if got := srv.meshServer.Endpoints("svc", "worker-1"); len(got) != 1 {
		t.Fatalf("the mesh lists the endpoints %v for the reverse conduit, want one", got)
	}

	// The dialer goes away.
	client.Close()

	select {
	case ctx := <-deregistering:
		if err := ctx.Err(); err != nil {
			t.Errorf("the context of the deregistration has ended: %v", err)
		}
		if deadline, ok := ctx.Deadline(); !ok {
			t.Errorf("the deregistration has no time limit")
		} else if left := time.Until(deadline); left > deregisterTimeout {
			t.Errorf("the deregistration has %v left, want at most %v", left, deregisterTimeout)
		}
	case <-time.After(5 * time.Second):
		t.Fatalf("the endpoint was not deregistered within 5s after the dialer went away")
	}
	if err := <-entryPortErr; err != nil {
		t.Errorf("the entry port was closed before the endpoint left the mesh: %v", err)
	}
	// Closing waits for the deregistration.
	time.Sleep(200 * time.Millisecond)
	con, err := srv.Manager.Conduit(conduitID)
	if err != nil {
		t.Fatalf("the conduit was removed while its endpoint was still being deregistered: Manager.Conduit() got error %v", err)
	}
	if got := con.State(); got != conduit.StateClosing {
		t.Errorf("State() while the endpoint is being deregistered = %v, want %v", got, conduit.StateClosing)
	}

	goOn()
	waitClosed(t, con.Done(), "the close of the conduit after its endpoint was deregistered")
	if err := <-deregisterErr; err != nil {
		t.Errorf("DeregisterEndpoint() got unexpected error: %v", err)
	}
	if got := srv.meshServer.Endpoints("svc", "worker-1"); len(got) != 0 {
		t.Errorf("the mesh still lists the endpoints %v after the conduit closed, want none", got)
	}
}

// TestReverseConduit_EndpointLeavesMeshAtShutdown covers an acceptor that
// shuts down. Its context has ended when its conduits close, and the
// endpoints must leave the mesh all the same: the deregistration must not
// run under that context.
func TestReverseConduit_EndpointLeavesMeshAtShutdown(t *testing.T) {
	deregistered := make(chan error, 1)
	ctx, shutDown := context.WithCancel(t.Context())
	defer shutDown()
	srv, addr := startTestAcceptorCtx(ctx, t, func(s *Service) {
		s.deregisterEndpoint = func(ctx context.Context, req *dconpb.EstablishConduitRequest, dynamicPort int) error {
			if err := ctx.Err(); err != nil {
				deregistered <- fmt.Errorf("the context of the deregistration has ended: %v", err)
				return err
			}
			err := s.DeregisterEndpoint(ctx, req, dynamicPort)
			deregistered <- err
			return err
		}
	})
	connectReverse(t, addr, "reverse-1")

	shutDown()

	select {
	case err := <-deregistered:
		if err != nil {
			t.Errorf("deregistration at shutdown: %v", err)
		}
	case <-time.After(5 * time.Second):
		t.Fatalf("the endpoint was not deregistered within 5s after the acceptor shut down")
	}
	if got := srv.meshServer.Endpoints("svc", "worker-1"); len(got) != 0 {
		t.Errorf("the mesh still lists the endpoints %v after the acceptor shut down, want none", got)
	}
}

// TestReverseConduit_EntryPortInUseClosesConduit covers a reverse conduit
// whose entry port cannot be opened, as when the conduit that had the port
// before is not gone yet. The acceptor refuses the SETUP, and it must not
// keep the conduit.
//
// In version 0.2.0 and older the conduit stayed in the manager for good, one
// more for each attempt of the dialer.
func TestReverseConduit_EntryPortInUseClosesConduit(t *testing.T) {
	const conduitID = "reverse-port-in-use"
	srv, addr := startTestAcceptor(t)
	// The entry port is taken.
	taken, err := net.Listen("tcp", ":0")
	if err != nil {
		t.Fatalf("net.Listen unexpected error: %v", err)
	}
	defer taken.Close()
	entryPort := taken.Addr().(*net.TCPAddr).Port

	client, err := connectSetup(t.Context(), addr, conduitID, reverseSetup(t, entryPort), true)
	if err == nil {
		client.Close()
		t.Fatalf("the acceptor accepted a reverse conduit whose entry port %d is in use", entryPort)
	}

	gone := func() bool {
		_, err := srv.Manager.Conduit(conduitID)
		return err != nil
	}
	for deadline := time.Now().Add(5 * time.Second); !gone() && time.Now().Before(deadline); time.Sleep(10 * time.Millisecond) {
	}
	if !gone() {
		t.Errorf("the acceptor still has the conduit 5s after it refused the SETUP, want it closed")
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

// TestRefusedSetups_LeaveNoGoroutine covers dialers whose SETUP the acceptor
// refuses, as it does for a protocol it does not serve. A refusal must leave
// nothing behind on the acceptor.
//
// In version 0.2.0 and older each refused SETUP left one goroutine for good:
// the scavenger (purgePeriodically) of a pool that rsocket-go creates for a
// connection before it asks the acceptor.
func TestRefusedSetups_LeaveNoGoroutine(t *testing.T) {
	const (
		refusals = 20
		// scavenger is the goroutine of each pool of rsocket-go.
		scavenger = "purgePeriodically"
	)
	_, addr := startTestAcceptor(t)
	// A SETUP without metadata is refused. The client shares one scheduler,
	// so that only what the acceptor leaves behind is counted.
	refuse := func() {
		t.Helper()
		closed := make(chan struct{})
		var once sync.Once
		client, err := rsocket.Connect().
			Scheduler(scheduler.Elastic(), nil).
			OnClose(func(error) { once.Do(func() { close(closed) }) }).
			SetupPayload(payload.New([]byte("refused"), nil)).
			Transport(rsocket.TCPClient().SetAddr(addr).Build()).
			Start(t.Context())
		if err != nil {
			return
		}
		defer client.Close()
		waitClosed(t, closed, "the refusal of a SETUP without metadata")
	}
	settled := func(want int) int {
		got := goroutinesIn(scavenger)
		for deadline := time.Now().Add(2 * time.Second); got > want && time.Now().Before(deadline); time.Sleep(20 * time.Millisecond) {
			got = goroutinesIn(scavenger)
		}
		return got
	}

	// The first refusal starts what the process keeps for good.
	refuse()
	before := settled(0)

	for range refusals {
		refuse()
	}

	if after := settled(before); after > before {
		t.Errorf("%d refused SETUPs left %d more %s goroutines (%d before, %d after), want none", refusals, after-before, scavenger, before, after)
	}
}

// TestConduit_SilentConnectionClosedAfterDialerLifetime covers a network path
// that dies without closing the connection. The acceptor must close the
// conduit when it has received nothing for the keep-alive lifetime that the
// dialer named in SETUP, as the dialer does on its side.
//
// In version 0.2.0 and older the acceptor kept such a conduit for 90 s, twice
// as long as the dialer. For a reverse conduit the mesh sent clients to the
// dead conduit meanwhile.
func TestConduit_SilentConnectionClosedAfterDialerLifetime(t *testing.T) {
	const (
		conduitID = "silent-conduit"
		// The lifetime is long enough for a loaded machine to deliver the
		// keep-alive frames in time while the connection works.
		lifetime = 3 * time.Second
		// limit is far below the 90 s that rsocket-go applies by itself.
		limit = 20 * time.Second
	)
	srv, addr := startTestAcceptor(t)
	relay := nettest.StartRelay(t, addr)
	metadata := mustMarshalProto(t, &dconpb.ConduitSetupMetadata{
		Request: &dconpb.EstablishConduitRequest{
			Type:                dconpb.EstablishConduitRequest_CONDUIT_TYPE_FORWARD,
			DestinationEndpoint: "127.0.0.1:1",
		},
		ProtocolVersion: version.ProtocolVersion,
	})
	// The transport of a dialer: rsocket-go writes the SETUP frame and the
	// first keep-alive frames from two goroutines.
	dial, err := dcontransport.CreateClientTransport(dcontransport.ClientConfig{
		Target:        relay.Addr,
		TransportType: dcontransport.TCP,
	})
	if err != nil {
		t.Fatalf("CreateClientTransport() got unexpected error: %v", err)
	}
	client, err := rsocket.Connect().
		KeepAlive(lifetime/12, lifetime/3, 3).
		SetupPayload(payload.New([]byte(conduitID), metadata)).
		Transport(dial).
		Start(t.Context())
	if err != nil {
		t.Fatalf("rsocket.Connect() got unexpected error: %v", err)
	}
	defer client.Close()
	open := func() bool {
		_, err := srv.Manager.Conduit(conduitID)
		return err == nil
	}
	for deadline := time.Now().Add(5 * time.Second); !open() && time.Now().Before(deadline); time.Sleep(10 * time.Millisecond) {
	}
	if !open() {
		t.Fatalf("acceptor has no conduit %q 5s after SETUP", conduitID)
	}

	// While keep-alive frames arrive, the conduit stays.
	time.Sleep(lifetime + lifetime/2)
	if !open() {
		t.Fatalf("acceptor closed a conduit whose keep-alive frames arrive")
	}

	relay.Silence()
	start := time.Now()
	for open() && time.Since(start) < limit {
		time.Sleep(10 * time.Millisecond)
	}
	if open() {
		t.Errorf("acceptor still has the conduit %v after its connection went silent, want it closed after the dialer's lifetime of %v", limit, lifetime)
	} else {
		t.Logf("acceptor closed the conduit %v after the last frame (lifetime %v)", time.Since(start).Round(10*time.Millisecond), lifetime)
	}
}

// TestConduit_ConnectionStalledUnderLoadClosedAfterDialerLifetime covers a
// network path that dies while the acceptor is sending data: its writes
// block, and what waits to be sent piles up behind them. The acceptor must
// still close the conduit when the keep-alive lifetime of the dialer has run
// out.
func TestConduit_ConnectionStalledUnderLoadClosedAfterDialerLifetime(t *testing.T) {
	t.Run("the dialer is still heard", func(t *testing.T) { testConnectionStalledUnderLoad(t, false) })
	t.Run("nothing gets through", func(t *testing.T) { testConnectionStalledUnderLoad(t, true) })
}

func testConnectionStalledUnderLoad(t *testing.T, bothWays bool) {
	const (
		conduitID = "stalled-conduit"
		lifetime  = 3 * time.Second
		limit     = 30 * time.Second
	)
	// A destination that sends without end.
	destination, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("net.Listen unexpected error: %v", err)
	}
	defer destination.Close()
	go func() {
		for {
			conn, err := destination.Accept()
			if err != nil {
				return
			}
			go func() {
				defer conn.Close()
				chunk := make([]byte, 64<<10)
				for {
					if _, err := conn.Write(chunk); err != nil {
						return
					}
				}
			}()
		}
	}()
	srv, addr := startTestAcceptor(t)
	relay := nettest.StartRelay(t, addr)
	metadata := mustMarshalProto(t, &dconpb.ConduitSetupMetadata{
		Request: &dconpb.EstablishConduitRequest{
			Type:                dconpb.EstablishConduitRequest_CONDUIT_TYPE_FORWARD,
			DestinationEndpoint: destination.Addr().String(),
		},
		ProtocolVersion: version.ProtocolVersion,
	})
	dial, err := dcontransport.CreateClientTransport(dcontransport.ClientConfig{
		Target:        relay.Addr,
		TransportType: dcontransport.TCP,
	})
	if err != nil {
		t.Fatalf("CreateClientTransport() got unexpected error: %v", err)
	}
	client, err := rsocket.Connect().
		KeepAlive(lifetime/12, lifetime/3, 3).
		SetupPayload(payload.New([]byte(conduitID), metadata)).
		Transport(dial).
		Start(t.Context())
	if err != nil {
		t.Fatalf("rsocket.Connect() got unexpected error: %v", err)
	}
	defer client.Close()
	// One stream, on which the destination sends to the dialer.
	var received atomic.Int64
	out := flux.Create(func(_ context.Context, sink flux.Sink) {
		sink.Next(payload.New([]byte("go"), nil))
	})
	client.RequestChannel(out).Subscribe(t.Context(),
		rx.OnSubscribe(func(_ context.Context, su rx.Subscription) { su.Request(rx.RequestMax) }),
		rx.OnNext(func(p payload.Payload) error {
			received.Add(int64(len(p.Data())))
			return nil
		}),
	)
	for deadline := time.Now().Add(10 * time.Second); received.Load() < 1<<20 && time.Now().Before(deadline); time.Sleep(10 * time.Millisecond) {
	}
	if got := received.Load(); got < 1<<20 {
		t.Fatalf("the dialer received %d bytes from the destination in 10s, want data to flow before the stall", got)
	}
	open := func() bool {
		_, err := srv.Manager.Conduit(conduitID)
		return err == nil
	}
	if !open() {
		t.Fatalf("acceptor has no conduit %q while data flows", conduitID)
	}

	relay.Stall(bothWays)
	start := time.Now()
	// A stream that opens during the stall: its first frame arrives if the
	// dialer is still heard, and the acceptor then waits to answer it.
	time.Sleep(lifetime / 6)
	late := flux.Create(func(_ context.Context, sink flux.Sink) {
		sink.Next(payload.New([]byte("go"), nil))
	})
	client.RequestChannel(late).Subscribe(t.Context(),
		rx.OnSubscribe(func(_ context.Context, su rx.Subscription) { su.Request(rx.RequestMax) }))
	for open() && time.Since(start) < limit {
		time.Sleep(10 * time.Millisecond)
	}
	if open() {
		t.Errorf("acceptor still has the conduit %v after its connection stalled under load, want it closed after the dialer's lifetime of %v", limit, lifetime)
	} else {
		t.Logf("acceptor closed the conduit %v after the stall (lifetime %v)", time.Since(start).Round(10*time.Millisecond), lifetime)
	}
}

// TestConduit_SilentConnectionClosedWhileDestinationDoesNotRead covers a
// connection that dies while a stream is writing to a destination that does
// not read: the goroutine that handles the frames of the connection is
// blocked in that write. The acceptor must still close the conduit when the
// keep-alive lifetime of the dialer has run out. rsocket-go by itself closes
// a connection only when its frame handlers have returned.
func TestConduit_SilentConnectionClosedWhileDestinationDoesNotRead(t *testing.T) {
	const (
		conduitID = "blocked-destination"
		lifetime  = 3 * time.Second
		limit     = 30 * time.Second
		upload    = 32 << 20
	)
	// A destination that accepts connections and never reads.
	destination, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("net.Listen unexpected error: %v", err)
	}
	defer destination.Close()
	testDone := make(chan struct{})
	defer close(testDone)
	go func() {
		for {
			conn, err := destination.Accept()
			if err != nil {
				return
			}
			go func() {
				defer conn.Close()
				<-testDone
			}()
		}
	}()
	srv, addr := startTestAcceptor(t)
	relay := nettest.StartRelay(t, addr)
	metadata := mustMarshalProto(t, &dconpb.ConduitSetupMetadata{
		Request: &dconpb.EstablishConduitRequest{
			Type:                dconpb.EstablishConduitRequest_CONDUIT_TYPE_FORWARD,
			DestinationEndpoint: destination.Addr().String(),
		},
		ProtocolVersion: version.ProtocolVersion,
	})
	dial, err := dcontransport.CreateClientTransport(dcontransport.ClientConfig{
		Target:        relay.Addr,
		TransportType: dcontransport.TCP,
	})
	if err != nil {
		t.Fatalf("CreateClientTransport() got unexpected error: %v", err)
	}
	client, err := rsocket.Connect().
		KeepAlive(lifetime/12, lifetime/3, 3).
		SetupPayload(payload.New([]byte(conduitID), metadata)).
		Transport(dial).
		Start(t.Context())
	if err != nil {
		t.Fatalf("rsocket.Connect() got unexpected error: %v", err)
	}
	defer client.Close()
	// One stream that uploads more than the destination's buffers take.
	uploaded := make(chan struct{})
	out := flux.Create(func(_ context.Context, sink flux.Sink) {
		go func() {
			// The connection closes under the stream at the end of the test.
			defer func() { _ = recover() }()
			chunk := make([]byte, 64<<10)
			for sent := 0; sent < upload; sent += len(chunk) {
				sink.Next(payload.New(chunk, nil))
			}
			close(uploaded)
		}()
	})
	client.RequestChannel(out).Subscribe(t.Context(),
		rx.OnSubscribe(func(_ context.Context, su rx.Subscription) { su.Request(rx.RequestMax) }))
	select {
	case <-uploaded:
	case <-time.After(20 * time.Second):
		t.Fatalf("the upload did not leave the client within 20s")
	}
	// Let what is on its way arrive: the acceptor is then blocked in its
	// write to the destination.
	time.Sleep(time.Second)
	open := func() bool {
		_, err := srv.Manager.Conduit(conduitID)
		return err == nil
	}
	if !open() {
		t.Fatalf("acceptor has no conduit %q during the upload", conduitID)
	}

	relay.Silence()
	start := time.Now()
	for open() && time.Since(start) < limit {
		time.Sleep(10 * time.Millisecond)
	}
	if open() {
		t.Errorf("acceptor still has the conduit %v after its connection went silent during an upload to a destination that does not read, want it closed after the dialer's lifetime of %v", limit, lifetime)
	} else {
		t.Logf("acceptor closed the conduit %v after the last frame (lifetime %v)", time.Since(start).Round(10*time.Millisecond), lifetime)
	}
}

// freeTCPPort returns a TCP port that was free when the function returned.
func freeTCPPort(t *testing.T) int {
	t.Helper()
	lis, err := net.Listen("tcp", ":0")
	if err != nil {
		t.Fatalf("net.Listen unexpected error: %v", err)
	}
	defer lis.Close()
	return lis.Addr().(*net.TCPAddr).Port
}

// TestReverseConduit_FailedSetupRefusesSETUP covers a reverse conduit whose
// entry point cannot be made ready. The acceptor must refuse the SETUP with
// the reason, so that the dialer learns why, and must leave nothing behind:
// no conduit, no open entry port and no endpoint in the mesh.
//
// Before, the acceptor accepted the SETUP, registered the endpoint and only
// then closed the conduit, and the dialer saw its connection close without
// a reason.
func TestReverseConduit_FailedSetupRefusesSETUP(t *testing.T) {
	tests := []struct {
		name     string
		protocol dconpb.EstablishConduitRequest_Protocol
		// registerErr, if not nil, is what registering the endpoint in the
		// mesh fails with.
		registerErr error
		wantErrSub  string
	}{
		{
			name:       "unsupported_protocol",
			protocol:   dconpb.EstablishConduitRequest_PROTOCOL_UNSPECIFIED,
			wantErrSub: "unsupported protocol",
		},
		{
			name:        "registration_fails",
			protocol:    dconpb.EstablishConduitRequest_PROTOCOL_TCP,
			registerErr: errors.New("the mesh is down"),
			wantErrSub:  "the mesh is down",
		},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			const conduitID = "reverse-refused"
			var registered atomic.Int32
			srv, addr := startTestAcceptorWith(t, func(s *Service) {
				register := s.registerEndpoint
				s.registerEndpoint = func(ctx context.Context, req *dconpb.EstablishConduitRequest, dynamicPort int) (string, error) {
					registered.Add(1)
					if tc.registerErr != nil {
						return "", tc.registerErr
					}
					return register(ctx, req, dynamicPort)
				}
			})
			entryPort := freeTCPPort(t)

			client, err := connectSetup(t.Context(), addr, conduitID, reverseSetupWith(t, entryPort, tc.protocol), true)
			if client != nil {
				client.Close()
			}
			if err == nil || !strings.Contains(err.Error(), tc.wantErrSub) {
				t.Errorf("connectSetup() of a reverse conduit with protocol %v err = %v, want the SETUP refused with an error containing %q", tc.protocol, err, tc.wantErrSub)
			}

			gone := func() bool {
				_, err := srv.Manager.Conduit(conduitID)
				return err != nil
			}
			portFree := func() error {
				lis, err := net.Listen("tcp", fmt.Sprintf(":%d", entryPort))
				if err != nil {
					return err
				}
				return lis.Close()
			}
			for deadline := time.Now().Add(5 * time.Second); (!gone() || portFree() != nil) && time.Now().Before(deadline); time.Sleep(10 * time.Millisecond) {
			}
			if !gone() {
				t.Errorf("the acceptor still has the conduit 5s after its setup failed, want it closed")
			}
			if err := portFree(); err != nil {
				t.Errorf("entry port %d is still in use 5s after the setup of its conduit failed: %v", entryPort, err)
			}
			if got := srv.meshServer.Endpoints("svc", "worker-1"); len(got) != 0 {
				t.Errorf("the mesh lists the endpoints %v after the setup of the conduit failed, want none", got)
			}
			wantRegistered := int32(0)
			if tc.registerErr != nil {
				wantRegistered = 1
			}
			if got := registered.Load(); got != wantRegistered {
				t.Errorf("the endpoint was registered in the mesh %d times, want %d", got, wantRegistered)
			}
		})
	}
}

// TestReverseConduit_EndpointRegisteredBeforeConduitCanClose covers a dialer
// that goes away right after SETUP. The acceptor must register the endpoint
// while the conduit is ACTIVE and its entry port is open, and must have
// taken the endpoint out of the mesh when the conduit has closed.
//
// Before, a goroutine registered the endpoint some time after the SETUP was
// accepted. When the conduit had closed by then, the entry port was already
// free, so with a fixed entry port the next conduit could have taken the
// port and registered the same address, and the late deregistration of the
// old conduit would have removed the endpoint of the new one.
func TestReverseConduit_EndpointRegisteredBeforeConduitCanClose(t *testing.T) {
	const conduitID = "reverse-short-lived"
	atRegistration := make(chan error, 1)
	srv, addr := startTestAcceptorWith(t, func(s *Service) {
		register := s.registerEndpoint
		s.registerEndpoint = func(ctx context.Context, req *dconpb.EstablishConduitRequest, dynamicPort int) (string, error) {
			// Time for the dialer to go away, and for the acceptor to
			// close the conduit if it can.
			time.Sleep(300 * time.Millisecond)
			atRegistration <- func() error {
				con, err := s.Manager.Conduit(conduitID)
				if err != nil {
					return fmt.Errorf("the conduit is not in the manager: %v", err)
				}
				if got := con.State(); got != conduit.StateActive {
					return fmt.Errorf("the conduit is %v", got)
				}
				conn, err := net.DialTimeout("tcp", fmt.Sprintf("127.0.0.1:%d", dynamicPort), time.Second)
				if err != nil {
					return fmt.Errorf("the entry port is closed: %v", err)
				}
				return conn.Close()
			}()
			return register(ctx, req, dynamicPort)
		}
	})

	client, err := rsocket.Connect().
		SetupPayload(payload.New([]byte(conduitID), reverseSetup(t, 0))).
		Transport(rsocket.TCPClient().SetAddr(addr).Build()).
		Start(t.Context())
	if err != nil {
		t.Fatalf("rsocket.Connect() unexpected error: %v", err)
	}
	// The dialer goes away at once.
	client.Close()

	select {
	case err := <-atRegistration:
		if err != nil {
			t.Errorf("when the endpoint was registered in the mesh, %v; want the conduit ACTIVE with its entry port open", err)
		}
	case <-time.After(10 * time.Second):
		t.Fatalf("the endpoint of the reverse conduit was not registered within 10s")
	}

	settled := func() bool {
		_, err := srv.Manager.Conduit(conduitID)
		return err != nil && len(srv.meshServer.Endpoints("svc", "worker-1")) == 0
	}
	for deadline := time.Now().Add(5 * time.Second); !settled() && time.Now().Before(deadline); time.Sleep(10 * time.Millisecond) {
	}
	if _, err := srv.Manager.Conduit(conduitID); err == nil {
		t.Errorf("the acceptor still has the conduit 5s after its dialer went away, want it closed")
	}
	if got := srv.meshServer.Endpoints("svc", "worker-1"); len(got) != 0 {
		t.Errorf("the mesh lists the endpoints %v 5s after the dialer went away, want none", got)
	}
}
