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
#   Mobile Harness test events.

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//:deviceinfra_all_pkg",
    ],
)

java_library(
    name = "test",
    exports = [
        ":client_test_events",
        ":lab_test_events",
        "//src/java/com/google/devtools/mobileharness/api/testrunner/plugin:annotation",
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
        ":base",
    ],
)

java_library(
    name = "lab_test_events",
    srcs = [
        "LocalDecoratorPostForwardEvent.java",
        "LocalDecoratorPreForwardEvent.java",
        "LocalDriverEndedEvent.java",
        "LocalDriverStartingEvent.java",
        "LocalTestEndedEvent.java",
        "LocalTestEndingEvent.java",
    ],
    deps = [
        ":base",
        ":client_test_events",
        ":lab_base",
    ],
)

java_library(
    name = "base",
    srcs = [
        "DeviceFeaturedEvent.java",
        "ExecutionEndEvent.java",
        "TestEvent.java",
    ],
    visibility = [
        # Only for Mobile Harness internal use.
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/util:__subpackages__",
    ],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/devtools/mobileharness/api/query/proto:lab_query_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/allocation",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "lab_base",
    srcs = [
        "DeviceHelperEvent.java",
        "DriverEvent.java",
    ],
    visibility = [
        # Only for Mobile Harness internal use.
    ],
    deps = [
        ":base",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
    ],
)
