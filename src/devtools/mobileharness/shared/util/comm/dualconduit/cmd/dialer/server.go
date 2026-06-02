// The dialer command is a gRPC server for the DualConduit Dialer service.
package main

import (
	"context"
	"flag"
	"fmt"
	"log/slog"
	"net"
	"net/url"
	"os"
	"os/signal"
	"syscall"

	"github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/cmd/flagutil"
	"github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/cmd/logutil"
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
	ForwardAddress string
}

func main() {
	logutil.Setup("/logs/dialer.log")
	var cfg config
	var port int

	flag.StringVar(&cfg.AcceptorTarget, "acceptor_target", "localhost:7878", "Acceptor target address (host:port or ws://host:port)")
	flag.BoolVar(&cfg.UseSAToken, "use_sa_token", false, "Use Self-Signed JWT from Service Account key")
	flag.StringVar(&cfg.SAKeyFile, "sa_key_file", "", "Path to Service Account JSON key file")
	flag.BoolVar(&cfg.Debug, "debug", false, "Enable debug logging and reflection")
	flag.IntVar(&port, "port", 50051, "Port to listen on for Dialer gRPC service")
	flag.StringVar(&cfg.Hostname, "hostname", "", "Hostname override for pre-flight check")
	flag.StringVar(&cfg.ForwardAddress, "forward_address", "", "Forward address (e.g., 127.0.0.1 or 0.0.0.0) to listen on for forward conduits")
	var forwardConduits flagutil.MultiString
	flag.Var(&forwardConduits, "L", "Establish forward conduit (format: entry_port:destination_endpoint), can be specified multiple times")

	flag.Parse()

	if cfg.UseSAToken && cfg.SAKeyFile == "" {
		slog.Error("sa_key_file must be set when use_sa_token is true")
		os.Exit(1)
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
	dialerSvc := dialer.New(context.Background(), cfg.Hostname, cfg.ForwardAddress, newTransporter)
	if err := dialerSvc.CheckConnection(context.Background()); err != nil {
		slog.Error("Pre-flight check failed", "error", err)
		os.Exit(1)
	}

	for _, fc := range forwardConduits {
		req, err := flagutil.ParseForwardConduitFlag(fc)
		if err != nil {
			slog.Error("Failed to parse -L flag", "flag", fc, "error", err)
			os.Exit(1)
		}
		req.InstanceId = cfg.Hostname
		if req.InstanceId == "" {
			hostname, err := os.Hostname()
			if err != nil {
				slog.Warn("Failed to get hostname", "error", err)
				// Proceed with an empty InstanceId if os.Hostname fails.
			} else {
				req.InstanceId = hostname
			}
		}

		resp, err := dialerSvc.EstablishConduit(context.Background(), req)
		if err != nil {
			slog.Error("Failed to establish forward conduit", "flag", fc, "error", err)
			os.Exit(1)
		}
		slog.Info("Established forward conduit", "id", resp.ConduitId, "locator", resp.ServiceLocator)
	}

	// 2. Start Dialer gRPC server
	lis, err := net.Listen("tcp", fmt.Sprintf(":%d", port))
	if err != nil {
		slog.Error("Failed to listen on port", "port", port, "error", err)
		os.Exit(1)
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
		slog.Info("Received signal, shutting down", "signal", sig)
		dialerSvc.Manager.Shutdown()
		s.GracefulStop()
		slog.Info("Graceful shutdown complete")
		os.Exit(0)
	}()

	slog.Info("Dialer gRPC server listening", "port", port)
	if err := s.Serve(lis); err != nil {
		slog.Error("Failed to serve", "error", err)
		os.Exit(1)
	}
}
