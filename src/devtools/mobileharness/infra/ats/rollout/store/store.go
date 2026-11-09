// Package store defines rollout-store, the only state shared by the
// components of MTT node auto-rollout: a passive record store with
// per-record versions and conditional writes.
//
// The contract is the seven operations of Store. It is written so that every
// backend behaves identically:
//
//   - List and Query return the direct children of one parent with one record
//     kind, never grandchildren or children of another kind.
//   - Query filters on one top-level string field equal to one value, both
//     non-empty, and returns every match.
//   - Versions are opaque. Callers get them only from the store and pass them
//     back unchanged; they never compare, parse, or construct them.
//
// Package storetest checks the contract against an implementation.
package store

import (
	"context"
	"errors"
	"fmt"
	"regexp"
	"unicode/utf8"
)

// Errors returned by Store implementations, possibly wrapped; test for them
// with errors.Is. Any other error is transient: the caller fails static.
var (
	// ErrNotFound means the record does not exist.
	ErrNotFound = errors.New("record not found")
	// ErrAlreadyExists means Create found an existing record.
	ErrAlreadyExists = errors.New("record already exists")
	// ErrVersionMismatch means a conditional write found a different version
	// of the record, or no record.
	ErrVersionMismatch = errors.New("record version mismatch")
	// ErrInvalidArgument means a key, record, version, or query is not allowed.
	ErrInvalidArgument = errors.New("invalid argument")
	// ErrClosed means a Snapshot was used after Close.
	ErrClosed = errors.New("snapshot closed")
)

// Version identifies one state of one record. Every write that changes a
// record gives it a new version, and a record that is deleted and created
// again never gets a version it had before.
//
// The zero Version matches no record and is rejected by every write.
type Version struct {
	token string
}

// NewVersion returns the version with the given backend token. Only backends
// call it.
func NewVersion(token string) Version {
	return Version{token: token}
}

// Token returns the backend token of v. Only backends call it.
func (v Version) Token() string {
	return v.token
}

// IsZero reports whether v is the zero Version.
func (v Version) IsZero() bool {
	return v.token == ""
}

// Item is one record returned by List or Query.
type Item struct {
	Key     Key
	Record  Record
	Version Version
}

// Eq is the filter of Query: records whose top-level Field is a string equal
// to Value.
type Eq struct {
	Field string
	Value string
}

// Reader is the read half of the store. Records returned by a Reader belong
// to the caller, which may modify them.
type Reader interface {
	// Get returns the record at key and its version, or ErrNotFound.
	Get(ctx context.Context, key Key) (Record, Version, error)
	// List returns every direct child of parent with the given record kind,
	// in unspecified order. The parent record need not exist; use the zero
	// Key to list top-level records.
	List(ctx context.Context, parent Key, kind string) ([]Item, error)
	// Query returns every direct child of parent with the given record kind
	// whose top-level field eq.Field is a string equal to eq.Value, in
	// unspecified order. It returns ErrInvalidArgument if eq is not valid
	// (see ValidateQuery).
	Query(ctx context.Context, parent Key, kind string, eq Eq) ([]Item, error)
}

// Snapshot is a read-only view of the store as of the moment of its first
// read: later reads do not see writes made after that moment. A version read
// through a snapshot is a valid expected version for Store.Update and
// Store.Delete, which fail with ErrVersionMismatch if the record changed
// since. Writes are never made through a snapshot.
type Snapshot interface {
	Reader
	// Close releases the snapshot. Reads after Close return ErrClosed; a
	// second Close does nothing. Close works even if ctx is already done.
	Close(ctx context.Context) error
}

