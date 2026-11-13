package parametermanager

import (
	"context"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/google/go-cmp/cmp"
)

// The tests in this file check the client against literal requests and
// responses, written from the Parameter Manager REST reference. They show
// that the client does what this file says the API wants, not that the API
// wants it: that takes a call to the real service.

const (
	versionName = "projects/p/locations/global/parameters/tenant_config/versions/20260917_163105"
	// rendered is a response as the API returns it without a field mask. The
	// payloads are the base64 of a config that the client must never look at.
	rendered = `{"parameterVersion": "` + versionName + `", "payload": {"data": "U0VDUkVU"}, "renderedPayload": "U0VDUkVU"}`
)

// apiRequest is one request as the API saw it.
type apiRequest struct {
	Method string
	// Path is the escaped path, as sent, and Query the raw query.
	Path  string
	Query string
	// Authorization is the credential the request carries.
	Authorization string
}

// fakeAPI answers every request with one status and body, and records the
// requests.
type fakeAPI struct {
	status int
	body   string
	// wait makes the API answer only when the client gives up.
	wait bool

	mu  sync.Mutex
	got []apiRequest
}

func (f *fakeAPI) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	f.mu.Lock()
	f.got = append(f.got, apiRequest{Method: r.Method, Path: r.URL.EscapedPath(), Query: r.URL.RawQuery, Authorization: r.Header.Get("Authorization")})
	f.mu.Unlock()
	if f.wait {
		<-r.Context().Done()
		return
	}
	w.WriteHeader(f.status)
	io.WriteString(w, f.body)
}

func (f *fakeAPI) requests() []apiRequest {
	f.mu.Lock()
	defer f.mu.Unlock()
	return append([]apiRequest(nil), f.got...)
}

// withCredentials is the transport of an http.Client that adds credentials,
// the way the caller's client does.
type withCredentials struct {
	base http.RoundTripper
}

func (t withCredentials) RoundTrip(r *http.Request) (*http.Response, error) {
	r = r.Clone(r.Context())
	r.Header.Set("Authorization", "Bearer the-token")
	return t.base.RoundTrip(r)
}

// newClient returns a Client for project that talks to f through an
// http.Client that adds credentials.
func newClient(t *testing.T, f *fakeAPI, project string) *Client {
	t.Helper()
	ts := httptest.NewServer(f)
	t.Cleanup(ts.Close)
	c, err := New(Config{Client: &http.Client{Transport: withCredentials{base: ts.Client().Transport}}, Project: project, Endpoint: ts.URL})
	if err != nil {
		t.Fatalf("New() failed: %v", err)
	}
	return c
}

func TestVersion(t *testing.T) {
	f := &fakeAPI{status: http.StatusOK, body: rendered}
	c := newClient(t, f, "p")

	got, err := c.Version(context.Background(), "tenant_config")
	if err != nil {
		t.Fatalf("Version() failed: %v", err)
	}

	if want := "20260917_163105"; got != want {
		t.Errorf("Version() = %q, want %q", got, want)
	}
	want := []apiRequest{{
		Method: "GET",
		Path:   "/v1/projects/p/locations/global/parameters/tenant_config/versions/latest:render",
		// Only the name of the resolved version is asked for, not the config.
		Query: "fields=parameterVersion",
		// The credential is the one the caller's http.Client adds; the
		// package neither adds one nor removes it.
		Authorization: "Bearer the-token",
	}}
	if diff := cmp.Diff(want, f.requests()); diff != "" {
		t.Errorf("Version() sent requests (-want +got):\n%s", diff)
	}
}

func TestVersionAddsNoCredentialsOfItsOwn(t *testing.T) {
	f := &fakeAPI{status: http.StatusOK, body: rendered}
	ts := httptest.NewServer(f)
	t.Cleanup(ts.Close)
	c, err := New(Config{Client: ts.Client(), Project: "p", Endpoint: ts.URL})
	if err != nil {
		t.Fatalf("New() failed: %v", err)
	}

	if _, err := c.Version(context.Background(), "tenant_config"); err != nil {
		t.Fatalf("Version() failed: %v", err)
	}

	if got := f.requests(); len(got) != 1 || got[0].Authorization != "" {
		t.Errorf("Version() through a client without credentials sent %+v, want one request without an Authorization header", got)
	}
}

