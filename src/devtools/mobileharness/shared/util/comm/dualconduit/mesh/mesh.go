// Package mesh implements the xDS control plane for DualConduit.
// It manages Envoy configurations and snapshots to route traffic to dynamic conduits.
package mesh

import (
	"context"
	"fmt"
	"log"
	"net"
	"strconv"
	"sync"
	"time"

	// Google3 packages
	clusterservicepb "github.com/envoyproxy/go-control-plane/envoy/service/cluster/v3"
	discoverygrpcpb "github.com/envoyproxy/go-control-plane/envoy/service/discovery/v3"
	endpointservicepb "github.com/envoyproxy/go-control-plane/envoy/service/endpoint/v3"
	listenerservicepb "github.com/envoyproxy/go-control-plane/envoy/service/listener/v3"
	routeservicepb "github.com/envoyproxy/go-control-plane/envoy/service/route/v3"
	runtimeservicepb "github.com/envoyproxy/go-control-plane/envoy/service/runtime/v3"
	secretservicepb "github.com/envoyproxy/go-control-plane/envoy/service/secret/v3"
	"google.golang.org/grpc"

	"github.com/envoyproxy/go-control-plane/pkg/cache/types"
	"github.com/envoyproxy/go-control-plane/pkg/cache/v3"
	"github.com/envoyproxy/go-control-plane/pkg/resource/v3"
	"github.com/envoyproxy/go-control-plane/pkg/server/v3"

	// Protocol Buffer imports
	anypb "google.golang.org/protobuf/types/known/anypb"
	durationpb "google.golang.org/protobuf/types/known/durationpb"
	clusterpb "github.com/envoyproxy/go-control-plane/envoy/config/cluster/v3"
	corepb "github.com/envoyproxy/go-control-plane/envoy/config/core/v3"
	endpointpb "github.com/envoyproxy/go-control-plane/envoy/config/endpoint/v3"
	listenerpb "github.com/envoyproxy/go-control-plane/envoy/config/listener/v3"
	routepb "github.com/envoyproxy/go-control-plane/envoy/config/route/v3"
	routerpb "github.com/envoyproxy/go-control-plane/envoy/extensions/filters/http/router/v3"
	hcmpb "github.com/envoyproxy/go-control-plane/envoy/extensions/filters/network/http_connection_manager/v3"
	tcppb "github.com/envoyproxy/go-control-plane/envoy/extensions/filters/network/tcp_proxy/v3"
	httppb "github.com/envoyproxy/go-control-plane/envoy/extensions/upstreams/http/v3"
)

// ServiceInfo holds information about a registered service.
type ServiceInfo struct {
	ServiceName     string
	Hostname        string
	PhysicalAddress string
	Protocol        string // "tcp" or "grpc"
}

// Server wraps the go-control-plane server and manages snapshots.
type Server struct {
	cache    cache.SnapshotCache
	server   server.Server
	mu       sync.Mutex
	services map[string]ServiceInfo // key: logicalName
	version  int
}

// NewServer creates a new Server.
func NewServer(ctx context.Context) *Server {
	// Create a cache
	cache := cache.NewSnapshotCache(false, cache.IDHash{}, nil)

	// Create a server
	srv := server.NewServer(ctx, cache, nil)

	return &Server{
		cache:    cache,
		server:   srv,
		services: make(map[string]ServiceInfo),
	}
}

// RegisterService adds or updates a service and pushes a new snapshot.
func (s *Server) RegisterService(ctx context.Context, serviceName, hostname, physicalAddress, protocol string) (string, error) {
	s.mu.Lock()
	defer s.mu.Unlock()

	logicalName := fmt.Sprintf("%s.%s.dcon", serviceName, hostname)
	s.services[logicalName] = ServiceInfo{
		ServiceName:     serviceName,
		Hostname:        hostname,
		PhysicalAddress: physicalAddress,
		Protocol:        protocol,
	}

	s.version++
	err := s.updateSnapshot(ctx)
	if err != nil {
		return "", err
	}
	return logicalName, nil
}

// DeregisterService removes a service and pushes a new snapshot.
func (s *Server) DeregisterService(ctx context.Context, serviceName, hostname string) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	logicalName := fmt.Sprintf("%s.%s.dcon", serviceName, hostname)
	delete(s.services, logicalName)

	s.version++
	return s.updateSnapshot(ctx)
}

func (s *Server) updateSnapshot(ctx context.Context) error {
	version := fmt.Sprintf("v%d", s.version)
	snap, err := s.GenerateSnapshot(version)
	if err != nil {
		log.Printf("Failed to generate snapshot: %v", err)
		return err
	}

	// We assume node ID is "node" for simplicity. In real case it should match Envoy node ID.
	err = s.cache.SetSnapshot(ctx, "node", snap)
	if err != nil {
		log.Printf("Failed to set snapshot: %v", err)
		return err
	}
	return nil
}

