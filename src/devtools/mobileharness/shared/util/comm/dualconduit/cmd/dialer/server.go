// The dialer command is a gRPC server for the DualConduit Dialer service.
package main

import (
	"context"
	"flag"
	"fmt"
	"log"
	"net"
	"net/url"
	"os"
	"os/signal"

	"syscall"

	"github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/dialer"
	"github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/cmd/flagutil"
	dconsvcpb "github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/proto/dconsvcpb"
	dcontransport "github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/transport"
	rsockettransport "github.com/rsocket/rsocket-go/core/transport"
	"google.golang.org/grpc"
	"google.golang.org/grpc/reflection"
)

type config struct {
	AcceptorTarget string
	UseSAToken     bool
	SAKeyFile      string
	Debug          bool
	Hostname       string
}

func main() {
	var cfg config
	var port int

	flag.StringVar(&cfg.AcceptorTarget, "acceptor_target", "localhost:7878", "Acceptor target address (host:port or ws://host:port)")
	flag.BoolVar(&cfg.UseSAToken, "use_sa_token", false, "Use Self-Signed JWT from Service Account key")
	flag.StringVar(&cfg.SAKeyFile, "sa_key_file", "", "Path to Service Account JSON key file")
	flag.BoolVar(&cfg.Debug, "debug", false, "Enable debug logging and reflection")
	flag.IntVar(&port, "port", 50051, "Port to listen on for Dialer gRPC service")
	flag.StringVar(&cfg.Hostname, "hostname", "", "Hostname override for pre-flight check")
	var forwardConduits flagutil.MultiString
	flag.Var(&forwardConduits, "L", "Establish forward conduit (format: entry_port:destination_endpoint), can be specified multiple times")

	flag.Parse()

	if cfg.UseSAToken && cfg.SAKeyFile == "" {
		log.Fatal("sa_key_file must be set when use_sa_token is true")
	}

	audience := cfg.AcceptorTarget
	parsedURL, err := url.Parse(cfg.AcceptorTarget)
	if err == nil && parsedURL.Host != "" {
		audience = parsedURL.Host
	}

	newTransporter := func() (rsockettransport.ClientTransporter, error) {
		clientCfg := dcontransport.ClientConfig{
			Target:     cfg.AcceptorTarget,
			UseSAToken: cfg.UseSAToken,
			SAKeyFile:  cfg.SAKeyFile,
			Aud:        audience,
		}
		return dcontransport.CreateClientTransport(clientCfg)
	}

	// 1. Perform pre-flight check
	dialerSvc := dialer.New(context.Background(), cfg.Hostname, newTransporter)
	if err := dialerSvc.CheckConnection(context.Background()); err != nil {
		log.Fatalf("Pre-flight check failed: %v", err)
	}

	for _, fc := range forwardConduits {
		req, err := flagutil.ParseForwardConduitFlag(fc)
		if err != nil {
			log.Fatalf("Failed to parse -L flag %q: %v", fc, err)
		}
		req.ClientHostname = cfg.Hostname
		if req.ClientHostname == "" {
			hostname, err := os.Hostname()
			if err != nil {
				log.Printf("Failed to get hostname: %v", err)
				// Proceed with an empty ClientHostname if os.Hostname fails.
			} else {
				req.ClientHostname = hostname
			}
		}

		resp, err := dialerSvc.EstablishConduit(context.Background(), req)
		if err != nil {
			log.Fatalf("Failed to establish forward conduit for %q: %v", fc, err)
		}
		log.Printf("Established forward conduit: %s, locator: %+v", resp.ConduitId, resp.ServiceLocator)
	}

	// 2. Start Dialer gRPC server
	lis, err := net.Listen("tcp", fmt.Sprintf(":%d", port))
	if err != nil {
		log.Fatalf("Failed to listen on port %d: %v", port, err)
	}

	s := grpc.NewServer()
	dconsvcpb.RegisterDualConduitServiceServer(s, dialerSvc)

	if cfg.Debug {
		reflection.Register(s)
	}

	// Handle graceful shutdown
	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, os.Interrupt, syscall.SIGTERM)
	go func() {
		sig := <-sigChan
		log.Printf("Received signal %v, shutting down...", sig)
		dialerSvc.Manager.Shutdown()
		s.GracefulStop()
		log.Println("Graceful shutdown complete.")
		os.Exit(0)
	}()

	log.Printf("Dialer gRPC server listening on port %d", port)
	if err := s.Serve(lis); err != nil {
		log.Fatalf("Failed to serve: %v", err)
	}
}
