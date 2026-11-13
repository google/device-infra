// Package registry resolves an image tag to the digest it points to, through
// the HTTP API of a container registry (the OCI distribution API, as served
// by Artifact Registry).
//
// The package does no authentication of its own: the caller passes a function
// that returns an OAuth access token, and every request carries it.
package registry

import (
	"context"
	"errors"
	"fmt"
	"net/http"
	"net/url"
	"regexp"
	"strings"
)

// ErrNotFound means the registry has no such image, or the image has no such
// tag. It is returned wrapped; test for it with errors.Is. Unlike every other
// error of this package, it is not going to fix itself: somebody has to push
// the image or correct the group's image or tag.
var ErrNotFound = errors.New("image or tag not found in the registry")

// accept lists the manifest types a tag may point to. It includes the two
// index types, so that for a multi-platform image the registry answers with
// the digest of the index. That is the digest `docker pull` records in the
// image's RepoDigests, which is what a host reports as its running build.
var accept = strings.Join([]string{
	"application/vnd.oci.image.index.v1+json",
	"application/vnd.docker.distribution.manifest.list.v2+json",
	"application/vnd.oci.image.manifest.v1+json",
	"application/vnd.docker.distribution.manifest.v2+json",
}, ", ")

var (
	// digestForm is the form of the digests this package returns.
	digestForm = regexp.MustCompile(`^sha256:[0-9a-f]{64}$`)
	// tagForm is the grammar of a tag in the OCI distribution specification.
	// A tag in this form needs no escaping in a URL path and is never "." or
	// "..", which a URL would resolve to another path.
	tagForm = regexp.MustCompile(`^[a-zA-Z0-9_][a-zA-Z0-9._-]{0,127}$`)
	// repositoryForm is the grammar of a repository name in the same
	// specification: lower-case path segments, none of them empty, "." or
	// "..", and nothing that needs escaping in a URL path.
	repositoryForm = regexp.MustCompile(`^[a-z0-9]+((\.|_|__|-+)[a-z0-9]+)*(/[a-z0-9]+((\.|_|__|-+)[a-z0-9]+)*)*$`)
	// googleRegistry matches the hosts of Artifact Registry and Container
	// Registry, such as "asia-docker.pkg.dev" and "gcr.io": lower case, no
	// port.
	googleRegistry = regexp.MustCompile(`^([a-z0-9-]+\.)*(pkg\.dev|gcr\.io)$`)
)

// Client resolves tags on Google's container registries. It is safe for
// concurrent use.
//
// It refuses an image on any other host, whether or not resolving it takes a
// request. The host is part of a group's image, which is data in the store,
// and two things follow the image: this client sends the caller's access
// token to that host, and every host of the group later logs in to it with
// its own credentials to pull the build. Without the check, whoever can
// write a group record could have those credentials sent to a host of their
// choosing.
type Client struct {
	http  *http.Client
	token func(context.Context) (string, error)
	// allowed reports whether a host is a registry that credentials may be
	// sent to. It is googleRegistry's match except in tests, which serve a
	// registry locally.
	allowed func(host string) bool
}

// New returns a client that sends its requests with c and authenticates them
// with the access token that token returns. c must not add credentials of its
// own. token is called once per request.
func New(c *http.Client, token func(context.Context) (string, error)) (*Client, error) {
	switch {
	case c == nil:
		return nil, errors.New("registry: the HTTP client is nil")
	case token == nil:
		return nil, errors.New("registry: the token function is nil")
	}
	return &Client{http: c, token: token, allowed: googleRegistry.MatchString}, nil
}

// Digest returns the digest, in the form "sha256:<64 hex digits>", that tag
// points to in the repository image.
//
// image is a repository on a Google registry, without a tag or digest, such
// as "asia-docker.pkg.dev/project/repo/omnilab-ats". tag is a tag such as
// "latest", or a pinned digest in the form "@sha256:...", which is returned
// as it is without a request.
//
// The image is checked in both cases, before anything else: the caller builds
// the reference "image@digest" from the result, so a pinned tag must not let
// through an image on another host, one that already carries a tag or a
// digest, or one that is no valid repository name.
func (c *Client) Digest(ctx context.Context, image, tag string) (string, error) {
	host, repository, err := c.splitImage(image)
	if err != nil {
		return "", err
	}
	if pinned, ok := strings.CutPrefix(tag, "@"); ok {
		if !digestForm.MatchString(pinned) {
			return "", fmt.Errorf("registry: tag %q of image %q starts with \"@\" but is not a sha256 digest", tag, image)
		}
		return pinned, nil
	}
	if !tagForm.MatchString(tag) {
		return "", fmt.Errorf("registry: %q is not a valid tag of image %q", tag, image)
	}
	// The scheme is always https: the request carries a credential.
	target := url.URL{Scheme: "https", Host: host, Path: "/v2/" + repository + "/manifests/" + tag}
	token, err := c.token(ctx)
	if err != nil {
		return "", fmt.Errorf("registry: getting an access token to resolve %s:%s: %w", image, tag, err)
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodHead, target.String(), nil)
	if err != nil {
		return "", fmt.Errorf("registry: resolving %s:%s: %w", image, tag, err)
	}
	req.Header.Set("Accept", accept)
	// Registries take an OAuth access token as the password of this user.
	req.SetBasicAuth("oauth2accesstoken", token)
	resp, err := c.http.Do(req)
	if err != nil {
		return "", fmt.Errorf("registry: resolving %s:%s: %w", image, tag, err)
	}
	defer resp.Body.Close()
	switch {
	case resp.StatusCode == http.StatusNotFound:
		return "", fmt.Errorf("registry: resolving %s:%s: %w", image, tag, ErrNotFound)
	case resp.StatusCode != http.StatusOK:
		return "", fmt.Errorf("registry: resolving %s:%s: the registry answered %s", image, tag, resp.Status)
	}
	digest := resp.Header.Get("Docker-Content-Digest")
	if !digestForm.MatchString(digest) {
		return "", fmt.Errorf("registry: resolving %s:%s: the response has Docker-Content-Digest %q, want a sha256 digest", image, tag, digest)
	}
	return digest, nil
}

// splitImage returns the registry host and the repository path of image. It
// returns an error unless the host is one that credentials may be sent to and
// the image is a repository alone: in the form host/repository, without a tag
// or a digest, the repository a valid name.
func (c *Client) splitImage(image string) (host, repository string, err error) {
	host, repository, ok := strings.Cut(image, "/")
	switch {
	case !ok || host == "" || repository == "":
		return "", "", fmt.Errorf("registry: image %q is not in the form host/repository", image)
	case !c.allowed(host):
		return "", "", fmt.Errorf("registry: image %q is on host %q, want a Google registry host such as \"asia-docker.pkg.dev\" or \"gcr.io\"", image, host)
	case strings.Contains(repository, "@"):
		return "", "", fmt.Errorf("registry: image %q carries a digest, want the repository alone", image)
	case strings.Contains(repository[strings.LastIndex(repository, "/")+1:], ":"):
		return "", "", fmt.Errorf("registry: image %q carries a tag, want the repository alone", image)
	case !repositoryForm.MatchString(repository):
		return "", "", fmt.Errorf("registry: image %q has repository %q, which is not a valid repository name", image, repository)
	}
	return host, repository, nil
}
