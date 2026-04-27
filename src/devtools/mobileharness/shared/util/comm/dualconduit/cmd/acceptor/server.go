// The acceptor server is a command line tool to start a dual conduit acceptor.
package main

import (
	"context"
	"flag"
	"fmt"
	"log"
	"os"
	"os/signal"
	"syscall"

	"github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/acceptor"
	"github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/mesh"
	dcontransport "github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/transport"
	rsockettransport "github.com/rsocket/rsocket-go/core/transport"
	"google.golang.org/grpc"
	"net"
)

func main() {
	port := flag.Int("port", 7878, "The RSocket server port")
	transportType := flag.String("transport", "tcp", "The transport protocol to use (tcp, websocket)")
	xdsPort := flag.Int("xds_port", 18000, "The xDS gRPC server port")
	reverseForwardAddress := flag.String("reverse_forward_address", "127.0.0.1", "The address to register in xDS for reverse conduits")
	httpPort := flag.Int("http_port", 80, "The port for HTTP data plane in Envoy")
	tcpPort := flag.Int("tcp_port", 443, "The port for TCP data plane in Envoy")
	flag.Parse()

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	// Initialize mesh server (xDS server)
	meshServer := mesh.NewServer(ctx, *httpPort, *tcpPort)

	newTransporter := func() (rsockettransport.ServerTransporter, error) {
		serverCfg := dcontransport.ServerConfig{
			Port:          *port,
			TransportType: dcontransport.Transport(*transportType),
		}
		return dcontransport.CreateServerTransport(serverCfg)
	}

	// Start xDS gRPC server
	go func() {
		grpcServer := grpc.NewServer()
		meshServer.RegisterGRPC(grpcServer)

		lis, err := net.Listen("tcp", fmt.Sprintf(":%d", *xdsPort))
		if err != nil {
			log.Fatalf("Failed to listen on xDS port %d: %v", *xdsPort, err)
		}
		log.Printf("xDS server listening on %d\n", *xdsPort)
		if err := grpcServer.Serve(lis); err != nil {
			log.Fatalf("xDS server error: %v", err)
		}
	}()

	// Start Acceptor service
	go func() {
		srv, err := acceptor.New(newTransporter, meshServer, *reverseForwardAddress)
		if err != nil {
			log.Fatalf("Failed to create acceptor: %v", err)
		}
		if err := srv.Run(ctx); err != nil {
			log.Fatalf("Acceptor error: %v", err)
		}
	}()

	// Wait for termination signal
	sigs := make(chan os.Signal, 1)
	signal.Notify(sigs, syscall.SIGINT, syscall.SIGTERM)
	<-sigs

	cancel()
	log.Println("Acceptor shutting down.")
}
