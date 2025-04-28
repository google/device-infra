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

load("@rules_java//java:defs.bzl", "java_library")

package(
    default_applicable_licenses = ["//:license"],
    default_testonly = 1,
    default_visibility = [
        "//:deviceinfra_all_pkg",
        "//:omnilab_fusion",
    ],
)

java_library(
    name = "capture_logs",
    srcs = ["CaptureLogs.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:formatter",
        "//src/javatests/com/google/devtools/mobileharness/shared/util/junit/rule/util:finish_with_failure_test_watcher",
        "//src/javatests/com/google/devtools/mobileharness/shared/util/junit/rule/util:strings_debugger",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
        "@maven//:junit_junit",
    ],
)

java_library(
    name = "monitored_string_builders",
    srcs = ["MonitoredStringBuilders.java"],
    deps = [
        "//src/javatests/com/google/devtools/mobileharness/shared/util/junit/rule/util:finish_with_failure_test_watcher",
        "//src/javatests/com/google/devtools/mobileharness/shared/util/junit/rule/util:strings_debugger",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
        "@maven//:junit_junit",
    ],
)

java_library(
    name = "monitored_string_suppliers",
    srcs = ["MonitoredStringSuppliers.java"],
    deps = [
        "//src/javatests/com/google/devtools/mobileharness/shared/util/junit/rule/util:finish_with_failure_test_watcher",
        "//src/javatests/com/google/devtools/mobileharness/shared/util/junit/rule/util:strings_debugger",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
        "@maven//:junit_junit",
    ],
)

java_library(
    name = "print_test_name",
    srcs = ["PrintTestName.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:junit_junit",
    ],
)

java_library(
    name = "set_flags_oss",
    srcs = ["SetFlagsOss.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "@maven//:com_google_guava_guava",
        "@maven//:junit_junit",
    ],
)
