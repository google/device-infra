package registry

import (
	"context"
	"errors"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/google/go-cmp/cmp"
)

var (
	digestA = "sha256:" + strings.Repeat("a", 64)
	digestB = "sha256:" + strings.Repeat("b", 64)

	errToken = errors.New("no credentials")
)

// request is one request as the registry saw it.
type request struct {
	Method string
	// Path is the escaped path, as sent.
	Path string
	// User and Password are the Basic credentials.
	User, Password string
	Accept         string
}

// fakeRegistry is an HTTPS registry that answers every request with one
// status and one Docker-Content-Digest header, and records the requests.
type fakeRegistry struct {
	status int
	digest string // empty: no header
	// wait makes the registry answer only when the client gives up.
	wait bool

	mu  sync.Mutex
	got []request
}

func (f *fakeRegistry) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	user, password, _ := r.BasicAuth()
	f.mu.Lock()
	f.got = append(f.got, request{Method: r.Method, Path: r.URL.EscapedPath(), User: user, Password: password, Accept: r.Header.Get("Accept")})
	f.mu.Unlock()
	if f.wait {
		<-r.Context().Done()
		return
	}
	if f.digest != "" {
		w.Header().Set("Docker-Content-Digest", f.digest)
	}
	w.WriteHeader(f.status)
}

func (f *fakeRegistry) requests() []request {
	f.mu.Lock()
	defer f.mu.Unlock()
	return append([]request(nil), f.got...)
}

// tokens counts the calls of its token function.
type tokens struct {
	err error

	mu    sync.Mutex
	calls int
}

func (t *tokens) token(context.Context) (string, error) {
	t.mu.Lock()
	defer t.mu.Unlock()
	t.calls++
	return "the-token", t.err
}

// newClient returns a client for the fake registry f, and the host under
// which the registry is reached. The registry is served over TLS, so the
// tests go through the same https URLs as production. The client is allowed
// to send its token to that one host and to no other.
func newClient(t *testing.T, f *fakeRegistry, tok *tokens) (c *Client, host string) {
	t.Helper()
	ts := httptest.NewTLSServer(f)
	t.Cleanup(ts.Close)
	c, err := New(ts.Client(), tok.token)
	if err != nil {
		t.Fatalf("New() failed: %v", err)
	}
	host = strings.TrimPrefix(ts.URL, "https://")
	c.allowed = func(h string) bool { return h == host }
	return c, host
}

func TestDigest(t *testing.T) {
	f := &fakeRegistry{status: http.StatusOK, digest: digestA}
	tok := &tokens{}
	c, host := newClient(t, f, tok)

	got, err := c.Digest(context.Background(), host+"/project/repo/omnilab-ats", "latest")
	if err != nil {
		t.Fatalf("Digest() failed: %v", err)
	}

	if got != digestA {
		t.Errorf("Digest() = %q, want %q", got, digestA)
	}
	want := []request{{
		Method:   "HEAD",
		Path:     "/v2/project/repo/omnilab-ats/manifests/latest",
		User:     "oauth2accesstoken",
		Password: "the-token",
		// The index types are accepted, so that a multi-platform image
		// resolves to the digest of its index.
		Accept: "application/vnd.oci.image.index.v1+json, application/vnd.docker.distribution.manifest.list.v2+json, application/vnd.oci.image.manifest.v1+json, application/vnd.docker.distribution.manifest.v2+json",
	}}
	if diff := cmp.Diff(want, f.requests()); diff != "" {
		t.Errorf("Digest() sent requests (-want +got):\n%s", diff)
	}
}

func TestDigest_TagCharacters(t *testing.T) {
	f := &fakeRegistry{status: http.StatusOK, digest: digestA}
	c, host := newClient(t, f, &tokens{})
	// The longest tag there is: 128 characters.
	tag := "_V1.0-rc_" + strings.Repeat("x", 119)

	if _, err := c.Digest(context.Background(), host+"/project/img", tag); err != nil {
		t.Fatalf("Digest() failed: %v", err)
	}

	got := f.requests()
	if want := "/v2/project/img/manifests/" + tag; len(got) != 1 || got[0].Path != want {
		t.Errorf("Digest() sent requests %+v, want one for %q", got, want)
	}
}

