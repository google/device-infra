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

	pb "github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/tests/helloworld/proto"
	pbgrpc "github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/tests/helloworld/proto/grpc"
)

var (
	port = flag.Int("port", 50052, "The server port")
)

type server struct {
	pbgrpc.UnimplementedGreeterServer
}

func (s *server) SayHello(ctx context.Context, in *pb.HelloRequest) (*pb.HelloReply, error) {
	log.Printf("Received SayHello: %v", in.GetName())
	return &pb.HelloReply{Message: "Hello " + in.GetName()}, nil
}

func main() {
	flag.Parse()
	lis, err := net.Listen("tcp", fmt.Sprintf(":%d", *port))
	if err != nil {
		log.Fatalf("failed to listen: %v", err)
	}
	s := grpc.NewServer()
	pbgrpc.RegisterGreeterServer(s, &server{})
	reflection.Register(s)
	log.Printf("helloworld server listening on %v", lis.Addr())
	if err := s.Serve(lis); err != nil {
		log.Fatalf("failed to serve: %v", err)
	}
}
