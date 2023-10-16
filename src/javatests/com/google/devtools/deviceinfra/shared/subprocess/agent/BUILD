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
    default_testonly = 1,
)

java_library(
    name = "agent",
    srcs = glob(
        ["*.java"],
        exclude = [
            "FakeProgram.java",
            "StressTestProgram.java",
        ],
    ),
    data = [
        ":fake_program_deploy.jar",
        ":stress_test_program_deploy.jar",
        "//src/java/com/google/devtools/deviceinfra/shared/subprocess/agent:udcluster_agent_boot_class_deploy.jar",
        "//src/java/com/google/devtools/deviceinfra/shared/subprocess/agent:udcluster_agent_deploy.jar",
    ],
    deps = [
        "//src/devtools/deviceinfra/shared/commandhistory/proto:command_record_java_proto",
        "//src/devtools/deviceinfra/shared/subprocess/proto:agent_config_java_proto",
        "//src/java/com/google/devtools/deviceinfra/shared/commandhistory/renderer:command_history_renderer",
        "//src/java/com/google/devtools/deviceinfra/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/command",
        "//src/java/com/google/devtools/mobileharness/shared/util/system",
        "//src/javatests/com/google/devtools/deviceinfra/builddefs:truth",
        "//src/javatests/com/google/devtools/deviceinfra/shared/util/runfiles:runfiles_util",
        "@com_google_protobuf//:protobuf_java",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_truth_extensions_truth_proto_extension",
        "@maven//:junit_junit",
    ],
)

java_binary(
    name = "fake_program",
    srcs = ["FakeProgram.java"],
    main_class = "com.google.devtools.deviceinfra.shared.subprocess.agent.FakeProgram",
    deps = [
        "//src/java/com/google/devtools/deviceinfra/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/deviceinfra/shared/util/time",
        "@maven//:com_google_guava_guava",
        "@maven//:junit_junit",
    ],
)

java_binary(
    name = "stress_test_program",
    srcs = ["StressTestProgram.java"],
    main_class = "com.google.devtools.deviceinfra.shared.subprocess.agent.StressTestProgram",
)

junit_test_suites(
    name = "gen_tests",
    sizes = [
        "medium",
    ],
    deps = [":agent"],
)