func TestDigest_RepositorySeparators(t *testing.T) {
	f := &fakeRegistry{status: http.StatusOK, digest: digestA}
	c, host := newClient(t, f, &tokens{})
	// A domain-scoped project, and each separator a path segment may have.
	const repository = "example.com/my-project/repo_1/a__b/c--d/omnilab-ats.v2"

	if _, err := c.Digest(context.Background(), host+"/"+repository, "latest"); err != nil {
		t.Fatalf("Digest() failed: %v", err)
	}

	got := f.requests()
	if want := "/v2/" + repository + "/manifests/latest"; len(got) != 1 || got[0].Path != want {
		t.Errorf("Digest() sent requests %+v, want one for %q", got, want)
	}
}

func TestDigest_PinnedTagMakesNoRequest(t *testing.T) {
	f := &fakeRegistry{status: http.StatusOK, digest: digestA}
	tok := &tokens{}
	c, host := newClient(t, f, tok)

	got, err := c.Digest(context.Background(), host+"/project/img", "@"+digestB)
	if err != nil {
		t.Fatalf("Digest() failed: %v", err)
	}

	if got != digestB {
		t.Errorf("Digest() = %q, want the pinned digest %q", got, digestB)
	}
	if n := len(f.requests()); n != 0 || tok.calls != 0 {
		t.Errorf("Digest() of a pinned tag sent %d requests and asked for %d tokens, want none", n, tok.calls)
	}
}

func TestDigest_Errors(t *testing.T) {
	tests := []struct {
		name string
		// status and digest are the registry's answer; an empty digest means
		// no Docker-Content-Digest header.
		status int
		digest string
		// wantNotFound is whether the error is ErrNotFound.
		wantNotFound bool
	}{
		{name: "no_such_image_or_tag", status: http.StatusNotFound, wantNotFound: true},
		{name: "not_authenticated", status: http.StatusUnauthorized},
		{name: "not_allowed", status: http.StatusForbidden},
		{name: "registry_error", status: http.StatusInternalServerError},
		{name: "unexpected_success_status", status: http.StatusNoContent, digest: digestA},
		{name: "no_digest_header", status: http.StatusOK},
		{name: "digest_not_sha256", status: http.StatusOK, digest: "sha512:" + strings.Repeat("a", 128)},
		{name: "short_digest", status: http.StatusOK, digest: "sha256:abc"},
		{name: "upper_case_digest", status: http.StatusOK, digest: "sha256:" + strings.Repeat("A", 64)},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			c, host := newClient(t, &fakeRegistry{status: tc.status, digest: tc.digest}, &tokens{})

			got, err := c.Digest(context.Background(), host+"/project/img", "latest")

			if err == nil || got != "" {
				t.Fatalf("Digest() = %q, %v, want an error and no digest", got, err)
			}
			if errors.Is(err, ErrNotFound) != tc.wantNotFound {
				t.Errorf("Digest() error = %v, want errors.Is(err, ErrNotFound) = %t", err, tc.wantNotFound)
			}
		})
	}
}

