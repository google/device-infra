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

load("@rules_go//go:def.bzl", "go_library", "go_test")

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = ["//src/devtools/mobileharness/shared/util/comm/dualconduit:__subpackages__"],
)

go_library(
    name = "conduit",
    srcs = [
        "conduit.go",
        "manager.go",
        "pump.go",
    ],
    importpath = "github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/conduit",
    deps = [
        "//src/devtools/mobileharness/shared/util/comm/dualconduit/proto:dual_conduit_go_proto",
        "//src/devtools/mobileharness/shared/util/comm/dualconduit/rxconn",
        "@com_github_rsocket_rsocket_go//:rsocket-go",
        "@com_github_rsocket_rsocket_go//rx/flux",
    ],
)

go_test(
    name = "conduit_test",
    srcs = [
        "conduit_test.go",
        "manager_test.go",
        "pump_test.go",
    ],
    embed = [":conduit"],
    deps = [
        "@com_github_rsocket_rsocket_go//:rsocket-go",
        "@com_github_rsocket_rsocket_go//payload",
        "@com_github_rsocket_rsocket_go//rx/flux",
        "@com_github_rsocket_rsocket_go//rx/mono",
    ],
)
