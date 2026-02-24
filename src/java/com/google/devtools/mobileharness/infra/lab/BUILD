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

load("@rules_java//java:defs.bzl", "java_binary", "java_library")

# Unified test run services builds
package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//:omnilab_fusion",
        "//src/java/com/google/devtools/deviceinfra:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/controller:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/lab:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/filetransfer/cloud/rpc/service:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/shared/version/rpc/service:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/client/longrunningservice:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/lab:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/shared/size:__subpackages__",
        "//third_party/py/multitest_transport:__subpackages__",
    ],
)

java_library(
    name = "annotations",
    srcs = ["Annotations.java"],
    deps = [
        "@maven//:com_google_inject_guice",
    ],
)

java_library(
    name = "lab_server",
    srcs = [
        "LabServer.java",
        "LabServerModule.java",
    ],
    runtime_deps = [
    ],
    deps = [
        ":annotations",
        ":lab_server_constants_module",
        "//src/devtools/deviceinfra/host/daemon/proto:health_java_grpc",
        "//src/devtools/mobileharness/api/model/proto:lab_java_proto",
        "//src/devtools/mobileharness/shared/trace/proto:span_java_proto",
        "//src/devtools/mobileharness/shared/util/comm/filetransfer/common/proto:tagged_file_metadata_java_proto",
        "//src/devtools/mobileharness/shared/version/proto:version_java_proto",
        "//src/java/com/google/devtools/deviceinfra/host/daemon/health:health_status_manager",
        "//src/java/com/google/devtools/deviceinfra/host/daemon/health:health_status_manager_module",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device:device_helper_factory",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device:device_id_manager",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device:test_executor_provider",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device/bootstrap:detector_dispatcher_selector",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device/bootstrap:detectors_and_dispatchers",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device/config",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device/config:api_config_file_processor",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device/external:external_device_manager",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device/external:noop_external_device_manager",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device/provider:allocated_device_provisioning_module",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device/provider:local_device_provisioning_module",
        "//src/java/com/google/devtools/mobileharness/infra/controller/messaging:message_sender_finder",
        "//src/java/com/google/devtools/mobileharness/infra/controller/messaging:messaging_manager",
        "//src/java/com/google/devtools/mobileharness/infra/controller/messaging:messaging_manager_holder",
        "//src/java/com/google/devtools/mobileharness/infra/controller/messaging:messaging_service",
        "//src/java/com/google/devtools/mobileharness/infra/controller/messaging:messaging_service_module",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/launcher:local_device_launcher_provisioning_module",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/launcher:thread_pool_launcher_provisioning_module",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/manager:lab_direct_test_runner_util",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/manager:proxy_test_manager",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/manager:test_manager_module",
        "//src/java/com/google/devtools/mobileharness/infra/lab/common/dir",
        "//src/java/com/google/devtools/mobileharness/infra/lab/controller:config_service_based_device_config_manager",
        "//src/java/com/google/devtools/mobileharness/infra/lab/controller:device_config_manager",
        "//src/java/com/google/devtools/mobileharness/infra/lab/controller:file_classifier",
        "//src/java/com/google/devtools/mobileharness/infra/lab/controller:job_manager",
        "//src/java/com/google/devtools/mobileharness/infra/lab/controller:lab_dimension_manager",
        "//src/java/com/google/devtools/mobileharness/infra/lab/controller:local_file_based_device_config_manager",
        "//src/java/com/google/devtools/mobileharness/infra/lab/controller:master_syncer_for_device",
        "//src/java/com/google/devtools/mobileharness/infra/lab/controller:master_syncer_for_job",
        "//src/java/com/google/devtools/mobileharness/infra/lab/controller/handler:drain_handler",
        "//src/java/com/google/devtools/mobileharness/infra/lab/rpc/service:exec_test",
        "//src/java/com/google/devtools/mobileharness/infra/lab/rpc/service:prepare_test",
        "//src/java/com/google/devtools/mobileharness/infra/lab/rpc/service/grpc:exec_test",
        "//src/java/com/google/devtools/mobileharness/infra/lab/rpc/service/grpc:prepare_test",
        "//src/java/com/google/devtools/mobileharness/infra/lab/rpc/service/grpc:stat",
        "//src/java/com/google/devtools/mobileharness/infra/lab/rpc/stub/helper:job_sync",
        "//src/java/com/google/devtools/mobileharness/infra/lab/rpc/stub/helper:lab_sync",
        "//src/java/com/google/devtools/mobileharness/infra/master/rpc/stub:annotation",
        "//src/java/com/google/devtools/mobileharness/infra/master/rpc/stub:job_sync",
        "//src/java/com/google/devtools/mobileharness/infra/master/rpc/stub:lab_sync",
        "//src/java/com/google/devtools/mobileharness/infra/master/rpc/stub/grpc:job_sync",
        "//src/java/com/google/devtools/mobileharness/infra/master/rpc/stub/grpc:lab_sync",
        "//src/java/com/google/devtools/mobileharness/service/deviceconfig/rpc/stub:annotation",
        "//src/java/com/google/devtools/mobileharness/service/deviceconfig/rpc/stub:device_config_stub",
        "//src/java/com/google/devtools/mobileharness/service/deviceconfig/rpc/stub/grpc:device_config_grpc_stub_module",
        "//src/java/com/google/devtools/mobileharness/shared/constant:log_record_importance",
        "//src/java/com/google/devtools/mobileharness/shared/constant/hostmanagement:host_property_constants",
        "//src/java/com/google/devtools/mobileharness/shared/constant/inject:annotations",
        "//src/java/com/google/devtools/mobileharness/shared/file/resolver:abstract_file_resolver",
        "//src/java/com/google/devtools/mobileharness/shared/file/resolver:ats_file_server_file_resolver",
        "//src/java/com/google/devtools/mobileharness/shared/file/resolver:cache_file_resolver",
        "//src/java/com/google/devtools/mobileharness/shared/file/resolver:file_resolver",
        "//src/java/com/google/devtools/mobileharness/shared/file/resolver:gcs_file_resolver",
        "//src/java/com/google/devtools/mobileharness/shared/file/resolver:local_file_resolver",
        "//src/java/com/google/devtools/mobileharness/shared/labinfo:lab_info_provider",
        "//src/java/com/google/devtools/mobileharness/shared/labinfo:lab_info_service",
        "//src/java/com/google/devtools/mobileharness/shared/labinfo:local_lab_info_provider",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/shared/util/base",
        "//src/java/com/google/devtools/mobileharness/shared/util/base:proto_text_format",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/filetransfer/cloud/rpc/service:cloud_file_transfer_service_grpc_impl",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/filetransfer/cloud/rpc/service:cloud_file_transfer_service_impl",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/filetransfer/common:tagged_file_handler",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/stub:channel_factory",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/stub:master_grpc_stub_helper",
        "//src/java/com/google/devtools/mobileharness/shared/util/command/history:recorder",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:callables",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:more_futures",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:service_module",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:thread_pools",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/system",
        "//src/java/com/google/devtools/mobileharness/shared/util/system:system_info_printer",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:sleeper",
        "//src/java/com/google/devtools/mobileharness/shared/version",
        "//src/java/com/google/devtools/mobileharness/shared/version:version_util",
        "//src/java/com/google/devtools/mobileharness/shared/version/rpc/service",
        "//src/java/com/google/devtools/mobileharness/shared/version/rpc/service/grpc",
        "//src/java/com/google/wireless/qa/mobileharness/shared:log",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:base_device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/comm/message:test_message_manager",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dimension",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dir",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:exit_code",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job/in/spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/util:device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/util:net_util",
        "@grpc-java//core",
        "@grpc-java//netty",
        "@grpc-java//services:reflection",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_extensions_guice_assistedinject",
        "@maven//:com_google_inject_guice",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "lab_server_constants_module",
    srcs = [
        "LabServerConstantsModule.java",
    ],
    deps = [
        ":annotations",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/wireless/qa/mobileharness/shared/util:device",
        "@maven//:com_google_inject_guice",
    ],
)

java_library(
    name = "lab_server_launcher",
    srcs = ["LabServerLauncher.java"],
    resources = [
    ],
    deps = [
        ":lab_server",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common/constant:builtin_flags",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/util:exception_handler",
        "//src/java/com/google/devtools/mobileharness/shared/logging:mobileharness_host_log_manager",
        "//src/java/com/google/devtools/mobileharness/shared/logging/parameter",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:external_service_manager",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging/flogger:flogger_formatter",
        "//src/java/com/google/devtools/mobileharness/shared/util/system",
        "//src/java/com/google/devtools/mobileharness/shared/util/system:shutdown_hook_manager",
        "//src/java/com/google/devtools/mobileharness/shared/util/system:system_properties_util",
        "//src/java/com/google/devtools/mobileharness/shared/version",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:exit_code",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
    ],
)

# Open-source version of MH lab server.
java_binary(
    name = "lab_server_oss",
    main_class = "com.google.devtools.mobileharness.infra.lab.LabServerLauncher",
    # Think twice before adding resources here. Always try to pack the resources
    # into the libraries using the resources.
    runtime_deps = [
        ":lab_server_launcher",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api:oss_lab_test",
    ],
)
