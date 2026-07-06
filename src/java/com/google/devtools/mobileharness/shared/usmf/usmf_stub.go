// Package main is the entry point for the USMF execution stub.
package main

import (
	"crypto/rand"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"syscall"
	"time"

	"go.starlark.net/starlark"
)

// MatchVal implements starlark.Value, starlark.Indexable, and starlark.Mapping.
type MatchVal struct {
	FullMatch   string
	Groups      []string
	NamedGroups map[string]string
}

func (m *MatchVal) String() string { return fmt.Sprintf("Match(%q)", m.FullMatch) }

// Type returns the type name of the MatchVal.
func (m *MatchVal) Type() string { return "Match" }

// Freeze freezes the MatchVal.
func (m *MatchVal) Freeze() {}

// Truth returns the truth value of the MatchVal.
func (m *MatchVal) Truth() starlark.Bool { return true }

// Hash returns a hash value for the MatchVal.
func (m *MatchVal) Hash() (uint32, error) { return 0, fmt.Errorf("unhashable Match") }

// Len returns the length of the MatchVal.
func (m *MatchVal) Len() int {
	return 1 + len(m.Groups)
}

// Index returns the element at index i of the MatchVal.
func (m *MatchVal) Index(i int) starlark.Value {
	if i < 0 || i >= m.Len() {
		return starlark.None
	}
	if i == 0 {
		return starlark.String(m.FullMatch)
	}
	return starlark.String(m.Groups[i-1])
}

// Get returns the value matching the key from the MatchVal.
func (m *MatchVal) Get(key starlark.Value) (starlark.Value, bool, error) {
	switch k := key.(type) {
	case starlark.String:
		val, ok := m.NamedGroups[string(k)]
		if !ok {
			return starlark.None, true, nil
		}
		return starlark.String(val), true, nil
	case starlark.Int:
		i64, ok := k.Int64()
		if !ok {
			return starlark.None, false, nil
		}
		idx := int(i64)
		if idx < 0 || idx >= m.Len() {
			return starlark.None, true, nil
		}
		return m.Index(idx), true, nil
	}
	return starlark.None, false, nil
}

// CreateDirVal represents a directory creation side effect.
type CreateDirVal struct {
	TargetPath string
}

func (c *CreateDirVal) String() string { return fmt.Sprintf("CreateDir(%q)", c.TargetPath) }

// Type returns the type name of the CreateDirVal.
func (c *CreateDirVal) Type() string { return "CreateDir" }

// Freeze freezes the CreateDirVal.
func (c *CreateDirVal) Freeze() {}

// Truth returns the truth value of the CreateDirVal.
func (c *CreateDirVal) Truth() starlark.Bool { return true }

// Hash returns a hash value for the CreateDirVal.
func (c *CreateDirVal) Hash() (uint32, error) { return 0, fmt.Errorf("unhashable CreateDir") }

// WriteFileVal represents a file write side effect.
type WriteFileVal struct {
	TargetPath string
	Content    string
}

func (w *WriteFileVal) String() string {
	return fmt.Sprintf("WriteFile(%q, %q)", w.TargetPath, w.Content)
}

// Type returns the type name of the WriteFileVal.
func (w *WriteFileVal) Type() string { return "WriteFile" }

// Freeze freezes the WriteFileVal.
func (w *WriteFileVal) Freeze() {}

// Truth returns the truth value of the WriteFileVal.
func (w *WriteFileVal) Truth() starlark.Bool { return true }

// Hash returns a hash value for the WriteFileVal.
func (w *WriteFileVal) Hash() (uint32, error) { return 0, fmt.Errorf("unhashable WriteFile") }

// ResultVal defines the outcome of a matched rule.
type ResultVal struct {
	Stdout      string
	Stderr      string
	ExitCode    int
	SleepMs     int
	SideEffects []starlark.Value
}

func (r *ResultVal) String() string {
	return fmt.Sprintf("Result(stdout=%q, stderr=%q, exit_code=%d, sleep_ms=%d)", r.Stdout, r.Stderr, r.ExitCode, r.SleepMs)
}

// Type returns the type name of the ResultVal.
func (r *ResultVal) Type() string { return "Result" }

// Freeze freezes the ResultVal.
func (r *ResultVal) Freeze() {}

