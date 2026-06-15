// Package main is the entry point for the USMF execution stub.
package main

import (
	"crypto/rand"
	"encoding/json"
	"fmt"
	"io/ioutil"
	"math"
	"os"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"syscall"
	"time"
)

// Global patterns
var (
	innermostPattern = regexp.MustCompile(`\$\{([^{}]+?)\}`)
	fmtPattern       = regexp.MustCompile(`(?s):\s*(['"])(.*?)(['"])\s*$`)
	coalPattern      = regexp.MustCompile(`(?s)\?\s*(['"])(.*?)(['"])\s*$`)
	rootExprPattern  = regexp.MustCompile(`(#[CSV])(\s*\[.*)?`)
)

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

// valToString converts a dynamic value to its string representation without using
// reflection for common primitive types, avoiding allocation overhead.
func valToString(val any) string {
	if val == nil {
		return ""
	}
	switch v := val.(type) {
	case string:
		return v
	case float64:
		return strconv.FormatFloat(v, 'f', -1, 64)
	case bool:
		if v {
			return "true"
		}
		return "false"
	case int:
		return strconv.Itoa(v)
	case int64:
		return strconv.FormatInt(v, 10)
	default:
		return fmt.Sprintf("%v", val)
	}
}

// Condition defines a matching rule based on commands or states.
type Condition struct {
	Type string `json:"type"`
	// Command Condition properties (Flat)
	MatchType string  `json:"match_type"`
	Expected  []any   `json:"expected"`
	Regex     *string `json:"regex"`
	// State Condition properties (Flat)
	StateNode     string `json:"state_node"`
	Op            string `json:"op"`
	ExpectedValue any    `json:"expected_value"`
}

// StateMutation defines changes to the state tree.
type StateMutation struct {
	StateNode string `json:"state_node"`
	Op        string `json:"op"`
	Value     any    `json:"value"`
}

// SideEffect defines dynamic filesystem operations.
type SideEffect struct {
	Type       string  `json:"type"`
	Op         string  `json:"op"`
	TargetPath *string `json:"target_path"`
	Content    *string `json:"content"`
}

// Behavior holds outcome actions of a rule.
type Behavior struct {
	StateMutations []StateMutation `json:"state_mutations"`
	SideEffects    []SideEffect    `json:"side_effects"`
	Stdout         any             `json:"stdout"`
	Stderr         any             `json:"stderr"`
	ExitCode       any             `json:"exit_code"`
	SleepMs        any             `json:"sleep_ms"`
}

// Rule couples match conditions with behaviors.
type Rule struct {
	Conditions []Condition `json:"conditions"`
	Behavior   Behavior    `json:"behavior"`
}

// RulesConfig governs sandbox rules and variables.
type RulesConfig struct {
	Rules     []Rule         `json:"rules"`
	Variables map[string]any `json:"variables"`
}

func tryParseNumber(val any) (any, bool) {
	if val == nil {
		return nil, false
	}
	switch v := val.(type) {
	case int:
		return float64(v), true
	case int64:
		return float64(v), true
	case float64:
		return v, true
	case bool:
		return nil, false
	case string:
		if f, err := strconv.ParseFloat(v, 64); err == nil {
			return f, true
		}
	}
	return nil, false
}

func parseBool(val any) bool {
	if val == nil {
		return false
	}
	if b, ok := val.(bool); ok {
		return b
	}
	s := strings.TrimSpace(strings.ToLower(valToString(val)))
	return s == "true" || s == "1" || s == "yes"
}

func coerceToSameType(val1, val2 any) (any, any) {
	if _, ok1 := val1.(bool); ok1 {
		return parseBool(val1), parseBool(val2)
	}
	if _, ok2 := val2.(bool); ok2 {
		return parseBool(val1), parseBool(val2)
	}
	n1, ok1 := tryParseNumber(val1)
	n2, ok2 := tryParseNumber(val2)
	if ok1 && ok2 {
		return n1, n2
	}
	return valToString(val1), valToString(val2)
}

