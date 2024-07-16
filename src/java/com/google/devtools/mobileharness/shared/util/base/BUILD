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
    default_visibility = [
        "//:deviceinfra_pkg",
    ],
)

java_library(
    name = "base",
    srcs = [
        "StrUtil.java",
    ],
    deps = [
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "count_down_set",
    srcs = ["CountDownSet.java"],
    deps = [
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "proto_text_format",
    srcs = ["ProtoTextFormat.java"],
    deps = [
        "@com_google_protobuf//:protobuf_java",
    ],
)

java_library(
    name = "stack_set",
    srcs = ["StackSet.java"],
    deps = [
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "supplier",
    srcs = [
        "MoreSuppliers.java",
        "ThrowingSupplier.java",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "table_formatter",
    srcs = ["TableFormatter.java"],
    deps = [
        "@maven//:com_google_guava_guava",
    ],
)
