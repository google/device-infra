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

load("@rules_java//java:defs.bzl", "java_library")
load("//src/javatests/com/google/devtools/mobileharness/builddefs:junit_test_suites.bzl", "junit_test_suites")

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
    data = ["//src/javatests/com/google/devtools/deviceaction/testdata"],
    deps = [
        "//src/devtools/deviceaction/schemas/proto:base_spec_java_proto",
        "//src/devtools/deviceaction/schemas/proto/actions:install_mainline_spec_java_proto",
        "//src/java/com/google/devtools/deviceaction/common/error",
        "//src/java/com/google/devtools/deviceaction/common/schemas",
        "//src/java/com/google/devtools/deviceaction/common/utils",
        "//src/java/com/google/devtools/deviceaction/framework",
        "//src/java/com/google/devtools/deviceaction/framework:device_action_module",
        "//src/java/com/google/devtools/deviceaction/framework/actions",
        "//src/java/com/google/devtools/deviceaction/framework/deviceconfigs",
        "//src/java/com/google/devtools/deviceaction/framework/devices",
        "//src/java/com/google/devtools/deviceinfra/platform/android/lightning/internal/sdk/adb",
        "//src/java/com/google/devtools/mobileharness/shared/util/command",
        "//src/java/com/google/devtools/mobileharness/shared/util/quota",
        "//src/java/com/google/wireless/qa/mobileharness/shared/android:aapt",
        "//src/javatests/com/google/devtools/mobileharness/builddefs:truth",
        "//src/javatests/com/google/devtools/mobileharness/shared/util/runfiles:runfiles_util",
        "@maven//:com_google_inject_guice",
        "@maven//:junit_junit",
        "@maven//:org_mockito_mockito_core",
    ],
)

junit_test_suites(
    name = "gen_tests",
    sizes = [
        "small",
        "medium",
    ],
    deps = [
        ":tests",
    ],
)
