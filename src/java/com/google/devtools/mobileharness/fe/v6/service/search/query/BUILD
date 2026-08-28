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
    name = "search_corpus",
    srcs = ["SearchCorpus.java"],
    deps = [
        ":scenario_curation",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_common_java_proto",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_index",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:postings",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "fleet_key_displays",
    srcs = ["FleetKeyDisplays.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/schema:ats_device_keys",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/schema:device_keys",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/schema:host_keys",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "device_corpus",
    srcs = ["DeviceCorpus.java"],
    deps = [
        ":fleet_cell_mapper",
        ":fleet_key_displays",
        ":scenario_curation",
        ":search_corpus",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_common_java_proto",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:composite_fleet_index",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:composite_postings",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:device_record",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_index",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_snapshot",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:overlay_view",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:postings",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/schema:device_keys",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "host_corpus",
    srcs = ["HostCorpus.java"],
    deps = [
        ":fleet_key_displays",
        ":host_cell_mapper",
        ":scenario_curation",
        ":search_corpus",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_common_java_proto",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_index",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_snapshot",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:postings",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/schema:host_keys",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "fleet_filter_engine",
    srcs = ["FleetFilterEngine.java"],
    deps = [
        ":search_corpus",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_index",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:postings",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "fleet_cell_mapper",
    srcs = ["FleetCellMapper.java"],
    deps = [
        ":fleet_key_displays",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_common_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:device_record",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_snapshot",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/schema:device_keys",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/schema:host_keys",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "host_cell_mapper",
    srcs = ["HostCellMapper.java"],
    deps = [
        ":fleet_key_displays",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_common_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_snapshot",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:host_record",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/schema:host_keys",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "fleet_flat_searcher",
    srcs = ["FleetFlatSearcher.java"],
    deps = [
        ":fleet_filter_engine",
        ":search_corpus",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_common_java_proto",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "fleet_value_lister",
    srcs = ["FleetValueLister.java"],
    deps = [
        ":fleet_filter_engine",
        ":search_corpus",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_index",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:postings",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "fleet_chip_resolver",
    srcs = ["FleetChipResolver.java"],
    deps = [
        ":fleet_key_displays",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_index",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_snapshot",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/schema:device_keys",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/schema:host_keys",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "scenario_curation",
    srcs = ["ScenarioCuration.java"],
    deps = [
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/schema:key_descriptor",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "fleet_key_priority",
    srcs = ["FleetKeyPriority.java"],
    deps = [
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/schema:ats_device_keys",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/schema:device_keys",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/schema:host_keys",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "ats_curation",
    srcs = ["AtsCuration.java"],
    deps = [
        ":fleet_key_priority",
        ":scenario_curation",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/schema:ats_device_keys",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/schema:device_keys",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/schema:host_keys",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/schema:key_descriptor",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "scenario_curation_module",
    srcs = ["ScenarioCurationModule.java"],
    deps = [
        ":ats_curation",
        ":scenario_curation",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "@maven//:com_google_inject_guice",
    ],
)

java_library(
    name = "fleet_promoted_keys_provider",
    srcs = ["FleetPromotedKeysProvider.java"],
    deps = [
        ":fleet_filter_engine",
        ":scenario_curation",
        ":search_corpus",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_index",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/schema:host_keys",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/schema:key_descriptor",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "fleet_suggester",
    srcs = ["FleetSuggester.java"],
    deps = [
        ":fleet_filter_engine",
        ":fleet_key_displays",
        ":scenario_curation",
        ":search_corpus",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_common_java_proto",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_index",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:key_count",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:postings",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:value_key_pair",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/schema:ats_device_keys",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/schema:device_keys",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/schema:host_keys",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "fleet_column_cataloger",
    srcs = ["FleetColumnCataloger.java"],
    deps = [
        ":fleet_key_displays",
        ":search_corpus",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_index",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:postings",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/schema:device_keys",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/schema:host_keys",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "fleet_search_config_provider",
    srcs = ["FleetSearchConfigProvider.java"],
    deps = [
        ":scenario_curation",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_common_java_proto",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_snapshot",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/schema:device_keys",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/schema:host_keys",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/schema:key_descriptor",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "fleet_group_searcher",
    srcs = ["FleetGroupSearcher.java"],
    deps = [
        ":fleet_filter_engine",
        ":fleet_flat_searcher",
        ":search_corpus",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_index",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)
