// Binary rollout_agent is rollout-agent: the agent of MTT node auto-rollout
// on one host. See "Agent" in node_auto_rollout_design.md.
//
//	rollout_agent enable  [flags]   enrolls the host in its group
//	rollout_agent poll    [flags]   does one poll
//	rollout_agent disable [flags]   opts the host out
//
// Each command does its work and exits; a timer runs "poll" every poll
// interval. The three commands take the same flags.
package main

import (
	"context"
	"errors"
	"flag"
	"fmt"
	"io"
	"log/slog"
	"os"
	"time"

	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/agent"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/auth"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/record"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/store/firestore"
)

// timeout is the time a command has. Most of it is for the mtt command,
// which looks for a newer version of itself each time it runs and downloads
// it. It is shorter than the poll interval, so that a poll has ended before
// the next one is due.
const timeout = 3 * time.Minute

// The commands of the binary.
const (
	commandPoll    = "poll"
	commandEnable  = "enable"
	commandDisable = "disable"
)

// options is what the arguments of the binary say.
type options struct {
	command string
	// credentials is the path of the service account key file of the host.
	credentials string
	// database is the ID of the Firestore database with the rollout records.
	database string
	// domain and group name the rollout group of the host.
	domain string
	group  string
	// role is the role of the node, and mtt the path of the mtt command.
	role string
	mtt  string
	// drain and drainTimeout are how the upgrades of this host drain.
	drain        bool
	drainTimeout time.Duration
}

func main() {
	opts, err := parseFlags(os.Args[1:], os.Stderr)
	if errors.Is(err, flag.ErrHelp) {
		return
	}
	log := slog.New(slog.NewTextHandler(os.Stderr, nil))
	if err == nil {
		err = run(opts, log)
	}
	if err != nil {
		// For a poll, the next poll is the retry.
		log.Error("rollout_agent failed", "error", err)
		os.Exit(1)
	}
}

// parseFlags returns what the command-line arguments say: a command, then
// flags, and nothing else.
//
// When the arguments ask for help, it writes the usage to usage and returns
// flag.ErrHelp. For any other error it writes nothing: the caller reports
// the error.
func parseFlags(args []string, usage io.Writer) (options, error) {
	var opts options
	fs := flag.NewFlagSet("rollout_agent", flag.ContinueOnError)
	// The flag package prints an error as well as returning it. Only the
	// returned one is used.
	fs.SetOutput(io.Discard)
	fs.StringVar(&opts.credentials, "credentials", "", "path of the service account key file of the host; its account reads and writes the rollout records")
	fs.StringVar(&opts.database, "database", "", "ID of the Firestore database that holds the rollout records")
	fs.StringVar(&opts.domain, "domain", "", "ID of the rollout domain of the host: the host name of the controller's Cloud Endpoints service, in lower case")
	fs.StringVar(&opts.group, "group", "", "ID of the rollout group of the host: \"controller\", or the pool of a worker")
	fs.StringVar(&opts.role, "role", "", "role of the node: \"worker\" or \"controller\"")
	fs.StringVar(&opts.mtt, "mtt", "mtt", "path of the mtt command")
	fs.BoolVar(&opts.drain, "drain", false, "let an upgrade wait for the tests of the host to end")
	fs.DurationVar(&opts.drainTimeout, "drain_timeout", 0, "the longest that an upgrade waits for the tests, with --drain; 0 leaves it to mtt")

	if len(args) > 0 {
		opts.command, args = args[0], args[1:]
	}
	switch opts.command {
	case commandPoll, commandEnable, commandDisable:
	case "-h", "-help", "--help":
		args = []string{"-h"}
	default:
		return options{}, fmt.Errorf("arguments: the command is %q, want poll, enable, or disable", opts.command)
	}
	err := fs.Parse(args)
	switch {
	case errors.Is(err, flag.ErrHelp):
		fmt.Fprintln(usage, "Usage: rollout_agent poll|enable|disable [flags]")
		fs.SetOutput(usage)
		fs.PrintDefaults()
		return options{}, err
	case err != nil:
		return options{}, fmt.Errorf("flags: %w", err)
	case fs.NArg() > 0:
		return options{}, fmt.Errorf("flags: unexpected arguments %q", fs.Args())
	case opts.credentials == "":
		return options{}, errors.New("flags: --credentials is not set")
	case opts.database == "":
		return options{}, errors.New("flags: --database is not set")
	case opts.domain == "":
		return options{}, errors.New("flags: --domain is not set")
	case opts.group == "":
		return options{}, errors.New("flags: --group is not set")
	case opts.role != agent.RoleWorker && opts.role != agent.RoleController:
		return options{}, fmt.Errorf("flags: --role is %q, want %q or %q", opts.role, agent.RoleWorker, agent.RoleController)
	case opts.drainTimeout < 0:
		return options{}, fmt.Errorf("flags: --drain_timeout is %v, want zero or more", opts.drainTimeout)
	case opts.drainTimeout > 0 && !opts.drain:
		return options{}, errors.New("flags: --drain_timeout is set without --drain")
	}
	return opts, nil
}

// run does what the command of opts says, and logs what it did.
func run(opts options, log *slog.Logger) error {
	hostname, err := os.Hostname()
	if err != nil {
		return err
	}
	agentID, err := record.AgentID(hostname)
	if err != nil {
		return err
	}
	// The project of the key is the project of the database.
	creds, err := auth.FromServiceAccountFile(opts.credentials, auth.ScopeDatastore)
	if err != nil {
		return err
	}
	records, err := firestore.New(firestore.Config{Client: creds.Client, Project: creds.Project, Database: opts.database})
	if err != nil {
		return err
	}
	a, err := agent.New(agent.Config{
		DomainID:     opts.domain,
		GroupID:      opts.group,
		AgentID:      agentID,
		Store:        records,
		Node:         &agent.MTTNode{MTT: opts.mtt, Role: opts.role, Drain: opts.drain, DrainTimeout: opts.drainTimeout},
		DrainTimeout: opts.drainTimeout,
	})
	if err != nil {
		return err
	}
	log = log.With("domain", opts.domain, "group", opts.group, "agent", agentID)
	ctx, cancel := context.WithTimeout(context.Background(), timeout)
	defer cancel()

	switch opts.command {
	case commandEnable:
		if err := a.Enable(ctx, time.Now()); err != nil {
			return err
		}
		log.Info("enabled")
	case commandDisable:
		if err := a.Disable(ctx, time.Now()); err != nil {
			return err
		}
		log.Info("disabled")
	default:
		did, err := a.Poll(ctx, time.Now())
		if err != nil {
			return err
		}
		log.Info("poll", "did", did)
	}
	return nil
}
