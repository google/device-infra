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
    name = "result",
    testonly = 1,
    srcs = glob(["*.java"]),
    data = [
        ":testdata",
    ],
    deps = [
        "//src/devtools/mobileharness/platform/android/instrumentation/result/proto:test_suite_result_java_proto",
        "//src/java/com/google/devtools/mobileharness/platform/android/instrumentation/result:test_suite_result_converter",
        "//src/java/com/google/devtools/mobileharness/shared/util/junit/xmlwriter/model",
        "//src/javatests/com/google/devtools/mobileharness/builddefs:truth",
        "//src/javatests/com/google/devtools/mobileharness/shared/util/runfiles:runfiles_util",
        "@maven//:com_google_protobuf_protobuf_java",
        "@maven//:junit_junit",
        "@protobuf//:protobuf_java",
        "@protobuf//:protobuf_java_util",
    ],
)

junit_test_suites(
    name = "gen_tests",
    sizes = [
        "medium",
    ],
    deps = [
        ":result",
    ],
)
