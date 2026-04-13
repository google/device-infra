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

load("@rules_go//go:def.bzl", "go_test")

package(default_applicable_licenses = ["//:license"])

go_test(
    name = "tests_test",
    srcs = ["integration_test.go"],
    deps = [
        "//src/devtools/mobileharness/shared/util/comm/dualconduit/acceptor",
        "//src/devtools/mobileharness/shared/util/comm/dualconduit/dialer",
        "//src/devtools/mobileharness/shared/util/comm/dualconduit/proto:dual_conduit_go_proto",
        "//src/devtools/mobileharness/shared/util/comm/dualconduit/proto:dual_conduit_service_go_grpc",
        "//src/devtools/mobileharness/shared/util/comm/dualconduit/tests/helloworld",
        "//src/devtools/mobileharness/shared/util/comm/dualconduit/tests/helloworld:helloworld_go_proto",
        "//src/devtools/mobileharness/shared/util/comm/dualconduit/tests/helloworld:helloworld_service_go_grpc",
        "//src/devtools/mobileharness/shared/util/comm/dualconduit/transport",
        "@com_github_rsocket_rsocket_go//core/transport",
        "@org_golang_google_grpc//:grpc",
        "@org_golang_google_grpc//credentials/insecure",
    ],
)
