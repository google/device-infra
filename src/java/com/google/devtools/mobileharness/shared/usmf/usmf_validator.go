// Package main is the entry point for the USMF configuration validation stub.
package main

import (
	"fmt"
	"os"

	"go.starlark.net/starlark"
)

func main() {
	if len(os.Args) < 2 {
		fmt.Fprintln(os.Stderr, "Usage: usmf_validator <rules_file>")
		os.Exit(2)
	}
	rulesFile := os.Args[1]

	// Prepare mockup builtins to allow parsing.
	// We must define the identical mockup functions/structures as usmf_stub does,
	// so that compilation does not fail with undefined identifier errors on valid rule scripts.
	dummyFunc := func(thread *starlark.Thread, b *starlark.Builtin, args starlark.Tuple, kwargs []starlark.Tuple) (starlark.Value, error) {
		return starlark.None, nil
	}

	predeclared := starlark.StringDict{
		"Result":    starlark.NewBuiltin("Result", dummyFunc),
		"CreateDir": starlark.NewBuiltin("CreateDir", dummyFunc),
		"WriteFile": starlark.NewBuiltin("WriteFile", dummyFunc),
		"re_search": starlark.NewBuiltin("re_search", dummyFunc),
		"rand":      starlark.NewBuiltin("rand", dummyFunc),
	}

	thread := &starlark.Thread{Name: "usmf_validator"}
	_, err := starlark.ExecFile(thread, rulesFile, nil, predeclared)
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
	os.Exit(0)
}
