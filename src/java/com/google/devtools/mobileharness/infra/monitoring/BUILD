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
        "//:omnilab_fusion",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/monitoring:__pkg__",
    ],
)

java_library(
    name = "cloud_pubsub_monitor_module",
    srcs = [
        "CloudPubsubMonitorModule.java",
        "LabInfoPullerImpl.java",
        "PubsubClientImpl.java",
    ],
    deps = [
        ":monitor_lib",
        ":monitor_pipeline_launcher",
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/devtools/mobileharness/api/model/proto:lab_java_proto",
        "//src/devtools/mobileharness/api/query/proto:lab_query_java_proto",
        "//src/devtools/mobileharness/infra/monitoring/proto:monitored_record_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/constant/hostmanagement:host_property_constants",
        "//src/java/com/google/devtools/mobileharness/shared/labinfo:lab_info_provider",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:time_utils",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dimension",
        "@grpc-java//auth",
        "@grpc-java//core",
        "@maven//:com_google_api_grpc_grpc_google_cloud_pubsub_v1",
        "@maven//:com_google_api_grpc_proto_google_cloud_pubsub_v1",
        "@maven//:com_google_auth_google_auth_library_oauth2_http",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@maven//:com_google_protobuf_protobuf_java",
        "@maven//:javax_inject_jsr330_api",
        "@protobuf//:protobuf_java",
        "@protobuf//:protobuf_java_util",
    ],
)

java_library(
    name = "monitor_lib",
    srcs = [
        "BatchPipelineService.java",
        "DataPuller.java",
        "DataPusher.java",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@protobuf//:protobuf_java",
    ],
)

java_library(
    name = "monitor_pipeline_launcher",
    srcs = ["MonitorPipelineLauncher.java"],
    deps = [
        ":monitor_lib",
        "//src/devtools/mobileharness/infra/monitoring/proto:monitored_record_java_proto",
        "@maven//:com_google_inject_guice",
    ],
)
