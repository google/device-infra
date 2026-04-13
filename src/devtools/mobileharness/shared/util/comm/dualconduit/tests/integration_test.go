package tests

import (
	"context"
	"fmt"
	"log"
	"net"
	"testing"
	"time"

	"github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/acceptor"
	"github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/dialer"
	"github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/tests/helloworld"
	dcontransport "github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/transport"
	rsockettransport "github.com/rsocket/rsocket-go/core/transport"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc"

	dconpb "github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/proto/dconpb"
	dconsvcpb "github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/proto/dconsvcpb"
	helloworldpb "github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/tests/helloworld/proto/helloworldpb"
	helloworldsvcpb "github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/tests/helloworld/proto/helloworldsvcpb"
)

func TestDualConduitForward(t *testing.T) {
	ctx, cancel, helloPort, _, dialerPort := setupServices(t)
	defer cancel()

	// 4. Call Dialer's EstablishConduit via gRPC
	dialerAddr := fmt.Sprintf("127.0.0.1:%d", dialerPort)
	dialerConn, err := grpc.NewClient(dialerAddr, grpc.WithTransportCredentials(insecure.NewCredentials()))
	if err != nil {
		t.Fatalf("grpc.NewClient(%v) got unexpected error: %v", dialerAddr, err)
	}
	defer dialerConn.Close()

	dconClient := dconsvcpb.NewDualConduitServiceClient(dialerConn)

	// We need a port for the Dialer to listen on for the Forward conduit.
	entryLis, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("net.Listen(\"tcp\", \"127.0.0.1:0\") got unexpected error: %v", err)
	}
	entryPort := entryLis.Addr().(*net.TCPAddr).Port
	entryLis.Close() // Close it so dialer can bind to it

	establishReq := &dconpb.EstablishConduitRequest{
		Type:                dconpb.EstablishConduitRequest_CONDUIT_TYPE_FORWARD,
		EntryPort:           int32(entryPort),
		DestinationEndpoint: fmt.Sprintf("127.0.0.1:%d", helloPort),
	}

	resp, err := dconClient.EstablishConduit(ctx, establishReq)
	if err != nil {
		t.Fatalf("dconClient.EstablishConduit(%v) got unexpected error: %v", establishReq, err)
	}
	t.Logf("Conduit established: %v", resp.ConduitId)

	// Wait a bit for the conduit to be fully established and listening
	time.Sleep(1 * time.Second)

	// 5. Test traffic through the conduit
	// Connect to Dialer's entry port
	entryAddr := fmt.Sprintf("127.0.0.1:%d", entryPort)
	helloClientConn, err := grpc.NewClient(entryAddr, grpc.WithTransportCredentials(insecure.NewCredentials()))
	if err != nil {
		t.Fatalf("grpc.NewClient(%v) got unexpected error: %v", entryAddr, err)
	}

	helloClient := helloworldsvcpb.NewGreeterClient(helloClientConn)

	helloResp, err := helloClient.SayHello(ctx, &helloworldpb.HelloRequest{Name: "World"})
	if err != nil {
		t.Fatalf("helloClient.SayHello(\"World\") got unexpected error: %v", err)
	}

	if helloResp.Message != "Hello World" {
		t.Errorf("helloClient.SayHello(\"World\") got message %q, want %q", helloResp.Message, "Hello World")
	}

	// Explicitly close the client connection and allow the reactive streams a
	// moment to naturally complete before the defer cancel() abruptly drops the
	// Conduit, preventing a "frame released" panic in rsocket-go.
	helloClientConn.Close()
	time.Sleep(1 * time.Second)

	teardownReq := &dconpb.TeardownConduitRequest{
		ConduitId: resp.ConduitId,
	}
	if _, err := dconClient.TeardownConduit(ctx, teardownReq); err != nil {
		t.Fatalf("dconClient.TeardownConduit(%v) got unexpected error: %v", teardownReq, err)
	}
	t.Logf("Conduit torn down: %v", resp.ConduitId)
}

