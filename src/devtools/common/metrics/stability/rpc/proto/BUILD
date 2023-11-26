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

load("@com_google_protobuf//:protobuf.bzl", "py_proto_library")

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//:deviceinfra_pkg",
    ],
)

proto_library(
    name = "rpc_error_proto",
    srcs = ["rpc_error.proto"],
)

java_proto_library(
    name = "rpc_error_java_proto",
    deps = [":rpc_error_proto"],
)

py_proto_library(
    name = "rpc_error_py_proto",
    srcs = [
        "rpc_error.proto",
    ],
)

proto_library(
    name = "rpc_error_payload_proto",
    srcs = ["rpc_error_payload.proto"],
    deps = [
        ":rpc_error_proto",
    ],
)

java_proto_library(
    name = "rpc_error_payload_java_proto",
    deps = [":rpc_error_payload_proto"],
)

py_proto_library(
    name = "rpc_error_payload_py_proto",
    srcs = [
        "rpc_error_payload.proto",
    ],
    deps = [
        ":rpc_error_py_proto",
    ],
)
