// Package fake provides an in-memory implementation of store.Store for
// tests. It is safe for concurrent use.
package fake

import (
	"context"
	"fmt"
	"maps"
	"math/rand/v2"
	"slices"
	"strconv"
	"sync"
	"time"

	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/store"
)

// Store is an in-memory store.Store. The zero value is not usable; call New.
type Store struct {
	mu          sync.Mutex
	records     records
	lastVersion int64
}

var _ store.Store = (*Store)(nil)

// New returns an empty store.
func New() *Store {
	return &Store{records: make(records)}
}

// entry is one stored record. Entries are never modified after they are
// stored, so a shallow copy of a records map is a consistent snapshot.
type entry struct {
	key     store.Key
	record  store.Record // canonical
	version string
}

// records maps Key.String() to the record stored at that key.
type records map[string]entry

// Get implements store.Reader.
func (s *Store) Get(ctx context.Context, key store.Key) (store.Record, store.Version, error) {
	if err := validateGet(ctx, key); err != nil {
		return nil, store.Version{}, err
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.records.get(key)
}

// List implements store.Reader.
func (s *Store) List(ctx context.Context, parent store.Key, kind string) ([]store.Item, error) {
	if err := validateList(ctx, parent, kind); err != nil {
		return nil, err
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.records.children(parent, kind), nil
}

// Query implements store.Reader.
func (s *Store) Query(ctx context.Context, parent store.Key, kind string, eq store.Eq) ([]store.Item, error) {
	if err := validateQuery(ctx, parent, kind, eq); err != nil {
		return nil, err
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	return store.Filter(s.records.children(parent, kind), eq), nil
}

// Snapshot implements store.Store. Like a Firestore read-only transaction,
// the snapshot's view is fixed at its first valid read, not at this call.
func (s *Store) Snapshot(ctx context.Context) (store.Snapshot, error) {
	if err := ctx.Err(); err != nil {
		return nil, err
	}
	return &snapshot{parent: s}, nil
}

// Create implements store.Store.
func (s *Store) Create(ctx context.Context, key store.Key, r store.Record) (store.Version, error) {
	if err := store.ValidateCreate(key, r); err != nil {
		return store.Version{}, err
	}
	if err := ctx.Err(); err != nil {
		return store.Version{}, err
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	if _, ok := s.records[key.String()]; ok {
		return store.Version{}, fmt.Errorf("creating %q: %w", key, store.ErrAlreadyExists)
	}
	return s.put(key, r), nil
}

// Update implements store.Store. Like Firestore, it leaves the version
// unchanged when r equals the stored record.
func (s *Store) Update(ctx context.Context, key store.Key, r store.Record, v store.Version) (store.Version, error) {
	if err := store.ValidateUpdate(key, r, v); err != nil {
		return store.Version{}, err
	}
	if err := ctx.Err(); err != nil {
		return store.Version{}, err
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	e, ok := s.records[key.String()]
	if !ok || e.version != v.Token() {
		return store.Version{}, fmt.Errorf("updating %q: %w", key, store.ErrVersionMismatch)
	}
	if equalRecords(e.record, r.Canonical()) {
		return v, nil
	}
	return s.put(key, r), nil
}

// equalRecords reports whether two canonical records hold the same values.
func equalRecords(a, b store.Record) bool {
	return maps.EqualFunc(a, b, equalValues)
}

func equalValues(a, b any) bool {
	switch a := a.(type) {
	case time.Time:
		b, ok := b.(time.Time)
		return ok && a.Equal(b)
	case []any:
		b, ok := b.([]any)
		return ok && slices.EqualFunc(a, b, equalValues)
	case map[string]any:
		b, ok := b.(map[string]any)
		return ok && maps.EqualFunc(a, b, equalValues)
	default: // string, bool, int64
		return a == b
	}
}

// Delete implements store.Store.
func (s *Store) Delete(ctx context.Context, key store.Key, v store.Version) error {
	if err := store.ValidateDelete(key, v); err != nil {
		return err
	}
	if err := ctx.Err(); err != nil {
		return err
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	e, ok := s.records[key.String()]
	if !ok {
		return nil
	}
	if e.version != v.Token() {
		return fmt.Errorf("deleting %q: %w", key, store.ErrVersionMismatch)
	}
	delete(s.records, key.String())
	return nil
}

// All returns every record in the store, in unspecified order. It is not
// part of store.Store: tests use it to compare the whole content of two
// stores.
func (s *Store) All() []store.Item {
	s.mu.Lock()
	defer s.mu.Unlock()
	items := make([]store.Item, 0, len(s.records))
	for _, e := range s.records {
		items = append(items, store.Item{Key: e.key, Record: e.record.Canonical(), Version: store.NewVersion(e.version)})
	}
	return items
}

// put stores r at key under a new version. s.mu must be held.
func (s *Store) put(key store.Key, r store.Record) store.Version {
	s.lastVersion++
	e := entry{key: key, record: r.Canonical(), version: strconv.FormatInt(s.lastVersion, 10)}
	s.records[key.String()] = e
	return store.NewVersion(e.version)
}

func (rs records) get(key store.Key) (store.Record, store.Version, error) {
	e, ok := rs[key.String()]
	if !ok {
		return nil, store.Version{}, fmt.Errorf("getting %q: %w", key, store.ErrNotFound)
	}
	return e.record.Canonical(), store.NewVersion(e.version), nil
}

// children returns the direct children of parent with the given kind, in
// random order: the contract leaves the order unspecified, and a shuffled
// order keeps callers from depending on one.
func (rs records) children(parent store.Key, kind string) []store.Item {
	var items []store.Item
	for _, e := range rs {
		if e.key.Kind() == kind && e.key.Parent() == parent {
			items = append(items, store.Item{Key: e.key, Record: e.record.Canonical(), Version: store.NewVersion(e.version)})
		}
	}
	rand.Shuffle(len(items), func(i, j int) { items[i], items[j] = items[j], items[i] })
	return items
}

// The read validators check the arguments first, then the context, so that
// an invalid call fails the same way whatever the context's state.

func validateGet(ctx context.Context, key store.Key) error {
	if err := key.Validate(); err != nil {
		return err
	}
	return ctx.Err()
}

func validateList(ctx context.Context, parent store.Key, kind string) error {
	if err := store.ValidateList(parent, kind); err != nil {
		return err
	}
	return ctx.Err()
}

func validateQuery(ctx context.Context, parent store.Key, kind string, eq store.Eq) error {
	if err := store.ValidateQuery(parent, kind, eq); err != nil {
		return err
	}
	return ctx.Err()
}

// snapshot is a store.Snapshot. It copies the parent's records map on its
// first valid read; entries are immutable, so the shallow copy is a
// consistent view.
type snapshot struct {
	parent *Store

	mu      sync.Mutex
	records records // nil until the first valid read
	closed  bool
}

// view returns the snapshot's records for a read whose arguments validate
// returned no error. It reports ErrClosed first, then the argument error, and
// only then fixes the view, so that an invalid call never pins it.
func (s *snapshot) view(validate func() error) (records, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.closed {
		return nil, store.ErrClosed
	}
	if err := validate(); err != nil {
		return nil, err
	}
	if s.records == nil {
		s.parent.mu.Lock()
		s.records = maps.Clone(s.parent.records)
		s.parent.mu.Unlock()
	}
	return s.records, nil
}

func (s *snapshot) Get(ctx context.Context, key store.Key) (store.Record, store.Version, error) {
	rs, err := s.view(func() error { return validateGet(ctx, key) })
	if err != nil {
		return nil, store.Version{}, err
	}
	return rs.get(key)
}

func (s *snapshot) List(ctx context.Context, parent store.Key, kind string) ([]store.Item, error) {
	rs, err := s.view(func() error { return validateList(ctx, parent, kind) })
	if err != nil {
		return nil, err
	}
	return rs.children(parent, kind), nil
}

func (s *snapshot) Query(ctx context.Context, parent store.Key, kind string, eq store.Eq) ([]store.Item, error) {
	rs, err := s.view(func() error { return validateQuery(ctx, parent, kind, eq) })
	if err != nil {
		return nil, err
	}
	return store.Filter(rs.children(parent, kind), eq), nil
}

// Close implements store.Snapshot. It ignores ctx, so a snapshot can always
// be released, even from a deferred call after the context is done.
func (s *snapshot) Close(context.Context) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.closed = true
	s.records = nil
	return nil
}
