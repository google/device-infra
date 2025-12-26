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
        "//:deviceinfra_all_pkg",
        "//:omnilab_fusion",
    ],
)

################################################################################
# Test sets containing runtime_deps of driver/decorator/device/validator/lister
# and local test built-in plugins.
################################################################################

# runtime_deps for OSS lab server tests.
java_library(
    name = "oss_lab_test",
    runtime_deps = [
        ":no_op_test",
        ":partner_test",
        ":xts_test",
    ],
)

# Lab-side runtime_deps for xTS tests.
java_library(
    name = "xts_test",
    runtime_deps = [
        ":xts_test_client",
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/detector:android_jit_emulator_detector",
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/dispatcher:android_jit_emulator_dispatcher",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/plugin:ats_device_recovery_plugin",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:android_adb_shell_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:android_ats_dynamic_config_pusher_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:android_clean_apps_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:android_device_features_check_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:android_device_settings_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:android_install_apps_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:android_mainline_modules_check_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:android_min_sdk_version_check_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:android_shipping_api_level_check_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:android_show_instruction_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:android_switch_user_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:android_jit_emulator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:android_local_emulator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:android_real_device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:failed_device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver:mobly_aosp_test",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver:xts_tradefed_test",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/module:xts_tradefed_test_module",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator/env:android_device_settings_decorator_env_validator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator/env:android_switch_user_decorator_env_validator",
    ],
)

# Client-side runtime_deps for xTS tests.
java_library(
    name = "xts_test_client",
    runtime_deps = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator/job:android_adb_shell_decorator_job_validator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator/job:android_clean_apps_decorator_job_validator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator/job:android_device_settings_decorator_job_validator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator/job:android_switch_user_decorator_job_validator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator/job:mobly_aosp_test_job_validator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator/job:xts_tradefed_test_job_validator",
    ],
)

# Lab-side runtime_deps for partner tests.
java_library(
    name = "partner_test",
    runtime_deps = [
        ":partner_test_client",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:android_account_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:android_file_puller_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:android_file_pusher_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:android_hd_video_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:android_lab_test_support_settings_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:android_logcat_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:android_logcat_monitoring_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:android_network_activity_logging_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:android_orientation_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:android_start_apps_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:android_switch_language_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:composite_device_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:mobly_decorator_adapter",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:android_local_emulator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:android_real_device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:failed_device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver:android_foreground_service_messenger",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver:android_instrumentation",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver:android_native_bin",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver:android_robo_test",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver:mobly_test",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator/env:android_hd_video_decorator_env_validator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator/env:android_native_bin_env_validator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator/env:android_network_activity_logging_env_validator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator/env:android_orientation_decorator_env_validator",
    ],
)

# Client-side runtime_deps for partner tests.
java_library(
    name = "partner_test_client",
    runtime_deps = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/lister:android_native_bin_lister",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/lister:android_robo_driver_lister",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator/job:android_account_decorator_job_validator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator/job:android_file_puller_decorator_job_validator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator/job:android_hd_video_decorator_job_validator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator/job:android_logcat_decorator_job_validator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator/job:android_logcat_monitoring_job_validator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator/job:android_native_bin_job_validator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator/job:android_network_activity_logging_job_validator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator/job:android_orientation_decorator_job_validator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator/job:android_robo_test_job_validator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator/job:android_start_apps_decorator_job_validator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator/job:android_switch_language_decorator_job_validator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator/job:composite_device_decorator_adapter_job_validator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator/job:mobly_decorator_adapter_job_validator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator/job:mobly_test_job_validator",
    ],
)

# Lab-side runtime_deps for no-op tests.
java_library(
    name = "no_op_test",
    runtime_deps = [
        ":no_op_test_client",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:no_op_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:no_op_device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver:no_op_driver",
    ],
)

# Client-side runtime_deps for no-op tests.
java_library(
    name = "no_op_test_client",
    runtime_deps = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/lister:no_op_driver_lister",
    ],
)

java_library(
    name = "all_drivers_and_decorators_for_unit_tests",
    testonly = 1,
    runtime_deps = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:android_adb_shell_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:android_ats_dynamic_config_pusher_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:android_clean_apps_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:android_device_features_check_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:android_device_settings_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:android_file_puller_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:android_install_apps_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:android_lab_test_support_settings_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:android_logcat_monitoring_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:android_mainline_modules_check_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:android_min_sdk_version_check_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:android_network_activity_logging_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:android_orientation_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:android_shipping_api_level_check_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:android_show_instruction_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:android_start_apps_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:android_switch_language_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:android_switch_user_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:composite_device_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:mobly_decorator_adapter",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:no_op_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver:android_instrumentation",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver:android_robo_test",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver:mobly_aosp_test",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver:mobly_test",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver:no_op_driver",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver:xts_tradefed_test",
    ],
)

################################################################################
# Common libraries
################################################################################

# Please use the exported ones instead.
java_library(
    name = "api",
    exports = [
        ":class_util",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:composite_device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/job:job_type_util",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/lister",
    ],
)

# Please use the exported ones instead.
java_library(
    name = "base",
    exports = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:async_timer_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:base_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:base_device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:device_factory",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver:base_driver",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver:driver_factory",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator:validator_factory",
    ],
)

java_library(
    name = "class_util",
    srcs = ["ClassUtil.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/local/annotation",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/reflection:class_constants",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/annotation",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator/env:env_validator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/util:reflection",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "common_library_module",
    srcs = ["CommonLibraryModule.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/testrunner/device/cache:device_cache",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:thread_pools",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:sleeper",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
    ],
)

java_library(
    name = "composite_device_util",
    srcs = ["CompositeDeviceUtil.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/testrunner/device/cache:device_cache",
        "//src/java/com/google/devtools/mobileharness/platform/testbed/adhoc",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:composite_device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:testbed_device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
    ],
)