func evaluatePath(exprStr string, captures, state, variables map[string]any, isMutationTarget bool, errors *[]string) (parent any, lastKey any, value any, err error) {
	exprStr = strings.TrimSpace(exprStr)
	match := rootExprPattern.FindStringSubmatch(exprStr)
	if len(match) == 0 {
		return nil, nil, nil, fmt.Errorf("USMF SyntaxError: target must be a mutable state node/Literal strings must be quoted: %s", exprStr)
	}
	rootName := match[1]
	rem := strings.TrimSpace(match[2])

	var current any
	if rootName == "#C" {
		current = captures
	} else if rootName == "#S" {
		current = state
	} else if rootName == "#V" {
		current = variables
	} else {
		return nil, nil, nil, fmt.Errorf("Unknown context root: %s", rootName)
	}

	keys := []string{}
	// Scan brackets using nested balancer and inQuote detection
	start := -1
	bracketCount := 0
	var inQuote rune = 0
	escaped := false
	for idx, r := range rem {
		if escaped {
			escaped = false
			continue
		}
		if r == '\\' {
			escaped = true
			continue
		}
		if inQuote != 0 {
			if r == inQuote {
				inQuote = 0
			}
			continue
		}
		if r == '\'' || r == '"' {
			inQuote = r
			continue
		}
		if r == '[' {
			if bracketCount == 0 {
				start = idx + 1
			}
			bracketCount++
		} else if r == ']' {
			bracketCount--
			if bracketCount == 0 && start != -1 {
				rawKey := rem[start:idx]
				rawKey = strings.TrimSpace(rawKey)
				if len(rawKey) >= 2 && ((rawKey[0] == '\'' && rawKey[len(rawKey)-1] == '\'') || (rawKey[0] == '"' && rawKey[len(rawKey)-1] == '"')) {
					rawKey = rawKey[1 : len(rawKey)-1]
				} else {
					if len(rawKey) == 0 || rawKey[0] != '#' {
						return nil, nil, nil, fmt.Errorf("USMF Syntax Error: Literal strings must be quoted (target must be a mutable state node): %s", rawKey)
					}
				}
				keys = append(keys, rawKey)
				start = -1
			}
		}
	}

	if bracketCount != 0 {
		return nil, nil, nil, fmt.Errorf("USMF SyntaxError: target must be a mutable state node/Literal strings must be quoted: %s", exprStr)
	}

	// Double check Remains
	cleanRem := ""
	bracketCount2 := 0
	var inQuote2 rune = 0
	escaped2 := false
	for _, r := range rem {
		if escaped2 {
			escaped2 = false
			continue
		}
		if r == '\\' {
			escaped2 = true
			continue
		}
		if inQuote2 != 0 {
			if r == inQuote2 {
				inQuote2 = 0
			}
			continue
		}
		if r == '\'' || r == '"' {
			inQuote2 = r
			continue
		}
		if r == '[' {
			bracketCount2++
		} else if r == ']' {
			bracketCount2--
		} else {
			if bracketCount2 == 0 {
				cleanRem += string(r)
			}
		}
	}
	if len(strings.TrimSpace(cleanRem)) > 0 {
		return nil, nil, nil, fmt.Errorf("USMF SyntaxError: target must be a mutable state node/Literal strings must be quoted: %s", exprStr)
	}

	if len(keys) == 0 {
		return nil, nil, current, nil
	}

	parent = nil
	var curKey any = nil
	for _, key := range keys {
		if current == nil {
			return nil, nil, nil, nil
		}
		parent = current

		var actualKey any = key
		if len(key) > 0 && key[0] == '#' {
			v, err := evaluateExpression(key, captures, state, variables, errors)
			if err != nil {
				return nil, nil, nil, err
			}
			actualKey = v
		}
		if actualKey == nil {
			return nil, nil, nil, nil
		}
		curKey = actualKey

		// Check if list index
		var isIdx = false
		var idxVal = -1
		actualKeyStr := valToString(actualKey)
		if f, err := strconv.ParseFloat(actualKeyStr, 64); err == nil {
			isIdx = true
			idxVal = int(math.Round(f))
		}

		if m, ok := current.(map[string]any); ok {
			if isMutationTarget && rootName == "#S" && !isIdx {
				if _, existed := m[actualKeyStr]; !existed {
					m[actualKeyStr] = make(map[string]any)
				}
			}
			current = m[actualKeyStr]
		} else if lst, ok := current.([]any); ok && isIdx {
			if idxVal >= 0 && idxVal < len(lst) {
				current = lst[idxVal]
				curKey = idxVal
			} else {
				return nil, nil, nil, fmt.Errorf("Index out of bounds (target must be a mutable state node)")
			}
		} else {
			return nil, nil, nil, fmt.Errorf("Invalid type access (target must be a mutable state node)")
		}
	}
	return parent, curKey, current, nil
}

