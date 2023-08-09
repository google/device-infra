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
#   MobileHarness Android device daemon version 2 for unlocking device screen
#   and keeping device awake. Target on devices with API level 28 and above.

load("@rules_android_sdk//android:rules.bzl", "android_binary", "android_library")

android_library(
    name = "resources",
    javacopts = ["-source 8 -target 8"],
    manifest = "AndroidManifest.xml",
    resource_files = ["//src/java/com/google/devtools/mobileharness/platform/android/app/binary/devicedaemon/shared:resource_files"],
)

android_library(
    name = "daemon_lib",
    srcs = [":resources"] + glob(["*.java"]),
    javacopts = ["-source 8 -target 8"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/platform/android/app/binary/devicedaemon/shared:daemon_base_activity",
        "//src/java/com/google/devtools/mobileharness/platform/android/app/binary/devicedaemon/shared:res_id",
        "@maven//:androidx_annotation_annotation",
        "@maven//:androidx_core_core",
    ],
)

android_binary(
    name = "daemon",
    javacopts = ["-source 8 -target 8"],
    manifest = "AndroidManifest.xml",
    multidex = "native",
    resource_files = ["//src/java/com/google/devtools/mobileharness/platform/android/app/binary/devicedaemon/shared:resource_files"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/platform/android/app/devicedaemon/impl:__pkg__",
    ],
    deps = [":daemon_lib"],
)
