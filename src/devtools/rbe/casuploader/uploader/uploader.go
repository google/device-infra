// Package uploader uploads local files/directories to CAS.
package uploader

import (
	"context"
	"os"
	"path"

	"github.com/bazelbuild/remote-apis-sdks/go/pkg/client"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/digest"
	"github.com/google/uuid"
)

// Uploader is the common interface implemented by all kind of uploaders.
type Uploader interface {
	// DoUpload uploads files/directories to CAS, and returns the digest of the root directory.
	DoUpload() (digest.Digest, error)
}

type uploaderConfig struct {
	ctx            context.Context
	client         *client.Client
	path           string
	excludeFilters []string
}

func createTmpDir() string {
	target := path.Join(os.TempDir(), "cas_uploader_tmp", uuid.New().String())
	os.MkdirAll(target, 0755)
	return target
}
