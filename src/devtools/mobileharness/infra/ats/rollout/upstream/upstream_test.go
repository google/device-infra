package upstream

import (
	"context"
	"errors"
	"fmt"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/parametermanager"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/record"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/registry"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/view"
	"github.com/google/go-cmp/cmp"
)

// The clients that the job gives a Resolver.
var (
	_ Digester  = (*registry.Client)(nil)
	_ Versioner = (*parametermanager.Client)(nil)
)

const (
	domainID   = "mtt.example.com"
	image      = "asia-docker.pkg.dev/project/repo/omnilab-ats"
	controller = record.GroupController
)

var (
	digestA = "sha256:" + strings.Repeat("a", 64)
	digestB = "sha256:" + strings.Repeat("b", 64)

	errRegistry   = errors.New("registry unreachable")
	errParameters = errors.New("parameter manager unreachable")
	// errAny stands for any error, in an expectation.
	errAny = errors.New("any error")
)

// group returns a valid group that follows tag of the test image.
func group(id, tag string) view.Group {
	return view.Group{ID: id, Key: record.GroupKey(domainID, id), Record: record.Group{Image: image, Tag: tag}}
}

// invalidGroup returns a group with invalid records, as view.Read leaves it:
// with an error and no record.
func invalidGroup(id string) view.Group {
	return view.Group{ID: id, Key: record.GroupKey(domainID, id), Err: fmt.Errorf("group %q: %w", id, record.ErrInvalid)}
}

func domain(groups ...view.Group) view.Domain {
	return view.Domain{ID: domainID, Key: record.DomainKey(domainID), Groups: groups}
}

// calls counts lookups by name and, when started is set, holds each one
// until release is closed or the lookup's context is done.
type calls struct {
	started chan string
	release chan struct{}

	mu    sync.Mutex
	count map[string]int
}

func (c *calls) enter(ctx context.Context, name string) error {
	c.mu.Lock()
	if c.count == nil {
		c.count = make(map[string]int)
	}
	c.count[name]++
	c.mu.Unlock()
	if c.started == nil {
		return nil
	}
	c.started <- name
	select {
	case <-c.release:
		return nil
	case <-ctx.Done():
		return ctx.Err()
	}
}

// answers is a fake Digester and Versioner. A lookup it has no answer for is
// an error, so that a test names every lookup it expects.
type answers struct {
	calls *calls
	// digests maps "image:tag" to a digest or an error.
	digests map[string]any
	// versions maps a parameter ID to a version ID or an error.
	versions map[string]any
}

func answer(from map[string]any, name string) (string, error) {
	switch v := from[name].(type) {
	case string:
		return v, nil
	case error:
		return "", v
	}
	return "", fmt.Errorf("the test has no answer for lookup %q", name)
}

func (a *answers) Digest(ctx context.Context, image, tag string) (string, error) {
	name := image + ":" + tag
	if err := a.calls.enter(ctx, name); err != nil {
		return "", err
	}
	return answer(a.digests, name)
}

func (a *answers) Version(ctx context.Context, parameter string) (string, error) {
	if err := a.calls.enter(ctx, parameter); err != nil {
		return "", err
	}
	return answer(a.versions, parameter)
}

func newResolver(a *answers) *Resolver {
	if a.calls == nil {
		a.calls = &calls{}
	}
	return &Resolver{Images: a, Configs: a}
}

