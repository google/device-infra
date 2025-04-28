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
    default_visibility = [
        "//:deviceinfra_all_pkg",
        "//:omnilab_fusion",
    ],
)

java_library(
    name = "mobileharness_host_log_manager",
    srcs = [
        "MobileHarnessHostLogManager.java",
        "MobileHarnessHostLogManagerModule.java",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/logging/controller:log_entry_uploader_manager",
        "//src/java/com/google/devtools/mobileharness/shared/logging/controller/handler",
        "//src/java/com/google/devtools/mobileharness/shared/logging/controller/queue",
        "//src/java/com/google/devtools/mobileharness/shared/logging/parameter",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
    ],
)
