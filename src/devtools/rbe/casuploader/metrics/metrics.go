// Package metrics contains the metrics for the uploader.
package metrics

import (
	"encoding/json"
	"fmt"
	"os"
)

// Metrics contains the metrics for the uploader.
type Metrics struct {
	TimeMs            int64 `json:"time_ms"`             // End to end time to upload the artifact.
	UnzipTimeMs       int64 `json:"unzip_time_ms"`       // Time to unzip the artifact if it is a zip file.
	ChunkTimeMs       int64 `json:"chunk_time_ms"`       // Time to chunk files if chunking is enabled.
	UploadTimeMs      int64 `json:"upload_time_ms"`      // Time to upload the artifact.
	SizeBytes         int64 `json:"size_bytes"`          // Size of the artifact in bytes.
	UploadedSizeBytes int64 `json:"uploaded_size_bytes"` // Size of uploaded entries in bytes.
	Entries           int   `json:"entries"`             // Number of entries.
	UploadedEntries   int   `json:"uploaded_entries"`    // Number of uploaded entries.
}

// Dump dumps the metrics to a file.
func (m *Metrics) Dump(path string) error {
	data, err := json.MarshalIndent(m, "", "  ")
	if err != nil {
		return fmt.Errorf("error marshaling: %v", err)
	}

	err = os.WriteFile(path, data, 0644)
	if err != nil {
		return fmt.Errorf("error writing to file: %v", err)
	}

	return nil
}
