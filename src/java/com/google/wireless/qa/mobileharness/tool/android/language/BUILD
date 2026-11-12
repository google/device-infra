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
#   MobileHarness Android device switch language app for switching language and country of the device.

load("@rules_android//android:rules.bzl", "android_binary")

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//src/java/com/google/devtools/mobileharness/platform/android/systemsetting:__pkg__",
    ],
)

android_binary(
    name = "switchlanguage",
    srcs = [
        "BlankActivity.java",
        "SwitchLanguageUtil.java",
    ],
    manifest = "AndroidManifest.xml",
    deps = [
        "//src/java/com/google/wireless/qa/mobileharness/tool/android/language/java/android/app",
        "@maven//:com_google_guava_guava",
    ],
)
