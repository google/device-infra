// Package upstream resolves, for each rollout group of a domain, the revision
// the group should be running: its upstream.
//
// A group's upstream is the digest that its image tag points to in the
// registry and, for every group other than the controller, the versions that
// the alias "latest" of the tenant config and of the group's pool config
// resolve to in Parameter Manager. When the upstream differs from the
// revision the group rolled out last, the manager starts a rollout.
//
// A run resolves the upstreams once, on the snapshot the manager decides on.
// Only the manager reads them.
package upstream

import (
	"context"
	"errors"
	"fmt"
	"sync"
	"time"

	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/record"
	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/view"
)

// TenantParameter is the ID of the Parameter Manager parameter that holds
// the tenant config, which every pool of a domain shares.
const TenantParameter = "tenant_config"

// PoolParameter returns the ID of the Parameter Manager parameter that holds
// the pool config of the group with the given ID. The controller group has
// none.
func PoolParameter(groupID string) string {
	return groupID + "_pool_config"
}

// Digester resolves an image tag to a digest. registry.Client is one.
type Digester interface {
	// Digest returns the digest, such as "sha256:...", that tag points to in
	// the repository image. It returns an error for an image whose build
	// hosts must not pull, such as one on a registry that is not allowed.
	Digest(ctx context.Context, image, tag string) (string, error)
}

// Versioner resolves the alias "latest" of a parameter to a version.
// parametermanager.Client is one.
type Versioner interface {
	// Version returns the ID of the version that "latest" resolves to.
	Version(ctx context.Context, parameter string) (string, error)
}

// Resolver resolves the upstreams of a domain's groups.
type Resolver struct {
	Images  Digester
	Configs Versioner
	// Timeout bounds one Resolve call as a whole. Every lookup of the call
	// starts at once, so this is also the most any one lookup can take; there
	// is no timeout per lookup. Zero sets no bound beyond the context's own.
	Timeout time.Duration
}

// lookup is the result of one call to the registry or to Parameter Manager.
type lookup struct {
	value string
	err   error
}

// imageTag identifies a registry lookup.
type imageTag struct {
	image, tag string
}

// Resolve sets Upstream and UpstreamErr on every valid group of d, and leaves
// groups with invalid records alone.
//
// It makes each distinct lookup once, and all of them at the same time:
// groups that follow the same tag of the same image get the same digest, and
// all pools get the same tenant config version, even if the tag or the config
// moves during the call.
//
// It returns no error. A lookup that fails, including one cut short by the
// timeout, leaves unresolved the groups that needed it, and only those, each
// with the error in UpstreamErr; a group is never resolved in part.
func (r *Resolver) Resolve(ctx context.Context, d *view.Domain) {
	if r.Timeout > 0 {
		var cancel context.CancelFunc
		ctx, cancel = context.WithTimeout(ctx, r.Timeout)
		defer cancel()
	}
	digests := make(map[imageTag]*lookup)
	versions := make(map[string]*lookup)
	for i := range d.Groups {
		g := &d.Groups[i]
		if g.Err != nil {
			continue
		}
		digests[imageTag{image: g.Record.Image, tag: g.Record.Tag}] = &lookup{}
		if g.ID != record.GroupController {
			versions[TenantParameter] = &lookup{}
			versions[PoolParameter(g.ID)] = &lookup{}
		}
	}

	// Each goroutine writes to a lookup of its own, so they share no state.
	var wg sync.WaitGroup
	for key, l := range digests {
		wg.Go(func() { l.value, l.err = r.Images.Digest(ctx, key.image, key.tag) })
	}
	for parameter, l := range versions {
		wg.Go(func() { l.value, l.err = r.Configs.Version(ctx, parameter) })
	}
	wg.Wait()

	for i := range d.Groups {
		g := &d.Groups[i]
		if g.Err != nil {
			continue
		}
		g.Upstream, g.UpstreamErr = revisionOf(g, digests, versions)
	}
}

// revisionOf assembles the upstream of g from the lookups, or returns the
// error of the first lookup that g needed and that failed.
func revisionOf(g *view.Group, digests map[imageTag]*lookup, versions map[string]*lookup) (record.Revision, error) {
	image, tag := g.Record.Image, g.Record.Tag
	digest, err := digests[imageTag{image: image, tag: tag}].get()
	if err != nil {
		return record.Revision{}, fmt.Errorf("resolving tag %q of image %q: %w", tag, image, err)
	}
	rev := record.Revision{Build: image + "@" + digest}
	if g.ID == record.GroupController {
		return rev, nil
	}
	if rev.TenantConfigVersion, err = versions[TenantParameter].get(); err != nil {
		return record.Revision{}, fmt.Errorf("resolving the latest version of parameter %q: %w", TenantParameter, err)
	}
	if rev.PoolConfigVersion, err = versions[PoolParameter(g.ID)].get(); err != nil {
		return record.Revision{}, fmt.Errorf("resolving the latest version of parameter %q: %w", PoolParameter(g.ID), err)
	}
	return rev, nil
}

// get returns the value of a lookup. An empty value is an error whatever the
// lookup said: a revision with an empty part would compare equal to one that
// has no such part.
func (l *lookup) get() (string, error) {
	switch {
	case l.err != nil:
		return "", l.err
	case l.value == "":
		return "", errors.New("the lookup returned nothing")
	}
	return l.value, nil
}
