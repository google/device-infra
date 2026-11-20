package rbeclient

import (
	"context"
	"reflect"
	"testing"

	"github.com/bazelbuild/remote-apis-sdks/go/pkg/client"
	"google.golang.org/grpc"
)

// fakePerRPCCreds implements credentials.PerRPCCredentials.
type fakePerRPCCreds struct{}

func (fakePerRPCCreds) GetRequestMetadata(ctx context.Context, uri ...string) (map[string]string, error) {
	return map[string]string{"authorization": "Bearer fake"}, nil
}

func (fakePerRPCCreds) RequireTransportSecurity() bool { return true }

func TestDirectDialParams(t *testing.T) {
	externalCreds := &client.PerRPCCreds{Creds: fakePerRPCCreds{}}
	dialOpts := []grpc.DialOption{grpc.WithUserAgent("test")}

	tests := []struct {
		name string
		opts Opts
		want client.DialParams
	}{
		{
			name: "application_default_credentials",
			opts: Opts{ServiceAddress: "rbe:443", UseApplicationDefault: true},
			want: client.DialParams{
				Service:               "rbe:443",
				UseApplicationDefault: true,
				MaxConcurrentRequests: client.DefaultMaxConcurrentRequests,
			},
		},
		{
			name: "service_account_json",
			opts: Opts{ServiceAddress: "rbe:443", ServiceAccountJSON: "/path/to/key.json"},
			want: client.DialParams{
				Service:               "rbe:443",
				CredFile:              "/path/to/key.json",
				MaxConcurrentRequests: client.DefaultMaxConcurrentRequests,
			},
		},
		{
			name: "external_per_rpc_creds",
			opts: Opts{ServiceAddress: "rbe:443", ExternalPerRPCCreds: externalCreds},
			want: client.DialParams{
				Service:               "rbe:443",
				UseExternalAuthToken:  true,
				ExternalPerRPCCreds:   externalCreds,
				MaxConcurrentRequests: client.DefaultMaxConcurrentRequests,
			},
		},
		{
			name: "no_security",
			opts: Opts{ServiceAddress: "localhost:1234", NoSecurity: true},
			want: client.DialParams{
				Service:               "localhost:1234",
				NoSecurity:            true,
				MaxConcurrentRequests: client.DefaultMaxConcurrentRequests,
			},
		},
		{
			name: "dial_opts",
			opts: Opts{ServiceAddress: "rbe:443", UseApplicationDefault: true, DialOpts: dialOpts},
			want: client.DialParams{
				Service:               "rbe:443",
				UseApplicationDefault: true,
				DialOpts:              dialOpts,
				MaxConcurrentRequests: client.DefaultMaxConcurrentRequests,
			},
		},
		{
			name: "proxy_address_ignored",
			opts: Opts{ServiceAddress: "rbe:443", ProxyAddress: "localhost:8980", UseApplicationDefault: true},
			want: client.DialParams{
				Service:               "rbe:443",
				UseApplicationDefault: true,
				MaxConcurrentRequests: client.DefaultMaxConcurrentRequests,
			},
		},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			if got := directDialParams(tc.opts); !reflect.DeepEqual(got, tc.want) {
				t.Errorf("directDialParams(%+v) = %+v, want %+v", tc.opts, got, tc.want)
			}
		})
	}
}
