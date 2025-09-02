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
    name = "proxy_device_manager",
    srcs = ["ProxyDeviceManager.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode/local:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/controller/device/proxy:__pkg__",
    ],
    deps = [
        ":proxy_device_requirement",
        ":proxy_device_runner",
        "//src/java/com/google/devtools/mobileharness/api/model/job:locator",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:callables",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:more_futures",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "proxy_device_manager_module",
    srcs = ["ProxyDeviceManagerModule.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode/local:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/controller/device/proxy:__pkg__",
    ],
    deps = [
        ":proxy_device_runner",
        "@maven//:com_google_inject_extensions_guice_assistedinject",
        "@maven//:com_google_inject_guice",
    ],
)

java_library(
    name = "proxy_device_requirement",
    srcs = ["ProxyDeviceRequirement.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/proxy:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode/local:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/api/devicemanager/proxy:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/controller/device/proxy:__pkg__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job/in",
    ],
)

java_library(
    name = "proxy_device_runner",
    srcs = ["ProxyDeviceRunner.java"],
    visibility = ["//visibility:private"],
    deps = [
        ":proxy_device_requirement",
        "//src/java/com/google/devtools/mobileharness/api/devicemanager/proxy:device_proxy",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/job:locator",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/reflection:reflection_util",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job/in",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_inject_extensions_guice_assistedinject",
        "@maven//:com_google_inject_guice",
        "@maven//:javax_inject_jsr330_api",
    ],
)
