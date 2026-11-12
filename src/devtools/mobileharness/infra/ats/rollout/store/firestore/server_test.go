package firestore

import (
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"net/url"
	"slices"
	"strconv"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/store/fake"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/store"
)

const (
	testProject  = "p"
	testDatabase = "d"

	// documentsPath is the path of the test database's documents resource,
	// and documentsName the prefix of its document names.
	documentsPath = "/v1/projects/p/databases/d/documents"
	documentsName = "projects/p/databases/d/documents/"
)

// server is a hermetic stand-in for the Firestore REST API. It keeps its
// documents in the in-memory fake store and serves the seven calls the
// adapter makes.
//
// It is written from the same reading of the API as the adapter, so a test
// that passes against it shows that the two agree, not that Firestore behaves
// this way; only a run against a real database shows that. Where the reading
// is uncertain, the server takes the answer that is harder for the adapter:
//
//   - A read-only transaction sees the database as of beginTransaction, not
//     as of its first read.
//   - A write with a precondition on a missing document fails with
//     missingStatus, which a test sets to each of the two statuses Firestore
//     might use.
type server struct {
	fake *fake.Store
	// missingStatus is "FAILED_PRECONDITION" or "NOT_FOUND".
	missingStatus string

	// mu makes each write's check and change one step, and guards the
	// fields below.
	mu      sync.Mutex
	txns    map[string]store.Snapshot
	lastTxn int
}

func newServer(missingStatus string) *server {
	return &server{fake: fake.New(), missingStatus: missingStatus, txns: make(map[string]store.Snapshot)}
}

// newTestStore returns a Store that talks to sv.
func newTestStore(t *testing.T, sv http.Handler) *Store {
	t.Helper()
	ts := httptest.NewServer(sv)
	t.Cleanup(ts.Close)
	s, err := New(Config{Client: ts.Client(), Project: testProject, Database: testDatabase, Endpoint: ts.URL})
	if err != nil {
		t.Fatalf("New() failed: %v", err)
	}
	return s
}

// failure is an error response.
type failure struct {
	code    int
	status  string
	message string
}

func (f *failure) Error() string { return f.status + ": " + f.message }

func invalid(format string, args ...any) *failure {
	return &failure{code: http.StatusBadRequest, status: "INVALID_ARGUMENT", message: fmt.Sprintf(format, args...)}
}

// failureOf returns the error response for an error of the fake store.
func failureOf(err error) *failure {
	var f *failure
	switch {
	case errors.As(err, &f):
		return f
	case errors.Is(err, store.ErrNotFound):
		return &failure{code: http.StatusNotFound, status: "NOT_FOUND", message: err.Error()}
	case errors.Is(err, store.ErrAlreadyExists):
		return &failure{code: http.StatusConflict, status: "ALREADY_EXISTS", message: err.Error()}
	case errors.Is(err, store.ErrVersionMismatch):
		return &failure{code: http.StatusBadRequest, status: "FAILED_PRECONDITION", message: err.Error()}
	case errors.Is(err, store.ErrInvalidArgument), errors.Is(err, store.ErrClosed):
		return invalid("%v", err)
	}
	return &failure{code: http.StatusInternalServerError, status: "INTERNAL", message: err.Error()}
}

func (f *failure) body() map[string]any {
	return map[string]any{"error": map[string]any{"code": f.code, "message": f.message, "status": f.status}}
}

// The fake's versions are counters. The server shows version n as the
// updateTime n microseconds after epoch, and reads it back the same way.
var epoch = time.Date(2026, 1, 1, 0, 0, 0, 0, time.UTC)

func updateTimeOf(v store.Version) string {
	n, err := strconv.ParseInt(v.Token(), 10, 64)
	if err != nil {
		panic(fmt.Sprintf("the fake store returned version %q, want a counter", v.Token()))
	}
	return epoch.Add(time.Duration(n) * time.Microsecond).Format(time.RFC3339Nano)
}

func versionOf(updateTime string) (store.Version, error) {
	t, err := time.Parse(time.RFC3339, updateTime)
	if err != nil {
		return store.Version{}, invalid("updateTime %q: %v", updateTime, err)
	}
	return store.NewVersion(strconv.FormatInt(int64(t.Sub(epoch)/time.Microsecond), 10)), nil
}

// documentOf returns the JSON form of a stored record.
func documentOf(key store.Key, r store.Record, v store.Version) (map[string]any, error) {
	fields, err := encodeFields(r)
	if err != nil {
		return nil, err
	}
	at := updateTimeOf(v)
	return map[string]any{"name": documentsName + key.String(), "fields": fields, "createTime": at, "updateTime": at}, nil
}

func (sv *server) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	// A custom method is a suffix of the path: {resource}:{method}.
	path, method := r.URL.EscapedPath(), ""
	if i := strings.LastIndex(path, ":"); i >= 0 && r.Method == http.MethodPost {
		if m := path[i+1:]; m == "runQuery" || m == "beginTransaction" || m == "rollback" {
			path, method = path[:i], m
		}
	}
	response, err := sv.serve(r, path, method)
	w.Header().Set("Content-Type", "application/json")
	if err != nil {
		f := failureOf(err)
		response = f.body()
		if method == "runQuery" {
			// A streaming method reports its error as an element.
			response = []any{response}
		}
		w.WriteHeader(f.code)
	}
	if err := json.NewEncoder(w).Encode(response); err != nil {
		panic(fmt.Sprintf("encoding a response: %v", err))
	}
}