func TestDualConduitReverse(t *testing.T) {
	ctx, cancel, helloPort, _, dialerPort := setupServices(t)
	defer cancel()

	// 4. Call Dialer's EstablishConduit via gRPC with REVERSE type
	dialerAddr := fmt.Sprintf("127.0.0.1:%d", dialerPort)
	dialerConn, err := grpc.NewClient(dialerAddr, grpc.WithTransportCredentials(insecure.NewCredentials()))
	if err != nil {
		t.Fatalf("grpc.NewClient(%v) got unexpected error: %v", dialerAddr, err)
	}
	defer dialerConn.Close()

	dconClient := dconsvcpb.NewDualConduitServiceClient(dialerConn)

	establishReq := &dconpb.EstablishConduitRequest{
		Type:                dconpb.EstablishConduitRequest_CONDUIT_TYPE_REVERSE,
		DestinationEndpoint: fmt.Sprintf("127.0.0.1:%d", helloPort),
	}

	resp, err := dconClient.EstablishConduit(ctx, establishReq)
	if err != nil {
		t.Fatalf("dconClient.EstablishConduit(%v) got unexpected error: %v", establishReq, err)
	}
	t.Logf("Conduit established: %v", resp.ConduitId)

	if resp.ServiceLocator == nil {
		t.Fatalf("EstablishConduit() got ServiceLocator nil, want non-nil")
	}

	locator := resp.ServiceLocator.GetHostnamePort()
	if locator == "" {
		t.Fatalf("EstablishConduit() got HostnamePort %q, want non-empty", locator)
	}

	// Wait a bit for the conduit to be fully established and listening on Acceptor side
	time.Sleep(200 * time.Millisecond)

	// 5. Test traffic through the conduit (connecting to Acceptor's dynamic port)
	// Override host to 127.0.0.1 for testing, as RegisterService returns "acceptor.local"
	_, port, err := net.SplitHostPort(locator)
	if err != nil {
		t.Fatalf("net.SplitHostPort(%q) got unexpected error: %v", locator, err)
	}
	locator = net.JoinHostPort("127.0.0.1", port)

	helloClientConn, err := grpc.NewClient(locator, grpc.WithTransportCredentials(insecure.NewCredentials()))
	if err != nil {
		t.Fatalf("grpc.NewClient(%v) got unexpected error: %v", locator, err)
	}

	helloClient := helloworldsvcpb.NewGreeterClient(helloClientConn)

	helloResp, err := helloClient.SayHello(ctx, &helloworldpb.HelloRequest{Name: "World-Reverse"})
	if err != nil {
		t.Fatalf("helloClient.SayHello(\"World-Reverse\") got unexpected error: %v", err)
	}

	if helloResp.Message != "Hello World-Reverse" {
		t.Errorf("helloClient.SayHello() got message %q, want %q", helloResp.Message, "Hello World-Reverse")
	}

	// Explicitly close the client connection and allow the reactive streams a
	// moment to naturally complete before the defer cancel() abruptly drops the
	// Conduit, preventing a "frame released" panic in rsocket-go.
	helloClientConn.Close()
	time.Sleep(200 * time.Millisecond)

	teardownReq := &dconpb.TeardownConduitRequest{
		ConduitId: resp.ConduitId,
	}
	if _, err := dconClient.TeardownConduit(ctx, teardownReq); err != nil {
		t.Fatalf("dconClient.TeardownConduit(%v) got unexpected error: %v", teardownReq, err)
	}
	t.Logf("Conduit torn down: %v", resp.ConduitId)
}

