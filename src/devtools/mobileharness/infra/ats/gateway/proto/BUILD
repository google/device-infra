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

load("@io_grpc_grpc_java//:java_grpc_library.bzl", "java_grpc_library")

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//java/com/google/devtools/mobileharness/service/cloudatlas:__subpackages__",
        "//javatests/com/google/devtools/mobileharness/service/cloudatlas:__subpackages__",
    ],
)

proto_library(
    name = "session_service_proto",
    srcs = ["session_service.proto"],
    deps = [
        "//src/devtools/mobileharness/api/gateway/proto:setting_proto",
    ],
)

java_proto_library(
    name = "session_service_java_proto",
    deps = [":session_service_proto"],
)

java_grpc_library(
    name = "session_service_java_grpc",
    srcs = [":session_service_proto"],
    deps = [":session_service_java_proto"],
)
