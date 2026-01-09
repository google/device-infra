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

java_library(
    name = "manager",
    srcs = [
        "TestManager.java",
    ],
    visibility = [
        "//:omnilab_fusion",
        "//src/java/com/google/devtools/mobileharness/infra/client:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/lab:__subpackages__",
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
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "direct_test_runner_util",
    srcs = ["DirectTestRunnerUtil.java"],
    visibility = ["//src/java/com/google/devtools/mobileharness/infra/client/api/controller/job:__pkg__"],
    deps = [
        ":manager",
        "//src/devtools/mobileharness/api/messaging:messaging_java_proto",
        "//src/java/com/google/devtools/mobileharness/infra/controller/messaging:message_sender",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:direct_test_runner",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/messaging/poster",
    ],
)

java_library(
    name = "lab_direct_test_runner_util",
    srcs = ["LabDirectTestRunnerUtil.java"],
    visibility = ["//src/java/com/google/devtools/mobileharness/infra/lab:__subpackages__"],
    deps = [
        ":manager",
        "//src/devtools/mobileharness/api/messaging:messaging_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/messaging:exception",
        "//src/java/com/google/devtools/mobileharness/infra/container/controller:proxy_test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/container/controller:proxy_to_direct_test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/controller/messaging:message_sender",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:direct_test_runner",
        "//src/java/com/google/devtools/mobileharness/shared/util/base:proto_text_format",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/messaging/poster",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
    ],
)

java_library(
    name = "proxy_test_manager",
    srcs = ["ProxyTestManager.java"],
    visibility = [
        "//:omnilab_fusion",
        "//java/com/google/devtools/deviceinfra/host/utrs:__subpackages__",
        "//javatests/com/google/devtools/deviceinfra/host/utrs:__subpackages__",
        "//javatests/com/google/devtools/mobileharness/infra/controller/test/manager:__subpackages__",
        "//javatests/com/google/devtools/mobileharness/infra/lab:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/lab:__subpackages__",
    ],
    exports = [
        ":exception",
    ],
    deps = [
        ":annotations",
        ":manager",
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/job/in",
        "//src/java/com/google/devtools/mobileharness/infra/container/controller:proxy_test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/container/controller:proxy_to_direct_test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:abstract_test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:direct_test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/lab/controller:lab_direct_test_runner_holder",
        "//src/java/com/google/devtools/mobileharness/infra/lab/controller/util:lab_file_notifier",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:callables",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:sleeper",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "annotations",
    srcs = ["Annotations.java"],
    deps = ["@maven//:javax_inject_jsr330_api"],
)

java_library(
    name = "test_manager_module",
    srcs = ["TestManagerModule.java"],
    visibility = [
        "//:omnilab_fusion",
        "//src/java/com/google/devtools/mobileharness/infra/lab:__subpackages__",
    ],
    deps = [
        ":annotations",
        ":manager",
        ":proxy_test_manager",
        "//src/java/com/google/devtools/mobileharness/infra/lab/controller:lab_direct_test_runner_holder",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:thread_pools",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "test_manager_service_module",
    srcs = [
        "TestManagerService.java",
        "TestManagerServiceModule.java",
    ],
    visibility = ["//:omnilab_fusion"],
    deps = [
        ":manager",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:service_module",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "exception",
    srcs = ["TestStartedException.java"],
    visibility = ["//visibility:private"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
    ],
)
