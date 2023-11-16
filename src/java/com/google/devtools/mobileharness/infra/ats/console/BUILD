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

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/console:__subpackages__",
    ],
)

java_library(
    name = "annotations",
    srcs = ["Annotations.java"],
    deps = ["@maven//:javax_inject_jsr330_api"],
)

java_binary(
    name = "ats_console",
    main_class = "com.google.devtools.mobileharness.infra.ats.console.AtsConsole",
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
        ":console_util",
        ":guice_factory",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/command",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/olcserver:server_log_printer",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/olcserver:server_preparer",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/console:console_reader_output_stream",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:callables",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:more_futures",
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
    deps = [
        ":annotations",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/olcserver:module",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/report:compatibility_report_module",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:thread_factory_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:sleeper",
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
    name = "console_util",
    srcs = ["ConsoleUtil.java"],
    deps = [
        ":annotations",
        ":apache_commons_io",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "guice_factory",
    srcs = ["GuiceFactory.java"],
    deps = [
        "@maven//:com_google_inject_guice",
        "@maven//:info_picocli_picocli",
    ],
)

java_library(
    name = "apache_commons_io",
    exports = [
        "@maven//:commons_io_commons_io",
    ],
)
