package transport

import (
	"context"
	"fmt"
	"net"
	"testing"
	"time"

	rsockettransport "github.com/rsocket/rsocket-go/core/transport"
	"github.com/rsocket/rsocket-go/payload"
	"github.com/rsocket/rsocket-go"
)

// freePort returns a TCP port that was free when the function returned.
func freePort(t *testing.T) int {
	t.Helper()
	lis, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("net.Listen got unexpected error: %v", err)
	}
	defer lis.Close()
	return lis.Addr().(*net.TCPAddr).Port
}

// startWebSocketServer serves RSocket over WebSocket on the port and accepts every setup.
func startWebSocketServer(ctx context.Context, t *testing.T, port int) {
	t.Helper()
	serverTransport, err := CreateServerTransport(ServerConfig{Port: port, TransportType: WebSocket})
	if err != nil {
		t.Fatalf("CreateServerTransport got unexpected error: %v", err)
	}
	go func() {
		_ = rsocket.Receive().
			Acceptor(func(ctx context.Context, setup payload.SetupPayload, rs rsocket.CloseableRSocket) (rsocket.RSocket, error) {
				return rsocket.NewAbstractSocket(), nil
			}).
			Transport(serverTransport).
			Serve(ctx)
	}()
	addr := fmt.Sprintf("127.0.0.1:%d", port)
	for deadline := time.Now().Add(10 * time.Second); time.Now().Before(deadline); time.Sleep(20 * time.Millisecond) {
		if conn, err := net.Dial("tcp", addr); err == nil {
			conn.Close()
			return
		}
	}
	t.Fatalf("server on %v did not start", addr)
}

// TestClientTransport_SlowDialLongerThanKeepAlive covers a connection to the
// acceptor that takes longer to establish than the keep-alive interval. The
// keep-alive timer starts before the dial, so a keep-alive frame is already due
// when the connection opens and is written at the same time as the SETUP frame.
// The client must survive that. A failure here is a panic in the test binary.
func TestClientTransport_SlowDialLongerThanKeepAlive(t *testing.T) {
	const (
		keepAlive = 20 * time.Millisecond
		dialDelay = 3 * keepAlive
		budget    = 20 * time.Second
	)
	ctx, cancel := context.WithCancel(t.Context())
	defer cancel()

	port := freePort(t)
	startWebSocketServer(ctx, t, port)

	dial, err := CreateClientTransport(ClientConfig{Target: fmt.Sprintf("ws://127.0.0.1:%d", port)})
	if err != nil {
		t.Fatalf("CreateClientTransport got unexpected error: %v", err)
	}
	slowDial := func(ctx context.Context) (*rsockettransport.Transport, error) {
		time.Sleep(dialDelay)
		return dial(ctx)
	}

	// The overlap of the two writes depends on scheduling, so connect repeatedly.
	// A connection may be refused when the keep-alive frame is sent first; that
	// is retried in production and is not what this test is about.
	attempts, connected := 0, 0
	for start := time.Now(); time.Since(start) < budget; attempts++ {
		client, err := rsocket.Connect().
			KeepAlive(keepAlive, 15*time.Second, 3).
			SetupPayload(payload.New([]byte("test"), nil)).
			Transport(slowDial).
			Start(ctx)
		if err != nil {
			continue
		}
		connected++
		client.Close()
	}
	if connected == 0 {
		t.Errorf("none of %d connection attempts succeeded", attempts)
	}
	t.Logf("%d of %d connection attempts succeeded, without a crash", connected, attempts)
}
