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
    name = "aapt",
    srcs = ["Aapt.java"],
    resources = [
    ],
    visibility = [
        "//:deviceinfra_all_pkg",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/platform/android/shared/constant:splitters",
        "//src/java/com/google/devtools/mobileharness/shared/util/command",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:android_device_spec_java_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/util:base",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "android_packages",
    srcs = [
        "AndroidPackages.java",
    ],
    visibility = ["//:deviceinfra_all_pkg"],
)

java_library(
    name = "runtime_charging_util",
    srcs = ["RuntimeChargingUtil.java"],
    visibility = [
        "//src/java/com/google/devtools/deviceinfra/ext/devicemanagement/device/platform/android:__subpackages__",
        "//src/javatests/com/google/devtools/deviceinfra/ext/devicemanagement/device/platform/android:__subpackages__",
    ],
    deps = [
        "//src/java/com/google/devtools/deviceinfra/platform/android/lightning/internal/sdk/adb",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dimension",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:error_code",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "serial_console_util",
    srcs = [
        "SerialConsole.java",
    ],
    visibility = [
        "//src/java/com/google/devtools/deviceinfra/ext/devicemanagement/device/platform/android/realdevice:__pkg__",
    ],
)

java_library(
    name = "wifi_util",
    srcs = ["WifiUtil.java"],
    resources = [
        "//src/java/com/google/devtools/mobileharness/platform/android/app/binary/wifiutil:WifiUtil.apk",
    ],
    visibility = [
        "//:deviceinfra_all_pkg",
    ],
    deps = [
        ":aapt",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local:res_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:error_code",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "sqlite",
    srcs = ["Sqlite.java"],
    visibility = [
        "//:deviceinfra_pkg",
    ],
    deps = [
        "//src/java/com/google/devtools/deviceinfra/platform/android/lightning/internal/sdk/adb",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:error_code",
        "@maven//:com_google_guava_guava",
    ],
)
