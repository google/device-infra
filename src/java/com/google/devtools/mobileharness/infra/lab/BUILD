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

# Unified test run services builds
package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//src/java/com/google/wireless/qa/mobileharness/lab:__pkg__",
    ],
)

java_library(
    name = "utrs",
    srcs = [
        "UnifiedTestRunServer.java",
        "UnifiedTestRunServerModule.java",
        "UtrsTestRunLabModule.java",
        "UtrsThreadPoolFactoryModule.java",
    ],
    deps = [
        ":annotations",
        ":test_run_base_module",
        ":test_run_module",
        "//src/devtools/mobileharness/shared/trace/proto:span_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device:base_detector_dispatcher_picker",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device:device_helper_factory",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device:device_id_manager",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device:local_device_runner_provider",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device/config",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device/external:external_device_manager",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device/external:noop_external_device_manager",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/manager:proxy_test_manager",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/manager:test_message_poster_util",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/util:exception_handler",
        "//src/java/com/google/devtools/mobileharness/infra/lab/common/dir",
        "//src/java/com/google/devtools/mobileharness/infra/lab/controller:job_manager",
        "//src/java/com/google/devtools/mobileharness/infra/lab/controller:lab_dimension_manager",
        "//src/java/com/google/devtools/mobileharness/infra/lab/controller:lab_direct_test_runner_holder",
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
        "//src/java/com/google/devtools/mobileharness/shared/file/resolver:abstract_file_resolver",
        "//src/java/com/google/devtools/mobileharness/shared/file/resolver:file_resolver",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/stub:master_grpc_stub_helper",
        "//src/java/com/google/devtools/mobileharness/shared/util/command/history:recorder",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:more_futures",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:thread_factory_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/error:more_throwables",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/system",
        "//src/java/com/google/devtools/mobileharness/shared/version",
        "//src/java/com/google/devtools/mobileharness/shared/version/rpc/service",
        "//src/java/com/google/devtools/mobileharness/shared/version/rpc/service/grpc",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared:log",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:base_device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/comm/message:test_message_manager",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dimension",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dir",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:exit_code",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job/in/spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/util:device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/util:net_util",
        "@io_grpc_grpc_java//core",
        "@io_grpc_grpc_java//netty",
        "@io_grpc_grpc_java//services:reflection",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_extensions_guice_assistedinject",
        "@maven//:com_google_inject_guice",
        "@maven//:javax_inject_jsr330_api",
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
    name = "test_run_base_module",
    srcs = ["TestRunBaseModule.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/manager",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/manager:proxy_test_manager",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/system",
        "//src/java/com/google/wireless/qa/mobileharness/shared/util:net_util",
        "@maven//:com_google_inject_guice",
    ],
)

java_library(
    name = "test_run_module",
    srcs = ["TestRunModule.java"],
    deps = [
        ":annotations",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device:local_device_runner_provider",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/manager:proxy_test_manager",
        "//src/java/com/google/devtools/mobileharness/infra/lab/controller:job_manager",
        "//src/java/com/google/devtools/mobileharness/infra/lab/rpc/service:prepare_test",
        "//src/java/com/google/devtools/mobileharness/shared/file/resolver:file_resolver",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/system",
        "//src/java/com/google/wireless/qa/mobileharness/shared/util:net_util",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
    ],
)
