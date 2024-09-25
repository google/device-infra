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

package(default_applicable_licenses = ["//:license"])

java_library(
    name = "xts_tradefed_runtime_info_file_util",
    srcs = ["XtsTradefedRuntimeInfoFileUtil.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/sessionplugin:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/platform/android/xts/agent:__pkg__",
    ],
    deps = [
        ":xts_tradefed_runtime_info",
        "@maven//:com_google_code_findbugs_jsr305",
    ],
)

java_library(
    name = "xts_tradefed_runtime_info",
    srcs = ["XtsTradefedRuntimeInfo.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/sessionplugin:__pkg__",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/platform/android/xts/agent:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/platform/android/xts/runtime:__pkg__",
        "//src/javatests/com/google/wireless/qa/mobileharness/shared/api/driver:__pkg__",
    ],
)

java_library(
    name = "xts_tradefed_runtime_info_monitor",
    srcs = ["XtsTradefedRuntimeInfoMonitor.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/agent:__pkg__",
    ],
    deps = [
        ":xts_tradefed_runtime_info",
        ":xts_tradefed_runtime_info_file_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_code_findbugs_jsr305",
    ],
)
