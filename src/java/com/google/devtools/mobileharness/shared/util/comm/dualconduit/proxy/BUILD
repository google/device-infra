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
    name = "proxy",
    srcs = ["ReverseProxiedServer.java"],
    deps = [
        "//src/devtools/mobileharness/shared/util/comm/dualconduit/proto:dual_conduit_java_proto",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/dualconduit/client",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/system:shutdown_hook_manager",
        "@grpc-java//core",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
    ],
)

java_library(
    name = "server_type",
    srcs = ["ServerType.java"],
    deps = [],
)

java_library(
    name = "util",
    srcs = ["DualConduitUtil.java"],
    deps = [
        ":server_type",
        "@maven//:com_google_guava_guava",
    ],
)
