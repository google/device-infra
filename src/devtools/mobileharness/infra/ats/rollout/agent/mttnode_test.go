package agent

import (
	"context"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"syscall"
	"testing"
	"time"

	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/record"
	"github.com/google/go-cmp/cmp"
)

// The test binary stands in for the mtt command: MTTNode runs the test
// binary itself, and TestMain turns the process into a fake mtt when these
// variables say so.
const (
	// envFake makes the process a fake mtt.
	envFake = "ROLLOUT_AGENT_TEST_FAKE_MTT"
	// envStdout and envStderr are what the fake prints. envExit, if set,
	// makes it exit with status 1.
	envStdout = "ROLLOUT_AGENT_TEST_FAKE_MTT_STDOUT"
	envStderr = "ROLLOUT_AGENT_TEST_FAKE_MTT_STDERR"
	envExit   = "ROLLOUT_AGENT_TEST_FAKE_MTT_EXIT"
	// envRecord names the file in which the fake records how it was run:
	// "leader" or "member" of its process group on the first line, then one
	// argument per line.
	envRecord = "ROLLOUT_AGENT_TEST_FAKE_MTT_RECORD"
)

func TestMain(m *testing.M) {
	if os.Getenv(envFake) != "" {
		os.Exit(fakeMTT())
	}
	os.Exit(m.Run())
}

// fakeMTT is the fake mtt command. It returns its exit status.
func fakeMTT() int {
	if path := os.Getenv(envRecord); path != "" {
		// A process that started a session of its own leads its process
		// group; one that stayed in the session of the test does not.
		session := "member"
		if syscall.Getpgrp() == os.Getpid() {
			session = "leader"
		}
		lines := append([]string{session}, os.Args[1:]...)
		// The file appears whole: the test waits for it.
		if err := os.WriteFile(path+".tmp", []byte(strings.Join(lines, "\n")), 0o600); err != nil {
			return 3
		}
		if err := os.Rename(path+".tmp", path); err != nil {
			return 3
		}
	}
	fmt.Fprint(os.Stdout, os.Getenv(envStdout))
	fmt.Fprint(os.Stderr, os.Getenv(envStderr))
	if os.Getenv(envExit) != "" {
		return 1
	}
	return 0
}

// newFakeMTT returns a node whose mtt command is the fake, which prints
// stdout, and the file in which the fake records how it was run.
func newFakeMTT(t *testing.T, role, stdout string) (*MTTNode, string) {
	t.Helper()
	recorded := filepath.Join(t.TempDir(), "run")
	t.Setenv(envFake, "1")
	t.Setenv(envStdout, stdout)
	t.Setenv(envRecord, recorded)
	return &MTTNode{MTT: os.Args[0], Role: role}, recorded
}

// readRun returns what the fake recorded in the file at path, waiting for a
// fake that has not got that far yet.
func readRun(t *testing.T, path string) []string {
	t.Helper()
	for deadline := time.Now().Add(30 * time.Second); time.Now().Before(deadline); time.Sleep(10 * time.Millisecond) {
		if b, err := os.ReadFile(path); err == nil {
			return strings.Split(string(b), "\n")
		}
	}
	t.Fatalf("the fake mtt command did not run: no file %q", path)
	return nil
}

func TestMTTNode_Status(t *testing.T) {
	tests := []struct {
		name   string
		role   string
		stdout string
		want   Status
	}{
		{
			name: "worker_running",
			role: RoleWorker,
			// The fields the agent does not read are those of a real status.
			stdout: `{
  "role": "worker",
  "tunnel": "healthy",
  "state": "RUNNING",
  "busy": false,
  "active_revision": {
    "build": "asia-docker.pkg.dev/p/r/img@sha256:abc",
    "tenant_config_version": "t1",
    "pool_config_version": "p1"
  },
  "last_upgrade": {"image_ref": "x", "started_at": "t0", "finished_at": "t1", "success": true, "reason": "converged"}
}`,
			want: Status{Running: true, Active: record.Revision{Build: "asia-docker.pkg.dev/p/r/img@sha256:abc", TenantConfigVersion: "t1", PoolConfigVersion: "p1"}},
		},
		{
			name:   "controller_running_has_a_build_alone",
			role:   RoleController,
			stdout: `{"role": "controller", "tunnel": "healthy", "state": "RUNNING", "busy": false, "active_revision": {"build": "gcr.io/p/img@sha256:abc"}}`,
			want:   Status{Running: true, Active: record.Revision{Build: "gcr.io/p/img@sha256:abc"}},
		},
		{
			name:   "down",
			role:   RoleWorker,
			stdout: `{"role": "worker", "tunnel": "down", "state": "DOWN", "busy": false, "active_revision": {}}`,
			want:   Status{},
		},
		{
			name:   "busy_upgrading",
			role:   RoleWorker,
			stdout: `{"role": "worker", "tunnel": "healthy", "state": "DOWN", "busy": true, "active_revision": {}, "busy_command": "UpgradeCore"}`,
			want:   Status{Busy: true},
		},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			node, recorded := newFakeMTT(t, tc.role, tc.stdout)

			got, err := node.Status(context.Background())

			if err != nil {
				t.Fatalf("Status failed: %v", err)
			}
			if diff := cmp.Diff(tc.want, got); diff != "" {
				t.Errorf("Status differs (-want +got):\n%s", diff)
			}
			if diff := cmp.Diff([]string{"node", tc.role, "status"}, readRun(t, recorded)[1:]); diff != "" {
				t.Errorf("arguments of mtt differ (-want +got):\n%s", diff)
			}
		})
	}
}

