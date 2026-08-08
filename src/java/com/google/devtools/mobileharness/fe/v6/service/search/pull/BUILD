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
load("@rules_java//java:java_test.bzl", "java_test")

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = ["//src/devtools/mobileharness/fe/v6:visibility"],
)

java_library(
    name = "lab_info_fleet_puller",
    srcs = ["LabInfoFleetPuller.java"],
    deps = [
        "//src/devtools/mobileharness/api/query/proto:lab_query_java_proto",
        "//src/devtools/mobileharness/shared/labinfo/proto:lab_info_service_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/shared/providers:lab_info_provider",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/util",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "fleet_data_source",
    srcs = ["FleetDataSource.java"],
    deps = [
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_raw_data",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "ats_one_fleet_data_source",
    srcs = ["AtsOneFleetDataSource.java"],
    deps = [
        ":fleet_data_source",
        ":lab_info_fleet_puller",
        "//src/devtools/mobileharness/api/deviceconfig/proto:device_java_proto",
        "//src/devtools/mobileharness/api/query/proto:lab_query_java_proto",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:device_enrichment",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_raw_data",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/shared/providers:configuration_provider",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/util",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_test(
    name = "AtsOneFleetDataSourceTest",
    srcs = ["AtsOneFleetDataSourceTest.java"],
    deps = [
        ":ats_one_fleet_data_source",
        ":lab_info_fleet_puller",
        "//src/devtools/mobileharness/api/deviceconfig/proto:basic_java_proto",
        "//src/devtools/mobileharness/api/deviceconfig/proto:device_java_proto",
        "//src/devtools/mobileharness/api/deviceconfig/proto:lab_java_proto",
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/devtools/mobileharness/api/query/proto:lab_query_java_proto",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "//src/devtools/mobileharness/shared/labinfo/proto:lab_info_service_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_raw_data",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/shared/providers:config_result",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/shared/providers:configuration_provider",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/shared/providers:lab_info_provider",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/util",
        "//src/javatests/com/google/devtools/mobileharness/builddefs:truth",
        "@maven//:com_google_guava_guava",
        "@maven//:junit_junit",
    ],
)
