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
    name = "lab_info_provider",
    srcs = ["LabInfoProvider.java"],
    visibility = [
        "//:omnilab_fusion",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode/ats:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/lab:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/monitoring:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/monitoring:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/shared/labinfo:__pkg__",
    ],
    deps = [
        "//src/devtools/mobileharness/api/query/proto:lab_query_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
    ],
)

java_library(
    name = "lab_info_service",
    srcs = ["LabInfoService.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode/ats:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/lab:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/shared/labinfo:__pkg__",
    ],
    deps = [
        ":lab_info_provider",
        ":lab_query_utils",
        "//src/devtools/mobileharness/api/query/proto:lab_query_java_proto",
        "//src/devtools/mobileharness/shared/labinfo/proto:lab_info_service_java_grpc_proto",
        "//src/devtools/mobileharness/shared/labinfo/proto:lab_info_service_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/rpc/grpc:service_util",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/shared/util/base:proto_text_format",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@io_grpc_grpc_java//stub",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "local_lab_info_provider",
    srcs = ["LocalLabInfoProvider.java"],
    visibility = ["//src/java/com/google/devtools/mobileharness/infra/lab:__pkg__"],
    deps = [
        ":lab_info_provider",
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/devtools/mobileharness/api/query/proto:lab_query_java_proto",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device:device_status_info",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "lab_query_utils",
    srcs = ["LabQueryUtils.java"],
    visibility = ["//:deviceinfra_all_pkg"],
    deps = [
        ":lab_info_provider",
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/devtools/mobileharness/api/query/proto:lab_query_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/shared/util/filter:mask_utils",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:time_utils",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dimension",
        "@maven//:com_google_guava_guava",
    ],
)
