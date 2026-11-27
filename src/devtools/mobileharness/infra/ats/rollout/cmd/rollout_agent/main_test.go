package main

import (
	"bytes"
	"errors"
	"flag"
	"strings"
	"testing"
	"time"
)

// required is the flags that every command needs.
var required = []string{"--credentials=/etc/mtt/secrets/sa-key.json", "--database=rollout", "--domain=mtt.example.com", "--group=dda", "--role=worker"}

func TestParseFlags(t *testing.T) {
	base := options{credentials: "/etc/mtt/secrets/sa-key.json", database: "rollout", domain: "mtt.example.com", group: "dda", role: "worker", mtt: "mtt"}
	with := func(change func(*options)) options {
		opts := base
		change(&opts)
		return opts
	}
	tests := []struct {
		name string
		args []string
		want options
	}{
		{
			name: "poll_with_defaults",
			args: append([]string{"poll"}, required...),
			want: with(func(o *options) { o.command = "poll" }),
		},
		{
			name: "enable",
			args: append([]string{"enable"}, required...),
			want: with(func(o *options) { o.command = "enable" }),
		},
		{
			name: "disable",
			args: append([]string{"disable"}, required...),
			want: with(func(o *options) { o.command = "disable" }),
		},
		{
			name: "every_flag",
			args: append([]string{"poll", "--mtt=/usr/local/bin/mtt", "--drain", "--drain_timeout=2h"}, required...),
			want: with(func(o *options) {
				o.command, o.mtt, o.drain, o.drainTimeout = "poll", "/usr/local/bin/mtt", true, 2*time.Hour
			}),
		},
		{
			name: "drain_without_a_timeout",
			args: append([]string{"poll", "--drain"}, required...),
			want: with(func(o *options) { o.command, o.drain = "poll", true }),
		},
		{
			name: "controller",
			args: []string{"poll", "--credentials=/etc/mtt/secrets/sa-key.json", "--database=rollout", "--domain=mtt.example.com", "--group=controller", "--role=controller"},
			want: with(func(o *options) { o.command, o.group, o.role = "poll", "controller", "controller" }),
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
	// without returns the required flags but the one with the given name.
	without := func(name string) []string {
		args := []string{"poll"}
		for _, f := range required {
			if !strings.HasPrefix(f, "--"+name+"=") {
				args = append(args, f)
			}
		}
		return args
	}
	tests := []struct {
		name string
		args []string
	}{
		{name: "no_arguments"},
		{name: "no_command", args: required},
		{name: "unknown_command", args: append([]string{"start"}, required...)},
		{name: "no_credentials", args: without("credentials")},
		{name: "no_database", args: without("database")},
		{name: "no_domain", args: without("domain")},
		{name: "no_group", args: without("group")},
		{name: "no_role", args: without("role")},
		{name: "unknown_role", args: append(without("role"), "--role=lab")},
		{name: "unknown_flag", args: append([]string{"poll", "--project=p"}, required...)},
		{name: "argument_after_the_flags", args: append(append([]string{"poll"}, required...), "now")},
		{name: "drain_timeout_without_unit", args: append([]string{"poll", "--drain", "--drain_timeout=60"}, required...)},
		{name: "drain_timeout_negative", args: append([]string{"poll", "--drain", "--drain_timeout=-1m"}, required...)},
		{name: "drain_timeout_without_drain", args: append([]string{"poll", "--drain_timeout=1h"}, required...)},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			var usage bytes.Buffer

			got, err := parseFlags(tc.args, &usage)

			if err == nil || errors.Is(err, flag.ErrHelp) {
				t.Errorf("parseFlags(%q) = %+v, %v, want an error", tc.args, got, err)
			}
			if usage.Len() != 0 {
				t.Errorf("parseFlags(%q) wrote %q, want nothing", tc.args, usage.String())
			}
		})
	}
}

func TestParseFlags_Help(t *testing.T) {
	for _, args := range [][]string{{"-h"}, {"--help"}, {"poll", "-h"}} {
		var usage bytes.Buffer

		_, err := parseFlags(args, &usage)

		if !errors.Is(err, flag.ErrHelp) {
			t.Errorf("parseFlags(%q) = %v, want flag.ErrHelp", args, err)
		}
		if !strings.Contains(usage.String(), "-credentials") {
			t.Errorf("parseFlags(%q) wrote %q, want the flags", args, usage.String())
		}
	}
}
