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
        "//java/com/google/devtools/mobileharness:__subpackages__",
        "//javatests/com/google/devtools/mobileharness:__subpackages__",
        "//src/java/com/google/devtools/mobileharness:__subpackages__",
    ],
)

java_library(
    name = "cloud_file_transfer_client",
    srcs = [
        "CloudFileTransferClient.java",
        "FileOperationStatus.java",
    ],
    deps = [
        "//src/devtools/mobileharness/shared/util/comm/filetransfer/cloud/proto:cloud_file_transfer_java_proto",
        "//src/devtools/mobileharness/shared/util/comm/filetransfer/common/proto:file_info_java_proto",
        "//src/devtools/mobileharness/shared/util/comm/filetransfer/common/proto:tagged_file_metadata_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/rpc:exception",
        "//src/java/com/google/devtools/deviceinfra/shared/util/file/remote/constant:remote_file_type",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/shared/util/base",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/filetransfer/cloud/common:file_transfer_constant",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/filetransfer/cloud/rpc/stub:cloud_file_transfer_stub",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/filetransfer/common/factory:file_transfer_parameters",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/filetransfer/common/watcher:file_transfer_event",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/filetransfer/common/watcher:watchable_file_transfer_client",
        "//src/java/com/google/devtools/mobileharness/shared/util/command",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:thread_pools",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/remote:dataclass",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/remote:gcs_file_manager",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/remote:gcs_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:sleeper",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:error_code",
        "@com_google_protobuf//:protobuf_java",
        "@com_google_protobuf_protobuf_java",
        "@io_grpc_grpc_java//core",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)
