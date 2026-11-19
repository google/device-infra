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
        "//src/java/com/google/devtools/mobileharness/infra/ats:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats:__subpackages__",
    ],
)

java_library(
    name = "ats_server_config_module",
    srcs = ["AtsServerConfigModule.java"],
    deps = [
        ":file_cleaner_service",
        "//src/java/com/google/devtools/mobileharness/infra/ats/server/config/store:ats_config_store",
        "//src/java/com/google/devtools/mobileharness/infra/ats/server/config/store:in_memory_ats_config_store",
        "//src/java/com/google/devtools/mobileharness/infra/ats/server/config/store:jdbc_ats_config_store",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice:annotations",
        "@grpc-java//core",
        "@maven//:com_google_inject_guice",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "file_cleaner_service",
    srcs = ["FileCleanerService.java"],
    deps = [
        "//src/devtools/mobileharness/infra/ats/server/proto/config:common_java_proto",
        "//src/devtools/mobileharness/infra/ats/server/proto/config:file_cleaner_service_java_grpc",
        "//src/devtools/mobileharness/infra/ats/server/proto/config:file_cleaner_service_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/ats/server/config/store:ats_config_store",
        "//src/java/com/google/devtools/mobileharness/shared/util/base:proto_extension_registry",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@grpc-java//core",
        "@grpc-java//stub",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
        "@protobuf//:protobuf_java",
    ],
)
