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
    name = "handlers",
    srcs = glob(
        ["*.java"],
        exclude = [
            "LogcatActionHelper.java",
            "NoOpLogcatActionHelper.java",
        ],
    ),
    deps = [
        ":logcat_action_helper",
        "//src/devtools/mobileharness/api/deviceconfig/proto:device_java_proto",
        "//src/devtools/mobileharness/api/deviceconfig/proto:lab_java_proto",
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/devtools/mobileharness/api/query/proto:filter_java_proto",
        "//src/devtools/mobileharness/api/query/proto:lab_query_java_proto",
        "//src/devtools/mobileharness/fe/v6/service/proto/common:common_resources_java_proto",
        "//src/devtools/mobileharness/fe/v6/service/proto/device:device_resources_java_proto",
        "//src/devtools/mobileharness/fe/v6/service/proto/device:device_service_java_proto",
        "//src/devtools/mobileharness/fe/v6/service/proto/host:host_resources_java_proto",
        "//src/devtools/mobileharness/infra/master/rpc/proto:job_sync_service_java_proto",
        "//src/devtools/mobileharness/shared/labinfo/proto:lab_info_service_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/shared",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/shared/providers:configuration_provider",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/shared/providers:lab_info_provider",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/shared/remotecontrol",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/util",
        "//src/java/com/google/devtools/mobileharness/infra/master/rpc/stub:annotation",
        "//src/java/com/google/devtools/mobileharness/infra/master/rpc/stub:job_sync",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:time_utils",
        "@maven//:com_github_ben_manes_caffeine_caffeine",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_protobuf_protobuf_java",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "logcat_action_helper",
    srcs = [
        "LogcatActionHelper.java",
        "NoOpLogcatActionHelper.java",
    ],
    deps = [
        "//src/devtools/mobileharness/fe/v6/service/proto/device:device_service_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/util",
        "@maven//:com_google_guava_guava",
    ],
)
