// Package main provides a simple helloworld gRPC server for testing DualConduit.
package main

import (
	"flag"
	"fmt"
	"log"
	"net"

	"google.golang.org/grpc"
	"google.golang.org/grpc/reflection"

	"github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/tests/helloworld"
	helloworldsvcpb "github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/tests/helloworld/proto/helloworldsvcpb"
)

var (
	port = flag.Int("port", 50052, "The server port")
)

func main() {
	flag.Parse()
	lis, err := net.Listen("tcp", fmt.Sprintf(":%d", *port))
	if err != nil {
		log.Fatalf("failed to listen: %v", err)
	}
	s := grpc.NewServer()
	helloworldsvcpb.RegisterGreeterServer(s, &helloworld.Server{})
	reflection.Register(s)
	log.Printf("helloworld server listening on %v", lis.Addr())
	if err := s.Serve(lis); err != nil {
		log.Fatalf("failed to serve: %v", err)
	}
}
