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
    name = "stub",
    srcs = glob(["*.java"]),
    data = glob(
        [
            "testdata/*.*",
        ],
        allow_empty = True,
    ),
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/logging/controller/uploader/stackdriver/stub",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/server:sever_builder_factory",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/stub:channel_factory",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local:res_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/path",
        "@grpc-java//core",
        "@grpc-java//stub",
        "@grpc-java//testing",
        "@maven//:com_google_api_grpc_grpc_google_cloud_logging_v2",
        "@maven//:com_google_api_grpc_proto_google_cloud_logging_v2",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_truth_extensions_truth_proto_extension",
        "@maven//:junit_junit",
        "@maven//:org_mockito_mockito_core",
    ],
)

junit_test_suites(
    name = "tests",
    sizes = [
        "medium",
    ],
    deps = [":stub"],
)
