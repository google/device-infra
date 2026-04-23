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

load("@rules_java//java:java_library.bzl", "java_library")

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = ["//src/devtools/mobileharness/fe/v6:visibility"],
)

java_library(
    name = "shared",
    srcs = glob(
        ["*.java"],
        exclude = ["OssStubsModule.java"],
    ),
    deps = [
        "//src/devtools/mobileharness/api/deviceconfig/proto:device_java_proto",
        "//src/devtools/mobileharness/api/deviceconfig/proto:lab_java_proto",
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/devtools/mobileharness/api/query/proto:filter_java_proto",
        "//src/devtools/mobileharness/api/query/proto:lab_query_java_proto",
        "//src/devtools/mobileharness/fe/v6/service/proto/common:common_resources_java_proto",
        "//src/devtools/mobileharness/fe/v6/service/proto/device:device_resources_java_proto",
        "//src/devtools/mobileharness/shared/labinfo/proto:lab_info_service_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/config/util:config_service_capability",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/shared/providers:configuration_provider",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/shared/providers:lab_info_provider",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/util",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/spec:testbed_device_spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dimension",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:device_model_java_proto",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@maven//:javax_inject_jsr330_api",
        "@protobuf//:protobuf_java",
    ],
)

java_library(
    name = "oss_stubs",
    srcs = ["OssStubsModule.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/config/util:config_service_capability",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/device/handlers",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/device/handlers:logcat_action_helper",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/device/handlers:screenshot_action_helper",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/host/builder",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/host/provider",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/shared/auth",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/shared/providers:configuration_provider",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/shared/providers:configuration_provider_impl",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/shared/providers:lab_info_provider",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/shared/providers:lab_info_provider_impl",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/shared/providers:no_op_configuration_provider",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/shared/providers:no_op_routed_universe_lab_info_stub_factory",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/shared/providers:routed_universe_lab_info_stub_factory",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/util",
        "//src/java/com/google/devtools/mobileharness/infra/master/rpc/stub:job_sync",
        "//src/java/com/google/devtools/mobileharness/infra/master/rpc/stub:lab_info",
        "//src/java/com/google/devtools/mobileharness/infra/master/rpc/stub/grpc:job_sync",
        "//src/java/com/google/devtools/mobileharness/infra/master/rpc/stub/grpc:lab_info",
        "//src/java/com/google/devtools/mobileharness/service/deviceconfig/rpc/stub:annotation",
        "//src/java/com/google/devtools/mobileharness/service/deviceconfig/rpc/stub:device_config_stub",
        "//src/java/com/google/devtools/mobileharness/service/deviceconfig/rpc/stub/grpc:device_config_grpc_stub_module",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/stub:channel_factory",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/stub:master_grpc_stub_helper",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "@grpc-java//core",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_extensions_guice_assistedinject",
        "@maven//:com_google_inject_guice",
        "@maven//:javax_inject_jsr330_api",
    ],
)
