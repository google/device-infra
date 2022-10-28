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

# Description:
#    Libs to access external device manager.

package(
    default_visibility = [
        "//java/com/google/devtools/deviceinfra/host/utrs:__subpackages__",
        "//java/com/google/devtools/mobileharness/infra/controller:__subpackages__",
        "//java/com/google/devtools/mobileharness/infra/lab:__subpackages__",
        "//java/com/google/wireless/qa/mobileharness/client/api/mode/local:__subpackages__",
        "//java/com/google/wireless/qa/mobileharness/tool/android:__subpackages__",
        "//javatests/com/google/devtools/deviceinfra/host/utrs:__subpackages__",
        "//javatests/com/google/devtools/mobileharness/infra/controller:__subpackages__",
        "//javatests/com/google/devtools/mobileharness/infra/controller/device/external:__subpackages__",
        "//javatests/com/google/devtools/mobileharness/infra/lab:__subpackages__",
        "//javatests/com/google/wireless/qa/mobileharness/client/api/mode/local:__subpackages__",
        "//src/java/com/google/devtools/deviceinfra/infra/client/api/mode/local:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device:__subpackages__",
        "//src/javatests/com/google/devtools/deviceinfra/infra/client/api/mode/local:__subpackages__",
    ],
)

java_library(
    name = "external_device_manager",
    srcs = [
        "ExternalDeviceManager.java",
    ],
    deps = [
        "//java/com/google/devtools/deviceinfra/host/daemon/health/handler:drain_handler",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
    ],
)

java_library(
    name = "noop_external_device_manager",
    srcs = [
        "NoopExternalDeviceManager.java",
    ],
    deps = [
        ":external_device_manager",
    ],
)
