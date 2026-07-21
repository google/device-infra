
package rbeclient

import (
	"context"
	"fmt"

	"google3/identity/cloud/gaia/client/go/credentialrefresher"
	"google3/net/base/go/health"
	"google3/security/loas/go/loas"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/client"
)

type perRPCCreds struct {
	r *credentialrefresher.CredentialRefresher
}

func (creds *perRPCCreds) GetRequestMetadata(ctx context.Context, uri ...string) (map[string]string, error) {
	t, err := creds.r.AccessToken(ctx)
	if err != nil {
		return nil, err
	}
	return map[string]string{
		"authorization": "Bearer " + t,
	}, nil
}

func (creds *perRPCCreds) RequireTransportSecurity() bool {
	return true
}

func borgServiceAccountPerRPCCreds(ctx context.Context, useBorgServiceAccount bool) (*client.PerRPCCreds, error) {
	if !useBorgServiceAccount {
		return nil, nil
	}
	account := credentialrefresher.BorgServiceAccountEmail(loas.Self().User)
	r, err := credentialrefresher.New("cloud-gaia", account, []string{"https://www.googleapis.com/auth/cloud-platform"})
	if err != nil {
		return nil, fmt.Errorf("unable to create a Cloud Gaia credential refresher: %w", err)
	}
	if err := health.Await(ctx, r.HealthWatcher(), nil); err != nil {
		return nil, fmt.Errorf("unable to wait for Cloud Gaia credential refresher: %w", err)
	}
	return &client.PerRPCCreds{
		Creds: &perRPCCreds{r: r},
	}, nil
}
