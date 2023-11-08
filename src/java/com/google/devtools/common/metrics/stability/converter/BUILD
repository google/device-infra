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

DEFAULT_VISIBILITY = [
    #  DO NOT public all libs to user projects.
    "//:deviceinfra_metrics_pkg",
]

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = DEFAULT_VISIBILITY,
)

java_library(
    name = "deserialized_exception",
    srcs = [
        "DeserializedException.java",
    ],
    visibility = [
        "//:deviceinfra_metrics_pkg",
    ],
    deps = [
        ":internal",
        "//src/java/com/google/devtools/common/metrics/stability/model:error_id",
        "@maven//:com_google_code_findbugs_jsr305",
    ],
)

java_library(
    name = "converter",
    srcs = [
        "ErrorModelConverter.java",
    ],
    visibility = [
        "//:deviceinfra_metrics_pkg",
        "//:deviceinfra_pkg",
    ],
    deps = [
        "deserialized_exception",
        ":internal",
        "//src/devtools/common/metrics/stability/model/proto:error_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/model:error_id",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "internal",
    srcs = [
        "DeserializedErrorId.java",
        "UnknownErrorId.java",
    ],
    deps = [
        "//src/devtools/common/metrics/stability/model/proto:error_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/model:error_id",
        "//src/java/com/google/devtools/common/metrics/stability/util:error_id_formatter",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
    ],
)
