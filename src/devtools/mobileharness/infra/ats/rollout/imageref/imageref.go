// Package imageref knows the form of the container image references that a
// rollout uses, and which registries they may name.
//
// An image is a repository on a registry host, "host/repository", with no
// tag and no digest: what a rollout group follows. A build is an image at
// one digest, "host/repository@sha256:...": what a rollout rolls out.
//
// Both are data in the store, and both end up naming a host that receives
// credentials: the job sends its access token to the registry of a group's
// image, and a host logs in to the registry of a rollout's build to pull it.
// So the rule for which registries are allowed lives here, in one place, for
// the job and the agent alike: Google's own.
package imageref

import (
	"errors"
	"fmt"
	"regexp"
	"strings"
)

// ErrInvalid means an image or a build is not one a rollout may use. It is
// returned wrapped; test for it with errors.Is. It does not fix itself:
// somebody has to correct the reference.
var ErrInvalid = errors.New("invalid image reference")

var (
	// digestForm is the only form of digest a rollout uses.
	digestForm = regexp.MustCompile(`^sha256:[0-9a-f]{64}$`)
	// repositoryForm is the grammar of a repository name in the OCI
	// distribution specification: lower-case path segments, none of them
	// empty, "." or "..", and nothing that needs escaping in a URL path.
	repositoryForm = regexp.MustCompile(`^[a-z0-9]+((\.|_|__|-+)[a-z0-9]+)*(/[a-z0-9]+((\.|_|__|-+)[a-z0-9]+)*)*$`)
	// googleRegistry matches the hosts of Artifact Registry and Container
	// Registry, such as "asia-docker.pkg.dev" and "gcr.io": lower case, no
	// port.
	googleRegistry = regexp.MustCompile(`^([a-z0-9-]+\.)*(pkg\.dev|gcr\.io)$`)
)

// IsDigest reports whether s is a digest in the form "sha256:<64 hex
// digits>".
func IsDigest(s string) bool {
	return digestForm.MatchString(s)
}

// IsGoogleRegistry reports whether host is a registry that a rollout may
// use, and so one that credentials may be sent to: a host of Artifact
// Registry or Container Registry.
func IsGoogleRegistry(host string) bool {
	return googleRegistry.MatchString(host)
}

// SplitImage returns the registry host and the repository of image. It
// returns an error wrapping ErrInvalid unless image is a repository alone: in the form
// host/repository, without a tag or a digest, the repository a valid name.
//
// It does not judge the host; see IsGoogleRegistry.
func SplitImage(image string) (host, repository string, err error) {
	host, repository, ok := strings.Cut(image, "/")
	switch {
	case !ok || host == "" || repository == "":
		return "", "", fmt.Errorf("%w: image %q is not in the form host/repository", ErrInvalid, image)
	case strings.Contains(repository, "@"):
		return "", "", fmt.Errorf("%w: image %q carries a digest, want the repository alone", ErrInvalid, image)
	case strings.Contains(repository[strings.LastIndex(repository, "/")+1:], ":"):
		return "", "", fmt.Errorf("%w: image %q carries a tag, want the repository alone", ErrInvalid, image)
	case !repositoryForm.MatchString(repository):
		return "", "", fmt.Errorf("%w: image %q has repository %q, which is not a valid repository name", ErrInvalid, image, repository)
	}
	return host, repository, nil
}

// Build returns the build that is image at digest: "image@digest". It checks
// neither part; ParseBuild does.
func Build(image, digest string) string {
	return image + "@" + digest
}

// ParseBuild returns the image and the digest of a build. It returns an
// error wrapping ErrInvalid unless build is one a rollout may roll out: "image@digest", the
// image a repository alone on a Google registry, the digest a sha256 digest.
func ParseBuild(build string) (image, digest string, err error) {
	image, digest, ok := strings.Cut(build, "@")
	if !ok {
		return "", "", fmt.Errorf("%w: build %q has no digest, want an image at a sha256 digest", ErrInvalid, build)
	}
	host, _, err := SplitImage(image)
	switch {
	case err != nil:
		return "", "", fmt.Errorf("build %q: %w", build, err)
	case !IsGoogleRegistry(host):
		return "", "", fmt.Errorf("%w: build %q is on host %q, want a Google registry host such as \"asia-docker.pkg.dev\" or \"gcr.io\"", ErrInvalid, build, host)
	case !IsDigest(digest):
		return "", "", fmt.Errorf("%w: build %q has digest %q, want a sha256 digest", ErrInvalid, build, digest)
	}
	return image, digest, nil
}

// Digest returns the part of ref after "@". A reference without a digest is
// its own digest.
//
// Unlike ParseBuild it accepts anything: it is for comparing what a host
// reports that it runs, which may be an image a rollout would never use,
// with a build. Two references are the same build if their digests are
// equal, however their repositories are spelled.
func Digest(ref string) string {
	if _, digest, ok := strings.Cut(ref, "@"); ok && digest != "" {
		return digest
	}
	return ref
}
