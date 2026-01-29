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
        "//src/java/com/google/devtools/mobileharness/infra/client:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/client/longrunningservice/controller:__pkg__",
    ],
)

java_library(
    name = "job",
    srcs = [
        "JobManager.java",
        "JobRunner.java",
    ],
    deps = [
        ":pending_test_printer",
        "//src/devtools/common/metrics/stability/model/proto:error_java_proto",
        "//src/devtools/mobileharness/api/messaging:messaging_java_proto",
        "//src/devtools/mobileharness/api/model/proto:job_java_proto",
        "//src/devtools/mobileharness/api/model/proto:test_java_proto",
        "//src/devtools/mobileharness/api/query/proto:device_query_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/converter",
        "//src/java/com/google/devtools/mobileharness/api/messaging:exception",
        "//src/java/com/google/devtools/mobileharness/api/model/allocation",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/job:locator",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:locator",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/controller/allocation/allocator",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/controller/allocation/diagnostic",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/controller/allocation/diagnostic/multidevice",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/controller/allocation/diagnostic/singledevice",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/controller/device:querier",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode/remote:exception",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/plugin:test_retry_handler",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/util/result:client_alloc_error_util",
        "//src/java/com/google/devtools/mobileharness/infra/controller/messaging:message_sender",
        "//src/java/com/google/devtools/mobileharness/infra/controller/messaging:message_sender_finder",
        "//src/java/com/google/devtools/mobileharness/infra/controller/plugin:common_setup_module",
        "//src/java/com/google/devtools/mobileharness/infra/controller/plugin:plugin_creator",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:direct_test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:direct_test_runner_setting",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/event",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/manager",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/manager:direct_test_runner_util",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/util:exception_handler",
        "//src/java/com/google/devtools/mobileharness/shared/constant:log_record_importance",
        "//src/java/com/google/devtools/mobileharness/shared/constant/closeable:mobile_harness_auto_closeable",
        "//src/java/com/google/devtools/mobileharness/shared/context:invocation_context",
        "//src/java/com/google/devtools/mobileharness/shared/file/resolver:local_file_resolver",
        "//src/java/com/google/devtools/mobileharness/shared/util/algorithm:graph_matching",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/shared/util/base:proto_text_format",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/messaging/poster",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:callables",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:more_futures",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:thread_pools",
        "//src/java/com/google/devtools/mobileharness/shared/util/error:more_throwables",
        "//src/java/com/google/devtools/mobileharness/shared/util/event:event_bus",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:tag",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:sleeper",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:time_utils",
        "//src/java/com/google/wireless/qa/mobileharness/client/api/event:job",
        "//src/java/com/google/wireless/qa/mobileharness/client/api/event/internal",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator:job_checker",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dimension",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:property",
        "//src/java/com/google/wireless/qa/mobileharness/shared/controller/event/util:scoped_bus",
        "//src/java/com/google/wireless/qa/mobileharness/shared/controller/event/util:skip_information_handler",
        "//src/java/com/google/wireless/qa/mobileharness/shared/controller/plugin",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/allocation",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job/util:result_util",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_java_proto",
        "//third_party/android/robolectric_android_all:android_platform_internal_main",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
    ],
)

java_library(
    name = "pending_test_printer",
    srcs = ["PendingTestPrinter.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
    ],
)
