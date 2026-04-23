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

load("@grpc//bazel:python_rules.bzl", "py_grpc_library")
load("@grpc-java//:java_grpc_library.bzl", "java_grpc_library")
load("@protobuf//bazel:java_proto_library.bzl", "java_proto_library")
load("@protobuf//bazel:proto_library.bzl", "proto_library")
load("@protobuf//bazel:py_proto_library.bzl", "py_proto_library")

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//:__subpackages__",
    ],
)

# exports_files(
#     ["health.proto"],
#     visibility = [
#         "//third_party/py/multitest_transport:__subpackages__",
#     ],
# )

proto_library(
    name = "health_proto",
    srcs = ["health.proto"],
    deps = [
        "@protobuf//:duration_proto",
    ],
)

java_proto_library(
    name = "health_java_proto",
    deps = [":health_proto"],
)

java_grpc_library(
    name = "health_java_grpc",
    srcs = [":health_proto"],
    deps = [":health_java_proto"],
)

py_proto_library(
    name = "health_service_py_proto",
    visibility = [
        "//third_party/py/multitest_transport:__subpackages__",
    ],
    deps = [":health_proto"],
)

py_grpc_library(
    name = "health_service_py_grpc",
    srcs = [":health_proto"],
    tags = ["deviceinfra_disable_in_kokoro"],
    visibility = [
        "//third_party/py/multitest_transport:__subpackages__",
    ],
    deps = [":health_service_py_proto"],
)
