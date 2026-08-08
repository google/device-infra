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
    name = "fleet_search_keys",
    srcs = ["FleetSearchKeys.java"],
    deps = ["@maven//:com_google_guava_guava"],
)

java_library(
    name = "fleet_search_index",
    srcs = ["FleetSearchIndex.java"],
    deps = [
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "device_record",
    srcs = ["DeviceRecord.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "host_record",
    srcs = ["HostRecord.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "device_enrichment",
    srcs = ["DeviceEnrichment.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
    ],
)

java_library(
    name = "host_enrichment",
    srcs = ["HostEnrichment.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "fleet_raw_data",
    srcs = ["FleetRawData.java"],
    deps = [
        ":device_enrichment",
        ":host_enrichment",
        "//src/devtools/mobileharness/api/query/proto:lab_query_java_proto",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "value_key_pair",
    srcs = ["ValueKeyPair.java"],
)

java_library(
    name = "key_count",
    srcs = ["KeyCount.java"],
)

java_library(
    name = "fleet_index",
    srcs = ["FleetIndex.java"],
    deps = [
        ":key_count",
        ":value_key_pair",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "device_value_extractor",
    srcs = ["DeviceValueExtractor.java"],
    deps = [
        ":device_record",
        ":fleet_search_keys",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "lazy_postings",
    srcs = ["LazyPostings.java"],
    deps = [
        ":device_record",
        ":device_value_extractor",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "fleet_snapshot",
    srcs = ["FleetSnapshot.java"],
    deps = [
        ":device_record",
        ":fleet_index",
        ":host_record",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "fleet_index_builder",
    srcs = ["FleetIndexBuilder.java"],
    deps = [
        ":device_enrichment",
        ":device_record",
        ":fleet_index",
        ":fleet_raw_data",
        ":fleet_search_keys",
        ":fleet_snapshot",
        ":host_enrichment",
        ":host_record",
        ":key_count",
        ":value_key_pair",
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/devtools/mobileharness/api/model/proto:lab_java_proto",
        "//src/devtools/mobileharness/api/query/proto:lab_query_java_proto",
        "//src/devtools/mobileharness/fe/v6/service/proto/host:host_resources_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/host/util:host_connectivity_statuses",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/host/util:host_types",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_protobuf_protobuf_java",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_test(
    name = "FleetIndexBuilderTest",
    srcs = ["FleetIndexBuilderTest.java"],
    deps = [
        ":device_enrichment",
        ":fleet_index",
        ":fleet_index_builder",
        ":fleet_raw_data",
        ":fleet_snapshot",
        ":host_enrichment",
        ":lazy_postings",
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/devtools/mobileharness/api/model/proto:lab_java_proto",
        "//src/devtools/mobileharness/api/query/proto:lab_query_java_proto",
        "//src/javatests/com/google/devtools/mobileharness/builddefs:truth",
        "@maven//:com_google_guava_guava",
        "@maven//:junit_junit",
    ],
)
