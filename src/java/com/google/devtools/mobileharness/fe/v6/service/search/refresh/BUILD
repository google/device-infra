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
    name = "fleet_snapshot_store",
    srcs = ["FleetSnapshotStore.java"],
    deps = [
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_snapshot",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "fleet_data_refresher",
    srcs = ["FleetDataRefresher.java"],
    deps = [
        ":fleet_snapshot_store",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_index_builder",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_raw_data",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_snapshot",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/pull:fleet_data_source",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "fleet_search_data_module",
    srcs = ["FleetSearchDataModule.java"],
    deps = [
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/pull:ats_one_fleet_data_source",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/pull:fleet_data_source",
        "@maven//:com_google_inject_guice",
    ],
)

java_test(
    name = "FleetSnapshotStoreTest",
    srcs = ["FleetSnapshotStoreTest.java"],
    deps = [
        ":fleet_snapshot_store",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_index",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_snapshot",
        "//src/javatests/com/google/devtools/mobileharness/builddefs:truth",
        "@maven//:com_google_guava_guava",
        "@maven//:junit_junit",
    ],
)

java_test(
    name = "FleetDataRefresherTest",
    srcs = ["FleetDataRefresherTest.java"],
    deps = [
        ":fleet_data_refresher",
        ":fleet_snapshot_store",
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/devtools/mobileharness/api/query/proto:lab_query_java_proto",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_index_builder",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_raw_data",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/pull:fleet_data_source",
        "//src/javatests/com/google/devtools/mobileharness/builddefs:truth",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@maven//:junit_junit",
    ],
)
