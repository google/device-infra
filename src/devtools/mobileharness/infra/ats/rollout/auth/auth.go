// Package auth gets Google credentials for the rollout binaries, from the
// metadata server of a Cloud Run job or a Compute Engine VM, or from a
// service account key file on a host outside Google Cloud.
//
// Where the credentials come from and how a client attaches them are kept
// apart. A binary picks the source, with FromMetadata or
// FromServiceAccountFile, and gets a Credentials either way. A client for a
// fixed Google API endpoint takes Credentials.Client, which sends the access
// token as a bearer token. A client that must decide for itself whether, and
// in what form, a host gets the token takes Credentials.Token; the registry
// client is the only one.
//
// This is the only package of the rollout system that imports an auth
// library.
package auth

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"slices"
	"strings"
	"sync"
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

const (
	// metadataEndpoint is the metadata server of a Cloud Run job or a Compute
	// Engine VM, by its address and not by its name, metadata.google.internal.
	// The address needs no DNS, which a VM with a resolver of its own may not
	// have for that name. It is also link-local, so on a host outside Google
	// Cloud the request stays on the host's own link and no resolver is asked
	// for the name. Another cloud may answer at this address; get refuses an
	// answer that does not come from a Google metadata server.
	//
	// It is not configurable, and no environment variable changes it; tests
	// pass another endpoint to fromMetadata.
	metadataEndpoint = "http://169.254.169.254"

	// fetchTimeout bounds one request for a token or for the project ID.
	fetchTimeout = 10 * time.Second

	// refreshBefore is how long before a token expires it stops being handed
	// out. It is the margin that golang.org/x/oauth2 uses, and it is far
	// shorter than the time for which a metadata server keeps answering with
	// the same token (minutes), so a new fetch always gets a new token.
	refreshBefore = 10 * time.Second

	// maxResponseBytes bounds how much of a metadata server response is read.
	maxResponseBytes = 1 << 20
)

// Credentials is the identity of a rollout binary: the project it belongs to
// and the two ways to authenticate as it. Both ways use the same access
// tokens, which are fetched when first needed, kept until shortly before
// they expire, and safe for concurrent use.
type Credentials struct {
	// Project is the ID of the Google Cloud project of the identity: the
	// project of the job or the VM, or the one named in the key file.
	Project string

	// Client sends every request with the access token as a bearer token,
	// to whatever host the request names. It is for clients of a fixed
	// Google API endpoint, such as firestore.New and parametermanager.New.
	// It does not follow redirects, so that the token goes to no host that
	// the caller did not name.
	//
	// A request waits for a token no longer than its context allows.
	Client *http.Client

	// Token returns an access token. It is for a client that attaches the
	// token itself; registry.New is the only one.
	//
	// It returns when ctx is done, even if a fetch is still under way.
	Token func(ctx context.Context) (string, error)
}

// FromMetadata returns the credentials of the service account that the
// Cloud Run job or the Compute Engine VM runs as, with access tokens for the
// given scopes. It reads the project ID from the metadata server before it
// returns, bounded by ctx; ctx is not kept.
//
// It asks the metadata server and nothing else. In particular it does not
// look for "application default credentials": those prefer a file named by
// GOOGLE_APPLICATION_CREDENTIALS or left in the home directory by gcloud to
// the metadata server, so a command run with sudo on a VM could pick up the
// operator's own login.
//
// On a Compute Engine VM, the access scopes of the instance decide what a
// token is good for, whatever scopes are asked for here.
func FromMetadata(ctx context.Context, scopes ...string) (*Credentials, error) {
	return fromMetadata(ctx, metadataEndpoint, scopes)
}

// fromMetadata is FromMetadata against the metadata server at endpoint.
func fromMetadata(ctx context.Context, endpoint string, scopes []string) (*Credentials, error) {
	if len(scopes) == 0 {
		return nil, errors.New("auth: no scopes")
	}
	m := &metadataServer{
		endpoint: endpoint,
		// The metadata server is reached directly, never through the proxy
		// that the environment configures for other hosts.
		client: &http.Client{Timeout: fetchTimeout, Transport: &http.Transport{Proxy: nil}},
	}
	project, err := m.get(ctx, "project/project-id")
	if err != nil {
		return nil, fmt.Errorf("auth: reading the project ID from the metadata server: %w", err)
	}
	if project = strings.TrimSpace(project); project == "" {
		return nil, errors.New("auth: the metadata server returned an empty project ID")
	}
	tokenPath := "instance/service-accounts/default/token?" + url.Values{"scopes": {strings.Join(scopes, ",")}}.Encode()
	return newCredentials(project, func(ctx context.Context) (string, time.Time, error) {
		return m.token(ctx, tokenPath)
	}), nil
}

// metadataServer reads values from a metadata server.
type metadataServer struct {
	endpoint string
	client   *http.Client
}

