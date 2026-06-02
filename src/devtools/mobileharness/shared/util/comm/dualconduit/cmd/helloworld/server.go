// Package main provides a simple helloworld gRPC server for testing DualConduit.
package main

import (
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

func main() {
	logutil.Setup("/logs/helloworld.log")
	flag.Parse()
	lis, err := net.Listen("tcp", fmt.Sprintf(":%d", *port))
	if err != nil {
		slog.Error("Failed to listen", "error", err)
		os.Exit(1)
	}
	s := grpc.NewServer()
	helloworldsvcpb.RegisterGreeterServer(s, &helloworld.Server{})
	reflection.Register(s)
	slog.Info("Helloworld server listening", "addr", lis.Addr())
	if err := s.Serve(lis); err != nil {
		slog.Error("Failed to serve", "error", err)
		os.Exit(1)
	}
}
