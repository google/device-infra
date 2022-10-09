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
#   Mobile Harness logging system.

package(
    default_visibility = [
        "//:deviceinfra_all_pkg",
    ],
)

java_library(
    name = "log",
    srcs = [
        "LogCollector.java",
        "LoggingApi.java",
    ],
    tags = [
        "alt_dep=//java/com/google/wireless/qa/mobileharness/shared/log",
        "avoid_dep",
    ],
    deps = [
        "//src/java/com/google/devtools/deviceinfra/shared/util/logging:google_logger",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
    ],
)

java_library(
    name = "log_data",
    srcs = [
        "LogData.java",
    ],
    tags = [
        "alt_dep=//java/com/google/wireless/qa/mobileharness/shared/log:log_data",
        "avoid_dep",
    ],
)

java_library(
    name = "log_collector_backend",
    srcs = [
        "LogCollectorBackend.java",
    ],
    tags = [
        "alt_dep=//java/com/google/wireless/qa/mobileharness/shared/log:log_collector_backend",
        "avoid_dep",
    ],
    deps = [":log_data"],
)

java_library(
    name = "log_context",
    srcs = [
        "LogContext.java",
    ],
    tags = [
        "alt_dep=//java/com/google/wireless/qa/mobileharness/shared/log:log_context",
        "avoid_dep",
    ],
    deps = [
        ":log",
        ":log_collector_backend",
        ":log_data",
        "//src/java/com/google/devtools/deviceinfra/shared/util/logging:google_logger",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)
