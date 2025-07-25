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
#   Events for Mobile Harness test message mechanism.

load("@rules_java//java:defs.bzl", "java_library")

package(
    default_applicable_licenses = ["//:license"],
)

java_library(
    name = "event",
    srcs = [
        "TestMessageEvent.java",
    ],
    visibility = ["//src/java/com/google/wireless/qa/mobileharness/shared/comm/message:test_message_users"],
    deps = [
        "//src/devtools/mobileharness/api/query/proto:device_query_java_proto",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/messaging/message",
        "//src/java/com/google/wireless/qa/mobileharness/shared/comm/message:proto_test_message_util",
        "//src/java/com/google/wireless/qa/mobileharness/shared/controller/event:base_test_events",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/allocation",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "@com_google_protobuf//:protobuf_java",
    ],
)
