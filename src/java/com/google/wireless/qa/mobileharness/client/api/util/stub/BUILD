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

package(default_applicable_licenses = ["//:license"])

java_library(
    name = "stub",
    srcs = ["StubManager.java"],
    visibility = ["//:deviceinfra_all_pkg"],
    deps = [
        "//java/com/google/devtools/mobileharness/infra/cloudatlas/grpcrouter/stub:grpc_router_stub_configuration_factory",
        "//src/devtools/mobileharness/infra/client/api/proto:resource_federation_java_proto",
        "//src/devtools/mobileharness/infra/client/api/proto:server_locator_java_proto",
        "//src/devtools/mobileharness/infra/container/proto:test_engine_java_proto",
        "//src/devtools/mobileharness/shared/util/comm/stub:stub_configuration_java_proto",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode/remote:lab_server_locator",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/util/resourcefederation:util",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/util/serverlocator:util",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/util/stub:grpc_stub_manager",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/util/stub:stub_factory",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/util/stub:stub_factory_module",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/util/stub:stub_utils",
        "//src/java/com/google/devtools/mobileharness/infra/lab/rpc/stub:exec_test",
        "//src/java/com/google/devtools/mobileharness/infra/lab/rpc/stub:prepare_test",
        "//src/java/com/google/devtools/mobileharness/shared/constant/environment",
        "//src/java/com/google/devtools/mobileharness/shared/util/base:proto_text_format",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/filetransfer/common/factory:file_transfer_client_factories",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/filetransfer/common/factory:file_transfer_client_factory",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/version/rpc/stub",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/lab",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
    ],
)
