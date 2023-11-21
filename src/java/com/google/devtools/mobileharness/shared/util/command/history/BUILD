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
    name = "history",
    srcs = [
        "CommandHistory.java",
        "CommandRecord.java",
        "CommandRecorderListener.java",
    ],
    visibility = [
        "//src/java/com/google/devtools/deviceaction/common/utils:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/util:__pkg__",
        "//src/java/com/google/devtools/mobileharness/shared/util/command:__pkg__",
        "//src/javatests/com/google/devtools/deviceaction/common/utils:__pkg__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/shared/util/command:command_results",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "recorder",
    srcs = [
        "CommandRecorder.java",
    ],
    visibility = [
        # Do not add other packages here. Please use CommandExecutor instead.
        "//src/java/com/google/devtools/deviceaction/framework/actions:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/lab:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/shared/util/command:__pkg__",
    ],
    deps = [
        ":history",
        "//src/java/com/google/devtools/mobileharness/shared/util/command:command_results",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
    ],
)
