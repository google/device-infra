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

load("@rules_java//java:defs.bzl", "java_binary", "java_library")

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//java/com/google/devtools/mobileharness/service/gateway:__subpackages__",
        "//javatests/com/google/devtools/mobileharness/service/gateway:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/ats:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/local:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite:__subpackages__",
        "//src/javatests/com/google/devtools:__subpackages__",
    ],
)

java_library(
    name = "configuration_util",
    srcs = ["ConfigurationUtil.java"],
    deps = [
        ":config_common",
        ":configuration_xml_parser",
        "//src/devtools/mobileharness/platform/android/xts/proto:configuration_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "configuration_xml_parser",
    srcs = ["ConfigurationXmlParser.java"],
    deps = [
        ":config_common",
        "//src/devtools/mobileharness/platform/android/xts/proto:configuration_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "dynamic_config",
    srcs = ["DynamicConfig.java"],
    visibility = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/platform/android/xts/config:__subpackages__",
    ],
    runtime_deps = [
        "@maven//:net_sf_kxml_kxml2",
    ],
    deps = [
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
        "@maven//:xmlpull_xmlpull",
    ],
)

java_library(
    name = "dynamic_config_handler",
    srcs = ["DynamicConfigHandler.java"],
    visibility = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/platform/android/xts/config:__subpackages__",
    ],
    runtime_deps = [
        "@maven//:net_sf_kxml_kxml2",
    ],
    deps = [
        ":dynamic_config",
        "@maven//:com_google_code_gson_gson",
        "@maven//:com_google_guava_guava",
        "@maven//:xmlpull_xmlpull",
    ],
)

java_library(
    name = "module_configuration_helper",
    srcs = ["ModuleConfigurationHelper.java"],
    deps = [
        ":configuration_util",
        "//src/devtools/mobileharness/platform/android/xts/proto:configuration_java_proto",
        "//src/devtools/mobileharness/platform/android/xts/proto:device_configuration_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/error:exception_factory",
        "//src/java/com/google/devtools/mobileharness/api/model/job/in",
        "//src/java/com/google/devtools/mobileharness/shared/util/base:proto_text_format",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dimension",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job/in/spec",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_code_gson_gson",
        "@maven//:com_google_guava_guava",
        "@protobuf//:protobuf_java",
    ],
)

java_library(
    name = "config_common",
    srcs = ["ConfigCommon.java"],
    deps = ["@maven//:com_google_guava_guava"],
)

java_library(
    name = "module_configuration_lab_plugin_lib",
    srcs = ["ModuleConfigurationLabPlugin.java"],
    deps = [
        ":configuration_util",
        ":module_configuration_helper",
        "//src/devtools/mobileharness/platform/android/xts/proto:configuration_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/testrunner/plugin:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/annotation",
        "//src/java/com/google/wireless/qa/mobileharness/shared/controller/event:test",
        "//src/java/com/google/wireless/qa/mobileharness/shared/controller/plugin",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "@maven//:com_google_guava_guava",
    ],
)

java_binary(
    name = "module_configuration_lab_plugin",
    srcs = ["ModuleConfigurationLabPlugin.java"],
    deps = [
        ":configuration_util",
        ":module_configuration_helper",
        "//src/devtools/mobileharness/platform/android/xts/proto:configuration_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/testrunner/plugin:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/annotation",
        "//src/java/com/google/wireless/qa/mobileharness/shared/controller/event:test",
        "//src/java/com/google/wireless/qa/mobileharness/shared/controller/plugin",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "@maven//:com_google_guava_guava",
    ],
)
