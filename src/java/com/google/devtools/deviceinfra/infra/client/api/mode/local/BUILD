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

package(default_applicable_licenses = ["//:license"])

java_library(
    name = "local",
    srcs = glob(["*.java"]),
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice:__subpackages__",
        "//src/javatests/com/google/devtools/deviceinfra/infra/client/api:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/client/longrunningservice:__subpackages__",
    ],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/devtools/mobileharness/api/model/proto:error_java_proto",
        "//src/devtools/mobileharness/api/query/proto:device_query_java_proto",
        "//src/devtools/mobileharness/infra/controller/test/local/utp/proto:incompatible_reason_java_proto",
        "//src/java/com/google/devtools/deviceinfra/infra/core/devicemanager",
        "//src/java/com/google/devtools/deviceinfra/shared/util/concurrent:callables",
        "//src/java/com/google/devtools/deviceinfra/shared/util/concurrent:thread_factory_util",
        "//src/java/com/google/devtools/deviceinfra/shared/util/flags",
        "//src/java/com/google/devtools/deviceinfra/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/deviceinfra/shared/util/time",
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/detector:base",
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/detector:base_adb",
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/detector:no_op",
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/dispatcher:android_real_device",
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/dispatcher:base",
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/dispatcher:no_op_device",
        "//src/java/com/google/devtools/mobileharness/api/model/allocation",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:locator",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:schedule_unit",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/controller/allocation/allocator",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/controller/device:querier",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device:device_status_info",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device:local_device_runner",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device:local_device_test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device/config",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device/external:noop_external_device_manager",
        "//src/java/com/google/devtools/mobileharness/infra/controller/scheduler",
        "//src/java/com/google/devtools/mobileharness/infra/controller/scheduler/simple",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:abstract_test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:direct_test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:direct_test_runner_setting",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/launcher:local_device_test_runner_launcher",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/local:local_direct_test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/local/utp/controller:no_op_test_flow_converter",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:future",
        "//src/java/com/google/devtools/mobileharness/shared/util/message:str_pair",
        "//src/java/com/google/devtools/mobileharness/shared/util/network/localhost",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dimension",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:error_code",
        "//src/java/com/google/wireless/qa/mobileharness/shared/controller/event:allocation",
        "//src/java/com/google/wireless/qa/mobileharness/shared/controller/event:device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/lab",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_java_proto",
        "//src/testing/mobileharness/shared/proto:device_query_java_proto",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)