// checkUpstreams checks every group of d: a group in want is resolved to that
// revision; a group in wantErr is unresolved with that error (errAny: any
// error); every other group has neither an upstream nor an error.
func checkUpstreams(t *testing.T, d view.Domain, want map[string]record.Revision, wantErr map[string]error) {
	t.Helper()
	for _, g := range d.Groups {
		rev, wantResolved := want[g.ID]
		target, wantUnresolved := wantErr[g.ID]
		switch {
		case wantResolved:
			if !g.Resolved() {
				t.Errorf("group %q is not resolved (error: %v), want upstream %+v", g.ID, g.UpstreamErr, rev)
			} else if diff := cmp.Diff(rev, g.Upstream); diff != "" {
				t.Errorf("upstream of group %q (-want +got):\n%s", g.ID, diff)
			}
		case wantUnresolved:
			if g.Resolved() || g.UpstreamErr == nil || !g.Upstream.IsZero() {
				t.Errorf("group %q has upstream %+v and error %v, want no upstream and an error", g.ID, g.Upstream, g.UpstreamErr)
			} else if target != errAny && !errors.Is(g.UpstreamErr, target) {
				t.Errorf("group %q is unresolved with error %v, want it to wrap %v", g.ID, g.UpstreamErr, target)
			}
		default:
			if !g.Upstream.IsZero() || g.UpstreamErr != nil {
				t.Errorf("group %q has upstream %+v and error %v, want it left alone", g.ID, g.Upstream, g.UpstreamErr)
			}
		}
	}
}

