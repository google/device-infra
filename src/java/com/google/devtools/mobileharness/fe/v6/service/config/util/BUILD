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

load("@rules_java//java:java_library.bzl", "java_library")

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = ["//src/devtools/mobileharness/fe/v6:visibility"],
)

java_library(
    name = "config_converter",
    srcs = ["ConfigConverter.java"],
    deps = [
        "//src/devtools/mobileharness/api/deviceconfig/proto:basic_java_proto",
        "//src/devtools/mobileharness/api/deviceconfig/proto:lab_java_proto",
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/devtools/mobileharness/fe/v6/service/proto/common:common_resources_java_proto",
        "//src/devtools/mobileharness/fe/v6/service/proto/config:config_resources_java_proto",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_protobuf_protobuf_java",
    ],
)

java_library(
    name = "config_util",
    srcs = ["ConfigUtil.java"],
    deps = [
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "config_service_capability",
    srcs = [
        "ConfigServiceCapability.java",
        "ConfigServiceCapabilityFactory.java",
    ],
    deps = [
        "//src/devtools/mobileharness/fe/v6/service/proto/config:config_resources_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/util",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "@maven//:com_google_inject_extensions_guice_assistedinject",
    ],
)
