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
    name = "messaging",
    srcs = glob(["*.java"]),
    deps = [
        "//src/devtools/common/metrics/stability/model/proto:error_java_proto",
        "//src/devtools/mobileharness/api/messaging:messaging_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/messaging:messaging_subscriber",
        "//src/java/com/google/devtools/mobileharness/infra/controller/messaging:message_subscriber_backend",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:time_utils",
        "//src/javatests/com/google/devtools/mobileharness/builddefs:truth",
        "@com_google_protobuf//:protobuf_java",
        "@com_google_protobuf_protobuf_java",
        "@maven//:com_google_truth_extensions_truth_proto_extension",
        "@maven//:junit_junit",
        "@maven//:org_mockito_mockito_core",
    ],
)

junit_test_suites(
    name = "gen_tests",
    sizes = [
        "small",
    ],
    deps = [":messaging"],
)
