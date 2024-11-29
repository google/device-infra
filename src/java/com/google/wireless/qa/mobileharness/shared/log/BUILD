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

load("@rules_java//java:defs.bzl", "java_library")

package(
    default_applicable_licenses = ["//:license"],
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
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
    ],
)

java_library(
    name = "log_data",
    srcs = [
        "LogData.java",
    ],
)

java_library(
    name = "log_collector_backend",
    srcs = [
        "LogCollectorBackend.java",
    ],
    deps = [":log_data"],
)

java_library(
    name = "log_context",
    srcs = [
        "LogContext.java",
    ],
    deps = [
        ":info_log_importance_scope",
        ":log",
        ":log_collector_backend",
        ":log_data",
        "//src/java/com/google/devtools/mobileharness/shared/constant:log_record_importance",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "info_log_importance_scope",
    srcs = ["InfoLogImportanceScope.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/constant/closeable",
        "//src/java/com/google/devtools/mobileharness/shared/util/base:stack_set",
    ],
)
