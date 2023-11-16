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
        "RunCommand.java",
        "SetCommand.java",
    ],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/console/command:__subpackages__",
    ],
    deps = [
        "//src/devtools/mobileharness/infra/ats/console/controller/proto:session_plugin_java_proto",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:control_service_java_proto",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:session_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/rpc/grpc",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console:apache_commons_io",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console:console_info",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console:console_util",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/olcserver:annotations",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/olcserver:ats_session_stub",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/olcserver:server_log_printer",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/olcserver:server_preparer",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/sessionplugin:plugin_output_printer",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/xml:mobly_result_info",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/xml:xml_result_formatter",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/xml:xml_result_util",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/testbed/config:yaml_testbed_updater",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/plan:plan_lister",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/result:result_lister",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/rpc/stub:control_stub",
        "//src/java/com/google/devtools/mobileharness/platform/android/sdktool/adb:adb_internal_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/sdktool/adb:enums",
        "//src/java/com/google/devtools/mobileharness/platform/testbed/mobly/util:mobly_aosp_test_setup_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/shared/util/base",
        "//src/java/com/google/devtools/mobileharness/shared/util/command",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_guava_guava",
        "@maven//:info_picocli_picocli",
        "@maven//:javax_inject_jsr330_api",
    ],
)
