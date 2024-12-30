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

package(default_applicable_licenses = ["//:license"])

java_library(
    name = "device_proxy",
    srcs = ["DeviceProxy.java"],
    visibility = ["//src/java/com/google/devtools/mobileharness/infra/controller/device/proxy:__pkg__"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
    ],
)

java_library(
    name = "all_device_proxies",
    visibility = ["//java/com/google/wireless/qa/mobileharness/shared/api:__pkg__"],
    runtime_deps = [
        ":no_op_device_proxy",
    ],
)

################################################################################
# Proxy implementations in alphabetical order
################################################################################

java_library(
    name = "no_op_device_proxy",
    srcs = ["NoOpDeviceProxy.java"],
    visibility = ["//src/javatests/com/google/devtools/mobileharness/infra/controller/device/proxy:__pkg__"],
    deps = [
        ":device_proxy",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/job:locator",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:device_id_descriptor",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:lite_device_info_factory",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device/proxy:proxy_device_requirement",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device/util:device_id_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:no_op_device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator:validator_factory",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dimension",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job/in",
        "@maven//:javax_inject_jsr330_api",
    ],
)
