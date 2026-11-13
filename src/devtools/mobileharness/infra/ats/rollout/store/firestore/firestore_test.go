package firestore

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"math"
	"net/http"
	"net/http/httptest"
	"sync"
	"testing"
	"time"

	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/store"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/store/storetest"
	"github.com/google/go-cmp/cmp"
)

// TestContract runs the store contract suite against the adapter talking to
// the hermetic server, once for each status the server may give a
// precondition on a missing document.
func TestContract(t *testing.T) {
	for _, missingStatus := range []string{"FAILED_PRECONDITION", "NOT_FOUND"} {
		t.Run("missing_document_is_"+missingStatus, func(t *testing.T) {
			storetest.Run(t, func(t *testing.T) store.Store {
				return newTestStore(t, newServer(missingStatus))
			})
		})
	}
}

// The tests below check the wire format against literal requests and
// responses, written from the Firestore REST reference rather than produced
// by the adapter's own codec.

// exchange is one request as the server saw it.
type exchange struct {
	Method string
	// URL is the escaped path and the raw query, as sent.
	URL string
	// Body is the decoded JSON body, or nil for none.
	Body any
}

// reply is a scripted response.
type reply struct {
	code int
	body string
}

func ok(body string) reply { return reply{code: http.StatusOK, body: body} }

// status returns the error response Firestore gives for a canonical status.
func status(code int, name string) reply {
	return reply{code: code, body: fmt.Sprintf(`{"error": {"code": %d, "message": "from the test", "status": %q}}`, code, name)}
}

// script is a server that records each request and answers with the next
// scripted reply.
type script struct {
	t *testing.T

	mu      sync.Mutex
	replies []reply
	got     []exchange
}

func (sc *script) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	sc.mu.Lock()
	defer sc.mu.Unlock()
	ex := exchange{Method: r.Method, URL: r.URL.EscapedPath()}
	if r.URL.RawQuery != "" {
		ex.URL += "?" + r.URL.RawQuery
	}
	if data, _ := io.ReadAll(r.Body); len(data) > 0 {
		if err := json.Unmarshal(data, &ex.Body); err != nil {
			sc.t.Errorf("%s %s: the request body %s is not JSON: %v", ex.Method, ex.URL, data, err)
		}
	}
	sc.got = append(sc.got, ex)
	if len(sc.replies) == 0 {
		sc.t.Errorf("unexpected request %s %s: the script has no reply left", ex.Method, ex.URL)
		w.WriteHeader(http.StatusInternalServerError)
		return
	}
	next := sc.replies[0]
	sc.replies = sc.replies[1:]
	w.WriteHeader(next.code)
	io.WriteString(w, next.body)
}

// requests returns the requests received so far.
func (sc *script) requests() []exchange {
	sc.mu.Lock()
	defer sc.mu.Unlock()
	return append([]exchange(nil), sc.got...)
}

// newScripted returns a Store whose server answers with replies, in order.
func newScripted(t *testing.T, replies ...reply) (*Store, *script) {
	t.Helper()
	sc := &script{t: t, replies: replies}
	return newTestStore(t, sc), sc
}

// parse returns the value of a JSON text, for comparing request bodies.
func parse(t *testing.T, text string) any {
	t.Helper()
	var v any
	if err := json.Unmarshal([]byte(text), &v); err != nil {
		t.Fatalf("test JSON %s is invalid: %v", text, err)
	}
	return v
}

const (
	docs = documentsPath

	// A document as Firestore returns it, and the version it carries.
	updateTime = "2026-11-12T10:00:00.123456Z"
	docJSON    = `{"name": "projects/p/databases/d/documents/domains/d1", "fields": {"n": {"integerValue": "1"}}, "createTime": "2026-11-01T00:00:00Z", "updateTime": "` + updateTime + `"}`
	// The same version, escaped as a query value.
	escapedUpdateTime = "2026-11-12T10%3A00%3A00.123456Z"
)

var (
	version = store.NewVersion(updateTime)
	d1      = store.NewKey("domains", "d1")
	// A key whose IDs need escaping, in a path and in a query.
	odd = store.NewKey("domains", "mtt.example.com").Child("groups", "a b?#%&+")
	// A key whose kinds and IDs have a colon, some ending in the name of a
	// custom method. A valid key may; unescaped, the API would read the end
	// of the path as a call to that method.
	colonTop = store.NewKey("x:runQuery", "d:1")
	colon    = colonTop.Child("y:rollback", "e:runQuery")
)

