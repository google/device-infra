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

DEFAULT_VISIBILITY = [
    "//:deviceinfra_all_pkg",
]

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = DEFAULT_VISIBILITY,
)

java_library(
    name = "file_transfer_client_factory",
    srcs = ["FileTransferClientFactory.java"],
    deps = [
        ":file_transfer_parameters",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/comm/filetransfer",
    ],
)

java_library(
    name = "file_transfer_client_factories",
    srcs = ["FileTransferClientFactories.java"],
    visibility = DEFAULT_VISIBILITY + [
    ],
    deps = [
        ":file_transfer_client_factory",
        ":file_transfer_parameters",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/filetransfer/cloud/client:cloud_file_transfer_client",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/filetransfer/cloud/rpc/stub:cloud_file_transfer_stub",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/comm/filetransfer",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "file_transfer_parameters",
    srcs = ["FileTransferParameters.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/filetransfer/cloud/common:file_transfer_constant",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
    ],
)

java_library(
    name = "file_transfer_parameters_factory",
    srcs = ["FileTransferParametersFactory.java"],
    deps = [
        ":file_transfer_parameters",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job/in",
        "@maven//:com_google_guava_guava",
    ],
)
