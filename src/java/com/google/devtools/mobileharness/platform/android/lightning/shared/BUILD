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

package(default_applicable_licenses = ["//:license"])

java_library(
    name = "adb_output_parsing_util",
    srcs = ["AdbOutputParsingUtil.java"],
    visibility = [
        "//:deviceinfra_pkg",
        "//src/java/com/google/devtools/mobileharness/platform/android/lightning:__subpackages__",
    ],
)

java_library(
    name = "shared_log_util",
    srcs = [
        "SharedLogUtil.java",
    ],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/platform/android/connectivity:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/platform/android/lightning:__subpackages__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared/log",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
    ],
)

java_library(
    name = "shared_property_util",
    srcs = [
        "SharedPropertyUtil.java",
    ],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/platform/android/lightning:__subpackages__",
    ],
    deps = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/api",
    ],
)
