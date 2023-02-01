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

java_library(
    name = "channel_factory",
    srcs = ["ChannelFactory.java"],
    deps = [
        "//third_party/java/grpc:core",
        "//third_party/java/grpc:netty",
    ],
)

java_library(
    name = "session_stub",
    srcs = ["SessionStub.java"],
    deps = [
        "//java/com/google/devtools/common/metrics/stability/rpc/grpc",
        "//java/com/google/devtools/common/metrics/stability/rpc/grpc:stub_util",
        "//src/devtools/mobileharness/infra/client/server/proto:session_service_java_grpc",
        "//src/devtools/mobileharness/infra/client/server/proto:session_service_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//third_party/java/grpc:core",
    ],
)

java_library(
    name = "version_stub",
    srcs = ["VersionStub.java"],
    deps = [
        "//java/com/google/devtools/common/metrics/stability/rpc/grpc",
        "//java/com/google/devtools/common/metrics/stability/rpc/grpc:stub_util",
        "//src/devtools/mobileharness/infra/client/server/proto:version_service_java_grpc",
        "//src/devtools/mobileharness/infra/client/server/proto:version_service_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//third_party/java/grpc:core",
    ],
)
