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

load("//tools/build_defs/go:go_library.bzl", "go_library")
load("//tools/build_defs/go:go_test.bzl", "go_test")

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = ["//visibility:public"],
)

go_library(
    name = "chunkstore",
    srcs = [
        "index.go",
        "storage.go",
    ],
    visibility = ["//src/devtools/rbe/casviewer:__subpackages__"],
    deps = [
        "//src/devtools/rbe/casuploader/chunker",
        "@com_github_hanwen_go_fuse_v2//fuse",
    ],
)

go_test(
    name = "chunkstore_test",
    srcs = [
        "index_test.go",
        "storage_test.go",
        "test_helpers_test.go",
    ],
    embed = [":chunkstore"],
    deps = [
        "//src/devtools/rbe/casuploader/chunker",
        "@com_github_hanwen_go_fuse_v2//fuse",
    ],
)
