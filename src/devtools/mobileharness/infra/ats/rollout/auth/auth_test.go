package auth

import (
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
	"encoding/json"
	"encoding/pem"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"sync"
	"testing"

	"golang.org/x/oauth2/google"
)

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

// tokenEndpoint serves an OAuth token endpoint that answers every request
// with the access token "sa-token", and returns its URL.
func tokenEndpoint(t *testing.T) string {
	t.Helper()
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		io.WriteString(w, `{"access_token": "sa-token", "expires_in": 3600, "token_type": "Bearer"}`)
	}))
	t.Cleanup(ts.Close)
	return ts.URL
}

// serviceAccountKey returns the fields of a service account key file of the
// project "the-project", whose tokens come from tokenURL.
func serviceAccountKey(tokenURL string) map[string]any {
	return map[string]any{
		"type":           "service_account",
		"project_id":     "the-project",
		"private_key_id": "key-1",
		"private_key":    privateKey(),
		"client_email":   "rollout@the-project.iam.gserviceaccount.com",
		"token_uri":      tokenURL,
	}
}

// writeFile writes a credentials file with the given fields and returns its
// path.
func writeFile(t *testing.T, fields map[string]any) string {
	t.Helper()
	data, err := json.Marshal(fields)
	if err != nil {
		t.Fatal(err)
	}
	path := filepath.Join(t.TempDir(), "credentials.json")
	if err := os.WriteFile(path, data, 0600); err != nil {
		t.Fatal(err)
	}
	return path
}

// checkCredentials checks that creds are those of serviceAccountKey: the
// project, the token, and a client that sends the token.
func checkCredentials(t *testing.T, creds *Credentials) {
	t.Helper()
	if creds.Project != "the-project" {
		t.Errorf("Project = %q, want %q", creds.Project, "the-project")
	}
	if got, err := creds.Token(); got != "sa-token" || err != nil {
		t.Errorf("Token() = %q, %v, want %q", got, err, "sa-token")
	}

	authorization := make(chan string, 1)
	api := httptest.NewServer(http.HandlerFunc(func(_ http.ResponseWriter, r *http.Request) {
		authorization <- r.Header.Get("Authorization")
	}))
	t.Cleanup(api.Close)
	resp, err := creds.Client.Get(api.URL)
	if err != nil {
		t.Fatalf("Client.Get() failed: %v", err)
	}
	resp.Body.Close()
	if got, want := <-authorization, "Bearer sa-token"; got != want {
		t.Errorf("Client.Get() sent Authorization %q, want %q", got, want)
	}
}

func TestFromServiceAccountFile(t *testing.T) {
	path := writeFile(t, serviceAccountKey(tokenEndpoint(t)))

	creds, err := FromServiceAccountFile(path, ScopeDatastore)

	if err != nil {
		t.Fatalf("FromServiceAccountFile() failed: %v", err)
	}
	checkCredentials(t, creds)
}

func TestFromServiceAccountFile_Errors(t *testing.T) {
	key := func(change func(map[string]any)) map[string]any {
		fields := serviceAccountKey("https://oauth2.example.com/token")
		change(fields)
		return fields
	}
	tests := []struct {
		name string
		// fields is the content of the file; nil means there is no file.
		fields map[string]any
	}{
		{name: "no_file"},
		{name: "no_project_ID", fields: key(func(f map[string]any) { delete(f, "project_id") })},
		// The login that gcloud leaves in a home directory is not a key.
		{name: "user_credentials", fields: key(func(f map[string]any) { f["type"] = "authorized_user" })},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			path := filepath.Join(t.TempDir(), "absent.json")
			if tc.fields != nil {
				path = writeFile(t, tc.fields)
			}

			if creds, err := FromServiceAccountFile(path, ScopeDatastore); err == nil {
				t.Errorf("FromServiceAccountFile() = %+v, want an error", creds)
			}
		})
	}
}

func TestDefault_FileNamedByEnvironment(t *testing.T) {
	t.Setenv("GOOGLE_APPLICATION_CREDENTIALS", writeFile(t, serviceAccountKey(tokenEndpoint(t))))

	creds, err := Default(ScopeCloudPlatform)

	if err != nil {
		t.Fatalf("Default() failed: %v", err)
	}
	checkCredentials(t, creds)
}

