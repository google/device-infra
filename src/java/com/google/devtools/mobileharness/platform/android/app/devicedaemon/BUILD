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

# Package for Mobile Harness device daemon utils related.

java_library(
    name = "device_daemon_apk_info",
    visibility = [
        "//:deviceinfra_all_pkg",
    ],
    exports = [
        ":device_daemon_apk_info_interface",
        ":device_daemon_apk_info_provider",
    ],
)

java_library(
    name = "device_daemon_apk_info_interface",
    srcs = [
        "DeviceDaemonApkInfo.java",
    ],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/platform/android/app/devicedaemon:__subpackages__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "device_daemon_apk_info_provider",
    srcs = [
        "DeviceDaemonApkInfoProvider.java",
    ],
    deps = [
        ":device_daemon_apk_info_interface",
        "//src/java/com/google/devtools/mobileharness/platform/android/app/devicedaemon/impl",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
    ],
)

java_library(
    name = "device_daemon_helper",
    srcs = [
        "DeviceDaemonHelper.java",
    ],
    visibility = [
        "//src/java/com/google/devtools/deviceinfra/ext/devicemanagement/device/platform/android:__subpackages__",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:__pkg__",
        "//src/javatests/com/google/devtools/deviceinfra/ext/devicemanagement/device/platform/android:__subpackages__",
    ],
    deps = [
        ":device_daemon_apk_info",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/platform/android/connectivity:android_connectivity_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/lightning/apkinstaller",
        "//src/java/com/google/devtools/mobileharness/platform/android/packagemanager:android_package_manager_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/packagemanager:enums",
        "//src/java/com/google/devtools/mobileharness/platform/android/process:android_process_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/systemsetting:system_setting_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/base",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api",
        "//src/java/com/google/wireless/qa/mobileharness/shared/log",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)
