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
#   Shared IO libraries.
package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//:deviceinfra_all_pkg",
    ],
)

java_library(
    name = "base",
    srcs = [
        "ArrayUtil.java",
    ],
    deps = [
    ],
)

java_library(
    name = "device",
    srcs = ["DeviceUtil.java"],
    deps = [
        "//src/java/com/google/devtools/deviceinfra/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/system",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dimension",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/lab",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "device_info",
    srcs = ["DeviceInfoUtil.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness:__subpackages__",
        "//src/java/com/google/wireless/qa/mobileharness/shared/controller/event:__pkg__",
    ],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/devtools/mobileharness/api/query/proto:device_query_java_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "flag_parser",
    srcs = ["FlagUtil.java"],
    visibility = [
        "//:deviceinfra_all_pkg",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "luhn",
    srcs = ["LuhnUtil.java"],
)

java_library(
    name = "resolution",
    srcs = [
        "ScreenResolution.java",
    ],
    deps = [
        "//src/java/com/google/devtools/deviceinfra/shared/util/auto:auto_value",
    ],
)

java_library(
    name = "net_util",
    srcs = ["NetUtil.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/controller/device/config:__pkg__",
        "//src/java/com/google/devtools/mobileharness/platform/android/connectivity:__pkg__",
        "//src/javatests/com/google/wireless/qa/mobileharness/shared/util:__pkg__",
    ],
    deps = [
        ":device",
        "//src/java/com/google/devtools/deviceinfra/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/deviceinfra/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/network",
        "//src/java/com/google/devtools/mobileharness/shared/util/network/localhost",
        "//src/java/com/google/devtools/mobileharness/shared/util/system",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dimension",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "reflection",
    srcs = ["ReflectionUtil.java"],
    visibility = [
        "//:deviceinfra_all_pkg",
    ],
    deps = [
        "//src/java/com/google/devtools/deviceinfra/api/error:exception",
        "//src/java/com/google/devtools/deviceinfra/shared/util/reflection:reflection_util",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/error:error_model_converter",
    ],
)
