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
    name = "subprocess",
    srcs = glob(["*.java"]),
    data = [
        "//src/java/com/google/devtools/mobileharness/infra/controller/plugin/subprocess/worker:plugin_worker_runner",
    ],
    runtime_deps = [
        "//src/java/com/google/devtools/mobileharness/infra/controller/plugin/subprocess/worker:plugin_worker_main_lib",
    ],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/devtools/mobileharness/api/query/proto:device_query_java_proto",
        "//src/devtools/mobileharness/infra/controller/plugin/proto:plugin_worker_java_grpc",
        "//src/devtools/mobileharness/infra/controller/plugin/proto:plugin_worker_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/allocation",
        "//src/java/com/google/devtools/mobileharness/api/model/job:locator",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:locator",
        "//src/java/com/google/devtools/mobileharness/api/testrunner/event/test",
        "//src/java/com/google/devtools/mobileharness/infra/controller/plugin/subprocess:delta_applier",
        "//src/java/com/google/devtools/mobileharness/infra/controller/plugin/subprocess:loader",
        "//src/java/com/google/devtools/mobileharness/infra/controller/plugin/subprocess:snapshotter",
        "//src/java/com/google/devtools/mobileharness/infra/controller/plugin/subprocess:subscriber",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/event:local_event_impl",
        "//src/java/com/google/devtools/mobileharness/shared/util/system",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:no_op_device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/controller/event:test",
        "//src/java/com/google/wireless/qa/mobileharness/shared/controller/plugin",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/allocation",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/lab",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_java_proto",
        "//src/javatests/com/google/devtools/mobileharness/builddefs:truth",
        "//src/javatests/com/google/devtools/mobileharness/shared/util/runfiles:runfiles_util",
        "@grpc-java//core:inprocess",
        "@grpc-java//netty",
        "@grpc-java//stub",
        "@grpc-java//testing",
        "@maven//:com_google_guava_guava",
        "@maven//:junit_junit",
    ],
)

junit_test_suites(
    name = "tests",
    sizes = [
        "small",
    ],
    deps = [":subprocess"],
)
