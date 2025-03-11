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

package(default_applicable_licenses = ["//:license"])

java_library(
    name = "command",
    srcs = [
        "DumpCommand.java",
        "ExitCommand.java",
        "InvocationCommand.java",
        "KillCommand.java",
        "ListCommand.java",
        "LogCommand.java",
        "RemoveCommand.java",
        "RootCommand.java",
        "ServerCommand.java",
        "SetCommand.java",
        "VersionCommand.java",
    ],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/console/command:__subpackages__",
    ],
    deps = [
        ":add_command",
        ":run_command",
        "//src/devtools/mobileharness/infra/ats/console/controller/proto:session_plugin_java_proto",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:control_service_java_proto",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:session_service_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/rpc/grpc",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common/olcserver:annotations",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common/olcserver:server_preparer",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common/plan:plan_config_util",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console:console_info",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/constant:dirs",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/olcserver:ats_session_stub",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/olcserver:server_log_printer",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/sessionplugin:ats_session_plugin_config_output",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/sessionplugin:plugin_output_printer",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/command:command_helper",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/console:console_util",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/log:log_dumper",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/plan:plan_helper",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/result:result_lister",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/subplan:sub_plan_lister",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/version:version_message_util",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/constant:dirs",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/rpc/stub:control_stub",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/params:module_parameters",
        "//src/java/com/google/devtools/mobileharness/shared/constant:log_record_importance",
        "//src/java/com/google/devtools/mobileharness/shared/util/base",
        "//src/java/com/google/devtools/mobileharness/shared/util/base:proto_text_format",
        "//src/java/com/google/devtools/mobileharness/shared/util/command",
        "//src/java/com/google/devtools/mobileharness/shared/util/error:more_throwables",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/path",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:sleeper",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dir",
        "@maven//:com_google_guava_guava",
        "@maven//:info_picocli_picocli",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "run_command",
    srcs = [
        "RunCommand.java",
    ],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/command/parser:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/console/command:__subpackages__",
    ],
    deps = [
        "//src/devtools/mobileharness/infra/ats/console/controller/proto:session_plugin_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common:session_request_info",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common/olcserver:server_preparer",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console:annotations",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console:console_info",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/command/picocli/parameterpreprocessor:map_preprocessor",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/command/picocli/parameterpreprocessor:multimap_preprocessor",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/olcserver:ats_session_stub",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/olcserver:server_log_printer",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/sessionplugin:plugin_output_printer",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/report:certification_suite_info_factory",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/command:command_helper",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/console:console_util",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/subplan:sub_plan_lister",
        "//src/java/com/google/devtools/mobileharness/platform/android/shared/constant:splitters",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/common/util:xts_command_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/common/util:xts_dir_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite:module_arg",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/retry:retry_type",
        "//src/java/com/google/devtools/mobileharness/shared/util/command",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_guava_guava",
        "@maven//:info_picocli_picocli",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "add_command",
    srcs = ["AddCommand.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console:console_info",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/command:command_helper",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/console:console_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/subplan:add_sub_plan_args",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/subplan:result_type",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/subplan:sub_plan_creator",
        "@maven//:com_google_guava_guava",
        "@maven//:info_picocli_picocli",
        "@maven//:javax_inject_jsr330_api",
    ],
)
