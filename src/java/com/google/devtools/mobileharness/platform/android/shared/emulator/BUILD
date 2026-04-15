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
    name = "emulator_id",
    srcs = ["AndroidEmulatorIds.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/dispatcher/util:__pkg__",
        "//src/java/com/google/devtools/mobileharness/platform/android:__subpackages__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
    ],
)

java_library(
    name = "android_jit_emulator_util",
    srcs = ["AndroidJitEmulatorUtil.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/detector:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/ats/tradefed:__pkg__",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/platform/android/shared/emulator:__subpackages__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "cloud_orchestrator_client",
    srcs = [
        "CloudOrchestratorClient.java",
        "CloudOrchestratorMessages.java",
    ],
    visibility = [
        "//javatests/com/google/wireless/qa/mobileharness/shared/api/device:__pkg__",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/platform/android/shared/emulator:__subpackages__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent/retry",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_http_client_google_http_client",
        "@maven//:com_google_http_client_google_http_client_gson",
    ],
)
