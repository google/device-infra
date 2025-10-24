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

# Session plugin for ats server requests.

load("@rules_java//java:defs.bzl", "java_library")

package(default_applicable_licenses = ["//:license"])

java_library(
    name = "ats_server_session_plugin",
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/common/olcserver:__subpackages__",
    ],
    runtime_deps = [
        ":ats_server_session_plugin_lib",
        ":ats_server_session_plugin_module",
    ],
)

java_library(
    name = "ats_server_session_plugin_lib",
    srcs = ["AtsServerSessionPlugin.java"],
    visibility = ["//src/javatests/com/google/devtools/mobileharness/infra/ats/server/sessionplugin:__pkg__"],
    deps = [
        ":new_multi_command_request_handler",
        "//src/devtools/mobileharness/api/model/proto:test_java_proto",
        "//src/devtools/mobileharness/infra/ats/console/result/proto:result_java_proto",
        "//src/devtools/mobileharness/infra/ats/server/proto:service_java_proto",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:session_java_proto",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:session_service_java_proto",
        "//src/devtools/mobileharness/platform/android/xts/proto:test_message_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/job/out",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common:xts_property_name",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/model:session_event",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/model:session_info",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/model:with_proto",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/rpc/service:local_session_stub",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/common/util:xts_constants",
        "//src/java/com/google/devtools/mobileharness/shared/util/base:proto_text_format",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/client/api/event:job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/comm/message",
        "//src/java/com/google/wireless/qa/mobileharness/shared/controller/event:client_test_events",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_java_proto",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_protobuf_protobuf_java",
        "@maven//:javax_inject_jsr330_api",
        "@protobuf//:protobuf_java",
        "@protobuf//:protobuf_java_util",
    ],
)

java_library(
    name = "new_multi_command_request_handler",
    srcs = ["NewMultiCommandRequestHandler.java"],
    visibility = ["//src/javatests/com/google/devtools/mobileharness/infra/ats/server/sessionplugin:__pkg__"],
    deps = [
        "//src/devtools/mobileharness/infra/ats/common/proto:xts_common_java_proto",
        "//src/devtools/mobileharness/infra/ats/console/result/proto:report_java_proto",
        "//src/devtools/mobileharness/infra/ats/server/proto:service_java_proto",
        "//src/java/com/google/devtools/deviceinfra/shared/util/file/remote/constant:remote_file_type",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/error:exception_factory",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common:session_request_handler_util",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common:session_request_info",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common:session_result_handler_util",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common:xts_property_name",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common:xts_type_loader",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common/jobcreator:xts_job_creator",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/command/parser:command_line_parser",
        "//src/java/com/google/devtools/mobileharness/infra/ats/server/util:ats_server_session_util",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/constant:session_properties",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/model:session_info",
        "//src/java/com/google/devtools/mobileharness/infra/lab/common/dir",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/common/util:xts_constants",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/runtime:xts_tradefed_runtime_info",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/runtime:xts_tradefed_runtime_info_file_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite:test_suite_info_provider",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/shared/util/command",
        "//src/java/com/google/devtools/mobileharness/shared/util/error:more_throwables",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/path",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:sleeper",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:time_utils",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_java_proto",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
        "@protobuf//:protobuf_java_util",
    ],
)

java_library(
    name = "ats_server_session_plugin_module",
    srcs = ["AtsServerSessionPluginModule.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/common/jobcreator:server_job_creator",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common/jobcreator:xts_job_creator",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/report:compatibility_report_module",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:sleeper",
        "@maven//:com_google_inject_guice",
    ],
)

java_library(
    name = "tradefed_config_generator",
    srcs = ["TradefedConfigGenerator.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/common/jobcreator:__pkg__",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/server/sessionplugin:__pkg__",
    ],
    deps = [
        "//src/devtools/mobileharness/infra/ats/server/proto:service_java_proto",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:time_utils",
        "@maven//:com_google_guava_guava",
        "@maven//:xmlpull_xmlpull",
        "@protobuf//:protobuf_java",
        "@protobuf//:protobuf_java_util",
    ],
)