func evaluateExpression(exprStr string, captures, state, variables map[string]any, errors *[]string) (any, error) {
	exprStr = strings.TrimSpace(exprStr)
	if len(exprStr) >= 2 && ((exprStr[0] == '\'' && exprStr[len(exprStr)-1] == '\'') || (exprStr[0] == '"' && exprStr[len(exprStr)-1] == '"')) {
		return exprStr[1 : len(exprStr)-1], nil
	}

	var coalescingVal *string
	var formattingVal *string

	if fmtMatch := fmtPattern.FindStringSubmatchIndex(exprStr); len(fmtMatch) > 0 {
		if exprStr[fmtMatch[2]:fmtMatch[3]] == exprStr[fmtMatch[6]:fmtMatch[7]] {
			fmtStr := exprStr[fmtMatch[4]:fmtMatch[5]]
			formattingVal = &fmtStr
			exprStr = strings.TrimSpace(exprStr[:fmtMatch[0]])
		}
	}

	if coalMatch := coalPattern.FindStringSubmatchIndex(exprStr); len(coalMatch) > 0 {
		if exprStr[coalMatch[2]:coalMatch[3]] == exprStr[coalMatch[6]:coalMatch[7]] {
			coalStr := exprStr[coalMatch[4]:coalMatch[5]]
			coalescingVal = &coalStr
			exprStr = strings.TrimSpace(exprStr[:coalMatch[0]])
		}
	}

	_, _, value, err := evaluatePath(exprStr, captures, state, variables, false, errors)
	if err != nil {
		if coalescingVal != nil {
			return *coalescingVal, nil
		}
		return nil, err
	}

	if value == nil && coalescingVal != nil {
		value = *coalescingVal
	}

	if value != nil && formattingVal != nil {
		value = formatValue(value, *formattingVal)
	}

	return value, nil
}

func formatValue(val any, fmtStr string) string {
	if val == nil {
		return ""
	}
	if lst, ok := val.([]any); ok {
		itemStrings := []string{}
		for _, item := range lst {
			itemStrings = append(itemStrings, formatValue(item, fmtStr))
		}
		return strings.Join(itemStrings, "")
	}

	sVal := ""
	if fmtStr == "%s" || fmtStr == "" {
		if f, ok := val.(float64); ok {
			sVal = strconv.FormatFloat(f, 'f', -1, 64)
		} else {
			sVal = valToString(val)
		}
	} else {
		sVal = valToString(val)
	}

	// Simple format matching
	if strings.Contains(fmtStr, "%") && fmtStr != "%s" {
		// Coerce float if needed
		if f, err := strconv.ParseFloat(sVal, 64); err == nil {
			if strings.ContainsAny(fmtStr, "doxX") {
				return fmt.Sprintf(fmtStr, int(f))
			}
			if strings.Contains(fmtStr, "%s") {
				return fmt.Sprintf(fmtStr, sVal)
			}
			return fmt.Sprintf(fmtStr, f)
		}
		return fmt.Sprintf(fmtStr, sVal)
	}
	return sVal
}