func TestRequests(t *testing.T) {
	ctx := context.Background()
	record := store.Record{"n": int64(1)}
	const fields = `{"fields": {"n": {"integerValue": "1"}}}`
	tests := []struct {
		name  string
		call  func(s *Store) error
		reply reply
		want  exchange
	}{
		{
			name:  "Get",
			call:  func(s *Store) error { _, _, err := s.Get(ctx, d1); return err },
			reply: ok(docJSON),
			want:  exchange{Method: "GET", URL: docs + "/domains/d1"},
		},
		{
			name:  "Get_escapes_kind_and_ID",
			call:  func(s *Store) error { _, _, err := s.Get(ctx, odd); return err },
			reply: ok(docJSON),
			want:  exchange{Method: "GET", URL: docs + "/domains/mtt.example.com/groups/a%20b%3F%23%25&+"},
		},
		{
			name:  "List_top_level",
			call:  func(s *Store) error { _, err := s.List(ctx, store.Key{}, "domains"); return err },
			reply: ok(`[]`),
			want: exchange{
				Method: "POST",
				URL:    docs + ":runQuery",
				Body:   parse(t, `{"structuredQuery": {"from": [{"collectionId": "domains"}]}}`),
			},
		},
		{
			name:  "List_children",
			call:  func(s *Store) error { _, err := s.List(ctx, odd, "slots"); return err },
			reply: ok(`[]`),
			want: exchange{
				Method: "POST",
				URL:    docs + "/domains/mtt.example.com/groups/a%20b%3F%23%25&+:runQuery",
				Body:   parse(t, `{"structuredQuery": {"from": [{"collectionId": "slots"}]}}`),
			},
		},
		{
			name: "Query",
			call: func(s *Store) error {
				_, err := s.Query(ctx, d1, "slots", store.Eq{Field: "agent_id", Value: "host \"a\""})
				return err
			},
			reply: ok(`[]`),
			want: exchange{
				Method: "POST",
				URL:    docs + "/domains/d1:runQuery",
				Body: parse(t, `{"structuredQuery": {
					"from": [{"collectionId": "slots"}],
					"where": {"fieldFilter": {"field": {"fieldPath": "agent_id"}, "op": "EQUAL", "value": {"stringValue": "host \"a\""}}}
				}}`),
			},
		},
		{
			// Get escapes a colon in a kind and in an ID.
			name:  "Get_escapes_colon",
			call:  func(s *Store) error { _, _, err := s.Get(ctx, colon); return err },
			reply: ok(docJSON),
			want:  exchange{Method: "GET", URL: docs + "/x%3ArunQuery/d%3A1/y%3Arollback/e%3ArunQuery"},
		},
		{
			// List under a parent with colons: only the custom method keeps its colon.
			name:  "List_under_parent_with_colons",
			call:  func(s *Store) error { _, err := s.List(ctx, colon, "z:runQuery"); return err },
			reply: ok(`[]`),
			want: exchange{
				Method: "POST",
				URL:    docs + "/x%3ArunQuery/d%3A1/y%3Arollback/e%3ArunQuery:runQuery",
				Body:   parse(t, `{"structuredQuery": {"from": [{"collectionId": "z:runQuery"}]}}`),
			},
		},
		{
			// Create in a top-level collection with a colon.
			name:  "Create_top_level_with_colon",
			call:  func(s *Store) error { _, err := s.Create(ctx, colonTop, record); return err },
			reply: ok(docJSON),
			want:  exchange{Method: "POST", URL: docs + "/x%3ArunQuery?documentId=d%3A1", Body: parse(t, fields)},
		},
		{
			// Create of a child in a collection with a colon.
			name:  "Create_child_with_colon",
			call:  func(s *Store) error { _, err := s.Create(ctx, colon, record); return err },
			reply: ok(docJSON),
			want: exchange{
				Method: "POST",
				URL:    docs + "/x%3ArunQuery/d%3A1/y%3Arollback?documentId=e%3ArunQuery",
				Body:   parse(t, fields),
			},
		},
		{
			name:  "Update_escapes_colon",
			call:  func(s *Store) error { _, err := s.Update(ctx, colon, record, version); return err },
			reply: ok(docJSON),
			want: exchange{
				Method: "PATCH",
				URL:    docs + "/x%3ArunQuery/d%3A1/y%3Arollback/e%3ArunQuery?currentDocument.updateTime=" + escapedUpdateTime,
				Body:   parse(t, fields),
			},
		},
		{
			name:  "Delete_escapes_colon",
			call:  func(s *Store) error { return s.Delete(ctx, colon, version) },
			reply: ok(`{}`),
			want: exchange{
				Method: "DELETE",
				URL:    docs + "/x%3ArunQuery/d%3A1/y%3Arollback/e%3ArunQuery?currentDocument.updateTime=" + escapedUpdateTime,
			},
		},
		{
			name:  "Create_top_level",
			call:  func(s *Store) error { _, err := s.Create(ctx, d1, record); return err },
			reply: ok(docJSON),
			want:  exchange{Method: "POST", URL: docs + "/domains?documentId=d1", Body: parse(t, fields)},
		},
		{
			// Create of a child escapes the ID as a query value.
			name:  "Create_child_escapes_ID",
			call:  func(s *Store) error { _, err := s.Create(ctx, odd, record); return err },
			reply: ok(docJSON),
			want: exchange{
				Method: "POST",
				URL:    docs + "/domains/mtt.example.com/groups?documentId=a+b%3F%23%25%26%2B",
				Body:   parse(t, fields),
			},
		},
		{
			// Update replaces the document under a precondition.
			name:  "Update_with_precondition",
			call:  func(s *Store) error { _, err := s.Update(ctx, d1, record, version); return err },
			reply: ok(docJSON),
			want: exchange{
				Method: "PATCH",
				URL:    docs + "/domains/d1?currentDocument.updateTime=" + escapedUpdateTime,
				Body:   parse(t, fields),
			},
		},
		{
			name:  "Delete_with_precondition",
			call:  func(s *Store) error { return s.Delete(ctx, d1, version) },
			reply: ok(`{}`),
			want:  exchange{Method: "DELETE", URL: docs + "/domains/d1?currentDocument.updateTime=" + escapedUpdateTime},
		},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			s, sc := newScripted(t, tc.reply)

			if err := tc.call(s); err != nil {
				t.Fatalf("the call failed: %v", err)
			}

			if diff := cmp.Diff([]exchange{tc.want}, sc.requests()); diff != "" {
				t.Errorf("requests sent (-want +got):\n%s", diff)
			}
		})
	}
}

