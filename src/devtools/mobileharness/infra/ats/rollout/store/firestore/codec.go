package firestore

import (
	"encoding/json"
	"fmt"
	"strconv"
	"time"

	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/store"
)

// This file maps store.Record values to Firestore's typed JSON values and
// back. Each store type has one Firestore type, so documents stay readable
// and editable in Firestore Studio:
//
//	string         stringValue
//	bool           booleanValue
//	int64          integerValue (a decimal string in JSON)
//	time.Time      timestampValue (UTC, microseconds)
//	[]any          arrayValue
//	map[string]any mapValue

// Unsupported stands for a stored value whose Firestore type has no
// counterpart in store.Record, such as bytesValue or referenceValue. Only a
// writer other than this package can have stored it. store.Record.Validate
// rejects a record that holds one, which is the point: see store.Record.
type Unsupported struct {
	// Type is the name of the Firestore value type.
	Type string
}

// document is the JSON form of a Firestore document, as far as the adapter
// reads it.
type document struct {
	Name       string                     `json:"name"`
	Fields     map[string]json.RawMessage `json:"fields"`
	UpdateTime string                     `json:"updateTime"`
}

// arrayValue and mapValue are the JSON forms of the two container values.
// Firestore omits values and fields when the container is empty.
type (
	arrayValue struct {
		Values []json.RawMessage `json:"values"`
	}
	mapValue struct {
		Fields map[string]json.RawMessage `json:"fields"`
	}
)

// record returns the record and the version that d holds. The version is the
// document's updateTime, kept exactly as the server wrote it.
func (d *document) record() (store.Record, store.Version, error) {
	if d.UpdateTime == "" {
		return nil, store.Version{}, fmt.Errorf("document %q has no updateTime", d.Name)
	}
	fields, err := decodeFields(d.Fields, "")
	if err != nil {
		return nil, store.Version{}, fmt.Errorf("document %q: %v", d.Name, err)
	}
	return fields, store.NewVersion(d.UpdateTime), nil
}

// encodeFields returns the JSON form of the fields of a record or of a nested
// map. The record must be valid (see store.Record.Validate).
func encodeFields(m map[string]any) (map[string]any, error) {
	fields := make(map[string]any, len(m))
	for name, v := range m {
		enc, err := encodeValue(v)
		if err != nil {
			return nil, fmt.Errorf("field %q: %v", name, err)
		}
		fields[name] = enc
	}
	return fields, nil
}

func encodeValue(v any) (any, error) {
	switch v := v.(type) {
	case string:
		return map[string]any{"stringValue": v}, nil
	case bool:
		return map[string]any{"booleanValue": v}, nil
	case int64:
		// A JSON number cannot hold every int64; Firestore takes a string.
		return map[string]any{"integerValue": strconv.FormatInt(v, 10)}, nil
	case time.Time:
		// Firestore keeps microseconds. Truncating here, rather than leaving
		// it to the server, makes what is sent equal to what is read back.
		t := v.Truncate(time.Microsecond).UTC()
		return map[string]any{"timestampValue": t.Format(time.RFC3339Nano)}, nil
	case []any:
		values := make([]any, len(v))
		for i, e := range v {
			enc, err := encodeValue(e)
			if err != nil {
				return nil, fmt.Errorf("element %d: %v", i, err)
			}
			values[i] = enc
		}
		return map[string]any{"arrayValue": map[string]any{"values": values}}, nil
	case map[string]any:
		fields, err := encodeFields(v)
		if err != nil {
			return nil, err
		}
		return map[string]any{"mapValue": map[string]any{"fields": fields}}, nil
	}
	return nil, fmt.Errorf("unsupported value type %T", v)
}

// decodeFields returns the fields of a document or of a mapValue. path is the
// path of the map in the document, for error messages. The result is never
// nil: a document without fields is an empty record.
func decodeFields(raw map[string]json.RawMessage, path string) (map[string]any, error) {
	fields := make(map[string]any, len(raw))
	for name, v := range raw {
		fieldPath := name
		if path != "" {
			fieldPath = path + "." + name
		}
		dec, err := decodeValue(v, fieldPath)
		if err != nil {
			return nil, err
		}
		fields[name] = dec
	}
	return fields, nil
}

// decodeValue returns the Go value of one Firestore value. A value of a type
// that store.Record does not have is not an error: nullValue becomes nil,
// doubleValue a float64, and any other type an Unsupported. An error means
// the response itself is malformed.
func decodeValue(raw json.RawMessage, path string) (any, error) {
	var typed map[string]json.RawMessage
	if err := json.Unmarshal(raw, &typed); err != nil || len(typed) != 1 {
		return nil, fmt.Errorf("field %q: want a value of exactly one type, got %s", path, raw)
	}
	var (
		typ string
		v   json.RawMessage
	)
	for typ, v = range typed {
	}
	malformed := func(err error) (any, error) {
		return nil, fmt.Errorf("field %q: malformed %s %s: %v", path, typ, v, err)
	}

	switch typ {
	case "stringValue":
		s, err := unmarshal[string](v)
		if err != nil {
			return malformed(err)
		}
		return s, nil
	case "booleanValue":
		b, err := unmarshal[bool](v)
		if err != nil {
			return malformed(err)
		}
		return b, nil
	case "integerValue":
		// json.Number takes the decimal string Firestore sends, and a bare
		// number as well.
		n, err := unmarshal[json.Number](v)
		if err != nil {
			return malformed(err)
		}
		i, err := strconv.ParseInt(n.String(), 10, 64)
		if err != nil {
			return malformed(err)
		}
		return i, nil
	case "timestampValue":
		s, err := unmarshal[string](v)
		if err != nil {
			return malformed(err)
		}
		t, err := time.Parse(time.RFC3339, s)
		if err != nil {
			return malformed(err)
		}
		return t.Truncate(time.Microsecond).UTC(), nil
	case "arrayValue":
		array, err := unmarshal[arrayValue](v)
		if err != nil {
			return malformed(err)
		}
		// Never nil: an empty array is a value, not an absent field.
		values := make([]any, len(array.Values))
		for i, e := range array.Values {
			if values[i], err = decodeValue(e, fmt.Sprintf("%s[%d]", path, i)); err != nil {
				return nil, err
			}
		}
		return values, nil
	case "mapValue":
		m, err := unmarshal[mapValue](v)
		if err != nil {
			return malformed(err)
		}
		return decodeFields(m.Fields, path)
	case "nullValue":
		return nil, nil
	case "doubleValue":
		// A number, or one of the strings "NaN", "Infinity", "-Infinity".
		if f, err := unmarshal[float64](v); err == nil {
			return f, nil
		}
		s, err := unmarshal[string](v)
		if err != nil {
			return malformed(err)
		}
		f, err := strconv.ParseFloat(s, 64)
		if err != nil {
			return malformed(err)
		}
		return f, nil
	}
	return Unsupported{Type: typ}, nil
}

func unmarshal[T any](raw json.RawMessage) (T, error) {
	var v T
	err := json.Unmarshal(raw, &v)
	return v, err
}
