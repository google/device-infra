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
    name = "client_address_server_interceptor",
    srcs = ["ClientAddressServerInterceptor.java"],
    deps = [
        ":grpc_contexts",
        "@io_grpc_grpc_java//context",
        "@io_grpc_grpc_java//core",
    ],
)

java_library(
    name = "grpc_contexts",
    srcs = ["GrpcContexts.java"],
    deps = [
        "@io_grpc_grpc_java//context",
    ],
)
