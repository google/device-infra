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
    name = "base",
    srcs = [
        "BaseSubDeviceInfo.java",
        "BaseTestbedConfig.java",
        "SubDeviceInfo.java",
        "SubDeviceKey.java",
        "TestbedConfig.java",
        "TestbedConfigYamlRepresenter.java",
        "TestbedLoader.java",
    ],
    visibility = [
        "//:omnilab_fusion",
        "//src/java/com/google/devtools/mobileharness/platform/testbed:__subpackages__",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:__pkg__",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:__pkg__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api",
        "@maven//:com_google_guava_guava",
        "@maven//:org_json_json",
        "@maven//:org_yaml_snakeyaml",
    ],
)

java_library(
    name = "json_config_lib",
    srcs = glob(["json/*.java"]),
    visibility = [
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/platform/testbed/config:base",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api:class_util",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "@maven//:com_google_code_gson_gson",
        "@maven//:com_google_guava_guava",
        "@maven//:org_json_json",
        "@maven//:org_yaml_snakeyaml",
    ],
)

java_library(
    name = "global_testbed_loader",
    srcs = [
        "GlobalTestbedLoader.java",
    ],
    visibility = [
        "//:omnilab_fusion",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:__pkg__",
    ],
    deps = [
        ":base",
        ":json_config_lib",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:misc_testbed_sub_device",
        "@maven//:com_google_guava_guava",
    ],
)
