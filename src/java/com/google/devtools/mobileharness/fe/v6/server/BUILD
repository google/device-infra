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
    default_visibility = ["//src/devtools/mobileharness/fe/v6:visibility"],
)

java_library(
    name = "annotations",
    srcs = ["Annotations.java"],
    deps = [
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "oss_fe_server_lib",
    srcs = ["OssFeServer.java"],
    runtime_deps = ["@grpc-java//netty"],
    deps = [
        ":annotations",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/config:config_service_grpc_impl",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/config:config_service_module",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/device:device_service_grpc_impl",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/device:device_service_module",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/host:host_service_grpc_impl",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/host:host_service_module",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/shared",
        "//src/java/com/google/devtools/mobileharness/fe/v6/shared/util/concurrent:oss_executor_module",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@grpc-java//core",
        "@grpc-java//services:reflection",
        "@maven//:com_google_inject_guice",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_binary(
    name = "oss_fe_server",
    main_class = "com.google.devtools.mobileharness.fe.v6.server.OssFeServer",
    visibility = [
        "//src/devtools/mobileharness/fe/v6:visibility",
        "//third_party/py/multitest_transport:__subpackages__",
    ],
    runtime_deps = [":oss_fe_server_lib"],
)