// Truth returns the truth value of the ResultVal.
func (r *ResultVal) Truth() starlark.Bool { return true }

// Hash returns a hash value for the ResultVal.
func (r *ResultVal) Hash() (uint32, error) { return 0, fmt.Errorf("unhashable Result") }

// ContextVal defines the Starlark execution context structure (ctx).
type ContextVal struct {
	Command string
	Args    *starlark.List
	State   *starlark.Dict
}

func (c *ContextVal) String() string { return "<context>" }

// Type returns the type name of the ContextVal.
func (c *ContextVal) Type() string { return "Context" }

// Freeze freezes the ContextVal.
func (c *ContextVal) Freeze() {}

// Truth returns the truth value of the ContextVal.
func (c *ContextVal) Truth() starlark.Bool { return true }

// Hash returns a hash value for the ContextVal.
func (c *ContextVal) Hash() (uint32, error) { return 0, fmt.Errorf("unhashable Context") }

// Attr returns the value of Starlark attribute with given name.
func (c *ContextVal) Attr(name string) (starlark.Value, error) {
	switch name {
	case "command":
		return starlark.String(c.Command), nil
	case "args":
		return c.Args, nil
	case "state":
		return c.State, nil
	}
	return nil, nil // Attribute not found.
}

// AttrNames returns the list of names of the Starlark attributes of ContextVal.
func (c *ContextVal) AttrNames() []string {
	return []string{"command", "args", "state"}
}

// Builtins implementation.

func makeResult(thread *starlark.Thread, b *starlark.Builtin, args starlark.Tuple, kwargs []starlark.Tuple) (starlark.Value, error) {
	stdout := ""
	stderr := ""
	var exitCode int
	var sleepMs int
	var sideEffects *starlark.List = starlark.NewList(nil)

	if err := starlark.UnpackArgs(
		b.Name(), args, kwargs,
		"stdout?", &stdout,
		"stderr?", &stderr,
		"exit_code?", &exitCode,
		"sleep_ms?", &sleepMs,
		"side_effects?", &sideEffects,
	); err != nil {
		return nil, err
	}

	var effects []starlark.Value
	if sideEffects != nil {
		for i := 0; i < sideEffects.Len(); i++ {
			effects = append(effects, sideEffects.Index(i))
		}
	}

	return &ResultVal{
		Stdout:      stdout,
		Stderr:      stderr,
		ExitCode:    exitCode,
		SleepMs:     sleepMs,
		SideEffects: effects,
	}, nil
}

func makeCreateDir(thread *starlark.Thread, b *starlark.Builtin, args starlark.Tuple, kwargs []starlark.Tuple) (starlark.Value, error) {
	var targetPath string
	if err := starlark.UnpackArgs(b.Name(), args, kwargs, "target_path", &targetPath); err != nil {
		return nil, err
	}
	return &CreateDirVal{TargetPath: targetPath}, nil
}

func makeWriteFile(thread *starlark.Thread, b *starlark.Builtin, args starlark.Tuple, kwargs []starlark.Tuple) (starlark.Value, error) {
	var targetPath, content string
	if err := starlark.UnpackArgs(b.Name(), args, kwargs, "target_path", &targetPath, "content", &content); err != nil {
		return nil, err
	}
	return &WriteFileVal{TargetPath: targetPath, Content: content}, nil
}

func reSearch(thread *starlark.Thread, b *starlark.Builtin, args starlark.Tuple, kwargs []starlark.Tuple) (starlark.Value, error) {
	var pattern, text string
	if err := starlark.UnpackArgs(b.Name(), args, kwargs, "pattern", &pattern, "text", &text); err != nil {
		return nil, err
	}
	re, err := regexp.Compile(pattern)
	if err != nil {
		return nil, err
	}
	loc := re.FindStringSubmatchIndex(text)
	if loc == nil {
		return starlark.None, nil
	}
	fullMatch := text[loc[0]:loc[1]]

	groups := []string{}
	for idx := 1; idx < len(loc)/2; idx++ {
		start, end := loc[2*idx], loc[2*idx+1]
		if start >= 0 && end >= 0 {
			groups = append(groups, text[start:end])
		} else {
			groups = append(groups, "")
		}
	}

	namedGroups := make(map[string]string)
	subnames := re.SubexpNames()
	for idx, name := range subnames {
		if name != "" && idx < len(loc)/2 {
			start, end := loc[2*idx], loc[2*idx+1]
			if start >= 0 && end >= 0 {
				namedGroups[name] = text[start:end]
			}
		}
	}

	return &MatchVal{
		FullMatch:   fullMatch,
		Groups:      groups,
		NamedGroups: namedGroups,
	}, nil
}

