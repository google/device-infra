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
        "//src/javatests/com/google/devtools/mobileharness/infra/client/api/mode/ats:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/lab:__subpackages__",
    ],
)

java_library(
    name = "annotations",
    srcs = ["Annotations.java"],
    deps = ["@maven//:javax_inject_jsr330_api"],
)

java_library(
    name = "ats",
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/client/longrunningservice:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/shared/size:__subpackages__",
    ],
    runtime_deps = [
        ":ats_mode",
        ":module",
    ],
)

java_library(
    name = "ats_mode",
    srcs = ["AtsMode.java"],
    deps = [
        ":annotations",
        ":job_sync_service",
        ":lab_record_manager",
        ":lab_record_service",
        ":remote_device_manager",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/controller/allocation/allocator",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/controller/device:querier",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode/local:local_device_allocator",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode/remote:remote_test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/controller:service_provider",
        "//src/java/com/google/devtools/mobileharness/infra/controller/scheduler:abstract_scheduler",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:direct_test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:direct_test_runner_setting",
        "//src/java/com/google/devtools/mobileharness/shared/constant/environment",
        "//src/java/com/google/devtools/mobileharness/shared/labinfo:lab_info_service",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "@io_grpc_grpc_java//core",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "device_querier",
    srcs = ["DeviceQuerierImpl.java"],
    deps = [
        ":remote_device_manager",
        "//src/devtools/mobileharness/api/query/proto:device_query_java_proto",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/controller/device:querier",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode/local:device_info_filter",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:callables",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dimension",
        "@com_google_protobuf//:protobuf_java",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "job_sync_service",
    srcs = ["JobSyncService.java"],
    deps = [
        ":annotations",
        "//src/devtools/mobileharness/api/model/proto:job_java_proto",
        "//src/devtools/mobileharness/api/model/proto:test_java_proto",
        "//src/devtools/mobileharness/infra/master/rpc/proto:job_sync_service_java_grpc_proto",
        "//src/devtools/mobileharness/infra/master/rpc/proto:job_sync_service_java_proto",
        "//src/devtools/mobileharness/shared/version/proto:version_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/rpc/grpc:service_util",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:locator",
        "//src/java/com/google/devtools/mobileharness/infra/controller/scheduler:abstract_scheduler",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/shared/util/base:proto_text_format",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:callables",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:more_futures",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/version",
        "//src/java/com/google/devtools/mobileharness/shared/version/checker",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job:job_schedule_unit",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job:job_setting",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job:locator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job:test_schedule_unit",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job/out:timing",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_java_proto",
        "@io_grpc_grpc_java//stub",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "lab_record_manager",
    srcs = ["LabRecordManager.java"],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/devtools/mobileharness/api/model/proto:lab_java_proto",
        "//src/devtools/mobileharness/api/query/proto:lab_query_java_proto",
        "//src/devtools/mobileharness/api/query/proto:lab_record_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:locator",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:schedule_unit",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:more_futures",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:time_utils",
        "@com_google_protobuf//:protobuf_java",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "module",
    srcs = ["AtsModeModule.java"],
    deps = [
        ":annotations",
        ":device_querier",
        ":remote_device_manager",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/controller/device:querier",
        "//src/java/com/google/devtools/mobileharness/infra/controller/scheduler:abstract_scheduler",
        "//src/java/com/google/devtools/mobileharness/infra/controller/scheduler/simple",
        "//src/java/com/google/devtools/mobileharness/shared/labinfo:lab_info_provider",
        "//src/java/com/google/devtools/mobileharness/shared/version",
        "//src/java/com/google/devtools/mobileharness/shared/version/checker",
        "@maven//:com_google_inject_guice",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "remote_device_manager",
    srcs = ["RemoteDeviceManager.java"],
    deps = [
        ":annotations",
        ":lab_record_manager",
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/devtools/mobileharness/api/model/proto:lab_java_proto",
        "//src/devtools/mobileharness/api/query/proto:device_query_java_proto",
        "//src/devtools/mobileharness/api/query/proto:filter_java_proto",
        "//src/devtools/mobileharness/api/query/proto:lab_query_java_proto",
        "//src/devtools/mobileharness/infra/master/rpc/proto:lab_sync_service_java_grpc_proto",
        "//src/devtools/mobileharness/infra/master/rpc/proto:lab_sync_service_java_proto",
        "//src/devtools/mobileharness/shared/version/proto:version_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/rpc/grpc:service_util",
        "//src/java/com/google/devtools/mobileharness/api/model/allocation",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:locator",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:schedule_unit",
        "//src/java/com/google/devtools/mobileharness/infra/controller/scheduler:abstract_scheduler",
        "//src/java/com/google/devtools/mobileharness/shared/labinfo:lab_info_provider",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/shared/util/base:proto_text_format",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/server:grpc_contexts",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:callables",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:more_futures",
        "//src/java/com/google/devtools/mobileharness/shared/util/error:more_throwables",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/version",
        "//src/java/com/google/devtools/mobileharness/shared/version/checker",
        "//src/java/com/google/wireless/qa/mobileharness/shared/controller/event:allocation",
        "@io_grpc_grpc_java//core",
        "@io_grpc_grpc_java//stub",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "lab_record_service",
    srcs = ["LabRecordService.java"],
    deps = [
        ":lab_record_manager",
        "//src/devtools/mobileharness/api/query/proto:lab_record_java_proto",
        "//src/devtools/mobileharness/infra/master/rpc/proto:lab_record_service_java_grpc_proto",
        "//src/devtools/mobileharness/infra/master/rpc/proto:lab_record_service_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/rpc/grpc:service_util",
        "@io_grpc_grpc_java//stub",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)
