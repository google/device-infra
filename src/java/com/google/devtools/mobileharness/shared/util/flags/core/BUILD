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

load("@rules_java//java:defs.bzl", "java_library")

package(default_applicable_licenses = ["//:license"])

java_library(
    name = "flag",
    srcs = [
        "Flag.java",
        "FlagSpec.java",
    ],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/shared/util/flags:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/shared/util/flags/core:__pkg__",
    ],
    deps = [
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "flags_manager",
    srcs = ["FlagsManager.java"],
    visibility = ["//src/java/com/google/devtools/mobileharness/shared/util/flags:flags_visibility"],
    deps = [
        ":flag",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags/core/converter",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
        "@maven//:info_picocli_picocli",
    ],
)

java_library(
    name = "testing",
    testonly = 1,
    srcs = ["SetFlags.java"],
    visibility = ["//src/java/com/google/devtools/mobileharness/shared/util/flags:flags_visibility"],
    deps = [
        ":flag",
        ":flags_manager",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:junit_junit",
    ],
)
