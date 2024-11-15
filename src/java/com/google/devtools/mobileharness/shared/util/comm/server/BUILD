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
        "//:deviceinfra_all_pkg",
    ],
)

java_library(
    name = "reflection_operation_exception_wrapper",
    srcs = [
        "ReflectionOperationExceptionWrapper.java",
    ],
    deps = [],
)

java_library(
    name = "alts_auth_interceptor",
    srcs = [
        "AltsAuthInterceptor.java",
    ],
    deps = [
        ":reflection_operation_exception_wrapper",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@io_grpc_grpc_java//core",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "sever_builder_factory",
    srcs = [
        "ServerBuilderFactory.java",
    ],
    deps = [
        ":alts_auth_interceptor",
        ":reflection_operation_exception_wrapper",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@io_grpc_grpc_java//core",
        "@io_grpc_grpc_java//netty",
    ],
)

java_library(
    name = "client_address_server_interceptor",
    srcs = ["ClientAddressServerInterceptor.java"],
    deps = [
        ":grpc_contexts",
        "@io_grpc_grpc_java//context",
        "@io_grpc_grpc_java//core",
    ],
)

java_library(
    name = "grpc_contexts",
    srcs = ["GrpcContexts.java"],
    deps = [
        "@io_grpc_grpc_java//context",
    ],
)

java_library(
    name = "lifecycle",
    srcs = [
        "LifecycleManager.java",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:more_futures",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@io_grpc_grpc_java//core",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)
