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

# Description:
#   Proto definitions for Mobile Harness config service.

load("@grpc-java//:java_grpc_library.bzl", "java_grpc_library")
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
    name = "basic_proto",
    srcs = ["basic.proto"],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:device_proto",
        "@protobuf//:wrappers_proto",
    ],
)

java_proto_library(
    name = "basic_java_proto",
    deps = [":basic_proto"],
)

proto_library(
    name = "device_proto",
    srcs = ["device.proto"],
    deps = [
        ":basic_proto",
        "@protobuf//:any_proto",
    ],
)

java_proto_library(
    name = "device_java_proto",
    deps = [":device_proto"],
)

proto_library(
    name = "lab_proto",
    srcs = ["lab.proto"],
    deps = [
        ":basic_proto",
        "//src/devtools/mobileharness/api/model/proto:lab_proto",
        "@com_google_protobuf//:field_mask_proto",
    ],
)

java_proto_library(
    name = "lab_java_proto",
    deps = [":lab_proto"],
)

proto_library(
    name = "conditioned_device_config_proto",
    srcs = ["conditioned_device_config.proto"],
    deps = [],
)

java_proto_library(
    name = "conditioned_device_config_java_proto",
    deps = [":conditioned_device_config_proto"],
)

proto_library(
    name = "lab_device_proto",
    srcs = ["lab_device.proto"],
    deps = [
        ":device_proto",
        ":lab_proto",
    ],
)

java_proto_library(
    name = "lab_device_java_proto",
    deps = [":lab_device_proto"],
)

proto_library(
    name = "device_config_service_proto",
    srcs = ["device_config_service.proto"],
    deps = [
        "//src/devtools/mobileharness/api/deviceconfig/proto:device_proto",
        "//src/devtools/mobileharness/api/deviceconfig/proto:lab_proto",
        "@protobuf//:field_mask_proto",
    ],
)

java_proto_library(
    name = "device_config_service_java_proto",
    deps = [":device_config_service_proto"],
)

java_grpc_library(
    name = "device_config_service_grpc",
    srcs = [":device_config_service_proto"],
    deps = [":device_config_service_java_proto"],
)
