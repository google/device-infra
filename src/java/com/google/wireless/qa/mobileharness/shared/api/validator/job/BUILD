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
    ],
)

################################################################################
# Common libraries in alphabetical order
################################################################################

java_library(
    name = "job_validator",
    srcs = ["JobValidator.java"],
    deps = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "@maven//:com_google_guava_guava",
    ],
)

################################################################################
# Validator libraries in alphabetical order
################################################################################

java_library(
    name = "android_account_decorator_job_validator",
    srcs = ["AndroidAccountDecoratorJobValidator.java"],
    deps = [
        ":job_validator",
        "//src/java/com/google/devtools/mobileharness/platform/android/accountmanager:enums",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/spec:google_account_decorator_spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dimension",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "android_adb_shell_decorator_job_validator",
    srcs = ["AndroidAdbShellDecoratorJobValidator.java"],
    deps = [
        ":job_validator",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/spec:android_adb_shell_spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dimension",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job/in/spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto/spec:android_adb_shell_decorator_spec_java_proto",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "android_clean_apps_decorator_job_validator",
    srcs = ["AndroidCleanAppsDecoratorJobValidator.java"],
    deps = [
        ":job_validator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/spec:android_clean_apps_spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
    ],
)

java_library(
    name = "android_device_settings_decorator_job_validator",
    srcs = ["AndroidDeviceSettingsDecoratorJobValidator.java"],
    deps = [
        ":job_validator",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job/in/spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto/spec:android_device_setting_decorator_spec_java_proto",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "android_file_puller_decorator_job_validator",
    srcs = ["AndroidFilePullerDecoratorJobValidator.java"],
    deps = [
        ":job_validator",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/base",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/spec:android_file_puller_spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job/in/spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto/spec:android_file_puller_decorator_spec_java_proto",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "android_hd_video_decorator_job_validator",
    srcs = ["AndroidHdVideoDecoratorJobValidator.java"],
    deps = [
        ":job_validator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/spec:android_hd_video_spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_java_proto",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "android_lab_test_support_settings_decorator_job_validator",
    srcs = ["AndroidLabTestSupportSettingsDecoratorJobValidator.java"],
    deps = [
        ":job_validator",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job/in/spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto/spec:android_lab_test_support_settings_decorator_spec_java_proto",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "android_logcat_decorator_job_validator",
    srcs = ["AndroidLogCatDecoratorJobValidator.java"],
    deps = [
        ":job_validator",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/spec:android_logcat_spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator/util:android_decorator_validator_util",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "android_logcat_monitoring_job_validator",
    srcs = ["AndroidLogcatMonitoringDecoratorJobValidator.java"],
    deps = [
        ":job_validator",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job/in/spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto/spec:android_logcat_monitoring_decorator_spec_java_proto",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "android_native_bin_job_validator",
    srcs = ["AndroidNativeBinJobValidator.java"],
    deps = [
        ":job_validator",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/spec:android_native_bin_spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
    ],
)

java_library(
    name = "android_network_activity_logging_job_validator",
    srcs = ["AndroidNetworkActivityLoggingJobValidator.java"],
    deps = [
        ":job_validator",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/platform/android/shared:application_ids",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job/in/spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto/spec:android_network_activity_logging_decorator_spec_java_proto",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "android_orientation_decorator_job_validator",
    srcs = ["AndroidOrientationDecoratorJobValidator.java"],
    deps = [
        ":job_validator",
        "//src/java/com/google/devtools/mobileharness/platform/android/media:enums",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/spec:android_orientation_decorator_spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
    ],
)

java_library(
    name = "android_perfetto_decorator_job_validator",
    srcs = ["AndroidPerfettoDecoratorJobValidator.java"],
    deps = [
        ":job_validator",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/step/android:perfetto_step",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
    ],
)

java_library(
    name = "android_robo_test_job_validator",
    srcs = ["AndroidRoboTestJobValidator.java"],
    deps = [
        ":job_validator",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job/in/spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto/spec:android_robo_test_spec_java_proto",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "android_start_apps_decorator_job_validator",
    srcs = ["AndroidStartAppsDecoratorJobValidator.java"],
    deps = [
        ":job_validator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "@maven//:com_google_code_gson_gson",
    ],
)

java_library(
    name = "android_switch_language_decorator_job_validator",
    srcs = ["AndroidSwitchLanguageDecoratorJobValidator.java"],
    deps = [
        ":job_validator",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job/in/spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto/spec:android_switch_language_decorator_spec_java_proto",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "android_switch_user_decorator_job_validator",
    srcs = ["AndroidSwitchUserDecoratorJobValidator.java"],
    deps = [
        ":job_validator",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/platform/android/user:android_user_util",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator/job/android:android_user_type",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job/in/spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto/spec:android_switch_user_decorator_spec_java_proto",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "composite_device_decorator_adapter_job_validator",
    srcs = ["CompositeDeviceDecoratorAdapterJobValidator.java"],
    deps = [
        ":job_validator",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/base",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/job:job_type_util",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/spec:composite_device_decorator_adapter_spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator/util:composite_device_adapter_validator_util",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_java_proto",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "mobly_aosp_test_job_validator",
    srcs = ["MoblyAospTestJobValidator.java"],
    deps = [
        ":job_validator",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/spec:mobly_aosp_test_spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "mobly_decorator_adapter_job_validator",
    srcs = ["MoblyDecoratorAdapterJobValidator.java"],
    deps = [
        ":job_validator",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator/util:composite_device_adapter_validator_util",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator/util:mobly_decorator_adapter_job_validator_util",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job/in/spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto/spec:mobly_decorator_adapter_spec_java_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto/spec:testbed_decorator_adapter_spec_java_proto",
    ],
)

java_library(
    name = "mobly_test_job_validator",
    srcs = ["MoblyTestJobValidator.java"],
    deps = [
        ":job_validator",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/spec:mobly_test_spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "tradefed_test_job_validator",
    srcs = ["TradefedTestJobValidator.java"],
    deps = [
        ":job_validator",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/spec:tradefed_test_spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job/in/spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto/spec:tradefed_test_spec_java_proto",
        "@maven//:com_google_guava_guava",
    ],
)
