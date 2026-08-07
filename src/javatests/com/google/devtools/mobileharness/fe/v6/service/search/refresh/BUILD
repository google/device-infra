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
load("//src/javatests/com/google/devtools/mobileharness/builddefs:junit_test_suites.bzl", "junit_test_suites")

package(
    default_applicable_licenses = ["//:license"],
)

java_library(
    name = "tests",
    testonly = 1,
    srcs = glob(["*.java"]),
    deps = [
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/devtools/mobileharness/api/query/proto:lab_query_java_proto",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:dimension_overlay",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_index",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_index_builder",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_raw_data",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_snapshot",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:host_record",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:lazy_postings",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/pull:dimension_overlay_raw",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/pull:fleet_data_source",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/refresh:dimension_overlay_store",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/refresh:fleet_data_refresher",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/refresh:fleet_snapshot_store",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:thread_pools",
        "//src/javatests/com/google/devtools/mobileharness/builddefs:truth",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@maven//:junit_junit",
    ],
)

junit_test_suites(
    name = "gen_tests",
    sizes = [
        "small",
    ],
    deps = [":tests"],
)
