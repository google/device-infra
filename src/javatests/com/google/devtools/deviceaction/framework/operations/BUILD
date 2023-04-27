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

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = ["//src/javatests/com/google/devtools/deviceaction:deviceaction_javatests_pkg"],
)

java_library(
    name = "tests",
    testonly = 1,
    srcs = glob(
        ["*Test.java"],
    ),
    deps = [
        "//src/java/com/google/devtools/deviceaction/common/error",
        "//src/java/com/google/devtools/deviceaction/common/schemas",
        "//src/java/com/google/devtools/deviceaction/framework/devices",
        "//src/java/com/google/devtools/deviceaction/framework/operations",
        "//src/java/com/google/devtools/deviceinfra/shared/util/time",
        "//src/java/com/google/devtools/mobileharness/platform/android/packagemanager:info",
        "//src/javatests/com/google/devtools/deviceinfra/builddefs:truth",
        "@maven//:com_google_guava_guava",
        "@maven//:junit_junit",
        "@maven//:org_mockito_mockito_core",
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
