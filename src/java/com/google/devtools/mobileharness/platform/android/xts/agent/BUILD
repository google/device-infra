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

java_binary(
    name = "tradefed_invocation_agent",
    srcs = ["TradefedInvocationAgent.java"],
    deploy_manifest_lines = [
        "Premain-Class: com.google.devtools.mobileharness.platform.android.xts.agent.TradefedInvocationAgent",
    ],
    visibility = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/platform/android/xts/agent:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/shared/size:__pkg__",
    ],
    deps = [
        ":tradefed_invocation_agent_logger",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/runtime:xts_tradefed_runtime_info_monitor",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:net_bytebuddy_byte_buddy",
    ],
)

java_library(
    name = "tradefed_invocation_agent_logger",
    srcs = ["TradefedInvocationAgentLogger.java"],
    deps = [
        "@maven//:com_google_code_findbugs_jsr305",
    ],
)
