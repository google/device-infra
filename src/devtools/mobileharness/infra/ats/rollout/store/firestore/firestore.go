// Package firestore implements store.Store on a Firestore database in Native
// mode, through the Firestore REST API (v1).
//
// A record is a document, a record kind is a collection, and a child record
// is a document in a subcollection: the key domains/d1/groups/controller is
// the document with that path. A record's version is the document's
// updateTime, and a conditional write is a write with a precondition on it.
// A snapshot is a read-only transaction.
//
// The package does no authentication: the caller passes an http.Client that
// already adds credentials with the scope
// https://www.googleapis.com/auth/datastore.
//
// The adapter never retries. A write that is sent again after it was applied
// would fail as a lost race, and for a read the caller's next run is the
// retry. Every error other than the ones package store defines is to be
// treated as transient.
package firestore

import (
	"bytes"
	"cmp"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"time"

	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/store"
)

// DefaultEndpoint is the production endpoint of the Firestore REST API.
const DefaultEndpoint = "https://firestore.googleapis.com"

// rollbackTimeout bounds the call that releases a snapshot's transaction.
const rollbackTimeout = 5 * time.Second

// Config says which database a Store talks to, and how.
type Config struct {
	// Client sends the requests. It must add the credentials.
	Client *http.Client
	// Project is the ID of the Google Cloud project that has the database,
	// not its resource name: "my-project", not "projects/my-project".
	Project string
	// Database is the ID of the Firestore database, not its resource name.
	Database string
	// Endpoint is the base URL of the API; a trailing slash is ignored. Empty
	// means DefaultEndpoint; tests set it to a local server.
	Endpoint string
}

// Store is a store.Store on one Firestore database. It is safe for concurrent
// use.
type Store struct {
	client *http.Client
	// documents is the URL of the database's documents resource:
	// {endpoint}/v1/projects/{project}/databases/{database}/documents.
	documents string
	// namePrefix is what every document name of the database starts with:
	// projects/{project}/databases/{database}/documents/.
	namePrefix string
}

var _ store.Store = (*Store)(nil)

// New returns a Store for the database that cfg names. It makes no request.
func New(cfg Config) (*Store, error) {
	switch {
	case cfg.Client == nil:
		return nil, errors.New("firestore: Config.Client is nil")
	case cfg.Project == "":
		return nil, errors.New("firestore: Config.Project is empty")
	case cfg.Database == "":
		return nil, errors.New("firestore: Config.Database is empty")
	case strings.Contains(cfg.Project, "/"):
		// An ID, not a resource name such as "projects/p": the URL and the
		// document names are both built from it and would disagree.
		return nil, fmt.Errorf("firestore: Config.Project %q contains \"/\", want a project ID", cfg.Project)
	case strings.Contains(cfg.Database, "/"):
		return nil, fmt.Errorf("firestore: Config.Database %q contains \"/\", want a database ID", cfg.Database)
	}
	endpoint := strings.TrimRight(cfg.Endpoint, "/")
	if endpoint == "" {
		endpoint = DefaultEndpoint
	}
	return &Store{
		client:     cfg.Client,
		documents:  fmt.Sprintf("%s/v1/projects/%s/databases/%s/documents", endpoint, escapeSegment(cfg.Project), escapeSegment(cfg.Database)),
		namePrefix: fmt.Sprintf("projects/%s/databases/%s/documents/", cfg.Project, cfg.Database),
	}, nil
}

// Each operation translates the statuses that mean something for it and
// leaves every other error as it is (transient). The tables differ on
// purpose: Firestore may answer a precondition on a missing document with
// either FAILED_PRECONDITION or NOT_FOUND, and for an Update both mean that
// the record is not at the expected version, while FAILED_PRECONDITION on a
// query means a missing index, which is not a store error at all.
var (
	readStatuses   = map[string]error{"INVALID_ARGUMENT": store.ErrInvalidArgument}
	getStatuses    = map[string]error{"NOT_FOUND": store.ErrNotFound, "INVALID_ARGUMENT": store.ErrInvalidArgument}
	createStatuses = map[string]error{"ALREADY_EXISTS": store.ErrAlreadyExists, "INVALID_ARGUMENT": store.ErrInvalidArgument}
	updateStatuses = map[string]error{
		"FAILED_PRECONDITION": store.ErrVersionMismatch,
		"NOT_FOUND":           store.ErrVersionMismatch,
		"INVALID_ARGUMENT":    store.ErrInvalidArgument,
	}
	deleteStatuses = map[string]error{"INVALID_ARGUMENT": store.ErrInvalidArgument}
)

