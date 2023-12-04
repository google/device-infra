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

load("@com_github_grpc_grpc//bazel:python_rules.bzl", "py_grpc_library")
load("@com_google_protobuf//:protobuf.bzl", "py_proto_library")
load("@io_grpc_grpc_java//:java_grpc_library.bzl", "java_grpc_library")

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/console:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/client/longrunningservice:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/shared/util/message:__subpackages__",
    ],
)

proto_library(
    name = "control_service_proto",
    srcs = ["control_service.proto"],
    deps = [
        ":log_proto",
    ],
)

java_proto_library(
    name = "control_service_java_proto",
    deps = [":control_service_proto"],
)

java_grpc_library(
    name = "control_service_java_grpc",
    srcs = [":control_service_proto"],
    deps = [":control_service_java_proto"],
)

proto_library(
    name = "log_proto",
    srcs = ["log.proto"],
)

java_proto_library(
    name = "log_java_proto",
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice:__subpackages__",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/client/longrunningservice:__subpackages__",
    ],
    deps = [":log_proto"],
)

proto_library(
    name = "session_proto",
    srcs = ["session.proto"],
    deps = [
        "//src/devtools/common/metrics/stability/model/proto:error_proto",
        "@com_google_protobuf//:any_proto",
    ],
)

java_proto_library(
    name = "session_java_proto",
    deps = [":session_proto"],
)

py_proto_library(
    name = "session_py_proto",
    srcs = [
        "session.proto",
    ],
    deps = [
        "//src/devtools/common/metrics/stability/model/proto:error_py_proto",
        "@com_github_protocolbuffers_protobuf//:well_known_types_py_pb2",
    ],
)

proto_library(
    name = "session_service_proto",
    srcs = ["session_service.proto"],
    deps = [
        ":session_proto",
        "@com_google_protobuf//:field_mask_proto",
    ],
)

java_proto_library(
    name = "session_service_java_proto",
    deps = [":session_service_proto"],
)

java_grpc_library(
    name = "session_service_java_grpc",
    srcs = [":session_service_proto"],
    deps = [":session_service_java_proto"],
)

py_proto_library(
    name = "session_service_py_proto",
    srcs = [
        "session_service.proto",
    ],
    deps = [
        ":session_py_proto",
        "@com_github_protocolbuffers_protobuf//:well_known_types_py_pb2",
    ],
)

py_grpc_library(
    name = "session_service_py_grpc",
    srcs = [":session_service_proto"],
    deps = [":session_service_py_proto"],
)

proto_library(
    name = "version_service_proto",
    srcs = ["version_service.proto"],
)

java_proto_library(
    name = "version_service_java_proto",
    deps = [":version_service_proto"],
)

java_grpc_library(
    name = "version_service_java_grpc",
    srcs = [":version_service_proto"],
    deps = [":version_service_java_proto"],
)
