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

load("@rules_go//go:def.bzl", "go_binary")
load("@rules_oci//oci:defs.bzl", "oci_image", "oci_load", "oci_push")
load("@rules_pkg//pkg:tar.bzl", "pkg_tar")

package(default_applicable_licenses = ["//:license"])

go_binary(
    name = "helloworld",
    srcs = ["server.go"],
    goarch = "amd64",
    goos = "linux",
    pure = "on",
    deps = [
        "//src/devtools/mobileharness/shared/util/comm/dualconduit/tests/helloworld",
        "//src/devtools/mobileharness/shared/util/comm/dualconduit/tests/helloworld:helloworld_service_go_grpc",
        "@org_golang_google_grpc//:grpc",
        "@org_golang_google_grpc//reflection",
    ],
)

# ==========================================
# Build and push Docker image.
# ==========================================
pkg_tar(
    name = "helloworld_tar",
    srcs = [":helloworld"],
    package_dir = "/dualconduit",
)

oci_image(
    name = "helloworld_image",
    base = "@distroless_static",
    entrypoint = ["/dualconduit/helloworld"],
    tars = [":helloworld_tar"],
)

oci_load(
    name = "helloworld_load",
    image = ":helloworld_image",
    repo_tags = ["dualconduit/helloworld:latest"],
)

oci_push(
    name = "helloworld_push",
    image = ":helloworld_image",
    remote_tags = ["latest"],
)
