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
    name = "mountutil",
    srcs = ["mountutil.go"],
    importpath = "github.com/google/device-infra/src/devtools/rbe/casviewer/mountutil",
    deps = ["//src/devtools/rbe/casuploader/chunkerutil"],
)

go_test(
    name = "mountutil_test",
    srcs = ["mountutil_test.go"],
    embed = [":mountutil"],
    deps = ["//src/devtools/rbe/casuploader/chunkerutil"],
)