// serve handles one request to path, the escaped URL path without the custom
// method, and returns the response body.
func (sv *server) serve(r *http.Request, path, method string) (any, error) {
	rest, ok := strings.CutPrefix(path, documentsPath)
	if !ok || (rest != "" && !strings.HasPrefix(rest, "/")) {
		return nil, &failure{code: http.StatusNotFound, status: "NOT_FOUND", message: "no such resource: " + path}
	}
	// segments alternates collection IDs and document IDs.
	var segments []string
	if rest != "" {
		for _, seg := range strings.Split(rest[1:], "/") {
			unescaped, err := url.PathUnescape(seg)
			if err != nil {
				return nil, invalid("path segment %q: %v", seg, err)
			}
			segments = append(segments, unescaped)
		}
	}
	body, err := io.ReadAll(r.Body)
	if err != nil {
		return nil, err
	}
	isDocument := len(segments) > 0 && len(segments)%2 == 0
	isCollection := len(segments)%2 == 1

	switch {
	case method == "beginTransaction" && len(segments) == 0:
		return sv.beginTransaction(r, body)
	case method == "rollback" && len(segments) == 0:
		return sv.rollback(r, body)
	case method == "runQuery" && !isCollection:
		return sv.runQuery(r, keyOf(segments), body)
	case method != "":
		return nil, invalid("%s on %s", method, path)
	case r.Method == http.MethodPost && isCollection:
		n := len(segments) - 1
		return sv.create(r, keyOf(segments[:n]).Child(segments[n], r.URL.Query().Get("documentId")), body)
	case r.Method == http.MethodGet && isDocument:
		return sv.get(r, keyOf(segments))
	case r.Method == http.MethodPatch && isDocument:
		return sv.patch(r, keyOf(segments), body)
	case r.Method == http.MethodDelete && isDocument:
		return sv.delete(r, keyOf(segments))
	}
	return nil, invalid("%s on %s", r.Method, path)
}

// keyOf returns the key of the document with the given path segments, or the
// root for none.
func keyOf(segments []string) store.Key {
	var key store.Key
	for i := 0; i+1 < len(segments); i += 2 {
		key = key.Child(segments[i], segments[i+1])
	}
	return key
}

// reader returns what a read with the given transaction ID reads from: the
// transaction's snapshot, or the latest state for no transaction.
func (sv *server) reader(txn string) (store.Reader, error) {
	if txn == "" {
		return sv.fake, nil
	}
	sv.mu.Lock()
	defer sv.mu.Unlock()
	snap, ok := sv.txns[txn]
	if !ok {
		return nil, invalid("transaction %q does not exist or was released", txn)
	}
	return snap, nil
}

func (sv *server) beginTransaction(r *http.Request, body []byte) (any, error) {
	var request struct {
		Options struct {
			ReadOnly *struct{} `json:"readOnly"`
		} `json:"options"`
	}
	if err := json.Unmarshal(body, &request); err != nil || request.Options.ReadOnly == nil {
		return nil, invalid("want a read-only transaction, got %s", body)
	}
	snap, err := sv.fake.Snapshot(r.Context())
	if err != nil {
		return nil, err
	}
	// The fake fixes a snapshot's view at its first read. Read now, so that
	// the transaction sees the database as of this call.
	if _, err := snap.List(r.Context(), store.Key{}, "any"); err != nil {
		return nil, err
	}
	sv.mu.Lock()
	defer sv.mu.Unlock()
	sv.lastTxn++
	// The ID is bytes, sent as base64. These bytes give an ID with "+", "/",
	// and "=", which a client must escape in a URL.
	id := base64.StdEncoding.EncodeToString(append([]byte{0xfb, 0xff}, strconv.Itoa(sv.lastTxn)...))
	sv.txns[id] = snap
	return map[string]any{"transaction": id}, nil
}

func (sv *server) rollback(r *http.Request, body []byte) (any, error) {
	var request struct {
		Transaction string `json:"transaction"`
	}
	if err := json.Unmarshal(body, &request); err != nil {
		return nil, invalid("rollback request %s: %v", body, err)
	}
	sv.mu.Lock()
	defer sv.mu.Unlock()
	snap, ok := sv.txns[request.Transaction]
	if !ok {
		return nil, invalid("transaction %q does not exist or was released", request.Transaction)
	}
	delete(sv.txns, request.Transaction)
	return map[string]any{}, snap.Close(r.Context())
}

