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
#   The controller for Mobile Harness lab.

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//:deviceinfra_all_pkg",
    ],
)

java_library(
    name = "file_publisher",
    srcs = ["FilePublisher.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/path",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dir",
        "//src/java/com/google/wireless/qa/mobileharness/shared/util:net_util",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "forwarding_test_message_buffer",
    srcs = ["ForwardingTestMessageBuffer.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:test_info_manager",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/messaging/message",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared/comm/message/event",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "job_manager",
    srcs = ["JobManager.java"],
    deps = [
        "//src/devtools/mobileharness/infra/lab/proto:file_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/job:locator",
        "//src/java/com/google/devtools/mobileharness/api/model/job/in:dirs",
        "//src/java/com/google/devtools/mobileharness/infra/container/controller:proxy_test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/manager",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/model:test_execution_unit",
        "//src/java/com/google/devtools/mobileharness/shared/file/resolver:file_resolver",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "lab_dimension_manager",
    srcs = ["LabDimensionManager.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/lab/in:local_dimension",
    ],
)

java_library(
    name = "lab_direct_test_runner_holder",
    srcs = ["LabDirectTestRunnerHolder.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:device_feature_manager",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:test_info_manager",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/launcher:direct_test_runner_launcher_factory",
        "//src/java/com/google/devtools/mobileharness/infra/lab/controller/util:lab_file_notifier_factory",
        "//src/java/com/google/devtools/mobileharness/infra/lab/rpc/service/util:job_dir_factory",
    ],
)

java_library(
    name = "lab_local_direct_test_runner",
    srcs = ["LabLocalDirectTestRunner.java"],
    deps = [
        "//src/devtools/mobileharness/infra/controller/test/local/utp/proto:incompatible_reason_java_proto",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:abstract_test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:direct_test_runner_setting",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/local:local_direct_test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/local/utp/controller:no_op_test_flow_converter",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/local/utp/controller:test_flow_converter",
        "//src/java/com/google/devtools/mobileharness/infra/lab/controller/util:lab_file_notifier",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/allocation",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
    ],
)

java_library(
    name = "master_syncer_for_device",
    srcs = ["MasterSyncerForDevice.java"],
    deps = [
        "//src/devtools/common/metrics/stability/model/proto:error_java_proto",
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/devtools/mobileharness/infra/master/rpc/proto:lab_sync_service_java_proto",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device:device_status_info",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device:device_status_provider",
        "//src/java/com/google/devtools/mobileharness/infra/lab/rpc/stub/helper:lab_sync",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api",
        "//src/java/com/google/wireless/qa/mobileharness/shared/controller/event:device",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "master_syncer_for_job",
    srcs = ["MasterSyncerForJob.java"],
    deps = [
        ":job_manager",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:locator",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device:device_state_checker",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/event",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/model:test_execution_unit",
        "//src/java/com/google/devtools/mobileharness/infra/lab/rpc/stub/helper:job_sync",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/util:net_util",
        "@maven//:com_google_guava_guava",
    ],
)
