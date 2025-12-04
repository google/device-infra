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
        "//:deviceinfra_all_pkg",
        "//:omnilab_fusion",
    ],
)

proto_library(
    name = "device_ops_serv_proto",
    srcs = ["device_ops_serv.proto"],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:device_proto",
    ],
)

java_proto_library(
    name = "device_ops_serv_java_proto",
    deps = [":device_ops_serv_proto"],
)

java_grpc_library(
    name = "device_ops_serv_grpc",
    srcs = [":device_ops_serv_proto"],
    deps = [":device_ops_serv_java_proto"],
)

proto_library(
    name = "exec_test_serv_proto",
    srcs = ["exec_test_serv.proto"],
    deps = [
        ":stat_proto",
        "//src/devtools/common/metrics/stability/model/proto:error_proto",
        "//src/devtools/mobileharness/api/model/proto:device_proto",
        "//src/devtools/mobileharness/api/model/proto:job_proto",
        "//src/devtools/mobileharness/api/model/proto:test_proto",
        "//src/devtools/mobileharness/shared/trace/proto:span_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:common_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto/spec:job_spec_proto",
    ],
)

java_proto_library(
    name = "exec_test_serv_java_proto",
    deps = [":exec_test_serv_proto"],
)

java_grpc_library(
    name = "exec_test_serv_grpc",
    srcs = [":exec_test_serv_proto"],
    deps = [":exec_test_serv_java_proto"],
)

exports_files(["stat.proto"])

proto_library(
    name = "stat_proto",
    srcs = ["stat.proto"],
    deps = [
        "//src/devtools/common/metrics/stability/model/proto:error_proto",
        "//src/devtools/mobileharness/api/model/proto:device_proto",
        "//src/devtools/mobileharness/api/model/proto:test_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:common_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_proto",
    ],
)

java_proto_library(
    name = "stat_java_proto",
    deps = [":stat_proto"],
)

proto_library(
    name = "stat_serv_proto",
    srcs = ["stat_serv.proto"],
    deps = [
        ":stat_proto",
    ],
)

java_proto_library(
    name = "stat_serv_java_proto",
    deps = [":stat_serv_proto"],
)

java_grpc_library(
    name = "stat_serv_grpc",
    srcs = [":stat_serv_proto"],
    deps = [":stat_serv_java_proto"],
)
