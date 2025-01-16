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

load("@rules_java//java:defs.bzl", "java_binary", "java_library")
load("//src/javatests/com/google/devtools/mobileharness/builddefs:junit_test_suites.bzl", "junit_test_suites")

package(
    default_applicable_licenses = ["//:license"],
    default_testonly = 1,
)

java_library(
    name = "signal",
    srcs = glob(
        ["*.java"],
        exclude = [
            "FakeProgram.java",
        ],
    ),
    data = [
        ":fake_program_deploy.jar",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/command",
        "//src/java/com/google/devtools/mobileharness/shared/util/system",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:sleeper",
        "//src/javatests/com/google/devtools/mobileharness/builddefs:truth",
        "//src/javatests/com/google/devtools/mobileharness/shared/util/junit/rule:monitored_string_builders",
        "//src/javatests/com/google/devtools/mobileharness/shared/util/junit/rule:print_test_name",
        "//src/javatests/com/google/devtools/mobileharness/shared/util/runfiles:runfiles_util",
        "//src/javatests/com/google/devtools/mobileharness/shared/util/truth:correspondences",
        "@maven//:com_google_guava_guava",
        "@maven//:junit_junit",
    ],
)

java_binary(
    name = "fake_program",
    srcs = ["FakeProgram.java"],
    main_class = "com.google.devtools.mobileharness.shared.util.signal.FakeProgram",
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/signal:signals",
    ],
)

junit_test_suites(
    name = "gen_tests",
    sizes = [
        "medium",
    ],
    deps = [":signal"],
)