// GenerateSnapshot creates a snapshot from active services.
func (s *Server) GenerateSnapshot(version string) (*cache.Snapshot, error) {
	var clusters []types.Resource
	var listeners []types.Resource

	// Base listener for port 443 (TCP SNI matching)
	// This listener will be updated with new FilterChains as services are added.
	var filterChains []*listenerpb.FilterChain

	for logicalName, info := range s.services {
		clusterName := fmt.Sprintf("cluster.%s.%s", info.ServiceName, info.Hostname)

		// Create Cluster
		c := &clusterpb.Cluster{
			Name:                 clusterName,
			ConnectTimeout:       durationpb.New(5 * time.Second),
			ClusterDiscoveryType: &clusterpb.Cluster_Type{Type: clusterpb.Cluster_LOGICAL_DNS},
			LbPolicy:             clusterpb.Cluster_ROUND_ROBIN,
			LoadAssignment:       makeEndpoint(clusterName, info.PhysicalAddress),
			DnsLookupFamily:      clusterpb.Cluster_V4_ONLY,
		}

		if info.Protocol == "grpc" {
			options := &httppb.HttpProtocolOptions{
				UpstreamProtocolOptions: &httppb.HttpProtocolOptions_ExplicitHttpConfig_{
					ExplicitHttpConfig: &httppb.HttpProtocolOptions_ExplicitHttpConfig{
						ProtocolConfig: &httppb.HttpProtocolOptions_ExplicitHttpConfig_Http2ProtocolOptions{
							Http2ProtocolOptions: &corepb.Http2ProtocolOptions{},
						},
					},
				},
			}
			anyOptions, err := anypb.New(options)
			if err != nil {
				return nil, fmt.Errorf("failed to marshal gRPC HttpProtocolOptions: %w", err)
			}
			c.TypedExtensionProtocolOptions = map[string]*anypb.Any{
				"envoy.extensions.upstreams.http.v3.HttpProtocolOptions": anyOptions,
			}
		} else if info.Protocol == "http" {
			options := &httppb.HttpProtocolOptions{
				UpstreamProtocolOptions: &httppb.HttpProtocolOptions_AutoConfig{
					AutoConfig: &httppb.HttpProtocolOptions_AutoHttpConfig{},
				},
			}
			anyOptions, err := anypb.New(options)
			if err != nil {
				return nil, fmt.Errorf("failed to marshal HTTP HttpProtocolOptions: %w", err)
			}
			c.TypedExtensionProtocolOptions = map[string]*anypb.Any{
				"envoy.extensions.upstreams.http.v3.HttpProtocolOptions": anyOptions,
			}
		}

		clusters = append(clusters, c)

		// Endpoints are inlined in Cluster in this case because of LOGICAL_DNS
		// But we can also provide them separately if we use EDS.
		// Let's stick to LOGICAL_DNS for now as in experimental code.

		if info.Protocol == "tcp" {
			tcpConfig, err := makeTCPProxyConfig(clusterName)
			if err != nil {
				return nil, err
			}
			// For TCP, add a FilterChain to the base listener
			filterChains = append(filterChains, &listenerpb.FilterChain{
				FilterChainMatch: &listenerpb.FilterChainMatch{ServerNames: []string{logicalName}},
				Filters: []*listenerpb.Filter{{
					Name: "envoy.filters.network.tcp_proxy",
					ConfigType: &listenerpb.Filter_TypedConfig{
						TypedConfig: tcpConfig,
					},
				}},
			})
		} else if info.Protocol == "grpc" {
			// For gRPC, we use an ApiListener
			apiConfig, err := makeAPIListenerConfig(clusterName, []string{"*"})
			if err != nil {
				return nil, err
			}
			listeners = append(listeners, &listenerpb.Listener{
				Name:        logicalName,
				ApiListener: &listenerpb.ApiListener{ApiListener: apiConfig},
			})
		} else if info.Protocol == "http" {
			// For HTTP, we also use an ApiListener but with specific domain
			apiConfig, err := makeAPIListenerConfig(clusterName, []string{logicalName})
			if err != nil {
				return nil, err
			}
			listeners = append(listeners, &listenerpb.Listener{
				Name:        logicalName,
				ApiListener: &listenerpb.ApiListener{ApiListener: apiConfig},
			})
		}
	}

	// Add the base listener if we have TCP services
	if len(filterChains) > 0 {
		listeners = append(listeners, &listenerpb.Listener{
			Name: "base_listener_443",
			Address: &corepb.Address{
				Address: &corepb.Address_SocketAddress{
					SocketAddress: &corepb.SocketAddress{
						Protocol: corepb.SocketAddress_TCP,
						Address:  "0.0.0.0",
						PortSpecifier: &corepb.SocketAddress_PortValue{
							PortValue: 443,
						},
					},
				},
			},
			FilterChains: filterChains,
		})
	}

	snap, err := cache.NewSnapshot(version, map[resource.Type][]types.Resource{
		resource.ClusterType:  clusters,
		resource.ListenerType: listeners,
	})
	if err != nil {
		return nil, fmt.Errorf("failed to create snapshot: %w", err)
	}
	return snap, nil
}

