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

# Description:
#   File transferring tools.
package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//:deviceinfra_all_pkg",
        "//devtools/mobileharness/project:sophont",
    ],
)

java_library(
    name = "filetransfer",
    srcs = [
        "FileCallback.java",
        "FileTransferClient.java",
        "FileTransferService.java",
    ],
    deps = [
        "//src/devtools/mobileharness/shared/util/comm/filetransfer/common/proto:file_info_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/constant/closeable:mobile_harness_auto_closeable",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/filetransfer/common/watcher:file_transfer_watcher",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
    ],
)