func TestResolve(t *testing.T) {
	const latest = image + ":latest"
	tests := []struct {
		name    string
		groups  []view.Group
		answers answers
		want    map[string]record.Revision
		wantErr map[string]error
		// wantCalls is every lookup made, with how many times.
		wantCalls map[string]int
	}{
		{
			name:    "no_groups",
			answers: answers{},
		},
		{
			// The controller's upstream is the build alone, a pool's has both config versions.
			name:   "controller_build_only_pools_with_configs",
			groups: []view.Group{group(controller, "latest"), group("dda", "latest"), group("public_testing", "latest")},
			answers: answers{
				digests:  map[string]any{latest: digestA},
				versions: map[string]any{"tenant_config": "t1", "dda_pool_config": "p1", "public_testing_pool_config": "p2"},
			},
			want: map[string]record.Revision{
				controller:       {Build: image + "@" + digestA},
				"dda":            {Build: image + "@" + digestA, TenantConfigVersion: "t1", PoolConfigVersion: "p1"},
				"public_testing": {Build: image + "@" + digestA, TenantConfigVersion: "t1", PoolConfigVersion: "p2"},
			},
			// One digest lookup for the three groups, and one tenant lookup
			// for the two pools.
			wantCalls: map[string]int{latest: 1, "tenant_config": 1, "dda_pool_config": 1, "public_testing_pool_config": 1},
		},
		{
			// A domain with only a controller reads no parameter.
			name:      "controller_only_reads_no_parameter",
			groups:    []view.Group{group(controller, "latest")},
			answers:   answers{digests: map[string]any{latest: digestA}},
			want:      map[string]record.Revision{controller: {Build: image + "@" + digestA}},
			wantCalls: map[string]int{latest: 1},
		},
		{
			// Groups that follow different tags are resolved separately.
			name:   "different_tags_resolved_separately",
			groups: []view.Group{group(controller, "stable"), group("dda", "latest")},
			answers: answers{
				digests:  map[string]any{image + ":stable": digestA, latest: digestB},
				versions: map[string]any{"tenant_config": "t1", "dda_pool_config": "p1"},
			},
			want: map[string]record.Revision{
				controller: {Build: image + "@" + digestA},
				"dda":      {Build: image + "@" + digestB, TenantConfigVersion: "t1", PoolConfigVersion: "p1"},
			},
			wantCalls: map[string]int{image + ":stable": 1, latest: 1, "tenant_config": 1, "dda_pool_config": 1},
		},
		{
			// A failed digest lookup leaves unresolved the groups that follow that tag, and no other.
			name:   "digest_lookup_fails",
			groups: []view.Group{group(controller, "stable"), group("dda", "latest"), group("public_testing", "stable")},
			answers: answers{
				digests:  map[string]any{image + ":stable": errRegistry, latest: digestB},
				versions: map[string]any{"tenant_config": "t1", "dda_pool_config": "p1", "public_testing_pool_config": "p2"},
			},
			want:      map[string]record.Revision{"dda": {Build: image + "@" + digestB, TenantConfigVersion: "t1", PoolConfigVersion: "p1"}},
			wantErr:   map[string]error{controller: errRegistry, "public_testing": errRegistry},
			wantCalls: map[string]int{image + ":stable": 1, latest: 1, "tenant_config": 1, "dda_pool_config": 1, "public_testing_pool_config": 1},
		},
		{
			// A failed tenant config lookup leaves every pool unresolved, not the controller.
			name:   "tenant_config_lookup_fails",
			groups: []view.Group{group(controller, "latest"), group("dda", "latest"), group("public_testing", "latest")},
			answers: answers{
				digests:  map[string]any{latest: digestA},
				versions: map[string]any{"tenant_config": errParameters, "dda_pool_config": "p1", "public_testing_pool_config": "p2"},
			},
			want:      map[string]record.Revision{controller: {Build: image + "@" + digestA}},
			wantErr:   map[string]error{"dda": errParameters, "public_testing": errParameters},
			wantCalls: map[string]int{latest: 1, "tenant_config": 1, "dda_pool_config": 1, "public_testing_pool_config": 1},
		},
		{
			// A failed pool config lookup leaves that pool unresolved: no upstream in part.
			name:   "pool_config_lookup_fails",
			groups: []view.Group{group("dda", "latest"), group("public_testing", "latest")},
			answers: answers{
				digests:  map[string]any{latest: digestA},
				versions: map[string]any{"tenant_config": "t1", "dda_pool_config": errParameters, "public_testing_pool_config": "p2"},
			},
			want:      map[string]record.Revision{"public_testing": {Build: image + "@" + digestA, TenantConfigVersion: "t1", PoolConfigVersion: "p2"}},
			wantErr:   map[string]error{"dda": errParameters},
			wantCalls: map[string]int{latest: 1, "tenant_config": 1, "dda_pool_config": 1, "public_testing_pool_config": 1},
		},
		{
			// A lookup that returns nothing is a failure.
			name:   "empty_lookup_result_is_failure",
			groups: []view.Group{group(controller, "stable"), group("dda", "latest"), group("public_testing", "latest")},
			answers: answers{
				digests:  map[string]any{image + ":stable": "", latest: digestA},
				versions: map[string]any{"tenant_config": "t1", "dda_pool_config": "", "public_testing_pool_config": "p2"},
			},
			want:      map[string]record.Revision{"public_testing": {Build: image + "@" + digestA, TenantConfigVersion: "t1", PoolConfigVersion: "p2"}},
			wantErr:   map[string]error{controller: errAny, "dda": errAny},
			wantCalls: map[string]int{image + ":stable": 1, latest: 1, "tenant_config": 1, "dda_pool_config": 1, "public_testing_pool_config": 1},
		},
		{
			// A group with invalid records is left alone, and nothing is looked up for it.
			name:   "invalid_group_left_alone",
			groups: []view.Group{invalidGroup(controller), group("dda", "latest"), invalidGroup("public_testing")},
			answers: answers{
				digests:  map[string]any{latest: digestA},
				versions: map[string]any{"tenant_config": "t1", "dda_pool_config": "p1"},
			},
			want:      map[string]record.Revision{"dda": {Build: image + "@" + digestA, TenantConfigVersion: "t1", PoolConfigVersion: "p1"}},
			wantCalls: map[string]int{latest: 1, "tenant_config": 1, "dda_pool_config": 1},
		},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			d := domain(tc.groups...)
			r := newResolver(&tc.answers)

			r.Resolve(context.Background(), &d)

			checkUpstreams(t, d, tc.want, tc.wantErr)
			if diff := cmp.Diff(tc.wantCalls, tc.answers.calls.count); diff != "" {
				t.Errorf("Resolve() lookups (-want +got):\n%s", diff)
			}
		})
	}
}

