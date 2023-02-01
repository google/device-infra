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
    default_visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/client/server:__subpackages__",
    ],
)

java_library(
    name = "session_service",
    srcs = ["SessionService.java"],
    deps = [
        "//java/com/google/devtools/common/metrics/stability/rpc/grpc:service_util",
        "//src/devtools/mobileharness/infra/client/server/proto:session_java_proto",
        "//src/devtools/mobileharness/infra/client/server/proto:session_service_java_grpc",
        "//src/devtools/mobileharness/infra/client/server/proto:session_service_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/client/server/controller:session_manager",
        "//third_party/java/grpc:stub",
        "//third_party/java/protobuf/util:field_mask",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "version_service",
    srcs = ["VersionService.java"],
    deps = [
        "//java/com/google/devtools/common/metrics/stability/rpc/grpc:service_util",
        "//src/devtools/mobileharness/infra/client/server/proto:version_service_java_grpc",
        "//src/devtools/mobileharness/infra/client/server/proto:version_service_java_proto",
        "//src/java/com/google/devtools/mobileharness/shared/version",
        "//third_party/java/grpc:stub",
    ],
)