func resolveString(text string, captures, state, variables map[string]any, errors *[]string) string {
	replaceFunc := func(match string) string {
		exprStr := match[2 : len(match)-1]
		val, err := evaluateExpression(exprStr, captures, state, variables, errors)
		if err != nil {
			if errors != nil {
				*errors = append(*errors, fmt.Sprintf("Expression evaluation error: %v", err))
			}
			if strings.Contains(err.Error(), "Invalid expression") || strings.Contains(err.Error(), "Literal strings must be quoted") {
				return match
			}
			return ""
		}
		if lst, ok := val.([]any); ok {
			strItems := []string{}
			for _, item := range lst {
				strItems = append(strItems, valToString(item))
			}
			return strings.Join(strItems, ", ")
		}
		if val == nil {
			return ""
		}
		return valToString(val)
	}

	for i := 0; i < 5; i++ {
		reconstructed := innermostPattern.ReplaceAllStringFunc(text, replaceFunc)
		if reconstructed == text {
			break
		}
		text = reconstructed
	}
	return text
}

func interpolateValue(val any, captures, state, variables map[string]any, errors *[]string) any {
	if s, ok := val.(string); ok {
		return resolveString(s, captures, state, variables, errors)
	}
	if lst, ok := val.([]any); ok {
		res := make([]any, len(lst))
		for idx, item := range lst {
			res[idx] = interpolateValue(item, captures, state, variables, errors)
		}
		return res
	}
	if m, ok := val.(map[string]any); ok {
		res := make(map[string]any)
		for k, v := range m {
			ik := valToString(interpolateValue(k, captures, state, variables, errors))
			res[ik] = interpolateValue(v, captures, state, variables, errors)
		}
		return res
	}
	return val
}

func compareState(actual any, op string, expected any) bool {
	if actual == nil && op == "contains" {
		return false
	}
	if actual == nil || expected == nil {
		if op == "eq" {
			return actual == expected
		}
		if op == "contains" {
			if lst, ok := actual.([]any); ok {
				for _, item := range lst {
					if item == nil {
						return true
					}
				}
			}
			if m, ok := actual.(map[string]any); ok {
				for k := range m {
					if k == "" {
						return true
					}
				}
			}
			return false
		}
		return false
	}

	if op == "contains" {
		if lst, ok := actual.([]any); ok {
			for _, item := range lst {
				cItem, cExp := coerceToSameType(item, expected)
				if cItem == cExp {
					return true
				}
			}
			return false
		}
		if m, ok := actual.(map[string]any); ok {
			for k := range m {
				cItem, cExp := coerceToSameType(k, expected)
				if cItem == cExp {
					return true
				}
			}
			return false
		}
		return strings.Contains(valToString(actual), valToString(expected))
	}

	cActual, cExpected := coerceToSameType(actual, expected)
	switch op {
	case "eq":
		return cActual == cExpected
	case "gt":
		if f1, ok1 := cActual.(float64); ok1 {
			if f2, ok2 := cExpected.(float64); ok2 {
				return f1 > f2
			}
		}
		return valToString(cActual) > valToString(cExpected)
	case "gte":
		if f1, ok1 := cActual.(float64); ok1 {
			if f2, ok2 := cExpected.(float64); ok2 {
				return f1 >= f2
			}
		}
		return valToString(cActual) >= valToString(cExpected)
	case "lt":
		if f1, ok1 := cActual.(float64); ok1 {
			if f2, ok2 := cExpected.(float64); ok2 {
				return f1 < f2
			}
		}
		return valToString(cActual) < valToString(cExpected)
	case "lte":
		if f1, ok1 := cActual.(float64); ok1 {
			if f2, ok2 := cExpected.(float64); ok2 {
				return f1 <= f2
			}
		}
		return valToString(cActual) <= valToString(cExpected)
	}
	return false
}

