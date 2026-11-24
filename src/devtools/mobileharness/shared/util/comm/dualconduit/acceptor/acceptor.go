// Package acceptor implements the acceptor service for dual conduit.
package acceptor

import (
	"context"
	"fmt"
	"log/slog"
	"net"
	"strings"
	"time"

	"github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/conduit"
	"github.com/jjeffcaii/reactor-go/scheduler"
	rsockettransport "github.com/rsocket/rsocket-go/core/transport"
	"github.com/rsocket/rsocket-go/payload"
	"github.com/rsocket/rsocket-go"
	"github.com/rsocket/rsocket-go/rx/flux"
	"github.com/rsocket/rsocket-go/rx/mono"
	"google.golang.org/protobuf/proto"

	"github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/mesh"
	dconpb "github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/proto/dconpb"
	dcontransport "github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/transport"
	"github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/version"
	"go.opentelemetry.io/otel/trace"
	"go.opentelemetry.io/otel/attribute"
	otelcodes "go.opentelemetry.io/otel/codes"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/propagation"
)

// deregisterTimeout limits the call that takes the endpoint of a reverse
// conduit out of the mesh when the conduit closes.
const deregisterTimeout = 5 * time.Second

// Service represents the acceptor service for Dual Conduit.
type Service struct {
	Manager               *conduit.Manager
	serverTransporter     rsockettransport.ServerTransporter
	meshServer            *mesh.Server
	reverseForwardAddress string
	// registerEndpoint and deregisterEndpoint are RegisterEndpoint and
	// DeregisterEndpoint, or their stand-ins in a test.
	registerEndpoint   func(ctx context.Context, req *dconpb.EstablishConduitRequest, dynamicPort int) (string, error)
	deregisterEndpoint func(ctx context.Context, req *dconpb.EstablishConduitRequest, dynamicPort int) error
}

// New creates a new Service with a initialized conduit.Manager and ServerTransporter.
func New(newTransporter func() (rsockettransport.ServerTransporter, error), meshServer *mesh.Server, reverseForwardAddress string) (*Service, error) {
	transporter, err := newTransporter()
	if err != nil {
		return nil, fmt.Errorf("failed to create server transport: %v", err)
	}
	s := &Service{
		Manager: conduit.NewManager(),
		// A conduit is kept for as long as its dialer keeps it: each
		// connection applies the keep-alive lifetime of its dialer.
		serverTransporter:     dcontransport.WithClientLifetime(transporter),
		meshServer:            meshServer,
		reverseForwardAddress: reverseForwardAddress,
	}
	s.registerEndpoint = s.RegisterEndpoint
	s.deregisterEndpoint = s.DeregisterEndpoint
	return s, nil
}

// RegisterEndpoint registers service discovery with mesh server.
func (s *Service) RegisterEndpoint(ctx context.Context, req *dconpb.EstablishConduitRequest, dynamicPort int) (string, error) {
	physicalAddress := fmt.Sprintf("%s:%d", s.reverseForwardAddress, dynamicPort)
	protocolStr := "tcp"
	switch req.Protocol {
	case dconpb.EstablishConduitRequest_PROTOCOL_GRPC:
		protocolStr = "grpc"
	case dconpb.EstablishConduitRequest_PROTOCOL_HTTP:
		protocolStr = "http"
	}

	return s.meshServer.RegisterEndpoint(ctx, req.ServerName, req.InstanceId, physicalAddress, protocolStr)
}

// DeregisterEndpoint deregisters service discovery with mesh server.
func (s *Service) DeregisterEndpoint(ctx context.Context, req *dconpb.EstablishConduitRequest, dynamicPort int) error {
	physicalAddress := fmt.Sprintf("%s:%d", s.reverseForwardAddress, dynamicPort)
	return s.meshServer.DeregisterEndpoint(ctx, req.ServerName, req.InstanceId, physicalAddress)
}

