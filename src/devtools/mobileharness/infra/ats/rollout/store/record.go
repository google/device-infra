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
//   - time.Time between years 1 and 9999 inclusive (the range of a Firestore
//     timestamp)
//   - []any, whose elements are values other than []any (Firestore rejects an
//     array directly inside an array; an array inside a map inside an array
//     is fine)
//   - map[string]any, whose keys are valid names and whose values are values
//
// Every backend accepts exactly these types and returns records built from
// them, so a record round-trips unchanged except for time values, which are
// returned in UTC truncated to microseconds (see Canonical). Field names
// follow the rules of Key.Validate.
//
// The schema of each record kind is defined by its users, not by the store.
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
		if err := validateValue(v, false); err != nil {
			return fmt.Errorf("field %q: %w", name, err)
		}
	}
	return nil
}

// Firestore timestamps cover years 1 to 9999. Outside that range Truncate
// would also round toward year 1 rather than down.
const (
	minYear = 1
	maxYear = 9999
)

// validateValue checks one value. inList reports whether v is a direct
// element of a []any.
func validateValue(v any, inList bool) error {
	switch v := v.(type) {
	case string:
		if !utf8.ValidString(v) {
			return fmt.Errorf("%w: string is not valid UTF-8", ErrInvalidArgument)
		}
	case bool, int64:
	case time.Time:
		if year := v.UTC().Year(); year < minYear || year > maxYear {
			return fmt.Errorf("%w: time %v is outside years %d to %d", ErrInvalidArgument, v, minYear, maxYear)
		}
	case []any:
		if inList {
			return fmt.Errorf("%w: array directly inside an array", ErrInvalidArgument)
		}
		for i, e := range v {
			if err := validateValue(e, true); err != nil {
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

// Canonical returns a deep copy of r in the form a backend returns it: never
// nil, with time values in UTC and truncated to microseconds, the precision
// of Firestore timestamps. Values of unsupported types are copied as they
// are; call Validate first.
func (r Record) Canonical() Record {
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
