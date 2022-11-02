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
    name = "direct_test_runner",
    srcs = [
        "DirectTestRunner.java",
        "PluginLoadingResult.java",
    ],
    visibility = [
        "//src/java/com/google/devtools/deviceinfra/infra/client:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/launcher:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/local:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/manager:__pkg__",
        "//src/javatests/com/google/devtools/deviceinfra/infra/client:__subpackages__",
    ],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/java/com/google/devtools/deviceinfra/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:test_runner",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/messaging/poster",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "direct_test_runner_setting",
    srcs = [
        "DirectTestRunnerSetting.java",
    ],
    visibility = [
        "//src/java/com/google/devtools/deviceinfra/infra/client:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/local:__pkg__",
        "//src/javatests/com/google/devtools/deviceinfra/infra/client:__subpackages__",
    ],
    deps = [
        "//src/java/com/google/devtools/deviceinfra/shared/util/auto:auto_value",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/allocation",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "test_runner",
    srcs = [
        "TestRunner.java",
    ],
    visibility = [
        "//src/java/com/google/devtools/deviceinfra/infra/client/api/mode/local:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:__subpackages__",
        "//src/javatests/com/google/devtools/deviceinfra/infra/client/api/mode/local:__subpackages__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/allocation",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/model:test_execution_unit",
    ],
)

java_library(
    name = "test_context",
    srcs = ["TestContext.java"],
    visibility = [
        "//:deviceinfra_pkg",
    ],
    deps = [
        "//src/java/com/google/devtools/deviceinfra/shared/util/auto:auto_value",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job:locator",
        "@maven//:com_google_code_findbugs_jsr305",
    ],
)
