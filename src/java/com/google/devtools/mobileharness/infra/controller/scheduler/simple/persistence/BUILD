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
    name = "allocation_persistence_util",
    srcs = ["AllocationPersistenceUtil.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/scheduler/simple:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/controller/scheduler/simple:__subpackages__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/allocation",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
    ],
)

java_library(
    name = "annotations",
    srcs = ["Annotations.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice:__pkg__",
    ],
    deps = ["@maven//:javax_inject_jsr330_api"],
)

java_library(
    name = "jdbc_allocation_persistence_util",
    srcs = ["JdbcAllocationPersistenceUtil.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/scheduler/simple:__subpackages__",
    ],
    deps = [
        ":allocation_persistence_util",
        ":annotations",
        "//src/devtools/mobileharness/service/moss/proto:slg_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/allocation",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/service/moss/util/slg:converter",
        "//src/java/com/google/devtools/mobileharness/shared/util/database:database_connections",
        "//src/java/com/google/devtools/mobileharness/shared/util/database:table_util",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)
