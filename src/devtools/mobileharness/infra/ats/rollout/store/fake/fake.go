// Package fake provides an in-memory implementation of store.Store for
// tests. It is safe for concurrent use.
package fake

import (
	"context"
	"errors"
	"fmt"
	"maps"
	"slices"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"

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
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.records.get(key)
}

// List implements store.Reader.
func (s *Store) List(ctx context.Context, parent store.Key, typ string) ([]store.Item, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.records.list(parent, typ)
}

// Query implements store.Reader.
func (s *Store) Query(ctx context.Context, parent store.Key, typ, field, value string) ([]store.Item, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.records.query(parent, typ, field, value)
}

// Snapshot implements store.Store.
func (s *Store) Snapshot(ctx context.Context) (store.Snapshot, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	return &snapshot{records: maps.Clone(s.records)}, nil
}

// Create implements store.Store.
func (s *Store) Create(ctx context.Context, key store.Key, r store.Record) (store.Version, error) {
	if err := store.ValidateWrite(key, r); err != nil {
		return store.Version{}, err
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	if _, ok := s.records[key.String()]; ok {
		return store.Version{}, fmt.Errorf("creating %q: %w", key, store.ErrAlreadyExists)
	}
	return s.put(key, r), nil
}

// Update implements store.Store.
func (s *Store) Update(ctx context.Context, key store.Key, r store.Record, v store.Version) (store.Version, error) {
	if err := store.ValidateWrite(key, r); err != nil {
		return store.Version{}, err
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	if e, ok := s.records[key.String()]; !ok || e.version != v.Token() {
		return store.Version{}, fmt.Errorf("updating %q: %w", key, store.ErrVersionMismatch)
	}
	return s.put(key, r), nil
}

// Delete implements store.Store.
func (s *Store) Delete(ctx context.Context, key store.Key, v *store.Version) error {
	if err := key.Validate(); err != nil {
		return err
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	e, ok := s.records[key.String()]
	if !ok {
		return nil
	}
	if v != nil && e.version != v.Token() {
		return fmt.Errorf("deleting %q: %w", key, store.ErrVersionMismatch)
	}
	delete(s.records, key.String())
	return nil
}

// put stores r at key under a new version. s.mu must be held.
func (s *Store) put(key store.Key, r store.Record) store.Version {
	s.lastVersion++
	e := entry{key: key, record: r.Canonical(), version: strconv.FormatInt(s.lastVersion, 10)}
	s.records[key.String()] = e
	return store.NewVersion(e.version)
}

func (rs records) get(key store.Key) (store.Record, store.Version, error) {
	if err := key.Validate(); err != nil {
		return nil, store.Version{}, err
	}
	e, ok := rs[key.String()]
	if !ok {
		return nil, store.Version{}, fmt.Errorf("getting %q: %w", key, store.ErrNotFound)
	}
	return e.record.Canonical(), store.NewVersion(e.version), nil
}

func (rs records) list(parent store.Key, typ string) ([]store.Item, error) {
	if err := store.ValidateList(parent, typ); err != nil {
		return nil, err
	}
	return rs.children(parent, typ), nil
}

func (rs records) query(parent store.Key, typ, field, value string) ([]store.Item, error) {
	if err := store.ValidateQuery(parent, typ, field, value); err != nil {
		return nil, err
	}
	return store.Filter(rs.children(parent, typ), field, value), nil
}

// children returns the direct children of parent with type typ, sorted by
// key so that tests see a stable order.
func (rs records) children(parent store.Key, typ string) []store.Item {
	var items []store.Item
	for _, e := range rs {
		if e.key.Kind() == typ && e.key.Parent().String() == parent.String() {
			items = append(items, store.Item{Key: e.key, Record: e.record.Canonical(), Version: store.NewVersion(e.version)})
		}
	}
	slices.SortFunc(items, func(a, b store.Item) int {
		return strings.Compare(a.Key.String(), b.Key.String())
	})
	return items
}

// snapshot is a store.Snapshot over a copy of the records map.
type snapshot struct {
	records records
	closed  atomic.Bool
}

var errClosed = errors.New("fake: snapshot used after Close")

func (s *snapshot) Get(ctx context.Context, key store.Key) (store.Record, store.Version, error) {
	if s.closed.Load() {
		return nil, store.Version{}, errClosed
	}
	return s.records.get(key)
}

func (s *snapshot) List(ctx context.Context, parent store.Key, typ string) ([]store.Item, error) {
	if s.closed.Load() {
		return nil, errClosed
	}
	return s.records.list(parent, typ)
}

func (s *snapshot) Query(ctx context.Context, parent store.Key, typ, field, value string) ([]store.Item, error) {
	if s.closed.Load() {
		return nil, errClosed
	}
	return s.records.query(parent, typ, field, value)
}

func (s *snapshot) Close(ctx context.Context) error {
	s.closed.Store(true)
	return nil
}
