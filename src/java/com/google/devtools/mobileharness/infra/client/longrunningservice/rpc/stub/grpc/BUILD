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
        "//src/java/com/google/devtools/mobileharness/infra/ats/common/olcserver:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/ats/dda/stub:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/ats/wrangleragent:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/client/longrunningservice:__subpackages__",
    ],
)

java_library(
    name = "control_grpc_stub",
    srcs = ["ControlGrpcStub.java"],
    deps = [
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:control_service_java_grpc",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:control_service_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/rpc/grpc",
        "//src/java/com/google/devtools/common/metrics/stability/rpc/grpc:stub_util",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/rpc/stub:control_stub",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/stub:stubs",
        "@grpc-java//core",
        "@grpc-java//stub",
    ],
)

java_library(
    name = "session_grpc_stub",
    srcs = ["SessionGrpcStub.java"],
    deps = [
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:session_service_java_grpc",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:session_service_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/rpc/grpc",
        "//src/java/com/google/devtools/common/metrics/stability/rpc/grpc:stub_util",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/rpc/stub:session_stub",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/stub:stubs",
        "@grpc-java//core",
        "@grpc-java//stub",
    ],
)

java_library(
    name = "version_grpc_stub",
    srcs = ["VersionGrpcStub.java"],
    deps = [
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:version_service_java_grpc",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:version_service_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/rpc/grpc",
        "//src/java/com/google/devtools/common/metrics/stability/rpc/grpc:stub_util",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/rpc/stub:version_stub",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/stub:stubs",
        "@grpc-java//core",
    ],
)
