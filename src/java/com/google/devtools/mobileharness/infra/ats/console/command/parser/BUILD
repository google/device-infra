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
    name = "command_line_parser",
    srcs = ["CommandLineParser.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/server/sessionplugin:__pkg__",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/retry:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/console/command/parser:__pkg__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common:session_request_info",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console:ats_console_module",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console:guice_factory",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/command:run_command",
        "//src/java/com/google/devtools/mobileharness/shared/util/shell",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@maven//:info_picocli_picocli",
    ],
)
