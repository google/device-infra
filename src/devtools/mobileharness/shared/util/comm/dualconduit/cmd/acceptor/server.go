// The acceptor server is a command line tool to start a dual conduit acceptor.
package main

import (
	"context"
	"flag"
	"log"
	"os"
	"os/signal"
	"syscall"

	"github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/acceptor"
	dcontransport "github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/transport"
	rsockettransport "github.com/rsocket/rsocket-go/core/transport"
)

func main() {
	port := flag.Int("port", 7878, "The RSocket server port")
	transportType := flag.String("transport", "tcp", "The transport protocol to use (tcp, websocket)")
	flag.Parse()

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	newTransporter := func() (rsockettransport.ServerTransporter, error) {
		serverCfg := dcontransport.ServerConfig{
			Port:          *port,
			TransportType: dcontransport.Transport(*transportType),
		}
		return dcontransport.CreateServerTransport(serverCfg)
	}

	go func() {
		srv, err := acceptor.New(newTransporter, "")
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