func (sv *server) runQuery(r *http.Request, parent store.Key, body []byte) (any, error) {
	var request struct {
		StructuredQuery struct {
			From []struct {
				CollectionID   string `json:"collectionId"`
				AllDescendants bool   `json:"allDescendants"`
			} `json:"from"`
			Where *struct {
				FieldFilter struct {
					Field struct {
						FieldPath string `json:"fieldPath"`
					} `json:"field"`
					Op    string          `json:"op"`
					Value json.RawMessage `json:"value"`
				} `json:"fieldFilter"`
			} `json:"where"`
		} `json:"structuredQuery"`
		Transaction string `json:"transaction"`
	}
	if err := json.Unmarshal(body, &request); err != nil {
		return nil, invalid("query %s: %v", body, err)
	}
	query := request.StructuredQuery
	if len(query.From) != 1 || query.From[0].AllDescendants {
		return nil, invalid("want one collection without its descendants, got %s", body)
	}
	rd, err := sv.reader(request.Transaction)
	if err != nil {
		return nil, err
	}
	var items []store.Item
	if query.Where == nil {
		items, err = rd.List(r.Context(), parent, query.From[0].CollectionID)
	} else {
		filter := query.Where.FieldFilter
		value, decodeErr := decodeValue(filter.Value, "where")
		s, isString := value.(string)
		if decodeErr != nil || !isString || filter.Op != "EQUAL" {
			return nil, invalid("want a string equality filter, got %s", body)
		}
		items, err = rd.Query(r.Context(), parent, query.From[0].CollectionID, store.Eq{Field: filter.Field.FieldPath, Value: s})
	}
	if err != nil {
		return nil, err
	}
	// Firestore returns documents in the order of their names.
	slices.SortFunc(items, func(a, b store.Item) int { return strings.Compare(a.Key.String(), b.Key.String()) })
	readTime := time.Now().UTC().Format(time.RFC3339Nano)
	if len(items) == 0 {
		// An empty result is one element with only the read time.
		return []any{map[string]any{"readTime": readTime}}, nil
	}
	elements := make([]any, len(items))
	for i, it := range items {
		doc, err := documentOf(it.Key, it.Record, it.Version)
		if err != nil {
			return nil, err
		}
		elements[i] = map[string]any{"document": doc, "readTime": readTime}
	}
	return elements, nil
}

func (sv *server) get(r *http.Request, key store.Key) (any, error) {
	rd, err := sv.reader(r.URL.Query().Get("transaction"))
	if err != nil {
		return nil, err
	}
	record, v, err := rd.Get(r.Context(), key)
	if err != nil {
		return nil, err
	}
	return documentOf(key, record, v)
}

// fieldsOf returns the record in the body of a write.
func fieldsOf(body []byte) (store.Record, error) {
	var doc document
	if err := json.Unmarshal(body, &doc); err != nil {
		return nil, invalid("document %s: %v", body, err)
	}
	fields, err := decodeFields(doc.Fields, "")
	if err != nil {
		return nil, invalid("%v", err)
	}
	return fields, nil
}

func (sv *server) create(r *http.Request, key store.Key, body []byte) (any, error) {
	record, err := fieldsOf(body)
	if err != nil {
		return nil, err
	}
	sv.mu.Lock()
	defer sv.mu.Unlock()
	v, err := sv.fake.Create(r.Context(), key, record)
	if err != nil {
		return nil, err
	}
	return documentOf(key, record, v)
}

// current returns the version of the document at key if it meets the
// precondition of the request, which the adapter always sends. sv.mu must be
// held.
func (sv *server) current(r *http.Request, key store.Key) (store.Version, error) {
	updateTime := r.URL.Query().Get("currentDocument.updateTime")
	if updateTime == "" {
		return store.Version{}, invalid("%s of %q without a precondition", r.Method, key)
	}
	want, err := versionOf(updateTime)
	if err != nil {
		return store.Version{}, err
	}
	_, v, err := sv.fake.Get(r.Context(), key)
	switch {
	case errors.Is(err, store.ErrNotFound):
		code := http.StatusBadRequest
		if sv.missingStatus == "NOT_FOUND" {
			code = http.StatusNotFound
		}
		return store.Version{}, &failure{code: code, status: sv.missingStatus, message: fmt.Sprintf("%q does not exist", key)}
	case err != nil:
		return store.Version{}, err
	case v != want:
		return store.Version{}, &failure{code: http.StatusBadRequest, status: "FAILED_PRECONDITION", message: fmt.Sprintf("%q was updated at %s, not %s", key, updateTimeOf(v), updateTime)}
	}
	return v, nil
}

func (sv *server) patch(r *http.Request, key store.Key, body []byte) (any, error) {
	record, err := fieldsOf(body)
	if err != nil {
		return nil, err
	}
	sv.mu.Lock()
	defer sv.mu.Unlock()
	v, err := sv.current(r, key)
	if err != nil {
		return nil, err
	}
	if v, err = sv.fake.Update(r.Context(), key, record, v); err != nil {
		return nil, err
	}
	return documentOf(key, record, v)
}

func (sv *server) delete(r *http.Request, key store.Key) (any, error) {
	sv.mu.Lock()
	defer sv.mu.Unlock()
	v, err := sv.current(r, key)
	if err != nil {
		return nil, err
	}
	return map[string]any{}, sv.fake.Delete(r.Context(), key, v)
}
