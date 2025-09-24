package download

import (
	"context"
	"strings"
	"testing"
)

func TestDoDownloadReturnsErrorForInvalidDigest(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	d := DownloadJob{Digest: "INVALID_DIGEST"}
	err := d.DoDownload(ctx)
	if err == nil || !strings.Contains(err.Error(), "INVALID_DIGEST") {
		t.Fatalf("Failed to return error for invalid root digest")
	}
}
