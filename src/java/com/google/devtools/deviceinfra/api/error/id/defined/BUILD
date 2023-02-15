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

package(default_applicable_licenses = ["//:license"])

java_library(
    name = "defined",
    visibility = [
        "//:deviceinfra_api_users",
    ],
    exports = [
        ":android_error_id",
        ":basic_error_id",
        ":infra_error_id",
    ],
)

java_library(
    name = "android_error_id",
    srcs = ["AndroidErrorId.java"],
    deps = [
        "//src/devtools/common/metrics/stability/model/proto:error_java_proto",
        "//src/devtools/deviceinfra/api/error/id/proto:error_code_range_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/util:error_id_formatter",
        "//src/java/com/google/devtools/deviceinfra/api/error:exception",
        "//src/java/com/google/devtools/deviceinfra/api/error:exception_generator",
        "//src/java/com/google/devtools/deviceinfra/api/error/id:error_id_interface",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "basic_error_id",
    srcs = ["BasicErrorId.java"],
    deps = [
        "//src/devtools/common/metrics/stability/model/proto:error_java_proto",
        "//src/devtools/deviceinfra/api/error/id/proto:error_code_range_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/util:error_id_formatter",
        "//src/java/com/google/devtools/deviceinfra/api/error:exception",
        "//src/java/com/google/devtools/deviceinfra/api/error:exception_generator",
        "//src/java/com/google/devtools/deviceinfra/api/error/id:error_id_interface",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "infra_error_id",
    srcs = ["InfraErrorId.java"],
    deps = [
        "//src/devtools/common/metrics/stability/model/proto:error_java_proto",
        "//src/devtools/deviceinfra/api/error/id/proto:error_code_range_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/util:error_id_formatter",
        "//src/java/com/google/devtools/deviceinfra/api/error:exception",
        "//src/java/com/google/devtools/deviceinfra/api/error:exception_generator",
        "//src/java/com/google/devtools/deviceinfra/api/error/id:error_id_interface",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "testing",
    testonly = 1,
    srcs = glob(["*.java"]),
    visibility = [
        "//src/javatests/com/google/devtools/deviceinfra/api/error/id:__subpackages__",
    ],
    deps = [
        "//src/devtools/common/metrics/stability/model/proto:error_java_proto",
        "//src/devtools/deviceinfra/api/error/id/proto:error_code_range_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/util:error_id_formatter",
        "//src/java/com/google/devtools/deviceinfra/api/error:exception",
        "//src/java/com/google/devtools/deviceinfra/api/error:exception_generator",
        "//src/java/com/google/devtools/deviceinfra/api/error/id:error_id_interface",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)
