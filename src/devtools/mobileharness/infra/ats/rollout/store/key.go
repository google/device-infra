package store

import (
	"fmt"
	"strings"
	"unicode/utf8"
)

// Key is the logical identity of a record: a path of (kind, ID) pairs from
// the root, such as domains/d1/groups/controller. The zero Key is the root;
// it names no record and is only used as the parent of top-level records.
//
// Keys are comparable values. Two keys are equal if and only if they have the
// same path.
type Key struct {
	path string
	// invalid describes the first invalid kind or ID passed to Child, if any.
	invalid string
}

// NewKey returns the key of a top-level record.
func NewKey(kind, id string) Key {
	return Key{}.Child(kind, id)
}

// Child returns the key of the record of the given kind and ID under k. If
// kind or id is not a valid name (see Validate), the returned key is invalid
// and every store operation rejects it.
func (k Key) Child(kind, id string) Key {
	child := Key{path: kind + "/" + id, invalid: k.invalid}
	if !k.IsRoot() {
		child.path = k.path + "/" + child.path
	}
	if child.invalid == "" {
		if err := validateName(kind); err != nil {
			child.invalid = "kind: " + err.Error()
		} else if err := validateName(id); err != nil {
			child.invalid = "ID: " + err.Error()
		}
	}
	return child
}

// Parent returns the key of the record that contains k. The parent of a
// top-level key, and of the root, is the root. Parent is only meaningful for
// a valid key.
func (k Key) Parent() Key {
	i := strings.LastIndexByte(k.path, '/')
	if i < 0 {
		return Key{}
	}
	if j := strings.LastIndexByte(k.path[:i], '/'); j >= 0 {
		return Key{path: k.path[:j], invalid: k.invalid}
	}
	return Key{}
}

// IsRoot reports whether k is the root.
func (k Key) IsRoot() bool {
	return k.path == ""
}

// Kind returns the record kind of k, such as "groups", or "" for the root.
func (k Key) Kind() string {
	_, kind, _ := k.split()
	return kind
}

// ID returns the ID of k among the records of its kind under its parent, or
// "" for the root.
func (k Key) ID() string {
	_, _, id := k.split()
	return id
}

// split returns the parent path, kind, and ID of k.
func (k Key) split() (parent, kind, id string) {
	i := strings.LastIndexByte(k.path, '/')
	if i < 0 {
		return "", "", ""
	}
	id = k.path[i+1:]
	parent, kind = "", k.path[:i]
	if j := strings.LastIndexByte(kind, '/'); j >= 0 {
		parent, kind = kind[:j], kind[j+1:]
	}
	return parent, kind, id
}

// String returns the path of k, such as "domains/d1/groups/controller". The
// root is "".
func (k Key) String() string {
	return k.path
}

// Validate returns an error wrapping ErrInvalidArgument unless k names a
// record: it is not the root, and every kind and ID in it is a valid name.
//
// A valid name is non-empty valid UTF-8 of at most 1500 bytes, contains no
// "/", is not "." or "..", and does not both start and end with "__". These
// are the restrictions Firestore places on collection and document IDs;
// every backend enforces them so that the same keys are accepted everywhere.
func (k Key) Validate() error {
	if k.IsRoot() {
		return fmt.Errorf("%w: the root key names no record", ErrInvalidArgument)
	}
	if k.invalid != "" {
		return fmt.Errorf("%w: key %q: %s", ErrInvalidArgument, k.path, k.invalid)
	}
	return nil
}

// maxNameBytes is the Firestore limit on the size of a document ID.
const maxNameBytes = 1500

// validateName checks one kind, ID, or map key.
func validateName(name string) error {
	switch {
	case name == "":
		return fmt.Errorf("%w: empty name", ErrInvalidArgument)
	case !utf8.ValidString(name):
		return fmt.Errorf("%w: name %q is not valid UTF-8", ErrInvalidArgument, name)
	case len(name) > maxNameBytes:
		return fmt.Errorf("%w: name is longer than %d bytes", ErrInvalidArgument, maxNameBytes)
	case strings.Contains(name, "/"):
		return fmt.Errorf("%w: name %q contains \"/\"", ErrInvalidArgument, name)
	case name == "." || name == "..":
		return fmt.Errorf("%w: name %q is reserved", ErrInvalidArgument, name)
	case len(name) >= 4 && strings.HasPrefix(name, "__") && strings.HasSuffix(name, "__"):
		return fmt.Errorf("%w: name %q is reserved", ErrInvalidArgument, name)
	}
	return nil
}
