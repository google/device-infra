package store

import (
	"fmt"
	"time"
	"unicode/utf8"
)

// Record is the content of one stored record: a map from field names to
// values. A value is one of
//
//   - string (valid UTF-8)
//   - bool
//   - int64
//   - time.Time
//   - []any, whose elements are values
//   - map[string]any, whose keys are valid names and whose values are values
//
// Every backend accepts exactly these types and returns records built from
// them, so a record round-trips unchanged except for time values, which are
// returned in UTC truncated to microseconds (see Canonical). Field names
// follow the rules of Key.Validate.
//
// The schema of each record type is defined by its users, not by the store.
type Record map[string]any

// Validate returns an error wrapping ErrInvalidArgument if r contains a field
// name or value that is not allowed.
func (r Record) Validate() error {
	return validateMap(r)
}

func validateMap(m map[string]any) error {
	for name, v := range m {
		if err := validateName(name); err != nil {
			return fmt.Errorf("field %q: %w", name, err)
		}
		if err := validateValue(v); err != nil {
			return fmt.Errorf("field %q: %w", name, err)
		}
	}
	return nil
}

func validateValue(v any) error {
	switch v := v.(type) {
	case string:
		if !utf8.ValidString(v) {
			return fmt.Errorf("%w: string is not valid UTF-8", ErrInvalidArgument)
		}
	case bool, int64, time.Time:
	case []any:
		for i, e := range v {
			if err := validateValue(e); err != nil {
				return fmt.Errorf("element %d: %w", i, err)
			}
		}
	case map[string]any:
		return validateMap(v)
	default:
		return fmt.Errorf("%w: unsupported value type %T", ErrInvalidArgument, v)
	}
	return nil
}

// Canonical returns a deep copy of r in the form a backend returns it: time
// values are in UTC and truncated to microseconds, the precision of Firestore
// timestamps. Values of unsupported types are copied as they are; call
// Validate first.
func (r Record) Canonical() Record {
	if r == nil {
		return nil
	}
	return canonicalMap(r)
}

func canonicalMap(m map[string]any) map[string]any {
	out := make(map[string]any, len(m))
	for name, v := range m {
		out[name] = canonicalValue(v)
	}
	return out
}

func canonicalValue(v any) any {
	switch v := v.(type) {
	case time.Time:
		return v.Truncate(time.Microsecond).UTC()
	case []any:
		out := make([]any, len(v))
		for i, e := range v {
			out[i] = canonicalValue(e)
		}
		return out
	case map[string]any:
		return canonicalMap(v)
	default:
		return v
	}
}
