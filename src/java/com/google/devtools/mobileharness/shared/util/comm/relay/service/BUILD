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
    "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice:__subpackages__",
    "//src/javatests/com/google/devtools/mobileharness/infra/client/longrunningservice:__subpackages__",
]

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = DEFAULT_VISIBILITY,
)

java_library(
    name = "channel_manager",
    srcs = [
        "ChannelManager.java",
        "SimpleChannelManager.java",
    ],
    deps = [
        "@io_grpc_grpc_java//core",
        "@maven//:com_google_code_findbugs_jsr305",
    ],
)

java_library(
    name = "relay_service",
    srcs = [
        "CallRelay.java",
        "RelayHandler.java",
        "RelayInterceptor.java",
        "RelayRegistry.java",
    ],
    deps = [
        ":channel_manager",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/relay",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@io_grpc_grpc_java//core",
        "@maven//:com_google_code_findbugs_jsr305",
    ],
)
