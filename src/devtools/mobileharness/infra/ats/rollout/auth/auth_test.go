package auth

import (
	"context"
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
	"encoding/base64"
	"encoding/json"
	"encoding/pem"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/google/go-cmp/cmp"
)

// longLife and shortLife are token lifetimes in seconds: one far above
// refreshBefore, so the token is kept, and one below it, so that the next
// call fetches again.
const (
	longLife  = 3600
	shortLife = 5
)

// giveUp is the deadline of a caller that is not going to get a token, and
// tooLong is how long such a caller may take to return: far less than
// fetchTimeout, which is what bounds the fetch it gave up on.
const (
	giveUp  = 50 * time.Millisecond
	tooLong = 5 * time.Second
)

// hold makes a fake server keep its token requests waiting until release is
// called, which the server's cleanup does at the latest. A nil hold keeps
// nothing waiting.
type hold struct {
	once sync.Once
	ch   chan struct{}
}

func newHold() *hold { return &hold{ch: make(chan struct{})} }

func (h *hold) release() {
	if h != nil {
		h.once.Do(func() { close(h.ch) })
	}
}

// wait returns when the hold is released or the client has gone away.
func (h *hold) wait(r *http.Request) {
	if h == nil {
		return
	}
	select {
	case <-h.ch:
	case <-r.Context().Done():
	}
}

// fakeMetadata is a metadata server. It answers the project ID and token
// requests, and records every request.
type fakeMetadata struct {
	// project and projectStatus are the answer for the project ID; a zero
	// status means 200.
	project       string
	projectStatus int
	// lifetime is the expires_in of the tokens, which are "token-1",
	// "token-2", and so on. tokenBody, if set, is the answer instead, and
	// tokenStatus its status; a zero status means 200.
	lifetime    int
	tokenBody   string
	tokenStatus int
	// noFlavor leaves out the header that marks a metadata server's answers.
	noFlavor bool
	// hold, if set, keeps token requests waiting.
	hold *hold

	mu sync.Mutex
	// got is each request as "<Metadata-Flavor header> <path>?<query>".
	got    []string
	tokens int
}

func (f *fakeMetadata) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	f.mu.Lock()
	f.got = append(f.got, r.Header.Get("Metadata-Flavor")+" "+r.URL.RequestURI())
	f.mu.Unlock()
	if !f.noFlavor {
		w.Header().Set("Metadata-Flavor", "Google")
	}
	status, body := http.StatusNotFound, "not found"
	switch r.URL.Path {
	case "/computeMetadata/v1/project/project-id":
		status, body = f.projectStatus, f.project
	case "/computeMetadata/v1/instance/service-accounts/default/token":
		f.hold.wait(r)
		f.mu.Lock()
		f.tokens++
		n := f.tokens
		f.mu.Unlock()
		status, body = f.tokenStatus, f.tokenBody
		if body == "" {
			body = fmt.Sprintf(`{"access_token": "token-%d", "expires_in": %d, "token_type": "Bearer"}`, n, f.lifetime)
		}
	}
	if status == 0 {
		status = http.StatusOK
	}
	w.WriteHeader(status)
	io.WriteString(w, body)
}

func (f *fakeMetadata) requests() []string {
	f.mu.Lock()
	defer f.mu.Unlock()
	return append([]string(nil), f.got...)
}

// tokenRequests returns how many token requests the server has answered or
// is holding.
func (f *fakeMetadata) tokenRequests() int {
	n := 0
	for _, r := range f.requests() {
		if strings.Contains(r, "/token") {
			n++
		}
	}
	return n
}

// serve starts f and returns its endpoint.
func (f *fakeMetadata) serve(t *testing.T) string {
	t.Helper()
	ts := httptest.NewServer(f)
	t.Cleanup(ts.Close)
	// Registered after the server's Close, so that it runs before it: Close
	// waits for the requests that are held.
	t.Cleanup(f.hold.release)
	return ts.URL
}

// metadataCredentials returns credentials from f for one scope.
func metadataCredentials(t *testing.T, f *fakeMetadata) *Credentials {
	t.Helper()
	creds, err := fromMetadata(context.Background(), f.serve(t), []string{ScopeDatastore})
	if err != nil {
		t.Fatalf("fromMetadata() failed: %v", err)
	}
	return creds
}

