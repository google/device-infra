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
load("//src/javatests/com/google/devtools/mobileharness/builddefs:junit_test_suites.bzl", "junit_test_suites")

package(
    default_applicable_licenses = ["//:license"],
    default_testonly = 1,
)

java_library(
    name = "rollup",
    srcs = glob(["*.java"]),
    deps = [
        "//src/devtools/mobileharness/api/model/proto:test_java_proto",
        "//src/devtools/mobileharness/platform/android/instrumentation/result/proto:test_suite_result_java_proto",
        "//src/java/com/google/devtools/mobileharness/shared/util/testresult/rollup:job_result_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/testresult/rollup:outcome",
        "//src/java/com/google/devtools/mobileharness/shared/util/testresult/rollup:stack_trace",
        "//src/java/com/google/devtools/mobileharness/shared/util/testresult/rollup:state",
        "//src/java/com/google/devtools/mobileharness/shared/util/testresult/rollup:test_case",
        "//src/java/com/google/devtools/mobileharness/shared/util/testresult/rollup:test_case_reference",
        "//src/java/com/google/devtools/mobileharness/shared/util/testresult/rollup:test_result",
        "//src/java/com/google/devtools/mobileharness/shared/util/testresult/rollup:test_suite_overview",
        "//src/java/com/google/devtools/mobileharness/shared/util/testresult/rollup:test_suite_overview_converter",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/javatests/com/google/devtools/mobileharness/builddefs:truth",
        "@maven//:com_google_guava_guava",
        "@maven//:junit_junit",
        "@maven//:org_mockito_mockito_core",
        "@protobuf//:protobuf_java_util",
    ],
)

junit_test_suites(
    name = "tests",
    sizes = [
        "small",
    ],
    deps = [":rollup"],
)
