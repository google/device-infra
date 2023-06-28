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

load("//tools/build_defs/proto/cpp:cc_proto_library.bzl", "cc_proto_library")

DEFAULT_VISIBILITY = [
    "//:deviceinfra_all_pkg",
    "//:deviceinfra_metrics_pkg",
]

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = DEFAULT_VISIBILITY,
)

proto_library(
    name = "error_proto",
    srcs = [
        "error_id.proto",
        "error_type.proto",
        "exception.proto",
        "namespace.proto",
    ],
)

cc_proto_library(
    name = "error_cc_proto",
    visibility = ["//devtools/mobileharness/shared/debuggability/flume/utils:__pkg__"],
    deps = [":error_proto"],
)

java_proto_library(
    name = "error_java_proto",
    deps = [":error_proto"],
)
