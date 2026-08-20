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
    name = "search_service_logic",
    srcs = ["SearchServiceLogic.java"],
    deps = [
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "search_service_logic_impl",
    srcs = ["SearchServiceLogicImpl.java"],
    deps = [
        ":search_service_logic",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_snapshot",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/query:device_corpus",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/query:fleet_chip_resolver",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/query:fleet_column_cataloger",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/query:fleet_flat_searcher",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/query:fleet_group_searcher",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/query:fleet_promoted_keys_provider",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/query:fleet_search_config_provider",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/query:fleet_suggester",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/query:fleet_value_lister",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/query:host_corpus",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/query:scenario_curation",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/query:search_corpus",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/refresh:fleet_snapshot_store",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "search_service_grpc_impl",
    srcs = ["SearchServiceGrpcImpl.java"],
    deps = [
        ":search_service_logic",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_service_java_grpc",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_tjs_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/grpc:fe_grpc_invoker",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/tjs:tjs_search_logic",
        "@grpc-java//stub",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "search_service_module",
    srcs = ["SearchServiceModule.java"],
    deps = [
        ":search_service_logic",
        ":search_service_logic_impl",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/pull:lab_info_fleet_puller",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/query:scenario_curation_module",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/refresh:fleet_search_data_module",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/tjs:noop_tjs_search_logic",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/tjs:tjs_search_logic",
        "@maven//:com_google_inject_guice",
    ],
)
