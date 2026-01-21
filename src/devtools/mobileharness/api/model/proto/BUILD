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
load("@protobuf//bazel:py_proto_library.bzl", "py_proto_library")

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//:deviceinfra_api_users",
        "//:omnilab_fusion",
    ],
)

proto_library(
    name = "device_proto",
    srcs = ["device.proto"],
    deps = [
        ":lab_proto",
        ":test_proto",
        "//src/devtools/common/metrics/stability/model/proto:error_proto",
        "@protobuf//:timestamp_proto",
    ],
)

py_proto_library(
    name = "device_py_pb2",
    deps = [":device_proto"],
)

java_proto_library(
    name = "device_java_proto",
    deps = [":device_proto"],
)

proto_library(
    name = "job_proto",
    srcs = ["job.proto"],
    deps = ["@protobuf//:timestamp_proto"],
)

java_proto_library(
    name = "job_java_proto",
    deps = [":job_proto"],
)

kt_jvm_proto_library(
    name = "job_kt_proto",
    visibility = [
        "//java/com/google/devtools/omnilab/device/lab/specs:__pkg__",
        "//javatests/com/google/devtools/omnilab/device/lab/specs:__pkg__",
    ],
    deps = [":job_proto"],
)

proto_library(
    name = "lab_proto",
    srcs = ["lab.proto"],
    deps = [
        ":universe_proto",
    ],
)

java_proto_library(
    name = "lab_java_proto",
    deps = [":lab_proto"],
)

py_proto_library(
    name = "lab_py_proto",
    deps = [":lab_proto"],
)

proto_library(
    name = "test_proto",
    srcs = ["test.proto"],
    deps = [":job_proto"],
)

java_proto_library(
    name = "test_java_proto",
    deps = [":test_proto"],
)

proto_library(
    name = "universe_proto",
    srcs = ["universe.proto"],
)

java_proto_library(
    name = "universe_java_proto",
    deps = [":universe_proto"],
)

py_proto_library(
    name = "universe_py_proto",
    deps = [":universe_proto"],
)
