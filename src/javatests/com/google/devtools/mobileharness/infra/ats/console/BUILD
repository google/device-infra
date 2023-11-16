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
    name = "console",
    srcs = glob(["*.java"]),
    data = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/olcserver:ats_olc_server_deploy.jar",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console:ats_console_lib",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console:ats_console_module",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/port:portprober",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:sleeper",
        "//src/javatests/com/google/devtools/deviceinfra/builddefs:truth",
        "//src/javatests/com/google/devtools/mobileharness/shared/util/runfiles:runfiles_util",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@maven//:javax_inject_jsr330_api",
        "@maven//:junit_junit",
        "@maven//:org_jline_jline",
        "@maven//:org_mockito_mockito_core",
    ],
)

junit_test_suites(
    name = "tests",
    sizes = [
        "small",
    ],
    deps = [":console"],
)
