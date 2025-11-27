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
        "//src/java/com/google/devtools/mobileharness/service/deviceconfig:__subpackages__",
    ],
)

java_library(
    name = "device_config_service_impl",
    srcs = ["DeviceConfigServiceImpl.java"],
    deps = [
        "//src/devtools/mobileharness/api/deviceconfig/proto:device_config_service_java_proto",
        "//src/devtools/mobileharness/api/deviceconfig/proto:device_java_proto",
        "//src/devtools/mobileharness/api/deviceconfig/proto:lab_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/service/deviceconfig/storage:storage_client",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:javax_inject_jsr330_api",
    ],
)
