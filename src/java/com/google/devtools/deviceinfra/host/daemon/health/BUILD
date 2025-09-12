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

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//:deviceinfra_api_users",
        "//:omnilab_fusion",
    ],
)

java_library(
    name = "health_status_manager",
    srcs = ["HealthStatusManager.java"],
    deps = [
        "//src/devtools/deviceinfra/host/daemon/proto:health_java_proto",
        "//src/java/com/google/devtools/mobileharness/infra/daemon/health/handler:drain_handler",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:sleeper",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
    ],
)

java_library(
    name = "health_status_manager_module",
    srcs = ["HealthStatusManagerModule.java"],
    deps = [
        ":health_status_manager",
        "//src/devtools/deviceinfra/host/daemon/proto:health_java_grpc",
        "//src/java/com/google/devtools/deviceinfra/host/daemon/health/service:health_service_impl",
        "//src/java/com/google/devtools/mobileharness/infra/daemon/health/handler:drain_handler",
        "//src/java/com/google/devtools/mobileharness/infra/lab:annotations",
        "@grpc-java//core",
        "@maven//:com_google_inject_guice",
    ],
)

java_library(
    name = "drain_service",
    srcs = [
        "DrainService.java",
        "DrainServiceModule.java",
    ],
    visibility = ["//:omnilab_fusion"],
    deps = [
        ":health_status_manager",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:service_module",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@maven//:javax_inject_jsr330_api",
    ],
)
