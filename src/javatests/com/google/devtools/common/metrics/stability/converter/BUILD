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

load("//src/javatests/com/google/devtools/deviceinfra/builddefs:junit_test_suites.bzl", "junit_test_suites")

licenses(["notice"])

java_library(
    name = "converter",
    testonly = True,
    srcs = glob(["*.java"]),
    visibility = [
        "//javatests/com/google/devtools/common/metrics/stability/rpc:__subpackages__",
        "//javatests/com/google/devtools/deviceinfra/shared/error/exception/rpc:__subpackages__",
        "//javatests/com/google/devtools/deviceinfra/shared/util/comm/rpc:__subpackages__",
    ],
    deps = [
        "//javatests/com/google/devtools/common/metrics/stability/testdata:fake_exception",
        "//javatests/com/google/devtools/deviceinfra/shared/error/id/defined",
        "//src/devtools/common/metrics/stability/model/proto:error_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/converter",
        "//src/java/com/google/devtools/common/metrics/stability/converter:deserialized_exception",
        "//src/java/com/google/devtools/common/metrics/stability/converter:internal",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_truth_extensions_truth_proto_extension",
        "@maven//:com_google_truth_truth",
        "@maven//:junit_junit",
    ],
)

junit_test_suites(
    name = "gen_tests",
    sizes = [
        "small",
    ],
    deps = [":converter"],
)
