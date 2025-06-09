// Package main is the entry point for the FastCDC-FUSE filesystem.
package main

import (
	"errors"
	"fmt"

	"flag"
	
	log "github.com/golang/glog"

	"os"

	"os/signal"
	"syscall"

	"github.com/google/device-infra/src/devtools/rbe/casviewer/chunkstore"
	"github.com/google/device-infra/src/devtools/rbe/casviewer/fuse"
	"github.com/google/device-infra/src/devtools/rbe/casviewer/mountutil"
)

const (
	version = "1.2"
)

var (
	printVersion = flag.Bool("version", false, "Print version information")
	indexPath    = flag.String("index", "", "Path to index JSON file (required)")
	chunkDir     = flag.String("chunks", "", "Directory containing chunk files")
	mountPoint   = flag.String("mount", "", "Mount point (required)")
)

func checkFlags() error {
	if *chunkDir == "" {
		return errors.New("Chunk dir must be specified with --chunks")
	}

	if *indexPath == "" {
		defaultIndexPath, err := mountutil.DefaultIndexFile(*chunkDir)
		if err != nil {
			return err
		}
		if defaultIndexPath == "" {
			return errors.New("Index file not specified with --index and no default found")
		}
		*indexPath = defaultIndexPath
		log.Infof("Use default index file: %v", *indexPath)
	}

	if err := mountutil.ValidateMountPoint(*mountPoint); err != nil {
		return err
	}

	return nil
}

func main() {
	// Parse command line arguments
	flag.Set("silent_init", "true")
	flag.Set("logtostderr", "true")
	flag.Set("stderrthreshold", "INFO")
	flag.Parse() // flag.Parse()

	if *printVersion {
		fmt.Printf("version: %s\n", version)
		os.Exit(0)
	}

	if err := checkFlags(); err != nil {
		log.Exit(err)
	}

	store, err := chunkstore.NewChunkStore(*chunkDir, *indexPath)
	if err != nil {
		log.Exit(err)
	}

	// Create and mount filesystem
	fs := fuse.NewFastCDCFS(store)
	server, err := fs.Mount(*mountPoint)
	if err != nil {
		log.Exit(err)
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
