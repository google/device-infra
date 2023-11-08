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
    name = "device_cache_manager",
    srcs = ["DeviceCacheManager.java"],
    visibility = [
        # Do NOT add visibility here.
        "//:deviceinfra_devicemanagement_pkg",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@com_google_protobuf//:protobuf_java_util",
        "@com_google_protobuf_protobuf_java",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "device_cache_interface",
    srcs = ["DeviceCache.java"],
    visibility = [
        # Do not use this target directly. Use "device_cache" instead.
    ],
    deps = [
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "local_device_cache",
    srcs = ["LocalDeviceCache.java"],
    visibility = [
        # Do not use this target directly. Use "device_cache" instead.
        "//javatests/com/google/devtools/mobileharness/api/testrunner/device/cache:__pkg__",
    ],
    deps = [
        ":device_cache_interface",
        ":device_cache_manager",
    ],
)

java_library(
    name = "device_cache",
    visibility = [
        "//:deviceinfra_pkg",
    ],
    exports = [":device_cache_interface"],
    runtime_deps = [
        ":local_device_cache",
    ],
)

java_library(
    name = "device_cache_lite",
    visibility = [
    ],
    exports = [":device_cache_interface"],
    runtime_deps = [
        ":local_device_cache",
    ],
)
