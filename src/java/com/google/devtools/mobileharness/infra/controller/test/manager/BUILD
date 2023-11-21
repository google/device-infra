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
    name = "manager",
    srcs = [
        "TestManager.java",
    ],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/client:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/lab:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/client:__subpackages__",
    ],
    exports = [
        ":exception",
    ],
    deps = [
        ":exception",
        "//src/java/com/google/devtools/mobileharness/api/model/allocation",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/job:locator",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:locator",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/model:test_execution_unit",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/system",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:sleeper",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:time_utils",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:error_code",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "test_message_poster_util",
    srcs = ["TestMessagePosterUtil.java"],
    visibility = [
        "//java/com/google/devtools/mobileharness/infra/client/api/controller/job:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/lab:__subpackages__",
    ],
    deps = [
        ":manager",
        "//src/java/com/google/devtools/mobileharness/infra/container/controller:proxy_test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/container/controller:proxy_to_direct_test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:direct_test_runner",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/messaging/poster",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
    ],
)

java_library(
    name = "proxy_test_manager",
    srcs = ["ProxyTestManager.java"],
    visibility = [
        "//java/com/google/devtools/deviceinfra/host/utrs:__subpackages__",
        "//javatests/com/google/devtools/deviceinfra/host/utrs:__subpackages__",
        "//javatests/com/google/devtools/mobileharness/infra/controller/test/manager:__subpackages__",
        "//javatests/com/google/devtools/mobileharness/infra/lab:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/lab:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/lab/rpc/service:__subpackages__",
    ],
    exports = [
        ":exception",
    ],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/job/in",
        "//src/java/com/google/devtools/mobileharness/infra/container/controller:proxy_test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/container/controller:proxy_to_direct_test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:abstract_test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:direct_test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/manager",
        "//src/java/com/google/devtools/mobileharness/infra/lab/controller:lab_direct_test_runner_holder",
        "//src/java/com/google/devtools/mobileharness/infra/lab/controller/util:lab_file_notifier",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
    ],
)

java_library(
    name = "exception",
    srcs = ["TestStartedException.java"],
    visibility = [
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
    ],
)
