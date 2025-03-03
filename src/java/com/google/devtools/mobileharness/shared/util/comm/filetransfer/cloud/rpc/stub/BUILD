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
        "//:__subpackages__",
        "//java/com/google/devtools/mobileharness:__subpackages__",
        "//javatests/com/google/devtools/mobileharness:__subpackages__",
    ],
)

java_library(
    name = "cloud_file_transfer_stub",
    srcs = [
        "CloudFileTransferStubInterface.java",
        "CloudFileTransferStubUtils.java",
        "RpcExceptionWrapper.java",
    ],
    deps = [
        "//src/devtools/mobileharness/shared/util/comm/filetransfer/cloud/proto:cloud_file_transfer_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/rpc:exception",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "@maven//:com_google_errorprone_error_prone_annotations",
    ],
)

java_library(
    name = "cloud_file_transfer_grpc_stub",
    srcs = ["CloudFileTransferGrpcStub.java"],
    deps = [
        ":cloud_file_transfer_stub",
        "//src/devtools/mobileharness/shared/util/comm/filetransfer/cloud/proto:cloud_file_transfer_grpc_proto",
        "//src/devtools/mobileharness/shared/util/comm/filetransfer/cloud/proto:cloud_file_transfer_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/rpc/grpc",
        "//src/java/com/google/devtools/common/metrics/stability/rpc/grpc:stub_util",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/stub:stub_configures",
        "@io_grpc_grpc_java//core",
    ],
)