// apiError is an error that Firestore reported.
type apiError struct {
	HTTPCode int
	// Status is the canonical status name, such as "NOT_FOUND". It is empty
	// if the response did not carry one.
	Status  string
	Message string
}

func (e *apiError) Error() string {
	return fmt.Sprintf("firestore: %s (HTTP %d): %s", cmp.Or(e.Status, "error"), e.HTTPCode, e.Message)
}

// errorBody is the JSON form of an error, alone in a response or as one
// element of a streamed response.
type errorBody struct {
	Error *struct {
		Message string `json:"message"`
		Status  string `json:"status"`
	} `json:"error"`
}

// hasStatus reports whether err is a Firestore error with the given status.
func hasStatus(err error, status string) bool {
	var api *apiError
	return errors.As(err, &api) && api.Status == status
}

// translate returns err wrapped in the store error that statuses gives for
// its Firestore status, or err itself if it has none.
func translate(err error, statuses map[string]error) error {
	var api *apiError
	if errors.As(err, &api) {
		if target, ok := statuses[api.Status]; ok {
			return fmt.Errorf("%w: %v", target, err)
		}
	}
	return err
}

// call sends one request with the JSON form of in as its body, if in is not
// nil, and decodes the response into out, if out is not nil. The request
// carries ctx, so the caller's deadline bounds it.
func (s *Store) call(ctx context.Context, method, target string, in, out any) error {
	var body io.Reader
	if in != nil {
		data, err := json.Marshal(in)
		if err != nil {
			return fmt.Errorf("encoding the request: %v", err)
		}
		body = bytes.NewReader(data)
	}
	req, err := http.NewRequestWithContext(ctx, method, target, body)
	if err != nil {
		return err
	}
	if in != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	resp, err := s.client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	data, err := io.ReadAll(resp.Body)
	if err != nil {
		return fmt.Errorf("reading the response: %w", err)
	}
	if resp.StatusCode < 200 || resp.StatusCode > 299 {
		return newAPIError(resp.StatusCode, data)
	}
	if out == nil {
		return nil
	}
	if err := json.Unmarshal(data, out); err != nil {
		return fmt.Errorf("decoding the response: %v", err)
	}
	return nil
}

// newAPIError reads the error out of the body of a failed request. The status
// is taken from the body, never from the HTTP code alone: 409 is both
// ALREADY_EXISTS and ABORTED. A streaming method reports the error as the
// element of a list.
func newAPIError(httpCode int, body []byte) *apiError {
	var one errorBody
	if json.Unmarshal(body, &one) == nil && one.Error != nil {
		return &apiError{HTTPCode: httpCode, Status: one.Error.Status, Message: one.Error.Message}
	}
	var list []errorBody
	if json.Unmarshal(body, &list) == nil && len(list) > 0 && list[0].Error != nil {
		return &apiError{HTTPCode: httpCode, Status: list[0].Error.Status, Message: list[0].Error.Message}
	}
	const maxLen = 200
	msg := string(body)
	if len(msg) > maxLen {
		msg = msg[:maxLen] + "..."
	}
	return &apiError{HTTPCode: httpCode, Message: "unrecognized error response: " + msg}
}

// escapeSegment returns one path segment (a project, a database, a kind, or
// an ID) escaped for use in a URL path. It escapes ":" as well, which
// url.PathEscape leaves alone and which a valid kind or ID may contain: the
// API takes a custom method such as ":runQuery" off the end of the path
// before it decodes the path, so a create in the collection "x:runQuery"
// would otherwise be read as a query.
func escapeSegment(segment string) string {
	return strings.ReplaceAll(url.PathEscape(segment), ":", "%3A")
}