// TestVersionEscapesColons uses a domain-scoped project ID and a parameter
// whose ID ends in the name of the custom method. Unescaped, the API would
// take ":render" off the wrong place.
func TestVersionEscapesColons(t *testing.T) {
	f := &fakeAPI{status: http.StatusOK, body: rendered}
	c := newClient(t, f, "example.com:p")

	if _, err := c.Version(context.Background(), "x:render_pool_config"); err != nil {
		t.Fatalf("Version() failed: %v", err)
	}

	got := f.requests()
	want := "/v1/projects/example.com%3Ap/locations/global/parameters/x%3Arender_pool_config/versions/latest:render"
	if len(got) != 1 || got[0].Path != want {
		t.Errorf("Version() sent requests %+v, want one for %q", got, want)
	}
}

func TestVersionReadsTheNameOfTheResolvedVersion(t *testing.T) {
	tests := []struct {
		name string
		body string
		// want is the version ID; empty means an error.
		want string
	}{
		{name: "a full resource name", body: `{"parameterVersion": "` + versionName + `"}`, want: "20260917_163105"},
		{name: "a response with the config", body: rendered, want: "20260917_163105"},
		{name: "a name with slashes around it", body: `{"parameterVersion": "/` + versionName + `/"}`, want: "20260917_163105"},
		{name: "a bare version ID", body: `{"parameterVersion": "20260917_163105"}`, want: "20260917_163105"},
		{name: "a name without a versions segment", body: `{"parameterVersion": "parameters/tenant_config/v7"}`, want: "v7"},
		{name: "an ID that contains the word versions", body: `{"parameterVersion": "a/versions/b/versions/c"}`, want: "c"},
		{name: "no name", body: `{"renderedPayload": "U0VDUkVU"}`},
		{name: "an empty name", body: `{"parameterVersion": ""}`},
		{name: "a name that is a slash only", body: `{"parameterVersion": "/"}`},
		{name: "the alias, not a version", body: `{"parameterVersion": "projects/p/locations/global/parameters/tenant_config/versions/latest"}`},
		{name: "the bare alias", body: `{"parameterVersion": "latest"}`},
		{name: "a name of another type", body: `{"parameterVersion": 7}`},
		{name: "not JSON", body: `<html>SECRET</html>`},
		{name: "truncated JSON", body: `{"renderedPayload": "SECRET", "parameterVersion": "proj`},
		{name: "an empty body", body: ``},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			c := newClient(t, &fakeAPI{status: http.StatusOK, body: tc.body}, "p")

			got, err := c.Version(context.Background(), "tenant_config")

			if got != tc.want || (err == nil) != (tc.want != "") {
				t.Errorf("Version() = %q, %v, want %q and an error only if that is empty", got, err, tc.want)
			}
			if errors.Is(err, ErrNotFound) {
				t.Errorf("Version() error = %v, want an error that is not %v: the API answered", err, ErrNotFound)
			}
			if err != nil && strings.Contains(err.Error(), "SECRET") {
				t.Errorf("Version() error quotes the response body: %v", err)
			}
		})
	}
}

func TestVersionErrors(t *testing.T) {
	apiError := func(code int, status string) string {
		return fmt.Sprintf(`{"error": {"code": %d, "message": "from the test", "status": %q}}`, code, status)
	}
	tests := []struct {
		name   string
		status int
		body   string
		// wantIn is a substring of the error message.
		wantIn string
		// wantNotFound is whether the error is ErrNotFound.
		wantNotFound bool
	}{
		// A missing parameter and a parameter without versions both get 404.
		{name: "no such parameter or version", status: http.StatusNotFound, body: apiError(404, "NOT_FOUND"), wantIn: "from the test", wantNotFound: true},
		{name: "404 that is not from the API", status: http.StatusNotFound, body: "<html>SECRET</html>", wantIn: "404", wantNotFound: true},
		{name: "not allowed", status: http.StatusForbidden, body: apiError(403, "PERMISSION_DENIED"), wantIn: "PERMISSION_DENIED"},
		{name: "not authenticated", status: http.StatusUnauthorized, body: apiError(401, "UNAUTHENTICATED"), wantIn: "UNAUTHENTICATED"},
		{name: "unavailable", status: http.StatusServiceUnavailable, body: apiError(503, "UNAVAILABLE"), wantIn: "from the test"},
		{name: "an error that is not from the API", status: http.StatusBadGateway, body: "<html>SECRET</html>", wantIn: "502"},
		{name: "a failure with a rendered body", status: http.StatusInternalServerError, body: `{"renderedPayload": "SECRET"}`, wantIn: "500"},
		{name: "a success status other than 200", status: http.StatusNoContent, body: "", wantIn: "204"},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			c := newClient(t, &fakeAPI{status: tc.status, body: tc.body}, "p")

			got, err := c.Version(context.Background(), "tenant_config")

			if err == nil || got != "" {
				t.Fatalf("Version() = %q, %v, want an error and no version", got, err)
			}
			if errors.Is(err, ErrNotFound) != tc.wantNotFound {
				t.Errorf("Version() error = %v, want errors.Is(err, ErrNotFound) = %t", err, tc.wantNotFound)
			}
			if !strings.Contains(err.Error(), tc.wantIn) {
				t.Errorf("Version() error = %v, want it to contain %q", err, tc.wantIn)
			}
			if strings.Contains(err.Error(), "SECRET") {
				t.Errorf("Version() error quotes the response body: %v", err)
			}
		})
	}
}

