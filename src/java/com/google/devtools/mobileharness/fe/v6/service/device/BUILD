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
    name = "device_service_grpc_impl",
    srcs = ["DeviceServiceGrpcImpl.java"],
    deps = [
        ":device_service_logic",
        "//src/devtools/mobileharness/fe/v6/service/proto/device:device_resources_java_proto",
        "//src/devtools/mobileharness/fe/v6/service/proto/device:device_service_java_grpc",
        "//src/devtools/mobileharness/fe/v6/service/proto/device:device_service_java_proto",
        "//src/devtools/mobileharness/fe/v6/service/proto/device:device_stats_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/rpc/grpc:service_util",
        "@grpc-java//stub",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "device_service_logic",
    srcs = ["DeviceServiceLogic.java"],
    deps = [
        "//src/devtools/mobileharness/fe/v6/service/proto/device:device_resources_java_proto",
        "//src/devtools/mobileharness/fe/v6/service/proto/device:device_service_java_proto",
        "//src/devtools/mobileharness/fe/v6/service/proto/device:device_stats_java_proto",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "configuration_provider",
    srcs = ["ConfigurationProvider.java"],
    deps = [
        "//src/devtools/mobileharness/api/deviceconfig/proto:device_java_proto",
        "//src/devtools/mobileharness/api/deviceconfig/proto:lab_java_proto",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "oss_configuration_provider_impl",
    srcs = ["OssConfigurationProviderImpl.java"],
    deps = [
        ":configuration_provider",
        "//src/devtools/mobileharness/api/deviceconfig/proto:device_java_proto",
        "//src/devtools/mobileharness/api/deviceconfig/proto:lab_java_proto",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "device_service_logic_impl",
    srcs = ["DeviceServiceLogicImpl.java"],
    deps = [
        ":device_service_logic",
        "//src/devtools/mobileharness/fe/v6/service/proto/device:device_resources_java_proto",
        "//src/devtools/mobileharness/fe/v6/service/proto/device:device_service_java_proto",
        "//src/devtools/mobileharness/fe/v6/service/proto/device:device_stats_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/device/handlers:get_device_overview_handler",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "device_service_module",
    srcs = ["DeviceServiceModule.java"],
    deps = [
        ":device_service_logic",
        ":device_service_logic_impl",
        "@maven//:com_google_inject_guice",
    ],
)
