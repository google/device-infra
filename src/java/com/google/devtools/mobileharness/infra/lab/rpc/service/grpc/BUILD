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
        "//java/com/google/devtools/deviceinfra/host/utrs:__subpackages__",
        "//java/com/google/devtools/mobileharness/infra/bridge:__subpackages__",
        "//java/com/google/devtools/mobileharness/infra/container/testengine:__subpackages__",
        "//src/java/com/google/devtools/deviceinfra/host/utrs:__subpackages__",
    ],
)

java_library(
    name = "prepare_test",
    srcs = ["PrepareTestGrpcImpl.java"],
    deps = [
        "//java/com/google/devtools/mobileharness/infra/lab/monitoring:metrics",
        "//java/com/google/devtools/mobileharness/shared/util/comm/service:grpc",
        "//src/devtools/mobileharness/infra/lab/proto:prepare_test_service_grpc",
        "//src/devtools/mobileharness/infra/lab/proto:prepare_test_service_java_proto",
        "//src/devtools/mobileharness/infra/lab/proto:prepare_test_service_java_stubby",
        "//src/java/com/google/devtools/mobileharness/infra/lab/rpc/service:prepare_test",
        "@io_grpc_grpc_java//stub",
    ],
)

java_library(
    name = "exec_test",
    srcs = ["ExecTestGrpcImpl.java"],
    deps = [
        "//java/com/google/devtools/mobileharness/infra/lab/monitoring:metrics",
        "//java/com/google/devtools/mobileharness/shared/util/comm/service:grpc",
        "//java/com/google/wireless/qa/mobileharness/lab/proto:exec_test_serv_grpc",
        "//java/com/google/wireless/qa/mobileharness/lab/proto:exec_test_serv_java_proto",
        "//src/java/com/google/devtools/mobileharness/infra/lab/rpc/service:exec_test",
        "@io_grpc_grpc_java//stub",
    ],
)

java_library(
    name = "device_ops",
    srcs = ["DeviceOpsGrpcImpl.java"],
    deps = [
        "//java/com/google/devtools/mobileharness/infra/lab/monitoring:metrics",
        "//java/com/google/devtools/mobileharness/shared/util/comm/service:grpc",
        "//java/com/google/wireless/qa/mobileharness/lab/proto:device_ops_serv_grpc",
        "//java/com/google/wireless/qa/mobileharness/lab/proto:device_ops_serv_java_proto",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device",
        "//src/java/com/google/devtools/mobileharness/infra/lab/rpc/service:device_ops",
        "@io_grpc_grpc_java//stub",
    ],
)

java_library(
    name = "stat",
    srcs = ["StatGrpcImpl.java"],
    deps = [
        "//java/com/google/devtools/mobileharness/infra/lab/monitoring:metrics",
        "//java/com/google/devtools/mobileharness/shared/util/comm/service:grpc",
        "//java/com/google/wireless/qa/mobileharness/lab/proto:stat_serv_grpc",
        "//java/com/google/wireless/qa/mobileharness/lab/proto:stat_serv_java_proto",
        "//src/java/com/google/devtools/mobileharness/infra/lab/rpc/service:stat",
        "@io_grpc_grpc_java//stub",
    ],
)