// Run starts the Acceptor service, listening for incoming RSocket connections.
func (s *Service) Run(ctx context.Context) error {
	slog.InfoContext(ctx, "Acceptor starting up")

	defer s.Manager.Shutdown()

	handler := func(ctx context.Context, setup payload.SetupPayload, rs rsocket.CloseableRSocket) (rsocket.RSocket, error) {
		// The string that rsocket-go returns points into a buffer that it
		// reuses for later frames, so the ID is copied.
		conduitID := strings.Clone(setup.DataUTF8())

		if conduitID == "PROBE" {
			return s.handleProbe(), nil
		}

		req, traceContext, protocolVersion, err := s.parseSetupMetadata(ctx, setup)
		if err != nil {
			return nil, err
		}
		// A dialer whose protocol this acceptor does not serve is refused.
		switch {
		case protocolVersion == version.ProtocolVersion:
		case protocolVersion >= version.MinProtocolVersion && protocolVersion < version.ProtocolVersion:
			// Protocol 0 support: a dialer of an older protocol that this
			// acceptor still serves is served in that protocol.
			slog.WarnContext(ctx, "Dialer speaks an older DualConduit protocol and is served with it. Upgrade the dialer",
				"id", conduitID, "instance_id", req.GetInstanceId(), "type", req.GetType().String(),
				"dialer_protocol", protocolVersion, "acceptor_protocol", version.ProtocolVersion, "acceptor_version", version.Version)
		default:
			err := version.MismatchError(protocolVersion)
			slog.ErrorContext(ctx, "Refusing conduit", "id", conduitID, "error", err)
			return nil, err
		}

		// Extract parent context from W3C headers in traceContext
		extractedCtx := otel.GetTextMapPropagator().Extract(ctx, propagation.MapCarrier(traceContext))

		slog.InfoContext(extractedCtx, "RSocket Connection incoming", "id", conduitID, "metadata", req)

		// Start OpenTelemetry span for acceptor side conduit lifecycle
		tracer := otel.Tracer("dualconduit/conduit")
		_, acceptorSpan := tracer.Start(extractedCtx, "conduit.open",
			trace.WithAttributes(
				attribute.String("conduit.id", conduitID),
				attribute.String("conduit.destination", req.DestinationEndpoint),
				attribute.Int("conduit.entry_port", int(req.EntryPort)),
				attribute.String("conduit.type", req.Type.String()),
			),
		)

		con, err := s.Manager.Add(ctx, conduitID, req, rs, nil, acceptorSpan.SpanContext())
		if err != nil {
			slog.ErrorContext(extractedCtx, "Failed to add conduit", "id", conduitID, "error", err)
			acceptorSpan.RecordError(err)
			acceptorSpan.SetStatus(otelcodes.Error, err.Error())
			acceptorSpan.End()
			return nil, err
		}
		acceptorSpan.End()
		// Protocol 0 support.
		con.SetPeerProtocol(protocolVersion)

		if req.Type == dconpb.EstablishConduitRequest_CONDUIT_TYPE_REVERSE {
			responder, err := s.handleReverseConduit(ctx, conduitID, req, rs, con)
			if err != nil {
				slog.ErrorContext(ctx, "Refusing reverse conduit", "id", conduitID, "error", err)
			}
			return responder, err
		}

		return s.handleForwardConduit(con, req), nil
	}

	// All connections share one scheduler. Left to itself, rsocket-go
	// creates a pool of goroutines for each connection before it asks the
	// handler above, and releases the pool only when the connection closes.
	// It never closes a connection whose SETUP the handler refuses, so each
	// refusal would leave the scavenger goroutine of its pool behind for
	// good. rsocket-go does not release a scheduler that it is given.
	return rsocket.Receive().
		Scheduler(scheduler.Elastic(), nil).
		Acceptor(handler).
		Transport(s.serverTransporter).
		Serve(ctx)
}

