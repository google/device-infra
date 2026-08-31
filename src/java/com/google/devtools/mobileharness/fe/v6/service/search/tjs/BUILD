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
    name = "tjs_search_logic",
    srcs = ["TjsSearchLogic.java"],
    deps = [
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_tjs_java_proto",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "noop_tjs_search_logic",
    srcs = ["NoOpTjsSearchLogic.java"],
    deps = [
        ":tjs_search_logic",
        "//src/devtools/mobileharness/fe/v6/service/proto/search:search_tjs_java_proto",
        "//src/java/com/google/devtools/mobileharness/fe/v6/service/errors",
        "@maven//:com_google_guava_guava",
    ],
)
