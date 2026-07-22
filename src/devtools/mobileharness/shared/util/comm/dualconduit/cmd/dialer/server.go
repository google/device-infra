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
	"time"

	"github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/cmd/flagutil"
	"github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/cmd/logutil"
	"github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/cmd/otelutil"
	"github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/dialer"
	dconpb "github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/proto/dconpb"
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

const serviceName = "dualconduit-dialer"

func main() {
	logutil.Setup("/logs/dialer.log", serviceName)
	var cfg config
	var port int
	var retryStrategy string
	var retryInitialInterval time.Duration
	var retryMaxInterval time.Duration
	var retryMultiplier float64
	var retryMaxAttempts int
	var keepAliveTickPeriod time.Duration

	flag.StringVar(&cfg.AcceptorTarget, "acceptor_target", "localhost:7878", "Acceptor target address (host:port or ws://host:port)")
	flag.BoolVar(&cfg.UseSAToken, "use_sa_token", false, "Use Self-Signed JWT from Service Account key")
	flag.StringVar(&cfg.SAKeyFile, "sa_key_file", "", "Path to Service Account JSON key file")
	flag.BoolVar(&cfg.Debug, "debug", false, "Enable debug logging and reflection")
	flag.IntVar(&port, "port", 50051, "Port to listen on for Dialer gRPC service")
	flag.StringVar(&cfg.Hostname, "hostname", "", "Hostname override for pre-flight check")
	flag.StringVar(&cfg.ForwardAddress, "forward_address", "", "Forward address (e.g., 127.0.0.1 or 0.0.0.0) to listen on for forward conduits")
	var forwardConduits flagutil.MultiString
	flag.Var(&forwardConduits, "L", "Establish forward conduit (format: entry_port:destination_endpoint), can be specified multiple times")

	flag.StringVar(&retryStrategy, "retry_strategy", "exponential", "Retry strategy (exponential, constant, none)")
	flag.DurationVar(&retryInitialInterval, "retry_initial_interval", 1*time.Second, "Initial interval for retry backoff")
	flag.DurationVar(&retryMaxInterval, "retry_max_interval", 10*time.Second, "Maximum interval for exponential retry backoff")
	flag.Float64Var(&retryMultiplier, "retry_multiplier", 2.0, "Multiplier for exponential retry backoff")
	flag.IntVar(&retryMaxAttempts, "retry_max_attempts", 0, "Maximum retry attempts (0 for infinite, 1 for no retry)")
	flag.DurationVar(&keepAliveTickPeriod, "keep_alive_tick_period", 20*time.Second, "Keep-alive tick period for the dialer connection")

	flag.Parse()

	if cfg.UseSAToken && cfg.SAKeyFile == "" {
		slog.ErrorContext(context.Background(), "sa_key_file must be set when use_sa_token is true")
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

	policy := dialer.RetryPolicy{
		MaxAttempts: retryMaxAttempts,
	}
	switch retryStrategy {
	case "exponential":
		policy.Strategy = &dialer.ExponentialBackoff{
			InitialInterval: retryInitialInterval,
			MaxInterval:     retryMaxInterval,
			Multiplier:      retryMultiplier,
		}
	case "constant":
		policy.Strategy = &dialer.ConstantBackoff{
			Interval: retryInitialInterval,
		}
	case "none":
		policy.MaxAttempts = 1
		policy.Strategy = &dialer.ConstantBackoff{}
	default:
		slog.ErrorContext(context.Background(), "Invalid retry_strategy", "strategy", retryStrategy)
		os.Exit(1)
	}

	// 1. Perform pre-flight check
	dialerCtx, dialerCancel := context.WithCancel(context.Background())
	defer dialerCancel()

	// Initialize OpenTelemetry telemetry (traces and metrics)
	shutdownTelemetry, err := otelutil.InitTelemetry(dialerCtx, serviceName)
	if err != nil {
		slog.ErrorContext(dialerCtx, "Failed to initialize OpenTelemetry", "error", err)
	} else {
		defer shutdownTelemetry(dialerCtx)
	}
	dialerSvc := dialer.New(dialerCtx, cfg.Hostname, cfg.ForwardAddress, newTransporter, policy, keepAliveTickPeriod)
	if err := dialerSvc.CheckConnection(context.Background()); err != nil {
		slog.ErrorContext(dialerCtx, "Pre-flight check failed", "error", err)
		os.Exit(1)
	}

	for _, fc := range forwardConduits {
		req, err := flagutil.ParseForwardConduitFlag(fc)
		if err != nil {
			slog.ErrorContext(dialerCtx, "Failed to parse -L flag", "flag", fc, "error", err)
			os.Exit(1)
		}
		req.InstanceId = cfg.Hostname
		if req.InstanceId == "" {
			hostname, err := os.Hostname()
			if err != nil {
				slog.WarnContext(dialerCtx, "Failed to get hostname", "error", err)
				// Proceed with an empty InstanceId if os.Hostname fails.
			} else {
				req.InstanceId = hostname
			}
		}

		sessionReq := &dconpb.EstablishSessionRequest{
			EstablishConduitRequest: req,
			AutoReconnect:           true,
		}
		resp, err := dialerSvc.EstablishSession(context.Background(), sessionReq)
		if err != nil {
			slog.ErrorContext(dialerCtx, "Failed to establish forward session", "flag", fc, "error", err)
			os.Exit(1)
		}
		if len(resp.EstablishConduitResponses) > 0 {
			cResp := resp.EstablishConduitResponses[0]
			slog.InfoContext(dialerCtx, "Established forward session", "session_id", resp.SessionId, "conduit_id", cResp.ConduitId, "locator", cResp.ServiceLocator)
		} else {
			slog.InfoContext(dialerCtx, "Established forward session", "session_id", resp.SessionId)
		}
	}

	// 2. Start Dialer gRPC server
	lis, err := net.Listen("tcp", fmt.Sprintf(":%d", port))
	if err != nil {
		slog.ErrorContext(dialerCtx, "Failed to listen on port", "port", port, "error", err)
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
		slog.InfoContext(dialerCtx, "Received signal, shutting down", "signal", sig)
		dialerCancel()
		dialerSvc.Manager.Shutdown()
		s.GracefulStop()
		slog.InfoContext(dialerCtx, "Graceful shutdown complete")
		os.Exit(0)
	}()

	slog.InfoContext(dialerCtx, "Dialer gRPC server listening", "port", port)
	if err := s.Serve(lis); err != nil {
		slog.ErrorContext(dialerCtx, "Failed to serve", "error", err)
		os.Exit(1)
	}
}