// Convert native Go maps/slices representation to Starlark values.
func jsonToStarlark(v any) starlark.Value {
	if v == nil {
		return starlark.None
	}
	switch val := v.(type) {
	case bool:
		return starlark.Bool(val)
	case float64:
		return starlark.Float(val)
	case int:
		return starlark.MakeInt(val)
	case int64:
		return starlark.MakeInt64(val)
	case string:
		return starlark.String(val)
	case []any:
		list := starlark.NewList(nil)
		for _, elem := range val {
			list.Append(jsonToStarlark(elem))
		}
		return list
	case map[string]any:
		dict := starlark.NewDict(len(val))
		for k, elem := range val {
			dict.SetKey(starlark.String(k), jsonToStarlark(elem))
		}
		return dict
	default:
		return starlark.String(fmt.Sprintf("%v", val))
	}
}

// Convert Starlark values back to JSON-compatible Go maps/slices.
func starlarkToJSON(v starlark.Value) (any, error) {
	if v == nil || v == starlark.None {
		return nil, nil
	}
	switch val := v.(type) {
	case starlark.Bool:
		return bool(val), nil
	case starlark.Int:
		if i, ok := val.Int64(); ok {
			return i, nil
		}
		return val.Float(), nil
	case starlark.Float:
		return float64(val), nil
	case starlark.String:
		return string(val), nil
	case *starlark.List:
		res := make([]any, val.Len())
		for i := 0; i < val.Len(); i++ {
			elem, err := starlarkToJSON(val.Index(i))
			if err != nil {
				return nil, err
			}
			res[i] = elem
		}
		return res, nil
	case *starlark.Dict:
		res := make(map[string]any)
		for _, item := range val.Items() {
			kVal := item[0]
			vVal := item[1]
			kStr, ok := kVal.(starlark.String)
			if !ok {
				return nil, fmt.Errorf("dict keys must be strings for JSON serialization, got: %T", kVal)
			}
			elem, err := starlarkToJSON(vVal)
			if err != nil {
				return nil, err
			}
			res[string(kStr)] = elem
		}
		return res, nil
	default:
		return nil, fmt.Errorf("unsupported type for state JSON: %T", v)
	}
}

func deepCopyValue(v any) any {
	if v == nil {
		return nil
	}
	switch val := v.(type) {
	case map[string]any:
		return deepCopyMap(val)
	case []any:
		return deepCopySlice(val)
	default:
		return val
	}
}

func deepCopyMap(m map[string]any) map[string]any {
	res := make(map[string]any, len(m))
	for k, v := range m {
		res[k] = deepCopyValue(v)
	}
	return res
}

func deepCopySlice(s []any) []any {
	res := make([]any, len(s))
	for i, v := range s {
		res[i] = deepCopyValue(v)
	}
	return res
}

func loadState(stateFile string) map[string]any {
	state := make(map[string]any)
	if stateFile == "" {
		return state
	}
	data, err := os.ReadFile(stateFile)
	if err != nil {
		return state
	}
	_ = json.Unmarshal(data, &state)
	return state
}

func writeJSONAtomic(file string, data any) {
	if file == "" {
		return
	}
	tmpFile := file + ".tmp"
	_ = os.MkdirAll(filepath.Dir(file), 0755)
	payload, err := json.MarshalIndent(data, "", "  ")
	if err != nil {
		return
	}
	if err := os.WriteFile(tmpFile, payload, 0644); err == nil {
		_ = os.Rename(tmpFile, file)
	}
}

func writeHistoryStatus(logFile string, args []string, status string, startTime time.Time, ruleName string, result map[string]any, errors []string) {
	if logFile == "" {
		return
	}
	data := map[string]any{
		"args":          args,
		"status":        status,
		"start_time_ms": startTime.UnixNano() / 1000000,
		"errors":        errors,
	}
	if ruleName != "" {
		data["rule_name"] = ruleName
	}
	if result != nil {
		data["result"] = result
	}
	writeJSONAtomic(logFile, data)
}