// Store is a record store. Every operation is atomic on one record; no
// operation spans records. Reads made directly on a Store see the latest
// state. Implementations are safe for concurrent use, validate their
// arguments before doing anything else, and return ctx.Err() when the
// context is done before the operation starts.
type Store interface {
	Reader
	// Snapshot returns a read-only view of the store.
	Snapshot(ctx context.Context) (Snapshot, error)
	// Create stores r at key if no record exists there, and returns its
	// version. It returns ErrAlreadyExists otherwise.
	Create(ctx context.Context, key Key, r Record) (Version, error)
	// Update replaces the record at key with r if its current version is v,
	// and returns the new version. It returns ErrVersionMismatch if the
	// version differs or the record does not exist. If r equals the stored
	// record, the backend may leave the record and its version unchanged
	// (Firestore does), so callers never detect a write by a version change.
	Update(ctx context.Context, key Key, r Record, v Version) (Version, error)
	// Delete removes the record at key if its current version is v, and
	// returns ErrVersionMismatch otherwise. Deleting a missing record is not
	// an error.
	Delete(ctx context.Context, key Key, v Version) error
}

// Every backend validates its arguments with the functions below before doing
// anything else, so that all backends reject the same calls with
// ErrInvalidArgument. Get calls Key.Validate; Create calls ValidateCreate;
// Update calls ValidateUpdate; Delete calls ValidateDelete; List calls
// ValidateList; Query calls ValidateQuery.

// ValidateCreate returns an error wrapping ErrInvalidArgument unless key
// names a record and r is a valid record.
func ValidateCreate(key Key, r Record) error {
	if err := key.Validate(); err != nil {
		return err
	}
	if err := r.Validate(); err != nil {
		return fmt.Errorf("record for %q: %w", key, err)
	}
	return nil
}

// ValidateUpdate returns an error wrapping ErrInvalidArgument unless key
// names a record, r is a valid record, and v is not the zero Version.
func ValidateUpdate(key Key, r Record, v Version) error {
	if err := ValidateCreate(key, r); err != nil {
		return err
	}
	return validateVersion(key, v)
}

// ValidateDelete returns an error wrapping ErrInvalidArgument unless key
// names a record and v is not the zero Version.
func ValidateDelete(key Key, v Version) error {
	if err := key.Validate(); err != nil {
		return err
	}
	return validateVersion(key, v)
}

func validateVersion(key Key, v Version) error {
	if v.IsZero() {
		return fmt.Errorf("%w: zero version for %q", ErrInvalidArgument, key)
	}
	return nil
}

// ValidateList returns an error wrapping ErrInvalidArgument unless parent is
// the root or names a record, and kind is a valid name.
func ValidateList(parent Key, kind string) error {
	if !parent.IsRoot() {
		if err := parent.Validate(); err != nil {
			return err
		}
	}
	if err := validateName(kind); err != nil {
		return fmt.Errorf("record kind: %w", err)
	}
	return nil
}

// fieldName is the form of a Query field: a plain identifier, so that the
// field path means the same thing to Firestore and to Filter (no dots, no
// quoting).
var fieldName = regexp.MustCompile(`^[a-z_][a-z0-9_]*$`)

// ValidateQuery returns an error wrapping ErrInvalidArgument unless the
// arguments are valid for List, eq.Field is a valid name that matches
// ^[a-z_][a-z0-9_]*$, and eq.Value is non-empty valid UTF-8. Rejecting an
// empty value keeps a missing field and an empty string from matching
// differently on different backends.
func ValidateQuery(parent Key, kind string, eq Eq) error {
	if err := ValidateList(parent, kind); err != nil {
		return err
	}
	if err := validateName(eq.Field); err != nil {
		return fmt.Errorf("query field: %w", err)
	}
	if !fieldName.MatchString(eq.Field) {
		return fmt.Errorf("%w: query field %q is not a plain identifier", ErrInvalidArgument, eq.Field)
	}
	switch {
	case eq.Value == "":
		return fmt.Errorf("%w: empty query value for field %q", ErrInvalidArgument, eq.Field)
	case !utf8.ValidString(eq.Value):
		return fmt.Errorf("%w: query value for field %q is not valid UTF-8", ErrInvalidArgument, eq.Field)
	}
	return nil
}

// Filter returns the items whose record matches eq. Backends without
// server-side filtering implement Query as List followed by Filter.
func Filter(items []Item, eq Eq) []Item {
	var matched []Item
	for _, it := range items {
		if s, ok := it.Record[eq.Field].(string); ok && s == eq.Value {
			matched = append(matched, it)
		}
	}
	return matched
}
