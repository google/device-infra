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

# RpcError related utils.

load("@rules_python//python:defs.bzl", "py_library")
load("@rules_python//python:defs.bzl", "py_test")

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = ["//visibility:public"],
)

py_library(
    name = "grpc_error_util_lib",
    srcs = ["grpc_error_util.py"],
    srcs_version = "PY3",
    deps = [
        "//src/devtools/common/metrics/stability/model/proto:error_py_proto",
        "//src/devtools/common/metrics/stability/rpc/proto:rpc_error_payload_py_proto",
        "//src/devtools/common/metrics/stability/rpc/proto:rpc_error_py_proto",
        "@com_github_grpc_grpc//src/python/grpcio/grpc:grpcio",
    ],
)

py_test(
    name = "grpc_error_util_test",
    srcs = [
        "grpc_error_util_test.py",
    ],
    deps = [
        ":grpc_error_util_lib",
        "@com_github_grpc_grpc//src/python/grpcio/grpc:grpcio",
        "@com_google_absl_py//absl/testing:absltest",
    ],
)
