package fake_test

import (
	"context"
	"maps"
	"testing"

	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/store/fake"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/store"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/store/storetest"
)

func TestContract(t *testing.T) {
	storetest.Run(t, func(*testing.T) store.Store { return fake.New() })
}

func TestAll(t *testing.T) {
	ctx := context.Background()
	s := fake.New()
	group := store.NewKey("domains", "d").Child("groups", "g")
	member := group.Child("members", "m")
	deleted := group.Child("members", "gone")
	for _, key := range []store.Key{group, member, deleted} {
		if _, err := s.Create(ctx, key, store.Record{"id": key.ID()}); err != nil {
			t.Fatalf("Create(%q) failed: %v", key, err)
		}
	}
	_, v, err := s.Get(ctx, deleted)
	if err != nil {
		t.Fatalf("Get(%q) failed: %v", deleted, err)
	}
	if err := s.Delete(ctx, deleted, v); err != nil {
		t.Fatalf("Delete(%q) failed: %v", deleted, err)
	}

	got := map[store.Key]string{}
	for _, it := range s.All() {
		if it.Version.IsZero() {
			t.Errorf("All() returned %q with a zero version", it.Key)
		}
		got[it.Key], _ = it.Record["id"].(string)
	}
	want := map[store.Key]string{group: "g", member: "m"}
	if !maps.Equal(got, want) {
		t.Errorf("All() returned records %v, want %v", got, want)
	}
}
