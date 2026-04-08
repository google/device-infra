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

load("@protobuf//bazel:java_proto_library.bzl", "java_proto_library")
load("@protobuf//bazel:proto_library.bzl", "proto_library")
load("@rules_java//java:defs.bzl", "java_library")

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//:deviceinfra_api_users",
    ],
)

java_library(
    name = "failure_analyzer",
    srcs = ["FailureAnalyzer.java"],
    resources = [":failure_analysis_config.textproto"],
    deps = [
        ":failure_analysis_config_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//third_party/java/protobuf:protobuf_impl",
        "@maven//:com_google_guava_guava",
    ],
)

proto_library(
    name = "failure_analysis_config_proto",
    srcs = ["proto/failure_analysis_config.proto"],
)

java_proto_library(
    name = "failure_analysis_config_java_proto",
    deps = [":failure_analysis_config_proto"],
)