// escapedPath returns the path of key with each kind and ID escaped for use
// in a URL. Kinds and IDs of a valid key contain no "/".
func escapedPath(key store.Key) string {
	segments := strings.Split(key.String(), "/")
	for i, seg := range segments {
		segments[i] = escapeSegment(seg)
	}
	return strings.Join(segments, "/")
}

// documentURL returns the URL of the document at key.
func (s *Store) documentURL(key store.Key) string {
	return s.documents + "/" + escapedPath(key)
}

// parentURL returns the URL of the resource whose collections hold the
// children of parent: a document, or the documents resource for the root.
func (s *Store) parentURL(parent store.Key) string {
	if parent.IsRoot() {
		return s.documents
	}
	return s.documentURL(parent)
}

// Get implements store.Reader.
func (s *Store) Get(ctx context.Context, key store.Key) (store.Record, store.Version, error) {
	if err := key.Validate(); err != nil {
		return nil, store.Version{}, err
	}
	if err := ctx.Err(); err != nil {
		return nil, store.Version{}, err
	}
	return s.get(ctx, key, "")
}

// List implements store.Reader.
func (s *Store) List(ctx context.Context, parent store.Key, kind string) ([]store.Item, error) {
	if err := store.ValidateList(parent, kind); err != nil {
		return nil, err
	}
	if err := ctx.Err(); err != nil {
		return nil, err
	}
	return s.runQuery(ctx, parent, kind, nil, "")
}

// Query implements store.Reader.
func (s *Store) Query(ctx context.Context, parent store.Key, kind string, eq store.Eq) ([]store.Item, error) {
	if err := store.ValidateQuery(parent, kind, eq); err != nil {
		return nil, err
	}
	if err := ctx.Err(); err != nil {
		return nil, err
	}
	return s.runQuery(ctx, parent, kind, &eq, "")
}

// get reads one document, through the transaction txn if it is not empty.
func (s *Store) get(ctx context.Context, key store.Key, txn string) (store.Record, store.Version, error) {
	target := s.documentURL(key)
	if txn != "" {
		target += "?transaction=" + url.QueryEscape(txn)
	}
	var doc document
	if err := s.call(ctx, http.MethodGet, target, nil, &doc); err != nil {
		return nil, store.Version{}, fmt.Errorf("getting %q: %w", key, translate(err, getStatuses))
	}
	r, v, err := doc.record()
	if err != nil {
		return nil, store.Version{}, fmt.Errorf("getting %q: %v", key, err)
	}
	return r, v, nil
}

// runQuery returns the documents of the collection kind under parent, all of
// them or those that match eq, through the transaction txn if it is not
// empty. One call returns every match as of one read time, so there is no
// paging. List and Query are the same call, without and with the filter.
func (s *Store) runQuery(ctx context.Context, parent store.Key, kind string, eq *store.Eq, txn string) ([]store.Item, error) {
	query := map[string]any{"from": []any{map[string]any{"collectionId": kind}}}
	if eq != nil {
		query["where"] = map[string]any{"fieldFilter": map[string]any{
			"field": map[string]any{"fieldPath": eq.Field},
			"op":    "EQUAL",
			"value": map[string]any{"stringValue": eq.Value},
		}}
	}
	request := map[string]any{"structuredQuery": query}
	if txn != "" {
		request["transaction"] = txn
	}
	describe := func(err error) error {
		if eq != nil {
			return fmt.Errorf("querying %q of %q for %s == %q: %w", kind, parent, eq.Field, eq.Value, err)
		}
		return fmt.Errorf("listing %q of %q: %w", kind, parent, err)
	}

	var elements []struct {
		errorBody
		Document *document `json:"document"`
	}
	if err := s.call(ctx, http.MethodPost, s.parentURL(parent)+":runQuery", request, &elements); err != nil {
		return nil, describe(translate(err, readStatuses))
	}
	// The name of every returned document is the collection's name plus an ID.
	collection := s.namePrefix + kind + "/"
	if !parent.IsRoot() {
		collection = s.namePrefix + parent.String() + "/" + kind + "/"
	}
	var items []store.Item
	for _, e := range elements {
		switch {
		case e.Error != nil:
			// Firestore reports a failure in mid-stream this way, under HTTP
			// 200. A partial result is no result.
			err := &apiError{HTTPCode: http.StatusOK, Status: e.Error.Status, Message: e.Error.Message}
			return nil, describe(translate(err, readStatuses))
		case e.Document == nil:
			// For example the element that carries only the read time of an
			// empty result.
			continue
		}
		id, ok := strings.CutPrefix(e.Document.Name, collection)
		if !ok || id == "" || strings.Contains(id, "/") {
			return nil, describe(fmt.Errorf("the response has document %q, which is not a direct child", e.Document.Name))
		}
		r, v, err := e.Document.record()
		if err != nil {
			return nil, describe(err)
		}
		items = append(items, store.Item{Key: parent.Child(kind, id), Record: r, Version: v})
	}
	return items, nil
}