func (s *Service) handleProbe() rsocket.RSocket {
	slog.InfoContext(context.Background(), "Received check connection, acknowledging")
	return rsocket.NewAbstractSocket(
		rsocket.RequestResponse(func(msg payload.Payload) mono.Mono {
			clientHostname := msg.DataUTF8()
			slog.InfoContext(context.Background(), "Probe from client", "hostname", clientHostname)
			return mono.Just(payload.New([]byte("ACK "+clientHostname), nil))
		}),
	)
}

// parseSetupMetadata returns the request, the trace context and the protocol
// version of the dialer. A dialer that does not name its protocol version has
// version 0.
func (s *Service) parseSetupMetadata(ctx context.Context, setup payload.SetupPayload) (*dconpb.EstablishConduitRequest, map[string]string, int32, error) {
	metadata, ok := setup.Metadata()
	if !ok || len(metadata) == 0 {
		slog.ErrorContext(ctx, "Setup payload is missing metadata")
		return nil, nil, 0, fmt.Errorf("setup payload is missing metadata")
	}
	setupMeta := &dconpb.ConduitSetupMetadata{}
	if err := proto.Unmarshal(metadata, setupMeta); err == nil {
		if setupMeta.Request != nil {
			return setupMeta.Request, setupMeta.TraceContext, setupMeta.ProtocolVersion, nil
		}
		if setupMeta.ProtocolVersion != 0 || len(setupMeta.TraceContext) > 0 {
			slog.ErrorContext(ctx, "Setup metadata is missing EstablishConduitRequest")
			return nil, nil, 0, fmt.Errorf("setup metadata is missing request")
		}
	}

	// Fallback to unmarshaling directly as EstablishConduitRequest.
	req := &dconpb.EstablishConduitRequest{}
	if err := proto.Unmarshal(metadata, req); err != nil {
		slog.ErrorContext(ctx, "Invalid setup metadata", "error", err)
		return nil, nil, 0, err
	}
	return req, nil, 0, nil
}

// loopListener is the entry port of a reverse conduit as its listening loop
// sees it. The loop closes its listener when it ends, which can be before the
// endpoint has left the mesh; the conduit closes the entry port itself, after
// that.
type loopListener struct{ net.Listener }

func (loopListener) Close() error { return nil }

