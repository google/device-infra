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
#   Resource and Utils for MobileHarness Android device daemon app.

load("@build_bazel_rules_android//android:rules.bzl", "android_library")

package(
    default_visibility = [
        "//src/java/com/google/devtools/mobileharness/platform/android/app/binary:__subpackages__",
    ],
)

filegroup(
    name = "resource_files",
    srcs = glob(["res/**"]),
)

android_library(
    name = "daemon_util",
    srcs = [
        "DaemonUtil.java",
    ],
    deps = [
    ],
)

android_library(
    name = "res_id",
    srcs = [
        "ResId.java",
    ],
)

android_library(
    name = "daemon_base_activity",
    srcs = [
        "DaemonBaseActivity.java",
    ],
    deps = [
        ":daemon_util",
        ":res_id",
    ],
)
