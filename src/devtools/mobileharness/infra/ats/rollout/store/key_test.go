package store

import (
	"errors"
	"testing"
)

func TestKey(t *testing.T) {
	group := NewKey("domains", "d1").Child("groups", "controller")
	member := group.Child("members", "host.example.com")

	tests := []struct {
		key                          Key
		wantString, wantType, wantID string
		wantParent                   string
	}{
		{key: Key{}, wantString: "", wantType: "", wantID: "", wantParent: ""},
		{key: NewKey("domains", "d1"), wantString: "domains/d1", wantType: "domains", wantID: "d1", wantParent: ""},
		{key: group, wantString: "domains/d1/groups/controller", wantType: "groups", wantID: "controller", wantParent: "domains/d1"},
		{key: member, wantString: "domains/d1/groups/controller/members/host.example.com", wantType: "members", wantID: "host.example.com", wantParent: "domains/d1/groups/controller"},
	}
	for _, tc := range tests {
		if got := tc.key.String(); got != tc.wantString {
			t.Errorf("Key.String() = %q, want %q", got, tc.wantString)
		}
		if got := tc.key.Kind(); got != tc.wantType {
			t.Errorf("Key(%q).Kind() = %q, want %q", tc.key, got, tc.wantType)
		}
		if got := tc.key.ID(); got != tc.wantID {
			t.Errorf("Key(%q).ID() = %q, want %q", tc.key, got, tc.wantID)
		}
		if got := tc.key.Parent().String(); got != tc.wantParent {
			t.Errorf("Key(%q).Parent() = %q, want %q", tc.key, got, tc.wantParent)
		}
	}
}

func TestKeyChildDoesNotAlias(t *testing.T) {
	// Parent() shares the backing array of its child; children of that
	// parent must not overwrite it.
	parent := NewKey("domains", "d1").Child("groups", "a").Parent()
	b := parent.Child("groups", "b")
	c := parent.Child("groups", "c")

	if b.String() != "domains/d1/groups/b" || c.String() != "domains/d1/groups/c" {
		t.Errorf("Child(groups, b) = %q and Child(groups, c) = %q of %q, want independent keys", b, c, parent)
	}
}

func TestValidateRejectsReservedNames(t *testing.T) {
	for _, id := range []string{"", "a/b", ".", "..", "__x__", "____"} {
		if err := NewKey("domains", id).Validate(); !errors.Is(err, ErrInvalidArgument) {
			t.Errorf("NewKey(domains, %q).Validate() = %v, want %v", id, err, ErrInvalidArgument)
		}
	}
	for _, id := range []string{"a", "...", "__x", "x__", "host.example.com", "__"} {
		if err := NewKey("domains", id).Validate(); err != nil {
			t.Errorf("NewKey(domains, %q).Validate() = %v, want nil", id, err)
		}
	}
}
