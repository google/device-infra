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

licenses(["notice"])

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//src/java/com/google/devtools/mobileharness/platform/android/app/telephony:__pkg__",
    ],
)

android_binary(
    name = "TelephonyUtility",
    testonly = 1,
    srcs = glob(["*.java"]),
    # This apk's package name is reused (instead of renamed to the mobileharness path) so that we
    # avoid having to potentially allowlist the new package to install on devices.
    custom_package = "android.telephony.utility",
    manifest = "AndroidManifest.xml",
    multidex = "native",
    deps = [
        "@maven//:androidx_test_core",
        "@maven//:androidx_test_ext_junit",
        "@maven//:androidx_test_runner",
    ],
)
