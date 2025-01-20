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
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode/ats:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/client/longrunningservice:__subpackages__",
    ],
)

java_library(
    name = "controller_module",
    srcs = ["ControllerModule.java"],
    deps = [
        ":session_runner",
        "@maven//:com_google_inject_extensions_guice_assistedinject",
        "@maven//:com_google_inject_guice",
    ],
)

java_library(
    name = "log_manager",
    srcs = ["LogManager.java"],
    deps = [
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:log_java_proto",
        "//src/java/com/google/devtools/mobileharness/shared/constant:log_record_importance",
        "//src/java/com/google/devtools/mobileharness/shared/context:invocation_context",
        "//src/java/com/google/devtools/mobileharness/shared/util/command/linecallback:command_output_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:callables",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:more_futures",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "log_recorder",
    srcs = ["LogRecorder.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice:__subpackages__",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver:__subpackages__",
    ],
    deps = [
        ":log_manager",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:log_java_proto",
    ],
)

java_library(
    name = "service_provider",
    srcs = ["ServiceProvider.java"],
    deps = [
        "@io_grpc_grpc_java//core",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "session_detail_creator",
    srcs = ["SessionDetailCreator.java"],
    deps = [
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:session_java_proto",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:time_utils",
    ],
)

java_library(
    name = "session_environment_preparer",
    srcs = ["SessionEnvironmentPreparer.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/model:session_detail_holder",
        "//src/java/com/google/devtools/mobileharness/shared/constant/closeable",
        "//src/java/com/google/devtools/mobileharness/shared/util/command/linecallback:command_output_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared:log",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dir",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "session_job_creator",
    srcs = ["SessionJobCreator.java"],
    deps = [
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:session_java_proto",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/model:session_detail_holder",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "session_job_runner",
    srcs = ["SessionJobRunner.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/client/api",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/model:session_detail_holder",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:sleeper",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:property",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "session_manager",
    srcs = ["SessionManager.java"],
    deps = [
        ":session_detail_creator",
        ":session_environment_preparer",
        ":session_runner",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:session_java_proto",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:session_service_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/converter",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/constant:session_properties",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/util/persistence:session_persistence_util",
        "//src/java/com/google/devtools/mobileharness/shared/context:invocation_context",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/shared/util/base:proto_text_format",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:callables",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:more_futures",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/message:field_mask_utils",
        "@com_google_protobuf//:protobuf_java",
        "@com_google_protobuf//:protobuf_java_util",
        "@com_google_protobuf_protobuf_java",
        "@io_grpc_grpc_java//stub",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "session_plugin_loader",
    srcs = ["SessionPluginLoader.java"],
    deps = [
        ":session_environment_preparer",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:session_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/controller/device:querier",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice:annotations",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/model:session_detail_holder",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/model:session_info",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/model:session_plugin",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/model:with_proto",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/rpc/service:local_session_stub",
        "//src/java/com/google/devtools/mobileharness/infra/controller/plugin/loader:plugin_instantiator",
        "//src/java/com/google/devtools/mobileharness/shared/constant/closeable",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/shared/util/base:proto_text_format",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:thread_pools",
        "//src/java/com/google/devtools/mobileharness/shared/util/event:event_bus_backend",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/reflection:reflection_util",
        "@com_google_protobuf//:protobuf_java",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "session_plugin_runner",
    srcs = ["SessionPluginRunner.java"],
    deps = [
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:session_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/converter",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/model:session_detail_holder",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/model:session_event",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/model:session_info",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/model:session_plugin",
        "//src/java/com/google/devtools/mobileharness/shared/util/event:event_bus_backend",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "session_runner",
    srcs = ["SessionRunner.java"],
    deps = [
        ":session_environment_preparer",
        ":session_job_creator",
        ":session_job_runner",
        ":session_plugin_loader",
        ":session_plugin_runner",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:session_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/error:exception_factory",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/util/longevity",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/constant:session_properties",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/model:session_detail_holder",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/model:session_plugin",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/util:version_proto_util",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/util/persistence:session_persistence_util",
        "//src/java/com/google/devtools/mobileharness/shared/constant/closeable",
        "//src/java/com/google/devtools/mobileharness/shared/util/base:proto_text_format",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:callables",
        "//src/java/com/google/devtools/mobileharness/shared/util/event:event_bus_backend",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/path",
        "//src/java/com/google/devtools/mobileharness/shared/util/system",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "@com_google_protobuf//:protobuf_java",
        "@com_google_protobuf_protobuf_java",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_extensions_guice_assistedinject",
        "@maven//:javax_inject_jsr330_api",
    ],
)