func TestFromMetadata(t *testing.T) {
	f := &fakeMetadata{project: "the-project\n", lifetime: longLife}
	creds, err := fromMetadata(context.Background(), f.serve(t), []string{ScopeCloudPlatform, ScopeDatastore})
	if err != nil {
		t.Fatalf("fromMetadata() failed: %v", err)
	}

	if creds.Project != "the-project" {
		t.Errorf("fromMetadata() project = %q, want %q", creds.Project, "the-project")
	}
	// The second token is the first one, kept.
	for range 2 {
		got, err := creds.Token(context.Background())
		if got != "token-1" || err != nil {
			t.Errorf("Token() = %q, %v, want %q", got, err, "token-1")
		}
	}
	want := []string{
		"Google /computeMetadata/v1/project/project-id",
		"Google /computeMetadata/v1/instance/service-accounts/default/token?scopes=https%3A%2F%2Fwww.googleapis.com%2Fauth%2Fcloud-platform%2Chttps%3A%2F%2Fwww.googleapis.com%2Fauth%2Fdatastore",
	}
	if diff := cmp.Diff(want, f.requests()); diff != "" {
		t.Errorf("requests to the metadata server (-want +got):\n%s", diff)
	}
}

func TestFromMetadataErrors(t *testing.T) {
	tests := []struct {
		name   string
		server *fakeMetadata
		scopes []string
	}{
		{name: "no scopes", server: &fakeMetadata{project: "p"}},
		{name: "the server fails", server: &fakeMetadata{project: "p", projectStatus: http.StatusInternalServerError}, scopes: []string{ScopeDatastore}},
		{name: "no project ID", server: &fakeMetadata{projectStatus: http.StatusNotFound}, scopes: []string{ScopeDatastore}},
		{name: "an empty project ID", server: &fakeMetadata{project: " \n"}, scopes: []string{ScopeDatastore}},
		{name: "not a metadata server", server: &fakeMetadata{project: "<html>", noFlavor: true}, scopes: []string{ScopeDatastore}},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			creds, err := fromMetadata(context.Background(), tc.server.serve(t), tc.scopes)
			if err == nil {
				t.Errorf("fromMetadata() = %+v, want an error", creds)
			}
		})
	}
}

func TestFromMetadataIsBoundedByItsContext(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		<-r.Context().Done()
	}))
	t.Cleanup(ts.Close)
	ctx, cancel := context.WithTimeout(context.Background(), giveUp)
	defer cancel()

	_, err := fromMetadata(ctx, ts.URL, []string{ScopeDatastore})

	if !errors.Is(err, context.DeadlineExceeded) {
		t.Errorf("fromMetadata() against a server that does not answer returned error %v, want %v", err, context.DeadlineExceeded)
	}
}

func TestMetadataTokenErrors(t *testing.T) {
	tests := []struct {
		name   string
		server *fakeMetadata
	}{
		{name: "the server fails", server: &fakeMetadata{tokenStatus: http.StatusInternalServerError}},
		{name: "no service account", server: &fakeMetadata{tokenStatus: http.StatusNotFound}},
		{name: "not JSON", server: &fakeMetadata{tokenBody: "the-secret"}},
		{name: "no token", server: &fakeMetadata{tokenBody: `{"expires_in": 3600}`}},
		{name: "no lifetime", server: &fakeMetadata{tokenBody: `{"access_token": "the-secret"}`}},
		{name: "a lifetime in the past", server: &fakeMetadata{tokenBody: `{"access_token": "the-secret", "expires_in": -1}`}},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			tc.server.project = "p"
			creds := metadataCredentials(t, tc.server)

			got, err := creds.Token(context.Background())

			if got != "" || err == nil {
				t.Fatalf("Token() = %q, %v, want an error and no token", got, err)
			}
			if strings.Contains(err.Error(), "the-secret") {
				t.Errorf("Token() error quotes the answer of the metadata server: %v", err)
			}
			// A failure is not kept: the next call asks again.
			if _, err := creds.Token(context.Background()); err == nil {
				t.Errorf("second Token() returned no error, want one")
			}
			if n := tc.server.tokenRequests(); n != 2 {
				t.Errorf("two Token() calls made %d token requests, want 2", n)
			}
		})
	}
}

