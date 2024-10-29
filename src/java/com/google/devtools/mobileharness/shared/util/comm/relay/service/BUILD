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
        "//:deviceinfra_all_pkg",
    ],
)

java_library(
    name = "connection_manager",
    srcs = [
        "ConnectionManager.java",
        "DirectConnectionManager.java",
    ],
    deps = [
        "//src/devtools/mobileharness/shared/util/comm/relay/proto:destination_java_proto",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/relay",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/stub:channel_factory",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/stub:channel_manager",
        "@io_grpc_grpc_java//core",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "relay_service",
    srcs = [
        "CallSwitcher.java",
        "RelayHandler.java",
        "RelayInterceptor.java",
        "RelayRegistry.java",
    ],
    deps = [
        ":connection_manager",
        "//src/devtools/mobileharness/shared/util/comm/relay/proto:destination_java_proto",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/relay",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@io_grpc_grpc_java//core",
        "@maven//:com_google_code_findbugs_jsr305",
    ],
)
