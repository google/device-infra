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

# Shared libs for mobile device management and test execution.
package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
    ],
)

java_library(
    name = "api_config_proto_util",
    srcs = ["ApiConfigProtoUtil.java"],
    visibility = [
    ],
    deps = [
        "//src/devtools/mobileharness/api/deviceconfig/proto:basic_java_proto",
        "//src/devtools/mobileharness/api/deviceconfig/proto:device_java_proto",
        "//src/devtools/mobileharness/api/deviceconfig/proto:lab_device_java_proto",
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dimension",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:config_java_proto",
        "@com_google_protobuf//:protobuf_java",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
    ],
)

java_library(
    name = "api_config_file_processor",
    srcs = ["ApiConfigFileProcessor.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode/local:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/lab:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/lab/controller:__pkg__",
    ],
    deps = [
        ":api_config_proto_util",
        "//src/devtools/mobileharness/api/deviceconfig/proto:basic_java_proto",
        "//src/devtools/mobileharness/api/deviceconfig/proto:device_java_proto",
        "//src/devtools/mobileharness/api/deviceconfig/proto:lab_device_java_proto",
        "//src/devtools/mobileharness/api/deviceconfig/proto:lab_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/service/deviceconfig/util/generator",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:config_java_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/util:net_util",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "config",
    srcs = [
        "ApiConfig.java",
        "ApiConfigV5.java",
    ],
    visibility = [
        "//:deviceinfra_all_pkg",
    ],
    deps = [
        "//src/devtools/mobileharness/api/deviceconfig/proto:basic_java_proto",
        "//src/devtools/mobileharness/api/deviceconfig/proto:device_java_proto",
        "//src/devtools/mobileharness/api/deviceconfig/proto:lab_java_proto",
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/devtools/mobileharness/api/model/proto:lab_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:device_id_descriptor",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device:device_id_manager",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/message:str_pair",
        "//src/java/com/google/devtools/mobileharness/shared/util/system",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:common_java_proto",
        "@com_google_protobuf_protobuf_java",
        "@maven//:com_google_guava_guava",
    ],
)
