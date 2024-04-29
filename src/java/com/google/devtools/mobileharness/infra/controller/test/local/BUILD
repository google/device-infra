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
    name = "local_test_runner",
    srcs = [
        "LocalTestRunner.java",
    ],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode/local:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/lab/controller:__pkg__",
    ],
    deps = [
        ":local_test_flow",
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/devtools/mobileharness/api/query/proto:device_query_java_proto",
        "//src/devtools/mobileharness/api/query/proto:lab_query_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/allocation",
        "//src/java/com/google/devtools/mobileharness/api/testrunner/event/test",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:abstract_test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:base_test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:direct_test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:direct_test_runner_setting",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/local/annotation",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/local/utp/controller:test_flow_converter",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/model:test_execution_result",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api:base",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:property",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/allocation",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job/util:job_info_util",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_java_proto",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "local_test_flow",
    srcs = ["LocalTestFlow.java"],
    visibility = [
    ],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/devtools/mobileharness/api/query/proto:device_query_java_proto",
        "//src/devtools/mobileharness/api/query/proto:lab_query_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/allocation",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/testrunner/event/test",
        "//src/java/com/google/devtools/mobileharness/infra/controller/plugin:lab_common_setup_module",
        "//src/java/com/google/devtools/mobileharness/infra/controller/plugin:plugin_creator",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:direct_test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/local/utp/controller:test_flow_converter",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/util:test_command_history_saver",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/util/atsfileserveruploader:ats_file_server_uploader_plugin",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/util/xtsdownloader:mcts_dynamic_download_plugin",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/plugin:nontradefed_report_generator",
        "//src/java/com/google/devtools/mobileharness/platform/testbed/adhoc/controller:adhoc_testbed_driver",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:concurrency_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:tag",
        "//src/java/com/google/devtools/mobileharness/shared/util/message:str_pair",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api:base",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dimension",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:error_code",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:property",
        "//src/java/com/google/wireless/qa/mobileharness/shared/controller/event:test",
        "//src/java/com/google/wireless/qa/mobileharness/shared/controller/plugin",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/allocation",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:common_java_proto",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)
