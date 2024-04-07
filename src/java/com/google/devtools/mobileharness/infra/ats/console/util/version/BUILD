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
    name = "version_message_util",
    srcs = ["VersionMessageUtil.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console:__subpackages__",
    ],
    deps = [
        ":version_parser",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console:console_info",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/command:command_helper",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite:test_suite_info",
        "//src/java/com/google/devtools/mobileharness/shared/version",
        "//src/java/com/google/devtools/mobileharness/shared/version:version_util",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "version_parser",
    srcs = ["VersionParser.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite:test_suite_info",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:javax_inject_jsr330_api",
    ],
)
