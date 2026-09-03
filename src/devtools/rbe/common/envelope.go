// Package common provides common functions for cas-tools
package common

import (
	"os"

	"flag"
)

var envelopeCandidatePaths = []string{
	"/usr/envelope/start_envelope",
	"/google/data/ro/teams/envelope/start_envelope",
}

// EnvelopeExists checks if the envelope executable physically exists and is executable.
// This is used to determine if envelope should be enabled by default.
func EnvelopeExists() bool {
	return EnvelopeExistsAt(envelopeCandidatePaths)
}

// EnvelopeExistsAt checks if an envelope executable exists at any of the given candidate paths.
func EnvelopeExistsAt(paths []string) bool {
	for _, path := range paths {
		if info, err := os.Stat(path); err == nil {
			if info.Mode().IsRegular() && (info.Mode().Perm()&0111 != 0) {
				return true
			}
		}
	}
	return false
}

// DisableEnvelopeIfAbsent disables envelope and svelte flags and sets ENVELOPE_OPT_OUT
// if a viable envelope binary does not exist on the host (e.g. on bare-metal lab machines).
// This must be called before flag.Parse().
func DisableEnvelopeIfAbsent() {
	if !EnvelopeExists() {
		if f := flag.Lookup("envelope_enabled"); f != nil {
			_ = f.Value.Set("false")
		}
		if f := flag.Lookup("disable_svelte"); f != nil {
			_ = f.Value.Set("true")
		}
		if _, ok := os.LookupEnv("ENVELOPE_OPT_OUT"); !ok {
			_ = os.Setenv("ENVELOPE_OPT_OUT", "1")
		}
	}
}
