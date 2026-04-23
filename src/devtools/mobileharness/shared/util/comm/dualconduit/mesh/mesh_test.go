package mesh

import (
	"testing"

	clusterpb "github.com/envoyproxy/go-control-plane/envoy/config/cluster/v3"
	httppb "github.com/envoyproxy/go-control-plane/envoy/extensions/upstreams/http/v3"
	"github.com/envoyproxy/go-control-plane/pkg/resource/v3"
)

func TestGenerateSnapshot(t *testing.T) {
	ctx := t.Context()
	srv := NewServer(ctx)

	// Register a TCP service
	_, err := srv.RegisterService(ctx, "adb", "host-A", "127.0.0.1:5555", "tcp")
	if err != nil {
		t.Fatalf("Failed to register service: %v", err)
	}

	// Register a gRPC service
	_, err = srv.RegisterService(ctx, "foo", "host-B", "127.0.0.1:6666", "grpc")
	if err != nil {
		t.Fatalf("Failed to register service: %v", err)
	}

	snap, err := srv.GenerateSnapshot("v1")
	if err != nil {
		t.Fatalf("GenerateSnapshot failed: %v", err)
	}

	// Verify clusters
	clusters := snap.GetResources(resource.ClusterType)
	if len(clusters) != 2 {
		t.Errorf("GenerateSnapshot() clusters count = %d, want 2", len(clusters))
	}

	for _, r := range clusters {
		c, ok := r.(*clusterpb.Cluster)
		if !ok {
			t.Errorf("GenerateSnapshot() resource type = %T, want *clusterpb.Cluster", r)
			continue
		}
		if c.Name == "cluster.foo.host-B" {
			opts := c.TypedExtensionProtocolOptions
			if opts == nil {
				t.Errorf("GenerateSnapshot() cluster %q TypedExtensionProtocolOptions is nil, want non-nil", c.Name)
				continue
			}
			httpProtoOptsKey := "envoy.extensions.upstreams.http.v3.HttpProtocolOptions"
			rawOpts, ok := opts[httpProtoOptsKey]
			if !ok {
				t.Errorf("GenerateSnapshot() cluster %q TypedExtensionProtocolOptions missing key %q, want present", c.Name, httpProtoOptsKey)
				continue
			}
			var protoOpts httppb.HttpProtocolOptions
			err := rawOpts.UnmarshalTo(&protoOpts)
			if err != nil {
				t.Errorf("GenerateSnapshot() failed to unmarshal HttpProtocolOptions for cluster %q: %v", c.Name, err)
				continue
			}
			explicitConfig := protoOpts.GetExplicitHttpConfig()
			if explicitConfig == nil {
				t.Errorf("GenerateSnapshot() cluster %q ExplicitHttpConfig is nil, want non-nil", c.Name)
				continue
			}
			http2Opts := explicitConfig.GetHttp2ProtocolOptions()
			if http2Opts == nil {
				t.Errorf("GenerateSnapshot() cluster %q Http2ProtocolOptions is nil, want non-nil", c.Name)
			}
		}
	}

	// Verify listeners
	listeners := snap.GetResources(resource.ListenerType)
	if len(listeners) != 2 {
		t.Errorf("GenerateSnapshot() listeners count = %d, want 2", len(listeners))
	}
}
