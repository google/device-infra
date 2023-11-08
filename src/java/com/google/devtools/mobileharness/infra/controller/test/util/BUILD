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

java_library(
    name = "test_command_history_saver",
    srcs = [
        "TestCommandHistorySaver.java",
    ],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/local:__pkg__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/allocation",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:locator",
        "//src/java/com/google/devtools/mobileharness/api/testrunner/event/test",
        "//src/java/com/google/devtools/mobileharness/shared/util/command/history",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "job_timer",
    srcs = [
        "JobTimer.java",
    ],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/controller/scheduler:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/model:__pkg__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/job/in",
        "//src/java/com/google/devtools/mobileharness/api/model/job/out:timing",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:count_down_timer",
    ],
)

java_library(
    name = "test_runner_timing",
    srcs = [
        "TestRunnerTiming.java",
    ],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/container/controller:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:__pkg__",
    ],
)

java_library(
    name = "exception_handler",
    srcs = ["SubscriberExceptionLoggingHandler.java"],
    visibility = [
        "//:deviceinfra_all_pkg",
    ],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:test_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/testrunner/event/test:base",
        "//src/java/com/google/devtools/mobileharness/api/testrunner/plugin:exception",
        "//src/java/com/google/devtools/mobileharness/shared/util/event:event_bus",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/client/api/event:job",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:error_code",
        "//src/java/com/google/wireless/qa/mobileharness/shared/controller/event:test",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)
