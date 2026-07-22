
package rbeclient

import (
	"context"
	"fmt"

	"google3/identity/cloud/gaia/client/go/credentialrefresher"
	"google3/net/base/go/health"
	"google3/security/corplogin/go/ssogoauth2"
	"google3/security/loas/go/loas"
	"github.com/bazelbuild/remote-apis-sdks/go/pkg/client"
	"google3/third_party/golang/oauth2/oauth2"
)

type perRPCCreds struct {
	r  *credentialrefresher.CredentialRefresher
	ts oauth2.TokenSource
}

func (creds *perRPCCreds) GetRequestMetadata(ctx context.Context, uri ...string) (map[string]string, error) {
	var t string
	var err error
	if creds.ts != nil {
		var tok *oauth2.Token
		tok, err = creds.ts.Token()
		if err == nil {
			t = tok.AccessToken
		}
	} else if creds.r != nil {
		t, err = creds.r.AccessToken(ctx)
	} else {
		return nil, fmt.Errorf("no credential provider configured")
	}

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

func borgServiceAccountPerRPCCreds(ctx context.Context) (*client.PerRPCCreds, error) {
	user := loas.Self().User
	account := credentialrefresher.BorgServiceAccountEmail(user)

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

func corpAuthPerRPCCreds(ctx context.Context) (*client.PerRPCCreds, error) {
	ts, err := ssogoauth2.TokenSourceForCurrentUser([]string{"https://www.googleapis.com/auth/cloud-platform"})
	if err != nil {
		return nil, fmt.Errorf("unable to create CorpLogin token source: %w", err)
	}
	return &client.PerRPCCreds{
		Creds: &perRPCCreds{ts: ts},
	}, nil
}
