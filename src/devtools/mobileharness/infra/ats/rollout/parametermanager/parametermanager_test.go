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

func TestVersion_AddsNoCredentials(t *testing.T) {
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

// TestVersion_EscapesColons uses a domain-scoped project ID and a parameter
// whose ID ends in the name of the custom method. Unescaped, the API would
// take ":render" off the wrong place.
func TestVersion_EscapesColons(t *testing.T) {
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

func TestVersion_ResolvedVersionName(t *testing.T) {
	tests := []struct {
		name string
		body string
		// want is the version ID; empty means an error.
		want string
	}{
		{name: "full_resource_name", body: `{"parameterVersion": "` + versionName + `"}`, want: "20260917_163105"},
		{name: "response_with_config", body: rendered, want: "20260917_163105"},
		{name: "name_wrapped_in_slashes", body: `{"parameterVersion": "/` + versionName + `/"}`, want: "20260917_163105"},
		{name: "bare_version_ID", body: `{"parameterVersion": "20260917_163105"}`, want: "20260917_163105"},
		{name: "name_without_versions_segment", body: `{"parameterVersion": "parameters/tenant_config/v7"}`, want: "v7"},
		// An ID that contains the word versions.
		{name: "ID_containing_versions", body: `{"parameterVersion": "a/versions/b/versions/c"}`, want: "c"},
		{name: "no_name", body: `{"renderedPayload": "U0VDUkVU"}`},
		{name: "empty_name", body: `{"parameterVersion": ""}`},
		{name: "name_is_slash_only", body: `{"parameterVersion": "/"}`},
		// The alias, not a version.
		{name: "alias_not_version", body: `{"parameterVersion": "projects/p/locations/global/parameters/tenant_config/versions/latest"}`},
		{name: "bare_alias", body: `{"parameterVersion": "latest"}`},
		{name: "name_of_another_type", body: `{"parameterVersion": 7}`},
		{name: "not_JSON", body: `<html>SECRET</html>`},
		{name: "truncated_JSON", body: `{"renderedPayload": "SECRET", "parameterVersion": "proj`},
		{name: "empty_body", body: ``},
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

func TestVersion_Errors(t *testing.T) {
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
		{name: "no_such_parameter_or_version", status: http.StatusNotFound, body: apiError(404, "NOT_FOUND"), wantIn: "from the test", wantNotFound: true},
		// 404 that is not from the API.
		{name: "non_API_404", status: http.StatusNotFound, body: "<html>SECRET</html>", wantIn: "404", wantNotFound: true},
		{name: "not_allowed", status: http.StatusForbidden, body: apiError(403, "PERMISSION_DENIED"), wantIn: "PERMISSION_DENIED"},
		{name: "not_authenticated", status: http.StatusUnauthorized, body: apiError(401, "UNAUTHENTICATED"), wantIn: "UNAUTHENTICATED"},
		{name: "unavailable", status: http.StatusServiceUnavailable, body: apiError(503, "UNAVAILABLE"), wantIn: "from the test"},
		// An error that is not from the API.
		{name: "non_API_error", status: http.StatusBadGateway, body: "<html>SECRET</html>", wantIn: "502"},
		{name: "failure_with_rendered_body", status: http.StatusInternalServerError, body: `{"renderedPayload": "SECRET"}`, wantIn: "500"},
		// A success status other than 200.
		{name: "non_200_success", status: http.StatusNoContent, body: "", wantIn: "204"},
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

func TestVersion_LongErrorMessageCut(t *testing.T) {
	body := fmt.Sprintf(`{"error": {"code": 400, "message": %q, "status": "INVALID_ARGUMENT"}}`, strings.Repeat("x", 5000))
	c := newClient(t, &fakeAPI{status: http.StatusBadRequest, body: body}, "p")

	_, err := c.Version(context.Background(), "tenant_config")

	if err == nil || len(err.Error()) > 500 {
		t.Errorf("Version() error = %v, want an error of at most 500 bytes", err)
	}
}

func TestVersion_RejectsWithoutRequest(t *testing.T) {
	for name, parameter := range map[string]string{
		"empty_parameter_ID":        "",
		"parameter_ID_with_slash":   "a/b",
		"parameter_ID_that_is_path": "../tenant_config",
		// A URL would resolve these two to another path.
		"parameter_ID_that_is_one_dot":  ".",
		"parameter_ID_that_is_two_dots": "..",
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

func TestVersion_CallerDeadline(t *testing.T) {
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
		"no_client":  func(c *Config) { c.Client = nil },
		"no_project": func(c *Config) { c.Project = "" },
		// A resource name where an ID belongs.
		"project_with_slash":  func(c *Config) { c.Project = "projects/p" },
		"project_of_two_dots": func(c *Config) { c.Project = ".." },
	} {
		t.Run(name, func(t *testing.T) {
			cfg := valid
			change(&cfg)
			if _, err := New(cfg); err == nil {
				t.Errorf("New(%+v) returned no error, want one", cfg)
			}
		})
	}
}

func TestNew_EndpointTrailingSlash(t *testing.T) {
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