func TestTokenIsFetchedAgainWhenAboutToExpire(t *testing.T) {
	creds := metadataCredentials(t, &fakeMetadata{project: "p", lifetime: shortLife})

	for _, want := range []string{"token-1", "token-2"} {
		got, err := creds.Token(context.Background())
		if got != want || err != nil {
			t.Errorf("Token() = %q, %v, want %q", got, err, want)
		}
	}
}

func TestConcurrentCallersShareOneFetch(t *testing.T) {
	f := &fakeMetadata{project: "p", lifetime: longLife, hold: newHold()}
	creds := metadataCredentials(t, f)
	const callers = 8
	type result struct {
		token string
		err   error
	}
	results := make(chan result, callers)
	var started sync.WaitGroup
	for range callers {
		started.Add(1)
		go func() {
			started.Done()
			token, err := creds.Token(context.Background())
			results <- result{token, err}
		}()
	}
	// Every caller asks while the first fetch is held. The pause gives the
	// callers time to get from Done to Token; one that is slower still gets
	// the token, and could only hide a second fetch, not cause a failure.
	started.Wait()
	time.Sleep(giveUp)

	f.hold.release()

	for range callers {
		if r := <-results; r.token != "token-1" || r.err != nil {
			t.Errorf("Token() = %q, %v, want %q", r.token, r.err, "token-1")
		}
	}
	if n := f.tokenRequests(); n != 1 {
		t.Errorf("%d concurrent Token() calls made %d token requests, want 1", callers, n)
	}
}

func TestTokenStopsWaitingWhenItsContextIsDone(t *testing.T) {
	f := &fakeMetadata{project: "p", lifetime: longLife, hold: newHold()}
	creds := metadataCredentials(t, f)
	ctx, cancel := context.WithTimeout(context.Background(), giveUp)
	defer cancel()
	start := time.Now()

	got, err := creds.Token(ctx)

	if got != "" || !errors.Is(err, context.DeadlineExceeded) {
		t.Errorf("Token() while the fetch stalls = %q, %v, want error %v", got, err, context.DeadlineExceeded)
	}
	if elapsed := time.Since(start); elapsed > tooLong {
		t.Errorf("Token() with a deadline of %v returned after %v", giveUp, elapsed)
	}

	// The fetch that the caller gave up on goes on, and its token serves
	// the next caller.
	f.hold.release()
	if got, err := creds.Token(context.Background()); got != "token-1" || err != nil {
		t.Errorf("Token() after the fetch went through = %q, %v, want %q", got, err, "token-1")
	}
	if n := f.tokenRequests(); n != 1 {
		t.Errorf("made %d token requests, want 1", n)
	}
}

func TestTokenWithADoneContextFetchesNothing(t *testing.T) {
	f := &fakeMetadata{project: "p", lifetime: longLife}
	creds := metadataCredentials(t, f)
	ctx, cancel := context.WithCancel(context.Background())
	cancel()

	got, err := creds.Token(ctx)

	if got != "" || !errors.Is(err, context.Canceled) {
		t.Errorf("Token() = %q, %v, want error %v", got, err, context.Canceled)
	}
	if n := f.tokenRequests(); n != 0 {
		t.Errorf("Token() with a done context made %d token requests, want 0", n)
	}
}

// fakeAPI is an API server that records the Authorization header of each
// request and answers with status, or redirects to redirect if set.
type fakeAPI struct {
	status   int
	redirect string

	mu  sync.Mutex
	got []string
}

func (f *fakeAPI) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	f.mu.Lock()
	f.got = append(f.got, r.Header.Get("Authorization"))
	f.mu.Unlock()
	if f.redirect != "" {
		http.Redirect(w, r, f.redirect, http.StatusFound)
		return
	}
	w.WriteHeader(f.status)
}

