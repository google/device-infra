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
    name = "dialer",
    srcs = ["dialer.go"],
    goarch = "amd64",
    goos = "linux",
    pure = "on",
    deps = [
        "//src/devtools/mobileharness/shared/util/comm/dualconduit/proto:dual_conduit_go_proto",
        "//src/devtools/mobileharness/shared/util/comm/dualconduit/proto:dual_conduit_service_go_grpc",
        "@org_golang_google_grpc//:grpc",
        "@org_golang_google_grpc//codes",
        "@org_golang_google_grpc//reflection",
        "@org_golang_google_grpc//status",
    ],
)

# ==========================================
# Build and push Docker image.
# ==========================================
pkg_tar(
    name = "dialer_tar",
    srcs = [":dialer"],
    package_dir = "/dualconduit",
)

oci_image(
    name = "dialer_image",
    base = "@distroless_static",
    entrypoint = ["/dualconduit/dialer"],
    tars = [":dialer_tar"],
)

oci_load(
    name = "dialer_load",
    image = ":dialer_image",
    repo_tags = ["dualconduit/dialer:latest"],
)

oci_push(
    name = "dialer_push",
    image = ":dialer_image",
    remote_tags = ["latest"],
)
