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

package_group(
    name = "flags_visibility",
    includes = [
        "//:deviceinfra_all_pkg",
        "//:omnilab_fusion",
    ],
    packages = [
        "//java/com/google/devtools/mobileharness/platform/maneki/...",
        "//javatests/com/google/devtools/mobileharness/platform/maneki/...",
    ],
)

java_library(
    name = "flags",
    srcs = [
        "Flags.java",
    ],
    visibility = [":flags_visibility"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/flags/core:flag",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags/core/ext:duration_flag",
        "@maven//:com_google_guava_guava",
    ],
)
