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

load("@protobuf//bazel:java_proto_library.bzl", "java_proto_library")
load("@protobuf//bazel:proto_library.bzl", "proto_library")

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//:deviceinfra_all_pkg",
        "//:omnilab_fusion",
    ],
)

proto_library(
    name = "file_handlers_test_proto",
    srcs = ["file_handlers_test.proto"],
)

java_proto_library(
    name = "file_handlers_test_java_proto",
    deps = [":file_handlers_test_proto"],
)

proto_library(
    name = "file_info_proto",
    srcs = ["file_info.proto"],
    deps = [
    ],
)

java_proto_library(
    name = "file_info_java_proto",
    deps = [":file_info_proto"],
)

proto_library(
    name = "tagged_file_metadata_proto",
    srcs = ["tagged_file_metadata.proto"],
)

java_proto_library(
    name = "tagged_file_metadata_java_proto",
    deps = [":tagged_file_metadata_proto"],
)
