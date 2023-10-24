// Uploader for optimized upload of a zip archive to RBE CAS.
package main

import (
	"context"
	"errors"
	"fmt"
	"os"
	"os/signal"
	"syscall"
	"time"

	"flag"
	
	log "github.com/golang/glog"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/digest"
	"github.com/google/device-infra/src/devtools/rbe/casuploader/uploader"
	"github.com/google/device-infra/src/devtools/rbe/rbeclient"
)

const version = "1.0"

// multiStringFlag is a slice of strings for parsing command flags into a string list.
type multiStringFlag []string

func (f *multiStringFlag) String() string {
	return fmt.Sprintf("%v", *f)
}

func (f *multiStringFlag) Set(val string) error {
	*f = append(*f, val)
	return nil
}

func (f *multiStringFlag) Get() any {
	return []string(*f)
}

var (
	printVersion = flag.Bool("version", false, "Print version information")

	zipPath         = flag.String("zip-path", "", "Path to a .zip file to upload")
	dirPath         = flag.String("dir-path", "", "Path to a directory to upload")
	casInstance     = flag.String("cas-instance", "", "RBE instance")
	casAddr         = flag.String("cas-addr", "remotebuildexecution.googleapis.com:443", "RBE server addr")
	serviceAccount  = flag.String("service-account-json", "", "Path to JSON file with service account credentials to use.")
	useADC          = flag.Bool("use-adc", false, "True to use Application Default Credentials (ADC).")
	dumpDigest      = flag.String("dump-digest", "", "Output the digest to file")
	dumpFileDetails = flag.String("dump-file-details", "", "Export information of all uploaded files to a file")
	excludeFilters  multiStringFlag
)

func checkFlags() error {
	if *casInstance == "" {
		return errors.New("-cas-instance must be specified")
	}
	if *casAddr == "" {
		return errors.New("-cas-addr must be specified")
	}
	if *zipPath == "" && *dirPath == "" {
		return errors.New("-zip-path or -dir-path must be specified")
	}
	if *dirPath != "" && *zipPath != "" {
		return errors.New("-dir-path and -zip-path cannot both be specified")
	}
	if *serviceAccount == "" && *useADC == false {
		return errors.New("Either -use-adc must be true or -service-account-json must be specified")
	}
	if *serviceAccount != "" && *useADC == true {
		return errors.New("-use-adc and -service-account-json must not be set together")
	}
	return nil
}

func main() {
	flag.Var(&excludeFilters, "exclude-filters",
		"Regular expression of paths to be excluded from uploading. The regex will implicitly "+
			"append the root directory path to the beginning, so DO NOT use \"^\" in the regex.")
	flag.Set("silent_init", "true")
	flag.Set("logtostderr", "true")
	flag.Set("stderrthreshold", "INFO")
	flag.Parse()

	if *printVersion == true {
		fmt.Printf("version: %s\n", version)
		os.Exit(0)
	}

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	// Handle interruption and SIGTERM to cancel read operations, so that the FUSE directory can be unmounted safely.
	go func() {
		sigChan := make(chan os.Signal, 1)
		signal.Notify(sigChan, os.Interrupt, syscall.SIGTERM)
		<-sigChan
		log.Infoln("Interrupted, exiting...")
		cancel()
		time.Sleep(3 * time.Second)
		os.Exit(1)
	}()

	if err := checkFlags(); err != nil {
		log.Exit(err)
	}

	start := time.Now()

	// Create a new RBE client.
	client, err := rbeclient.New(ctx, rbeclient.Opts{*casInstance, *casAddr, *serviceAccount, *useADC})
	if err != nil {
		log.Exit(err)
	}
	defer client.Close()

	var rootDigest digest.Digest
	uploaderConfig := uploader.NewCommonConfig(ctx, client, excludeFilters, *dumpFileDetails)
	if *zipPath != "" {
		zipUploader := uploader.NewZipUploader(uploaderConfig, *zipPath)
		rootDigest, err = zipUploader.DoUpload()
		if err != nil {
			log.Exitf("Failed to upload the zip archive to CAS: %v", err)
		}
	} else if *dirPath != "" {
		dirUploader := uploader.NewDirUploader(uploaderConfig, *dirPath, nil)
		rootDigest, err = dirUploader.DoUpload()
		if err != nil {
			log.Exitf("Failed to upload the directory to CAS: %v", err)
		}
	}

	output := fmt.Sprintf("%s/%d", rootDigest.Hash, rootDigest.Size)
	if *dumpDigest != "" {
		os.WriteFile(*dumpDigest, []byte(output), 0644)
	}
	log.Infof("Uploaded %s to RBE instance %s, root digest: %s. E2E time: %v\n", *zipPath, *casInstance, output, time.Since(start))
}
