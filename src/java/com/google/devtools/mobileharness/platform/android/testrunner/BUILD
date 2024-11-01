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

load("@rules_android_sdk//android:rules.bzl", "android_library")

android_library(
    name = "android_repeated_test_runner",
    srcs = ["AndroidRepeatedTestRunner.kt"],
    visibility = [
    ],
    exports = [
        ":annotations",
        ":repetition_info",
    ],
    deps = [
        ":annotations",
        ":repetition_info",
        "//third_party/android/androidx_test/runner/android_junit_runner",
        "@maven//:junit_junit:junit-android",
    ],
)

android_library(
    name = "annotations",
    srcs = [
        "RepeatedTest.kt",
    ],
    visibility = ["//visibility:private"],
)

android_library(
    name = "repetition_info",
    srcs = [
        "RepetitionInfo.kt",
    ],
    visibility = ["//visibility:private"],
)
