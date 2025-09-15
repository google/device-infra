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

DEFAULT_VISIBILITY = [
    "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice:__subpackages__",
    "//src/javatests/com/google/devtools/mobileharness/infra/client/longrunningservice:__subpackages__",
]

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = DEFAULT_VISIBILITY,
)

java_library(
    name = "control_service",
    srcs = ["ControlService.java"],
    deps = [
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:control_service_java_grpc",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:control_service_java_proto",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:log_java_proto",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:session_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/rpc/grpc:service_util",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/controller:log_manager",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/controller:session_manager",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/util:session_query_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/base:proto_text_format",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/server:lifecycle",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@grpc-java//core",
        "@grpc-java//stub",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "local_session_stub",
    srcs = ["LocalSessionStub.java"],
    visibility = DEFAULT_VISIBILITY + [
        "//src/java/com/google/devtools/mobileharness/infra/ats/server/sessionplugin:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/server/sessionplugin:__pkg__",
    ],
    deps = [
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:session_service_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
    ],
)

java_library(
    name = "local_session_stub_impl",
    srcs = ["LocalSessionStubImpl.java"],
    deps = [
        ":local_session_stub",
        ":session_service",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:session_service_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "session_service",
    srcs = ["SessionService.java"],
    deps = [
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:session_java_proto",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:session_service_java_grpc",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:session_service_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/rpc/grpc:service_util",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/controller:session_manager",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/util:session_query_util",
        "@grpc-java//stub",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_protobuf_protobuf_java",
        "@maven//:javax_inject_jsr330_api",
        "@protobuf//:protobuf_java_util",
    ],
)

java_library(
    name = "version_service",
    srcs = ["VersionService.java"],
    deps = [
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:version_service_java_grpc",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:version_service_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/rpc/grpc:service_util",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/util:version_proto_util",
        "@grpc-java//stub",
    ],
)
