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
        "//src/java/com/google/devtools/mobileharness/infra/lab:__subpackages__",
    ],
)

java_library(
    name = "prepare_test",
    srcs = ["PrepareTestGrpcImpl.java"],
    deps = [
        "//src/devtools/mobileharness/infra/lab/proto:prepare_test_service_grpc",
        "//src/devtools/mobileharness/infra/lab/proto:prepare_test_service_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/rpc/grpc:service_util",
        "//src/java/com/google/devtools/mobileharness/infra/lab/rpc/service:prepare_test",
        "@io_grpc_grpc_java//stub",
    ],
)

java_library(
    name = "exec_test",
    srcs = ["ExecTestGrpcImpl.java"],
    deps = [
        "//src/java/com/google/devtools/common/metrics/stability/rpc/grpc:service_util",
        "//src/java/com/google/devtools/mobileharness/infra/lab/rpc/service:exec_test",
        "//src/java/com/google/wireless/qa/mobileharness/lab/proto:exec_test_serv_grpc",
        "//src/java/com/google/wireless/qa/mobileharness/lab/proto:exec_test_serv_java_proto",
        "@io_grpc_grpc_java//stub",
    ],
)

java_library(
    name = "stat",
    srcs = ["StatGrpcImpl.java"],
    deps = [
        "//src/java/com/google/devtools/common/metrics/stability/rpc/grpc:service_util",
        "//src/java/com/google/devtools/mobileharness/infra/lab/rpc/service:stat",
        "//src/java/com/google/wireless/qa/mobileharness/lab/proto:stat_serv_grpc",
        "//src/java/com/google/wireless/qa/mobileharness/lab/proto:stat_serv_java_proto",
        "@io_grpc_grpc_java//stub",
    ],
)
