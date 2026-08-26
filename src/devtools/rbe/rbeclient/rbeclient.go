// Package rbeclient provides the entry point to create a new RBE client.
package rbeclient

import (
	"context"
	"time"

	log "github.com/golang/glog"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/client"
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
	RPCTimeouts map[string]time.Duration
	// NoSecurity indicates that insecure/plaintext credentials should be used (e.g. in tests or local proxy).
	NoSecurity bool
	// ProxyAddress is the address of the local caching proxy (optional).
	ProxyAddress string
}

// New creates a new RBE client with given options.
func New(ctx context.Context, clientOpts Opts) (*client.Client, error) {
	start := time.Now()

	// 1. Connect to the CAS proxy if configured
	if clientOpts.ProxyAddress != "" {
		proxyOpts := []client.Opt{
			client.CASConcurrency(clientOpts.CASConcurrency),
			client.StartupCapabilities(true), // Performs a synchronous GetCapabilities call to verify connection
			client.RPCTimeouts(clientOpts.RPCTimeouts),
		}

		newClient, err := client.NewClient(ctx, clientOpts.Instance, client.DialParams{
			Service:               clientOpts.ProxyAddress,
			NoSecurity:            true, // Proxy is plaintext
			MaxConcurrentRequests: client.DefaultMaxConcurrentRequests,
		}, proxyOpts...)
		if err == nil {
			log.InfoContextf(ctx, "created CAS proxy client, took %s", time.Since(start))
		}
		return newClient, err
	}

	// 2. Direct connection to RBE
	opts := []client.Opt{
		client.CASConcurrency(clientOpts.CASConcurrency),
		client.StartupCapabilities(true),
		client.RPCTimeouts(clientOpts.RPCTimeouts),
	}

	newClient, err := client.NewClient(ctx, clientOpts.Instance, client.DialParams{
		Service:               clientOpts.ServiceAddress,
		CredFile:              clientOpts.ServiceAccountJSON,
		UseApplicationDefault: clientOpts.UseApplicationDefault,
		NoSecurity:            clientOpts.NoSecurity,
		MaxConcurrentRequests: client.DefaultMaxConcurrentRequests,
	}, opts...)
	log.InfoContextf(ctx, "created RBE client, took %s", time.Since(start))
	return newClient, err
}
