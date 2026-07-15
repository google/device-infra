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
        "//google/corp/devtools/mobileharness/fe:__subpackages__",
        "//src/devtools/mobileharness/fe/v6:visibility",
    ],
)

java_library(
    name = "admin_service_logic",
    srcs = ["AdminServiceLogic.java"],
    deps = [
        "//src/devtools/mobileharness/fe/v6/service/proto/admin:admin_service_java_proto",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "admin_service_logic_impl",
    srcs = ["AdminServiceLogicImpl.java"],
    deps = [
        ":admin_service_logic",
        "//src/devtools/mobileharness/fe/v6/service/proto/admin:admin_service_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/admin/handlers:list_wifi_credentials_handler",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/admin/handlers:set_wifi_credentials_handler",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "admin_service_grpc_impl",
    srcs = ["AdminServiceGrpcImpl.java"],
    deps = [
        ":admin_service_logic",
        "//src/devtools/mobileharness/fe/v6/service/proto/admin:admin_service_java_grpc",
        "//src/devtools/mobileharness/fe/v6/service/proto/admin:admin_service_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/grpc:fe_grpc_invoker",
        "@grpc-java//stub",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "admin_service_module",
    srcs = ["AdminServiceModule.java"],
    deps = [
        ":admin_service_logic",
        ":admin_service_logic_impl",
        "@maven//:com_google_inject_guice",
    ],
)
