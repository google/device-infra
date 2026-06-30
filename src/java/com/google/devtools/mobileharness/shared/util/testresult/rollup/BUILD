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
        ":job_run_result",
        ":outcome_rollup_helper",
        ":state_rollup_helper",
        ":test_case_rollup_helper",
        ":test_result",
        ":test_suite_overview_rollup_helper",
        "//src/devtools/mobileharness/api/model/proto:test_java_proto",
        "//src/devtools/mobileharness/platform/android/instrumentation/result/proto:test_suite_result_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/job/out",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/controller/job/retry:flaky_test_retry_constants",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/path",
        "//src/java/com/google/devtools/mobileharness/shared/util/testresult",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "@maven//:com_google_guava_guava",
        "@protobuf//:protobuf_java",
    ],
)

java_library(
    name = "job_run_result",
    srcs = ["JobRunResult.java"],
    deps = [
        ":test_result",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
    ],
)

java_library(
    name = "test_result",
    srcs = ["TestResult.java"],
    deps = [
        ":test_suite_overview_converter",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/shared/util/testresult",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "android_instrumentation_test_suite_result_converter",
    srcs = ["AndroidInstrumentationTestSuiteResultConverter.java"],
    deps = [
        ":test_result",
        "//src/devtools/mobileharness/platform/android/instrumentation/result/proto:test_suite_result_java_proto",
        "//src/java/com/google/devtools/mobileharness/shared/util/testresult",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_protobuf_protobuf_java",
    ],
)

java_library(
    name = "outcome_rollup_helper",
    srcs = ["OutcomeRollupHelper.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/testresult",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "state_rollup_helper",
    srcs = ["StateRollupHelper.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/testresult",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "test_case_rollup_helper",
    srcs = ["TestCaseRollupHelper.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/testresult",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "test_suite_overview_converter",
    srcs = ["TestSuiteOverviewConverter.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/testresult",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "test_suite_overview_rollup_helper",
    srcs = ["TestSuiteOverviewRollupHelper.java"],
    deps = [
        ":test_result",
        "//src/java/com/google/devtools/mobileharness/shared/util/testresult",
        "@maven//:com_google_guava_guava",
    ],
)