func (f *fakeAPI) authorizations() []string {
	f.mu.Lock()
	defer f.mu.Unlock()
	return append([]string(nil), f.got...)
}

func (f *fakeAPI) serve(t *testing.T) string {
	t.Helper()
	ts := httptest.NewServer(f)
	t.Cleanup(ts.Close)
	return ts.URL
}

func TestClientSendsTheTokenAsABearerToken(t *testing.T) {
	creds := metadataCredentials(t, &fakeMetadata{project: "p", lifetime: longLife})
	api := &fakeAPI{status: http.StatusOK}
	url := api.serve(t)

	for range 2 {
		resp, err := creds.Client.Post(url, "application/json", strings.NewReader("{}"))
		if err != nil {
			t.Fatalf("Client.Post() failed: %v", err)
		}
		resp.Body.Close()
	}

	want := []string{"Bearer token-1", "Bearer token-1"}
	if diff := cmp.Diff(want, api.authorizations()); diff != "" {
		t.Errorf("Authorization headers the API saw (-want +got):\n%s", diff)
	}
}

func TestClientLeavesTheCallersRequestAlone(t *testing.T) {
	creds := metadataCredentials(t, &fakeMetadata{project: "p", lifetime: longLife})
	api := &fakeAPI{status: http.StatusOK}
	req, err := http.NewRequest(http.MethodGet, api.serve(t), nil)
	if err != nil {
		t.Fatal(err)
	}

	resp, err := creds.Client.Do(req)
	if err != nil {
		t.Fatalf("Client.Do() failed: %v", err)
	}
	resp.Body.Close()

	if got := req.Header.Get("Authorization"); got != "" {
		t.Errorf("Client.Do() set Authorization %q on the caller's request, want it unchanged", got)
	}
}

func TestClientDoesNotFollowRedirects(t *testing.T) {
	creds := metadataCredentials(t, &fakeMetadata{project: "p", lifetime: longLife})
	elsewhere := &fakeAPI{status: http.StatusOK}
	api := &fakeAPI{redirect: elsewhere.serve(t)}

	resp, err := creds.Client.Get(api.serve(t))
	if err != nil {
		t.Fatalf("Client.Get() failed: %v", err)
	}
	resp.Body.Close()

	if resp.StatusCode != http.StatusFound {
		t.Errorf("Client.Get() of a URL that redirects returned status %d, want the redirect itself, %d", resp.StatusCode, http.StatusFound)
	}
	if got := elsewhere.authorizations(); len(got) != 0 {
		t.Errorf("the host redirected to got requests with Authorization %q, want no request", got)
	}
}

func TestClientRequestWaitsForATokenNoLongerThanItsContext(t *testing.T) {
	creds := metadataCredentials(t, &fakeMetadata{project: "p", lifetime: longLife, hold: newHold()})
	api := &fakeAPI{status: http.StatusOK}
	ctx, cancel := context.WithTimeout(context.Background(), giveUp)
	defer cancel()
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, api.serve(t), nil)
	if err != nil {
		t.Fatal(err)
	}
	start := time.Now()

	resp, err := creds.Client.Do(req)

	if !errors.Is(err, context.DeadlineExceeded) {
		if err == nil {
			resp.Body.Close()
		}
		t.Errorf("Client.Do() while the token fetch stalls returned error %v, want %v", err, context.DeadlineExceeded)
	}
	if elapsed := time.Since(start); elapsed > tooLong {
		t.Errorf("Client.Do() with a deadline of %v returned after %v", giveUp, elapsed)
	}
	if got := api.authorizations(); len(got) != 0 {
		t.Errorf("the API got requests with Authorization %q, want no request without a token", got)
	}
}

// closeRecorder is a request body that records that it was closed.
type closeRecorder struct {
	io.Reader
	closed bool
}

func (c *closeRecorder) Close() error {
	c.closed = true
	return nil
}

