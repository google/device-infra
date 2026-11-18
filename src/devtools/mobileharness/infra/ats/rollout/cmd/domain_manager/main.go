// Binary domain_manager is mtt-domain-manager: it does one run of the
// rollout job for one domain and exits. It runs as a Cloud Run job that Cloud
// Scheduler starts; see "Job" in node_auto_rollout_design.md.
//
// It exits with a status other than zero only when the run failed as a
// whole. Everything else is logged and left to the next run.
package main

import (
	"context"
	"errors"
	"flag"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"os"
	"time"

	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/auth"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/imageref"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/job"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/parametermanager"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/registry"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/store/firestore"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/upstream"
)

// options is what the flags of the binary say.
type options struct {
	// domain is the ID of the rollout domain to run.
	domain string
	// database is the ID of the Firestore database with the rollout records.
	database string
	// runTimeout is the task timeout of the Cloud Run job.
	runTimeout time.Duration
}

func main() {
	opts, err := parseFlags(os.Args[1:], os.Stderr)
	if errors.Is(err, flag.ErrHelp) {
		return
	}
	log := newLogger(os.Stdout)
	if err != nil {
		// There is no domain to name yet.
		log.Error("run failed", "error", err)
		os.Exit(1)
	}
	if err := run(context.Background(), opts, log); err != nil {
		log.Error("run failed", "domain", opts.domain, "error", err)
		os.Exit(1)
	}
}

// parseFlags returns what the command-line arguments say. The binary takes
// flags only: any other argument is an error.
//
// When the arguments ask for help, it writes the usage to usage and returns
// flag.ErrHelp. For any other error it writes nothing: the caller reports
// the error, in the form of every other failure of a run.
func parseFlags(args []string, usage io.Writer) (options, error) {
	var opts options
	fs := flag.NewFlagSet("domain_manager", flag.ContinueOnError)
	// The flag package prints an error as well as returning it. Only the
	// returned one is used.
	fs.SetOutput(io.Discard)
	fs.StringVar(&opts.domain, "domain", "", "ID of the rollout domain to run, as the hosts derive it: the host name of the controller's Cloud Endpoints service, in lower case")
	fs.StringVar(&opts.database, "database", "", "ID of the Firestore database that holds the rollout records")
	fs.DurationVar(&opts.runTimeout, "run_timeout", time.Minute, "task timeout of the Cloud Run job; the run derives its time budget from it")

	err := fs.Parse(args)
	switch {
	case errors.Is(err, flag.ErrHelp):
		fmt.Fprintln(usage, "Usage of domain_manager:")
		fs.SetOutput(usage)
		fs.PrintDefaults()
		return options{}, err
	case err != nil:
		return options{}, fmt.Errorf("flags: %w", err)
	case fs.NArg() > 0:
		return options{}, fmt.Errorf("flags: unexpected arguments %q", fs.Args())
	// The checks below are made here, before anything asks for credentials,
	// so that a run with flags left out fails on the flags.
	case opts.domain == "":
		return options{}, errors.New("flags: --domain is not set")
	case opts.database == "":
		return options{}, errors.New("flags: --database is not set")
	case opts.runTimeout <= 0:
		return options{}, fmt.Errorf("flags: --run_timeout is %v, want more than zero", opts.runTimeout)
	}
	return opts, nil
}

// run does one run of the job for the domain that opts names. It returns an
// error when the run failed as a whole.
func run(ctx context.Context, opts options, log *slog.Logger) error {
	// The project of the job is the project of the database, of the
	// parameters, and of the credentials.
	creds, err := auth.Default(auth.ScopeCloudPlatform)
	if err != nil {
		return err
	}
	records, err := firestore.New(firestore.Config{Client: creds.Client, Project: creds.Project, Database: opts.database})
	if err != nil {
		return err
	}
	configs, err := parametermanager.New(parametermanager.Config{Client: creds.Client, Project: creds.Project})
	if err != nil {
		return err
	}
	images, err := registry.New(newRegistryHTTPClient(), creds.Token)
	if err != nil {
		return err
	}
	j, err := job.New(job.Config{
		DomainID:  opts.domain,
		Store:     records,
		Resolver:  &upstream.Resolver{Images: images, Configs: configs},
		Timeout:   opts.runTimeout,
		Permanent: permanent,
		Log:       log,
	})
	if err != nil {
		return err
	}
	_, err = j.Run(ctx, time.Now())
	return err
}

// newRegistryHTTPClient returns the HTTP client of the registry client. It
// adds no credentials: the registry client attaches the token itself, to the
// hosts it allows. It follows no redirect, like the client of package auth:
// the token would go along to another host of the same domain, and the
// digest would be taken from whatever host the redirect names.
func newRegistryHTTPClient() *http.Client {
	return &http.Client{
		CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse },
	}
}

// permanent reports whether an error of the upstream clients is one that
// does not fix itself: somebody has to push the image, create the parameter,
// or correct the group's image or tag.
func permanent(err error) bool {
	return errors.Is(err, registry.ErrNotFound) ||
		errors.Is(err, parametermanager.ErrNotFound) ||
		errors.Is(err, imageref.ErrInvalid)
}

// newLogger returns a logger that writes one JSON object per line to w, in
// the form Cloud Logging reads from the output of a Cloud Run job: the level
// in "severity", under the names Cloud Logging has for it, and the text in
// "message".
func newLogger(w io.Writer) *slog.Logger {
	return slog.New(slog.NewJSONHandler(w, &slog.HandlerOptions{
		ReplaceAttr: func(groups []string, a slog.Attr) slog.Attr {
			if len(groups) > 0 {
				return a
			}
			switch a.Key {
			case slog.LevelKey:
				a.Key = "severity"
				// slog writes "WARN"; Cloud Logging knows "WARNING". The
				// other levels have the same name in both.
				if level, ok := a.Value.Any().(slog.Level); ok && level == slog.LevelWarn {
					a.Value = slog.StringValue("WARNING")
				}
			case slog.MessageKey:
				a.Key = "message"
			}
			return a
		},
	}))
}
