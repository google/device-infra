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
    name = "command_helper",
    srcs = ["CommandHelper.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/console/command:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/console/util/console:__pkg__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/common:xts_type_loader",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console:console_info",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "exit_util",
    srcs = ["ExitUtil.java"],
    visibility = ["//src/java/com/google/devtools/mobileharness/infra/ats/console/command:__subpackages__"],
    deps = [
        "//src/devtools/mobileharness/infra/ats/console/controller/proto:session_plugin_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/command:run_command",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/olcserver:ats_session_stub",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/console:console_util",
        "//src/java/com/google/devtools/mobileharness/shared/constant:log_record_importance",
        "//src/java/com/google/devtools/mobileharness/shared/util/error:more_throwables",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:sleeper",
        "@maven//:javax_inject_jsr330_api",
    ],
)
