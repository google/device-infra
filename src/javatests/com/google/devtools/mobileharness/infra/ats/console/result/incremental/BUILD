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

filegroup(
    name = "testdata",
    srcs = glob(["testdata/**/*"]),
)

java_library(
    name = "incremental",
    testonly = 1,
    srcs = glob(["*Test.java"]),
    data = [":testdata"],
    deps = [
        "//src/devtools/mobileharness/infra/ats/console/result/proto:report_java_proto",
        "//src/devtools/mobileharness/infra/ats/console/tradefed/proto:record_java_proto",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/incremental:incremental_result_converter",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/report:compatibility_report_creator",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/report:compatibility_report_module",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/report:compatibility_report_parser",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/xml:xml_constants",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/constant:xts_constants",
        "//src/java/com/google/devtools/mobileharness/shared/util/inject:common_module",
        "//src/javatests/com/google/devtools/mobileharness/builddefs:truth",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/console/util:test_runfiles_util",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@maven//:com_google_protobuf_protobuf_java",
        "@maven//:com_google_testparameterinjector_test_parameter_injector",
        "@maven//:com_google_truth_extensions_truth_proto_extension",
        "@maven//:junit_junit",
        "@protobuf//:protobuf_java",
    ],
)

junit_test_suites(
    name = "gen_tests",
    sizes = [
        "small",
    ],
    deps = [":incremental"],
)