// get returns the metadata value at path, which is relative to
// /computeMetadata/v1/.
func (m *metadataServer) get(ctx context.Context, path string) (string, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, m.endpoint+"/computeMetadata/v1/"+path, nil)
	if err != nil {
		return "", err
	}
	req.Header.Set("Metadata-Flavor", "Google")
	resp, err := m.client.Do(req)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()
	body, err := io.ReadAll(io.LimitReader(resp.Body, maxResponseBytes))
	if err != nil {
		return "", err
	}
	switch {
	case resp.StatusCode != http.StatusOK:
		return "", fmt.Errorf("the server at %s answered %s", m.endpoint, resp.Status)
	case resp.Header.Get("Metadata-Flavor") != "Google":
		// Some other server answers at the metadata server's address, as
		// can happen on a host that is not on Google Cloud.
		return "", fmt.Errorf("the server at %s is not a metadata server: its answer has no \"Metadata-Flavor: Google\" header", m.endpoint)
	}
	return string(body), nil
}

// token fetches an access token and returns it with the time it expires.
// Errors never quote the response, which may hold a token.
func (m *metadataServer) token(ctx context.Context, path string) (string, time.Time, error) {
	body, err := m.get(ctx, path)
	if err != nil {
		return "", time.Time{}, fmt.Errorf("auth: getting an access token from the metadata server: %w", err)
	}
	var t struct {
		AccessToken string `json:"access_token"`
		// ExpiresIn is in seconds.
		ExpiresIn int64 `json:"expires_in"`
	}
	if err := json.Unmarshal([]byte(body), &t); err != nil {
		return "", time.Time{}, errors.New("auth: getting an access token from the metadata server: the answer is not the JSON of a token")
	}
	if t.AccessToken == "" || t.ExpiresIn <= 0 {
		return "", time.Time{}, errors.New("auth: getting an access token from the metadata server: the answer has no token or no lifetime")
	}
	return t.AccessToken, time.Now().Add(time.Duration(t.ExpiresIn) * time.Second), nil
}

// FromServiceAccountFile returns the credentials of the service account
// whose JSON key is in the file at path, with access tokens for the given
// scopes. It makes no request: a key that Google does not accept shows only
// when the first token is fetched.
//
// The file must be a service account key and name its project. Any other
// kind of credentials file is refused, whatever it would give access to.
//
// It reads the file once. A key that is replaced on disk takes effect when
// the process restarts.
//
// It takes no context, and none outlives it: each later token fetch runs
// under a context and a timeout of its own, never under that of the caller's
// start-up.
func FromServiceAccountFile(path string, scopes ...string) (*Credentials, error) {
	k, project, err := readKeyFile(path, scopes)
	if err != nil {
		return nil, err
	}
	return newCredentials(project, k.token), nil
}

// keyFile exchanges the key of a service account for access tokens.
type keyFile struct {
	// path is where the key was read from. It is for error messages only.
	path   string
	key    []byte
	scopes []string
	// client sends the exchanges. Its timeout bounds each one.
	client *http.Client
}

// readKeyFile reads the service account key in the file at path, and
// returns it with the ID of the project it names. It returns an error for a
// file that is not a service account key or names no project.
func readKeyFile(path string, scopes []string) (k *keyFile, project string, err error) {
	if len(scopes) == 0 {
		return nil, "", errors.New("auth: no scopes")
	}
	key, err := os.ReadFile(path)
	if err != nil {
		return nil, "", fmt.Errorf("auth: reading the service account key: %w", err)
	}
	k = &keyFile{
		path:   path,
		key:    key,
		scopes: slices.Clone(scopes),
		client: &http.Client{Timeout: fetchTimeout},
	}
	creds, err := k.load(context.Background())
	if err != nil {
		return nil, "", err
	}
	if creds.ProjectID == "" {
		return nil, "", fmt.Errorf("auth: service account key %q has no project_id", path)
	}
	return k, creds.ProjectID, nil
}

// load returns the credentials of the key, whose token source sends its
// requests with k.client.
func (k *keyFile) load(ctx context.Context) (*google.Credentials, error) {
	ctx = context.WithValue(ctx, oauth2.HTTPClient, k.client)
	creds, err := google.CredentialsFromJSONWithType(ctx, k.key, google.ServiceAccount, k.scopes...)
	if err != nil {
		// The error names what is wrong with the file; it does not quote it.
		return nil, fmt.Errorf("auth: service account key %q: %w", k.path, err)
	}
	return creds, nil
}

