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

load("//src/javatests/com/google/devtools/deviceinfra/builddefs:junit_test_suites.bzl", "junit_test_suites")

package(default_applicable_licenses = ["//:license"])

java_library(
    name = "command",
    testonly = True,
    srcs = glob(
        ["*.java"],
        exclude = ["ConsoleCommandTestModule.java"],
    ),
    deps = [
        ":console_command_test_module",
        "//src/java/com/google/devtools/atsconsole:console_info",
        "//src/java/com/google/devtools/atsconsole:console_util",
        "//src/java/com/google/devtools/atsconsole:guice_factory",
        "//src/java/com/google/devtools/atsconsole:test_util",
        "//src/java/com/google/devtools/atsconsole/command",
        "//src/java/com/google/devtools/atsconsole/result/xml:xml_result_formatter",
        "//src/java/com/google/devtools/atsconsole/result/xml:xml_result_util",
        "//src/java/com/google/devtools/atsconsole/testbed/config:yaml_testbed_updater",
        "//src/java/com/google/devtools/mobileharness/platform/android/sdktool/adb:adb_internal_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/sdktool/adb:enums",
        "//src/java/com/google/devtools/mobileharness/platform/testbed/mobly/util:mobly_aosp_test_setup_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/command",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/javatests/com/google/devtools/deviceinfra/builddefs:truth",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_extensions_guice_testlib",
        "@maven//:com_google_inject_guice",
        "@maven//:info_picocli_picocli",
        "@maven//:junit_junit",
        "@maven//:org_mockito_mockito_core",
    ],
)

java_library(
    name = "console_command_test_module",
    srcs = ["ConsoleCommandTestModule.java"],
    deps = [
        "//src/java/com/google/devtools/atsconsole:console_info",
        "@maven//:com_google_inject_guice",
    ],
)

junit_test_suites(
    name = "tests",
    sizes = [
        "small",
    ],
    deps = [":command"],
)
