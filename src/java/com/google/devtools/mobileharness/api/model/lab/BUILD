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
#   Shared data models for Mobile Harness labs and devices.

DEFAULT_VISIBILITY = [
    "//src/java/com/google/devtools/deviceinfra/infra/client:__subpackages__",
    "//src/java/com/google/devtools/mobileharness/api:__subpackages__",
    "//src/java/com/google/devtools/mobileharness/infra/controller/scheduler:__subpackages__",
    "//src/java/com/google/devtools/mobileharness/infra/controller/device:__subpackages__",
    "//src/java/com/google/devtools/mobileharness/infra/shared/api:__subpackages__",
    "//src/java/com/google/wireless/qa/mobileharness/shared/model:__subpackages__",
    "//src/javatests/com/google/devtools/deviceinfra/infra/client:__subpackages__",
    "//src/java/com/google/devtools/mobileharness/infra/controller/test:__subpackages__",
    "//src/javatests/com/google/devtools/mobileharness/api/devicemanager:__subpackages__",
    "//src/javatests/com/google/devtools/mobileharness/infra/controller/scheduler:__subpackages__",
    "//src/javatests/com/google/devtools/mobileharness/infra/shared/api:__subpackages__",
]

package(
    default_visibility = DEFAULT_VISIBILITY,
)

java_library(
    name = "locator",
    srcs = [
        "DeviceLocator.java",
        "LabLocator.java",
    ],
    visibility = DEFAULT_VISIBILITY,
    exports = [
        "//src/java/com/google/devtools/mobileharness/api/model/lab/in:ports",
    ],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/devtools/mobileharness/api/model/proto:lab_java_proto",
        "//src/java/com/google/devtools/deviceinfra/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/api/model/lab/in:ports",
        "@maven//:com_google_errorprone_error_prone_annotations",
    ],
)
