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

DEFAULT_VISIBILITY = [
    "//src/java/com/google/devtools/mobileharness/infra/ats/console:__subpackages__",
    "//src/javatests/com/google/devtools/mobileharness/infra/ats/console:__subpackages__",
]

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = DEFAULT_VISIBILITY,
)

java_library(
    name = "annotations",
    srcs = ["Annotations.java"],
    deps = ["@maven//:javax_inject_jsr330_api"],
)

java_binary(
    name = "ats_console",
    main_class = "com.google.devtools.mobileharness.infra.ats.console.AtsConsole",
    visibility = DEFAULT_VISIBILITY + [
        "//src/javatests/com/google/devtools/mobileharness/shared/size:__subpackages__",
    ],
    runtime_deps = [
        ":ats_console_lib",
    ],
)

java_library(
    name = "ats_console_lib",
    srcs = ["AtsConsole.java"],
    deps = [
        ":annotations",
        ":ats_console_module",
        ":console_info",
        ":guice_factory",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:control_service_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/rpc/grpc",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common:device_infra_service_util",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common/olcserver:annotations",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common/olcserver:server_preparer",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/command",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/command/completer:command_completer",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/command/completer:command_completer_holder",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/command/preprocessor:command_preprocessor",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/constant:dirs",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/olcserver:server_log_printer",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/command:command_helper",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/console:console_util",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/log:log_dumper",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/notice:notice_message_util",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/version:version_message_util",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/rpc/stub:control_stub",
        "//src/java/com/google/devtools/mobileharness/shared/constant:log_record_importance",
        "//src/java/com/google/devtools/mobileharness/shared/constant/closeable",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:callables",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/shell",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:sleeper",
        "//src/java/com/google/wireless/qa/mobileharness/shared:log",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@maven//:info_picocli_picocli",
        "@maven//:javax_inject_jsr330_api",
        "@maven//:org_jline_jline",
    ],
)

java_library(
    name = "ats_console_module",
    srcs = ["AtsConsoleModule.java"],
    visibility = DEFAULT_VISIBILITY + [
        "//src/java/com/google/devtools/mobileharness/infra/ats/server:__subpackages__",
    ],
    deps = [
        ":annotations",
        ":console_info",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common:session_request_info",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common/olcserver:annotations",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common/olcserver:module",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common/olcserver:server_preparer",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/report:compatibility_report_module",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/command:command_helper",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/console:console_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/common/util:xts_command_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:thread_pools",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:sleeper",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@maven//:javax_inject_jsr330_api",
        "@maven//:org_jline_jline",
    ],
)

java_library(
    name = "console_info",
    srcs = ["ConsoleInfo.java"],
    deps = [
        ":annotations",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "guice_factory",
    srcs = ["GuiceFactory.java"],
    visibility = DEFAULT_VISIBILITY + [
        "//src/java/com/google/devtools/mobileharness/infra/ats/server:__subpackages__",
    ],
    deps = [
        "@maven//:com_google_inject_guice",
        "@maven//:info_picocli_picocli",
    ],
)

java_library(
    name = "apache_commons_io",
    visibility = [
        "//src/java/com/google/devtools/mobileharness/shared/util/path:__pkg__",
    ],
    exports = [
        "@maven//:commons_io_commons_io",
    ],
)
