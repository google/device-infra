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

load("@rules_go//go:def.bzl", "go_binary", "go_test")

package(default_applicable_licenses = ["//:license"])

go_binary(
    name = "rollout_agent",
    srcs = ["main.go"],
    goarch = "amd64",
    goos = "linux",
    pure = "on",
    deps = [
        "//src/devtools/mobileharness/infra/ats/rollout/agent",
        "//src/devtools/mobileharness/infra/ats/rollout/auth",
        "//src/devtools/mobileharness/infra/ats/rollout/record",
        "//src/devtools/mobileharness/infra/ats/rollout/store/firestore",
    ],
)

# The internal build gives the test the binary as its library. The external
# Go rules cannot embed a binary in a test, so there the test compiles
# main.go itself, with the dependencies of main.go.
go_test(
    name = "rollout_agent_test",
    srcs = [
        "main.go",
        "main_test.go",
    ],
    deps = [
        "//src/devtools/mobileharness/infra/ats/rollout/agent",
        "//src/devtools/mobileharness/infra/ats/rollout/auth",
        "//src/devtools/mobileharness/infra/ats/rollout/record",
        "//src/devtools/mobileharness/infra/ats/rollout/store/firestore",
    ],
)
