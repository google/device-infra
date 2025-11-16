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

# Android Instrumentation Test Result proto.

load("@protobuf//bazel:java_lite_proto_library.bzl", "java_lite_proto_library")
load("@protobuf//bazel:java_proto_library.bzl", "java_proto_library")
load("@protobuf//bazel:proto_library.bzl", "proto_library")

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = ["//visibility:public"],
)

proto_library(
    name = "error_proto",
    srcs = ["error.proto"],
)

java_proto_library(
    name = "error_java_proto",
    deps = [":error_proto"],
)

java_lite_proto_library(
    name = "error_java_proto_lite",
    deps = [":error_proto"],
)

proto_library(
    name = "test_case_proto",
    srcs = ["test_case.proto"],
    deps = ["@protobuf//:timestamp_proto"],
)

java_proto_library(
    name = "test_case_java_proto",
    deps = [":test_case_proto"],
)

java_lite_proto_library(
    name = "test_case_java_proto_lite",
    deps = [":test_case_proto"],
)

proto_library(
    name = "test_result_proto",
    srcs = ["test_result.proto"],
    deps = [
        ":error_proto",
        ":test_case_proto",
        ":test_status_proto",
    ],
)

java_proto_library(
    name = "test_result_java_proto",
    deps = [":test_result_proto"],
)

java_lite_proto_library(
    name = "test_result_java_proto_lite",
    deps = [":test_result_proto"],
)

proto_library(
    name = "test_status_proto",
    srcs = ["test_status.proto"],
)

java_proto_library(
    name = "test_status_java_proto",
    deps = [":test_status_proto"],
)

java_lite_proto_library(
    name = "test_status_java_proto_lite",
    deps = [":test_status_proto"],
)

proto_library(
    name = "test_suite_result_proto",
    srcs = ["test_suite_result.proto"],
    deps = [
        ":test_result_proto",
        ":test_status_proto",
    ],
)

java_proto_library(
    name = "test_suite_result_java_proto",
    deps = [":test_suite_result_proto"],
)

java_lite_proto_library(
    name = "test_suite_result_java_proto_lite",
    deps = [":test_suite_result_proto"],
)
