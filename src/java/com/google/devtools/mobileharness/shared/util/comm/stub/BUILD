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
        "//:omnilab_fusion",
    ],
)

java_library(
    name = "channel_factory",
    srcs = [
        "ChannelFactory.java",
        "ManagedChannelSupplier.java",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:thread_pools",
        "@grpc-java//core",
        "@grpc-java//netty",
    ],
)

java_library(
    name = "channel_manager",
    srcs = ["ChannelManager.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:more_futures",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:thread_pools",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@grpc-java//core",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "client_interceptor_factory",
    srcs = ["ClientInterceptorFactory.java"],
    deps = [
        "@grpc-java//auth",
        "@grpc-java//core",
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
        "//src/java/com/google/devtools/mobileharness/shared/constant/closeable",
        "@grpc-java//core",
        "@grpc-java//stub",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "stubs",
    srcs = ["Stubs.java"],
    deps = [
        "@grpc-java//stub",
    ],
)

java_library(
    name = "proxy",
    srcs = [
        "Annotations.java",
        "BlockingInterfaceInvocationHandler.java",
        "DirectInvocationHandler.java",
        "FutureExecutorModule.java",
        "GrpcFutureInterfaceInvocationHandler.java",
        "GrpcStatusUtils.java",
        "MessageTransformer.java",
        "RpcCall.java",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/shared/util/base:proto_reflection_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:thread_pools",
        "@grpc-java//core",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@protobuf//:protobuf_java",
    ],
)

java_library(
    name = "stub_configures",
    srcs = [
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
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/relay/client",
        "@grpc-java//core",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@maven//:javax_inject_jsr330_api",
    ],
)
