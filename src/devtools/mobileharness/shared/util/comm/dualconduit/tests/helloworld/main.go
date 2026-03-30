// Package main provides a simple helloworld gRPC server for testing DualConduit.
package main

import (
	"context"
	"flag"
	"fmt"
	"log"
	"net"

	"google.golang.org/grpc"
	"google.golang.org/grpc/reflection"

	helloworldpb "github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/tests/helloworld/proto/helloworldpb"
	helloworldsvcpb "github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/tests/helloworld/proto/helloworldsvcpb"
)

var (
	port = flag.Int("port", 50052, "The server port")
)

type server struct {
	helloworldsvcpb.UnimplementedGreeterServer
}

func (s *server) SayHello(ctx context.Context, in *helloworldpb.HelloRequest) (*helloworldpb.HelloReply, error) {
	log.Printf("Received SayHello: %v", in.GetName())
	return &helloworldpb.HelloReply{Message: "Hello " + in.GetName()}, nil
}

func main() {
	flag.Parse()
	lis, err := net.Listen("tcp", fmt.Sprintf(":%d", *port))
	if err != nil {
		log.Fatalf("failed to listen: %v", err)
	}
	s := grpc.NewServer()
	helloworldsvcpb.RegisterGreeterServer(s, &server{})
	reflection.Register(s)
	log.Printf("helloworld server listening on %v", lis.Addr())
	if err := s.Serve(lis); err != nil {
		log.Fatalf("failed to serve: %v", err)
	}
}
