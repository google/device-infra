// Package main is the entry point for the FastCDC-FUSE filesystem.
package main

import (
	"errors"
	"fmt"
	"io"

	"flag"
	
	log "github.com/golang/glog"

	"os"
	"path/filepath"

	"os/signal"
	"syscall"

	"github.com/google/device-infra/src/devtools/rbe/casuploader/chunkerutil"
	"github.com/google/device-infra/src/devtools/rbe/casviewer/chunkstore"
	"github.com/google/device-infra/src/devtools/rbe/casviewer/fuse"
)

const (
	version = "1.0"
)

var (
	indexPath  = flag.String("index", "", "Path to index JSON file (required)")
	chunkDir   = flag.String("chunks", "", "Directory containing chunk files")
	mountPoint = flag.String("mount", "", "Mount point (required)")
)

func validateMountPoint(mountPoint string) error {
	if mountPoint == "" {
		return errors.New("mount point must be specified with --mount")
	}

	mpFile, err := os.Open(mountPoint)
	if err != nil {
		return fmt.Errorf("mount point %s cannot be opened: %v", mountPoint, err)
	}
	defer mpFile.Close()

	_, err = mpFile.Readdirnames(1) // Try to read one entry
	if err == nil {
		return fmt.Errorf("mount point %s is not empty. FUSE requires an empty directory", mountPoint)
	}

	if err != io.EOF {
		return fmt.Errorf("error checking if mount point %s is empty: %v", mountPoint, err)
	}

	return nil
}

func main() {
	// Parse command line arguments
	flag.Set("silent_init", "true")
	flag.Set("logtostderr", "true")
	flag.Set("stderrthreshold", "INFO")
	flag.Parse() // flag.Parse()

	log.Infof("Version: %v", version)

	if *chunkDir == "" {
		log.Fatal("Chunk dir must be specified with --chunks")
	}

	if *indexPath == "" {
		*indexPath = filepath.Join(*chunkDir, chunkerutil.ChunksIndexFileName)
		log.Infof("Index file not specified with --index, use default: %v", *indexPath)
	}

	if err := validateMountPoint(*mountPoint); err != nil {
		log.Fatalf("Invalid mount point: %v", err)
	}

	store, err := chunkstore.NewChunkStore(*chunkDir, *indexPath)
	if err != nil {
		log.Fatalf("Failed to initialize chunk store: %v", err)
	}

	// Create and mount filesystem
	fs := fuse.NewFastCDCFS(store)
	server, err := fs.Mount(*mountPoint)
	if err != nil {
		log.Fatalf("Mount failed: %v", err)
	}

	log.Infof("FastCDC-FUSE mounted at %s", *mountPoint)
	log.Infof("To unmount, press Ctrl-C or run 'fusermount -u <mount_point>'")

	// Setup signal handling
	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, syscall.SIGINT, syscall.SIGTERM)

	go func() {
		<-sigChan
		log.Info("Received signal, unmounting...")

		// Try clean unmount first
		if err := server.Unmount(); err != nil {
			log.Infof("Clean unmount failed: %v", err)
			log.Info("Attempting lazy unmount...")
			// Fallback to forced unmount
			if err := syscall.Unmount(*mountPoint, syscall.MNT_DETACH); err != nil {
				log.Infof("Forced unmount failed: %v", err)
			}
		}
	}()

	// Wait for unmount
	server.Wait()
	log.Infof("Successfully unmounted FastCDC-FUSE at: %s", *mountPoint)
}
