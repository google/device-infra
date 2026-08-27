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
    name = "dimension_overlay_raw",
    srcs = ["DimensionOverlayRaw.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "lab_info_fleet_puller",
    srcs = ["LabInfoFleetPuller.java"],
    deps = [
        ":dimension_overlay_raw",
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/devtools/mobileharness/api/query/proto:lab_query_java_proto",
        "//src/devtools/mobileharness/shared/labinfo/proto:lab_info_service_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/schema:device_keys",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/shared/providers:lab_info_provider",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/util",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_protobuf_protobuf_java",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "fleet_data_source",
    srcs = ["FleetDataSource.java"],
    deps = [
        ":dimension_overlay_raw",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:core_fleet_raw_data",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "ats_fleet_data_source",
    srcs = ["AtsFleetDataSource.java"],
    deps = [
        ":dimension_overlay_raw",
        ":fleet_data_source",
        ":lab_info_fleet_puller",
        "//src/devtools/mobileharness/api/deviceconfig/proto:device_java_proto",
        "//src/devtools/mobileharness/api/query/proto:lab_query_java_proto",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:core_fleet_raw_data",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:device_enrichment",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/schema:key_registry",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/shared/providers:configuration_provider",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/util",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)
