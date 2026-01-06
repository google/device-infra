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

proto_library(
    name = "device_resources_proto",
    srcs = ["device_resources.proto"],
    deps = [
        "@protobuf//:timestamp_proto",
    ],
)

java_proto_library(
    name = "device_resources_java_proto",
    deps = [":device_resources_proto"],
)

proto_library(
    name = "device_stats_proto",
    srcs = ["device_stats.proto"],
)

java_proto_library(
    name = "device_stats_java_proto",
    deps = [":device_stats_proto"],
)

proto_library(
    name = "device_service_proto",
    srcs = ["device_service.proto"],
    deps = [
        ":device_resources_proto",
        ":device_stats_proto",
        "@googleapis//google/api:annotations_proto",
        "@googleapis//google/type:date_proto",
        "@protobuf//:timestamp_proto",
    ],
)

java_proto_library(
    name = "device_service_java_proto",
    deps = [":device_service_proto"],
)

java_grpc_library(
    name = "device_service_java_grpc",
    srcs = [":device_service_proto"],
    deps = [":device_service_java_proto"],
)
