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
    name = "suggestion_intent_parser",
    srcs = ["SuggestionIntentParser.java"],
    deps = ["@maven//:com_google_guava_guava"],
)

# The search-bar suggestion pipeline: intent parsing, per-shape candidate generation, wording and
# actions, and ranking. Entity knowledge enters only through the corpus's KeyVocabulary.
java_library(
    name = "fleet_suggester",
    srcs = [
        "FleetSuggester.java",
        "SuggestionActions.java",
        "SuggestionCandidate.java",
        "SuggestionContext.java",
        "SuggestionRanker.java",
        "SuggestionText.java",
    ],
    deps = [
        ":suggestion_intent_parser",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_common_java_proto",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_fleet_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:fleet_index",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:key_count",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:postings",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/index:value_key_pair",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/query:fleet_filter_engine",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/query:key_vocabulary",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/query:search_corpus",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/search/schema:key_descriptor",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)
