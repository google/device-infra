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

java_binary(
    name = "atsconsole",
    main_class = "com.google.devtools.atsconsole.AtsConsole",
    runtime_deps = [
        ":atsconsole_lib",
    ],
)

java_library(
    name = "atsconsole_lib",
    srcs = ["AtsConsole.java"],
    visibility = [
        "//src/javatests/com/google/devtools/atsconsole:__subpackages__",
    ],
    deps = [
        ":console_command_module",
        ":console_info",
        ":console_util",
        ":guice_factory",
        "//src/java/com/google/devtools/atsconsole/command",
        "//src/java/com/google/devtools/deviceinfra/shared/util/flags",
        "//src/java/com/google/devtools/deviceinfra/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/deviceinfra/shared/util/shell",
        "//src/java/com/google/devtools/deviceinfra/shared/util/time",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@maven//:info_picocli_picocli",
        "@maven//:org_jline_jline",
    ],
)

java_library(
    name = "console_info",
    srcs = ["ConsoleInfo.java"],
    visibility = [
        "//src/java/com/google/devtools/atsconsole/command:__subpackages__",
        "//src/javatests/com/google/devtools/atsconsole:__subpackages__",
    ],
    deps = [
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "guice_factory",
    srcs = ["GuiceFactory.java"],
    visibility = [
        "//src/javatests/com/google/devtools/atsconsole:__subpackages__",
    ],
    deps = [
        "@maven//:com_google_inject_guice",
        "@maven//:info_picocli_picocli",
    ],
)

java_library(
    name = "console_command_module",
    srcs = ["ConsoleCommandModule.java"],
    visibility = [
        "//visibility:private",  # Only private by automation, not intent. Owner may accept CLs adding visibility. See go/scheuklappen#explicit-private.
    ],
    deps = [
        ":console_info",
        "@maven//:com_google_inject_guice",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "console_util",
    srcs = ["ConsoleUtil.java"],
    visibility = [
        "//src/java/com/google/devtools/atsconsole/command:__pkg__",
        "//src/javatests/com/google/devtools/atsconsole:__subpackages__",
    ],
    deps = [
        ":apache_commons_io",
    ],
)

java_library(
    name = "apache_commons_io",
    visibility = [
        "//src/java/com/google/devtools/atsconsole/command:__pkg__",
    ],
    exports = [
        "@maven//:commons_io_commons_io",
    ],
)

java_library(
    name = "test_util",
    srcs = ["TestUtil.java"],
    visibility = [
        "//src/javatests/com/google/devtools/atsconsole:__subpackages__",
    ],
    deps = [":console_info"],
)