// Snapshot implements store.Store. It makes no request: the read-only
// transaction begins at the snapshot's first valid read. The contract fixes
// the view at that read, so a transaction that began earlier could not show
// a record created in between, whenever Firestore picks the read time.
//
// Firestore ends a read-only transaction after about 60 seconds without a
// read, or 270 seconds in all; reads then fail.
func (s *Store) Snapshot(ctx context.Context) (store.Snapshot, error) {
	if err := ctx.Err(); err != nil {
		return nil, err
	}
	return &snapshot{store: s}, nil
}

// Create implements store.Store.
func (s *Store) Create(ctx context.Context, key store.Key, r store.Record) (store.Version, error) {
	if err := store.ValidateCreate(key, r); err != nil {
		return store.Version{}, err
	}
	if err := ctx.Err(); err != nil {
		return store.Version{}, err
	}
	target := fmt.Sprintf("%s/%s?documentId=%s", s.parentURL(key.Parent()), escapeSegment(key.Kind()), url.QueryEscape(key.ID()))
	v, err := s.write(ctx, http.MethodPost, target, r)
	if err != nil {
		return store.Version{}, fmt.Errorf("creating %q: %w", key, translate(err, createStatuses))
	}
	return v, nil
}

// Update implements store.Store. The request has no update mask, so it
// replaces the whole document. If r equals the stored record Firestore keeps
// the updateTime, and the version returned is the one passed in.
func (s *Store) Update(ctx context.Context, key store.Key, r store.Record, v store.Version) (store.Version, error) {
	if err := store.ValidateUpdate(key, r, v); err != nil {
		return store.Version{}, err
	}
	if err := ctx.Err(); err != nil {
		return store.Version{}, err
	}
	newV, err := s.write(ctx, http.MethodPatch, s.documentURL(key)+"?"+precondition(v), r)
	if err != nil {
		return store.Version{}, fmt.Errorf("updating %q: %w", key, translate(err, updateStatuses))
	}
	return newV, nil
}

// Delete implements store.Store.
func (s *Store) Delete(ctx context.Context, key store.Key, v store.Version) error {
	if err := store.ValidateDelete(key, v); err != nil {
		return err
	}
	if err := ctx.Err(); err != nil {
		return err
	}
	err := s.call(ctx, http.MethodDelete, s.documentURL(key)+"?"+precondition(v), nil, nil)
	switch {
	case err == nil, hasStatus(err, "NOT_FOUND"):
		// Deleting a missing record is not an error.
		return nil
	case hasStatus(err, "FAILED_PRECONDITION"):
		// The document has another version, or it does not exist: the
		// status does not say which. Look.
		_, _, getErr := s.get(ctx, key, "")
		switch {
		case errors.Is(getErr, store.ErrNotFound):
			return nil
		case getErr != nil:
			return fmt.Errorf("deleting %q: checking whether it exists after %v: %w", key, err, getErr)
		}
		return fmt.Errorf("deleting %q: %w: %v", key, store.ErrVersionMismatch, err)
	}
	return fmt.Errorf("deleting %q: %w", key, translate(err, deleteStatuses))
}

