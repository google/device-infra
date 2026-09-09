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

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//:deviceinfra_all_pkg",
    ],
)

java_library(
    name = "snapshotter",
    srcs = ["TestInfoSnapshotter.java"],
    deps = [
        "//src/devtools/mobileharness/infra/controller/plugin/proto:plugin_worker_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:common_java_proto",
        "@maven//:com_google_code_findbugs_jsr305",
    ],
)

java_library(
    name = "delta_applier",
    srcs = ["TestInfoDeltaApplier.java"],
    deps = [
        "//src/devtools/mobileharness/infra/controller/plugin/proto:plugin_worker_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/messaging/message",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/comm/message:test_message_manager",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_java_proto",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "launcher",
    srcs = ["SubprocessPluginLauncher.java"],
    deps = [
        "//src/devtools/mobileharness/infra/controller/plugin/proto:plugin_worker_java_grpc",
        "//src/devtools/mobileharness/infra/controller/plugin/proto:plugin_worker_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/command",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/system",
        "@grpc-java//core",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "subscriber",
    srcs = ["SubprocessPluginSubscriber.java"],
    deps = [
        ":delta_applier",
        ":launcher",
        ":snapshotter",
        "//src/devtools/common/metrics/stability/model/proto:error_java_proto",
        "//src/devtools/mobileharness/infra/controller/plugin/proto:plugin_worker_java_grpc",
        "//src/devtools/mobileharness/infra/controller/plugin/proto:plugin_worker_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/testrunner/event/test",
        "//src/java/com/google/devtools/mobileharness/api/testrunner/plugin:exception",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/comm/message/event",
        "//src/java/com/google/wireless/qa/mobileharness/shared/controller/event:test",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "loader",
    srcs = ["SubprocessPluginLoader.java"],
    deps = [
        ":launcher",
        ":subscriber",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "@maven//:com_google_code_findbugs_jsr305",
    ],
)
