// Package store defines rollout-store, the only state shared by the
// components of MTT node auto-rollout: a passive record store with
// per-record versions and conditional writes.
//
// The contract is the seven operations of Store. It is written so that every
// backend behaves identically:
//
//   - List and Query return the direct children of one parent with one record
//     type, never grandchildren or children of another type.
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
)

// Errors returned by Store implementations, possibly wrapped; test for them
// with errors.Is.
var (
	// ErrNotFound means the record does not exist.
	ErrNotFound = errors.New("record not found")
	// ErrAlreadyExists means Create found an existing record.
	ErrAlreadyExists = errors.New("record already exists")
	// ErrVersionMismatch means a conditional write found a different version
	// of the record, or no record.
	ErrVersionMismatch = errors.New("record version mismatch")
	// ErrInvalidArgument means a key, record, or query is not allowed.
	ErrInvalidArgument = errors.New("invalid argument")
)

// Version identifies one state of one record. Every write that changes a
// record gives it a new version, and a record that is deleted and created
// again never gets a version it had before.
//
// The zero Version matches no record.
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

// Item is one record returned by List or Query.
type Item struct {
	Key     Key
	Record  Record
	Version Version
}

// Reader is the read half of the store. Records returned by a Reader belong
// to the caller, which may modify them.
type Reader interface {
	// Get returns the record at key and its version, or ErrNotFound.
	Get(ctx context.Context, key Key) (Record, Version, error)
	// List returns every direct child of parent with record type typ, in
	// unspecified order. The parent record need not exist; use the zero Key
	// to list top-level records.
	List(ctx context.Context, parent Key, typ string) ([]Item, error)
	// Query returns every direct child of parent with record type typ whose
	// top-level field is a string equal to value, in unspecified order. It
	// returns ErrInvalidArgument if field or value is empty.
	Query(ctx context.Context, parent Key, typ, field, value string) ([]Item, error)
}

// Snapshot is a read-only view of the store as of the moment it was taken:
// its reads do not see writes made after that moment. A version read
// through a snapshot is a valid expected version for Store.Update and
// Store.Delete, which fail with ErrVersionMismatch if the record changed
// since. Writes are never made through a snapshot.
type Snapshot interface {
	Reader
	// Close releases the snapshot. The snapshot must not be used afterwards.
	Close(ctx context.Context) error
}

// Store is a record store. Every operation is atomic on one record; no
// operation spans records. Reads made directly on a Store see the latest
// state.
type Store interface {
	Reader
	// Snapshot returns a read-only view of the store as of now.
	Snapshot(ctx context.Context) (Snapshot, error)
	// Create stores r at key if no record exists there, and returns its
	// version. It returns ErrAlreadyExists otherwise.
	Create(ctx context.Context, key Key, r Record) (Version, error)
	// Update replaces the record at key with r if its current version is v,
	// and returns the new version. It returns ErrVersionMismatch if the
	// version differs or the record does not exist.
	Update(ctx context.Context, key Key, r Record, v Version) (Version, error)
	// Delete removes the record at key. If v is not nil, it removes the
	// record only if its current version is *v and returns
	// ErrVersionMismatch otherwise. Deleting a missing record is not an
	// error, with or without v.
	Delete(ctx context.Context, key Key, v *Version) error
}

// Every backend validates its arguments with the functions below before doing
// anything else, so that all backends reject the same calls with
// ErrInvalidArgument. Get and Delete call Key.Validate; Create and Update call
// ValidateWrite.

// ValidateWrite returns an error wrapping ErrInvalidArgument unless key names
// a record and r is a valid record.
func ValidateWrite(key Key, r Record) error {
	if err := key.Validate(); err != nil {
		return err
	}
	if err := r.Validate(); err != nil {
		return fmt.Errorf("record for %q: %w", key, err)
	}
	return nil
}

// ValidateList returns an error wrapping ErrInvalidArgument unless parent is
// the root or names a record, and typ is a valid name.
func ValidateList(parent Key, typ string) error {
	if !parent.IsRoot() {
		if err := parent.Validate(); err != nil {
			return err
		}
	}
	if err := validateName(typ); err != nil {
		return fmt.Errorf("record type: %w", err)
	}
	return nil
}

// ValidateQuery returns an error wrapping ErrInvalidArgument unless the
// arguments are valid for List, field is a valid field name, and value is
// non-empty. Rejecting an empty value keeps a missing field and an empty
// string from matching differently on different backends.
func ValidateQuery(parent Key, typ, field, value string) error {
	if err := ValidateList(parent, typ); err != nil {
		return err
	}
	if err := validateName(field); err != nil {
		return fmt.Errorf("query field: %w", err)
	}
	if value == "" {
		return fmt.Errorf("%w: empty query value for field %q", ErrInvalidArgument, field)
	}
	return nil
}

// Filter returns the items whose record has a top-level string field equal
// to value. Backends without server-side filtering implement Query as List
// followed by Filter.
func Filter(items []Item, field, value string) []Item {
	var matched []Item
	for _, it := range items {
		if s, ok := it.Record[field].(string); ok && s == value {
			matched = append(matched, it)
		}
	}
	return matched
}