func TestMTTNode_Status_Errors(t *testing.T) {
	t.Run("command_fails", func(t *testing.T) {
		node, _ := newFakeMTT(t, RoleWorker, "")
		t.Setenv(envStderr, "ERROR: not a member of the mtt group")
		t.Setenv(envExit, "1")

		_, err := node.Status(context.Background())

		// The error carries what the command said, for the log of the agent.
		if err == nil || !strings.Contains(err.Error(), "not a member of the mtt group") {
			t.Errorf("Status = %v, want an error with the output of the command", err)
		}
	})
	t.Run("command_does_not_exist", func(t *testing.T) {
		node := &MTTNode{MTT: filepath.Join(t.TempDir(), "mtt"), Role: RoleWorker}

		if _, err := node.Status(context.Background()); !errors.Is(err, os.ErrNotExist) {
			t.Errorf("Status = %v, want that the command does not exist", err)
		}
	})
	for name, stdout := range map[string]string{
		"output_is_not_json": "Checking for a newer version...",
		"no_state":           `{"busy": false}`,
		"unknown_state":      `{"state": "STARTING", "busy": false}`,
	} {
		t.Run(name, func(t *testing.T) {
			node, _ := newFakeMTT(t, RoleWorker, stdout)

			if st, err := node.Status(context.Background()); err == nil {
				t.Errorf("Status = %+v, nil, want an error", st)
			}
		})
	}
}

func TestMTTNode_StartUpgrade(t *testing.T) {
	const build = "asia-docker.pkg.dev/p/r/img@sha256:abc"
	tests := []struct {
		name         string
		role         string
		drain        bool
		drainTimeout time.Duration
		want         []string
	}{
		{
			name: "no_drain",
			role: RoleController,
			want: []string{"node", "controller", "core", "upgrade", "--image_ref=" + build, "--enable_rollback"},
		},
		{
			name:  "drain",
			role:  RoleWorker,
			drain: true,
			want:  []string{"node", "worker", "core", "upgrade", "--image_ref=" + build, "--enable_rollback", "--drain"},
		},
		{
			name:         "drain_with_timeout",
			role:         RoleWorker,
			drain:        true,
			drainTimeout: 90 * time.Minute,
			want:         []string{"node", "worker", "core", "upgrade", "--image_ref=" + build, "--enable_rollback", "--drain", "--drain_timeout=5400"},
		},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			node, recorded := newFakeMTT(t, tc.role, "")
			node.Drain, node.DrainTimeout = tc.drain, tc.drainTimeout

			if err := node.StartUpgrade(context.Background(), build); err != nil {
				t.Fatalf("StartUpgrade failed: %v", err)
			}

			run := readRun(t, recorded)
			// A session of its own is what lets the upgrade outlive the agent.
			if run[0] != "leader" {
				t.Errorf("the upgrade runs as a %s of its process group, want its leader", run[0])
			}
			if diff := cmp.Diff(tc.want, run[1:]); diff != "" {
				t.Errorf("arguments of mtt differ (-want +got):\n%s", diff)
			}
		})
	}
}

func TestMTTNode_StartUpgrade_CommandDoesNotExist(t *testing.T) {
	node := &MTTNode{MTT: filepath.Join(t.TempDir(), "mtt"), Role: RoleWorker}

	if err := node.StartUpgrade(context.Background(), "build"); !errors.Is(err, os.ErrNotExist) {
		t.Errorf("StartUpgrade = %v, want that the command does not exist", err)
	}
}
