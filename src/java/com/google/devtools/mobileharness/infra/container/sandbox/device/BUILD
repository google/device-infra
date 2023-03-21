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

java_library(
    name = "device_sandbox_controller",
    srcs = ["DeviceSandboxController.java"],
    visibility = [
        "//src/java/com/google/devtools/deviceinfra/ext/devicemanagement/device/platform/android:__subpackages__",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api:__subpackages__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/model:test_execution_unit",
        "//src/java/com/google/devtools/mobileharness/platform/android/sdktool/adb:usb_device_locator",
    ],
)

java_library(
    name = "base",
    srcs = ["BaseDeviceSandboxController.java"],
    visibility = [
    ],
    deps = [
        ":device_sandbox_controller",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api",
    ],
)

java_library(
    name = "no_op_device",
    srcs = ["NoOpDeviceSandboxController.java"],
    visibility = [
        "//src/java/com/google/devtools/deviceinfra/ext/devicemanagement/device/platform/android:__subpackages__",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api:__subpackages__",
    ],
    deps = [
        ":base",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/model:test_execution_unit",
        "//src/java/com/google/devtools/mobileharness/platform/android/sdktool/adb:usb_device_locator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api",
    ],
)

java_library(
    name = "unsupported_device",
    srcs = ["UnsupportedDeviceSandboxController.java"],
    visibility = [
        "//java/com/google/wireless/qa/mobileharness/shared/api:__pkg__",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api:__subpackages__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/container/sandbox/device:device_sandbox_controller",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/model:test_execution_unit",
        "//src/java/com/google/devtools/mobileharness/platform/android/sdktool/adb:usb_device_locator",
    ],
)
