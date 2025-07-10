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

package(default_applicable_licenses = ["//:license"])

filegroup(
    name = "testdata",
    srcs = glob(["testdata/**/*"]),
)

java_library(
    name = "retry",
    testonly = 1,
    srcs = glob(["*.java"]),
    data = [
        ":testdata",
    ],
    deps = [
        "//src/devtools/mobileharness/infra/ats/console/result/proto:report_java_proto",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/report:compatibility_report_parser",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/result:result_lister_helper",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite:suite_common",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite:suite_test_filter",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/retry:previous_result_loader",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/retry:retry_args",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/retry:retry_generator",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/retry:retry_report_merger",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/retry:retry_type",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/subplan:sub_plan",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/subplan:sub_plan_helper",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/javatests/com/google/devtools/mobileharness/builddefs:truth",
        "//src/javatests/com/google/devtools/mobileharness/shared/util/junit/rule:set_flags_oss",
        "//src/javatests/com/google/devtools/mobileharness/shared/util/runfiles:runfiles_util",
        "@com_google_protobuf//:protobuf_java",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_extensions_guice_testlib",
        "@maven//:com_google_inject_guice",
        "@maven//:javax_inject_jsr330_api",
        "@maven//:junit_junit",
        "@maven//:org_mockito_mockito_core",
    ],
)

junit_test_suites(
    name = "tests",
    sizes = [
        "small",
    ],
    deps = [":retry"],
)
