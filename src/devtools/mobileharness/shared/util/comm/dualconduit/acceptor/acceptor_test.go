package acceptor

import (
	"context"
	"net"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/mesh"
	dconpb "github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/proto/dconpb"
	"github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/version"
	rsockettransport "github.com/rsocket/rsocket-go/core/transport"
	"github.com/rsocket/rsocket-go/payload"
	"github.com/rsocket/rsocket-go"
	"google.golang.org/protobuf/proto"
)

func startTestAcceptor(t *testing.T) (*Service, string) {
	t.Helper()
	ctx, cancel := context.WithCancel(t.Context())
	lis, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("net.Listen unexpected error: %v", err)
	}
	t.Cleanup(func() {
		cancel()
		lis.Close()
	})
	meshSrv := mesh.New(ctx, 0, 0, "strict_dns")
	srv, err := New(func() (rsockettransport.ServerTransporter, error) {
		return func(context.Context) (rsockettransport.ServerTransport, error) {
			return rsockettransport.NewTCPServerTransport(func(context.Context) (net.Listener, error) {
				return lis, nil
			}), nil
		}, nil
	}, meshSrv, "127.0.0.1")
	if err != nil {
		t.Fatalf("New() unexpected error: %v", err)
	}
	go func() {
		_ = srv.Run(ctx)
	}()
	return srv, lis.Addr().String()
}

func mustMarshalProto(t *testing.T, msg proto.Message) []byte {
	t.Helper()
	b, err := proto.Marshal(msg)
	if err != nil {
		t.Fatalf("proto.Marshal(%T) unexpected error: %v", msg, err)
	}
	return b
}

func connectSetup(ctx context.Context, addr, id string, meta []byte, hasMeta bool) (rsocket.Client, error) {
	var setup payload.Payload
	if hasMeta {
		setup = payload.New([]byte(id), meta)
	} else {
		setup = payload.New([]byte(id), nil)
	}
	var (
		once    sync.Once
		closeCh = make(chan error, 1)
	)
	client, err := rsocket.Connect().
		OnClose(func(err error) { once.Do(func() { closeCh <- err }) }).
		SetupPayload(setup).
		Transport(rsocket.TCPClient().SetAddr(addr).Build()).
		Start(ctx)
	if err != nil {
		return nil, err
	}
	select {
	case closeErr := <-closeCh:
		_ = client.Close()
		return nil, closeErr
	case <-time.After(150 * time.Millisecond):
		return client, nil
	}
}

func TestService_ProtocolVersionAndSetupValidation(t *testing.T) {
	srv, addr := startTestAcceptor(t)
	validReq := &dconpb.EstablishConduitRequest{
		Type:                dconpb.EstablishConduitRequest_CONDUIT_TYPE_FORWARD,
		DestinationEndpoint: "127.0.0.1:9",
	}

	tests := []struct {
		name       string
		conduitID  string
		meta       []byte
		hasMeta    bool
		wantErrSub string
	}{
		{
			name:       "missing_metadata",
			conduitID:  "no-meta",
			hasMeta:    false,
			wantErrSub: "missing metadata",
		},
		{
			name:       "empty_metadata",
			conduitID:  "empty-meta",
			meta:       []byte{},
			hasMeta:    true,
			wantErrSub: "missing metadata",
		},
		{
			name:       "corrupt_metadata",
			conduitID:  "corrupt-meta",
			meta:       []byte{0xff, 0xff, 0xff},
			hasMeta:    true,
			wantErrSub: "REJECTED_SETUP",
		},
		{
			name:      "missing_request_in_envelope",
			conduitID: "nil-req",
			meta: mustMarshalProto(t, &dconpb.ConduitSetupMetadata{
				ProtocolVersion: version.ProtocolVersion,
			}),
			hasMeta:    true,
			wantErrSub: "missing request",
		},
		{
			// Protocol 0 support: a dialer older than 0.2.0 is served.
			// Refused, it would retry at once, without end.
			name:      "legacy_raw_establish_request_is_served",
			conduitID: "legacy-raw",
			meta:      mustMarshalProto(t, validReq),
			hasMeta:   true,
		},
		{
			// Protocol 0 support.
			name:      "protocol_0_is_served",
			conduitID: "proto-0",
			meta: mustMarshalProto(t, &dconpb.ConduitSetupMetadata{
				Request:         validReq,
				ProtocolVersion: 0,
			}),
			hasMeta: true,
		},
		{
			name:      "newer_protocol_version",
			conduitID: "proto-newer",
			meta: mustMarshalProto(t, &dconpb.ConduitSetupMetadata{
				Request:         validReq,
				ProtocolVersion: version.ProtocolVersion + 1,
			}),
			hasMeta:    true,
			wantErrSub: "upgrade the acceptor",
		},
		{
			name:      "matching_protocol_version",
			conduitID: "proto-ok",
			meta: mustMarshalProto(t, &dconpb.ConduitSetupMetadata{
				Request:         validReq,
				ProtocolVersion: version.ProtocolVersion,
			}),
			hasMeta: true,
		},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			client, err := connectSetup(t.Context(), addr, tc.conduitID, tc.meta, tc.hasMeta)
			if client != nil {
				defer client.Close()
			}
			if tc.wantErrSub != "" {
				if err == nil || !strings.Contains(err.Error(), tc.wantErrSub) {
					t.Errorf("connectSetup(%q) err = %v, want error containing %q", tc.conduitID, err, tc.wantErrSub)
				}
				if _, mgrErr := srv.Manager.Conduit(tc.conduitID); mgrErr == nil {
					t.Errorf("Manager.Conduit(%q) = nil error, want rejected conduit absent", tc.conduitID)
				}
				return
			}
			if err != nil {
				t.Fatalf("connectSetup(%q) unexpected error: %v", tc.conduitID, err)
			}
			if _, mgrErr := srv.Manager.Conduit(tc.conduitID); mgrErr != nil {
				t.Errorf("Manager.Conduit(%q) = %v, want nil", tc.conduitID, mgrErr)
			}
		})
	}
}

func TestService_ProbeAcceptedWithoutProtocolVersion(t *testing.T) {
	_, addr := startTestAcceptor(t)
	client, err := rsocket.Connect().
		SetupPayload(payload.New([]byte("PROBE"), nil)).
		Transport(rsocket.TCPClient().SetAddr(addr).Build()).
		Start(t.Context())
	if err != nil {
		t.Fatalf("rsocket.Connect(PROBE) unexpected error: %v", err)
	}
	defer client.Close()

	resp, err := client.RequestResponse(payload.New([]byte("worker-1"), nil)).Block(t.Context())
	if err != nil {
		t.Fatalf("RequestResponse(PROBE) unexpected error: %v", err)
	}
	if got := resp.DataUTF8(); got != "ACK worker-1" {
		t.Errorf("RequestResponse(PROBE) = %q, want %q", got, "ACK worker-1")
	}
}
