// Package transport provides transport utilities for Dual Conduit, supporting TCP and WebSocket.
package transport

import (
	"context"
	"fmt"
	"net/http"
	"strings"
	"sync"

	"github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/auth"
	"github.com/rsocket/rsocket-go/core"
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

// lockedConn serializes writes to the underlying connection.
//
// rsocket-go writes to a connection from more than one goroutine while a client
// is being set up: the SETUP frame is written by the caller, and a keep-alive
// frame may be written by the write loop at the same time. The connections it
// provides are not safe for concurrent writes, and the WebSocket one panics.
type lockedConn struct {
	transport.Conn
	mu sync.Mutex
}

// Write writes a frame while holding the write lock.
func (c *lockedConn) Write(frame core.WriteableFrame) error {
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.Conn.Write(frame)
}

// Flush flushes buffered frames while holding the write lock.
func (c *lockedConn) Flush() error {
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.Conn.Flush()
}

// Addr returns the address of the underlying connection, if it has one.
func (c *lockedConn) Addr() string {
	if ac, ok := c.Conn.(transport.AddrConn); ok {
		return ac.Addr()
	}
	return ""
}

// withLockedWrites returns a ClientTransporter whose connections serialize writes.
func withLockedWrites(dial transport.ClientTransporter) transport.ClientTransporter {
	return func(ctx context.Context) (*transport.Transport, error) {
		tp, err := dial(ctx)
		if err != nil {
			return nil, err
		}
		return transport.NewTransport(&lockedConn{Conn: tp.Connection()}), nil
	}
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
	return withLockedWrites(trans), nil
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
