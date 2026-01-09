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
        "//google/corp/devtools/mobileharness/fe:__subpackages__",
        "//src/devtools/mobileharness/fe/v6:visibility",
    ],
)

proto_library(
    name = "config_resources_proto",
    srcs = ["config_resources.proto"],
)

java_proto_library(
    name = "config_resources_java_proto",
    deps = [":config_resources_proto"],
)

proto_library(
    name = "config_service_proto",
    srcs = ["config_service.proto"],
    deps = [
        ":config_resources_proto",
        "@googleapis//google/api:annotations_proto",
    ],
)

java_proto_library(
    name = "config_service_java_proto",
    deps = [":config_service_proto"],
)

java_grpc_library(
    name = "config_service_java_grpc",
    srcs = [":config_service_proto"],
    deps = [":config_service_java_proto"],
)

rpc_endpoint_interface(
    name = "config_endpoint",
    default_provider = "//java/com/google/devtools/mobileharness/fe/v6/config/service:tin_sut_component",
    metadata = [
    ],
    rpc_services = [
        RpcService(
            name = "mobileharness.fe.v6.config.ConfigService",
            proto_library = ":config_service_proto",
        ),
    ],
    visibility = ["//visibility:public"],
)
