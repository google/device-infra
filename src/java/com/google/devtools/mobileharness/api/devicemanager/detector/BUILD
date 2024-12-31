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
#   Interface for Device Infra device management detector.

load("@rules_java//java:defs.bzl", "java_library")

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = ["//:deviceinfra_devicemanagement_pkg"],
)

java_library(
    name = "android_jit_emulator_detector",
    srcs = ["AndroidJitEmulatorDetector.java"],
    deps = [
        ":base",
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/detector/model",
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/detector/model:android_emulator_type",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "base",
    srcs = ["Detector.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/detector/model",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
    ],
)

java_library(
    name = "base_adb",
    srcs = [
        "BaseAdbDetector.java",
    ],
    deps = [
        ":base",
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/detector/model",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/testrunner/device/cache:device_cache_manager",
        "//src/java/com/google/devtools/mobileharness/platform/android/sdktool/adb:adb_internal_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/sdktool/adb:enums",
        "//src/java/com/google/devtools/mobileharness/shared/util/command",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/system",
        "//src/java/com/google/wireless/qa/mobileharness/shared/util:device",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "failed_device",
    srcs = ["FailedDeviceDetector.java"],
    deps = [
        ":base",
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/detector/model",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device/faileddevice:failed_device_table",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "no_op",
    srcs = ["NoOpDeviceDetector.java"],
    runtime_deps = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:no_op_device",
    ],
    deps = [
        ":base",
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/detector/model",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "@maven//:com_google_guava_guava",
    ],
)
