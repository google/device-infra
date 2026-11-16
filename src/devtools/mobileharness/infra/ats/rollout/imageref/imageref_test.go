package imageref

import (
	"strings"
	"testing"
)

const image = "asia-docker.pkg.dev/project/repo/omnilab-ats"

var (
	digestA = "sha256:" + strings.Repeat("a", 64)
	digestB = "sha256:" + strings.Repeat("b", 64)
)

func TestIsDigest(t *testing.T) {
	tests := []struct {
		digest string
		want   bool
	}{
		{digest: digestA, want: true},
		{digest: "sha256:abc"},
		{digest: "sha256:" + strings.Repeat("A", 64)},
		{digest: "sha512:" + strings.Repeat("a", 128)},
		{digest: strings.Repeat("a", 64)},
		{digest: digestA + "\n"},
		{digest: ""},
	}
	for _, tc := range tests {
		if got := IsDigest(tc.digest); got != tc.want {
			t.Errorf("IsDigest(%q) = %t, want %t", tc.digest, got, tc.want)
		}
	}
}

func TestIsGoogleRegistry(t *testing.T) {
	tests := []struct {
		host string
		want bool
	}{
		{host: "asia-docker.pkg.dev", want: true},
		{host: "us-central1-docker.pkg.dev", want: true},
		{host: "gcr.io", want: true},
		{host: "eu.gcr.io", want: true},
		{host: "registry.example.com"},
		{host: "docker.io"},
		{host: "localhost"},
		{host: "127.0.0.1:5000"},
		// Hosts made to look like a Google registry.
		{host: "pkg.dev.example.com"},
		{host: "examplepkg.dev"},
		{host: "example.com:443#.pkg.dev"},
		{host: "example.com?x=.pkg.dev"},
		{host: "example.com\\.pkg.dev"},
		{host: "asia-docker.pkg.dev:443"},
		{host: "asia-docker.pkg.dev."},
		{host: "ASIA-DOCKER.PKG.DEV"},
		{host: ".pkg.dev"},
		{host: ""},
	}
	for _, tc := range tests {
		if got := IsGoogleRegistry(tc.host); got != tc.want {
			t.Errorf("IsGoogleRegistry(%q) = %t, want %t", tc.host, got, tc.want)
		}
	}
}

func TestSplitImage(t *testing.T) {
	tests := []struct {
		image          string
		wantHost       string
		wantRepository string
	}{
		{image: image, wantHost: "asia-docker.pkg.dev", wantRepository: "project/repo/omnilab-ats"},
		{image: "gcr.io/project/img", wantHost: "gcr.io", wantRepository: "project/img"},
		// A repository of one segment.
		{image: "gcr.io/img", wantHost: "gcr.io", wantRepository: "img"},
		// A domain-scoped project, and each separator a path segment may have.
		{image: "gcr.io/example.com/my-project/repo_1/a__b/c--d/img.v2", wantHost: "gcr.io", wantRepository: "example.com/my-project/repo_1/a__b/c--d/img.v2"},
		// The host is not judged here.
		{image: "127.0.0.1:5000/project/img", wantHost: "127.0.0.1:5000", wantRepository: "project/img"},
	}
	for _, tc := range tests {
		host, repository, err := SplitImage(tc.image)
		if err != nil || host != tc.wantHost || repository != tc.wantRepository {
			t.Errorf("SplitImage(%q) = %q, %q, %v, want %q, %q", tc.image, host, repository, err, tc.wantHost, tc.wantRepository)
		}
	}
}

