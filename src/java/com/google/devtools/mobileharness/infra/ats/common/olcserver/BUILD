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
        "//src/java/com/google/devtools/mobileharness/infra/ats:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/client/longrunningservice:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/shared/size:__subpackages__",
        "//third_party/py/multitest_transport:__subpackages__",
    ],
)

olc_server_binary(
    name = "ats_olc_server",
    extra_runtime_deps = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/dda/sessionplugin:ats_dda_session_plugin",
        "//src/java/com/google/devtools/mobileharness/infra/ats/server/sessionplugin:ats_server_session_plugin",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode/ats",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api:ats_test_client",
        "@io_grpc_grpc_java//alts",
    ],
)

olc_server_binary(
    name = "ats_olc_server_local_mode",
    extra_runtime_deps = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/local/sessionplugin:ats_local_session_plugin",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/sessionplugin:ats_session_plugin",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode/local",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api:ats_test",
    ],
)

java_library(
    name = "annotations",
    srcs = ["Annotations.java"],
    deps = [
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "builtin_olc_server_flags",
    srcs = ["BuiltinOlcServerFlags.java"],
    deps = [
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "module",
    srcs = ["OlcServerModule.java"],
    deps = [
        ":annotations",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common:flags_string",
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
    name = "server_preparer",
    srcs = ["ServerPreparer.java"],
    deps = [
        ":annotations",
        ":builtin_olc_server_flags",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:control_service_java_proto",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:version_service_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/rpc/grpc",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/error:exception_factory",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common:flags_string",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/constant:dirs",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/rpc/stub:control_stub",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/rpc/stub:version_stub",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/util:version_proto_util",
        "//src/java/com/google/devtools/mobileharness/shared/constant:log_record_importance",
        "//src/java/com/google/devtools/mobileharness/shared/util/base:proto_text_format",
        "//src/java/com/google/devtools/mobileharness/shared/util/base:table_formatter",
        "//src/java/com/google/devtools/mobileharness/shared/util/command",
        "//src/java/com/google/devtools/mobileharness/shared/util/command/java:java_command_creator",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:more_futures",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/system",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:sleeper",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:time_utils",
        "@io_grpc_grpc_java//core",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)