// TestKeysWithColons runs every operation, against the hermetic server, on
// keys whose kinds and IDs end in the name of a custom method. The server,
// like the API, takes a custom method off the escaped path before it decodes
// the path, so an unescaped colon sends the request to the wrong call: a
// Create in the collection "x:runQuery" would be read as a query.
func TestKeysWithColons(t *testing.T) {
	ctx := context.Background()
	s := newTestStore(t, newServer("FAILED_PRECONDITION"))
	keys := func(items []store.Item) []string {
		var ks []string
		for _, it := range items {
			ks = append(ks, it.Key.String())
		}
		return ks
	}

	topV, err := s.Create(ctx, colonTop, store.Record{"n": int64(1)})
	if err != nil {
		t.Fatalf("Create(%q) failed: %v", colonTop, err)
	}
	v, err := s.Create(ctx, colon, store.Record{"state": "OPEN"})
	if err != nil {
		t.Fatalf("Create(%q) failed: %v", colon, err)
	}
	if got, _, err := s.Get(ctx, colon); err != nil || got["state"] != "OPEN" {
		t.Errorf("Get(%q) = %v, %v, want the record created there", colon, got, err)
	}
	if items, err := s.List(ctx, store.Key{}, "x:runQuery"); err != nil || !cmp.Equal(keys(items), []string{colonTop.String()}) {
		t.Errorf("List(root, x:runQuery) = %v, %v, want %q", keys(items), err, colonTop)
	}
	if items, err := s.List(ctx, colonTop, "y:rollback"); err != nil || !cmp.Equal(keys(items), []string{colon.String()}) {
		t.Errorf("List(%q, y:rollback) = %v, %v, want %q", colonTop, keys(items), err, colon)
	}
	open := store.Eq{Field: "state", Value: "OPEN"}
	if items, err := s.Query(ctx, colonTop, "y:rollback", open); err != nil || !cmp.Equal(keys(items), []string{colon.String()}) {
		t.Errorf("Query(%q, y:rollback, %v) = %v, %v, want %q", colonTop, open, keys(items), err, colon)
	}
	// The children of a record whose ID ends in a method name.
	if items, err := s.List(ctx, colon, "z:runQuery"); err != nil || len(items) != 0 {
		t.Errorf("List(%q, z:runQuery) = %v, %v, want no items", colon, keys(items), err)
	}

	snap, err := s.Snapshot(ctx)
	if err != nil {
		t.Fatalf("Snapshot() failed: %v", err)
	}
	if _, snapV, err := snap.Get(ctx, colon); err != nil || snapV != v {
		t.Errorf("snapshot Get(%q) returned version %q, %v, want %q", colon, snapV.Token(), err, v.Token())
	}
	if err := snap.Close(ctx); err != nil {
		t.Errorf("snapshot Close() failed: %v", err)
	}

	v, err = s.Update(ctx, colon, store.Record{"state": "CLOSED"}, v)
	if err != nil {
		t.Fatalf("Update(%q) failed: %v", colon, err)
	}
	if err := s.Delete(ctx, colon, v); err != nil {
		t.Errorf("Delete(%q) failed: %v", colon, err)
	}
	if _, _, err := s.Get(ctx, colon); !errors.Is(err, store.ErrNotFound) {
		t.Errorf("Get(%q) after Delete returned error %v, want %v", colon, err, store.ErrNotFound)
	}
	if err := s.Delete(ctx, colonTop, topV); err != nil {
		t.Errorf("Delete(%q) failed: %v", colonTop, err)
	}
}

func TestWrite_VersionIsUpdateTime(t *testing.T) {
	ctx := context.Background()
	s, _ := newScripted(t, ok(docJSON), ok(docJSON), ok(docJSON))

	created, err := s.Create(ctx, d1, store.Record{})
	if err != nil {
		t.Fatalf("Create() failed: %v", err)
	}
	updated, err := s.Update(ctx, d1, store.Record{}, version)
	if err != nil {
		t.Fatalf("Update() failed: %v", err)
	}
	_, read, err := s.Get(ctx, d1)
	if err != nil {
		t.Fatalf("Get() failed: %v", err)
	}

	for name, got := range map[string]store.Version{"Create": created, "Update": updated, "Get": read} {
		if got != version {
			t.Errorf("%s() returned version %q, want the document's updateTime %q", name, got.Token(), updateTime)
		}
	}
}

func TestEncode_EveryValueType(t *testing.T) {
	at := time.Date(2026, 11, 5, 19, 0, 0, 123456789, time.FixedZone("CST", 8*60*60))
	record := store.Record{
		"s":          "x",
		"b":          true,
		"min":        int64(math.MinInt64),
		"max":        int64(math.MaxInt64),
		"t":          at,
		"whole":      time.Date(2026, 11, 5, 11, 0, 0, 0, time.UTC),
		"l":          []any{"a", int64(1), map[string]any{"k": []any{false}}},
		"m":          map[string]any{"host.example.com": "DONE"},
		"empty_map":  map[string]any{},
		"empty_list": []any{},
	}
	s, sc := newScripted(t, ok(docJSON))

	if _, err := s.Create(context.Background(), d1, record); err != nil {
		t.Fatalf("Create() failed: %v", err)
	}

	// An integer is a decimal string. A time is UTC, truncated to
	// microseconds.
	want := parse(t, `{"fields": {
		"s": {"stringValue": "x"},
		"b": {"booleanValue": true},
		"min": {"integerValue": "-9223372036854775808"},
		"max": {"integerValue": "9223372036854775807"},
		"t": {"timestampValue": "2026-11-05T11:00:00.123456Z"},
		"whole": {"timestampValue": "2026-11-05T11:00:00Z"},
		"l": {"arrayValue": {"values": [
			{"stringValue": "a"},
			{"integerValue": "1"},
			{"mapValue": {"fields": {"k": {"arrayValue": {"values": [{"booleanValue": false}]}}}}}
		]}},
		"m": {"mapValue": {"fields": {"host.example.com": {"stringValue": "DONE"}}}},
		"empty_map": {"mapValue": {"fields": {}}},
		"empty_list": {"arrayValue": {"values": []}}
	}}`)
	requests := sc.requests()
	if len(requests) != 1 {
		t.Fatalf("Create() sent %d requests, want 1", len(requests))
	}
	if diff := cmp.Diff(want, requests[0].Body); diff != "" {
		t.Errorf("Create() request body (-want +got):\n%s", diff)
	}
}

