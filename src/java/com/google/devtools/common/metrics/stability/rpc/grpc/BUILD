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

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//:deviceinfra_pkg",
    ],
)

java_library(
    name = "grpc",
    srcs = [
        "GrpcExceptionUtil.java",
        "GrpcExceptionWithErrorId.java",
    ],
    deps = [
        "//src/devtools/common/metrics/stability/rpc/proto:rpc_error_java_proto",
        "//src/devtools/common/metrics/stability/rpc/proto:rpc_error_payload_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/converter",
        "//src/java/com/google/devtools/common/metrics/stability/converter:deserialized_exception",
        "//src/java/com/google/devtools/common/metrics/stability/model:error_id",
        "//src/java/com/google/devtools/common/metrics/stability/rpc:exception",
        "//src/java/com/google/devtools/common/metrics/stability/rpc:rpc_error_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@com_google_protobuf//:protobuf_java",
        "@io_grpc_grpc_java//core",
        "@io_grpc_grpc_java//protobuf",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "stub_util",
    srcs = ["GrpcStubUtil.java"],
    deps = [
        ":grpc",
        "//src/java/com/google/devtools/common/metrics/stability/model:error_id",
        "@io_grpc_grpc_java//core",
        "@maven//:com_google_errorprone_error_prone_annotations",
    ],
)

java_library(
    name = "service_util",
    srcs = ["GrpcServiceUtil.java"],
    deps = [
        ":grpc",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@io_grpc_grpc_java//core",
        "@io_grpc_grpc_java//stub",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)
