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

# Description:
#   Utilities to Mobile Harness compactor.

package(default_applicable_licenses = ["//:license"])

java_library(
    name = "comparator",
    srcs = [
        "ErrorTypeComparator.java",
        "TestResultComparator.java",
    ],
    visibility = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/controller/event/util:__subpackages__",
    ],
    deps = [
        "//src/devtools/common/metrics/stability/model/proto:error_java_proto",
        "//src/devtools/mobileharness/api/model/proto:test_java_proto",
    ],
)

java_library(
    name = "natural_sort_order_comparator",
    srcs = ["NaturalSortOrderComparator.java"],
    visibility = ["//:deviceinfra_all_pkg"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)
