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

load("//src/javatests/com/google/devtools/mobileharness/builddefs:junit_test_suites.bzl", "junit_test_suites")

package(default_applicable_licenses = ["//:license"])

filegroup(
    name = "testdata",
    srcs = glob(["testdata/**/*"]),
)

java_library(
    name = "xml",
    testonly = 1,
    srcs = glob(["*.java"]),
    data = [
        ":testdata",
    ],
    deps = [
        "//src/devtools/mobileharness/infra/ats/console/result/proto:result_java_proto",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/mobly:parser",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/xml:mobly_result_info",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/xml:xml_result_formatter",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/xml:xml_result_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/sdktool/adb:adb_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/sdktool/adb:enums",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/javatests/com/google/devtools/mobileharness/builddefs:truth",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/console/util:test_runfiles_util",
        "@com_google_protobuf//:protobuf_java",
        "@maven//:com_google_guava_guava",
        "@maven//:junit_junit",
        "@maven//:org_mockito_mockito_core",
    ],
)

junit_test_suites(
    name = "tests",
    sizes = [
        "small",
        "medium",
    ],
    deps = [":xml"],
)
