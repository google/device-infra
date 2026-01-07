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

package(default_applicable_licenses = ["//:license"])

java_library(
    name = "local",
    srcs = [
        "LocalDeviceManagerSchedulerSyncer.java",
        "LocalDeviceQuerier.java",
        "LocalMode.java",
    ],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/common/olcserver:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/client:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/local:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/client:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/client/api:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/controller/device/proxy:__pkg__",
    ],
    deps = [
        ":device_info_filter",
        ":local_device_allocator",
        ":local_device_verifier",
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/devtools/mobileharness/api/query/proto:device_query_java_proto",
        "//src/devtools/mobileharness/infra/controller/test/local/utp/proto:incompatible_reason_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:locator",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:schedule_unit",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/controller/allocation/allocator",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/controller/device:querier",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device:device_id_manager",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device:device_status_info",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device:local_device_runner",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device:test_executor",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device/bootstrap:detector_dispatcher_selector",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device/bootstrap:detectors_and_dispatchers",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device/config",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device/config:api_config_file_processor",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device/config:api_config_listener",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device/external:noop_external_device_manager",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device/proxy:proxy_device_manager",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device/proxy:proxy_device_manager_module",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device/util:device_status_info_printer",
        "//src/java/com/google/devtools/mobileharness/infra/controller/scheduler:abstract_scheduler",
        "//src/java/com/google/devtools/mobileharness/infra/controller/scheduler/simple",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:abstract_test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:direct_test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:direct_test_runner_setting",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/launcher:local_device_test_runner_launcher",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/launcher:thread_pool_test_runner_launcher",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/local:local_test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/local/utp/controller:no_op_test_flow_converter",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/local/utp/controller:test_flow_converter",
        "//src/java/com/google/devtools/mobileharness/infra/lab/controller:local_file_based_device_config_manager",
        "//src/java/com/google/devtools/mobileharness/shared/context:invocation_context_executors",
        "//src/java/com/google/devtools/mobileharness/shared/file/resolver:file_resolver",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:callables",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:more_futures",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:thread_factory_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:thread_pools",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/message:str_pair",
        "//src/java/com/google/devtools/mobileharness/shared/util/network/localhost",
        "//src/java/com/google/devtools/mobileharness/shared/util/system:shutdown_hook_manager",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:sleeper",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dimension",
        "//src/java/com/google/wireless/qa/mobileharness/shared/controller/event:device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/lab",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@protobuf//:protobuf_java",
    ],
)

java_library(
    name = "device_info_filter",
    srcs = ["DeviceInfoFilter.java"],
    visibility = ["//src/java/com/google/devtools/mobileharness/infra/client/api/mode/ats:__pkg__"],
    deps = [
        "//src/devtools/mobileharness/api/query/proto:device_query_java_proto",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "local_device_allocator",
    srcs = ["LocalDeviceAllocator.java"],
    visibility = ["//src/java/com/google/devtools/mobileharness/infra/client/api/mode/ats:__pkg__"],
    deps = [
        "//src/devtools/common/metrics/stability/model/proto:error_java_proto",
        "//src/devtools/mobileharness/api/model/proto:test_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/allocation",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/job:locator",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:locator",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/controller/allocation/allocator",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device/proxy:proxy_device_manager",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device/proxy:proxy_device_requirement",
        "//src/java/com/google/devtools/mobileharness/infra/controller/scheduler:abstract_scheduler",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:more_futures",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/controller/event:allocation",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_java_proto",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "local_device_verifier",
    srcs = ["LocalDeviceVerifier.java"],
    deps = [
        ":local_device_allocator",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device:local_device_runner",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
    ],
)
