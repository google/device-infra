package main

import (
	"bufio"
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/imageref"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/parametermanager"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/registry"
	"github.com/google/go-cmp/cmp"
)

func TestParseFlags(t *testing.T) {
	tests := []struct {
		name string
		args []string
		want options
	}{
		{
			name: "defaults",
			args: []string{"--domain=mtt.example.com", "--database=rollout"},
			want: options{domain: "mtt.example.com", database: "rollout", runTimeout: time.Minute},
		},
		{
			name: "every_flag",
			args: []string{"--domain=mtt.example.com", "--database=rollout", "--run_timeout=90s"},
			want: options{domain: "mtt.example.com", database: "rollout", runTimeout: 90 * time.Second},
		},
		{
			name: "value_as_next_argument",
			args: []string{"-domain", "mtt.example.com", "-database", "rollout"},
			want: options{domain: "mtt.example.com", database: "rollout", runTimeout: time.Minute},
		},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			var usage bytes.Buffer

			got, err := parseFlags(tc.args, &usage)

			if err != nil || got != tc.want {
				t.Errorf("parseFlags(%q) = %+v, %v, want %+v", tc.args, got, err, tc.want)
			}
			if usage.Len() != 0 {
				t.Errorf("parseFlags(%q) wrote %q, want nothing", tc.args, usage.String())
			}
		})
	}
}

func TestParseFlags_Errors(t *testing.T) {
	tests := []struct {
		name string
		args []string
	}{
		{name: "no_flags"},
		{name: "no_domain", args: []string{"--database=rollout"}},
		{name: "empty_domain", args: []string{"--domain=", "--database=rollout"}},
		{name: "no_database", args: []string{"--domain=mtt.example.com"}},
		{name: "unknown_flag", args: []string{"--domain=mtt.example.com", "--database=rollout", "--credentials=key.json"}},
		{name: "run_timeout_not_a_duration", args: []string{"--domain=mtt.example.com", "--database=rollout", "--run_timeout=soon"}},
		{name: "run_timeout_without_unit", args: []string{"--domain=mtt.example.com", "--database=rollout", "--run_timeout=60"}},
		{name: "run_timeout_zero", args: []string{"--domain=mtt.example.com", "--database=rollout", "--run_timeout=0s"}},
		{name: "run_timeout_negative", args: []string{"--domain=mtt.example.com", "--database=rollout", "--run_timeout=-1m"}},
		{name: "argument_after_flags", args: []string{"--domain=mtt.example.com", "--database=rollout", "now"}},
		// The flag package stops at the first argument that is no flag, so
		// the flags after it would be left out without a word.
		{name: "argument_before_flags", args: []string{"now", "--domain=mtt.example.com", "--database=rollout"}},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			var usage bytes.Buffer

			got, err := parseFlags(tc.args, &usage)

			if err == nil || errors.Is(err, flag.ErrHelp) || got != (options{}) {
				t.Errorf("parseFlags(%q) = %+v, %v, want no options and an error other than flag.ErrHelp", tc.args, got, err)
			}
			// The caller reports the error; nothing else is printed.
			if usage.Len() != 0 {
				t.Errorf("parseFlags(%q) wrote %q, want nothing", tc.args, usage.String())
			}
		})
	}
}

func TestParseFlags_Help(t *testing.T) {
	for _, arg := range []string{"-h", "-help", "--help"} {
		t.Run(strings.TrimLeft(arg, "-"), func(t *testing.T) {
			var usage bytes.Buffer

			_, err := parseFlags([]string{"--domain=mtt.example.com", arg}, &usage)

			if !errors.Is(err, flag.ErrHelp) {
				t.Errorf("parseFlags(%q) error = %v, want %v", arg, err, flag.ErrHelp)
			}
			for _, name := range []string{"-domain", "-database", "-run_timeout"} {
				if !strings.Contains(usage.String(), name) {
					t.Errorf("parseFlags(%q) wrote usage %q, want it to name the flag %s", arg, usage.String(), name)
				}
			}
		})
	}
}

func TestNewRegistryHTTPClient_FollowsNoRedirect(t *testing.T) {
	// A host that the registry client did not name.
	var reached atomic.Int32
	other := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		reached.Add(1)
		w.Header().Set("Docker-Content-Digest", "sha256:"+strings.Repeat("a", 64))
	}))
	t.Cleanup(other.Close)
	redirecting := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		http.Redirect(w, r, other.URL, http.StatusFound)
	}))
	t.Cleanup(redirecting.Close)

	resp, err := newRegistryHTTPClient().Head(redirecting.URL)

	if err != nil {
		t.Fatalf("Head() failed: %v", err)
	}
	resp.Body.Close()
	if resp.StatusCode != http.StatusFound || reached.Load() != 0 {
		t.Errorf("Head() status = %d and the host of the redirect got %d requests, want the redirect itself, %d, and no request", resp.StatusCode, reached.Load(), http.StatusFound)
	}
}

func TestNewLogger_WritesCloudLoggingFields(t *testing.T) {
	var out bytes.Buffer
	log := newLogger(&out)

	log.Info("run finished", "writes", 2)
	log.Warn("write failed")
	log.Error("run failed", "error", errors.New("no credentials"))
	// Only the level and the text of the line itself are renamed, not
	// attributes of the same names inside a group.
	log.WithGroup("request").Info("grouped", "level", "high", "msg", "hello")

	var got []map[string]any
	lines := bufio.NewScanner(&out)
	for lines.Scan() {
		var line map[string]any
		if err := json.Unmarshal(lines.Bytes(), &line); err != nil {
			t.Fatalf("log line %q is not a JSON object: %v", lines.Text(), err)
		}
		// When the line was written is not for a test to say.
		if _, ok := line["time"]; !ok {
			t.Errorf("log line %q has no time", lines.Text())
		}
		delete(line, "time")
		got = append(got, line)
	}
	if err := lines.Err(); err != nil {
		t.Fatalf("reading the log lines failed: %v", err)
	}
	want := []map[string]any{
		{"severity": "INFO", "message": "run finished", "writes": float64(2)},
		{"severity": "WARNING", "message": "write failed"},
		{"severity": "ERROR", "message": "run failed", "error": "no credentials"},
		{"severity": "INFO", "message": "grouped", "request": map[string]any{"level": "high", "msg": "hello"}},
	}
	if diff := cmp.Diff(want, got); diff != "" {
		t.Errorf("log lines (-want +got):\n%s", diff)
	}
}

func TestPermanent(t *testing.T) {
	tests := []struct {
		name string
		err  error
		want bool
	}{
		{name: "no_such_image", err: fmt.Errorf("resolving: %w", registry.ErrNotFound), want: true},
		{name: "no_such_parameter", err: fmt.Errorf("resolving: %w", parametermanager.ErrNotFound), want: true},
		{name: "invalid_image", err: fmt.Errorf("resolving: %w", imageref.ErrInvalid), want: true},
		{name: "out_of_time", err: fmt.Errorf("resolving: %w", context.DeadlineExceeded)},
		{name: "server_error", err: errors.New("the registry answered 503 Service Unavailable")},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			if got := permanent(tc.err); got != tc.want {
				t.Errorf("permanent(%v) = %t, want %t", tc.err, got, tc.want)
			}
		})
	}
}
