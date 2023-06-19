// Package common provides common functions for cas-tools
package common

import "fmt"

// MultiStringFlag is a slice of strings for parsing command flags into a string list.
type MultiStringFlag []string

// String returns the values.
func (f *MultiStringFlag) String() string {
	return fmt.Sprintf("%v", *f)
}

// Set adds a value to the flag.
func (f *MultiStringFlag) Set(val string) error {
	*f = append(*f, val)
	return nil
}

// Get returns the list of all flag values set.
func (f *MultiStringFlag) Get() any {
	return []string(*f)
}
