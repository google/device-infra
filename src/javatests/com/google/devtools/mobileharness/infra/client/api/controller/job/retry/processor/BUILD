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
    name = "processor",
    srcs = glob(["*.java"]),
    deps = [
        "//src/devtools/mobileharness/api/model/proto:test_java_proto",
        "//src/devtools/mobileharness/platform/android/instrumentation/result/proto:test_suite_result_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/controller/job/retry/processor:android_instrumentation_test_retry_processor",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/controller/job/retry/processor:retry_tests_grouper",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/controller/job/retry/processor:test_retry_processor",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/controller/job/retry/strategy:flaky_test_retry_constants",
        "//src/java/com/google/devtools/mobileharness/platform/android/instrumentation/result:test_suite_result_loader",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:property",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_java_proto",
        "//src/javatests/com/google/devtools/mobileharness/builddefs:truth",
        "@maven//:com_google_guava_guava",
        "@maven//:junit_junit",
        "@maven//:org_mockito_mockito_core",
    ],
)

junit_test_suites(
    name = "gen_tests",
    sizes = [
        "small",
    ],
    deps = [":processor"],
)
