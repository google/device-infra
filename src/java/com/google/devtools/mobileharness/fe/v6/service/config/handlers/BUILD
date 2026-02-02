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
    name = "get_device_config_handler",
    srcs = ["GetDeviceConfigHandler.java"],
    deps = [
        "//src/devtools/mobileharness/fe/v6/service/proto/config:config_service_java_proto",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "check_device_write_permission_handler",
    srcs = ["CheckDeviceWritePermissionHandler.java"],
    deps = [
        "//src/devtools/mobileharness/fe/v6/service/proto/config:config_service_java_proto",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "update_device_config_handler",
    srcs = ["UpdateDeviceConfigHandler.java"],
    deps = [
        "//src/devtools/mobileharness/fe/v6/service/proto/config:config_service_java_proto",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "get_recommended_wifi_handler",
    srcs = ["GetRecommendedWifiHandler.java"],
    deps = [
        "//src/devtools/mobileharness/fe/v6/service/proto/config:config_service_java_proto",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "get_host_default_device_config_handler",
    srcs = ["GetHostDefaultDeviceConfigHandler.java"],
    deps = [
        "//src/devtools/mobileharness/fe/v6/service/proto/config:config_service_java_proto",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "get_host_config_handler",
    srcs = ["GetHostConfigHandler.java"],
    deps = [
        "//src/devtools/mobileharness/fe/v6/service/proto/config:config_service_java_proto",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "check_host_write_permission_handler",
    srcs = ["CheckHostWritePermissionHandler.java"],
    deps = [
        "//src/devtools/mobileharness/fe/v6/service/proto/config:config_service_java_proto",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "update_host_config_handler",
    srcs = ["UpdateHostConfigHandler.java"],
    deps = [
        "//src/devtools/mobileharness/fe/v6/service/proto/config:config_service_java_proto",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)
