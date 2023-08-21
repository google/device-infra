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
}

// New creates a new RBE client with given options.
func New(ctx context.Context, clientOpts Opts) (*client.Client, error) {
	opts := []client.Opt{
		client.CASConcurrency(client.DefaultCASConcurrency),
		client.StartupCapabilities(true),
	}

	start := time.Now()
	newClient, err := client.NewClient(ctx, clientOpts.Instance, client.DialParams{
		Service:               clientOpts.ServiceAddress,
		CredFile:              clientOpts.ServiceAccountJSON,
		UseApplicationDefault: clientOpts.UseApplicationDefault,
		MaxConcurrentRequests: client.DefaultMaxConcurrentRequests,
		MaxConcurrentStreams:  client.DefaultMaxConcurrentStreams,
	}, opts...)
	log.Infof("created RBE client, took %s", time.Since(start))
	return newClient, err
}
