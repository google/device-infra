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

load("@protobuf//bazel:proto_library.bzl", "proto_library")
load("@rules_go//go:def.bzl", "go_library")
load("@rules_go//proto:def.bzl", "go_proto_library")

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = ["//visibility:public"],
)

# ==========================================
# The Message Proto Targets
# ==========================================

proto_library(
    name = "helloworld_proto",
    srcs = ["helloworld.proto"],
)

go_proto_library(
    name = "helloworld_go_proto",
    importpath = "github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/tests/helloworld/proto/helloworldpb",
    proto = ":helloworld_proto",
)

# ==========================================
# The Service Proto Targets
# ==========================================

proto_library(
    name = "helloworld_service_proto",
    srcs = ["helloworld_service.proto"],
    deps = [":helloworld_proto"],
)

go_proto_library(
    name = "helloworld_service_go_grpc",
    compilers = ["@rules_go//proto:go_grpc"],
    importpath = "github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/tests/helloworld/proto/helloworldsvcpb",
    proto = ":helloworld_service_proto",
    visibility = ["//visibility:public"],
    deps = [":helloworld_go_proto"],
)

go_library(
    name = "helloworld",
    srcs = ["helloworld.go"],
    importpath = "github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/tests/helloworld",
    deps = [
        ":helloworld_go_proto",
        ":helloworld_service_go_grpc",
    ],
)
