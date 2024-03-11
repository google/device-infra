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

# Session plugin for ats server requests.

package(default_applicable_licenses = ["//:license"])

java_library(
    name = "ats_server_session_plugin",
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/common/olcserver:__subpackages__",
    ],
    runtime_deps = [
        ":ats_server_session_plugin_lib",
        ":ats_server_session_plugin_module",
    ],
)

java_library(
    name = "ats_server_session_plugin_lib",
    srcs = ["AtsServerSessionPlugin.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/common/olcserver:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/server/sessionplugin:__pkg__",
    ],
    deps = [
        ":new_multi_command_request_handler",
        "//src/devtools/mobileharness/infra/ats/console/result/proto:report_java_proto",
        "//src/devtools/mobileharness/infra/ats/server/proto:service_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common:session_request_handler_util",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/model:session_event",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/model:session_info",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:time_utils",
        "//src/java/com/google/wireless/qa/mobileharness/client/api/event:job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:property",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_java_proto",
        "@com_google_protobuf//:protobuf_java",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "new_multi_command_request_handler",
    srcs = ["NewMultiCommandRequestHandler.java"],
    visibility = ["//src/javatests/com/google/devtools/mobileharness/infra/ats/server/sessionplugin:__pkg__"],
    deps = [
        "//src/devtools/mobileharness/infra/ats/common/proto:xts_common_java_proto",
        "//src/devtools/mobileharness/infra/ats/server/proto:service_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common:session_request_handler_util",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/model:session_info",
        "//src/java/com/google/devtools/mobileharness/infra/lab/common/dir",
        "//src/java/com/google/devtools/mobileharness/shared/util/command",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/path",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "@com_google_protobuf//:protobuf_java",
        "@com_google_protobuf//:protobuf_java_util",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "ats_server_session_plugin_module",
    srcs = ["AtsServerSessionPluginModule.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/report:compatibility_report_module",
        "@maven//:com_google_inject_guice",
    ],
)
