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

package(
    default_applicable_licenses = ["//:license"],
)

java_library(
    name = "central_directory_info",
    srcs = ["CentralDirectoryInfo.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/base:byte_array_util",
    ],
)

java_library(
    name = "local_file_header",
    srcs = ["LocalFileHeader.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/base:byte_array_util",
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
        ":local_file_header",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/base:stream_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/error:more_throwables",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local:file_util",
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
        ":zip_util",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local:file_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:org_apache_commons_commons_compress",
    ],
)
