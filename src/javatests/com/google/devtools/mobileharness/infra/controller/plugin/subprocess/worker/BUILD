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
    name = "worker",
    srcs = glob(["*.java"]),
    deps = [
        "//src/devtools/mobileharness/infra/controller/plugin/proto:plugin_worker_java_grpc",
        "//src/devtools/mobileharness/infra/controller/plugin/proto:plugin_worker_java_proto",
        "//src/java/com/google/devtools/mobileharness/infra/controller/plugin/subprocess/worker:plugin_worker_service_impl",
        "//src/java/com/google/devtools/mobileharness/infra/controller/plugin/subprocess/worker:worker_event_dispatcher",
        "//src/java/com/google/wireless/qa/mobileharness/shared/controller/event:test",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_java_proto",
        "//src/javatests/com/google/devtools/mobileharness/builddefs:truth",
        "@grpc-java//core:inprocess",
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
    deps = [":worker"],
)
