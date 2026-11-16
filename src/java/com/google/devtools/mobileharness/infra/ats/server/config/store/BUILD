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
        "//src/java/com/google/devtools/mobileharness/infra/ats/server/config:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/server/config:__subpackages__",
    ],
)

java_library(
    name = "ats_config_store",
    srcs = ["AtsConfigStore.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "@protobuf//:protobuf_java",
    ],
)

java_library(
    name = "in_memory_ats_config_store",
    srcs = ["InMemoryAtsConfigStore.java"],
    deps = [
        ":ats_config_store",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
        "@protobuf//:protobuf_java",
    ],
)

java_library(
    name = "jdbc_ats_config_store",
    srcs = ["JdbcAtsConfigStore.java"],
    deps = [
        ":ats_config_store",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice:annotations",
        "//src/java/com/google/devtools/mobileharness/shared/util/database:database_connections",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
        "@protobuf//:protobuf_java",
    ],
)
