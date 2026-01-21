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

load("@rules_java//java:defs.bzl", "java_library")

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/platform/testbed/mobly/util:__subpackages__",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/controller/test:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/platform/android/xts:__subpackages__",
        "//src/javatests/com/google/wireless/qa/mobileharness/shared/api/driver:__subpackages__",
    ],
)

java_library(
    name = "non_tradefed_report_generator_constants",
    srcs = ["NonTradefedReportGeneratorConstants.java"],
    deps = ["//src/java/com/google/wireless/qa/mobileharness/shared/api/annotation:basic_annotation"],
)

java_library(
    name = "xts_constants",
    srcs = ["XtsConstants.java"],
)

java_library(
    name = "xts_property_name",
    srcs = ["XtsPropertyName.java"],
    deps = ["//src/java/com/google/wireless/qa/mobileharness/shared/constant:property"],
)
