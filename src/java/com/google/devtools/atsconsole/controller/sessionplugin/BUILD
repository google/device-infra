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
    name = "ats_session_plugin",
    srcs = ["AtsSessionPlugin.java"],
    visibility = [
        "//src/java/com/google/devtools/atsconsole/controller:__subpackages__",
    ],
    deps = [
        ":dump_env_var_command_handler",
        ":dump_stack_trace_command_handler",
        ":dump_uptime_command_handler",
        ":list_devices_command_handler",
        ":list_modules_command_handler",
        ":run_command_handler",
        "//src/devtools/atsconsole/controller/proto:session_plugin_java_proto",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:session_java_proto",
        "//src/java/com/google/devtools/deviceinfra/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/model:session_event",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/model:session_info",
        "@com_google_protobuf//:protobuf_java",
        "@com_google_protobuf_protobuf_java",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "dump_env_var_command_handler",
    srcs = ["DumpEnvVarCommandHandler.java"],
    deps = [
        "//src/devtools/atsconsole/controller/proto:session_plugin_java_proto",
    ],
)

java_library(
    name = "dump_stack_trace_command_handler",
    srcs = ["DumpStackTraceCommandHandler.java"],
    deps = [
        "//src/devtools/atsconsole/controller/proto:session_plugin_java_proto",
    ],
)

java_library(
    name = "dump_uptime_command_handler",
    srcs = ["DumpUptimeCommandHandler.java"],
    deps = [
        "//src/devtools/atsconsole/controller/proto:session_plugin_java_proto",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice:annotations",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:time_util",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "list_devices_command_handler",
    srcs = ["ListDevicesCommandHandler.java"],
    visibility = ["//src/javatests/com/google/devtools/atsconsole/controller/sessionplugin:__pkg__"],
    deps = [
        "//src/devtools/atsconsole/controller/proto:device_descriptor_java_proto",
        "//src/devtools/atsconsole/controller/proto:session_plugin_java_proto",
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/devtools/mobileharness/api/query/proto:device_query_java_proto",
        "//src/java/com/google/devtools/deviceinfra/ext/devicemanagement/device/platform/android/realdevice:android_real_device_constants",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/controller/device:querier",
        "//src/java/com/google/devtools/mobileharness/shared/util/base:table_formatter",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dimension",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "list_modules_command_handler",
    srcs = ["ListModulesCommandHandler.java"],
    visibility = ["//src/javatests/com/google/devtools/atsconsole/controller/sessionplugin:__pkg__"],
    deps = [
        "//src/devtools/atsconsole/controller/proto:session_plugin_java_proto",
        "//src/devtools/mobileharness/platform/android/xts/proto:configuration_java_proto",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/config:configuration_util",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "run_command_handler",
    srcs = ["RunCommandHandler.java"],
    visibility = ["//src/javatests/com/google/devtools/atsconsole/controller/sessionplugin:__pkg__"],
    deps = [
        "//src/devtools/atsconsole/controller/proto:session_plugin_java_proto",
        "//src/java/com/google/devtools/deviceinfra/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/model:session_info",
        "//src/java/com/google/devtools/mobileharness/platform/android/sdktool/adb:adb_internal_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/jobconfig:job_info_creator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_config_java_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_java_proto",
        "@com_google_protobuf//:protobuf_java",
        "@maven//:com_google_code_gson_gson",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "plugin_output_printer",
    srcs = ["PluginOutputPrinter.java"],
    visibility = ["//src/java/com/google/devtools/atsconsole/command:__pkg__"],
    deps = [
        "//src/devtools/atsconsole/controller/proto:session_plugin_java_proto",
        "//src/java/com/google/devtools/atsconsole:console_util",
        "//src/java/com/google/devtools/deviceinfra/shared/util/logging:google_logger",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
        "@maven//:info_picocli_picocli",
    ],
)
