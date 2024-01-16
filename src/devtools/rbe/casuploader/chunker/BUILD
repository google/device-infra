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

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = ["//visibility:public"],
)

go_library(
    name = "chunker",
    srcs = [
        "chunker.go",
    ],
    importpath = "github.com/google/device-infra/src/devtools/rbe/caschunker/chunker",
    deps = [
        "//third_party/fastcdc_go:fastcdc",
        "@com_github_golang_glog//:glog",
    ],
)

go_test(
    name = "chunker_test",
    srcs = ["chunker_test.go"],
    library = ":chunker",
    deps = ["@com_github_google_uuid//:uuid"],
)
