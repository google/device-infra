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

load("//third_party/protobuf/build_defs:kt_jvm_proto_library.bzl", "kt_jvm_proto_library")

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
        "@com_google_protobuf//:wrappers_proto",
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
        "@com_google_protobuf//:any_proto",
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
    ],
)

java_proto_library(
    name = "lab_java_proto",
    deps = [":lab_proto"],
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
