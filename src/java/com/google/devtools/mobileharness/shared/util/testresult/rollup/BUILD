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
    default_visibility = [
        "//:deviceinfra_all_pkg",
    ],
)

java_library(
    name = "job_result_util",
    srcs = ["JobResultUtil.java"],
    deps = [
        ":android_instrumentation_test_suite_result_converter",
        ":outcome",
        ":state",
        ":test_case",
        ":test_case_reference",
        ":test_result",
        ":test_suite_overview",
        "//src/devtools/mobileharness/api/model/proto:test_java_proto",
        "//src/devtools/mobileharness/platform/android/instrumentation/result/proto:test_suite_result_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/job/out",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/controller/job/retry/strategy:flaky_test_retry_constants",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/path",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:property",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "@maven//:com_google_guava_guava",
        "@protobuf//:protobuf_java",
    ],
)

java_library(
    name = "test_result",
    srcs = ["TestResult.java"],
    deps = [
        ":outcome",
        ":state",
        ":test_case",
        ":test_suite_overview",
        ":test_suite_overview_converter",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "outcome",
    srcs = ["Outcome.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "stack_trace",
    srcs = ["StackTrace.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
    ],
)

java_library(
    name = "state",
    srcs = ["State.java"],
    deps = [
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "test_case",
    srcs = ["TestCase.java"],
    deps = [
        ":outcome",
        ":stack_trace",
        ":test_case_reference",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "test_case_reference",
    srcs = ["TestCaseReference.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
    ],
)

java_library(
    name = "test_suite_overview",
    srcs = ["TestSuiteOverview.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "android_instrumentation_test_suite_result_converter",
    srcs = ["AndroidInstrumentationTestSuiteResultConverter.java"],
    deps = [
        ":outcome",
        ":stack_trace",
        ":state",
        ":test_case",
        ":test_case_reference",
        ":test_result",
        ":test_suite_overview",
        "//src/devtools/mobileharness/platform/android/instrumentation/result/proto:test_suite_result_java_proto",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_protobuf_protobuf_java",
    ],
)

java_library(
    name = "test_suite_overview_converter",
    srcs = ["TestSuiteOverviewConverter.java"],
    deps = [
        ":test_case",
        ":test_suite_overview",
        "@maven//:com_google_guava_guava",
    ],
)
