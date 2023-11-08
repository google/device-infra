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
#   Data models for Mobile Harness device manager dispatcher.

package(default_applicable_licenses = ["//:license"])

java_library(
    name = "model",
    srcs = [
        "DispatchResult.java",
        "DispatchResults.java",
    ],
    visibility = [
        "//:deviceinfra_devicemanagement_pkg",
        "//:deviceinfra_pkg",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/lab:device_id_descriptor",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)
