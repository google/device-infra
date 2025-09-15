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

# Session Plugin messages used for serving ATS server and ATS Web UI.

load("@grpc//bazel:python_rules.bzl", "py_proto_library")

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//:deviceinfra_all_pkg",
        "//third_party/py/multitest_transport/util:__subpackages__",
    ],
)

proto_library(
    name = "service_proto",
    srcs = ["service.proto"],
    deps = [
        "//src/devtools/mobileharness/infra/ats/common/proto:xts_common_proto",
        "@protobuf//:duration_proto",
        "@protobuf//:timestamp_proto",
    ],
)

py_proto_library(
    name = "service_py_proto",
    deps = [":service_proto"],
)

java_proto_library(
    name = "service_java_proto",
    deps = [":service_proto"],
)
