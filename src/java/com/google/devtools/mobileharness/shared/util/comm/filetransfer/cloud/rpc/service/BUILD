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

load("@rules_java//java:defs.bzl", "java_library")

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/lab:__subpackages__",
    ],
)

java_library(
    name = "cloud_file_transfer_service_grpc_impl",
    srcs = ["CloudFileTransferServiceGrpcImpl.java"],
    deps = [
        ":cloud_file_transfer_service_impl",
        "//src/devtools/mobileharness/shared/util/comm/filetransfer/cloud/proto:cloud_file_transfer_grpc_proto",
        "//src/devtools/mobileharness/shared/util/comm/filetransfer/cloud/proto:cloud_file_transfer_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/rpc/grpc:service_util",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/filetransfer/common:file_handlers",
        "@com_google_protobuf//:protobuf_java",
        "@io_grpc_grpc_java//stub",
        "@maven//:com_google_errorprone_error_prone_annotations",
    ],
)

java_library(
    name = "cloud_file_transfer_service_impl",
    srcs = ["CloudFileTransferServiceImpl.java"],
    deps = [
        "//src/devtools/mobileharness/shared/util/comm/filetransfer/cloud/proto:cloud_file_transfer_java_proto",
        "//src/devtools/mobileharness/shared/util/comm/filetransfer/common/proto:file_info_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/base",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/filetransfer/cloud/common:file_transfer_constant",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/filetransfer/common:file_handlers",
        "//src/java/com/google/devtools/mobileharness/shared/util/command",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:thread_pools",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/remote:gcs_file_manager",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/remote:gcs_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@com_google_protobuf//:protobuf_java",
        "@com_google_protobuf_protobuf_java",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)