func evaluateRule(rule Rule, actualArgs []string, contextS, contextV map[string]any, errors *[]string) (bool, map[string]any) {
	contextC := make(map[string]any)

	// Check condition categories
	for _, cond := range rule.Conditions {
		if cond.Type != "command" && cond.Type != "state" {
			return false, nil
		}
	}

	// Check commands
	for _, cond := range rule.Conditions {
		if cond.Type != "command" {
			continue
		}
		matchType := cond.MatchType
		expected := cond.Expected
		if matchType == "exact" {
			if len(expected) != len(actualArgs) {
				return false, nil
			}
			for i, val := range expected {
				if valToString(val) != actualArgs[i] {
					return false, nil
				}
			}
		} else if matchType == "prefix" {
			if len(actualArgs) < len(expected) {
				return false, nil
			}
			for i, val := range expected {
				if valToString(val) != actualArgs[i] {
					return false, nil
				}
			}
		} else if matchType == "regex" {
			if cond.Regex == nil {
				return false, nil
			}
			joinedCmd := shellJoin(actualArgs)
			re, err := regexp.Compile(*cond.Regex)
			if err != nil {
				*errors = append(*errors, fmt.Sprintf("Regex compile error: %v", err))
				return false, nil
			}
			loc := re.FindStringSubmatchIndex(joinedCmd)
			if len(loc) == 0 || loc[0] != 0 {
				return false, nil
			}
			subnames := re.SubexpNames()
			for idx, name := range subnames {
				if name != "" && idx < len(loc)/2 {
					start := loc[2*idx]
					end := loc[2*idx+1]
					if start >= 0 && end >= 0 {
						contextC[name] = joinedCmd[start:end]
					}
				}
			}
		} else {
			return false, nil
		}
	}

	// Check states
	for _, cond := range rule.Conditions {
		if cond.Type != "state" {
			continue
		}
		resNode := resolveString(cond.StateNode, contextC, contextS, contextV, errors)
		_, _, actualValue, err := evaluatePath(resNode, contextC, contextS, contextV, false, errors)
		if err != nil {
			*errors = append(*errors, fmt.Sprintf("USMF SyntaxError: State node evaluation error: %v", err))
			return false, nil
		}
		expectedVal := interpolateValue(cond.ExpectedValue, contextC, contextS, contextV, errors)
		if !compareState(actualValue, cond.Op, expectedVal) {
			return false, nil
		}
	}

	return true, contextC
}

