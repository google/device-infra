// Package auth gets Google credentials for the rollout binaries, with
// golang.org/x/oauth2: the application default credentials, or those of a
// service account key file.
//
// A binary picks the source, with Default or FromServiceAccountFile, and
// gets a Credentials either way. A client for a Google API endpoint takes
// Credentials.Client, which sends the access token as a bearer token. The
// registry client, which attaches the token in a form of its own, takes
// Credentials.Token.
//
// This is the only package of the rollout system that imports an auth
// library.
package auth

import (
	"context"
	"errors"
	"fmt"
	"net/http"
	"os"
	"time"

	"golang.org/x/oauth2/google"
	"golang.org/x/oauth2"
)

// OAuth scopes for the access tokens.
const (
	// ScopeCloudPlatform covers every Google Cloud API. The domain manager
	// asks for it, so that one token serves Firestore, Parameter Manager,
	// and Artifact Registry; Parameter Manager takes no narrower scope.
	ScopeCloudPlatform = "https://www.googleapis.com/auth/cloud-platform"
	// ScopeDatastore covers Firestore, the only API the agent calls.
	ScopeDatastore = "https://www.googleapis.com/auth/datastore"
)

// tokenTimeout bounds one token exchange with Google's OAuth endpoint. The
// library makes the exchange without the context of the request that needs
// the token, so nothing else bounds it. Its requests to the metadata server
// have a timeout of their own.
const tokenTimeout = 10 * time.Second

// Credentials is the identity of a rollout binary: the project it belongs to
// and the two ways to authenticate as it. Both use the same access tokens,
// which the library fetches when first needed and keeps until they are about
// to expire. Both are safe for concurrent use, also with each other.
type Credentials struct {
	// Project is the ID of the Google Cloud project of the identity.
	Project string

	// Client sends every request with the access token as a bearer token.
	// It is for clients of a Google API endpoint, such as firestore.New and
	// parametermanager.New. It does not follow redirects, so that the token
	// goes to no host that the caller did not name.
	Client *http.Client

	// Token returns an access token. It is for a client that attaches the
	// token itself; registry.New is the only one.
	//
	// It takes no context, because the library's token source takes none: a
	// call that has to fetch a token returns when the fetch does, within
	// tokenTimeout or the timeout of the metadata client. A caller with a
	// deadline checks it before the call.
	Token func() (string, error)
}

// Default returns the application default credentials, with access tokens
// for the given scopes. On Cloud Run and on a Compute Engine VM those are
// the credentials of the service account it runs as, and its project, from
// the metadata server.
//
// A credentials file comes first if there is one, and it must name its
// project. A service account key file that GOOGLE_APPLICATION_CREDENTIALS
// names does: that is how a binary is run by hand on a workstation. The
// login that "gcloud auth application-default login" leaves in the home
// directory does not (it is an "authorized_user" file, with a
// quota_project_id and no project_id), and Default returns an error for it.
//
// It takes no context: the library keeps the context it is given for as long
// as the credentials are used, so it gets one that never ends.
func Default(scopes ...string) (*Credentials, error) {
	creds, err := google.FindDefaultCredentials(libraryContext(), scopes...)
	if err != nil {
		return nil, fmt.Errorf("auth: finding the default credentials: %w", err)
	}
	return newCredentials(creds)
}

// FromServiceAccountFile returns the credentials of the service account
// whose JSON key is in the file at path, with access tokens for the given
// scopes. It makes no request: a key that Google does not accept shows only
// when the first token is fetched.
//
// It takes no context, for the same reason as Default.
func FromServiceAccountFile(path string, scopes ...string) (*Credentials, error) {
	key, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("auth: reading the service account key: %w", err)
	}
	creds, err := google.CredentialsFromJSONWithType(libraryContext(), key, google.ServiceAccount, scopes...)
	if err != nil {
		return nil, fmt.Errorf("auth: service account key %q: %w", path, err)
	}
	return newCredentials(creds)
}

// libraryContext returns the context that the library gets and keeps. It
// never ends, and it carries the HTTP client for the token exchanges.
func libraryContext() context.Context {
	return context.WithValue(context.Background(), oauth2.HTTPClient, &http.Client{Timeout: tokenTimeout})
}

// newCredentials returns the credentials that the library found, in the form
// the rollout clients take.
func newCredentials(creds *google.Credentials) (*Credentials, error) {
	if creds.ProjectID == "" {
		return nil, errors.New("auth: the credentials name no project")
	}
	// One token source serves both ways. The library hands out the source of
	// a credentials file wrapped, so that oauth2.NewClient would not
	// recognize it and put a second token cache, with a lock of its own,
	// around it; the two would then race on the token they share.
	ts := oauth2.ReuseTokenSource(nil, creds.TokenSource)
	// The client gets the background context, not libraryContext: its
	// requests are bounded by their own contexts, not by the timeout of a
	// token exchange.
	client := oauth2.NewClient(context.Background(), ts)
	// The library's transport adds the token to every request it is given,
	// a redirected one included, so redirects are not followed: the caller
	// gets the redirect response itself.
	client.CheckRedirect = func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse }
	return &Credentials{
		Project: creds.ProjectID,
		Client:  client,
		Token: func() (string, error) {
			t, err := ts.Token()
			if err != nil {
				return "", fmt.Errorf("auth: getting an access token: %w", err)
			}
			return t.AccessToken, nil
		},
	}, nil
}
