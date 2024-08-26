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
#   Data models for the allocations of the devices for the tests.

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = ["//visibility:public"],
)

java_library(
    name = "allocation",
    srcs = ["Allocation.java"],
    deps = [
        "//src/devtools/mobileharness/service/moss/proto:slg_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/allocation",
        "//src/java/com/google/devtools/mobileharness/api/model/job:locator",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:locator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job:locator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/lab:locator",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "allocation_factory",
    srcs = ["AllocationInternalFactory.java"],
    visibility = [
        # Note: this tool is only for MH infra, please don't add more visibilities.
        "//src/java/com/google/devtools/mobileharness/service/moss/util/slg:__pkg__",
    ],
    deps = [
        ":allocation",
        "//src/devtools/mobileharness/service/moss/proto:slg_java_proto",
    ],
)