func TestDecode_EveryValueType(t *testing.T) {
	const doc = `{
		"name": "projects/p/databases/d/documents/domains/d1",
		"fields": {
			"s": {"stringValue": "x"},
			"b": {"booleanValue": true},
			"min": {"integerValue": "-9223372036854775808"},
			"bare": {"integerValue": 42},
			"t": {"timestampValue": "2026-11-05T19:00:00.123456789+08:00"},
			"l": {"arrayValue": {"values": [{"stringValue": "a"}, {"mapValue": {"fields": {"k": {"booleanValue": false}}}}]}},
			"m": {"mapValue": {"fields": {"host.example.com": {"stringValue": "DONE"}}}},
			"empty_map": {"mapValue": {}},
			"empty_list": {"arrayValue": {}},
			"null": {"nullValue": null},
			"double": {"doubleValue": 1.5},
			"infinity": {"doubleValue": "Infinity"},
			"bytes": {"bytesValue": "eA=="},
			"reference": {"referenceValue": "projects/p/databases/d/documents/domains/d2"},
			"point": {"geoPointValue": {"latitude": 1, "longitude": 2}},
			"nested": {"mapValue": {"fields": {"null": {"nullValue": null}}}}
		},
		"createTime": "2026-11-01T00:00:00Z",
		"updateTime": "` + updateTime + `"
	}`
	s, _ := newScripted(t, ok(doc))

	got, v, err := s.Get(context.Background(), d1)
	if err != nil {
		t.Fatalf("Get() failed: %v", err)
	}

	// A value of a type store.Record does not have is returned, not refused:
	// a null as nil, a double as a float64, anything else as Unsupported.
	want := store.Record{
		"s":          "x",
		"b":          true,
		"min":        int64(math.MinInt64),
		"bare":       int64(42),
		"t":          time.Date(2026, 11, 5, 11, 0, 0, 123456000, time.UTC),
		"l":          []any{"a", map[string]any{"k": false}},
		"m":          map[string]any{"host.example.com": "DONE"},
		"empty_map":  map[string]any{},
		"empty_list": []any{},
		"null":       nil,
		"double":     1.5,
		"infinity":   math.Inf(1),
		"bytes":      Unsupported{Type: "bytesValue"},
		"reference":  Unsupported{Type: "referenceValue"},
		"point":      Unsupported{Type: "geoPointValue"},
		"nested":     map[string]any{"null": nil},
	}
	if diff := cmp.Diff(want, got); diff != "" {
		t.Errorf("Get() record (-want +got):\n%s", diff)
	}
	if v != version {
		t.Errorf("Get() version = %q, want %q", v.Token(), updateTime)
	}
	if at, isTime := got["t"].(time.Time); !isTime || at.Location() != time.UTC {
		t.Errorf("Get() field \"t\" = %v, want a time in UTC", got["t"])
	}
	// The record is returned so that its reader can reject it.
	if err := got.Validate(); !errors.Is(err, store.ErrInvalidArgument) {
		t.Errorf("Validate() of a record with values the store does not allow returned error %v, want %v", err, store.ErrInvalidArgument)
	}
	for _, name := range []string{"null", "double", "infinity", "bytes", "reference", "point", "nested"} {
		if err := (store.Record{name: got[name]}).Validate(); !errors.Is(err, store.ErrInvalidArgument) {
			t.Errorf("Validate() of field %q = %v returned error %v, want %v", name, got[name], err, store.ErrInvalidArgument)
		}
	}
}

func TestEmptyDocument(t *testing.T) {
	s, _ := newScripted(t, ok(`{"name": "projects/p/databases/d/documents/domains/d1", "updateTime": "`+updateTime+`"}`))

	got, _, err := s.Get(context.Background(), d1)
	if err != nil {
		t.Fatalf("Get() failed: %v", err)
	}

	if got == nil || len(got) != 0 {
		t.Errorf("Get() of a document without fields = %v, want a non-nil empty record", got)
	}
}

// errTransient stands for any error that wraps none of the store's errors:
// the caller treats it as transient.
var errTransient = errors.New("an error that is none of the store's errors")

// checkError reports whether err is what want asks for: nil, one of the
// store's errors, or errTransient.
func checkError(t *testing.T, what string, err, want error) {
	t.Helper()
	storeErrors := []error{store.ErrNotFound, store.ErrAlreadyExists, store.ErrVersionMismatch, store.ErrInvalidArgument, store.ErrClosed}
	switch {
	case want == nil:
		if err != nil {
			t.Errorf("%s returned error %v, want nil", what, err)
		}
	case errors.Is(want, errTransient):
		if err == nil {
			t.Errorf("%s returned no error, want one", what)
		}
		for _, se := range storeErrors {
			if errors.Is(err, se) {
				t.Errorf("%s returned error %v, want an error that is not %v", what, err, se)
			}
		}
	default:
		if !errors.Is(err, want) {
			t.Errorf("%s returned error %v, want %v", what, err, want)
		}
	}
}

