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

load("@rules_android//android:rules.bzl", "android_binary")
load("@rules_java//java:defs.bzl", "java_library")

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:__subpackages__",
    ],
)

java_library(
    name = "constants",
    srcs = ["Constants.java"],
)

android_binary(
    name = "InstructionDialog",
    srcs = ["InstructionDialogActivity.java"],
    javacopts = ["-source 8 -target 8"],  # TODO: Upgrade android rules
    manifest = "AndroidManifest.xml",
    resource_files = glob(["res/**"]),
    deps = [
        ":constants",
    ],
)
