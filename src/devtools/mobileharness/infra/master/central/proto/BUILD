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
#   Protos for Mobile Harness Master V5 Central.
load("@protobuf//bazel:java_proto_library.bzl", "java_proto_library")
load("@protobuf//bazel:proto_library.bzl", "proto_library")

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//:deviceinfra_all_pkg",
    ],
)

proto_library(
    name = "allocation_task_proto",
    srcs = ["allocation_task.proto"],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:device_proto",
    ],
)

java_proto_library(
    name = "allocation_task_java_proto",
    deps = [":allocation_task_proto"],
)

proto_library(
    name = "device_proto",
    srcs = ["device.proto"],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:device_proto",
        "//src/devtools/mobileharness/api/model/proto:test_proto",
    ],
)

java_proto_library(
    name = "device_java_proto",
    deps = [":device_proto"],
)

proto_library(
    name = "job_proto",
    srcs = ["job.proto"],
    deps = [
        ":quota_proto",
        ":test_proto",
        "//src/devtools/mobileharness/api/model/proto:device_proto",
        "//src/devtools/mobileharness/api/model/proto:job_proto",
    ],
)

java_proto_library(
    name = "job_java_proto",
    deps = [":job_proto"],
)

proto_library(
    name = "lab_proto",
    srcs = ["lab.proto"],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:lab_proto",
    ],
)

cc_proto_library(
    name = "lab_cc_proto",
    deps = [":lab_proto"],
)

java_proto_library(
    name = "lab_java_proto",
    deps = [":lab_proto"],
)

proto_library(
    name = "quota_proto",
    srcs = ["quota.proto"],
)

java_proto_library(
    name = "quota_java_proto",
    deps = [":quota_proto"],
)

proto_library(
    name = "test_proto",
    srcs = ["test.proto"],
    deps = [
        ":quota_proto",
        "//src/devtools/mobileharness/api/model/proto:device_proto",
        "//src/devtools/mobileharness/api/model/proto:test_proto",
    ],
)

java_proto_library(
    name = "test_java_proto",
    deps = [":test_proto"],
)
