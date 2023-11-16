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

load("//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice:server.bzl", "olc_server_binary")

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/console:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/client/longrunningservice:__subpackages__",
    ],
)

olc_server_binary(
    name = "ats_olc_server",
    extra_runtime_deps = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/sessionplugin:ats_session_plugin",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api:ats_test",
    ],
)

java_library(
    name = "annotations",
    srcs = ["Annotations.java"],
    deps = ["@maven//:javax_inject_jsr330_api"],
)

java_library(
    name = "ats_session_stub",
    srcs = ["AtsSessionStub.java"],
    deps = [
        ":annotations",
        "//src/devtools/mobileharness/infra/ats/console/controller/proto:session_plugin_java_proto",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:session_java_proto",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:session_service_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/converter",
        "//src/java/com/google/devtools/common/metrics/stability/rpc/grpc",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/rpc/stub:session_stub",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:callables",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:sleeper",
        "@com_google_protobuf//:protobuf_java",
        "@com_google_protobuf_protobuf_java",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "module",
    srcs = ["OlcServerModule.java"],
    deps = [
        ":annotations",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/rpc/stub:control_stub",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/rpc/stub:session_stub",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/rpc/stub:version_stub",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/stub:channel_factory",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "@io_grpc_grpc_java//core",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "server_log_printer",
    srcs = ["ServerLogPrinter.java"],
    deps = [
        ":annotations",
        ":server_preparer",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:control_service_java_proto",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:log_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console:console_util",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/rpc/stub:control_stub",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@io_grpc_grpc_java//stub",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "server_preparer",
    srcs = ["ServerPreparer.java"],
    deps = [
        ":annotations",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:control_service_java_proto",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:version_service_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/rpc/grpc",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console:annotations",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console:console_util",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/rpc/stub:control_stub",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/rpc/stub:version_stub",
        "//src/java/com/google/devtools/mobileharness/shared/util/command",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/system",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:sleeper",
        "@com_google_protobuf//:protobuf_java",
        "@io_grpc_grpc_java//core",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)
