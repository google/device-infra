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
    default_visibility = ["//src/devtools/mobileharness/infra/ats/rollout:__subpackages__"],
)

go_library(
    name = "auth",
    srcs = ["auth.go"],
    importpath = "github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/auth",
    deps = [
        "@org_golang_x_oauth2//:oauth2",
        "@org_golang_x_oauth2//google",
    ],
)

go_test(
    name = "auth_test",
    srcs = ["auth_test.go"],
    embed = [":auth"],
    deps = ["@com_github_google_go_cmp//cmp"],
)
