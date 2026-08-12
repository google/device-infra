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
    name = "fleet_filter_engine",
    srcs = ["FleetFilterEngine.java"],
    deps = [
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:device_record",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_index",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_search_keys",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_snapshot",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
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
