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

java_library(
    name = "android_adb_shell_spec",
    srcs = ["AndroidAdbShellSpec.java"],
    deps = [
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "android_clean_apps_spec",
    srcs = ["AndroidCleanAppsSpec.java"],
    deps = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/annotation",
    ],
)

java_library(
    name = "android_file_puller_spec",
    srcs = ["AndroidFilePullerSpec.java"],
    deps = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/annotation:basic_annotation",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto/spec:android_file_puller_decorator_spec_java_proto",
    ],
)

java_library(
    name = "android_file_pusher_spec",
    srcs = ["AndroidFilePusherSpec.java"],
    deps = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/annotation",
    ],
)

java_library(
    name = "android_logcat_spec",
    srcs = ["AndroidLogCatSpec.java"],
    deps = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/annotation",
    ],
)

java_library(
    name = "android_native_bin_spec",
    srcs = ["AndroidNativeBinSpec.java"],
    deps = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/annotation",
    ],
)

java_library(
    name = "android_real_device_spec",
    srcs = ["AndroidRealDeviceSpec.java"],
    deps = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/annotation",
    ],
)

java_library(
    name = "mobly_aosp_package_test_spec",
    srcs = ["MoblyAospPackageTestSpec.java"],
    deps = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/annotation",
    ],
)