func TestErrorMapping(t *testing.T) {
	ctx := context.Background()
	var (
		notFound     = status(http.StatusNotFound, "NOT_FOUND")
		exists       = status(http.StatusConflict, "ALREADY_EXISTS")
		aborted      = status(http.StatusConflict, "ABORTED")
		precondition = status(http.StatusBadRequest, "FAILED_PRECONDITION")
		invalidArg   = status(http.StatusBadRequest, "INVALID_ARGUMENT")
		unavailable  = status(http.StatusServiceUnavailable, "UNAVAILABLE")
		denied       = status(http.StatusForbidden, "PERMISSION_DENIED")
		// An error that does not come from Firestore, such as a proxy's.
		gateway = reply{code: http.StatusBadGateway, body: "<html>Bad Gateway</html>"}
	)
	get := func(s *Store) error { _, _, err := s.Get(ctx, d1); return err }
	list := func(s *Store) error { _, err := s.List(ctx, d1, "groups"); return err }
	query := func(s *Store) error {
		_, err := s.Query(ctx, d1, "groups", store.Eq{Field: "state", Value: "OPEN"})
		return err
	}
	create := func(s *Store) error { _, err := s.Create(ctx, d1, store.Record{}); return err }
	update := func(s *Store) error { _, err := s.Update(ctx, d1, store.Record{}, version); return err }
	remove := func(s *Store) error { return s.Delete(ctx, d1, version) }
	firstSnapshotRead := func(s *Store) error {
		snap, err := s.Snapshot(ctx)
		if err != nil {
			return err
		}
		_, _, err = snap.Get(ctx, d1)
		return err
	}

	tests := []struct {
		name    string
		call    func(s *Store) error
		replies []reply
		want    error
	}{
		{name: "Get_NOT_FOUND", call: get, replies: []reply{notFound}, want: store.ErrNotFound},
		{name: "Get_INVALID_ARGUMENT", call: get, replies: []reply{invalidArg}, want: store.ErrInvalidArgument},
		{name: "Get_UNAVAILABLE", call: get, replies: []reply{unavailable}, want: errTransient},
		{name: "Get_PERMISSION_DENIED", call: get, replies: []reply{denied}, want: errTransient},
		{name: "Get_unrecognized_error", call: get, replies: []reply{gateway}, want: errTransient},

		{name: "List_INVALID_ARGUMENT", call: list, replies: []reply{invalidArg}, want: store.ErrInvalidArgument},
		// A parent that does not exist is not an error for a query, so this
		// status means something else; so does a missing index.
		{name: "List_NOT_FOUND", call: list, replies: []reply{notFound}, want: errTransient},
		{name: "Query_FAILED_PRECONDITION", call: query, replies: []reply{precondition}, want: errTransient},
		{name: "Query_UNAVAILABLE", call: query, replies: []reply{unavailable}, want: errTransient},

		{name: "Create_ALREADY_EXISTS", call: create, replies: []reply{exists}, want: store.ErrAlreadyExists},
		// ABORTED has the HTTP code of ALREADY_EXISTS.
		{name: "Create_ABORTED", call: create, replies: []reply{aborted}, want: errTransient},
		{name: "Create_INVALID_ARGUMENT", call: create, replies: []reply{invalidArg}, want: store.ErrInvalidArgument},

		{name: "Update_FAILED_PRECONDITION", call: update, replies: []reply{precondition}, want: store.ErrVersionMismatch},
		{name: "Update_NOT_FOUND", call: update, replies: []reply{notFound}, want: store.ErrVersionMismatch},
		{name: "Update_ABORTED", call: update, replies: []reply{aborted}, want: errTransient},
		{name: "Update_INVALID_ARGUMENT", call: update, replies: []reply{invalidArg}, want: store.ErrInvalidArgument},

		{name: "Delete_NOT_FOUND", call: remove, replies: []reply{notFound}, want: nil},
		{name: "Delete_FAILED_PRECONDITION_document_missing", call: remove, replies: []reply{precondition, notFound}, want: nil},
		{name: "Delete_FAILED_PRECONDITION_document_exists", call: remove, replies: []reply{precondition, ok(docJSON)}, want: store.ErrVersionMismatch},
		{name: "Delete_FAILED_PRECONDITION_check_fails", call: remove, replies: []reply{precondition, unavailable}, want: errTransient},
		{name: "Delete_ABORTED", call: remove, replies: []reply{aborted}, want: errTransient},
		{name: "Delete_INVALID_ARGUMENT", call: remove, replies: []reply{invalidArg}, want: store.ErrInvalidArgument},

		{name: "beginTransaction_UNAVAILABLE", call: firstSnapshotRead, replies: []reply{unavailable}, want: errTransient},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			s, sc := newScripted(t, tc.replies...)

			err := tc.call(s)

			checkError(t, "the call", err, tc.want)
			if got := len(sc.requests()); got != len(tc.replies) {
				t.Errorf("the call sent %d requests, want %d", got, len(tc.replies))
			}
		})
	}
}

