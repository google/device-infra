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

load("@io_bazel_rules_go//go:def.bzl", "go_library", "go_test")

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = ["//visibility:public"],
)

go_library(
    name = "uploader",
    srcs = [
        "dir.go",
        "file.go",
        "uploader.go",
        "zip.go",
    ],
    importpath = "github.com/google/device-infra/src/devtools/rbe/casuploader/uploader",
    deps = [
        "//src/devtools/rbe/casuploader/chunkerutil",
        "//src/devtools/rbe/casuploader/metrics",
        "@com_github_bazelbuild_remote_apis//build/bazel/remote/execution/v2:remote_execution_go_proto",
        "@com_github_bazelbuild_remote_apis_sdks//go/pkg/client",
        "@com_github_bazelbuild_remote_apis_sdks//go/pkg/command",
        "@com_github_bazelbuild_remote_apis_sdks//go/pkg/digest",
        "@com_github_bazelbuild_remote_apis_sdks//go/pkg/filemetadata",
        "@com_github_bazelbuild_remote_apis_sdks//go/pkg/uploadinfo",
        "@com_github_golang_glog//:glog",
        "@com_github_google_uuid//:uuid",
        "@com_github_pkg_xattr//:xattr",
        "@org_golang_google_protobuf//proto",
    ],
)

go_test(
    name = "uploader_test",
    srcs = ["zip_test.go"],
    data = ["test_data/partial_zip.zip"],
    embed = [":uploader"],
)
