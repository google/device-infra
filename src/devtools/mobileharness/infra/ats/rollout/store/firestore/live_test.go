package firestore

import (
	"context"
	"flag"
	"fmt"
	"net/http"
	"strings"
	"testing"
	"time"

	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/store/fake"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/store"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/store/storetest"
)

// The hermetic tests show that the adapter and the test server agree. Only a
// real database shows that Firestore behaves the way both assume: that an
// update without a mask replaces the document, that an update that changes
// nothing keeps the updateTime, which status a precondition on a missing
// document gets, and what a read-only transaction sees. This test runs the
// contract suite against one. It is skipped unless all three flags are set,
// so it never runs in presubmit:
//
//	blaze test :firestore_test --test_arg=--firestore_project=PROJECT \
//	  --test_arg=--firestore_database=DATABASE \
//	  --test_arg=--firestore_access_token="$(gcloud auth print-access-token)"
//
// Use a database for tests, never one that hosts a rollout domain. Every
// subtest writes under its own document of the collection storetest_runs,
// and nothing deletes them afterwards.
var (
	liveProject  = flag.String("firestore_project", "", "project of the Firestore database for TestContractOnFirestore")
	liveDatabase = flag.String("firestore_database", "", "ID of the Firestore database for TestContractOnFirestore")
	liveToken    = flag.String("firestore_access_token", "", "OAuth access token with the datastore scope for TestContractOnFirestore")
)

// bearer adds an access token to every request.
type bearer struct {
	token string
}

func (b bearer) RoundTrip(r *http.Request) (*http.Response, error) {
	r = r.Clone(r.Context())
	r.Header.Set("Authorization", "Bearer "+b.token)
	return http.DefaultTransport.RoundTrip(r)
}

func TestContractOnFirestore(t *testing.T) {
	if *liveProject == "" || *liveDatabase == "" || *liveToken == "" {
		t.Skip("needs --firestore_project, --firestore_database, and --firestore_access_token")
	}
	s, err := New(Config{Client: &http.Client{Transport: bearer{token: *liveToken}}, Project: *liveProject, Database: *liveDatabase})
	if err != nil {
		t.Fatalf("New() failed: %v", err)
	}
	// The suite wants an empty store for every subtest, and the database is
	// shared, so each subtest gets a root record of its own.
	run := time.Now().UTC().Format("20060102T150405.000000000")
	subtest := 0
	storetest.Run(t, func(t *testing.T) store.Store {
		subtest++
		root := store.NewKey("storetest_runs", fmt.Sprintf("%s-%d", run, subtest))
		t.Logf("records of this subtest are under %q", root)
		return nested{Store: s, reader: reader{Reader: s, root: root}}
	})
}

// TestNestedKeepsTheContract checks the wrapper that TestContractOnFirestore
// depends on, against the fake store.
func TestNestedKeepsTheContract(t *testing.T) {
	storetest.Run(t, func(t *testing.T) store.Store {
		s := fake.New()
		return nested{Store: s, reader: reader{Reader: s, root: store.NewKey("storetest_runs", "r1")}}
	})
}

// nested is a store.Store that keeps every record under one root record of
// another store, and shows them as if the root record were the root. It
// validates arguments itself, before it moves a key under the root: the root
// key would otherwise become a valid key.
type nested struct {
	store.Store
	reader
}

// reader is the read half of nested, for the store and for its snapshots.
type reader struct {
	store.Reader
	root store.Key
}

// in returns key moved under the root record. key must be valid or the root.
func (r reader) in(key store.Key) store.Key {
	return rebase(r.root, key.String())
}

// out returns the items with their keys as the caller knows them.
func (r reader) out(items []store.Item, err error) ([]store.Item, error) {
	for i := range items {
		items[i].Key = rebase(store.Key{}, strings.TrimPrefix(items[i].Key.String(), r.root.String()+"/"))
	}
	return items, err
}

// rebase returns the key with the given path under base.
func rebase(base store.Key, path string) store.Key {
	segments := strings.Split(path, "/")
	for i := 0; i+1 < len(segments); i += 2 {
		base = base.Child(segments[i], segments[i+1])
	}
	return base
}

func (r reader) Get(ctx context.Context, key store.Key) (store.Record, store.Version, error) {
	if err := key.Validate(); err != nil {
		return nil, store.Version{}, err
	}
	return r.Reader.Get(ctx, r.in(key))
}

func (r reader) List(ctx context.Context, parent store.Key, kind string) ([]store.Item, error) {
	if err := store.ValidateList(parent, kind); err != nil {
		return nil, err
	}
	return r.out(r.Reader.List(ctx, r.in(parent), kind))
}

func (r reader) Query(ctx context.Context, parent store.Key, kind string, eq store.Eq) ([]store.Item, error) {
	if err := store.ValidateQuery(parent, kind, eq); err != nil {
		return nil, err
	}
	return r.out(r.Reader.Query(ctx, r.in(parent), kind, eq))
}

func (n nested) Get(ctx context.Context, key store.Key) (store.Record, store.Version, error) {
	return n.reader.Get(ctx, key)
}

func (n nested) List(ctx context.Context, parent store.Key, kind string) ([]store.Item, error) {
	return n.reader.List(ctx, parent, kind)
}

func (n nested) Query(ctx context.Context, parent store.Key, kind string, eq store.Eq) ([]store.Item, error) {
	return n.reader.Query(ctx, parent, kind, eq)
}

// nestedSnapshot is a snapshot of nested.
type nestedSnapshot struct {
	reader
	snap store.Snapshot
}

func (s nestedSnapshot) Close(ctx context.Context) error { return s.snap.Close(ctx) }

func (n nested) Snapshot(ctx context.Context) (store.Snapshot, error) {
	snap, err := n.Store.Snapshot(ctx)
	if err != nil {
		return nil, err
	}
	return nestedSnapshot{reader: reader{Reader: snap, root: n.root}, snap: snap}, nil
}

func (n nested) Create(ctx context.Context, key store.Key, r store.Record) (store.Version, error) {
	if err := store.ValidateCreate(key, r); err != nil {
		return store.Version{}, err
	}
	return n.Store.Create(ctx, n.in(key), r)
}

func (n nested) Update(ctx context.Context, key store.Key, r store.Record, v store.Version) (store.Version, error) {
	if err := store.ValidateUpdate(key, r, v); err != nil {
		return store.Version{}, err
	}
	return n.Store.Update(ctx, n.in(key), r, v)
}

func (n nested) Delete(ctx context.Context, key store.Key, v store.Version) error {
	if err := store.ValidateDelete(key, v); err != nil {
		return err
	}
	return n.Store.Delete(ctx, n.in(key), v)
}