func makeEndpoint(clusterName string, address string) *endpointpb.ClusterLoadAssignment {
	host, portStr, err := net.SplitHostPort(address)
	if err != nil {
		log.Printf("Failed to split host port from %s: %v", address, err)
		host = address // Fallback to address as host if split fails
	}
	var port uint32
	if p, err := strconv.Atoi(portStr); err == nil {
		port = uint32(p)
	}

	return &endpointpb.ClusterLoadAssignment{
		ClusterName: clusterName,
		Endpoints: []*endpointpb.LocalityLbEndpoints{{
			LbEndpoints: []*endpointpb.LbEndpoint{{
				HostIdentifier: &endpointpb.LbEndpoint_Endpoint{
					Endpoint: &endpointpb.Endpoint{
						Address: &corepb.Address{
							Address: &corepb.Address_SocketAddress{
								SocketAddress: &corepb.SocketAddress{
									Protocol: corepb.SocketAddress_TCP,
									Address:  host,
									PortSpecifier: &corepb.SocketAddress_PortValue{
										PortValue: port,
									},
								},
							},
						},
					},
				},
			}},
		}},
	}
}

func makeTCPProxyConfig(clusterName string) (*anypb.Any, error) {
	config := &tcppb.TcpProxy{
		StatPrefix: "tcp",
		ClusterSpecifier: &tcppb.TcpProxy_Cluster{
			Cluster: clusterName,
		},
	}
	pbst, err := anypb.New(config)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal TcpProxy config: %w", err)
	}
	return pbst, nil
}

func makeAPIListenerConfig(clusterName string, domains []string) (*anypb.Any, error) {
	routerConfig, err := anypb.New(&routerpb.Router{})
	if err != nil {
		return nil, fmt.Errorf("failed to marshal Router config: %w", err)
	}

	manager := &hcmpb.HttpConnectionManager{
		CodecType:  hcmpb.HttpConnectionManager_AUTO,
		StatPrefix: "ingress_http",
		RouteSpecifier: &hcmpb.HttpConnectionManager_RouteConfig{
			RouteConfig: &routepb.RouteConfiguration{
				Name: "local_route",
				VirtualHosts: []*routepb.VirtualHost{{
					Name:    "local_service",
					Domains: domains,
					Routes: []*routepb.Route{{
						Match: &routepb.RouteMatch{
							PathSpecifier: &routepb.RouteMatch_Prefix{
								Prefix: "/",
							},
						},
						Action: &routepb.Route_Route{
							Route: &routepb.RouteAction{
								ClusterSpecifier: &routepb.RouteAction_Cluster{
									Cluster: clusterName,
								},
							},
						},
					}},
				}},
			},
		},
		HttpFilters: []*hcmpb.HttpFilter{
			{
				Name: "envoy.filters.http.router",
				ConfigType: &hcmpb.HttpFilter_TypedConfig{
					TypedConfig: routerConfig,
				},
			},
		},
	}
	pbst, err := anypb.New(manager)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal HttpConnectionManager config: %w", err)
	}
	return pbst, nil
}

// GetServer returns the underlying xDS server.
func (s *Server) GetServer() server.Server {
	return s.server
}

// RegisterGRPC registers the xDS server with the given gRPC server.
func (s *Server) RegisterGRPC(grpcServer *grpc.Server) {
	discoverygrpcpb.RegisterAggregatedDiscoveryServiceServer(grpcServer, s.server)
	endpointservicepb.RegisterEndpointDiscoveryServiceServer(grpcServer, s.server)
	clusterservicepb.RegisterClusterDiscoveryServiceServer(grpcServer, s.server)
	routeservicepb.RegisterRouteDiscoveryServiceServer(grpcServer, s.server)
	listenerservicepb.RegisterListenerDiscoveryServiceServer(grpcServer, s.server)
	secretservicepb.RegisterSecretDiscoveryServiceServer(grpcServer, s.server)
	runtimeservicepb.RegisterRuntimeDiscoveryServiceServer(grpcServer, s.server)
}
