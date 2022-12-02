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

java_library(
    name = "android_packages",
    srcs = [
        "AndroidPackages.java",
    ],
    visibility = ["//:deviceinfra_all_pkg"],
)

java_library(
    name = "sqlite",
    srcs = ["Sqlite.java"],
    tags = [
        "alt_dep=//java/com/google/wireless/qa/mobileharness/shared/android:sqlite",
        "avoid_dep",
    ],
    visibility = [
        "//:deviceinfra_pkg",
    ],
    deps = [
        "//src/java/com/google/devtools/deviceinfra/platform/android/lightning/internal/sdk/adb",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:error_code",
        "@maven//:com_google_guava_guava",
    ],
)
