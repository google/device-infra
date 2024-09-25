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
#   Module to resolve file.

load("@rules_java//java:defs.bzl", "java_library")

package(
    default_visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/lab:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/lab/controller:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/lab/rpc/service:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/shared/file/resolver:__pkg__",
    ],
)

java_library(
    name = "file_resolver",
    srcs = ["FileResolver.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "abstract_file_resolver",
    srcs = ["AbstractFileResolver.java"],
    deps = [
        ":file_resolver",
        "//src/devtools/mobileharness/shared/trace/proto:span_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "cache_file_resolver",
    srcs = ["CacheFileResolver.java"],
    deps = [
        ":abstract_file_resolver",
        ":file_resolver",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/path",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "local_file_resolver",
    srcs = ["LocalFileResolver.java"],
    deps = [
        ":abstract_file_resolver",
        ":file_resolver",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/annotation:basic_annotation",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "ats_file_server_file_resolver",
    srcs = ["AtsFileServerFileResolver.java"],
    deps = [
        ":abstract_file_resolver",
        ":file_resolver",
        "//src/java/com/google/devtools/deviceinfra/shared/util/file/remote/constant:remote_file_type",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/command",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/path",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/annotation:basic_annotation",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)
