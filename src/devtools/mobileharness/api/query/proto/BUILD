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

load("@com_google_protobuf//:protobuf.bzl", "py_proto_library")
load("//third_party/protobuf/bazel:java_proto_library.bzl", "java_proto_library")
load("//third_party/protobuf/bazel:proto_library.bzl", "proto_library")

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//:deviceinfra_all_pkg",
        "//:omnilab_fusion",
        "//wireless/android/pixel/testtools/djar:__subpackages__",
    ],
)

proto_library(
    name = "device_query_proto",
    srcs = ["device_query.proto"],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:device_proto",
    ],
)

java_proto_library(
    name = "device_query_java_proto",
    deps = [":device_query_proto"],
)

proto_library(
    name = "filter_proto",
    srcs = ["filter.proto"],
)

java_proto_library(
    name = "filter_java_proto",
    deps = [":filter_proto"],
)

py_proto_library(
    name = "filter_py_proto",
    srcs = [
        "filter.proto",
    ],
    deps = [
        "@com_github_protocolbuffers_protobuf//:well_known_types_py_pb2",
    ],
)

proto_library(
    name = "lab_query_proto",
    srcs = ["lab_query.proto"],
    deps = [
        ":filter_proto",
        "//src/devtools/mobileharness/api/model/proto:device_proto",
        "//src/devtools/mobileharness/api/model/proto:lab_proto",
        "@com_google_protobuf//:field_mask_proto",
        "@com_google_protobuf//:timestamp_proto",
    ],
)

java_proto_library(
    name = "lab_query_java_proto",
    deps = [":lab_query_proto"],
)

py_proto_library(
    name = "lab_query_py_proto",
    srcs = [
        "lab_query.proto",
    ],
    deps = [
        ":filter_py_proto",
        "//src/devtools/mobileharness/api/model/proto:device_py_pb2",
        "//src/devtools/mobileharness/api/model/proto:lab_py_proto",
        "@com_github_protocolbuffers_protobuf//:well_known_types_py_pb2",
    ],
)

proto_library(
    name = "lab_record_proto",
    srcs = ["lab_record.proto"],
    deps = [
        ":lab_query_proto",
        "@com_google_protobuf//:timestamp_proto",
    ],
)

java_proto_library(
    name = "lab_record_java_proto",
    deps = [":lab_record_proto"],
)

py_proto_library(
    name = "lab_record_py_proto",
    srcs = [
        "lab_record.proto",
    ],
    deps = [
        ":filter_py_proto",
        ":lab_query_py_proto",
        "//src/devtools/mobileharness/api/model/proto:device_py_pb2",
        "//src/devtools/mobileharness/api/model/proto:lab_py_proto",
        "@com_github_protocolbuffers_protobuf//:well_known_types_py_pb2",
    ],
)
