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

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//java/com/google/devtools/deviceinfra/host/utrs:__subpackages__",
        "//java/com/google/devtools/mobileharness/infra/container/testengine:__subpackages__",
        "//java/com/google/devtools/mobileharness/infra/lab:__subpackages__",
        "//javatests/com/google/devtools/deviceinfra/host/utrs:__subpackages__",
        "//javatests/com/google/devtools/mobileharness/infra/lab:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/lab:__subpackages__",
    ],
)

java_library(
    name = "prepare_test",
    srcs = ["PrepareTestServiceImpl.java"],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:job_java_proto",
        "//src/devtools/mobileharness/infra/container/proto:mode_setting_java_proto",
        "//src/devtools/mobileharness/infra/container/proto:test_engine_java_proto",
        "//src/devtools/mobileharness/infra/lab/proto:prepare_test_service_java_proto",
        "//src/devtools/mobileharness/shared/version/proto:version_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/allocation",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/job:locator",
        "//src/java/com/google/devtools/mobileharness/api/model/job/in",
        "//src/java/com/google/devtools/mobileharness/api/model/job/out:timing",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:locator",
        "//src/java/com/google/devtools/mobileharness/infra/container/controller:proxy_test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/container/controller:proxy_to_direct_test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device:local_device_runner_provider",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device:local_device_test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:abstract_test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/launcher:local_device_test_runner_launcher",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/manager:proxy_test_manager",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/model:test_execution_unit",
        "//src/java/com/google/devtools/mobileharness/infra/lab/common/dir",
        "//src/java/com/google/devtools/mobileharness/infra/lab/controller:job_manager",
        "//src/java/com/google/devtools/mobileharness/infra/lab/controller/util:lab_file_notifier",
        "//src/java/com/google/devtools/mobileharness/shared/file/resolver:file_resolver",
        "//src/java/com/google/devtools/mobileharness/shared/util/command",
        "//src/java/com/google/devtools/mobileharness/shared/util/error:error_model_converter",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local:res_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/path",
        "//src/java/com/google/devtools/mobileharness/shared/util/system",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:sleeper",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:time_utils",
        "//src/java/com/google/devtools/mobileharness/shared/version",
        "//src/java/com/google/devtools/mobileharness/shared/version/checker",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_java_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/util:base",
        "//src/java/com/google/wireless/qa/mobileharness/shared/util:device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/util:net_util",
        "@com_google_protobuf//:protobuf_java",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "exec_test",
    srcs = ["ExecTestServiceImpl.java"],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/job/out",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device:device_helper_factory",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:abstract_test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:direct_test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:direct_test_runner_setting",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/exception",
        "//src/java/com/google/devtools/mobileharness/infra/lab/common/env",
        "//src/java/com/google/devtools/mobileharness/infra/lab/controller:file_publisher",
        "//src/java/com/google/devtools/mobileharness/infra/lab/controller:forwarding_test_message_buffer",
        "//src/java/com/google/devtools/mobileharness/infra/lab/controller:lab_direct_test_runner_holder",
        "//src/java/com/google/devtools/mobileharness/infra/lab/controller:lab_local_direct_test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/lab/controller/util:lab_file_notifier",
        "//src/java/com/google/devtools/mobileharness/infra/lab/rpc/service/util:lab_response_proto_generator",
        "//src/java/com/google/devtools/mobileharness/infra/lab/rpc/service/util:test_info_creator",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/messaging/message",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/message:str_pair",
        "//src/java/com/google/wireless/qa/mobileharness/lab/proto:exec_test_serv_java_proto",
        "//src/java/com/google/wireless/qa/mobileharness/lab/proto:stat_java_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api",
        "//src/java/com/google/wireless/qa/mobileharness/shared/comm/message:test_message_manager",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:property",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/allocation",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/lab:locator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_java_proto",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_extensions_guice_assistedinject",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "stat",
    srcs = ["StatServiceImpl.java"],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:locator",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/wireless/qa/mobileharness/lab/proto:stat_java_proto",
        "//src/java/com/google/wireless/qa/mobileharness/lab/proto:stat_serv_java_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/controller/stat",
        "@maven//:com_google_guava_guava",
    ],
)
