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

package(default_applicable_licenses = ["//:license"])

java_library(
    name = "command",
    srcs = [
        "DumpCommand.java",
        "ExitCommand.java",
        "ListCommand.java",
        "LogCommand.java",
        "RootCommand.java",
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
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:session_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/rpc/grpc",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common/olcserver:annotations",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common/olcserver:server_preparer",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console:console_info",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/olcserver:ats_session_stub",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/olcserver:server_log_printer",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/sessionplugin:ats_session_plugin_config_output",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/sessionplugin:plugin_output_printer",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/command:command_helper",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/console:console_util",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/log:log_dumper",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/plan:plan_lister",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/result:result_lister",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/subplan:sub_plan_lister",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/version:version_message_util",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/rpc/stub:control_stub",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/params:module_parameters",
        "//src/java/com/google/devtools/mobileharness/shared/util/base",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/path",
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
        "//src/java/com/google/devtools/mobileharness/infra/ats/server/sessionplugin:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/console/command:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/console/command/run:__pkg__",
    ],
    deps = [
        "//src/devtools/mobileharness/infra/ats/console/controller/proto:session_plugin_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common:session_request_info",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common/olcserver:server_preparer",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console:annotations",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console:console_info",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/olcserver:ats_session_stub",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/olcserver:server_log_printer",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/sessionplugin:plugin_output_printer",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/xml:mobly_result_info",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/xml:xml_result_formatter",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/xml:xml_result_util",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/testbed/config:yaml_testbed_updater",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/command:command_helper",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/console:console_util",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/subplan:sub_plan_lister",
        "//src/java/com/google/devtools/mobileharness/platform/android/sdktool/adb:adb_internal_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/sdktool/adb:enums",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/retry:retry_type",
        "//src/java/com/google/devtools/mobileharness/platform/testbed/mobly/util:mobly_aosp_package_test_setup_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/shared/util/command",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/path",
        "//src/java/com/google/devtools/mobileharness/shared/util/path:file_name_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/system",
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
