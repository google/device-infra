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
    name = "local_command_history_manager",
    srcs = ["LocalCommandHistoryManager.java"],
    visibility = [
        "//javatests/com/google/devtools/mobileharness/shared/commandhistory/controller:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/shared/subprocess/agent:__subpackages__",
    ],
    deps = [
        "//src/devtools/mobileharness/shared/commandhistory/proto:command_record_java_proto",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:time_utils",
        "@maven//:com_google_errorprone_error_prone_annotations",
    ],
)

java_library(
    name = "local_command_history_recorder",
    srcs = ["LocalCommandHistoryRecorder.java"],
    visibility = [
        # Do NOT add visibility here.
        "//javatests/com/google/devtools/mobileharness/shared/commandhistory/controller:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/shared/subprocess/agent:__subpackages__",
    ],
    deps = [
        ":local_command_history_manager",
        "//src/devtools/mobileharness/shared/commandhistory/proto:command_record_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/converter",
        "//src/java/com/google/devtools/mobileharness/shared/subprocess/listener:process_builder_listener",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:more_futures",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:thread_factory_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:time_utils",
        "@com_google_protobuf//:protobuf_java_util",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)
