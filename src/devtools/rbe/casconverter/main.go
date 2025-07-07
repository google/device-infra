// Binary casconverter restores a source directory from a FastCDC-chunked directory.
package main

import (
	"fmt"
	"os"
	"time"

	"flag"
	
	log "github.com/golang/glog"
	"github.com/google/device-infra/src/devtools/rbe/casuploader/chunkerutil"
)

const (
	version = "1.0"
)

var (
	printVersion = flag.Bool("version", false, "Print version information.")
	srcPath      = flag.String("src-path", "", "Path of a FastCDC-chunked directory to be restored. This should be the directory speicified as --dir to casdownloader.")
	dstPath      = flag.String("dst-path", "", "Path of destination directory for restore files. If not specified, files are restored in the source directory.")
	keepChunks   = flag.Bool("keep-chunks", false, "Whether to keep chunk files after restoration.")
)

func main() {
	flag.Set("silent_init", "true")
	flag.Set("logtostderr", "true")
	flag.Set("stderrthreshold", "INFO")
	flag.Parse()

	if *printVersion {
		fmt.Printf("version: %s\n", version)
		os.Exit(0)
	}

	if *srcPath == "" {
		log.Exitf("--src-path is required.")
	}
	dst := *dstPath
	if dst == "" {
		dst = *srcPath
	} else {
		if _, err := os.Stat(dst); err != nil {
			if os.IsNotExist(err) {
				if err = os.MkdirAll(dst, 0755); err != nil {
					log.Exitf("Failed to create directory %q: %v", dst, err)
				}
			} else {
				log.Exitf("Failed to check directory %q: %v", dst, err)
			}
		}
	}

	start := time.Now()
	if err := chunkerutil.RestoreFiles(*srcPath, dst, *keepChunks); err != nil {
		log.Exitf("Failed to restore directory %q: %v", *srcPath, err)
	}
	log.Info(summary(*srcPath, dst, *keepChunks, time.Since(start)))
}

func summary(src, dst string, keepChunks bool, duration time.Duration) string {
	msg := fmt.Sprintf("Successfully restored '%s'", src)
	if dst != src {
		msg += fmt.Sprintf(" to '%s'", dst)
	}
	if keepChunks {
		msg += " and kept chunk files"
	}
	return msg + fmt.Sprintf(" (took %v).", duration)
}
