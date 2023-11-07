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
    ],
)

proto_library(
    name = "debug_service_proto",
    srcs = ["debug_service.proto"],
)

java_proto_library(
    name = "debug_service_java_proto",
    deps = [":debug_service_proto"],
)

proto_library(
    name = "device_allocation_service_proto",
    srcs = ["device_allocation_service.proto"],
    deps = [
        "//src/devtools/deviceinfra/api/deviceinfo/proto:device_locator_proto",
        "//src/devtools/deviceinfra/api/deviceinfo/proto:device_requirement_proto",
        "//src/devtools/mobileharness/api/model/proto:test_proto",
        "//src/devtools/mobileharness/api/query/proto:device_query_proto",
        "@com_google_protobuf//:duration_proto",
    ],
)

java_proto_library(
    name = "device_allocation_service_java_proto",
    deps = [":device_allocation_service_proto"],
)

proto_library(
    name = "device_info_service_proto",
    srcs = ["device_info_service.proto"],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:lab_proto",
        "//src/devtools/mobileharness/api/query/proto:device_query_proto",
    ],
)

java_proto_library(
    name = "device_info_service_java_proto",
    deps = [":device_info_service_proto"],
)

java_grpc_library(
    name = "device_info_service_java_grpc_proto",
    srcs = [":device_info_service_proto"],
    deps = [":device_info_service_java_proto"],
)

proto_library(
    name = "job_sync_service_proto",
    srcs = ["job_sync_service.proto"],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:device_proto",
        "//src/devtools/mobileharness/api/model/proto:error_proto",
        "//src/devtools/mobileharness/api/model/proto:job_proto",
        "//src/devtools/mobileharness/api/model/proto:lab_proto",
        "//src/devtools/mobileharness/api/model/proto:test_proto",
        "//src/devtools/mobileharness/shared/version/proto:version_proto",
        "@com_google_protobuf//:duration_proto",
    ],
)

java_proto_library(
    name = "job_sync_service_java_proto",
    deps = [":job_sync_service_proto"],
)

java_grpc_library(
    name = "job_sync_service_java_grpc_proto",
    srcs = [":job_sync_service_proto"],
    deps = [":job_sync_service_java_proto"],
)

proto_library(
    name = "lab_info_service_proto",
    srcs = ["lab_info_service.proto"],
    deps = [
        "//src/devtools/mobileharness/api/query/proto:lab_query_proto",
    ],
)

java_proto_library(
    name = "lab_info_service_java_proto",
    deps = [":lab_info_service_proto"],
)

java_grpc_library(
    name = "lab_info_service_java_grpc_proto",
    srcs = [":lab_info_service_proto"],
    deps = [":lab_info_service_java_proto"],
)

proto_library(
    name = "lab_sync_service_proto",
    srcs = ["lab_sync_service.proto"],
    deps = [
        "//src/devtools/common/metrics/stability/model/proto:error_proto",
        "//src/devtools/mobileharness/api/model/proto:device_proto",
        "//src/devtools/mobileharness/api/model/proto:lab_proto",
        "//src/devtools/mobileharness/shared/version/proto:version_proto",
    ],
)

java_proto_library(
    name = "lab_sync_service_java_proto",
    deps = [":lab_sync_service_proto"],
)

java_grpc_library(
    name = "lab_sync_service_java_grpc_proto",
    srcs = [":lab_sync_service_proto"],
    deps = [":lab_sync_service_java_proto"],
)
