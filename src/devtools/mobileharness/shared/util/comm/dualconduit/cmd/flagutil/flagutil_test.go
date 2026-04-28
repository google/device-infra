package flagutil

import (
	"flag"
	"testing"

	dconpb "github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/proto/dconpb"
)

func TestParseForwardConduitFlag(t *testing.T) {
	tests := []struct {
		name      string
		flagValue string
		wantReq   *dconpb.EstablishConduitRequest
		wantErr   bool
	}{
		{
			name:      "valid flag",
			flagValue: "1234:localhost:5678",
			wantReq: &dconpb.EstablishConduitRequest{
				Type:                dconpb.EstablishConduitRequest_CONDUIT_TYPE_FORWARD,
				EntryPort:           1234,
				DestinationEndpoint: "localhost:5678",
			},
			wantErr: false,
		},
		{
			name:      "valid flag with colon in destination",
			flagValue: "1234:192.168.1.1:8080",
			wantReq: &dconpb.EstablishConduitRequest{
				Type:                dconpb.EstablishConduitRequest_CONDUIT_TYPE_FORWARD,
				EntryPort:           1234,
				DestinationEndpoint: "192.168.1.1:8080",
			},
			wantErr: false,
		},
		{
			name:      "invalid format - missing colon",
			flagValue: "1234",
			wantReq:   nil,
			wantErr:   true,
		},
		{
			name:      "invalid format - too many parts (handled by SplitN(..., 2))",
			flagValue: "1234:host:port:extra",
			wantReq: &dconpb.EstablishConduitRequest{
				Type:                dconpb.EstablishConduitRequest_CONDUIT_TYPE_FORWARD,
				EntryPort:           1234,
				DestinationEndpoint: "host:port:extra",
			},
			wantErr: false,
		},
		{
			name:      "invalid port",
			flagValue: "abc:localhost:5678",
			wantReq:   nil,
			wantErr:   true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			gotReq, err := ParseForwardConduitFlag(tt.flagValue)
			if (err != nil) != tt.wantErr {
				t.Errorf("ParseForwardConduitFlag() error = %v, wantErr %v", err, tt.wantErr)
				return
			}
			if tt.wantErr {
				return
			}
			if gotReq.Type != tt.wantReq.Type ||
				gotReq.EntryPort != tt.wantReq.EntryPort ||
				gotReq.DestinationEndpoint != tt.wantReq.DestinationEndpoint {
				t.Errorf("ParseForwardConduitFlag() = %+v, want %+v", gotReq, tt.wantReq)
			}
		})
	}
}

func TestMultiStringFlag(t *testing.T) {
	var l MultiString
	fs := flag.NewFlagSet("test", flag.ContinueOnError)
	fs.Var(&l, "L", "usage")

	err := fs.Parse([]string{"-L", "1234:localhost:5678", "-L", "4321:localhost:8765"})
	if err != nil {
		t.Fatal(err)
	}

	if len(l) != 2 {
		t.Errorf("expected 2 values, got %d", len(l))
	}
	if l[0] != "1234:localhost:5678" {
		t.Errorf("expected l[0] = %q, got %q", "1234:localhost:5678", l[0])
	}
	if l[1] != "4321:localhost:8765" {
		t.Errorf("expected l[1] = %q, got %q", "4321:localhost:8765", l[1])
	}
}