func TestDigest_RejectsWithoutRequest(t *testing.T) {
	tests := []struct {
		name string
		// image is appended to the registry's host.
		image string
		tag   string
	}{
		{name: "no_repository", image: "", tag: "latest"},
		{name: "empty_repository", image: "/", tag: "latest"},
		{name: "empty_path_segment", image: "/project//img", tag: "latest"},
		{name: "parent_path_segment", image: "/project/../img", tag: "latest"},
		{name: "image_with_tag", image: "/project/img:latest", tag: "latest"},
		{name: "image_with_digest", image: "/project/img@" + digestA, tag: "latest"},
		{name: "repository_trailing_slash", image: "/project/img/", tag: "latest"},
		{name: "repository_upper_case", image: "/Project/img", tag: "latest"},
		{name: "repository_with_space", image: "/project/my img", tag: "latest"},
		{name: "repository_with_query", image: "/project/img?a=b", tag: "latest"},
		{name: "repository_with_fragment", image: "/project/img#a", tag: "latest"},
		{name: "repository_with_escape", image: "/project%2Fimg", tag: "latest"},
		// A repository with a colon before the last segment.
		{name: "repository_colon_before_last_segment", image: "/pro:ject/img", tag: "latest"},
		{name: "repository_segment_starts_with_separator", image: "/project/-img", tag: "latest"},
		{name: "empty_tag", image: "/project/img", tag: ""},
		{name: "tag_with_slash", image: "/project/img", tag: "a/b"},
		// A URL would resolve these two to another path.
		{name: "tag_one_dot", image: "/project/img", tag: "."},
		{name: "tag_two_dots", image: "/project/img", tag: ".."},
		{name: "tag_that_starts_with_dot", image: "/project/img", tag: ".a"},
		{name: "tag_that_starts_with_hyphen", image: "/project/img", tag: "-a"},
		{name: "tag_with_space", image: "/project/img", tag: "a b"},
		{name: "tag_with_query", image: "/project/img", tag: "a?b=c"},
		{name: "tag_with_fragment", image: "/project/img", tag: "a#b"},
		{name: "tag_with_colon", image: "/project/img", tag: "a:b"},
		{name: "tag_129_characters", image: "/project/img", tag: strings.Repeat("x", 129)},
		{name: "pinned_tag_not_digest", image: "/project/img", tag: "@latest"},
		{name: "pinned_digest_not_sha256", image: "/project/img", tag: "@sha512:" + strings.Repeat("a", 128)},
		// A pinned tag takes no request, and the image is checked all the
		// same: the caller builds "image@digest" from the result.
		{name: "pinned_no_repository", image: "", tag: "@" + digestA},
		{name: "pinned_empty_path_segment", image: "/project//img", tag: "@" + digestA},
		{name: "pinned_parent_path_segment", image: "/project/../img", tag: "@" + digestA},
		{name: "pinned_image_with_tag", image: "/project/img:latest", tag: "@" + digestA},
		{name: "pinned_image_with_digest", image: "/project/img@" + digestB, tag: "@" + digestA},
		{name: "pinned_repository_with_space", image: "/project/my img", tag: "@" + digestA},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			f := &fakeRegistry{status: http.StatusOK, digest: digestA}
			tok := &tokens{}
			c, host := newClient(t, f, tok)

			got, err := c.Digest(context.Background(), host+tc.image, tc.tag)

			if err == nil || got != "" {
				t.Errorf("Digest(%q, %q) = %q, %v, want an error and no digest", host+tc.image, tc.tag, got, err)
			}
			if n := len(f.requests()); n != 0 || tok.calls != 0 {
				t.Errorf("Digest(%q, %q) sent %d requests and asked for %d tokens, want none", host+tc.image, tc.tag, n, tok.calls)
			}
		})
	}
}

func TestDigest_NoTokenSendsNothing(t *testing.T) {
	f := &fakeRegistry{status: http.StatusOK, digest: digestA}
	c, host := newClient(t, f, &tokens{err: errToken})

	_, err := c.Digest(context.Background(), host+"/project/img", "latest")

	if !errors.Is(err, errToken) {
		t.Errorf("Digest() error = %v, want it to wrap %v", err, errToken)
	}
	if got := f.requests(); len(got) != 0 {
		t.Errorf("Digest() without a token sent %+v, want no request", got)
	}
}

func TestDigest_CallerDeadline(t *testing.T) {
	c, host := newClient(t, &fakeRegistry{wait: true}, &tokens{})
	ctx, cancel := context.WithTimeout(context.Background(), 50*time.Millisecond)
	defer cancel()

	_, err := c.Digest(ctx, host+"/project/img", "latest")

	if !errors.Is(err, context.DeadlineExceeded) {
		t.Errorf("Digest() against a registry that does not answer returned error %v, want %v", err, context.DeadlineExceeded)
	}
}

