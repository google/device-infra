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

load("@com_github_grpc_grpc//bazel:python_rules.bzl", "py_grpc_library")
load("@com_google_protobuf//:protobuf.bzl", "py_proto_library")
load("@io_grpc_grpc_java//:java_grpc_library.bzl", "java_grpc_library")

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//:__subpackages__",
    ],
)

proto_library(
    name = "health_proto",
    srcs = ["health.proto"],
    deps = [
        "@com_google_protobuf//:duration_proto",
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
    srcs = ["health.proto"],
    visibility = [
        "//third_party/py/multitest_transport:__subpackages__",
    ],
    deps = [
        "@com_github_protocolbuffers_protobuf//:well_known_types_py_pb2",
    ],
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
