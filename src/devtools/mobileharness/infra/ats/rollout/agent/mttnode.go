package agent

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"os/exec"
	"strconv"
	"syscall"
	"time"

	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/record"
)

// The roles of a node, as the mtt command names them.
const (
	RoleWorker     = "worker"
	RoleController = "controller"
)

// statusWaitDelay is how long Status waits for the output of a status
// command that it has killed at its deadline. A command that hangs on
// Docker can leave a child behind that keeps the output open.
const statusWaitDelay = 5 * time.Second

// MTTNode is the Node of a host that the "mtt node" commands manage. It runs
// them as the user of the agent.
type MTTNode struct {
	// MTT is the path of the mtt command.
	MTT string
	// Role is the role of the node, RoleWorker or RoleController: the
	// commands are "mtt node <role> ...".
	Role string
	// Drain makes an upgrade wait for the tests of the host to end before it
	// stops the core service. DrainTimeout, if not zero, is the longest that
	// it waits. Both are the choice of the owner of the host.
	Drain        bool
	DrainTimeout time.Duration
}

// nodeStatus is what the agent reads of the JSON that "mtt node <role>
// status" prints. The rest is for people.
type nodeStatus struct {
	State          string `json:"state"`
	Busy           bool   `json:"busy"`
	ActiveRevision struct {
		Build               string `json:"build"`
		TenantConfigVersion string `json:"tenant_config_version"`
		PoolConfigVersion   string `json:"pool_config_version"`
	} `json:"active_revision"`
}

// Status implements Node. It runs "mtt node <role> status", which ends when
// ctx does at the latest.
func (n *MTTNode) Status(ctx context.Context) (Status, error) {
	cmd := exec.CommandContext(ctx, n.MTT, "node", n.Role, "status")
	cmd.WaitDelay = statusWaitDelay
	out, err := cmd.Output()
	if err != nil {
		var exit *exec.ExitError
		if errors.As(err, &exit) {
			return Status{}, fmt.Errorf("%s: %w: %s", cmd, err, bytes.TrimSpace(exit.Stderr))
		}
		return Status{}, fmt.Errorf("%s: %w", cmd, err)
	}
	var st nodeStatus
	if err := json.Unmarshal(out, &st); err != nil {
		return Status{}, fmt.Errorf("%s: the output is not the status of a node: %w", cmd, err)
	}
	if st.State != "RUNNING" && st.State != "DOWN" {
		return Status{}, fmt.Errorf("%s: state is %q, want RUNNING or DOWN", cmd, st.State)
	}
	return Status{
		Running: st.State == "RUNNING",
		Busy:    st.Busy,
		Active: record.Revision{
			Build:               st.ActiveRevision.Build,
			TenantConfigVersion: st.ActiveRevision.TenantConfigVersion,
			PoolConfigVersion:   st.ActiveRevision.PoolConfigVersion,
		},
	}, nil
}

// StartUpgrade implements Node. It starts "mtt node <role> core upgrade" to
// build, with a rollback if the new core does not come up.
//
// The upgrade runs in a session of its own, with no input and no output, so
// that it outlives the agent: the node records what the upgrade did, and the
// agent reads the result from the status of the node.
func (n *MTTNode) StartUpgrade(_ context.Context, build string) error {
	args := []string{"node", n.Role, "core", "upgrade", "--image_ref=" + build, "--enable_rollback"}
	if n.Drain {
		args = append(args, "--drain")
		if n.DrainTimeout > 0 {
			args = append(args, "--drain_timeout="+strconv.Itoa(int(n.DrainTimeout.Seconds())))
		}
	}
	cmd := exec.Command(n.MTT, args...)
	cmd.SysProcAttr = &syscall.SysProcAttr{Setsid: true}
	if err := cmd.Start(); err != nil {
		return fmt.Errorf("%s: %w", cmd, err)
	}
	// Nothing reads the exit status; the wait only lets the system forget
	// the process once it has ended.
	go cmd.Wait()
	return nil
}