func TestDefault_FileWithoutProject(t *testing.T) {
	// What "gcloud auth application-default login" leaves: a user's login,
	// which names a quota project and no project.
	t.Setenv("GOOGLE_APPLICATION_CREDENTIALS", writeFile(t, map[string]any{
		"type":             "authorized_user",
		"client_id":        "id",
		"client_secret":    "secret",
		"refresh_token":    "token",
		"quota_project_id": "the-project",
	}))

	if creds, err := Default(ScopeCloudPlatform); err == nil {
		t.Errorf("Default() = %+v, want an error for credentials that name no project", creds)
	}
}

func TestToken_ExchangeFails(t *testing.T) {
	refusing := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusBadRequest)
		io.WriteString(w, `{"error": "invalid_grant"}`)
	}))
	t.Cleanup(refusing.Close)
	creds, err := FromServiceAccountFile(writeFile(t, serviceAccountKey(refusing.URL)), ScopeDatastore)
	if err != nil {
		t.Fatalf("FromServiceAccountFile() failed: %v", err)
	}

	got, err := creds.Token()

	// The package's error wraps the library's, which says what Google
	// answered.
	var authErr *google.AuthenticationError
	if got != "" || !errors.As(err, &authErr) {
		t.Errorf("Token() = %q, %v, want no token and an error wrapping a *google.AuthenticationError", got, err)
	}
}

func TestClient_NoRedirects(t *testing.T) {
	// A host that the caller did not name, and that must not get the token.
	var mu sync.Mutex
	var reached []string
	other := httptest.NewServer(http.HandlerFunc(func(_ http.ResponseWriter, r *http.Request) {
		mu.Lock()
		defer mu.Unlock()
		reached = append(reached, r.Header.Get("Authorization"))
	}))
	t.Cleanup(other.Close)
	api := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		http.Redirect(w, r, other.URL, http.StatusFound)
	}))
	t.Cleanup(api.Close)
	creds, err := FromServiceAccountFile(writeFile(t, serviceAccountKey(tokenEndpoint(t))), ScopeDatastore)
	if err != nil {
		t.Fatalf("FromServiceAccountFile() failed: %v", err)
	}

	resp, err := creds.Client.Get(api.URL)

	if err != nil {
		t.Fatalf("Client.Get() failed: %v", err)
	}
	resp.Body.Close()
	if resp.StatusCode != http.StatusFound {
		t.Errorf("Client.Get() status = %d, want the redirect itself, %d", resp.StatusCode, http.StatusFound)
	}
	mu.Lock()
	defer mu.Unlock()
	if len(reached) != 0 {
		t.Errorf("the host of the redirect got requests with Authorization %q, want no request", reached)
	}
}

func TestCredentials_ClientAndTokenAtTheSameTime(t *testing.T) {
	// The client and the token function share the tokens, and callers use
	// both at once: the upstream step resolves tags and config versions at
	// the same time. The race detector is what checks this test. Every
	// round starts with new credentials, because the first token is where
	// the two ways meet.
	path := writeFile(t, serviceAccountKey(tokenEndpoint(t)))
	api := httptest.NewServer(http.HandlerFunc(func(http.ResponseWriter, *http.Request) {}))
	t.Cleanup(api.Close)

	for range 20 {
		creds, err := FromServiceAccountFile(path, ScopeDatastore)
		if err != nil {
			t.Fatalf("FromServiceAccountFile() failed: %v", err)
		}
		start := make(chan struct{})
		var wg sync.WaitGroup
		for range 4 {
			wg.Go(func() {
				<-start
				if _, err := creds.Token(); err != nil {
					t.Errorf("Token() failed: %v", err)
				}
			})
			wg.Go(func() {
				<-start
				resp, err := creds.Client.Get(api.URL)
				if err != nil {
					t.Errorf("Client.Get() failed: %v", err)
					return
				}
				resp.Body.Close()
			})
		}
		close(start)
		wg.Wait()
	}
}