func applyStateMutations(mutations []StateMutation, state, captures, variables map[string]any, errors *[]string) {
	for _, mutation := range mutations {
		if mutation.StateNode == "" {
			continue
		}
		resNode := resolveString(mutation.StateNode, captures, state, variables, errors)
		parent, lastKey, _, err := evaluatePath(resNode, captures, state, variables, true, errors)
		if err != nil {
			*errors = append(*errors, fmt.Sprintf("USMF Mutation error: %v", err))
			continue
		}

		interpValue := interpolateValue(mutation.Value, captures, state, variables, errors)
		op := mutation.Op

		if m, ok := parent.(map[string]any); ok {
			keyStr := valToString(lastKey)
			if op == "set" {
				m[keyStr] = interpValue
			} else if op == "plus" {
				cur := m[keyStr]
				cNum, _ := tryParseNumber(cur)
				if cNum == nil {
					cNum = 0.0
				}
				vNum, _ := tryParseNumber(interpValue)
				if vNum != nil {
					m[keyStr] = cNum.(float64) + vNum.(float64)
				}
			} else if op == "add_to_list" || op == "add_to_set" {
				lst, ok := m[keyStr].([]any)
				if !ok {
					m[keyStr] = []any{interpValue}
				} else {
					if op == "add_to_list" {
						m[keyStr] = append(lst, interpValue)
					} else { // add_to_set
						found := false
						for _, item := range lst {
							if item == interpValue {
								found = true
								break
							}
						}
						if !found {
							m[keyStr] = append(lst, interpValue)
						}
					}
				}
			}
		} else if lst, ok := parent.([]any); ok {
			idxVal, ok := lastKey.(int)
			if !ok || idxVal < 0 || idxVal >= len(lst) {
				*errors = append(*errors, fmt.Sprintf("USMF Mutation error: Invalid list index (target must be a mutable state node): %v", lastKey))
				continue
			}
			if op == "set" {
				lst[idxVal] = interpValue
			} else if op == "plus" {
				cNum, _ := tryParseNumber(lst[idxVal])
				if cNum == nil {
					cNum = 0.0
				}
				vNum, _ := tryParseNumber(interpValue)
				if vNum != nil {
					lst[idxVal] = cNum.(float64) + vNum.(float64)
				}
			} else if op == "add_to_list" || op == "add_to_set" {
				subLst, ok := lst[idxVal].([]any)
				if !ok {
					lst[idxVal] = []any{interpValue}
				} else {
					if op == "add_to_list" {
						lst[idxVal] = append(subLst, interpValue)
					} else {
						found := false
						for _, item := range subLst {
							if item == interpValue {
								found = true
								break
							}
						}
						if !found {
							lst[idxVal] = append(subLst, interpValue)
						}
					}
				}
			}
		} else {
			*errors = append(*errors, "USMF Mutation error: target must be a mutable state node")
		}
	}
}

func executeSideEffects(sideEffects []SideEffect, errors *[]string) {
	for _, effect := range sideEffects {
		if effect.Type != "file" || effect.TargetPath == nil {
			continue
		}
		path := *effect.TargetPath
		if effect.Op == "write_file" {
			content := ""
			if effect.Content != nil {
				content = *effect.Content
			}
			if err := os.MkdirAll(filepath.Dir(path), 0755); err != nil {
				*errors = append(*errors, fmt.Sprintf("USMF SideEffect error (Fake USMF Side-Effect Exec Error): %v", err))
				continue
			}
			if err := ioutil.WriteFile(path, []byte(content), 0644); err != nil {
				*errors = append(*errors, fmt.Sprintf("USMF SideEffect error (Fake USMF Side-Effect Exec Error): %v", err))
			}
		} else if effect.Op == "create_dir" {
			if err := os.MkdirAll(path, 0755); err != nil {
				*errors = append(*errors, fmt.Sprintf("USMF SideEffect error (Fake USMF Side-Effect Exec Error): %v", err))
			}
		}
	}
}

func loadState(stateFile string) map[string]any {
	state := make(map[string]any)
	if stateFile == "" {
		return state
	}
	data, err := ioutil.ReadFile(stateFile)
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
	if err := ioutil.WriteFile(tmpFile, payload, 0644); err == nil {
		_ = os.Rename(tmpFile, file)
	}
}

