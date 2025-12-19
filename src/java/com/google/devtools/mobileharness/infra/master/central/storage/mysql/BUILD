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
        "//src/java/com/google/devtools/mobileharness/infra/master/central/storage:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/master/central/storage:__subpackages__",
    ],
)

java_library(
    name = "storage_module",
    srcs = ["CentralStorageMySqlModule.java"],
    visibility = [
        "//java/com/google/devtools/mobileharness/infra/master/central:__subpackages__",
    ],
    deps = [
        ":mysql",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/master/central:annotation",
        "//src/java/com/google/devtools/mobileharness/infra/master/central/storage/lab:lab_repository",
        "//src/java/com/google/devtools/mobileharness/shared/storage/transaction:transaction_runner",
        "//src/java/com/google/devtools/mobileharness/shared/util/database:database_connections",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_inject_guice",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "mysql",
    srcs = [
        "MySqlLabRepository.java",
        "MySqlTransactionContext.java",
        "MySqlTransactionRunner.java",
    ],
    deps = [
        ":lab_table_client",
        "//src/devtools/mobileharness/infra/master/central/proto:lab_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:locator",
        "//src/java/com/google/devtools/mobileharness/infra/master/central:annotation",
        "//src/java/com/google/devtools/mobileharness/infra/master/central/storage/lab:lab_repository",
        "//src/java/com/google/devtools/mobileharness/shared/storage/transaction:transaction_config",
        "//src/java/com/google/devtools/mobileharness/shared/storage/transaction:transaction_context",
        "//src/java/com/google/devtools/mobileharness/shared/storage/transaction:transaction_runner",
        "//src/java/com/google/devtools/mobileharness/shared/storage/transaction:transaction_worker",
        "//src/java/com/google/devtools/mobileharness/shared/util/database:database_connections",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "lab_table_client",
    srcs = [
        "MySqlLabTableClient.java",
    ],
    deps = [
        "//src/devtools/mobileharness/infra/master/central/proto:lab_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:locator",
        "//src/java/com/google/devtools/mobileharness/shared/util/base:proto_extension_registry",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:javax_inject_jsr330_api",
        "@protobuf//:protobuf_java",
    ],
)
