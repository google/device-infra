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
    name = "direct_test_runner",
    srcs = [
        "DirectTestRunner.java",
        "PluginLoadingResult.java",
    ],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/client:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/container/controller:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/launcher:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/local:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/manager:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/lab/rpc/service:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/client/longrunningservice/controller:__pkg__",
    ],
    deps = [
        ":test_runner",
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/java/com/google/devtools/mobileharness/infra/controller/messaging:message_sender",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
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
        "//src/java/com/google/devtools/mobileharness/infra/client:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/local:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/lab/controller:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/lab/rpc/service:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/client/longrunningservice/controller:__pkg__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
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
        "//:omnilab_fusion",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode/local:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/container/controller:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/client/longrunningservice/controller:__pkg__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/allocation",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:locator",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/model:test_execution_unit",
    ],
)

java_library(
    name = "abstract_test_runner",
    srcs = [
        "AbstractTestRunner.java",
        "TestRunnerLauncher.java",
    ],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode/local:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode/remote:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/container/controller:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/lab:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/client/longrunningservice/controller:__pkg__",
    ],
    exports = [
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/exception",
    ],
    deps = [
        ":test_runner",
        "//src/java/com/google/devtools/mobileharness/api/model/allocation",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/exception",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/model:test_execution_result",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/model:test_execution_unit",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/util:test_runner_timing",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "base_test_runner",
    srcs = ["BaseTestRunner.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:__subpackages__",
    ],
    runtime_deps = [
        "//src/java/com/google/devtools/mobileharness/api/testrunner/plugin:skip_exception_factory",  # For package-visible method access.
    ],
    deps = [
        ":abstract_test_runner",
        ":direct_test_runner",
        ":direct_test_runner_setting",
        ":test_context",
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/devtools/mobileharness/api/model/proto:test_java_proto",
        "//src/devtools/mobileharness/api/query/proto:device_query_java_proto",
        "//src/devtools/mobileharness/api/query/proto:lab_query_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/allocation",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/testrunner/event/test:client_test_events",
        "//src/java/com/google/devtools/mobileharness/infra/controller/messaging:message_sender",
        "//src/java/com/google/devtools/mobileharness/infra/controller/messaging:message_subscriber_backend",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/exception",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/model:test_execution_result",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/util:exception_handler",
        "//src/java/com/google/devtools/mobileharness/shared/constant/closeable:mobile_harness_auto_closeable",
        "//src/java/com/google/devtools/mobileharness/shared/context:invocation_context",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/messaging/message",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/messaging/poster",
        "//src/java/com/google/devtools/mobileharness/shared/util/event:event_bus",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:tag",
        "//src/java/com/google/devtools/mobileharness/shared/util/sharedpool:shared_pool_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/system",
        "//src/java/com/google/wireless/qa/mobileharness/shared/comm/message:cacheable_test_message_handler",
        "//src/java/com/google/wireless/qa/mobileharness/shared/comm/message/event",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dimension",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:property",
        "//src/java/com/google/wireless/qa/mobileharness/shared/controller/event:client_test_events",
        "//src/java/com/google/wireless/qa/mobileharness/shared/controller/event/util:scoped_bus",
        "//src/java/com/google/wireless/qa/mobileharness/shared/controller/event/util:skip_information_handler",
        "//src/java/com/google/wireless/qa/mobileharness/shared/log:info_log_importance_scope",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/allocation",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job/util:result_util",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_java_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/util:device_info",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "device_feature_manager",
    srcs = ["DeviceFeatureManager.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/lab/controller:__pkg__",
    ],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
    ],
)

java_library(
    name = "test_info_manager",
    srcs = ["TestInfoManager.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/lab/controller:__pkg__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
    ],
)

java_library(
    name = "test_context",
    srcs = ["TestContext.java"],
    visibility = [
        "//:deviceinfra_pkg",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job:locator",
        "@maven//:com_google_code_findbugs_jsr305",
    ],
)
