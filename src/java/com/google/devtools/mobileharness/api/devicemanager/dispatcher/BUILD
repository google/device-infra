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

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//:deviceinfra_all_pkg",
        "//:deviceinfra_devicemanagement_pkg",
    ],
)

java_library(
    name = "base",
    srcs = [
        "BaseDispatcher.java",
        "Dispatcher.java",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/detector/model",
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/dispatcher/model",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:device_id_descriptor",
        "@maven//:com_google_code_findbugs_jsr305",
    ],
)

java_library(
    name = "cacheable",
    srcs = ["CacheableDispatcher.java"],
    deps = [
        ":base",
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/detector/model",
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/dispatcher/model",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/testrunner/device/cache:device_cache_manager",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "android_real_device",
    srcs = ["AndroidRealDeviceDispatcher.java"],
    deps = [
        ":cacheable",
        "//src/java/com/google/devtools/deviceinfra/platform/android/sdk/fastboot",
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/detector/model",
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/dispatcher/model",
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/dispatcher/util:device_id_descriptor_generator",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:device_id_descriptor",
        "//src/java/com/google/devtools/mobileharness/platform/android/sdktool/adb:enums",
        "//src/java/com/google/devtools/mobileharness/shared/util/error:more_throwables",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared/android:aapt",
        "//src/java/com/google/wireless/qa/mobileharness/shared/util:device",
        "@maven//:com_google_guava_guava",
    ],
)

################################################################################
## Debug related
################################################################################
java_library(
    name = "no_op_device",
    srcs = ["NoOpDeviceDispatcher.java"],
    deps = [
        ":base",
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/detector/model",
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/dispatcher/model",
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/dispatcher/util:device_id_descriptor_generator",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:device_id_descriptor",
        "@maven//:com_google_guava_guava",
    ],
)
