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
    name = "testing",
    srcs = glob(["*.java"]),
    deps = [
        "//src/devtools/mobileharness/shared/util/comm/testing/proto:dummy_service_java_proto",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/testing:dummy_service_lib",
        "//src/javatests/com/google/devtools/mobileharness/builddefs:truth",
        "@io_grpc_grpc_java//core",
        "@io_grpc_grpc_java//core:inprocess",
        "@io_grpc_grpc_java//testing",
        "@maven//:com_google_guava_guava",
        "@maven//:junit_junit",
    ],
)

junit_test_suites(
    name = "gen_tests",
    sizes = [
        "small",
    ],
    deps = [":testing"],
)
