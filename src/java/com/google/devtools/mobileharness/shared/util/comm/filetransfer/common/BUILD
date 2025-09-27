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
        "//src/java/com/google/devtools/mobileharness/shared:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/shared:__subpackages__",
    ],
)

java_library(
    name = "file_handlers",
    srcs = ["FileHandlers.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_protobuf_protobuf_java",
        "@protobuf//:protobuf_java",
    ],
)

java_library(
    name = "tagged_file_handler",
    srcs = ["TaggedFileHandler.java"],
    visibility = [
        "//java/com/google/wireless/qa/mobileharness/shared/comm/filetransfer/stubby/service:__pkg__",
        "//src/java/com/google/devtools/mobileharness:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/shared/util/comm/filetransfer:__subpackages__",
    ],
    deps = [
        ":file_handlers",
        "//src/devtools/mobileharness/shared/util/comm/filetransfer/common/proto:tagged_file_metadata_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/wireless/qa/mobileharness/shared/comm/filetransfer",
        "@maven//:com_google_code_findbugs_jsr305",
    ],
)