func TestDelete_ChecksDocumentWithoutTransaction(t *testing.T) {
	s, sc := newScripted(t, status(http.StatusBadRequest, "FAILED_PRECONDITION"), status(http.StatusNotFound, "NOT_FOUND"))

	if err := s.Delete(context.Background(), d1, version); err != nil {
		t.Fatalf("Delete() failed: %v", err)
	}

	want := []exchange{
		{Method: "DELETE", URL: docs + "/domains/d1?currentDocument.updateTime=" + escapedUpdateTime},
		{Method: "GET", URL: docs + "/domains/d1"},
	}
	if diff := cmp.Diff(want, sc.requests()); diff != "" {
		t.Errorf("requests sent (-want +got):\n%s", diff)
	}
}

func TestRunQueryResponses(t *testing.T) {
	const (
		readTime = `{"readTime": "2026-11-12T10:00:01Z"}`
		slotA    = `{"document": {"name": "projects/p/databases/d/documents/domains/d1/slots/a", "fields": {"state": {"stringValue": "OPEN"}}, "updateTime": "2026-11-12T10:00:00.000001Z"}, "readTime": "2026-11-12T10:00:01Z"}`
		slotB    = `{"document": {"name": "projects/p/databases/d/documents/domains/d1/slots/b b", "updateTime": "2026-11-12T10:00:00.000002Z"}, "readTime": "2026-11-12T10:00:01Z"}`
	)
	a, b := d1.Child("slots", "a"), d1.Child("slots", "b b")
	tests := []struct {
		name  string
		reply reply
		want  []store.Item
		// wantErr is nil, one of the store's errors, or errTransient.
		wantErr error
	}{
		{
			name:  "documents",
			reply: ok(`[` + slotA + `, ` + slotB + `]`),
			want: []store.Item{
				{Key: a, Record: store.Record{"state": "OPEN"}, Version: store.NewVersion("2026-11-12T10:00:00.000001Z")},
				{Key: b, Record: store.Record{}, Version: store.NewVersion("2026-11-12T10:00:00.000002Z")},
			},
		},
		// An empty result is one element with only a read time.
		{name: "empty_result_read_time_only", reply: ok(`[` + readTime + `]`)},
		{name: "empty_list", reply: ok(`[]`)},
		{
			name:  "elements_without_document_skipped",
			reply: ok(`[` + readTime + `, ` + slotA + `, {"skippedResults": 0}]`),
			want:  []store.Item{{Key: a, Record: store.Record{"state": "OPEN"}, Version: store.NewVersion("2026-11-12T10:00:00.000001Z")}},
		},
		{
			// An error element under HTTP 200 fails the whole call.
			name:    "error_element_under_200",
			reply:   ok(`[` + slotA + `, {"error": {"code": 503, "message": "from the test", "status": "UNAVAILABLE"}}]`),
			wantErr: errTransient,
		},
		{
			// An error element is translated like any error.
			name:    "error_element_translated",
			reply:   ok(`[{"error": {"code": 400, "message": "from the test", "status": "INVALID_ARGUMENT"}}]`),
			wantErr: store.ErrInvalidArgument,
		},
		{
			// A failed call reports its error as an element.
			name:    "failed_call_reports_error_element",
			reply:   reply{code: http.StatusBadRequest, body: `[{"error": {"code": 400, "message": "from the test", "status": "INVALID_ARGUMENT"}}]`},
			wantErr: store.ErrInvalidArgument,
		},
		{name: "truncated_response", reply: ok(`[` + slotA + `, {"document": {"name": "projects/p`), wantErr: errTransient},
		{name: "response_not_a_list", reply: ok(slotA), wantErr: errTransient},
		{
			name:    "document_of_another_collection",
			reply:   ok(`[{"document": {"name": "projects/p/databases/d/documents/domains/d1/members/a", "updateTime": "2026-11-12T10:00:00Z"}}]`),
			wantErr: errTransient,
		},
		{
			// A document deeper than a direct child.
			name:    "document_deeper_than_child",
			reply:   ok(`[{"document": {"name": "projects/p/databases/d/documents/domains/d1/slots/a/slots/b", "updateTime": "2026-11-12T10:00:00Z"}}]`),
			wantErr: errTransient,
		},
		{
			name:    "document_of_another_database",
			reply:   ok(`[{"document": {"name": "projects/p/databases/other/documents/domains/d1/slots/a", "updateTime": "2026-11-12T10:00:00Z"}}]`),
			wantErr: errTransient,
		},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			s, _ := newScripted(t, tc.reply)

			got, err := s.List(context.Background(), d1, "slots")

			checkError(t, "List()", err, tc.wantErr)
			// Keys and versions are opaque; the test compares them whole.
			if diff := cmp.Diff(tc.want, got, cmp.AllowUnexported(store.Key{}, store.Version{})); diff != "" {
				t.Errorf("List() items (-want +got):\n%s", diff)
			}
		})
	}
}

