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
    name = "device_id_util",
    srcs = ["DeviceIdUtil.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/proxy:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device:__pkg__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/lab:device_id_descriptor",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dimension",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "device_reboot_util",
    srcs = [
        "DeviceRebootUtil.java",
    ],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/controller/device:__pkg__",
    ],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/devtools/mobileharness/api/model/proto:test_java_proto",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device/config",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/model:test_execution_result",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api",
        "//src/java/com/google/wireless/qa/mobileharness/shared/controller/stat",
    ],
)

java_library(
    name = "device_status_info_printer",
    srcs = ["DeviceStatusInfoPrinter.java"],
    visibility = [
        "//java/com/google/devtools/mobileharness/infra/lab/controller/wrangler:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode/local:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/lab/controller:__pkg__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/infra/controller/device:device_status_info",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "conditioned_device_config_util",
    srcs = ["ConditionedDeviceConfigUtil.java"],
    visibility = [
        "//src/java/com/google/devtools/deviceinfra/ext/devicemanagement/device/platform/android/realdevice:__pkg__",
    ],
    deps = [
        "//src/devtools/mobileharness/api/deviceconfig/proto:conditioned_device_config_java_proto",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "@maven//:com_google_guava_guava",
        "@protobuf//:protobuf_java",
    ],
)
