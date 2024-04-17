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

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//:deviceinfra_all_pkg",
    ],
)

# Utility class to allow access to useful Flogger data structures wrapped in LogRecord instances
# without needing to depend on internal Flogger types.
java_library(
    name = "log_data",
    srcs = ["LogDataExtractor.java"],
    deps = ["//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger"],
)

java_library(
    name = "log_entry_util",
    srcs = ["LogEntryUtil.java"],
    deps = [
        ":log_data",
        "//src/java/com/google/devtools/deviceinfra/shared/logging/parameter:log_environment",
        "//src/java/com/google/devtools/deviceinfra/shared/logging/parameter:log_project",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/base",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/network",
        "@com_google_googleapis//google/api:api_java_proto",
        "@com_google_googleapis//google/logging/type:type_java_proto",
        "@com_google_protobuf//:protobuf_java_util",
        "@maven//:com_google_api_grpc_proto_google_cloud_logging_v2",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
    ],
)