// token exchanges the key for a new access token and returns it with the
// time it expires. Every call is a request.
//
// It loads the key anew for each exchange and uses the token source once.
// A token source of the library that is kept keeps two things with it: the
// last token, which it hands out again for as long as its own reading of the
// clock allows, and the context it was built with, which it uses for every
// later request. Used once, it does neither, and tokenCache is the only
// place where a token is kept and where its expiry is judged.
func (k *keyFile) token(ctx context.Context) (string, time.Time, error) {
	creds, err := k.load(ctx)
	if err != nil {
		return "", time.Time{}, err
	}
	// Token takes no context, and of the one given to load the library uses
	// the HTTP client alone: it sends the request without a context. So the
	// client's timeout, which equals that of ctx, is what bounds the request.
	t, err := creds.TokenSource.Token()
	if err != nil {
		return "", time.Time{}, fmt.Errorf("auth: getting an access token for service account key %q: %w", k.path, err)
	}
	// The library leaves the expiry zero for an answer that has no lifetime,
	// or one that is not positive, and treats such a token as good forever.
	if t.AccessToken == "" || !t.Expiry.After(time.Now()) {
		return "", time.Time{}, fmt.Errorf("auth: getting an access token for service account key %q: the answer has no token or no lifetime", k.path)
	}
	return t.AccessToken, t.Expiry, nil
}

// newCredentials returns the credentials of the given project whose access
// tokens come from fetch.
func newCredentials(project string, fetch fetchFunc) *Credentials {
	tokens := &tokenCache{fetch: fetch}
	return &Credentials{
		Project: project,
		Client: &http.Client{
			Transport: &bearerTransport{token: tokens.get, base: http.DefaultTransport},
			// The transport adds the token to every request it is given, a
			// redirected one included, so redirects are not followed: the
			// caller gets the redirect response itself.
			CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse },
		},
		Token: tokens.get,
	}
}

// bearerTransport sends each request with an access token as a bearer token.
type bearerTransport struct {
	token func(context.Context) (string, error)
	base  http.RoundTripper
}

// RoundTrip implements http.RoundTripper. Unlike the transport of
// golang.org/x/oauth2, it gets the token under the context of the request,
// so a fetch that stalls costs the request no more than its own deadline.
func (t *bearerTransport) RoundTrip(req *http.Request) (*http.Response, error) {
	token, err := t.token(req.Context())
	if err != nil {
		// A RoundTripper closes the body even when it sends nothing.
		if req.Body != nil {
			req.Body.Close()
		}
		return nil, err
	}
	// A RoundTripper must not change the request it is given.
	req = req.Clone(req.Context())
	req.Header.Set("Authorization", "Bearer "+token)
	return t.base.RoundTrip(req)
}

// fetchFunc fetches a new access token with a request of its own, and
// returns it with the time it expires, which is in the future. It keeps no
// token itself.
type fetchFunc func(ctx context.Context) (token string, expiry time.Time, err error)

// tokenCache hands out one access token until shortly before it expires,
// and then fetches the next. It is safe for concurrent use. It is the only
// place where a token is kept, whichever source the tokens come from.
//
// Callers that need a new token at the same time share one fetch. The fetch
// runs under no caller's context: a caller whose context ends stops waiting,
// while the fetch goes on, bounded by fetchTimeout, and its token serves
// whoever asks next.
type tokenCache struct {
	fetch fetchFunc

	mu    sync.Mutex
	token string
	// expiry is when token expires, as a wall-clock time alone: see run.
	expiry time.Time
	// fetching is the fetch under way, if any.
	fetching *fetchResult
}

// fetchResult is the outcome of one fetch. token and err are set before
// done is closed, and not changed after.
type fetchResult struct {
	done  chan struct{}
	token string
	err   error
}

// get returns an access token, fetching one if the token it holds is
// missing or about to expire. It returns the error of ctx once ctx is done.
func (c *tokenCache) get(ctx context.Context) (string, error) {
	if err := ctx.Err(); err != nil {
		return "", fmt.Errorf("auth: getting an access token: %w", err)
	}
	c.mu.Lock()
	if c.token != "" && time.Until(c.expiry) > refreshBefore {
		token := c.token
		c.mu.Unlock()
		return token, nil
	}
	f := c.fetching
	if f == nil {
		f = &fetchResult{done: make(chan struct{})}
		c.fetching = f
		go c.run(f)
	}
	c.mu.Unlock()

	select {
	case <-f.done:
		return f.token, f.err
	case <-ctx.Done():
		return "", fmt.Errorf("auth: getting an access token: %w", ctx.Err())
	}
}

// run fetches a token, keeps it if the fetch succeeded, and wakes the
// callers that wait on f.
func (c *tokenCache) run(f *fetchResult) {
	ctx, cancel := context.WithTimeout(context.Background(), fetchTimeout)
	defer cancel()
	token, expiry, err := c.fetch(ctx)

	c.mu.Lock()
	if err == nil {
		// Round drops the monotonic clock reading that a time made from
		// time.Now carries, so that get measures the time left by the wall
		// clock, which is the clock the expiry is on. The monotonic clock
		// does not follow a wall clock that is set, and may not count the
		// time a machine is suspended; measured by it, a token would still
		// be handed out after it has expired.
		c.token, c.expiry = token, expiry.Round(0)
	}
	c.fetching = nil
	c.mu.Unlock()

	f.token, f.err = token, err
	close(f.done)
}
