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
load("//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice:server.bzl", "olc_server_binary")
load("//src/javatests/com/google/devtools/mobileharness/builddefs:junit_test_suites.bzl", "junit_test_suites")

package(
    default_applicable_licenses = ["//:license"],
    default_testonly = 1,
)

java_library(
    name = "longrunningservice",
    srcs = glob(
        ["*.java"],
        exclude = [
            "SessionPluginForTesting.java",
        ],
    ),
    data = [
        ":api_config.textproto",
        ":olc_server_for_testing_deploy.jar",
        ":olc_server_for_testing_local_mode_deploy.jar",
        "//src/java/com/google/devtools/mobileharness/infra/lab:lab_server_oss_deploy.jar",
    ],
    deps = [
        ":session_plugin_for_testing_java_proto",
        "//src/devtools/mobileharness/api/model/proto:lab_java_proto",
        "//src/devtools/mobileharness/api/query/proto:lab_query_java_proto",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:control_service_java_proto",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:log_java_proto",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:session_java_proto",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:session_service_java_proto",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:version_service_java_proto",
        "//src/devtools/mobileharness/shared/labinfo/proto:lab_info_service_java_proto",
        "//src/devtools/mobileharness/shared/util/comm/relay/proto:destination_java_proto",
        "//src/devtools/mobileharness/shared/version/proto:version_service_java_proto",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/constant:logs",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/constant:session_properties",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/rpc/stub:control_stub",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/rpc/stub:session_stub",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/rpc/stub:version_stub",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/rpc/stub/grpc:control_grpc_stub",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/rpc/stub/grpc:session_grpc_stub",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/rpc/stub/grpc:version_grpc_stub",
        "//src/java/com/google/devtools/mobileharness/infra/master/rpc/stub/grpc:lab_info",
        "//src/java/com/google/devtools/mobileharness/shared/util/base:count_down_set",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/relay/client",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/stub:channel_factory",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/stub:master_grpc_stub_helper",
        "//src/java/com/google/devtools/mobileharness/shared/util/command",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/port:portprober",
        "//src/java/com/google/devtools/mobileharness/shared/util/system",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:sleeper",
        "//src/java/com/google/devtools/mobileharness/shared/version",
        "//src/java/com/google/devtools/mobileharness/shared/version/rpc/stub/grpc",
        "//src/javatests/com/google/devtools/mobileharness/builddefs:truth",
        "//src/javatests/com/google/devtools/mobileharness/shared/util/junit/rule:monitored_string_builders",
        "//src/javatests/com/google/devtools/mobileharness/shared/util/junit/rule:print_test_name",
        "//src/javatests/com/google/devtools/mobileharness/shared/util/runfiles:runfiles_util",
        "@io_grpc_grpc_java//core",
        "@io_grpc_grpc_java//stub",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_extensions_guice_testlib",
        "@maven//:com_google_inject_guice",
        "@maven//:com_google_protobuf_protobuf_java",
        "@maven//:com_google_truth_extensions_truth_proto_extension",
        "@maven//:javax_inject_jsr330_api",
        "@maven//:junit_junit",
    ],
)

olc_server_binary(
    name = "olc_server_for_testing",
    extra_runtime_deps = [
        ":session_plugin_for_testing",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode/ats",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/relay/service:server_utils_impl",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api:no_op_test_client",
    ],
)

olc_server_binary(
    name = "olc_server_for_testing_local_mode",
    extra_runtime_deps = [
        ":session_plugin_for_testing",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode/local",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api:no_op_test",
    ],
)

java_library(
    name = "session_plugin_for_testing",
    srcs = ["SessionPluginForTesting.java"],
    deps = [
        ":session_plugin_for_testing_java_proto",
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/devtools/mobileharness/api/model/proto:job_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/testrunner/event/test",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/model:session_event",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/model:session_info",
        "//src/java/com/google/devtools/mobileharness/shared/util/base:proto_text_format",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/client/api/event:job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dimension",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_java_proto",
        "@com_google_protobuf//:protobuf_java",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

proto_library(
    name = "session_plugin_for_testing_proto",
    srcs = ["session_plugin_for_testing.proto"],
)

java_proto_library(
    name = "session_plugin_for_testing_java_proto",
    deps = [":session_plugin_for_testing_proto"],
)

junit_test_suites(
    name = "gen_tests",
    sizes = [
        "medium",
    ],
    deps = [":longrunningservice"],
)
