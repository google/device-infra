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
    name = "fleet_filter_engine",
    srcs = ["FleetFilterEngine.java"],
    deps = [
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:device_record",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_index",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_search_keys",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_snapshot",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_test(
    name = "FleetFilterEngineTest",
    srcs = ["FleetFilterEngineTest.java"],
    deps = [
        ":fleet_filter_engine",
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/devtools/mobileharness/api/model/proto:lab_java_proto",
        "//src/devtools/mobileharness/api/query/proto:lab_query_java_proto",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_index_builder",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_snapshot",
        "//src/javatests/com/google/devtools/mobileharness/builddefs:truth",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@maven//:junit_junit",
    ],
)

java_library(
    name = "fleet_cell_mapper",
    srcs = ["FleetCellMapper.java"],
    deps = [
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_common_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:device_record",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_search_keys",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_snapshot",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "fleet_flat_searcher",
    srcs = ["FleetFlatSearcher.java"],
    deps = [
        ":fleet_cell_mapper",
        ":fleet_filter_engine",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:device_record",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_search_keys",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_snapshot",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_test(
    name = "FleetFlatSearcherTest",
    srcs = ["FleetFlatSearcherTest.java"],
    deps = [
        ":fleet_flat_searcher",
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/devtools/mobileharness/api/model/proto:lab_java_proto",
        "//src/devtools/mobileharness/api/query/proto:lab_query_java_proto",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_common_java_proto",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:device_enrichment",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_index_builder",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_raw_data",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_snapshot",
        "//src/javatests/com/google/devtools/mobileharness/builddefs:truth",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@maven//:junit_junit",
    ],
)

java_library(
    name = "fleet_value_lister",
    srcs = ["FleetValueLister.java"],
    deps = [
        ":fleet_filter_engine",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_index",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_search_keys",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_snapshot",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_test(
    name = "FleetValueListerTest",
    srcs = ["FleetValueListerTest.java"],
    deps = [
        ":fleet_value_lister",
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/devtools/mobileharness/api/model/proto:lab_java_proto",
        "//src/devtools/mobileharness/api/query/proto:lab_query_java_proto",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_index_builder",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_snapshot",
        "//src/javatests/com/google/devtools/mobileharness/builddefs:truth",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@maven//:junit_junit",
    ],
)

java_library(
    name = "fleet_chip_resolver",
    srcs = ["FleetChipResolver.java"],
    deps = [
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_index",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_search_keys",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_snapshot",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_test(
    name = "FleetChipResolverTest",
    srcs = ["FleetChipResolverTest.java"],
    deps = [
        ":fleet_chip_resolver",
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/devtools/mobileharness/api/model/proto:lab_java_proto",
        "//src/devtools/mobileharness/api/query/proto:lab_query_java_proto",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_index_builder",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_snapshot",
        "//src/javatests/com/google/devtools/mobileharness/builddefs:truth",
        "@maven//:com_google_inject_guice",
        "@maven//:junit_junit",
    ],
)

java_library(
    name = "scenario_curation",
    srcs = ["ScenarioCuration.java"],
    deps = [
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "fleet_key_priority",
    srcs = ["FleetKeyPriority.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_search_keys",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "ats_one_curation",
    srcs = ["AtsOneCuration.java"],
    deps = [
        ":fleet_key_priority",
        ":scenario_curation",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_search_keys",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "scenario_curation_module",
    srcs = ["ScenarioCurationModule.java"],
    deps = [
        ":ats_one_curation",
        ":scenario_curation",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "@maven//:com_google_inject_guice",
    ],
)

java_test(
    name = "FleetKeyPriorityTest",
    srcs = ["FleetKeyPriorityTest.java"],
    deps = [
        ":fleet_key_priority",
        "//src/javatests/com/google/devtools/mobileharness/builddefs:truth",
        "@maven//:com_google_testparameterinjector_test_parameter_injector",
        "@maven//:junit_junit",
    ],
)

java_test(
    name = "AtsOneCurationTest",
    srcs = ["AtsOneCurationTest.java"],
    deps = [
        ":ats_one_curation",
        ":fleet_key_priority",
        "//src/javatests/com/google/devtools/mobileharness/builddefs:truth",
        "@maven//:junit_junit",
    ],
)

java_library(
    name = "fleet_promoted_keys_provider",
    srcs = ["FleetPromotedKeysProvider.java"],
    deps = [
        ":fleet_filter_engine",
        ":scenario_curation",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:device_record",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_index",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_search_keys",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_snapshot",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_test(
    name = "FleetPromotedKeysProviderTest",
    srcs = ["FleetPromotedKeysProviderTest.java"],
    deps = [
        ":fleet_filter_engine",
        ":fleet_promoted_keys_provider",
        ":scenario_curation",
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/devtools/mobileharness/api/model/proto:lab_java_proto",
        "//src/devtools/mobileharness/api/query/proto:lab_query_java_proto",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_index_builder",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_snapshot",
        "//src/javatests/com/google/devtools/mobileharness/builddefs:truth",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@maven//:junit_junit",
    ],
)

java_library(
    name = "fleet_suggester",
    srcs = ["FleetSuggester.java"],
    deps = [
        ":fleet_filter_engine",
        ":scenario_curation",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_common_java_proto",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:device_record",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_index",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_search_keys",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_snapshot",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_test(
    name = "FleetSuggesterTest",
    srcs = ["FleetSuggesterTest.java"],
    deps = [
        ":ats_one_curation",
        ":fleet_filter_engine",
        ":fleet_suggester",
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/devtools/mobileharness/api/model/proto:lab_java_proto",
        "//src/devtools/mobileharness/api/query/proto:lab_query_java_proto",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_index_builder",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_snapshot",
        "//src/javatests/com/google/devtools/mobileharness/builddefs:truth",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@maven//:junit_junit",
    ],
)

java_library(
    name = "fleet_column_cataloger",
    srcs = ["FleetColumnCataloger.java"],
    deps = [
        ":fleet_filter_engine",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:device_record",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_index",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_search_keys",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_snapshot",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_test(
    name = "FleetColumnCatalogerTest",
    srcs = ["FleetColumnCatalogerTest.java"],
    deps = [
        ":fleet_column_cataloger",
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/devtools/mobileharness/api/model/proto:lab_java_proto",
        "//src/devtools/mobileharness/api/query/proto:lab_query_java_proto",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_index_builder",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_snapshot",
        "//src/javatests/com/google/devtools/mobileharness/builddefs:truth",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@maven//:junit_junit",
    ],
)

java_library(
    name = "fleet_search_config_provider",
    srcs = ["FleetSearchConfigProvider.java"],
    deps = [
        ":scenario_curation",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_common_java_proto",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_index",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_search_keys",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_snapshot",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_test(
    name = "FleetSearchConfigProviderTest",
    srcs = ["FleetSearchConfigProviderTest.java"],
    deps = [
        ":fleet_search_config_provider",
        ":scenario_curation",
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/devtools/mobileharness/api/model/proto:lab_java_proto",
        "//src/devtools/mobileharness/api/query/proto:lab_query_java_proto",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_common_java_proto",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_index_builder",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_snapshot",
        "//src/javatests/com/google/devtools/mobileharness/builddefs:truth",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@maven//:junit_junit",
    ],
)

java_library(
    name = "fleet_group_searcher",
    srcs = ["FleetGroupSearcher.java"],
    deps = [
        ":fleet_cell_mapper",
        ":fleet_filter_engine",
        ":fleet_flat_searcher",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:device_record",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_index",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_snapshot",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_test(
    name = "FleetGroupSearcherTest",
    srcs = ["FleetGroupSearcherTest.java"],
    deps = [
        ":fleet_group_searcher",
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/devtools/mobileharness/api/model/proto:lab_java_proto",
        "//src/devtools/mobileharness/api/query/proto:lab_query_java_proto",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_common_java_proto",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_index_builder",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_snapshot",
        "//src/javatests/com/google/devtools/mobileharness/builddefs:truth",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@maven//:junit_junit",
    ],
)
