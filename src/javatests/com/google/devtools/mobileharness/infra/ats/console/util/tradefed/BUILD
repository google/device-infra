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

load("//src/javatests/com/google/devtools/deviceinfra/builddefs:junit_test_suites.bzl", "junit_test_suites")

package(
    default_applicable_licenses = ["//:license"],
    default_testonly = 1,
)

java_library(
    name = "tradefed",
    srcs = glob(
        ["*.java"],
        exclude = [
            "TestRecordHelperTestData.java",
            "TestModule.java",
        ],
    ),
    deps = [
        ":test_data",
        ":test_module",
        "//src/devtools/mobileharness/infra/ats/console/tradefed/proto:record_java_proto",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/tradefed:test_record_helper",
        "//src/javatests/com/google/devtools/deviceinfra/builddefs:truth",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@maven//:javax_inject_jsr330_api",
        "@maven//:junit_junit",
    ],
)

java_library(
    name = "test_data",
    srcs = ["TestRecordHelperTestData.java"],
    deps = [
        "//src/devtools/mobileharness/infra/ats/console/tradefed/proto:record_java_proto",
        "@com_google_protobuf_protobuf_java",
    ],
)

java_library(
    name = "test_module",
    srcs = ["TestModule.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:thread_pools",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@maven//:javax_inject_jsr330_api",
    ],
)

junit_test_suites(
    name = "gen_tests",
    sizes = [
        "small",
    ],
    deps = [":tradefed"],
)
