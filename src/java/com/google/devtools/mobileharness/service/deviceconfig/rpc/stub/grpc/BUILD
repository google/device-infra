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
    default_visibility = [
        "//:deviceinfra_all_pkg",
        "//:omnilab_fusion",
        "//java/com/google/devtools/omnilab/device/lifecycle/android:__subpackages__",
    ],
)

java_library(
    name = "device_config_grpc_stub",
    srcs = ["DeviceConfigGrpcStub.java"],
    deps = [
        "//src/devtools/mobileharness/api/deviceconfig/proto:device_config_service_grpc",
        "//src/devtools/mobileharness/api/deviceconfig/proto:device_config_service_java_proto",
        "//src/devtools/mobileharness/api/deviceconfig/proto:device_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/rpc/grpc",
        "//src/java/com/google/devtools/common/metrics/stability/rpc/grpc:stub_util",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/service/deviceconfig/rpc/stub:device_config_stub",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
    ],
)
