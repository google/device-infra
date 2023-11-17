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
#   RPC stubs wrappers of the Master services.
package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//:deviceinfra_all_pkg",
    ],
)

java_library(
    name = "annotation",
    srcs = ["MasterStubAnnotation.java"],
    deps = [
        "@maven//:com_google_inject_guice",
    ],
)

java_library(
    name = "debug",
    srcs = ["DebugStub.java"],
    deps = [
        "//src/devtools/mobileharness/infra/master/rpc/proto:debug_service_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/rpc:exception",
    ],
)

java_library(
    name = "device_allocation",
    srcs = ["DeviceAllocationStub.java"],
    deps = [
        "//src/devtools/mobileharness/infra/master/rpc/proto:device_allocation_service_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/rpc:exception",
        "@maven//:com_google_errorprone_error_prone_annotations",
    ],
)

java_library(
    name = "device_info",
    srcs = ["DeviceInfoStub.java"],
    deps = [
        "//src/devtools/mobileharness/infra/master/rpc/proto:device_info_service_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/rpc:exception",
    ],
)

java_library(
    name = "job_sync",
    srcs = ["JobSyncStub.java"],
    deps = [
        "//src/devtools/mobileharness/infra/master/rpc/proto:job_sync_service_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/rpc:exception",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "lab_info",
    srcs = ["LabInfoStub.java"],
    deps = [
        "//src/devtools/mobileharness/infra/master/rpc/proto:lab_info_service_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/rpc/grpc",
    ],
)

java_library(
    name = "lab_sync",
    srcs = ["LabSyncStub.java"],
    deps = [
        "//src/devtools/mobileharness/infra/master/rpc/proto:lab_sync_service_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/rpc:exception",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)
