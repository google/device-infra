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

java_binary(
    name = "device_config_server_starter",
    srcs = ["DeviceConfigServerStarter.java"],
    launcher = "//devtools/java/launcher:envelope_loader",
    main_class = "com.google.devtools.mobileharness.service.deviceconfig.DeviceConfigServerStarter",
    deps = [
        "//devtools/mobileharness/api/deviceconfig/proto:device_config_service_grpc",
        "//java/com/google/net/grpc:prod_server_builder",
        "//src/java/com/google/devtools/mobileharness/service/deviceconfig/rpc/service:device_config_service_impl",
        "//src/java/com/google/devtools/mobileharness/service/deviceconfig/rpc/service/grpc:device_config_grpc_impl",
        "@maven//:com_google_guava_guava",
    ],
)
