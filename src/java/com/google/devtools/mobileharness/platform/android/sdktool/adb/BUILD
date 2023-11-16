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

DEFAULT_VISIBILITY = [
    "//:deviceinfra_all_pkg",
]

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = DEFAULT_VISIBILITY,
)

java_library(
    name = "enums",
    srcs = [
        "AndroidContent.java",
        "AndroidProperty.java",
        "AndroidService.java",
        "AndroidSettings.java",
        "AndroidSvc.java",
        "AndroidVersion.java",
        "DeviceConnectionState.java",
        "DeviceState.java",
        "DumpSysType.java",
        "KeyEvent.java",
        "LogCatPriority.java",
        "RebootMode.java",
    ],
    visibility = DEFAULT_VISIBILITY + [
        "//src/java/com/google/devtools/deviceaction:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console:__subpackages__",
        "//src/javatests/com/google/devtools/deviceaction:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/console:__subpackages__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "android_adb_version_util",
    srcs = ["AndroidAdbVersionUtil.java"],
    visibility = ["//src/javatests/com/google/devtools/mobileharness/platform/android/sdktool/adb:__pkg__"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/platform/android/shared/constant:splitters",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_guava_guava",
    ],
)

# For internal use only, driver/decorator/plugin should NOT use it directly
java_library(
    name = "adb_internal_util",
    srcs = [
        "AndroidAdbInternalUtil.java",
    ],
    visibility = [
        "//:deviceinfra_devicemanagement_pkg",
        "//src/java/com/google/devtools/deviceinfra/ext/devicemanagement/device/platform/android:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/platform/android/lightning/systemstate:__pkg__",
        "//src/java/com/google/devtools/mobileharness/platform/android/systemspec:__pkg__",
        "//src/javatests/com/google/devtools/deviceinfra/ext/devicemanagement/device/platform/android:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/console:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/platform/android/sdktool/adb:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/platform/android/systemspec:__pkg__",
    ],
    deps = [
        ":android_adb_version_util",
        ":enums",
        "//src/devtools/mobileharness/platform/android/sdktool/proto:adb_java_proto",
        "//src/java/com/google/devtools/deviceinfra/platform/android/lightning/internal/sdk/adb",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/platform/android/shared/constant:device_constant",
        "//src/java/com/google/devtools/mobileharness/platform/android/shared/constant:splitters",
        "//src/java/com/google/devtools/mobileharness/platform/android/shared/emulator:emulator_id",
        "//src/java/com/google/devtools/mobileharness/shared/util/base",
        "//src/java/com/google/devtools/mobileharness/shared/util/error:more_throwables",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "adb_util",
    srcs = [
        "AndroidAdbUtil.java",
        "IntentArgs.java",
        "WaitArgs.java",
    ],
    visibility = DEFAULT_VISIBILITY + [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/console:__subpackages__",
    ],
    deps = [
        ":adb_internal_util",
        ":enums",
        "//src/java/com/google/devtools/deviceinfra/platform/android/lightning/internal/sdk/adb",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/platform/android/shared/autovalue:util_args",
        "//src/java/com/google/devtools/mobileharness/platform/android/shared/constant:splitters",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/shared/util/base",
        "//src/java/com/google/devtools/mobileharness/shared/util/command",
        "//src/java/com/google/devtools/mobileharness/shared/util/command/linecallback",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/shell",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:sleeper",
        "//src/java/com/google/wireless/qa/mobileharness/shared/util:base",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "usb_device_locator",
    srcs = ["UsbDeviceLocator.java"],
    visibility = [
        "//src/java/com/google/devtools/deviceinfra/ext/devicemanagement/device/platform/android:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/container:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/platform/android/systemspec:__pkg__",
        "//src/javatests/com/google/devtools/deviceinfra/ext/devicemanagement/device/platform/android:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/platform/android/systemspec:__pkg__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
    ],
)
