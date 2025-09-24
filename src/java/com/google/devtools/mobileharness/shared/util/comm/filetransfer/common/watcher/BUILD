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
        "//:deviceinfra_all_pkg",
        "//javatests/com/google/geo/engprod/mobileharness:__subpackages__",
    ],
)

java_library(
    name = "file_transfer_event",
    srcs = ["FileTransferEvent.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
    ],
)

java_library(
    name = "file_transfer_watcher",
    srcs = ["FileTransferWatcher.java"],
    deps = [
        ":file_transfer_event",
        "//src/java/com/google/devtools/mobileharness/shared/util/base",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:time_utils",
    ],
)

java_library(
    name = "watchable_file_transfer_client",
    srcs = ["WatchableFileTransferClient.java"],
    deps = [
        ":file_transfer_event",
        ":file_transfer_watcher",
        "//src/java/com/google/devtools/mobileharness/shared/constant/closeable:mobile_harness_auto_closeable",
        "//src/java/com/google/wireless/qa/mobileharness/shared/comm/filetransfer",
        "@maven//:com_google_guava_guava",
    ],
)
