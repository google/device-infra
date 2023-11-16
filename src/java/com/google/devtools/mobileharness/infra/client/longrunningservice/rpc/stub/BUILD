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
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/console/controller:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/client/longrunningservice:__subpackages__",
    ],
)

java_library(
    name = "control_stub",
    srcs = ["ControlStub.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/command:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/console/controller:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/client/longrunningservice:__subpackages__",
    ],
    deps = [
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:control_service_java_grpc",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:control_service_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/rpc/grpc",
        "//src/java/com/google/devtools/common/metrics/stability/rpc/grpc:stub_util",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "@io_grpc_grpc_java//core",
        "@io_grpc_grpc_java//stub",
    ],
)

java_library(
    name = "session_stub",
    srcs = ["SessionStub.java"],
    deps = [
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:session_service_java_grpc",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:session_service_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/rpc/grpc",
        "//src/java/com/google/devtools/common/metrics/stability/rpc/grpc:stub_util",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "@io_grpc_grpc_java//core",
    ],
)

java_library(
    name = "version_stub",
    srcs = ["VersionStub.java"],
    deps = [
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:version_service_java_grpc",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:version_service_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/rpc/grpc",
        "//src/java/com/google/devtools/common/metrics/stability/rpc/grpc:stub_util",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "@io_grpc_grpc_java//core",
        "@maven//:com_google_errorprone_error_prone_annotations",
    ],
)
