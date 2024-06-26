// Package rbeclient provides the entry point to create a new RBE client.
package rbeclient

import (
	"context"
	"time"

	log "github.com/golang/glog"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/client"
)

const (
	// RBECASConcurrency is the default maximum number of concurrent upload and download operations for RBE clients.
	RBECASConcurrency = 500
	// DefaultRPCTimeout is the default RPC timeout for per-rpc deadline.
	DefaultRPCTimeout = 60 * time.Second // 60s to match AB (CAS default is 20s)
)

// Opts contains options for creating a new RBE client.
type Opts struct {
	// Instance is the name of RBE instance.
	Instance string
	// ServiceAddress is the address of remote execution service,
	// e.g. "remotebuildexecution.googleapis.com:443".
	ServiceAddress string
	// ServiceAccountJSON is the path to the Service Account JSON file for auth.
	ServiceAccountJSON string
	// UseApplicationDefault indicates that the default credentials should be used.
	UseApplicationDefault bool
	// CASConcurrency is the maximum number of concurrent upload and download operations.
	CASConcurrency int
	// RPCTimeouts is default RPC timeout.
	RPCTimeout time.Duration
}

// New creates a new RBE client with given options.
func New(ctx context.Context, clientOpts Opts) (*client.Client, error) {
	casConcurrency := clientOpts.CASConcurrency
	if casConcurrency <= 0 {
		casConcurrency = RBECASConcurrency
	}
	rpcTimeouts := client.DefaultRPCTimeouts
	defaultRPCTimeout := clientOpts.RPCTimeout
	if defaultRPCTimeout <= 0 {
		defaultRPCTimeout = DefaultRPCTimeout
	}
	rpcTimeouts["default"] = defaultRPCTimeout
	opts := []client.Opt{
		client.CASConcurrency(casConcurrency),
		client.StartupCapabilities(true),
		client.RPCTimeouts(rpcTimeouts),
	}

	start := time.Now()
	newClient, err := client.NewClient(ctx, clientOpts.Instance, client.DialParams{
		Service:               clientOpts.ServiceAddress,
		CredFile:              clientOpts.ServiceAccountJSON,
		UseApplicationDefault: clientOpts.UseApplicationDefault,
		MaxConcurrentRequests: client.DefaultMaxConcurrentRequests,
	}, opts...)
	log.Infof("created RBE client, took %s", time.Since(start))
	return newClient, err
}