// TestResolve_ReplacesStaleUpstream resolves a domain whose groups
// already hold an upstream or an error: a valid group keeps neither, whatever
// its lookups return.
func TestResolve_ReplacesStaleUpstream(t *testing.T) {
	stale := record.Revision{Build: image + "@" + digestB, TenantConfigVersion: "t0", PoolConfigVersion: "p0"}
	failsNow := group(controller, "stable")
	failsNow.Upstream = stale
	resolvesNow := group("dda", "latest")
	resolvesNow.UpstreamErr = errRegistry
	changed := group("public_testing", "latest")
	changed.Upstream = stale
	d := domain(failsNow, resolvesNow, changed)
	r := newResolver(&answers{
		digests:  map[string]any{image + ":stable": errRegistry, image + ":latest": digestA},
		versions: map[string]any{"tenant_config": "t1", "dda_pool_config": "p1", "public_testing_pool_config": "p2"},
	})

	r.Resolve(context.Background(), &d)

	checkUpstreams(t, d,
		map[string]record.Revision{
			"dda":            {Build: image + "@" + digestA, TenantConfigVersion: "t1", PoolConfigVersion: "p1"},
			"public_testing": {Build: image + "@" + digestA, TenantConfigVersion: "t1", PoolConfigVersion: "p2"},
		},
		map[string]error{controller: errRegistry})
}

func TestResolve_LookupsRunConcurrently(t *testing.T) {
	// Two digests, the tenant config, and two pool configs. Every lookup
	// waits until all five have started, so a resolver that made them one
	// after another would never finish.
	const lookups = 5
	c := &calls{started: make(chan string, lookups), release: make(chan struct{})}
	a := &answers{
		calls:    c,
		digests:  map[string]any{image + ":stable": digestA, image + ":latest": digestB},
		versions: map[string]any{"tenant_config": "t1", "dda_pool_config": "p1", "public_testing_pool_config": "p2"},
	}
	d := domain(group(controller, "stable"), group("dda", "latest"), group("public_testing", "latest"))
	r := newResolver(a)
	done := make(chan struct{})
	go func() {
		defer close(done)
		r.Resolve(context.Background(), &d)
	}()

	for i := range lookups {
		select {
		case <-c.started:
		case <-time.After(30 * time.Second):
			t.Fatalf("only %d of %d lookups started while the others were still running", i, lookups)
		}
	}
	close(c.release)
	<-done

	checkUpstreams(t, d, map[string]record.Revision{
		controller:       {Build: image + "@" + digestA},
		"dda":            {Build: image + "@" + digestB, TenantConfigVersion: "t1", PoolConfigVersion: "p1"},
		"public_testing": {Build: image + "@" + digestB, TenantConfigVersion: "t1", PoolConfigVersion: "p2"},
	}, nil)
}

func TestResolve_TimeoutBoundsWholeCall(t *testing.T) {
	// No lookup ever returns on its own.
	c := &calls{started: make(chan string, 16), release: make(chan struct{})}
	r := newResolver(&answers{calls: c})
	r.Timeout = 50 * time.Millisecond
	d := domain(group(controller, "latest"), group("dda", "latest"), invalidGroup("public_testing"))
	start := time.Now()

	r.Resolve(context.Background(), &d)

	if elapsed := time.Since(start); elapsed > 10*time.Second {
		t.Errorf("Resolve() with a timeout of %v took %v", r.Timeout, elapsed)
	}
	checkUpstreams(t, d, nil, map[string]error{controller: context.DeadlineExceeded, "dda": context.DeadlineExceeded})
}

func TestResolve_DoneContext(t *testing.T) {
	c := &calls{started: make(chan string, 16), release: make(chan struct{})}
	r := newResolver(&answers{calls: c})
	d := domain(group(controller, "latest"), group("dda", "latest"))
	ctx, cancel := context.WithCancel(context.Background())
	cancel()

	r.Resolve(ctx, &d)

	checkUpstreams(t, d, nil, map[string]error{controller: context.Canceled, "dda": context.Canceled})
}

func TestPoolParameter(t *testing.T) {
	if got, want := PoolParameter("public_testing"), "public_testing_pool_config"; got != want {
		t.Errorf("PoolParameter(%q) = %q, want %q", "public_testing", got, want)
	}
}
