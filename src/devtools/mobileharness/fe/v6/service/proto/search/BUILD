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

load("@grpc-java//:java_grpc_library.bzl", "java_grpc_library")
load("@protobuf//bazel:java_proto_library.bzl", "java_proto_library")
load("@protobuf//bazel:proto_library.bzl", "proto_library")

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//google/corp/devtools/mobileharness/fe:__subpackages__",
        "//src/devtools/mobileharness/fe/v6:visibility",
    ],
)

# --- Shared types (Cell, Column, Row, NavTarget, Severity, TextSegment) ---

proto_library(
    name = "search_common_proto",
    srcs = ["search_common.proto"],
)

java_proto_library(
    name = "search_common_java_proto",
    deps = [":search_common_proto"],
)

# --- Device/Host (fleet) search types ---

proto_library(
    name = "search_fleet_proto",
    srcs = ["search_fleet.proto"],
    deps = [":search_common_proto"],
)

java_proto_library(
    name = "search_fleet_java_proto",
    deps = [":search_fleet_proto"],
)

# --- Home page global summary types ---

proto_library(
    name = "search_summary_proto",
    srcs = ["search_summary.proto"],
    deps = [":search_fleet_proto"],
)

java_proto_library(
    name = "search_summary_java_proto",
    deps = [":search_summary_proto"],
)

# --- Test/Job/Session (TJS) search types ---

proto_library(
    name = "search_tjs_proto",
    srcs = ["search_tjs.proto"],
    deps = [
        ":search_common_proto",
        "@protobuf//:timestamp_proto",
    ],
)

java_proto_library(
    name = "search_tjs_java_proto",
    deps = [":search_tjs_proto"],
)

# --- SearchService definition ---

proto_library(
    name = "search_service_proto",
    srcs = ["search_service.proto"],
    deps = [
        ":search_fleet_proto",
        ":search_summary_proto",
        ":search_tjs_proto",
        "@googleapis//google/api:annotations_proto",
    ],
)

java_proto_library(
    name = "search_service_java_proto",
    deps = [":search_service_proto"],
)

java_grpc_library(
    name = "search_service_java_grpc",
    srcs = [":search_service_proto"],
    deps = [":search_service_java_proto"],
)