func TestSplitImage_Errors(t *testing.T) {
	tests := []struct {
		name  string
		image string
	}{
		{name: "empty", image: ""},
		{name: "no_repository", image: "gcr.io"},
		{name: "empty_repository", image: "gcr.io/"},
		{name: "no_host", image: "/project/img"},
		{name: "empty_path_segment", image: "gcr.io/project//img"},
		{name: "parent_path_segment", image: "gcr.io/project/../img"},
		{name: "trailing_slash", image: "gcr.io/project/img/"},
		{name: "with_tag", image: "gcr.io/project/img:latest"},
		{name: "with_digest", image: "gcr.io/project/img@" + digestA},
		// The same on a repository of one segment, which has no "/" to
		// look for the tag after.
		{name: "one_segment_with_tag", image: "gcr.io/img:latest"},
		{name: "one_segment_with_digest", image: "gcr.io/img@" + digestA},
		{name: "upper_case", image: "gcr.io/Project/img"},
		{name: "space", image: "gcr.io/project/my img"},
		{name: "query", image: "gcr.io/project/img?a=b"},
		{name: "fragment", image: "gcr.io/project/img#a"},
		{name: "escape", image: "gcr.io/project%2Fimg"},
		{name: "colon_before_last_segment", image: "gcr.io/pro:ject/img"},
		{name: "segment_starts_with_separator", image: "gcr.io/project/-img"},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			host, repository, err := SplitImage(tc.image)
			if err == nil || host != "" || repository != "" {
				t.Errorf("SplitImage(%q) = %q, %q, %v, want an error and no parts", tc.image, host, repository, err)
			}
		})
	}
}

func TestParseBuild(t *testing.T) {
	build := Build(image, digestA)
	if want := image + "@" + digestA; build != want {
		t.Fatalf("Build(%q, %q) = %q, want %q", image, digestA, build, want)
	}

	gotImage, gotDigest, err := ParseBuild(build)

	if err != nil || gotImage != image || gotDigest != digestA {
		t.Errorf("ParseBuild(%q) = %q, %q, %v, want %q, %q", build, gotImage, gotDigest, err, image, digestA)
	}
}

func TestParseBuild_Errors(t *testing.T) {
	tests := []struct {
		name  string
		build string
	}{
		{name: "empty", build: ""},
		{name: "no_digest", build: image},
		{name: "tag_instead_of_digest", build: image + ":latest"},
		{name: "empty_digest", build: image + "@"},
		{name: "short_digest", build: image + "@sha256:abc"},
		{name: "digest_not_sha256", build: image + "@sha512:" + strings.Repeat("a", 128)},
		{name: "two_digests", build: image + "@" + digestA + "@" + digestB},
		{name: "tag_and_digest", build: image + ":latest@" + digestA},
		{name: "digest_alone", build: digestA},
		{name: "no_repository", build: "gcr.io@" + digestA},
		{name: "repository_upper_case", build: "gcr.io/Project/img@" + digestA},
		// A host that would get the credentials of every host of the group.
		{name: "other_registry", build: "registry.example.com/project/img@" + digestA},
		{name: "local_registry", build: "localhost:5000/project/img@" + digestA},
		{name: "host_like_google", build: "pkg.dev.example.com/project/img@" + digestA},
		{name: "host_with_port", build: "asia-docker.pkg.dev:443/project/img@" + digestA},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			gotImage, gotDigest, err := ParseBuild(tc.build)
			if err == nil || gotImage != "" || gotDigest != "" {
				t.Errorf("ParseBuild(%q) = %q, %q, %v, want an error and no parts", tc.build, gotImage, gotDigest, err)
			}
		})
	}
}

func TestDigest(t *testing.T) {
	tests := []struct {
		ref  string
		want string
	}{
		{ref: image + "@" + digestA, want: digestA},
		// The same build under another repository name.
		{ref: "mirror.example.com/img@" + digestA, want: digestA},
		// What a host may report: a tag, or a local image ID.
		{ref: "mtt:latest", want: "mtt:latest"},
		{ref: digestA, want: digestA},
		{ref: image + "@", want: image + "@"},
		{ref: "", want: ""},
	}
	for _, tc := range tests {
		if got := Digest(tc.ref); got != tc.want {
			t.Errorf("Digest(%q) = %q, want %q", tc.ref, got, tc.want)
		}
	}
}
