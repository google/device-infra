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
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/relay:__subpackages__",
        "//src/java/com/google/wireless/qa/mobileharness/client:__subpackages__",
    ],
)

java_library(
    name = "exception",
    srcs = ["JobCancelledException.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
    ],
)

java_library(
    name = "lab_server_locator",
    srcs = ["LabServerLocator.java"],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:lab_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/job:locator",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:locator",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
    ],
)

java_library(
    name = "remote_test_runner",
    srcs = ["RemoteTestRunner.java"],
    deps = [
        ":lab_server_locator",
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/devtools/mobileharness/api/model/proto:job_java_proto",
        "//src/devtools/mobileharness/api/query/proto:lab_query_java_proto",
        "//src/devtools/mobileharness/infra/client/api/proto:resource_federation_java_proto",
        "//src/devtools/mobileharness/infra/container/proto:mode_setting_java_proto",
        "//src/devtools/mobileharness/infra/container/proto:sandbox_setting_java_proto",
        "//src/devtools/mobileharness/infra/container/proto:test_engine_java_proto",
        "//src/devtools/mobileharness/infra/lab/proto:prepare_test_service_java_proto",
        "//src/devtools/mobileharness/shared/trace/proto:span_java_proto",
        "//src/devtools/mobileharness/shared/version/proto:version_java_proto",
        "//src/devtools/mobileharness/shared/version/proto:version_service_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/converter",
        "//src/java/com/google/devtools/common/metrics/stability/rpc:exception",
        "//src/java/com/google/devtools/common/metrics/stability/util:error_id_comparator",
        "//src/java/com/google/devtools/deviceinfra/shared/util/file/remote/constant:remote_file_type",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/job/in",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode/remote/util:lab_rpc_proto_converter",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/util/longevity",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:abstract_test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:base_test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:direct_test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:direct_test_runner_setting",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/launcher:thread_pool_test_runner_launcher",
        "//src/java/com/google/devtools/mobileharness/infra/lab/rpc/stub:exec_test",
        "//src/java/com/google/devtools/mobileharness/infra/lab/rpc/stub:prepare_test",
        "//src/java/com/google/devtools/mobileharness/shared/constant/closeable:mobile_harness_auto_closeable",
        "//src/java/com/google/devtools/mobileharness/shared/context:invocation_context_executors",
        "//src/java/com/google/devtools/mobileharness/shared/file/resolver:file_resolver",
        "//src/java/com/google/devtools/mobileharness/shared/util/base",
        "//src/java/com/google/devtools/mobileharness/shared/util/base:proto_text_format",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/messaging/message",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:thread_factory_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/path",
        "//src/java/com/google/devtools/mobileharness/shared/util/sharedpool:shared_pool_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/system",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:sleeper",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:timeout_util",
        "//src/java/com/google/devtools/mobileharness/shared/version",
        "//src/java/com/google/devtools/mobileharness/shared/version/rpc/stub",
        "//src/java/com/google/wireless/qa/mobileharness/client/api/util/stub",
        "//src/java/com/google/wireless/qa/mobileharness/lab/proto:exec_test_serv_java_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/comm/message:cacheable_test_message_handler",
        "//src/java/com/google/wireless/qa/mobileharness/shared/comm/message:test_message_manager",
        "//src/java/com/google/wireless/qa/mobileharness/shared/comm/message/event",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:property",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/allocation",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job/in/spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/lab:locator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_java_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/util:device",
        "@com_google_protobuf//:protobuf_java_util",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)
