package store

import (
	"errors"
	"strings"
	"testing"
)

func TestKey(t *testing.T) {
	group := NewKey("domains", "d1").Child("groups", "controller")
	member := group.Child("members", "host.example.com")

	tests := []struct {
		key                          Key
		wantString, wantKind, wantID string
		wantParent                   string
	}{
		{key: Key{}, wantString: "", wantKind: "", wantID: "", wantParent: ""},
		{key: NewKey("domains", "d1"), wantString: "domains/d1", wantKind: "domains", wantID: "d1", wantParent: ""},
		{key: group, wantString: "domains/d1/groups/controller", wantKind: "groups", wantID: "controller", wantParent: "domains/d1"},
		{key: member, wantString: "domains/d1/groups/controller/members/host.example.com", wantKind: "members", wantID: "host.example.com", wantParent: "domains/d1/groups/controller"},
	}
	for _, tc := range tests {
		if got := tc.key.String(); got != tc.wantString {
			t.Errorf("Key.String() = %q, want %q", got, tc.wantString)
		}
		if got := tc.key.Kind(); got != tc.wantKind {
			t.Errorf("Key(%q).Kind() = %q, want %q", tc.key, got, tc.wantKind)
		}
		if got := tc.key.ID(); got != tc.wantID {
			t.Errorf("Key(%q).ID() = %q, want %q", tc.key, got, tc.wantID)
		}
		if got := tc.key.Parent().String(); got != tc.wantParent {
			t.Errorf("Key(%q).Parent() = %q, want %q", tc.key, got, tc.wantParent)
		}
	}
}

func TestKey_Comparable(t *testing.T) {
	a := NewKey("domains", "d1").Child("groups", "a")
	same := NewKey("domains", "d1").Child("groups", "a")
	parent := a.Parent()

	if a != same {
		t.Errorf("Key %q != an equal key built separately, want ==", a)
	}
	if parent != NewKey("domains", "d1") {
		t.Errorf("Key(%q).Parent() = %q, want == NewKey(domains, d1)", a, parent)
	}
	if NewKey("domains", "d1").Parent() != (Key{}) {
		t.Errorf("Key(domains/d1).Parent() = %q, want the root", NewKey("domains", "d1").Parent())
	}
}

func TestKey_Validate(t *testing.T) {
	for _, id := range []string{"", "a/b", ".", "..", "__x__", "____", "\xff", strings.Repeat("x", maxNameBytes+1)} {
		if err := NewKey("domains", id).Validate(); !errors.Is(err, ErrInvalidArgument) {
			t.Errorf("NewKey(domains, %q).Validate() = %v, want %v", id, err, ErrInvalidArgument)
		}
	}
	for _, id := range []string{"a", "...", "__x", "x__", "host.example.com", "__", strings.Repeat("x", maxNameBytes)} {
		if err := NewKey("domains", id).Validate(); err != nil {
			t.Errorf("NewKey(domains, %q).Validate() = %v, want nil", id, err)
		}
	}
	if err := (Key{}).Validate(); !errors.Is(err, ErrInvalidArgument) {
		t.Errorf("root Key.Validate() = %v, want %v", err, ErrInvalidArgument)
	}
	if err := NewKey("", "d1").Child("groups", "a").Validate(); !errors.Is(err, ErrInvalidArgument) {
		t.Errorf("Key with an empty kind in its parent: Validate() = %v, want %v", err, ErrInvalidArgument)
	}
	// The reason is wrapped once, not once per layer.
	err := NewKey("domains", "a/b").Validate()
	if got, want := strings.Count(err.Error(), ErrInvalidArgument.Error()), 1; got != want {
		t.Errorf("NewKey(domains, a/b).Validate() = %q, want %q to appear %d time(s), got %d", err, ErrInvalidArgument, want, got)
	}
}