func TestTransportWithoutATokenSendsNothingAndClosesTheBody(t *testing.T) {
	creds := metadataCredentials(t, &fakeMetadata{project: "p", tokenStatus: http.StatusInternalServerError})
	api := &fakeAPI{status: http.StatusOK}
	body := &closeRecorder{Reader: strings.NewReader("{}")}
	req, err := http.NewRequest(http.MethodPost, api.serve(t), body)
	if err != nil {
		t.Fatal(err)
	}

	// The transport is called directly: an http.Client leaves closing the
	// body to its transport, which is what is tested here.
	resp, err := creds.Client.Transport.RoundTrip(req)

	if err == nil {
		resp.Body.Close()
		t.Errorf("RoundTrip() without a token returned no error, want one")
	}
	if !body.closed {
		t.Errorf("RoundTrip() without a token left the request body open, want it closed")
	}
	if got := api.authorizations(); len(got) != 0 {
		t.Errorf("the API got requests with Authorization %q, want no request without a token", got)
	}
}

// privateKey returns a private key in the PEM form of a service account key
// file. The key is generated once for the test binary.
var privateKey = sync.OnceValue(func() string {
	key, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		panic(err)
	}
	der, err := x509.MarshalPKCS8PrivateKey(key)
	if err != nil {
		panic(err)
	}
	return string(pem.EncodeToMemory(&pem.Block{Type: "PRIVATE KEY", Bytes: der}))
})

// claims is what a token request asserts, of the claims the tests check.
type claims struct {
	Iss   string `json:"iss"`
	Scope string `json:"scope"`
	Aud   string `json:"aud"`
}

// fakeTokenEndpoint is the OAuth token endpoint that a service account key
// names. It answers each request with a token, "sa-token-1", "sa-token-2",
// and so on, and records what the requests assert.
type fakeTokenEndpoint struct {
	// lifetime is the expires_in of the tokens.
	lifetime int
	// status, if not zero, is the status of an error answer instead.
	status int
	// hold, if set, keeps the requests waiting.
	hold *hold

	mu  sync.Mutex
	got []claims
	// malformed counts the requests that are not a JWT bearer grant.
	malformed int
}

func (f *fakeTokenEndpoint) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	c, ok := assertedClaims(r)
	f.mu.Lock()
	if ok {
		f.got = append(f.got, c)
	} else {
		f.malformed++
	}
	n := len(f.got)
	f.mu.Unlock()
	f.hold.wait(r)
	w.Header().Set("Content-Type", "application/json")
	if f.status != 0 {
		w.WriteHeader(f.status)
		io.WriteString(w, `{"error": "invalid_grant", "error_description": "from the test"}`)
		return
	}
	fmt.Fprintf(w, `{"access_token": "sa-token-%d", "expires_in": %d, "token_type": "Bearer"}`, n, f.lifetime)
}

// assertedClaims returns the claims of the JWT in a token request, without
// checking its signature. It reports false if the request is not a JWT
// bearer grant.
func assertedClaims(r *http.Request) (claims, bool) {
	if r.Method != http.MethodPost || r.PostFormValue("grant_type") != "urn:ietf:params:oauth:grant-type:jwt-bearer" {
		return claims{}, false
	}
	parts := strings.Split(r.PostFormValue("assertion"), ".")
	if len(parts) != 3 {
		return claims{}, false
	}
	payload, err := base64.RawURLEncoding.DecodeString(parts[1])
	if err != nil {
		return claims{}, false
	}
	var c claims
	if json.Unmarshal(payload, &c) != nil {
		return claims{}, false
	}
	return c, true
}

func (f *fakeTokenEndpoint) requests() (got []claims, malformed int) {
	f.mu.Lock()
	defer f.mu.Unlock()
	return append([]claims(nil), f.got...), f.malformed
}

func (f *fakeTokenEndpoint) serve(t *testing.T) string {
	t.Helper()
	ts := httptest.NewServer(f)
	t.Cleanup(ts.Close)
	// See fakeMetadata.serve for the order.
	t.Cleanup(f.hold.release)
	return ts.URL
}

const serviceAccount = "rollout@the-project.iam.gserviceaccount.com"

// serviceAccountKey returns the fields of a service account key file whose
// tokens come from tokenURL.
func serviceAccountKey(tokenURL string) map[string]any {
	return map[string]any{
		"type":           "service_account",
		"project_id":     "the-project",
		"private_key_id": "key-1",
		"private_key":    privateKey(),
		"client_email":   serviceAccount,
		"token_uri":      tokenURL,
	}
}

