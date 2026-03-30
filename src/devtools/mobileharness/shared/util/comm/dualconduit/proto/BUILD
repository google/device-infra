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
load("@rules_go//proto:def.bzl", "go_proto_library")

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = ["//visibility:public"],
)

# ==========================================
# The Message Proto Targets
# ==========================================

proto_library(
    name = "dual_conduit_proto",
    srcs = ["dual_conduit.proto"],
)

go_proto_library(
    name = "dual_conduit_go_proto",
    importpath = "github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/proto/dconpb",
    proto = ":dual_conduit_proto",
)

# ==========================================
# The Service Proto Targets
# ==========================================

proto_library(
    name = "dual_conduit_service_proto",
    srcs = ["dual_conduit_service.proto"],
    deps = [":dual_conduit_proto"],
)

go_proto_library(
    name = "dual_conduit_service_go_grpc",
    compilers = ["@rules_go//proto:go_grpc"],
    importpath = "github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/proto/dconsvcpb",
    proto = ":dual_conduit_service_proto",
    deps = [":dual_conduit_go_proto"],
)