// handleReverseConduit makes the entry point of a reverse conduit ready and
// sends its locator to the dialer. It does all of it before it returns, so
// that the conduit cannot close while a part of its entry point is not yet
// registered to be closed.
//
// The conduit is already in the manager. When a step fails, the SETUP is
// refused with the returned error. rsocket-go reports no close for such a
// connection, but its handling returns, which ends the context of the
// connection: the conduit lives under that context and closes with it, and
// closes what is registered here.
func (s *Service) handleReverseConduit(ctx context.Context, conduitID string, req *dconpb.EstablishConduitRequest, rs rsocket.CloseableRSocket, con *conduit.Conduit) (rsocket.RSocket, error) {
	addr := ":0" // ":0" tells the OS to pick a free port
	if req.EntryPort != 0 {
		addr = fmt.Sprintf(":%d", req.EntryPort)
	}
	lis, err := net.Listen("tcp", addr)
	if err != nil {
		return nil, fmt.Errorf("failed to open the entry port: %v", err)
	}
	// The entry port closes here and nowhere else. It is registered first,
	// so that it closes last, after the endpoint has left the mesh: with a
	// fixed entry port, the next conduit can then not register the same
	// address before this one has removed it. It also closes the port when
	// a step below fails.
	con.AtEntryClose(func() { lis.Close() })
	tcpAddr, ok := lis.Addr().(*net.TCPAddr)
	if !ok {
		return nil, fmt.Errorf("listener address is not a TCP address: %v", lis.Addr())
	}
	dynamicPort := tcpAddr.Port

	// The locator that the dialer is sent depends on the protocol. A
	// protocol without one is refused before the mesh is touched.
	var newLocator func(logicalName string) *dconpb.ServiceLocator
	switch req.Protocol {
	case dconpb.EstablishConduitRequest_PROTOCOL_GRPC:
		newLocator = func(logicalName string) *dconpb.ServiceLocator {
			return &dconpb.ServiceLocator{Locator: &dconpb.ServiceLocator_XdsAddress{XdsAddress: "xds:///" + logicalName}}
		}
	case dconpb.EstablishConduitRequest_PROTOCOL_HTTP:
		newLocator = func(logicalName string) *dconpb.ServiceLocator {
			return &dconpb.ServiceLocator{Locator: &dconpb.ServiceLocator_VirtualHost{VirtualHost: logicalName}}
		}
	case dconpb.EstablishConduitRequest_PROTOCOL_TCP:
		newLocator = func(logicalName string) *dconpb.ServiceLocator {
			return &dconpb.ServiceLocator{Locator: &dconpb.ServiceLocator_Sni{Sni: logicalName}}
		}
	default:
		return nil, fmt.Errorf("unsupported protocol %v for a reverse conduit", req.Protocol)
	}

	logicalName, err := s.registerEndpoint(ctx, req, dynamicPort)
	if err != nil {
		return nil, fmt.Errorf("failed to register the endpoint in the mesh: %v", err)
	}
	// The endpoint leaves the mesh in the step of closing that ends the
	// context of the conduit, so that the mesh does not route clients to a
	// conduit that is closing. ctx is the context of the connection, which
	// may have ended by then, so the call has a context of its own.
	con.AtEntryClose(func() {
		deregisterCtx, cancel := context.WithTimeout(context.WithoutCancel(ctx), deregisterTimeout)
		defer cancel()
		if err := s.deregisterEndpoint(deregisterCtx, req, dynamicPort); err != nil {
			slog.ErrorContext(ctx, "Service deregister failed", "id", conduitID, "error", err)
		}
	})

	metadata, err := proto.Marshal(&dconpb.EstablishConduitResponse{
		ConduitId:      conduitID,
		ServiceLocator: newLocator(logicalName),
	})
	if err != nil {
		return nil, fmt.Errorf("failed to marshal the locator: %v", err)
	}
	// MetadataPush only puts the frame into the send queue of the
	// connection, which is empty here. The frame leaves once rsocket-go has
	// accepted the SETUP and started to write.
	rs.MetadataPush(payload.New(nil, metadata))

	go func() {
		if err := conduit.StartListeningLoop(con, func() (net.Listener, error) {
			return loopListener{lis}, nil
		}); err != nil {
			slog.ErrorContext(ctx, "StartListeningLoop error", "id", conduitID, "error", err)
		}
	}()

	// For Reverse conduits, the Acceptor is the Requester, but we listen for CLOSE signal.
	return rsocket.NewAbstractSocket(
		rsocket.MetadataPush(closeSignalHandler(ctx, conduitID, con)),
	), nil
}

// closeSignalHandler returns the handler of the METADATA_PUSH frames of a
// reverse conduit. The CLOSE signal of the dialer starts closing the conduit.
// The handler runs on a goroutine of rsocket-go, so it does not wait for the
// conduit to close.
func closeSignalHandler(ctx context.Context, conduitID string, con *conduit.Conduit) func(payload.Payload) {
	return func(msg payload.Payload) {
		metadata, ok := msg.Metadata()
		if ok && string(metadata) == "CLOSE" {
			slog.InfoContext(ctx, "Received CLOSE signal from Dialer", "id", conduitID)
			con.BeginClose()
		}
	}
}

func (s *Service) handleForwardConduit(con *conduit.Conduit, req *dconpb.EstablishConduitRequest) rsocket.RSocket {
	return rsocket.NewAbstractSocket(
		rsocket.RequestChannel(func(incoming flux.Flux) flux.Flux {
			return conduit.AcceptStream(con, req.DestinationEndpoint, incoming)
		}),
	)
}
