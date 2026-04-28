// Package flagutil provides utilities for parsing flags for DualConduit commands.
package flagutil

import (
	"fmt"
	"strconv"
	"strings"

	dconpb "github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/proto/dconpb"
)

// MultiString is a custom flag type that collects multiple string values.
type MultiString []string

// String returns the string representation of the flag.
func (s *MultiString) String() string {
	return fmt.Sprint(*s)
}

// Set appends the value to the list.
func (s *MultiString) Set(value string) error {
	*s = append(*s, value)
	return nil
}

// ParseForwardConduitFlag parses a forward conduit flag value in the format "port:endpoint".
func ParseForwardConduitFlag(flagValue string) (*dconpb.EstablishConduitRequest, error) {
	parts := strings.SplitN(flagValue, ":", 2)
	if len(parts) != 2 {
		return nil, fmt.Errorf("invalid -L flag format, expected entry_port:destination_endpoint")
	}
	entryPortStr := parts[0]
	destinationEndpoint := parts[1]

	entryPort, err := strconv.Atoi(entryPortStr)
	if err != nil {
		return nil, fmt.Errorf("invalid entry port in -L flag: %v", err)
	}

	return &dconpb.EstablishConduitRequest{
		Type:                dconpb.EstablishConduitRequest_CONDUIT_TYPE_FORWARD,
		EntryPort:           int32(entryPort),
		DestinationEndpoint: destinationEndpoint,
	}, nil
}
