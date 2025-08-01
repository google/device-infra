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

package(default_applicable_licenses = ["//:license"])

java_library(
    name = "annotations",
    srcs = ["Annotations.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/client/longrunningservice:__subpackages__",
    ],
    deps = [
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "olc_server",
    srcs = ["OlcServer.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/client/longrunningservice:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/shared/size:__subpackages__",
    ],
    deps = [
        ":annotations",
        ":olc_server_module",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:log_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/client/api",
        "//src/java/com/google/devtools/mobileharness/infra/client/api:annotations",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/constant:dirs",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/constant:logs",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/controller:log_manager",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/controller:log_recorder",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/controller:service_provider",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/controller:session_manager",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/rpc/service:control_service",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/rpc/service:session_service",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/rpc/service:version_service",
        "//src/java/com/google/devtools/mobileharness/infra/monitoring:monitor_pipeline_launcher",
        "//src/java/com/google/devtools/mobileharness/shared/constant:log_record_importance",
        "//src/java/com/google/devtools/mobileharness/shared/util/base:proto_text_format",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/relay/service:server_utils",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/server:client_address_server_interceptor",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/server:lifecycle",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/server:sever_builder_factory",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:callables",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:more_futures",
        "//src/java/com/google/devtools/mobileharness/shared/util/database:database_connections",
        "//src/java/com/google/devtools/mobileharness/shared/util/database:table_lister",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/inject:common_module",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging/flogger:flogger_formatter",
        "//src/java/com/google/devtools/mobileharness/shared/util/signal:signals",
        "//src/java/com/google/devtools/mobileharness/shared/util/system",
        "//src/java/com/google/devtools/mobileharness/shared/util/system:system_info_printer",
        "//src/java/com/google/devtools/mobileharness/shared/version",
        "//src/java/com/google/devtools/mobileharness/shared/version:version_util",
        "//src/java/com/google/wireless/qa/mobileharness/shared:log",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dir_preparer",
        "@io_grpc_grpc_java//core",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "olc_server_module",
    srcs = ["OlcServerModule.java"],
    deps = [
        ":annotations",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:log_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/client/api",
        "//src/java/com/google/devtools/mobileharness/infra/client/api:annotations",
        "//src/java/com/google/devtools/mobileharness/infra/client/api:module",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/controller/device:querier",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/controller:controller_module",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/controller:log_manager",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/rpc/service:local_session_stub",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/rpc/service:local_session_stub_impl",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/util/persistence:jdbc_based_session_persistence_util",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/util/persistence:session_persistence_util",
        "//src/java/com/google/devtools/mobileharness/infra/controller/scheduler/simple/persistence:allocation_persistence_util",
        "//src/java/com/google/devtools/mobileharness/infra/controller/scheduler/simple/persistence:annotations",
        "//src/java/com/google/devtools/mobileharness/infra/controller/scheduler/simple/persistence:jdbc_allocation_persistence_util",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/util:exception_handler",
        "//src/java/com/google/devtools/mobileharness/infra/monitoring:cloud_pubsub_monitor_module",
        "//src/java/com/google/devtools/mobileharness/infra/monitoring:monitor_pipeline_launcher",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/relay/service:server_utils",
        "//src/java/com/google/devtools/mobileharness/shared/util/database:database_connections",
        "//src/java/com/google/devtools/mobileharness/shared/util/reflection:reflection_util",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@maven//:javax_inject_jsr330_api",
    ],
)
