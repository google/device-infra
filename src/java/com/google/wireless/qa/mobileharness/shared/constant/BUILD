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

# Description:
#   Shared constants used by different parts of MobileHarness.

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = ["//visibility:public"],
)

java_library(
    name = "dir",
    srcs = ["DirCommon.java"],
    deps = [
        ":gen_dir",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
    ],
)

java_library(
    name = "gen_dir",
    srcs = ["GenDir.java"],
)

java_library(
    name = "property",
    srcs = ["PropertyName.java"],
)

java_library(
    name = "dimension",
    srcs = ["Dimension.java"],
    deps = [
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "error_code",
    srcs = ["ErrorCode.java"],
)

java_library(
    name = "exit_code",
    srcs = ["ExitCode.java"],
    deps = ["//src/java/com/google/devtools/mobileharness/shared/util/system/constant:exit_code"],
)
