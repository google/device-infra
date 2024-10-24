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

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//:deviceinfra_all_pkg",
    ],
)

java_library(
    name = "channel_factory",
    srcs = [
        "ChannelFactory.java",
        "ManagedChannelSupplier.java",
    ],
    deps = [
        "@io_grpc_grpc_java//core",
        "@io_grpc_grpc_java//netty",
    ],
)

java_library(
    name = "channel_manager",
    srcs = ["ChannelManager.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:more_futures",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:thread_pools",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@io_grpc_grpc_java//core",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "client_interceptor_factory",
    srcs = ["ClientInterceptorFactory.java"],
    deps = [
        "@io_grpc_grpc_java//auth",
        "@io_grpc_grpc_java//core",
        "@maven//:com_google_auth_google_auth_library_credentials",
        "@maven//:com_google_auth_google_auth_library_oauth2_http",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "master_grpc_stub_helper",
    srcs = [
        "MasterGrpcStubHelper.java",
    ],
    runtime_deps = [
    ],
    deps = [
        "@io_grpc_grpc_java//core",
        "@io_grpc_grpc_java//stub",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "stubs",
    srcs = ["Stubs.java"],
    deps = [
        "@io_grpc_grpc_java//stub",
    ],
)

java_library(
    name = "proxy",
    srcs = [
        "BlockingInterfaceInvocationHandler.java",
        "DirectInvocationHandler.java",
        "SyncCallMethod.java",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/shared/util/base:proto_reflection_util",
        "@com_google_protobuf//:protobuf_java",
        "@io_grpc_grpc_java//core",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "stub_configures",
    srcs = [
        "Annotations.java",
        "GrpcDirectTargetConfigures.java",
        "GrpcDirectTargetConfiguresModule.java",
    ],
    visibility = [
        "//:deviceinfra_all_pkg",
    ],
    deps = [
        ":channel_factory",
        ":channel_manager",
        ":proxy",
        "//src/devtools/mobileharness/shared/util/comm/stub:stub_configuration_java_proto",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:thread_pools",
        "@io_grpc_grpc_java//core",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@maven//:javax_inject_jsr330_api",
    ],
)
