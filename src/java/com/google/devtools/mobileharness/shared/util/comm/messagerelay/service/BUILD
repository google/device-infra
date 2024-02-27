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
    name = "stream_manager",
    srcs = ["StreamManager.java"],
    visibility = [
        "//src/javatests/com/google/devtools/mobileharness/shared/util/comm/messagerelay/service:__subpackages__",
    ],
    deps = [
        "//src/devtools/mobileharness/shared/util/comm/messagerelay/proto:message_relay_service_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "@io_grpc_grpc_java//stub",
        "@maven//:com_google_guava_guava",
    ],
)