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

package(default_applicable_licenses = ["//:license"])

java_library(
    name = "sub_plan",
    srcs = ["SubPlan.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/common:__pkg__",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/retry:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/common:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/platform/android/xts/suite/retry:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/platform/android/xts/suite/subplan:__pkg__",
    ],
    runtime_deps = [
        "@maven//:net_sf_kxml_kxml2",
    ],
    deps = [
        ":abstract_xml_parser",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite:suite_test_filter",
        "@maven//:com_google_guava_guava",
        "@maven//:xmlpull_xmlpull",
    ],
)

java_library(
    name = "abstract_xml_parser",
    srcs = ["AbstractXmlParser.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
    ],
)
