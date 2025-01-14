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

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/command:__pkg__",
    ],
)

java_library(
    name = "map_entry_preprocessor",
    srcs = ["MapEntryPreprocessor.java"],
    deps = [
        "@maven//:info_picocli_picocli",
    ],
)

java_library(
    name = "map_preprocessor",
    srcs = ["MapPreprocessor.java"],
    deps = [
        ":map_entry_preprocessor",
        "@maven//:info_picocli_picocli",
    ],
)

java_library(
    name = "multimap_preprocessor",
    srcs = ["MultimapPreprocessor.java"],
    deps = [
        ":map_entry_preprocessor",
        "@maven//:com_google_guava_guava",
        "@maven//:info_picocli_picocli",
    ],
)
