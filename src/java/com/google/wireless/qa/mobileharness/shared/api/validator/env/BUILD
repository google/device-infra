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
    name = "env_validator",
    srcs = ["EnvValidator.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
    ],
)

################################################################################
# Validator libraries in alphabetical order
################################################################################

java_library(
    name = "android_device_settings_decorator_env_validator",
    srcs = ["AndroidDeviceSettingsDecoratorEnvValidator.java"],
    deps = [
        ":env_validator",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/platform/android/sdktool/adb:enums",
        "//src/java/com/google/devtools/mobileharness/platform/android/systemsetting:system_setting_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:android_device",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "android_device_version_env_validator",
    srcs = ["AndroidDeviceVersionEnvValidator.java"],
    deps = [
        ":env_validator",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:android_device",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "android_hd_video_decorator_env_validator",
    srcs = ["AndroidHdVideoDecoratorEnvValidator.java"],
    deps = [
        ":android_device_version_env_validator",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "android_native_bin_env_validator",
    srcs = ["AndroidNativeBinEnvValidator.java"],
    deps = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator/env:android_device_version_env_validator",
    ],
)

java_library(
    name = "android_perfetto_decorator_env_validator",
    srcs = ["AndroidPerfettoDecoratorEnvValidator.java"],
    deps = [
        ":android_device_version_env_validator",
    ],
)

java_library(
    name = "android_switch_user_decorator_env_validator",
    srcs = ["AndroidSwitchUserDecoratorEnvValidator.java"],
    deps = [
        ":env_validator",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:android_device",
        "@maven//:com_google_code_findbugs_jsr305",
    ],
)