func TestMalformedDocuments(t *testing.T) {
	tests := map[string]string{
		"not_JSON":             `<html>`,
		"no_updateTime":        `{"name": "n", "fields": {}}`,
		"value_of_no_type":     `{"fields": {"f": {}}, "updateTime": "` + updateTime + `"}`,
		"value_of_two_types":   `{"fields": {"f": {"stringValue": "a", "integerValue": "1"}}, "updateTime": "` + updateTime + `"}`,
		"value_that_is_bare":   `{"fields": {"f": "a"}, "updateTime": "` + updateTime + `"}`,
		"integer_of_letters":   `{"fields": {"f": {"integerValue": "one"}}, "updateTime": "` + updateTime + `"}`,
		"integer_too_large":    `{"fields": {"f": {"integerValue": "9223372036854775808"}}, "updateTime": "` + updateTime + `"}`,
		"fractional_integer":   `{"fields": {"f": {"integerValue": 1.5}}, "updateTime": "` + updateTime + `"}`,
		"timestamp_of_letters": `{"fields": {"f": {"timestampValue": "yesterday"}}, "updateTime": "` + updateTime + `"}`,
		"string_that_is_list":  `{"fields": {"f": {"stringValue": ["a"]}}, "updateTime": "` + updateTime + `"}`,
		"boolean_of_letters":   `{"fields": {"f": {"booleanValue": "yes"}}, "updateTime": "` + updateTime + `"}`,
		"double_of_letters":    `{"fields": {"f": {"doubleValue": "big"}}, "updateTime": "` + updateTime + `"}`,
		"bad_value_in_list":    `{"fields": {"f": {"arrayValue": {"values": [{}]}}}, "updateTime": "` + updateTime + `"}`,
		"bad_value_in_map":     `{"fields": {"f": {"mapValue": {"fields": {"g": {}}}}}, "updateTime": "` + updateTime + `"}`,
	}
	for name, body := range tests {
		t.Run(name, func(t *testing.T) {
			s, _ := newScripted(t, ok(body))

			got, _, err := s.Get(context.Background(), d1)

			checkError(t, "Get()", err, errTransient)
			if got != nil {
				t.Errorf("Get() returned record %v with an error, want none", got)
			}
		})
	}
}

func TestSnapshot_Requests(t *testing.T) {
	ctx := context.Background()
	// A transaction ID with characters that must be escaped in a URL.
	const (
		txn        = "+/8x=="
		escapedTxn = "%2B%2F8x%3D%3D"
	)
	s, sc := newScripted(t, ok(`{"transaction": "`+txn+`"}`), ok(docJSON), ok(`[]`), ok(`[]`), ok(`{}`))

	snap, err := s.Snapshot(ctx)
	if err != nil {
		t.Fatalf("Snapshot() failed: %v", err)
	}
	if got := sc.requests(); len(got) != 0 {
		t.Errorf("Snapshot() sent %v, want no request before the first read", got)
	}
	// An invalid read does not begin the transaction.
	if _, _, err := snap.Get(ctx, store.Key{}); !errors.Is(err, store.ErrInvalidArgument) {
		t.Errorf("snapshot Get(root) returned error %v, want %v", err, store.ErrInvalidArgument)
	}
	if got := sc.requests(); len(got) != 0 {
		t.Errorf("an invalid snapshot read sent %v, want no request", got)
	}

	if _, _, err := snap.Get(ctx, d1); err != nil {
		t.Fatalf("snapshot Get() failed: %v", err)
	}
	if _, err := snap.List(ctx, d1, "groups"); err != nil {
		t.Fatalf("snapshot List() failed: %v", err)
	}
	if _, err := snap.Query(ctx, d1, "groups", store.Eq{Field: "state", Value: "OPEN"}); err != nil {
		t.Fatalf("snapshot Query() failed: %v", err)
	}
	// Close releases the transaction even if the context is done.
	done, cancel := context.WithCancel(ctx)
	cancel()
	if err := snap.Close(done); err != nil {
		t.Errorf("snapshot Close() with a done context returned error %v, want nil", err)
	}
	if err := snap.Close(ctx); err != nil {
		t.Errorf("second snapshot Close() returned error %v, want nil", err)
	}
	if _, _, err := snap.Get(ctx, d1); !errors.Is(err, store.ErrClosed) {
		t.Errorf("snapshot Get() after Close returned error %v, want %v", err, store.ErrClosed)
	}

	want := []exchange{
		{Method: "POST", URL: docs + ":beginTransaction", Body: parse(t, `{"options": {"readOnly": {}}}`)},
		{Method: "GET", URL: docs + "/domains/d1?transaction=" + escapedTxn},
		{
			Method: "POST",
			URL:    docs + "/domains/d1:runQuery",
			Body:   parse(t, `{"structuredQuery": {"from": [{"collectionId": "groups"}]}, "transaction": "`+txn+`"}`),
		},
		{
			Method: "POST",
			URL:    docs + "/domains/d1:runQuery",
			Body: parse(t, `{"structuredQuery": {
				"from": [{"collectionId": "groups"}],
				"where": {"fieldFilter": {"field": {"fieldPath": "state"}, "op": "EQUAL", "value": {"stringValue": "OPEN"}}}
			}, "transaction": "`+txn+`"}`),
		},
		{Method: "POST", URL: docs + ":rollback", Body: parse(t, `{"transaction": "`+txn+`"}`)},
	}
	if diff := cmp.Diff(want, sc.requests()); diff != "" {
		t.Errorf("requests sent by the snapshot (-want +got):\n%s", diff)
	}
}

func TestSnapshot_NeverReadSendsNothing(t *testing.T) {
	ctx := context.Background()
	s, sc := newScripted(t)

	snap, err := s.Snapshot(ctx)
	if err != nil {
		t.Fatalf("Snapshot() failed: %v", err)
	}
	if err := snap.Close(ctx); err != nil {
		t.Errorf("Close() of a snapshot that was never read returned error %v, want nil", err)
	}

	if got := sc.requests(); len(got) != 0 {
		t.Errorf("a snapshot that was never read sent %v, want no request", got)
	}
}

