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

# Mobile Harness shared state proto files.

load("@protobuf//bazel:java_proto_library.bzl", "java_proto_library")
load("@protobuf//bazel:proto_library.bzl", "proto_library")

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//:deviceinfra_all_pkg",
    ],
)

proto_library(
    name = "device_info_collector_decorator_state_proto",
    srcs = ["decorator/device_info_collector_decorator_state.proto"],
)

java_proto_library(
    name = "device_info_collector_decorator_state_java_proto",
    deps = [":device_info_collector_decorator_state_proto"],
)

proto_library(
    name = "android_ats_dynamic_config_pusher_decorator_state_proto",
    srcs = ["decorator/android_ats_dynamic_config_pusher_decorator_state.proto"],
)

java_proto_library(
    name = "android_ats_dynamic_config_pusher_decorator_state_java_proto",
    deps = [":android_ats_dynamic_config_pusher_decorator_state_proto"],
)
