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

# Description:
#   RPC stubs for talking to Master GRPC services via OnePlatform API.

load("@rules_java//java:defs.bzl", "java_library")

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//:deviceinfra_all_pkg",
        "//:omnilab_fusion",
    ],
)

java_library(
    name = "job_sync",
    srcs = [
        "JobSyncGrpcStub.java",
        "JobSyncGrpcStubModule.java",
    ],
    deps = [
        "//src/devtools/mobileharness/infra/master/rpc/proto:job_sync_service_java_grpc_proto",
        "//src/devtools/mobileharness/infra/master/rpc/proto:job_sync_service_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/rpc/grpc",
        "//src/java/com/google/devtools/common/metrics/stability/rpc/grpc:stub_util",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/master/rpc/stub:annotation",
        "//src/java/com/google/devtools/mobileharness/infra/master/rpc/stub:job_sync",
        "//src/java/com/google/devtools/mobileharness/shared/constant/closeable",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/stub:master_grpc_stub_helper",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/stub:stub_configures",
        "@io_grpc_grpc_java//core",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "lab_info",
    srcs = [
        "LabInfoGrpcStub.java",
        "LabInfoGrpcStubModule.java",
    ],
    deps = [
        "//src/devtools/mobileharness/shared/labinfo/proto:lab_info_service_java_grpc_proto",
        "//src/devtools/mobileharness/shared/labinfo/proto:lab_info_service_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/rpc/grpc",
        "//src/java/com/google/devtools/common/metrics/stability/rpc/grpc:stub_util",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/master/rpc/stub:annotation",
        "//src/java/com/google/devtools/mobileharness/infra/master/rpc/stub:lab_info",
        "//src/java/com/google/devtools/mobileharness/shared/util/base:proto_text_format",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/stub:master_grpc_stub_helper",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/stub:stubs",
        "@io_grpc_grpc_java//core",
        "@maven//:com_google_inject_guice",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "lab_sync",
    srcs = [
        "LabSyncGrpcStub.java",
        "LabSyncGrpcStubModule.java",
    ],
    deps = [
        "//src/devtools/mobileharness/infra/master/rpc/proto:lab_sync_service_java_grpc_proto",
        "//src/devtools/mobileharness/infra/master/rpc/proto:lab_sync_service_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/rpc/grpc",
        "//src/java/com/google/devtools/common/metrics/stability/rpc/grpc:stub_util",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/master/rpc/stub:annotation",
        "//src/java/com/google/devtools/mobileharness/infra/master/rpc/stub:lab_sync",
        "//src/java/com/google/devtools/mobileharness/shared/constant/closeable",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/stub:master_grpc_stub_helper",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/stub:stub_configures",
        "@io_grpc_grpc_java//core",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@maven//:javax_inject_jsr330_api",
    ],
)
