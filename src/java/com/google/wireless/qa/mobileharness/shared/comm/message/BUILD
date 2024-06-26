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

# Description:
#   Mobile Harness test message mechanism.

package(default_applicable_licenses = ["//:license"])

package_group(
    name = "test_message_users",
    includes = [
        "//:deviceinfra_all_pkg",
    ],
)

java_library(
    name = "cacheable_test_message_handler",
    srcs = ["CacheableTestMessageHandler.java"],
    visibility = [
        # Do not make it public.
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode/remote:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:__pkg__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/constant/closeable:mobile_harness_auto_closeable",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/messaging/message",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:callables",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:more_futures",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "proto_test_message_util",
    srcs = ["ProtoTestMessageUtil.java"],
    visibility = [
        # Do not make it public.
        "//src/java/com/google/wireless/qa/mobileharness/shared/comm/message/event:__pkg__",
    ],
    deps = [
        "@com_google_protobuf//:protobuf_java",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "test_message_manager",
    srcs = [
        "TestMessageManager.java",
    ],
    visibility = [
        "//:deviceinfra_all_pkg",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/messaging/message",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/messaging/poster",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "message",
    srcs = [
        "TestMessageUtil.java",
    ],
    visibility = [":test_message_users"],
    deps = [
        ":proto_test_message_util",
        ":test_message_manager",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/messaging/message",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "@com_google_protobuf//:protobuf_java",
        "@maven//:com_google_guava_guava",
    ],
)