func TestSnapshot_BeginsAgainAfterFailedBegin(t *testing.T) {
	ctx := context.Background()
	s, sc := newScripted(t, status(http.StatusServiceUnavailable, "UNAVAILABLE"), ok(`{"transaction": "dHhu"}`), ok(docJSON))
	snap, err := s.Snapshot(ctx)
	if err != nil {
		t.Fatalf("Snapshot() failed: %v", err)
	}

	_, _, first := snap.Get(ctx, d1)
	_, _, second := snap.Get(ctx, d1)

	checkError(t, "the first snapshot Get()", first, errTransient)
	checkError(t, "the second snapshot Get()", second, nil)
	if got := len(sc.requests()); got != 3 {
		t.Errorf("the snapshot sent %d requests, want 3: a failed begin, a begin, and a read", got)
	}
}

func TestSnapshot_ClosedEvenIfRollbackFails(t *testing.T) {
	ctx := context.Background()
	s, sc := newScripted(t, ok(`{"transaction": "dHhu"}`), ok(docJSON), status(http.StatusServiceUnavailable, "UNAVAILABLE"))
	snap, err := s.Snapshot(ctx)
	if err != nil {
		t.Fatalf("Snapshot() failed: %v", err)
	}
	if _, _, err := snap.Get(ctx, d1); err != nil {
		t.Fatalf("snapshot Get() failed: %v", err)
	}

	closeErr := snap.Close(ctx)

	checkError(t, "Close() with a failing rollback", closeErr, errTransient)
	if _, _, err := snap.Get(ctx, d1); !errors.Is(err, store.ErrClosed) {
		t.Errorf("snapshot Get() after a failed Close returned error %v, want %v", err, store.ErrClosed)
	}
	if err := snap.Close(ctx); err != nil {
		t.Errorf("second Close() returned error %v, want nil", err)
	}
	if got := len(sc.requests()); got != 3 {
		t.Errorf("the snapshot sent %d requests, want 3: the rollback is not sent twice", got)
	}
}

func TestRequest_CallerDeadline(t *testing.T) {
	// The server answers only when the client gives up.
	blocked := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		<-r.Context().Done()
	})
	s := newTestStore(t, blocked)
	ctx, cancel := context.WithTimeout(context.Background(), 50*time.Millisecond)
	defer cancel()

	_, _, err := s.Get(ctx, d1)

	if !errors.Is(err, context.DeadlineExceeded) {
		t.Errorf("Get() against a server that does not answer returned error %v, want %v", err, context.DeadlineExceeded)
	}
}

func TestNew(t *testing.T) {
	valid := Config{Client: http.DefaultClient, Project: "p", Database: "d"}
	if _, err := New(valid); err != nil {
		t.Errorf("New(%+v) failed: %v", valid, err)
	}
	for name, change := range map[string]func(*Config){
		"no_client":   func(c *Config) { c.Client = nil },
		"no_project":  func(c *Config) { c.Project = "" },
		"no_database": func(c *Config) { c.Database = "" },
		// A resource name where an ID belongs.
		"project_with_slash":  func(c *Config) { c.Project = "projects/p" },
		"database_with_slash": func(c *Config) { c.Database = "projects/p/databases/d" },
	} {
		t.Run(name, func(t *testing.T) {
			cfg := valid
			change(&cfg)
			if _, err := New(cfg); err == nil {
				t.Errorf("New(%+v) returned no error, want one", cfg)
			}
		})
	}
}

// newConfigured is newScripted for a Store built from a changed Config.
func newConfigured(t *testing.T, change func(*Config), replies ...reply) (*Store, *script) {
	t.Helper()
	sc := &script{t: t, replies: replies}
	ts := httptest.NewServer(sc)
	t.Cleanup(ts.Close)
	cfg := Config{Client: ts.Client(), Project: testProject, Database: testDatabase, Endpoint: ts.URL}
	change(&cfg)
	s, err := New(cfg)
	if err != nil {
		t.Fatalf("New(%+v) failed: %v", cfg, err)
	}
	return s, sc
}

func TestNew_EndpointTrailingSlash(t *testing.T) {
	s, sc := newConfigured(t, func(c *Config) { c.Endpoint += "//" }, ok(docJSON))

	if _, _, err := s.Get(context.Background(), d1); err != nil {
		t.Fatalf("Get() failed: %v", err)
	}

	want := []exchange{{Method: "GET", URL: docs + "/domains/d1"}}
	if diff := cmp.Diff(want, sc.requests()); diff != "" {
		t.Errorf("requests sent (-want +got):\n%s", diff)
	}
}

// TestNew_EscapesProjectAndDatabase uses a domain-scoped project ID,
// which has a colon, and the default database, whose ID has parentheses. They
// are escaped in the URL and appear as they are in document names.
func TestNew_EscapesProjectAndDatabase(t *testing.T) {
	const element = `[{"document": {"name": "projects/example.com:p/databases/(default)/documents/domains/d1", "updateTime": "` + updateTime + `"}}]`
	s, sc := newConfigured(t, func(c *Config) { c.Project, c.Database = "example.com:p", "(default)" }, ok(element))

	items, err := s.List(context.Background(), store.Key{}, "domains")
	if err != nil {
		t.Fatalf("List() failed: %v", err)
	}

	if len(items) != 1 || items[0].Key != d1 {
		t.Errorf("List() = %v, want the one document %q", items, d1)
	}
	want := []exchange{{
		Method: "POST",
		URL:    "/v1/projects/example.com%3Ap/databases/%28default%29/documents:runQuery",
		Body:   parse(t, `{"structuredQuery": {"from": [{"collectionId": "domains"}]}}`),
	}}
	if diff := cmp.Diff(want, sc.requests()); diff != "" {
		t.Errorf("requests sent (-want +got):\n%s", diff)
	}
}
