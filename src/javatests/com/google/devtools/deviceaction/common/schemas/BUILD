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

package(default_applicable_licenses = ["//:license"])

java_library(
    name = "tests",
    testonly = 1,
    srcs = glob(
        ["*Test.java"],
    ),
    deps = [
        "//src/java/com/google/devtools/deviceaction/common/error",
        "//src/java/com/google/devtools/deviceaction/common/schemas",
        "//src/javatests/com/google/devtools/deviceinfra/builddefs:truth",
        "@maven//:junit_junit",
    ],
)

junit_test_suites(
    name = "gen_tests",
    sizes = [
        "small",
    ],
    deps = [
        ":tests",
    ],
)
