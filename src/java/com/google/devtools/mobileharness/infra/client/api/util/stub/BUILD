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
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:thread_pools",
        "//src/java/com/google/devtools/mobileharness/shared/version/rpc/stub",
        "//src/java/com/google/devtools/mobileharness/shared/version/rpc/stub/grpc",
        "@io_grpc_grpc_java//core",
    ],
)
