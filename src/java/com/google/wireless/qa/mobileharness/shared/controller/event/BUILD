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
#   Events of the shared controller libs.

load("@rules_java//java:defs.bzl", "java_library")

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = ["//visibility:public"],
)

java_library(
    name = "base",
    srcs = [
        "ControllerEvent.java",
    ],
)

java_library(
    name = "device",
    srcs = [
        "ControllerEvent.java",
        "LocalDeviceAllcoatedEvent.java",
        "LocalDeviceChangeEvent.java",
        "LocalDeviceDownEvent.java",
        "LocalDeviceErrorEvent.java",
        "LocalDeviceEvent.java",
        "LocalDeviceUpEvent.java",
    ],
    deps = [
        "//src/devtools/common/metrics/stability/model/proto:error_java_proto",
    ],
)

java_library(
    name = "allocation",
    srcs = [
        "AllocationEvent.java",
        "ControllerEvent.java",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/allocation",
    ],
)

java_library(
    name = "test",
    exports = [
        ":base_test_events",
        ":client_test_events",
        ":lab_test_events",
    ],
)

java_library(
    name = "client_test_events",
    srcs = [
        "TestEndedEvent.java",
        "TestEndingEvent.java",
        "TestStartedEvent.java",
        "TestStartingEvent.java",
    ],
    deps = [
        ":base_test_events",
        "//src/devtools/mobileharness/api/query/proto:device_query_java_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/allocation",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "lab_test_events",
    srcs = [
        "LocalTestEndedEvent.java",
        "LocalTestEndingEvent.java",
        "LocalTestEvent.java",
        "LocalTestStartedEvent.java",
        "LocalTestStartingEvent.java",
    ],
    deps = [
        ":base",
        ":client_test_events",
        "//src/devtools/mobileharness/api/query/proto:device_query_java_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/controller/event/util:injection_event",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/allocation",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "base_test_events",
    srcs = [
        "DeviceCheckedTestEvent.java",
        "TestEvent.java",
    ],
    deps = [
        ":base",
        "//src/devtools/mobileharness/api/query/proto:device_query_java_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/controller/event/util:injection_event",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/allocation",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/lab",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)