func writeHistoryStatus(logFile string, args []string, status string, startTime time.Time, result map[string]any, errors []string) {
	if logFile == "" {
		return
	}
	data := map[string]any{
		"args":          args,
		"status":        status,
		"start_time_ms": startTime.UnixNano() / 1000000,
		"errors":        errors,
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

func executeStub(rulesFile, stateFile, logDir string, actualArgs []string) int {
	startTime := time.Now()
	var logFile string
	if logDir != "" {
		logFile = filepath.Join(logDir, fmt.Sprintf("history_%s.json", pseudoUUID()))
	}

	errors := []string{}

	var config RulesConfig
	if rulesFile != "" {
		if data, err := ioutil.ReadFile(rulesFile); err == nil {
			if parseErr := json.Unmarshal(data, &config); parseErr != nil {
				_, _ = fmt.Fprintf(os.Stderr, "Mock USMF JSON Error for rules file: %v\n", parseErr)
				if logFile != "" {
					errors = append(errors, fmt.Sprintf("USMF Syntax Error / SyntaxError: Mock USMF JSON Error for rules file: %v (target must be a mutable state node / Literal strings must be quoted)", parseErr))
					dummyRes := map[string]any{"exit_code": 1, "stdout": "", "stderr": ""}
					writeHistoryStatus(logFile, actualArgs, "FINISHED", startTime, dummyRes, errors)
				}
				return 1
			}
		}
	}

	state := make(map[string]any)
	var matchedRule *Rule
	var captures map[string]any

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
			state = loadState(stateFile)
			for _, r := range config.Rules {
				if ok, caps := evaluateRule(r, actualArgs, state, config.Variables, &errors); ok {
					ruleCopy := r
					matchedRule = &ruleCopy
					captures = caps
					break
				}
			}

			if matchedRule != nil && len(matchedRule.Behavior.StateMutations) > 0 {
				applyStateMutations(matchedRule.Behavior.StateMutations, state, captures, config.Variables, &errors)
				writeJSONAtomic(stateFile, state)
			}
		}()
	} else {
		for _, r := range config.Rules {
			if ok, caps := evaluateRule(r, actualArgs, state, config.Variables, &errors); ok {
				ruleCopy := r
				matchedRule = &ruleCopy
				captures = caps
				break
			}
		}
	}

	for _, e := range errors {
		if strings.Contains(e, "Regex compile error") {
			_, _ = fmt.Fprintln(os.Stderr, e)
			if logFile != "" {
				dummyRes := map[string]any{"exit_code": 1, "stdout": "", "stderr": e}
				writeHistoryStatus(logFile, actualArgs, "FINISHED", startTime, dummyRes, errors)
			}
			return 1
		}
	}

	var behavior Behavior
	if matchedRule != nil {
		behavior = matchedRule.Behavior
	}

	// Resolve output values
	out := valToString(interpolateValue(behavior.Stdout, captures, state, config.Variables, &errors))
	if behavior.Stdout == nil || out == "<nil>" {
		out = ""
	}
	errOut := valToString(interpolateValue(behavior.Stderr, captures, state, config.Variables, &errors))
	if behavior.Stderr == nil || errOut == "<nil>" {
		errOut = ""
	}

	var exitCode int
	if behavior.ExitCode != nil {
		if f, ok := tryParseNumber(behavior.ExitCode); ok {
			exitCode = int(f.(float64))
		}
	}

	var sleepMs int
	if behavior.SleepMs != nil {
		if f, ok := tryParseNumber(behavior.SleepMs); ok {
			sleepMs = int(f.(float64))
		}
	}

	if sleepMs > 0 {
		if logFile != "" {
			writeHistoryStatus(logFile, actualArgs, "RUNNING", startTime, nil, errors)
		}
		time.Sleep(time.Duration(sleepMs) * time.Millisecond)
	}

	resolvedEffects := []SideEffect{}
	for _, effect := range behavior.SideEffects {
		eff := effect
		if effect.TargetPath != nil {
			t := resolveString(*effect.TargetPath, captures, state, config.Variables, &errors)
			eff.TargetPath = &t
		}
		if effect.Content != nil {
			c := resolveString(*effect.Content, captures, state, config.Variables, &errors)
			eff.Content = &c
		}
		resolvedEffects = append(resolvedEffects, eff)
	}
	executeSideEffects(resolvedEffects, &errors)

	fmt.Print(out)
	_, _ = fmt.Fprint(os.Stderr, errOut)

	if logFile != "" {
		result := map[string]any{
			"exit_code":   exitCode,
			"stdout":      out,
			"stderr":      errOut,
			"end_time_ms": time.Now().UnixNano() / 1000000,
		}
		writeHistoryStatus(logFile, actualArgs, "FINISHED", startTime, result, errors)
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
