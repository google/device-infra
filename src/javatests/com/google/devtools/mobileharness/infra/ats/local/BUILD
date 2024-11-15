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

filegroup(
    name = "testdata",
    srcs = glob(["testdata/*"]),
    visibility = ["//src/javatests/com/google/devtools/mobileharness/infra/ats/local:__subpackages__"],
)

java_library(
    name = "localrunner",
    srcs = glob(["*.java"]),
    data = [
        ":olc_server_for_local_runner_testing_deploy.jar",
        ":testdata",
        "//src/java/com/google/devtools/mobileharness/infra/ats/local:ats_local_runner_deploy.jar",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/command",
        "//src/java/com/google/devtools/mobileharness/shared/util/port:portprober",
        "//src/java/com/google/devtools/mobileharness/shared/util/system",
        "//src/javatests/com/google/devtools/mobileharness/builddefs:truth",
        "//src/javatests/com/google/devtools/mobileharness/shared/util/runfiles:runfiles_util",
        "@maven//:com_google_guava_guava",
        "@maven//:junit_junit",
    ],
)

olc_server_binary(
    name = "olc_server_for_local_runner_testing",
    extra_runtime_deps = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/local/sessionplugin:ats_local_session_plugin",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode/local",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api:no_op_test",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:android_real_device",
    ],
)

junit_test_suites(
    name = "gen_tests",
    sizes = [
        "medium",
    ],
    deps = [":localrunner"],
)
