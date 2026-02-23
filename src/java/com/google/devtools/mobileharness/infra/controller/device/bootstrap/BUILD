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

package(
    default_applicable_licenses = ["//:license"],
)

java_library(
    name = "all_detectors_and_dispatchers",
    srcs = ["AllDetectorsAndDispatchers.java"],
    visibility = [
        "//src/javatests/com/google/devtools/mobileharness/infra/controller/device/bootstrap:__subpackages__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/detector:adb",
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/detector:base",
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/detector:failed_device",
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/detector:fastboot",
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/detector:no_op",
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/detector/model:android_emulator_type",
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/dispatcher:android_local_emulator",
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/dispatcher:android_real_device",
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/dispatcher:base",
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/dispatcher:no_op_device",
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/util:class_util",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device:dispatcher_manager",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/reflection:reflection_util",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "detector_dispatcher_selector",
    srcs = ["DetectorDispatcherSelector.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode/local:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/lab:__pkg__",
    ],
    deps = [
        ":all_detectors_and_dispatchers",
        ":detectors_and_dispatchers",
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/detector:base",
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/dispatcher:base",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device:dispatcher_manager",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:thread_pools",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "detectors_and_dispatchers",
    srcs = ["DetectorsAndDispatchers.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode/local:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/lab:__pkg__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/detector:base",
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/dispatcher:base",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "@maven//:com_google_guava_guava",
    ],
)