// TestDigest_TokenToGoogleRegistriesOnly uses a client as New returns
// it. The transport fails every request, so the test shows which hosts the
// client would have contacted: only for those does it ask for a token.
func TestDigest_TokenToGoogleRegistriesOnly(t *testing.T) {
	tests := []struct {
		host        string
		wantAllowed bool
	}{
		{host: "asia-docker.pkg.dev", wantAllowed: true},
		{host: "us-central1-docker.pkg.dev", wantAllowed: true},
		{host: "gcr.io", wantAllowed: true},
		{host: "eu.gcr.io", wantAllowed: true},
		{host: "registry.example.com"},
		{host: "docker.io"},
		{host: "localhost"},
		{host: "127.0.0.1:5000"},
		// Hosts made to look like a Google registry.
		{host: "pkg.dev.example.com"},
		{host: "examplepkg.dev"},
		{host: "example.com:443#.pkg.dev"},
		{host: "example.com?x=.pkg.dev"},
		{host: "example.com\\.pkg.dev"},
		{host: "asia-docker.pkg.dev:443"},
		{host: "asia-docker.pkg.dev."},
		{host: "ASIA-DOCKER.PKG.DEV"},
		{host: ".pkg.dev"},
	}
	for _, tc := range tests {
		t.Run(tc.host, func(t *testing.T) {
			var contacted []string
			transport := roundTripper(func(r *http.Request) (*http.Response, error) {
				contacted = append(contacted, r.URL.Scheme+"://"+r.URL.Host)
				return nil, errors.New("the test makes no connection")
			})
			tok := &tokens{}
			c, err := New(&http.Client{Transport: transport}, tok.token)
			if err != nil {
				t.Fatalf("New() failed: %v", err)
			}

			if _, err := c.Digest(context.Background(), tc.host+"/project/img", "latest"); err == nil {
				t.Fatalf("Digest() on host %q returned no error, want one", tc.host)
			}

			var want []string
			wantTokens := 0
			if tc.wantAllowed {
				want, wantTokens = []string{"https://" + tc.host}, 1
			}
			if diff := cmp.Diff(want, contacted); diff != "" || tok.calls != wantTokens {
				t.Errorf("Digest() on host %q contacted %q and asked for %d tokens, want %q and %d", tc.host, contacted, tok.calls, want, wantTokens)
			}

			// A pinned tag takes no request, but the host is checked all the
			// same: every host of the group later logs in to that registry
			// to pull the build.
			got, err := c.Digest(context.Background(), tc.host+"/project/img", "@"+digestA)
			if tc.wantAllowed && (got != digestA || err != nil) {
				t.Errorf("Digest() of a pinned tag on host %q = %q, %v, want %q", tc.host, got, err, digestA)
			}
			if !tc.wantAllowed && (got != "" || err == nil) {
				t.Errorf("Digest() of a pinned tag on host %q = %q, %v, want an error and no digest", tc.host, got, err)
			}
			if len(contacted) != len(want) || tok.calls != wantTokens {
				t.Errorf("Digest() of a pinned tag on host %q made a request or asked for a token: contacted %q, %d tokens", tc.host, contacted, tok.calls)
			}
		})
	}
}

// roundTripper is a function as an http.RoundTripper.
type roundTripper func(*http.Request) (*http.Response, error)

func (f roundTripper) RoundTrip(r *http.Request) (*http.Response, error) { return f(r) }

func TestNew(t *testing.T) {
	token := func(context.Context) (string, error) { return "", nil }
	if _, err := New(http.DefaultClient, token); err != nil {
		t.Errorf("New() failed: %v", err)
	}
	if _, err := New(nil, token); err == nil {
		t.Errorf("New() without an HTTP client returned no error, want one")
	}
	if _, err := New(http.DefaultClient, nil); err == nil {
		t.Errorf("New() without a token function returned no error, want one")
	}
}
