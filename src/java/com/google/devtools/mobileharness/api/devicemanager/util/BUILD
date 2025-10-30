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
    name = "class_util",
    srcs = [
        "ClassUtil.java",
    ],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/controller/device:__subpackages__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/dispatcher:base",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api",
        "//src/java/com/google/wireless/qa/mobileharness/shared/util:reflection",
    ],
)

java_library(
    name = "persistent_device_info_manager",
    srcs = ["PersistentDeviceInfoManager.java"],
    deps = [
        "//src/devtools/mobileharness/api/devicemanager/proto:persistent_device_info_java_proto",
        "//src/java/com/google/devtools/mobileharness/shared/util/base:proto_text_format",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)
