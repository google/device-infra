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
#   Fake log collector for testing.

package(
    default_applicable_licenses = ["//:license"],
    default_testonly = 1,
    default_visibility = [
        "//java/com/google/wireless/qa/mobileharness/shared/log/testing:__subpackages__",
        "//src/javatests/com/google/wireless/qa/mobileharness/shared/log:__subpackages__",
    ],
)

java_library(
    name = "testing",
    srcs = [
        "FakeLogCollector.java",
    ],
    deps = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/log",
        "//src/java/com/google/wireless/qa/mobileharness/shared/log:log_collector_backend",
        "//src/java/com/google/wireless/qa/mobileharness/shared/log:log_context",
        "//src/java/com/google/wireless/qa/mobileharness/shared/log:log_data",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:org_mockito_mockito_core",
    ],
)
