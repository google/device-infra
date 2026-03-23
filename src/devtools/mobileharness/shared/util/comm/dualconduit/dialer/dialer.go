// Package main implements the dialer service for dual conduit.
package main

import (
	"context"
	"flag"
	"fmt"
	"log"
	"net"

	"google.golang.org/grpc/codes"
	"google.golang.org/grpc"
	"google.golang.org/grpc/reflection"
	"google.golang.org/grpc/status"

	pb "github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/proto/pb"
	pbgrpc "github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/proto/pbgrpc"
)

// DialerServer is the skeleton implementation of the DualConduitService.
type DialerServer struct {
	pbgrpc.UnimplementedDualConduitServiceServer
}

// EstablishConduit handles the gRPC request to establish a forward/reverse conduit.
func (s *DialerServer) EstablishConduit(ctx context.Context, req *pb.EstablishConduitRequest) (*pb.EstablishConduitResponse, error) {
	log.Printf("Received EstablishConduit request: %+v", req)
	return nil, status.Errorf(codes.Unimplemented, "method EstablishConduit not implemented")
}

// TeardownConduit handles the gRPC request to tear down a conduit.
func (s *DialerServer) TeardownConduit(ctx context.Context, req *pb.TeardownConduitRequest) (*pb.TeardownConduitResponse, error) {
	log.Printf("Received TeardownConduit request: %+v", req)
	return nil, status.Errorf(codes.Unimplemented, "method TeardownConduit not implemented")
}

var (
	port  = flag.Int("port", 50051, "The server port")
	debug = flag.Bool("debug", false, "turn on debug mode")
)

func main() {
	flag.Parse()
	lis, err := net.Listen("tcp", fmt.Sprintf(":%d", *port))
	if err != nil {
		log.Fatalf("failed to listen: %v", err)
	}

	s := grpc.NewServer()
	pbgrpc.RegisterDualConduitServiceServer(s, &DialerServer{})
	if *debug {
		reflection.Register(s)
	}

	log.Printf("Dialer gRPC server listening on %v", lis.Addr())
	if err := s.Serve(lis); err != nil {
		log.Fatalf("failed to serve: %v", err)
	}
}
