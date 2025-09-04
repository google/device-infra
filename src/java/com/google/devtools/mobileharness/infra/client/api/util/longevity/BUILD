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
    ],
)

java_library(
    name = "longevity",
    srcs = [
        "LongevityTestHelper.java",
    ],
    deps = [
        ":composite_storage_backend",
        ":storage_backend",
        "//src/devtools/mobileharness/infra/container/proto:test_engine_java_proto",
        "//src/devtools/mobileharness/service/moss/proto:slg_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/service/moss/util/slg:converter",
        "//src/java/com/google/devtools/mobileharness/shared/util/base:proto_text_format",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:property",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job/in/spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto/spec:base_spec_java_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto/spec:job_spec_java_proto",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
        "@protobuf//:protobuf_java",
    ],
)

java_library(
    name = "storage_backend",
    srcs = ["StorageBackend.java"],
    deps = ["//src/java/com/google/devtools/mobileharness/api/model/error"],
)

java_library(
    name = "composite_storage_backend",
    srcs = ["CompositeStorageBackend.java"],
    deps = [
        ":local_file_storage_backend",
        ":storage_backend",
        "//src/java/com/google/devtools/deviceinfra/shared/util/file/remote/constant:remote_file_type",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "local_file_storage_backend",
    srcs = ["LocalFileStorageBackend.java"],
    deps = [
        ":storage_backend",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
    ],
)
