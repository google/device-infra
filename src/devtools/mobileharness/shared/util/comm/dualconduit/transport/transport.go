// Package transport provides transport utilities for Dual Conduit, supporting TCP and WebSocket.
package transport

import (
	"fmt"
	"net/http"
	"strings"

	"github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/auth"
	"github.com/rsocket/rsocket-go/core/transport"
	"github.com/rsocket/rsocket-go"
)

// Transport represents the transport type.
type Transport string

const (
	// TCP is the transport type for TCP.
	TCP Transport = "tcp"
	// WebSocket is the transport type for WebSocket.
	WebSocket Transport = "websocket"
)

// ClientConfig holds configuration for creating a client transporter.
type ClientConfig struct {
	Target        string
	TransportType Transport
	UseSAToken    bool
	SAKeyFile     string
	Aud           string
}

// ServerConfig holds configuration for creating a server transporter.
type ServerConfig struct {
	Port          int
	TransportType Transport
}

// CreateClientTransport creates a ClientTransporter based on the config.
// If cfg.UseSAToken is true, a new token is generated every time this function is called.
func CreateClientTransport(cfg ClientConfig) (transport.ClientTransporter, error) {
	var trans transport.ClientTransporter
	isWS := cfg.TransportType == WebSocket || strings.HasPrefix(cfg.Target, "ws://") || strings.HasPrefix(cfg.Target, "wss://")

	var token string
	if cfg.UseSAToken {
		var err error
		token, err = auth.GenerateSelfSignedJWT(cfg.SAKeyFile, cfg.Aud)
		if err != nil {
			return nil, fmt.Errorf("failed to generate SA token: %v", err)
		}
	}

	if isWS {
		wsBuilder := rsocket.WebsocketClient().SetURL(cfg.Target)
		if token != "" {
			header := http.Header{}
			header.Set("Authorization", "Bearer "+token)
			wsBuilder = wsBuilder.SetHeader(header)
		}
		trans = wsBuilder.Build()
	} else {
		trans = rsocket.TCPClient().SetAddr(cfg.Target).Build()
	}
	return trans, nil
}

// CreateServerTransport creates a ServerTransporter based on config.
func CreateServerTransport(cfg ServerConfig) (transport.ServerTransporter, error) {
	var serverTransport transport.ServerTransporter
	switch cfg.TransportType {
	case TCP:
		serverTransport = rsocket.TCPServer().SetHostAndPort("0.0.0.0", cfg.Port).Build()
	case WebSocket:
		serverTransport = rsocket.WebsocketServer().SetAddr(fmt.Sprintf("0.0.0.0:%d", cfg.Port)).Build()
	default:
		return nil, fmt.Errorf("unknown transport: %s", cfg.TransportType)
	}
	return serverTransport, nil
}
