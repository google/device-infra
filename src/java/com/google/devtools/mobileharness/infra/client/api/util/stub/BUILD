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
        "//src/java/com/google/devtools/mobileharness/infra/client/api/util/serverlocator:__pkg__",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/relay:__subpackages__",
        "//src/java/com/google/wireless/qa/mobileharness/client/api/util/stub:__pkg__",
    ],
)

java_library(
    name = "grpc_stub_manager",
    srcs = ["GrpcStubManager.java"],
    visibility = [
        "//src/java/com/google/wireless/qa/mobileharness/client/api/util/stub:__pkg__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/infra/lab/rpc/stub:exec_test",
        "//src/java/com/google/devtools/mobileharness/infra/lab/rpc/stub:prepare_test",
        "//src/java/com/google/devtools/mobileharness/infra/lab/rpc/stub/grpc:exec_test_grpc_stub",
        "//src/java/com/google/devtools/mobileharness/infra/lab/rpc/stub/grpc:prepare_test_grpc_stub",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/stub:channel_factory",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/stub:channel_manager",
        "//src/java/com/google/devtools/mobileharness/shared/version/rpc/stub",
        "//src/java/com/google/devtools/mobileharness/shared/version/rpc/stub/grpc",
        "@grpc-java//core",
    ],
)

java_library(
    name = "stub_factory",
    srcs = [
        "BlockingInterfaceFactory.java",
        "GrpcDirectTargetFactory.java",
        "GrpcFutureInterfaceFactory.java",
        "GrpcStubFactory.java",
        "StubFactory.java",
    ],
    deps = [
        "//java/com/google/wireless/qa/mobileharness/master/rpc/stub:lab_info_future",
        "//java/com/google/wireless/qa/mobileharness/master/rpc/stub/grpc:lab_info_future",
        "//src/devtools/mobileharness/shared/util/comm/stub:stub_configuration_java_proto",
        "//src/java/com/google/devtools/mobileharness/infra/lab/rpc/stub:exec_test",
        "//src/java/com/google/devtools/mobileharness/infra/lab/rpc/stub:prepare_test",
        "//src/java/com/google/devtools/mobileharness/infra/lab/rpc/stub/grpc:exec_test_grpc_stub",
        "//src/java/com/google/devtools/mobileharness/infra/lab/rpc/stub/grpc:prepare_test_grpc_stub",
        "//src/java/com/google/devtools/mobileharness/infra/master/rpc/stub:job_sync",
        "//src/java/com/google/devtools/mobileharness/infra/master/rpc/stub/grpc:job_sync",
        "//src/java/com/google/devtools/mobileharness/shared/constant/closeable",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/filetransfer/cloud/rpc/stub:cloud_file_transfer_grpc_stub",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/filetransfer/cloud/rpc/stub:cloud_file_transfer_stub",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/stub:stub_configures",
        "//src/java/com/google/devtools/mobileharness/shared/version/rpc/stub",
        "//src/java/com/google/devtools/mobileharness/shared/version/rpc/stub/grpc",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "stub_factory_module",
    srcs = [
        "GrpcStubFactoryModule.java",
        "StubFactoryModule.java",
    ],
    deps = [
        ":stub_factory",
        "//src/devtools/mobileharness/shared/util/comm/stub:stub_configuration_java_proto",
        "//src/java/com/google/devtools/mobileharness/shared/constant/environment",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/stub:stub_configures",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "@maven//:com_google_inject_guice",
    ],
)

java_library(
    name = "stub_utils",
    srcs = ["StubUtils.java"],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:lab_java_proto",
        "//src/devtools/mobileharness/infra/container/proto:test_engine_java_proto",
        "//src/devtools/mobileharness/shared/util/comm/relay/proto:destination_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:locator",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode/remote:lab_server_locator",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
    ],
)
