// Package uploader uploads local files/directories to CAS.
package uploader

import (
	"context"
	"os"
	"path"

	"github.com/bazelbuild/remote-apis-sdks/go/pkg/client"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/digest"
	"github.com/google/device-infra/src/devtools/rbe/casuploader/metrics"
	"github.com/google/uuid"
)

// Uploader is the common interface implemented by all kind of uploaders.
type Uploader interface {
	// DoUpload uploads files/directories to CAS, and returns the digest of the root directory.
	DoUpload() (digest.Digest, error)
}

// CommonConfig is the common configurations used for all kinds of uploders
type CommonConfig struct {
	ctx             context.Context
	client          *client.Client
	excludeFilters  []string
	dumpFileDetails string
	chunk           bool
	avgChunkSize    int
	metrics         *metrics.Metrics
}

// NewCommonConfig creates a common CAS uploader configuration.
func NewCommonConfig(ctx context.Context, client *client.Client, excludeFilters []string, dumpFileDetails string, chunk bool, avgChunkSize int, metrics *metrics.Metrics) *CommonConfig {
	return &CommonConfig{
		ctx:             ctx,
		client:          client,
		excludeFilters:  excludeFilters,
		dumpFileDetails: dumpFileDetails,
		chunk:           chunk,
		avgChunkSize:    avgChunkSize,
		metrics:         metrics,
	}
}

func createTmpDir() string {
	target := path.Join(os.TempDir(), "cas_uploader_tmp", uuid.New().String())
	os.MkdirAll(target, 0755)
	return target
}