func pseudoUUID() string {
	b := make([]byte, 16)
	_, _ = rand.Read(b)
	return fmt.Sprintf("%x-%x-%x-%x-%x", b[0:4], b[4:6], b[6:8], b[8:10], b[10:])
}

var unsafeCharPattern = regexp.MustCompile(`[^A-Za-z0-9_@%+=:,./-]`)

func shellQuote(s string) string {
	if s == "" {
		return "''"
	}
	if unsafeCharPattern.MatchString(s) {
		return "'" + strings.ReplaceAll(s, "'", "'\\''") + "'"
	}
	return s
}

func shellJoin(args []string) string {
	quoted := make([]string, len(args))
	for i, arg := range args {
		quoted[i] = shellQuote(arg)
	}
	return strings.Join(quoted, " ")
}

func executeStub(rulesFile, stateFile, logDir string, actualArgs []string) int {
	startTime := time.Now()
	var logFile string
	if logDir != "" {
		logFile = filepath.Join(logDir, fmt.Sprintf("history_%s.json", pseudoUUID()))
	}

	var errors []string

	// 1. Context Preparation
	predeclared := starlark.StringDict{
		"Result":    starlark.NewBuiltin("Result", makeResult),
		"CreateDir": starlark.NewBuiltin("CreateDir", makeCreateDir),
		"WriteFile": starlark.NewBuiltin("WriteFile", makeWriteFile),
		"re_search": starlark.NewBuiltin("re_search", reSearch),
	}

	thread := &starlark.Thread{Name: "usmf"}

	var globals starlark.StringDict
	var err error
	if rulesFile != "" {
		if _, statErr := os.Stat(rulesFile); statErr == nil {
			//lint:ignore SA1019 ExecFile is fine to use here
			globals, err = starlark.ExecFile(thread, rulesFile, nil, predeclared)
			if err != nil {
				errMsg := fmt.Sprintf("Starlark compilation/execution error: %v", err)
				_, _ = fmt.Fprintln(os.Stderr, errMsg)
				errors = append(errors, errMsg)
				if logFile != "" {
					dummyRes := map[string]any{"exit_code": 1, "stdout": "", "stderr": errMsg}
					writeHistoryStatus(logFile, actualArgs, "FINISHED", startTime, "", dummyRes, errors)
				}
				return 1
			}
		}
	}

	var rulesList []starlark.Callable
	if globals != nil {
		if rulesVal, ok := globals["usmf_rules"]; ok {
			if listVal, ok := rulesVal.(*starlark.List); ok {
				for i := 0; i < listVal.Len(); i++ {
					if fn, ok := listVal.Index(i).(starlark.Callable); ok {
						rulesList = append(rulesList, fn)
					}
				}
			} else if tupleVal, ok := rulesVal.(starlark.Tuple); ok {
				for i := 0; i < tupleVal.Len(); i++ {
					if fn, ok := tupleVal.Index(i).(starlark.Callable); ok {
						rulesList = append(rulesList, fn)
					}
				}
			}
		}
	}

	currentState := make(map[string]any)
	var matchedRuleName string
	var finalResult *ResultVal

	// Lock and load state database
	if stateFile != "" {
		func() {
			lockFile := stateFile + ".lock"
			_ = os.MkdirAll(filepath.Dir(lockFile), 0755)
			if f, err := os.OpenFile(lockFile, os.O_CREATE|os.O_WRONLY, 0644); err == nil {
				_ = syscall.Flock(int(f.Fd()), syscall.LOCK_EX)
				defer func() {
					_ = syscall.Flock(int(f.Fd()), syscall.LOCK_UN)
					_ = f.Close()
				}()
			}
			currentState = loadState(stateFile)

			// Process Match-and-Route with dynamic state copy
			for _, fn := range rulesList {
				clonedState := deepCopyMap(currentState)
				stateDict := jsonToStarlark(clonedState).(*starlark.Dict)

				argsList := starlark.NewList(nil)
				for _, arg := range actualArgs {
					argsList.Append(starlark.String(arg))
				}

				ctx := &ContextVal{
					Command: shellJoin(actualArgs),
					Args:    argsList,
					State:   stateDict,
				}

				resVal, runErr := starlark.Call(thread, fn, starlark.Tuple{ctx}, nil)
				if runErr != nil {
					errMsg := fmt.Sprintf("Starlark execution exception in %s: %v", fn.Name(), runErr)
					_, _ = fmt.Fprintln(os.Stderr, errMsg)
					errors = append(errors, errMsg)
					continue
				}

				if resVal == nil || resVal == starlark.None {
					// Modifications are discarded on None
					continue
				}

				if res, ok := resVal.(*ResultVal); ok {
					finalResult = res
					matchedRuleName = fn.Name()

					// Commit modifications
					mutatedState, convErr := starlarkToJSON(stateDict)
					if convErr == nil {
						if ms, ok := mutatedState.(map[string]any); ok {
							currentState = ms
						}
					} else {
						errors = append(errors, fmt.Sprintf("Failed to convert state: %v", convErr))
					}
					writeJSONAtomic(stateFile, currentState)
					break
				}
			}
		}()
	} else {
		for _, fn := range rulesList {
			clonedState := deepCopyMap(currentState)
			stateDict := jsonToStarlark(clonedState).(*starlark.Dict)

			argsList := starlark.NewList(nil)
			for _, arg := range actualArgs {
				argsList.Append(starlark.String(arg))
			}

			ctx := &ContextVal{
				Command: shellJoin(actualArgs),
				Args:    argsList,
				State:   stateDict,
			}

			resVal, runErr := starlark.Call(thread, fn, starlark.Tuple{ctx}, nil)
			if runErr != nil {
				errMsg := fmt.Sprintf("Starlark execution exception in %s: %v", fn.Name(), runErr)
				_, _ = fmt.Fprintln(os.Stderr, errMsg)
				errors = append(errors, errMsg)
				continue
			}

			if resVal == nil || resVal == starlark.None {
				continue
			}

			if res, ok := resVal.(*ResultVal); ok {
				finalResult = res
				matchedRuleName = fn.Name()
				break
			}
		}
	}

	exitCode := 0
	stdout := ""
	stderr := ""
	sleepMs := 0
	var sideEffects []starlark.Value

	if finalResult != nil {
		exitCode = finalResult.ExitCode
		stdout = finalResult.Stdout
		stderr = finalResult.Stderr
		sleepMs = finalResult.SleepMs
		sideEffects = finalResult.SideEffects
	}

	if sleepMs > 0 {
		if logFile != "" {
			writeHistoryStatus(logFile, actualArgs, "RUNNING", startTime, matchedRuleName, nil, errors)
		}
		time.Sleep(time.Duration(sleepMs) * time.Millisecond)
	}

	// Physical host-side effects
	for _, effect := range sideEffects {
		switch eff := effect.(type) {
		case *CreateDirVal:
			if err := os.MkdirAll(eff.TargetPath, 0755); err != nil {
				errors = append(errors, fmt.Sprintf("USMF SideEffect error (Fake USMF Side-Effect Exec Error): %v", err))
			}
		case *WriteFileVal:
			if err := os.MkdirAll(filepath.Dir(eff.TargetPath), 0755); err != nil {
				errors = append(errors, fmt.Sprintf("USMF SideEffect error (Fake USMF Side-Effect Exec Error): %v", err))
				continue
			}
			if err := os.WriteFile(eff.TargetPath, []byte(eff.Content), 0644); err != nil {
				errors = append(errors, fmt.Sprintf("USMF SideEffect error (Fake USMF Side-Effect Exec Error): %v", err))
			}
		}
	}

	fmt.Print(stdout)
	_, _ = fmt.Fprint(os.Stderr, stderr)

	if logFile != "" {
		result := map[string]any{
			"exit_code":   exitCode,
			"stdout":      stdout,
			"stderr":      stderr,
			"end_time_ms": time.Now().UnixNano() / 1000000,
		}
		writeHistoryStatus(logFile, actualArgs, "FINISHED", startTime, matchedRuleName, result, errors)
	}

	return exitCode
}

func main() {
	rulesFile := os.Getenv("USMF_RULES_FILE")
	stateFile := os.Getenv("USMF_STATES_FILE")
	logDir := os.Getenv("USMF_LOGS_DIR")
	actualArgs := os.Args[1:]

	exitCode := executeStub(rulesFile, stateFile, logDir, actualArgs)
	os.Exit(exitCode)
}