// writeFile writes a credentials file with the given content, which is
// marshaled as JSON unless it is a string, and returns its path.
func writeFile(t *testing.T, content any) string {
	t.Helper()
	data, ok := content.(string)
	if !ok {
		b, err := json.Marshal(content)
		if err != nil {
			t.Fatal(err)
		}
		data = string(b)
	}
	path := filepath.Join(t.TempDir(), "sa-key.json")
	if err := os.WriteFile(path, []byte(data), 0600); err != nil {
		t.Fatal(err)
	}
	return path
}

// serviceAccountCredentials returns credentials from a key file whose
// tokens come from f.
func serviceAccountCredentials(t *testing.T, f *fakeTokenEndpoint) *Credentials {
	t.Helper()
	creds, err := FromServiceAccountFile(writeFile(t, serviceAccountKey(f.serve(t))), ScopeDatastore)
	if err != nil {
		t.Fatalf("FromServiceAccountFile() failed: %v", err)
	}
	return creds
}

func TestFromServiceAccountFile(t *testing.T) {
	f := &fakeTokenEndpoint{lifetime: longLife}
	tokenURL := f.serve(t)
	creds, err := FromServiceAccountFile(writeFile(t, serviceAccountKey(tokenURL)), ScopeCloudPlatform, ScopeDatastore)
	if err != nil {
		t.Fatalf("FromServiceAccountFile() failed: %v", err)
	}

	if creds.Project != "the-project" {
		t.Errorf("FromServiceAccountFile() project = %q, want %q", creds.Project, "the-project")
	}
	if got, malformed := f.requests(); len(got) != 0 || malformed != 0 {
		t.Errorf("FromServiceAccountFile() made token requests %+v and %d malformed ones, want none before a token is needed", got, malformed)
	}
	// The second token is the first one, kept.
	for range 2 {
		got, err := creds.Token(context.Background())
		if got != "sa-token-1" || err != nil {
			t.Errorf("Token() = %q, %v, want %q", got, err, "sa-token-1")
		}
	}
	want := []claims{{Iss: serviceAccount, Scope: ScopeCloudPlatform + " " + ScopeDatastore, Aud: tokenURL}}
	got, malformed := f.requests()
	if diff := cmp.Diff(want, got); diff != "" || malformed != 0 {
		t.Errorf("token requests: %d malformed, claims (-want +got):\n%s", malformed, diff)
	}
}

func TestServiceAccountClientSendsTheTokenAsABearerToken(t *testing.T) {
	creds := serviceAccountCredentials(t, &fakeTokenEndpoint{lifetime: longLife})
	api := &fakeAPI{status: http.StatusOK}

	resp, err := creds.Client.Get(api.serve(t))
	if err != nil {
		t.Fatalf("Client.Get() failed: %v", err)
	}
	resp.Body.Close()

	if diff := cmp.Diff([]string{"Bearer sa-token-1"}, api.authorizations()); diff != "" {
		t.Errorf("Authorization headers the API saw (-want +got):\n%s", diff)
	}
}

