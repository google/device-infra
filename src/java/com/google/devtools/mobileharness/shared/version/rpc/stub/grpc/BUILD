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
#   RPC stubs for talking to version service.

load("@rules_java//java:defs.bzl", "java_library")

package(default_applicable_licenses = ["//:license"])

java_library(
    name = "grpc",
    srcs = [
        "VersionGrpcStub.java",
    ],
    visibility = [
        "//:deviceinfra_all_pkg",
        "//:omnilab_fusion",
    ],
    deps = [
        "//src/devtools/mobileharness/shared/version/proto:version_service_grpc",
        "//src/devtools/mobileharness/shared/version/proto:version_service_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/rpc/grpc",
        "//src/java/com/google/devtools/common/metrics/stability/rpc/grpc:stub_util",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/stub:stub_configures",
        "//src/java/com/google/devtools/mobileharness/shared/version/rpc/stub",
        "@io_grpc_grpc_java//core",
    ],
)
