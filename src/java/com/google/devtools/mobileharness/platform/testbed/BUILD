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
    name = "util",
    srcs = [
        "TestbedUtil.java",
    ],
    visibility = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:__pkg__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/platform/testbed/config:base",
        "//src/java/com/google/devtools/mobileharness/shared/util/base:proto_extension_registry",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:device_model_java_proto",
        "@com_google_protobuf//:protobuf_java",
    ],
)

java_library(
    name = "runner_util",
    srcs = [
        "SubDeviceDecoratorStack.java",
        "TestbedTestRunnerUtil.java",
    ],
    visibility = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:__pkg__",
    ],
    deps = [
        ":subdevice_synchronization_driver",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:test_context",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver:driver_factory",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_java_proto",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "subdevice_synchronization_driver",
    srcs = [
        "SubDeviceSynchronizationDriver.java",
    ],
    visibility = [
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver:base_driver",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
    ],
)
