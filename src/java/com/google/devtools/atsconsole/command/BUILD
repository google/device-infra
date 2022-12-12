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
    name = "command",
    srcs = [
        "ExitCommand.java",
        "RootCommand.java",
        "RunCommand.java",
        "SetCommand.java",
    ],
    visibility = [
        "//src/java/com/google/devtools/atsconsole:__pkg__",
        "//src/javatests/com/google/devtools/atsconsole/command:__subpackages__",
    ],
    deps = [
        "//src/java/com/google/devtools/atsconsole:apache_commons_io",
        "//src/java/com/google/devtools/atsconsole:console_info",
        "//src/java/com/google/devtools/atsconsole:console_util",
        "//src/java/com/google/devtools/atsconsole/result/xml:mobly_result_info",
        "//src/java/com/google/devtools/atsconsole/result/xml:xml_result_formatter",
        "//src/java/com/google/devtools/atsconsole/result/xml:xml_result_util",
        "//src/java/com/google/devtools/atsconsole/testbed/config:yaml_testbed_updater",
        "//src/java/com/google/devtools/deviceinfra/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/deviceinfra/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/platform/android/sdktool/adb:adb_internal_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/sdktool/adb:enums",
        "//src/java/com/google/devtools/mobileharness/platform/testbed/mobly/util:mobly_aosp_test_setup_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/command",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "@maven//:com_google_guava_guava",
        "@maven//:info_picocli_picocli",
        "@maven//:javax_inject_jsr330_api",
    ],
)
