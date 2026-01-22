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
        "//java/com/google/devtools/omnilab/costmanagement:__subpackages__",
        "//javatests/com/google/devtools/omnilab/costmanagement:__subpackages__",
    ],
)

proto_library(
    name = "result_proto",
    srcs = ["result.proto"],
    deps = [
        "//src/devtools/common/metrics/stability/model/proto:error_proto",
        "//src/devtools/mobileharness/api/model/proto:job_proto",
        "//src/devtools/mobileharness/api/model/proto:test_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:common_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_proto",
    ],
)

java_proto_library(
    name = "result_java_proto",
    deps = [":result_proto"],
)

proto_library(
    name = "slg_proto",
    srcs = ["slg.proto"],
    deps = [
        ":result_proto",
        "//src/devtools/common/metrics/stability/model/proto:error_proto",
        "//src/devtools/mobileharness/api/model/proto:device_proto",
        "//src/devtools/mobileharness/api/model/proto:job_proto",
        "//src/devtools/mobileharness/api/model/proto:test_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto/spec:job_spec_proto",
    ],
)

java_proto_library(
    name = "slg_java_proto",
    deps = [":slg_proto"],
)
