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

package(default_applicable_licenses = ["//:license"])

java_library(
    name = "google_logger",
    visibility = ["//:deviceinfra_all_pkg"],
    exports = [
        "@maven//:com_google_flogger_flogger",
        "@maven//:com_google_flogger_flogger_system_backend",
    ],
)

java_library(
    name = "formatter",
    srcs = ["MobileHarnessLogFormatter.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/shared/logging:__subpackages__",
        "//src/java/com/google/wireless/qa/mobileharness/shared:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/client/api:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/client/longrunningservice/controller:__pkg__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/context:invocation_context",
        "//src/java/com/google/devtools/mobileharness/shared/util/command/linecallback:command_output_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "log_data_extractor",
    srcs = ["LogDataExtractor.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/console:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/controller:__pkg__",
        "//src/java/com/google/devtools/mobileharness/shared/constant:__pkg__",
        "//src/java/com/google/devtools/mobileharness/shared/logging/util:__pkg__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_code_findbugs_jsr305",
    ],
)

java_library(
    name = "tag",
    srcs = ["MobileHarnessLogTag.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/client/api/controller/job:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/platform/testbed/adhoc/controller:__pkg__",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:__pkg__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/context:invocation_context",
    ],
)
