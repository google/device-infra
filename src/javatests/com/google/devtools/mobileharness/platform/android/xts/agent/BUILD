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

load("//src/javatests/com/google/devtools/mobileharness/builddefs:junit_test_suites.bzl", "junit_test_suites")

package(
    default_applicable_licenses = ["//:license"],
    default_testonly = 1,
)

java_library(
    name = "agent",
    srcs = glob(["*.java"]),
    data = [
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/agent:tradefed_invocation_agent_deploy.jar",
        "//src/javatests/com/google/devtools/mobileharness/platform/android/xts/agent/testdata:fake_tradefed_deploy.jar",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/runtime:xts_tradefed_runtime_info",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/runtime:xts_tradefed_runtime_info_file_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/command",
        "//src/java/com/google/devtools/mobileharness/shared/util/system",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:sleeper",
        "//src/javatests/com/google/devtools/mobileharness/builddefs:truth",
        "//src/javatests/com/google/devtools/mobileharness/shared/util/runfiles:runfiles_util",
        "@maven//:com_google_guava_guava",
        "@maven//:junit_junit",
    ],
)

junit_test_suites(
    name = "gen_tests",
    sizes = [
        "small",
    ],
    deps = [":agent"],
)
