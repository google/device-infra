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
    "//src/java/com/google/devtools/deviceinfra/ext/devicemanagement/device/platform/android:__subpackages__",
    "//src/java/com/google/devtools/deviceinfra/infra/client:__subpackages__",
    "//src/java/com/google/devtools/mobileharness/api:__subpackages__",
    "//src/java/com/google/devtools/mobileharness/infra/controller/device:__subpackages__",
    "//src/java/com/google/devtools/mobileharness/infra/controller/scheduler:__subpackages__",
    "//src/java/com/google/devtools/mobileharness/infra/controller/test:__subpackages__",
    "//src/java/com/google/devtools/mobileharness/infra/shared/api:__subpackages__",
    "//src/java/com/google/wireless/qa/mobileharness/shared/model:__subpackages__",
    "//src/javatests/com/google/devtools/deviceinfra/infra/client:__subpackages__",
    "//src/javatests/com/google/devtools/mobileharness/api/devicemanager:__subpackages__",
    "//src/javatests/com/google/devtools/mobileharness/infra/controller/scheduler:__subpackages__",
    "//src/javatests/com/google/devtools/mobileharness/infra/shared/api:__subpackages__",
]

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = DEFAULT_VISIBILITY,
)

java_library(
    name = "locator",
    srcs = [
        "DeviceLocator.java",
        "LabLocator.java",
    ],
    visibility = DEFAULT_VISIBILITY + [
    ],
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

java_library(
    name = "schedule_unit",
    srcs = [
        "DeviceScheduleUnit.java",
        "LabScheduleUnit.java",
    ],
    deps = [
        ":locator",
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/lab/in",
        "//src/java/com/google/devtools/mobileharness/api/model/lab/in:lite_dimension_factory",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "device_id_descriptor",
    srcs = ["DeviceId.java"],
    visibility = DEFAULT_VISIBILITY,
    deps = [
        "//src/java/com/google/devtools/deviceinfra/shared/util/auto:auto_value",
    ],
)

java_library(
    name = "device_info",
    srcs = [
        "DeviceInfo.java",
    ],
    visibility = DEFAULT_VISIBILITY + [
        "//:deviceinfra_pkg",
    ],
    deps = [
        ":device_id_descriptor",
        "//src/java/com/google/devtools/deviceinfra/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/api/model/lab/in",
        "//src/java/com/google/devtools/mobileharness/api/model/lab/in:dimension",
        "//src/java/com/google/devtools/mobileharness/api/model/lab/out:property",
    ],
)

java_library(
    name = "lab",
    exports = [
        ":device_id_descriptor",
        ":device_info",
        ":locator",
        ":schedule_unit",
        "//src/java/com/google/devtools/mobileharness/api/model/lab/in",
        "//src/java/com/google/devtools/mobileharness/api/model/lab/out",
    ],
)

java_library(
    name = "device_info_factory",
    srcs = [
        "DeviceInfoFactory.java",
    ],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/controller/device:__pkg__",
    ],
    deps = [
        ":device_id_descriptor",
        ":device_info",
        "//src/java/com/google/devtools/mobileharness/api/model/lab/in:dimension_factory",
        "//src/java/com/google/devtools/mobileharness/api/model/lab/in:local_dimension",
        "//src/java/com/google/devtools/mobileharness/api/model/lab/out:property_factory",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device/config",
        "@maven//:com_google_code_findbugs_jsr305",
    ],
)

java_library(
    name = "lite_device_info_factory",
    srcs = [
        "LiteDeviceInfoFactory.java",
    ],
    visibility = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/api:__pkg__",
    ],
    deps = [
        ":device_id_descriptor",
        ":device_info",
        "//src/java/com/google/devtools/mobileharness/api/model/lab/in:lite_dimension_factory",
        "//src/java/com/google/devtools/mobileharness/api/model/lab/out:local_property_factory",
    ],
)
