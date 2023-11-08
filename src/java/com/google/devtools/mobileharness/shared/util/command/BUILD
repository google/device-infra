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
    name = "command",
    srcs = glob(
        ["*.java"],
        exclude = [
            "CommandResult.java",
            "CommandResults.java",
        ],
    ),
    visibility = [
        "//:deviceinfra_all_pkg",
    ],
    exports = [
        ":command_result",
    ],
    deps = [
        ":command_results",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:test_context",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/shared/util/command/backend",
        "//src/java/com/google/devtools/mobileharness/shared/util/command/history",
        "//src/java/com/google/devtools/mobileharness/shared/util/command/history:recorder",
        "//src/java/com/google/devtools/mobileharness/shared/util/command/io",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:count_down_timer",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "command_result",
    srcs = [
        "CommandResult.java",
    ],
    visibility = ["//visibility:private"],
    deps = [
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "command_results",
    srcs = [
        "CommandResults.java",
    ],
    visibility = [
        # Do not add other packages here.
        "//src/java/com/google/devtools/mobileharness/shared/util/command/history:__pkg__",
        "//src/java/com/google/devtools/mobileharness/shared/util/command/testing:__pkg__",
    ],
    exports = [
        ":command_result",
    ],
    deps = [
        ":command_result",
    ],
)
