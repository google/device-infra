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

package(default_applicable_licenses = ["//:license"])

java_library(
    name = "command",
    testonly = True,
    srcs = glob(
        ["*.java"],
        exclude = ["ConsoleCommandTestModule.java"],
    ),
    data = [
        ":testdata",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common/olcserver:ats_olc_server_local_mode_deploy.jar",
        "//src/javatests/com/google/devtools/mobileharness/platform/android/xts/config:testdata",
    ],
    deps = [
        ":console_command_test_module",
        "//src/devtools/mobileharness/infra/ats/console/controller/proto:session_plugin_java_proto",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common:flags_string",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common/olcserver:server_environment_preparer",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common/olcserver:server_preparer",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console:annotations",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console:ats_console_lib",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console:ats_console_module",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console:console_info",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console:guice_factory",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/command",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/command:run_command",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/command/alias:alias_manager",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/constant:dirs",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/olcserver:ats_session_stub",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/command:command_helper",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/console:console_util",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/plan:plan_helper",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/result:result_lister_helper",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/constant:dirs",
        "//src/java/com/google/devtools/mobileharness/shared/util/command",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/path",
        "//src/java/com/google/devtools/mobileharness/shared/util/port:portprober",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:sleeper",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dir",
        "//src/javatests/com/google/devtools/mobileharness/builddefs:truth",
        "//src/javatests/com/google/devtools/mobileharness/shared/util/junit/rule:set_flags_oss",
        "//src/javatests/com/google/devtools/mobileharness/shared/util/runfiles:runfiles_util",
        "//src/javatests/com/google/devtools/mobileharness/shared/util/truth:correspondences",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_extensions_guice_testlib",
        "@maven//:com_google_inject_guice",
        "@maven//:info_picocli_picocli",
        "@maven//:javax_inject_jsr330_api",
        "@maven//:junit_junit",
        "@maven//:org_jline_jline",
        "@maven//:org_mockito_mockito_core",
    ],
)

java_library(
    name = "console_command_test_module",
    srcs = ["ConsoleCommandTestModule.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/common:flags_string",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common:session_request_info",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common/olcserver:module",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common/olcserver:server_environment_preparer",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console:annotations",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console:console_info",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/report:compatibility_report_module",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:thread_pools",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:sleeper",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
    ],
)

filegroup(
    name = "testdata",
    srcs = glob(["testdata/**/*"]),
)

junit_test_suites(
    name = "tests",
    sizes = [
        "medium",
        "small",
    ],
    deps = [":command"],
)
