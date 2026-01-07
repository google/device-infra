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
)

java_library(
    name = "byte_array_list",
    srcs = ["ByteArrayList.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/tradefed:__pkg__",
    ],
    deps = [
        "@maven//:com_google_errorprone_error_prone_annotations",
    ],
)

java_library(
    name = "byte_array_util",
    srcs = ["ByteArrayUtil.java"],
)

java_library(
    name = "central_directory_info",
    srcs = ["CentralDirectoryInfo.java"],
    deps = [
        ":byte_array_util",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
    ],
)

java_library(
    name = "file_util",
    srcs = ["FileUtil.java"],
    deps = [
        ":log_data_type",
        ":stream_util",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/error:more_throwables",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "local_file_header",
    srcs = ["LocalFileHeader.java"],
    deps = [
        ":byte_array_util",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
    ],
)

java_library(
    name = "log_data_type",
    srcs = ["LogDataType.java"],
)

java_library(
    name = "stream_util",
    srcs = ["StreamUtil.java"],
    deps = [
        ":byte_array_list",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "zip_util",
    srcs = ["ZipUtil.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/report/generator:__pkg__",
    ],
    deps = [
        ":central_directory_info",
        ":file_util",
        ":local_file_header",
        ":stream_util",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/error:more_throwables",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "zip_util2",
    srcs = ["ZipUtil2.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/report/generator:__pkg__",
    ],
    deps = [
        ":file_util",
        ":zip_util",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:org_apache_commons_commons_compress:zip",
    ],
)
