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

load("@io_grpc_grpc_java//:java_grpc_library.bzl", "java_grpc_library")

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//:deviceinfra_all_pkg",
        "//:omnilab_fusion",
    ],
)

proto_library(
    name = "file_proto",
    srcs = ["file.proto"],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:job_proto",
        "//src/devtools/mobileharness/api/model/proto:test_proto",
    ],
)

java_proto_library(
    name = "file_java_proto",
    deps = [":file_proto"],
)

proto_library(
    name = "prepare_test_service_proto",
    srcs = ["prepare_test_service.proto"],
    deps = [
        "//src/devtools/common/metrics/stability/model/proto:error_proto",
        "//src/devtools/mobileharness/api/model/proto:device_proto",
        "//src/devtools/mobileharness/api/model/proto:error_proto",
        "//src/devtools/mobileharness/api/model/proto:job_proto",
        "//src/devtools/mobileharness/infra/container/proto:mode_setting_proto",
        "//src/devtools/mobileharness/infra/container/proto:sandbox_setting_proto",
        "//src/devtools/mobileharness/infra/container/proto:test_engine_proto",
        "//src/devtools/mobileharness/shared/trace/proto:span_proto",
        "//src/devtools/mobileharness/shared/version/proto:version_proto",
        "@com_google_protobuf//:duration_proto",
        "@com_google_protobuf//:timestamp_proto",
    ],
)

java_proto_library(
    name = "prepare_test_service_java_proto",
    deps = [":prepare_test_service_proto"],
)

java_grpc_library(
    name = "prepare_test_service_grpc",
    srcs = [":prepare_test_service_proto"],
    deps = [":prepare_test_service_java_proto"],
)
