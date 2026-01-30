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

load("@rules_java//java:java_library.bzl", "java_library")

package(default_applicable_licenses = ["//:license"])

java_library(
    name = "device_provider",
    srcs = ["DeviceProvider.java"],
    visibility = [
        "//:omnilab_fusion",
        "//src/java/com/google/devtools/mobileharness/infra/lab:__subpackages__",
    ],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "local_device_provider",
    srcs = ["LocalDeviceProvider.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/lab:__subpackages__",
    ],
    deps = [
        ":device_provider",
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device:device_helper_factory",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "allocated_device_provider",
    srcs = ["AllocatedDeviceProvider.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/lab:__subpackages__",
    ],
    deps = [
        ":device_provider",
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:device_info",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:lite_device_info_factory",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api:class_util",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:device_factory",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "allocated_device_provisioning_module",
    srcs = ["AllocatedDeviceProvisioningModule.java"],
    visibility = [
        "//:omnilab_fusion",
        "//src/java/com/google/devtools/mobileharness/infra/lab:__subpackages__",
    ],
    deps = [
        ":allocated_device_provider",
        ":device_provider",
        "@maven//:com_google_inject_guice",
    ],
)

java_library(
    name = "local_device_provisioning_module",
    srcs = ["LocalDeviceProvisioningModule.java"],
    visibility = [
        "//:omnilab_fusion",
        "//src/java/com/google/devtools/mobileharness/infra/lab:__subpackages__",
    ],
    deps = [
        ":device_provider",
        ":local_device_provider",
        "@maven//:com_google_inject_guice",
    ],
)
