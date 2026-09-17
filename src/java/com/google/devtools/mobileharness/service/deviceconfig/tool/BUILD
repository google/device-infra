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

load("@rules_java//java:java_binary.bzl", "java_binary")
load("@rules_java//java:java_library.bzl", "java_library")

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//:deviceinfra_all_pkg",
        "//javatests/com/google/devtools/mobileharness/service/deviceconfig/tool:__pkg__",
    ],
)

java_binary(
    name = "device_config_importer",
    main_class = "com.google.devtools.mobileharness.service.deviceconfig.tool.DeviceConfigImporter",
    runtime_deps = [":device_config_importer_lib"],
)

java_library(
    name = "device_config_importer_lib",
    srcs = ["DeviceConfigImporter.java"],
    deps = [
        "//src/devtools/mobileharness/api/deviceconfig/proto:device_config_service_java_proto",
        "//src/devtools/mobileharness/api/deviceconfig/proto:device_java_proto",
        "//src/devtools/mobileharness/api/deviceconfig/proto:lab_device_java_proto",
        "//src/java/com/google/devtools/mobileharness/service/deviceconfig/rpc/stub:annotation",
        "//src/java/com/google/devtools/mobileharness/service/deviceconfig/rpc/stub:device_config_stub",
        "//src/java/com/google/devtools/mobileharness/service/deviceconfig/rpc/stub/grpc:device_config_grpc_stub_module",
        "//src/java/com/google/devtools/mobileharness/shared/util/base:proto_text_format",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags/core:flags_manager",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
    ],
)
