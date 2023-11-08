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
#   Basic input data models of Mobile Harness jobs and tests.

package(default_applicable_licenses = ["//:license"])

java_library(
    name = "in",
    visibility = [
        "//:deviceinfra_all_pkg",
    ],
    exports = [
        ":decorators",
        ":device_specs",
        ":dimensions",
        ":dirs",
        ":files",
        ":params",
        ":timeout",
    ],
)

java_library(
    name = "decorators",
    srcs = ["Decorators.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/controller/scheduler/model/job/in:__pkg__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "device_specs",
    srcs = [
        "DeviceSpec.java",
        "DeviceSpecs.java",
    ],
    visibility = [
    ],
    deps = [
        ":decorators",
        ":dimensions",
        "//src/devtools/mobileharness/api/model/proto:job_java_proto",
        "//src/java/com/google/devtools/mobileharness/infra/controller/scheduler/model/job/in:device_requirements",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "dimensions",
    srcs = ["Dimensions.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/controller/scheduler/model/job/in:__pkg__",
        "//src/java/com/google/devtools/mobileharness/shared/util/sharedpool:__pkg__",
    ],
    deps = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dimension",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "dirs",
    srcs = ["Dirs.java"],
    visibility = [
        "//:deviceinfra_all_pkg",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "@maven//:com_google_code_findbugs_jsr305",
    ],
)

java_library(
    name = "files",
    srcs = ["Files.java"],
    visibility = [
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/job/out:touchable_timing",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "params",
    srcs = ["Params.java"],
    visibility = [
        "//java/com/google/devtools/mobileharness/api/model/job/in:__pkg__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/job/out:touchable_timing",
        "//src/java/com/google/devtools/mobileharness/shared/util/base",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "timeout",
    srcs = ["Timeout.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/lab/controller:__pkg__",
    ],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:job_java_proto",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_guava_guava",
    ],
)
