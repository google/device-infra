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
        "//src/java/com/google/devtools/atsconsole:__subpackages__",
        "//src/javatests/com/google/devtools/atsconsole:__subpackages__",
    ],
)

java_library(
    name = "annotations",
    srcs = ["Annotations.java"],
    deps = ["@maven//:javax_inject_jsr330_api"],
)

java_binary(
    name = "atsconsole",
    main_class = "com.google.devtools.atsconsole.AtsConsole",
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
        "//src/java/com/google/devtools/atsconsole/command",
        "//src/java/com/google/devtools/deviceinfra/shared/util/concurrent:callables",
        "//src/java/com/google/devtools/deviceinfra/shared/util/flags",
        "//src/java/com/google/devtools/deviceinfra/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/deviceinfra/shared/util/shell",
        "//src/java/com/google/devtools/deviceinfra/shared/util/time",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:future",
        "@maven//:com_google_code_findbugs_jsr305",
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
        ":console_info",
        "//src/java/com/google/devtools/atsconsole/controller/olcserver:module",
        "//src/java/com/google/devtools/deviceinfra/shared/util/concurrent:thread_factory_util",
        "//src/java/com/google/devtools/deviceinfra/shared/util/time",
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
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "console_util",
    srcs = ["ConsoleUtil.java"],
    deps = [
        ":annotations",
        ":apache_commons_io",
        "@maven//:com_google_code_findbugs_jsr305",
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
    name = "test_util",
    srcs = ["TestUtil.java"],
    deps = [":console_info"],
)

java_library(
    name = "apache_commons_io",
    exports = [
        "@maven//:commons_io_commons_io",
    ],
)
