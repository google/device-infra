// Package main provides a simple helloworld gRPC server for testing DualConduit.
package main

import (
	"context"
	"flag"
	"fmt"
	"log/slog"
	"net"
	"os"

	"google.golang.org/grpc"
	"google.golang.org/grpc/reflection"

	"github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/cmd/logutil"
	"github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/tests/helloworld"
	helloworldsvcpb "github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/tests/helloworld/proto/helloworldsvcpb"
)

var (
	port = flag.Int("port", 50052, "The server port")
)

const serviceName = "dualconduit-helloworld"

func main() {
	logutil.Setup("/logs/helloworld.log", serviceName)
	flag.Parse()
	ctx := context.Background()
	lis, err := net.Listen("tcp", fmt.Sprintf(":%d", *port))
	if err != nil {
		slog.ErrorContext(ctx, "Failed to listen", "error", err)
		os.Exit(1)
	}
	s := grpc.NewServer()
	helloworldsvcpb.RegisterGreeterServer(s, &helloworld.Server{})
	reflection.Register(s)
	slog.InfoContext(ctx, "Helloworld server listening", "addr", lis.Addr())
	if err := s.Serve(lis); err != nil {
		slog.ErrorContext(ctx, "Failed to serve", "error", err)
		os.Exit(1)
	}
}
