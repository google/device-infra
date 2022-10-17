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
        "//javatests/com/google/devtools/mobileharness/infra/controller/test:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/local:__pkg__",
    ],
    deps = [
        "//java/com/google/common/eventbus",
        "//java/com/google/devtools/mobileharness/api/testrunner/event/test",
        "//java/com/google/devtools/mobileharness/infra/container/util:container_env_var",
        "//src/java/com/google/devtools/deviceinfra/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/api/model/allocation",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:locator",
        "//src/java/com/google/devtools/mobileharness/shared/util/command/history",
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
        "//java/com/google/devtools/mobileharness/infra/controller/scheduler:__subpackages__",
        "//javatests/com/google/devtools/mobileharness/infra/controller/test:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/model:__pkg__",
    ],
    deps = [
        "//java/com/google/devtools/mobileharness/shared/util/time:count_down_timer",
        "//src/java/com/google/devtools/deviceinfra/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/job/in",
        "//src/java/com/google/devtools/mobileharness/api/model/job/out:timing",
    ],
)

java_library(
    name = "test_runner_timing",
    srcs = [
        "TestRunnerTiming.java",
    ],
    visibility = [
        "//java/com/google/devtools/mobileharness/infra/container/controller:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:__pkg__",
    ],
)

java_library(
    name = "decorator_span_tracker",
    srcs = [
        "DecoratorSpanTracker.java",
    ],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/local:__pkg__",
    ],
    deps = [
        "//devtools/mobileharness/shared/trace/proto:span_java_proto",
        "//google/protobuf:timestamp_java_proto",
        "//java/com/google/common/eventbus",
        "//java/com/google/devtools/mobileharness/api/testrunner/event/test",
        "//java/com/google/devtools/mobileharness/api/testrunner/plugin:annotation",
        "//java/com/google/devtools/mobileharness/shared/trace",
        "//src/java/com/google/wireless/qa/mobileharness/shared/controller/plugin",
    ],
)