func TestVersionCutsALongErrorMessageShort(t *testing.T) {
	body := fmt.Sprintf(`{"error": {"code": 400, "message": %q, "status": "INVALID_ARGUMENT"}}`, strings.Repeat("x", 5000))
	c := newClient(t, &fakeAPI{status: http.StatusBadRequest, body: body}, "p")

	_, err := c.Version(context.Background(), "tenant_config")

	if err == nil || len(err.Error()) > 500 {
		t.Errorf("Version() error = %v, want an error of at most 500 bytes", err)
	}
}

func TestVersionRejectsWithoutARequest(t *testing.T) {
	for name, parameter := range map[string]string{
		"an empty parameter ID":         "",
		"a parameter ID with a slash":   "a/b",
		"a parameter ID that is a path": "../tenant_config",
		// A URL would resolve these two to another path.
		"a parameter ID that is one dot":  ".",
		"a parameter ID that is two dots": "..",
	} {
		t.Run(name, func(t *testing.T) {
			f := &fakeAPI{status: http.StatusOK, body: rendered}
			c := newClient(t, f, "p")

			got, err := c.Version(context.Background(), parameter)

			if err == nil || got != "" {
				t.Errorf("Version(%q) = %q, %v, want an error and no version", parameter, got, err)
			}
			if sent := f.requests(); len(sent) != 0 {
				t.Errorf("Version(%q) sent %+v, want no request", parameter, sent)
			}
		})
	}
}

func TestVersionCarriesTheCallersDeadline(t *testing.T) {
	c := newClient(t, &fakeAPI{wait: true}, "p")
	ctx, cancel := context.WithTimeout(context.Background(), 50*time.Millisecond)
	defer cancel()

	_, err := c.Version(ctx, "tenant_config")

	if !errors.Is(err, context.DeadlineExceeded) {
		t.Errorf("Version() against an API that does not answer returned error %v, want %v", err, context.DeadlineExceeded)
	}
}

func TestNew(t *testing.T) {
	valid := Config{Client: http.DefaultClient, Project: "p"}
	if _, err := New(valid); err != nil {
		t.Errorf("New(%+v) failed: %v", valid, err)
	}
	for name, change := range map[string]func(*Config){
		"no client":  func(c *Config) { c.Client = nil },
		"no project": func(c *Config) { c.Project = "" },
		// A resource name where an ID belongs.
		"a project with a slash": func(c *Config) { c.Project = "projects/p" },
		"a project of two dots":  func(c *Config) { c.Project = ".." },
	} {
		cfg := valid
		change(&cfg)
		if _, err := New(cfg); err == nil {
			t.Errorf("New() with %s returned no error, want one", name)
		}
	}
}

func TestNewIgnoresATrailingSlashOfTheEndpoint(t *testing.T) {
	f := &fakeAPI{status: http.StatusOK, body: rendered}
	ts := httptest.NewServer(f)
	t.Cleanup(ts.Close)
	c, err := New(Config{Client: ts.Client(), Project: "p", Endpoint: ts.URL + "//"})
	if err != nil {
		t.Fatalf("New() failed: %v", err)
	}

	if _, err := c.Version(context.Background(), "tenant_config"); err != nil {
		t.Fatalf("Version() failed: %v", err)
	}

	got := f.requests()
	if want := "/v1/projects/p/locations/global/parameters/tenant_config/versions/latest:render"; len(got) != 1 || got[0].Path != want {
		t.Errorf("Version() sent requests %+v, want one for %q", got, want)
	}
}