func TestDualConduitSimple(t *testing.T) {
	ctx, cancel, helloPort, _, dialerPort := setupServices(t)
	defer cancel()

	dialerAddr := fmt.Sprintf("127.0.0.1:%d", dialerPort)
	dialerConn, err := grpc.NewClient(dialerAddr, grpc.WithTransportCredentials(insecure.NewCredentials()))
	if err != nil {
		t.Fatalf("grpc.NewClient(%v) got unexpected error: %v", dialerAddr, err)
	}
	defer dialerConn.Close()

	dconClient := dconsvcpb.NewDualConduitServiceClient(dialerConn)

	establishReq := &dconpb.EstablishConduitRequest{
		Type:                dconpb.EstablishConduitRequest_CONDUIT_TYPE_REVERSE,
		DestinationEndpoint: fmt.Sprintf("127.0.0.1:%d", helloPort),
	}

	resp, err := dconClient.EstablishConduit(ctx, establishReq)
	if err != nil {
		t.Fatalf("dconClient.EstablishConduit(%v) got unexpected error: %v", establishReq, err)
	}
	t.Logf("Conduit established: %v", resp.ConduitId)

	teardownReq := &dconpb.TeardownConduitRequest{
		ConduitId: resp.ConduitId,
	}

	_, err = dconClient.TeardownConduit(ctx, teardownReq)
	if err != nil {
		t.Fatalf("dconClient.TeardownConduit(%v) got unexpected error: %v", teardownReq, err)
	}
	t.Logf("Conduit torn down: %v", resp.ConduitId)
}

func setupServices(t *testing.T) (ctx context.Context, teardown func(), helloPort int, acceptorPort int, dialerPort int) {
	t.Helper()

	_, cancelHello := context.WithCancel(t.Context())
	acceptorCtx, cancelAcceptor := context.WithCancel(t.Context())
	dialerCtx, cancelDialer := context.WithCancel(t.Context())

	var helloSrv *grpc.Server
	var dialerSrv *grpc.Server

	teardown = func() {
		// Teardown Dialer
		if dialerSrv != nil {
			dialerSrv.Stop()
		}
		cancelDialer()
		time.Sleep(200 * time.Millisecond)

		// Teardown Acceptor
		cancelAcceptor()
		time.Sleep(200 * time.Millisecond)

		// Teardown Helloworld
		if helloSrv != nil {
			helloSrv.Stop()
		}
		cancelHello()
	}

	// 1. Start Helloworld server on a random port
	helloLis, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("net.Listen(\"tcp\", \"127.0.0.1:0\") got unexpected error: %v", err)
	}
	helloPort = helloLis.Addr().(*net.TCPAddr).Port
	helloSrv = grpc.NewServer()
	helloworldsvcpb.RegisterGreeterServer(helloSrv, &helloworld.Server{})
	go func() {
		if err := helloSrv.Serve(helloLis); err != nil {
			log.Printf("Helloworld server stopped: %v", err)
		}
	}()

	// 2. Start Acceptor on a random port.
	lis, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("net.Listen(\"tcp\", \"127.0.0.1:0\") got unexpected error: %v", err)
	}
	acceptorPort = lis.Addr().(*net.TCPAddr).Port

	srv, err := acceptor.New(func() (rsockettransport.ServerTransporter, error) {
		return func(ctx context.Context) (rsockettransport.ServerTransport, error) {
			return rsockettransport.NewTCPServerTransport(func(ctx context.Context) (net.Listener, error) {
				return lis, nil
			}), nil
		}, nil
	}, "127.0.0.1")
	if err != nil {
		t.Fatalf("acceptor.New() got unexpected error: %v", err)
	}

	go func() {
		if err := srv.Run(acceptorCtx); err != nil {
			log.Printf("Acceptor Run failed: %v", err)
		}
	}()

	// Give it a short moment to start the accept loop
	time.Sleep(100 * time.Millisecond)

	// 3. Start Dialer on a random port
	dialerLis, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("net.Listen(\"tcp\", \"127.0.0.1:0\") got unexpected error: %v", err)
	}
	dialerPort = dialerLis.Addr().(*net.TCPAddr).Port
	dialerSvc := dialer.New(dialerCtx, "", func() (rsockettransport.ClientTransporter, error) {
		return dcontransport.CreateClientTransport(dcontransport.ClientConfig{
			Target:        fmt.Sprintf("127.0.0.1:%d", acceptorPort),
			TransportType: dcontransport.TCP,
		})
	})
	dialerSrv = grpc.NewServer()
	dconsvcpb.RegisterDualConduitServiceServer(dialerSrv, dialerSvc)
	go func() {
		if err := dialerSrv.Serve(dialerLis); err != nil {
			log.Printf("Dialer server stopped: %v", err)
		}
	}()

	return t.Context(), teardown, helloPort, acceptorPort, dialerPort
}
