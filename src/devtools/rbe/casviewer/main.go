// Package main is the entry point for the FastCDC-FUSE filesystem.
package main

import (
	"errors"
	"fmt"
	"os"
	"os/signal"
	"syscall"

	"flag"
	
	log "github.com/golang/glog"
	"github.com/google/device-infra/src/devtools/rbe/casviewer/chunkstore"
	"github.com/google/device-infra/src/devtools/rbe/casviewer/fuse"
	"github.com/google/device-infra/src/devtools/rbe/casviewer/mountutil"
)

const (
	version = "1.5"
)

var (
	printVersion = flag.Bool("version", false, "Print version information")
	indexPath    = flag.String("index", "", "Path to index JSON file (required)")
	chunkDir     = flag.String("chunks", "", "Directory containing chunk files")
	mountPoint   = flag.String("mount", "", "Mount point (required)")
	logDir       = flag.String("log-dir", "", "Log directory path")
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

func logToDir(dir string) error {
	// Create log directory if it doesn't exist.
	if _, err := os.Stat(dir); os.IsNotExist(err) {
		if err := os.MkdirAll(dir, 0755); err != nil {
			return err
		}
	} else if err != nil {
		return err
	}
	return flag.Set("log_dir", dir)
}

func main() {
	// Parse command line arguments
	flag.Set("silent_init", "true")
	flag.Set("logalsotostderr", "true")
	flag.Set("stderrthreshold", "INFO")

	flag.Parse()

	if *logDir != "" {
		if err := logToDir(*logDir); err != nil {
			log.Exit(err)
		} else {
			log.Infof("Log directory: %s", *logDir)
		}
	}

	fmt.Printf("version: %s\n", version) // Always print version.
	if *printVersion {
		os.Exit(0)
	}

	if err := checkFlags(); err != nil {
		log.Exit(err)
	}

	log.Info("Creating new ChunkStore...")
	store, err := chunkstore.NewChunkStore(*chunkDir, *indexPath)
	if err != nil {
		log.Exit(err)
	}

	log.Info("Creating new FastCDCFS...")
	fs := fuse.NewFastCDCFS(store)

	log.Infof("Mounting filesystem at %s...", *mountPoint)
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
