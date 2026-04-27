// The dialer command is a gRPC server for the DualConduit Dialer service.
package main

import (
	"context"
	"flag"
	"fmt"
	"log"
	"net"

	"net/url"

	"github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/dialer"
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

	log.Printf("Dialer gRPC server listening on port %d", port)
	if err := s.Serve(lis); err != nil {
		log.Fatalf("Failed to serve: %v", err)
	}
}
