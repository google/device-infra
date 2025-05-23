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
load("//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice:server.bzl", "olc_server_binary")
load("//src/javatests/com/google/devtools/mobileharness/builddefs:junit_test_suites.bzl", "junit_test_suites")

package(
    default_applicable_licenses = ["//:license"],
    default_testonly = 1,
)

java_library(
    name = "size",
    srcs = glob(["*.java"]),
    data = [
        ":base_olc_server_deploy.jar",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common/olcserver:ats_olc_server_deploy.jar",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common/olcserver:ats_olc_server_local_mode_deploy.jar",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console:ats_console_deploy.jar",
        "//src/java/com/google/devtools/mobileharness/infra/lab:lab_server_oss_deploy.jar",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/agent:tradefed_invocation_agent_deploy.jar",
    ],
    deps = [
        "//src/javatests/com/google/devtools/mobileharness/builddefs:truth",
        "//src/javatests/com/google/devtools/mobileharness/shared/util/file/local:binary_size_checker",
        "//src/javatests/com/google/devtools/mobileharness/shared/util/runfiles:runfiles_util",
        "@maven//:com_google_guava_guava",
        "@maven//:junit_junit",
    ],
)

olc_server_binary(
    name = "base_olc_server",
    extra_runtime_deps = [
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode/ats",
    ],
)

junit_test_suites(
    name = "gen_tests",
    sizes = [
        "small",
    ],
    deps = [":size"],
)
