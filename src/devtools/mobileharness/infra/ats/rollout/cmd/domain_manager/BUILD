# Copyright 2022 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

load("@rules_go//go:def.bzl", "go_binary", "go_test")
load("@rules_oci//oci:defs.bzl", "oci_image", "oci_load", "oci_push")
load("@rules_pkg//pkg:tar.bzl", "pkg_tar")

package(default_applicable_licenses = ["//:license"])

go_binary(
    name = "domain_manager",
    srcs = ["main.go"],
    goarch = "amd64",
    goos = "linux",
    pure = "on",
    deps = [
        "//src/devtools/mobileharness/infra/ats/rollout/auth",
        "//src/devtools/mobileharness/infra/ats/rollout/imageref",
        "//src/devtools/mobileharness/infra/ats/rollout/job",
        "//src/devtools/mobileharness/infra/ats/rollout/parametermanager",
        "//src/devtools/mobileharness/infra/ats/rollout/registry",
        "//src/devtools/mobileharness/infra/ats/rollout/store/firestore",
        "//src/devtools/mobileharness/infra/ats/rollout/upstream",
    ],
)

# The internal build gives the test the binary as its library. The external
# Go rules cannot embed a binary in a test, so there the test compiles
# main.go itself, with the dependencies of main.go.
go_test(
    name = "domain_manager_test",
    srcs = [
        "main.go",
        "main_test.go",
    ],
    deps = [
        "//src/devtools/mobileharness/infra/ats/rollout/auth",
        "//src/devtools/mobileharness/infra/ats/rollout/imageref",
        "//src/devtools/mobileharness/infra/ats/rollout/job",
        "//src/devtools/mobileharness/infra/ats/rollout/parametermanager",
        "//src/devtools/mobileharness/infra/ats/rollout/registry",
        "//src/devtools/mobileharness/infra/ats/rollout/store/firestore",
        "//src/devtools/mobileharness/infra/ats/rollout/upstream",
        "@com_github_google_go_cmp//cmp",
    ],
)

# The container image of the job. The push target names no repository:
# pass one when running it, after "--", as --repository.
pkg_tar(
    name = "domain_manager_tar",
    srcs = [":domain_manager"],
    package_dir = "/rollout",
)

oci_image(
    name = "domain_manager_image",
    base = "@distroless_static",
    entrypoint = ["/rollout/domain_manager"],
    tags = ["deviceinfra_need_gcloud_auth"],
    tars = [":domain_manager_tar"],
)

oci_load(
    name = "domain_manager_load",
    image = ":domain_manager_image",
    repo_tags = ["rollout/domain_manager:latest"],
    tags = ["deviceinfra_need_gcloud_auth"],
)

oci_push(
    name = "domain_manager_push",
    image = ":domain_manager_image",
    remote_tags = ["latest"],
    tags = ["deviceinfra_need_gcloud_auth"],
)
