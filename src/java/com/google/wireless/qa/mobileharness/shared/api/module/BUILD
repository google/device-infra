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

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = ["//:deviceinfra_all_pkg"],
)

java_library(
    name = "xts_tradefed_test_module",
    srcs = ["XtsTradefedTestModule.java"],
    visibility = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/api:__subpackages__",
        "//src/javatests/com/google/wireless/qa/mobileharness/shared/api:__subpackages__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/report:compatibility_report_module",
        "@maven//:com_google_inject_guice",
    ],
)

java_library(
    name = "cros_ls_nexus_decorator_module",
    srcs = ["CrosLsNexusDecoratorModule.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/command",
        "//src/java/com/google/devtools/mobileharness/shared/util/port:portprober",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:cros_ls_nexus_decorator",
        "@maven//:com_google_inject_guice",
    ],
)
