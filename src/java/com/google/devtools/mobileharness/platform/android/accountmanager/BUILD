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
load("@rules_java//java:defs.bzl", "java_library")

DEFAULT_VISIBILITY = [
    "//:deviceinfra_all_pkg",
]

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = DEFAULT_VISIBILITY,
)

java_library(
    name = "android_account_setting",
    srcs = [
        "AndroidAccountSetting.java",
    ],
    deps = [
        ":enums",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
    ],
)

java_library(
    name = "enums",
    srcs = [
        "AccountCredentialType.java",
        "AndroidGoogleAccountType.java",
    ],
    visibility = DEFAULT_VISIBILITY + [
    ],
)

android_library(
    name = "android_google_account_type",
    srcs = ["AndroidGoogleAccountType.java"],
    visibility = [
    ],
)