// precondition returns the query parameter that makes a write conditional on
// the document still having the version v.
func precondition(v store.Version) string {
	return "currentDocument.updateTime=" + url.QueryEscape(v.Token())
}

// write sends the fields of r and returns the version of the document that
// the response describes.
func (s *Store) write(ctx context.Context, method, target string, r store.Record) (store.Version, error) {
	fields, err := encodeFields(r)
	if err != nil {
		return store.Version{}, err
	}
	var doc document
	if err := s.call(ctx, method, target, map[string]any{"fields": fields}, &doc); err != nil {
		return store.Version{}, err
	}
	if doc.UpdateTime == "" {
		return store.Version{}, errors.New("the response has no updateTime")
	}
	return store.NewVersion(doc.UpdateTime), nil
}

// snapshot is a store.Snapshot: a read-only transaction that begins at the
// first valid read.
type snapshot struct {
	store *Store

	mu     sync.Mutex
	txn    string // transaction ID; empty until the first valid read
	closed bool
}

// transaction returns the ID of the snapshot's transaction for a read whose
// arguments validate accepts, beginning the transaction if this is the first
// such read. It reports ErrClosed first, then the argument error, then a
// done context, so that an invalid read never fixes the view.
func (sn *snapshot) transaction(ctx context.Context, validate func() error) (string, error) {
	sn.mu.Lock()
	defer sn.mu.Unlock()
	if sn.closed {
		return "", store.ErrClosed
	}
	if err := validate(); err != nil {
		return "", err
	}
	if err := ctx.Err(); err != nil {
		return "", err
	}
	if sn.txn == "" {
		request := map[string]any{"options": map[string]any{"readOnly": map[string]any{}}}
		var response struct {
			Transaction string `json:"transaction"`
		}
		if err := sn.store.call(ctx, http.MethodPost, sn.store.documents+":beginTransaction", request, &response); err != nil {
			return "", fmt.Errorf("beginning the snapshot's transaction: %w", translate(err, readStatuses))
		}
		if response.Transaction == "" {
			return "", errors.New("beginning the snapshot's transaction: the response has no transaction ID")
		}
		sn.txn = response.Transaction
	}
	return sn.txn, nil
}

func (sn *snapshot) Get(ctx context.Context, key store.Key) (store.Record, store.Version, error) {
	txn, err := sn.transaction(ctx, key.Validate)
	if err != nil {
		return nil, store.Version{}, err
	}
	return sn.store.get(ctx, key, txn)
}

func (sn *snapshot) List(ctx context.Context, parent store.Key, kind string) ([]store.Item, error) {
	txn, err := sn.transaction(ctx, func() error { return store.ValidateList(parent, kind) })
	if err != nil {
		return nil, err
	}
	return sn.store.runQuery(ctx, parent, kind, nil, txn)
}

func (sn *snapshot) Query(ctx context.Context, parent store.Key, kind string, eq store.Eq) ([]store.Item, error) {
	txn, err := sn.transaction(ctx, func() error { return store.ValidateQuery(parent, kind, eq) })
	if err != nil {
		return nil, err
	}
	return sn.store.runQuery(ctx, parent, kind, &eq, txn)
}

// Close implements store.Snapshot. The snapshot is closed when Close returns,
// whatever it returns. If a transaction began, Close asks Firestore to
// release it, with a short timeout of its own that outlives a done ctx, so
// that a deferred Close works after the caller's deadline. A read-only
// transaction holds no locks; an error here only means that it lingers until
// Firestore expires it.
func (sn *snapshot) Close(ctx context.Context) error {
	sn.mu.Lock()
	txn, wasClosed := sn.txn, sn.closed
	sn.closed = true
	sn.mu.Unlock()
	if wasClosed || txn == "" {
		return nil
	}
	ctx, cancel := context.WithTimeout(context.WithoutCancel(ctx), rollbackTimeout)
	defer cancel()
	request := map[string]any{"transaction": txn}
	if err := sn.store.call(ctx, http.MethodPost, sn.store.documents+":rollback", request, nil); err != nil {
		return fmt.Errorf("releasing the snapshot's transaction: %w", err)
	}
	return nil
}