func TestFromServiceAccountFileErrors(t *testing.T) {
	without := func(field string) map[string]any {
		key := serviceAccountKey("https://oauth2.example.com/token")
		delete(key, field)
		return key
	}
	with := func(field string, value any) map[string]any {
		key := serviceAccountKey("https://oauth2.example.com/token")
		key[field] = value
		return key
	}
	tests := []struct {
		name string
		// content is the content of the file; nil means there is no file.
		content any
		scopes  []string
	}{
		{name: "no file", scopes: []string{ScopeDatastore}},
		{name: "no scopes", content: serviceAccountKey("https://oauth2.example.com/token")},
		{name: "not JSON", content: privateKey(), scopes: []string{ScopeDatastore}},
		{name: "an empty file", content: "", scopes: []string{ScopeDatastore}},
		{name: "no type", content: without("type"), scopes: []string{ScopeDatastore}},
		{name: "no project ID", content: without("project_id"), scopes: []string{ScopeDatastore}},
		{name: "an empty project ID", content: with("project_id", ""), scopes: []string{ScopeDatastore}},
		// The login that gcloud leaves in a home directory.
		{
			name: "a user's credentials",
			content: map[string]any{
				"type":          "authorized_user",
				"client_id":     "id",
				"client_secret": "secret",
				"refresh_token": "token",
			},
			scopes: []string{ScopeDatastore},
		},
		// Credentials that would be fetched from a URL the file names.
		{name: "an external account", content: with("type", "external_account"), scopes: []string{ScopeDatastore}},
		{name: "an impersonated service account", content: with("type", "impersonated_service_account"), scopes: []string{ScopeDatastore}},
		// A key of another kind of service account, which the library
		// would load the same way.
		{name: "a service account of another kind", content: with("type", "gdch_service_account"), scopes: []string{ScopeDatastore}},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			path := filepath.Join(t.TempDir(), "absent.json")
			if tc.content != nil {
				path = writeFile(t, tc.content)
			}

			creds, err := FromServiceAccountFile(path, tc.scopes...)

			if err == nil {
				t.Fatalf("FromServiceAccountFile() = %+v, want an error", creds)
			}
			if strings.Contains(err.Error(), "PRIVATE KEY") {
				t.Errorf("FromServiceAccountFile() error quotes the key file: %v", err)
			}
		})
	}
}

func TestServiceAccountTokenErrors(t *testing.T) {
	tests := []struct {
		name string
		// key changes a valid key whose tokens come from the fake endpoint.
		key      func(key map[string]any)
		endpoint *fakeTokenEndpoint
	}{
		{name: "the endpoint refuses the key", endpoint: &fakeTokenEndpoint{status: http.StatusBadRequest}},
		{name: "the endpoint fails", endpoint: &fakeTokenEndpoint{status: http.StatusInternalServerError}},
		{
			name: "a private key that is no key",
			key: func(key map[string]any) {
				key["private_key"] = "-----BEGIN PRIVATE KEY-----\nAAAA\n-----END PRIVATE KEY-----\n"
			},
			endpoint: &fakeTokenEndpoint{lifetime: longLife},
		},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			key := serviceAccountKey(tc.endpoint.serve(t))
			if tc.key != nil {
				tc.key(key)
			}
			creds, err := FromServiceAccountFile(writeFile(t, key), ScopeDatastore)
			if err != nil {
				t.Fatalf("FromServiceAccountFile() failed: %v", err)
			}

			got, err := creds.Token(context.Background())

			if got != "" || err == nil {
				t.Errorf("Token() = %q, %v, want an error and no token", got, err)
			}
		})
	}
}

func TestServiceAccountTokenIsFetchedAgainWhenAboutToExpire(t *testing.T) {
	creds := serviceAccountCredentials(t, &fakeTokenEndpoint{lifetime: shortLife})

	for _, want := range []string{"sa-token-1", "sa-token-2"} {
		got, err := creds.Token(context.Background())
		if got != want || err != nil {
			t.Errorf("Token() = %q, %v, want %q", got, err, want)
		}
	}
}

func TestServiceAccountTokenStopsWaitingWhenItsContextIsDone(t *testing.T) {
	f := &fakeTokenEndpoint{lifetime: longLife, hold: newHold()}
	creds := serviceAccountCredentials(t, f)
	ctx, cancel := context.WithTimeout(context.Background(), giveUp)
	defer cancel()
	start := time.Now()

	got, err := creds.Token(ctx)

	if got != "" || !errors.Is(err, context.DeadlineExceeded) {
		t.Errorf("Token() while the fetch stalls = %q, %v, want error %v", got, err, context.DeadlineExceeded)
	}
	if elapsed := time.Since(start); elapsed > tooLong {
		t.Errorf("Token() with a deadline of %v returned after %v", giveUp, elapsed)
	}

	// The fetch does not end with the context of any caller: it goes on,
	// and its token serves the next one.
	f.hold.release()
	if got, err := creds.Token(context.Background()); got != "sa-token-1" || err != nil {
		t.Errorf("Token() after the fetch went through = %q, %v, want %q", got, err, "sa-token-1")
	}
}
