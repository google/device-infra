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
#   Shared libs for locating remote/local mobile devices.

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode/local:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/lab:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/client/api/mode/local:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/controller/device:__subpackages__",
    ],
)

java_library(
    name = "base_detector_dispatcher_picker",
    srcs = ["BaseDetectorDispatcherPicker.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/detector:base",
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/detector:base_adb",
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/detector:no_op",
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/dispatcher:android_real_device",
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/dispatcher:base",
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/dispatcher:no_op_device",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device:dispatcher_manager",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "base_device_status_provider",
    srcs = ["BaseDeviceStatusProvider.java"],
    visibility = [
        "//java/com/google/devtools/mobileharness/infra/dmintegration/tragent:__pkg__",  # deviceinfra:google-only(internal)
    ],
    deps = [
        ":device_status_info",
        ":device_status_provider",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "device",
    srcs = [
        "LocalDeviceDispatch.java",
        "LocalDeviceManager.java",
    ],
    deps = [
        ":base_device_status_provider",
        ":device_helper_factory",
        ":device_id_manager",
        ":device_info_manager",
        ":device_state_checker",
        ":device_status_info",
        ":device_status_provider",
        ":local_device_lifecycle_and_test_runner",
        ":local_device_runner",
        ":local_device_runner_provider",
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/detector:base",
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/detector/model",
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/dispatcher:base",
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/dispatcher/model",
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/util:class_util",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:device_id_descriptor",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:locator",
        "//src/java/com/google/devtools/mobileharness/api/testrunner/device/cache:device_cache_manager",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device/external:external_device_manager",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:callables",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:more_futures",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:sleeper",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api",
        "//src/java/com/google/wireless/qa/mobileharness/shared/controller/event:device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/controller/stat",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "device_helper_factory",
    srcs = ["DeviceHelperFactory.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/lab:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/lab/rpc/service:__subpackages__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api",
    ],
)

java_library(
    name = "device_id_manager",
    srcs = ["DeviceIdManager.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/controller/device/config:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/lab:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/lab/controller:__pkg__",
        "//src/java/com/google/wireless/qa/mobileharness/shared/controller/stat:__pkg__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/lab:device_id_descriptor",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "device_info_manager",
    srcs = ["DeviceInfoManager.java"],
    visibility = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/api:__subpackages__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/lab:device_id_descriptor",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:device_info",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:device_info_factory",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device/config",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/system",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "device_state_checker",
    srcs = ["DeviceStateChecker.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/lab/controller:__pkg__",
    ],
    deps = ["//src/java/com/google/devtools/mobileharness/api/model/error"],
)

java_library(
    name = "device_status_info",
    srcs = ["DeviceStatusInfo.java"],
    deps = [
        "//src/devtools/common/metrics/stability/model/proto:error_java_proto",
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "@maven//:com_google_code_findbugs_jsr305",
    ],
)

java_library(
    name = "device_status_provider",
    srcs = ["DeviceStatusProvider.java"],
    deps = [
        ":device_status_info",
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "dispatcher_manager",
    srcs = ["DispatcherManager.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode/local:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/controller/device:__subpackages__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/dispatcher:base",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "local_device_lifecycle_and_test_runner",
    srcs = ["LocalDeviceLifecycleAndTestRunner.java"],
    deps = [
        ":device_info_manager",
        ":local_device_runner",
        ":local_device_test_executor",
        "//src/devtools/common/metrics/stability/model/proto:error_java_proto",
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/devtools/mobileharness/api/model/proto:test_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/converter",
        "//src/java/com/google/devtools/mobileharness/api/model/allocation",
        "//src/java/com/google/devtools/mobileharness/api/model/constant:device_property",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:device_id_descriptor",
        "//src/java/com/google/devtools/mobileharness/api/testrunner/device/cache:device_cache_manager",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device/config",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device/external:external_device_manager",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device/util:device_reboot_util",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/event",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/model:test_execution_result",
        "//src/java/com/google/devtools/mobileharness/shared/util/error:more_throwables",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:tag",
        "//src/java/com/google/devtools/mobileharness/shared/util/message:str_pair",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:sleeper",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api:base",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/annotation",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dimension",
        "//src/java/com/google/wireless/qa/mobileharness/shared/controller/event:device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/controller/stat",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:common_java_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_java_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/util:device",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "local_device_runner",
    srcs = ["LocalDeviceRunner.java"],
    deps = [
        ":local_device_test_runner",
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
    ],
)

java_library(
    name = "local_device_runner_provider",
    srcs = ["LocalDeviceRunnerProvider.java"],
    deps = [
        ":local_device_test_runner",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
    ],
)

java_library(
    name = "local_device_test_executor",
    srcs = ["LocalDeviceTestExecutor.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/model:test_execution_result",
    ],
)

java_library(
    name = "local_device_test_runner",
    srcs = ["LocalDeviceTestRunner.java"],
    deps = [
        ":local_device_test_executor",
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_java_proto",
    ],
)
