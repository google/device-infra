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

load("@rules_go//go:def.bzl", "go_library")

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//src/devtools/mobileharness/shared/util/comm/dualconduit/cmd/acceptor:__pkg__",
        "//src/devtools/mobileharness/shared/util/comm/dualconduit/tests:__pkg__",
    ],
)

go_library(
    name = "acceptor",
    srcs = ["acceptor.go"],
    importpath = "github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/acceptor",
    deps = [
        "//src/devtools/mobileharness/shared/util/comm/dualconduit/conduit",
        "//src/devtools/mobileharness/shared/util/comm/dualconduit/mesh",
        "//src/devtools/mobileharness/shared/util/comm/dualconduit/proto:dual_conduit_go_proto",
        "@com_github_rsocket_rsocket_go//:rsocket-go",
        "@com_github_rsocket_rsocket_go//core/transport",
        "@com_github_rsocket_rsocket_go//payload",
        "@com_github_rsocket_rsocket_go//rx/flux",
        "@com_github_rsocket_rsocket_go//rx/mono",
        "@org_golang_google_protobuf//proto",
    ],
)
