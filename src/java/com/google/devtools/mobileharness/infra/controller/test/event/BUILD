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
    name = "event",
    srcs = ["TestExecutionEndedEvent.java"],
    visibility = [
        "//:omnilab_fusion",
        "//src/java/com/google/devtools/mobileharness/infra/client:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/lab/controller:__pkg__",
    ],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:test_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/allocation",
    ],
)

java_library(
    name = "event_impl",
    srcs = [
        "TestEndedEventImpl.java",
        "TestEndingEventImpl.java",
        "TestStartedEventImpl.java",
        "TestStartingEventImpl.java",
    ],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/controller/test:__subpackages__",
    ],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/devtools/mobileharness/api/query/proto:lab_query_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/allocation",
        "//src/java/com/google/devtools/mobileharness/api/testrunner/event/test:client_test_events",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "local_event_impl",
    srcs = [
        "LocalDecoratorPostForwardEventImpl.java",
        "LocalDecoratorPreForwardEventImpl.java",
        "LocalDriverEndedEventImpl.java",
        "LocalDriverStartingEventImpl.java",
        "LocalTestEndedEventImpl.java",
        "LocalTestEndingEventImpl.java",
    ],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/controller/test:__subpackages__",
    ],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/devtools/mobileharness/api/query/proto:lab_query_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/allocation",
        "//src/java/com/google/devtools/mobileharness/api/testrunner/event/test",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)
