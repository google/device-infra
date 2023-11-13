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

# Device Action Framework
DEFAULT_VISIBILITY = [
    "//src/java/com/google/devtools/deviceaction:deviceaction_java_pkg",
    "//src/javatests/com/google/devtools/deviceaction:deviceaction_javatests_pkg",
]

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = DEFAULT_VISIBILITY,
)

java_library(
    name = "framework",
    srcs = [
        "ActionConfigurer.java",
        "MergingDeviceConfigurer.java",
    ],
    deps = [
        "//src/devtools/common/metrics/stability/model/proto:error_java_proto",
        "//src/devtools/deviceaction/schemas/proto:base_spec_java_proto",
        "//src/java/com/google/devtools/deviceaction/common/error",
        "//src/java/com/google/devtools/deviceaction/common/schemas",
        "//src/java/com/google/devtools/deviceaction/common/utils",
        "//src/java/com/google/devtools/deviceaction/framework/deviceconfigs",
        "//src/java/com/google/devtools/deviceaction/framework/devices",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "device_action_module",
    srcs = ["DeviceActionModule.java"],
    deps = [
        ":framework",
        "//src/java/com/google/devtools/deviceaction/common/annotations",
        "//src/java/com/google/devtools/deviceaction/common/utils",
        "//src/java/com/google/devtools/deviceaction/common/utils:module",
        "//src/java/com/google/devtools/deviceaction/framework/actions",
        "//src/java/com/google/devtools/deviceaction/framework/deviceconfigs",
        "//src/java/com/google/devtools/deviceaction/framework/devices",
        "//src/java/com/google/devtools/mobileharness/platform/android/file:android_file_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/packagemanager:android_package_manager_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/sdktool/adb:adb_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/systemsetting:system_setting_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/systemstate:system_state_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/quota",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:sleeper",
        "@maven//:com_google_inject_guice",
        "@maven//:javax_inject_jsr330_api",
    ],
)
