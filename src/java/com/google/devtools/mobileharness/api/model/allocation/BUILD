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
#   Data models for the device allocations for tests.

DEFAULT_VISIBILITY = [
    "//src/java/com/google/devtools/deviceinfra/infra:__subpackages__",
    "//src/java/com/google/devtools/mobileharness/api/testrunner/event/test:__subpackages__",
    "//src/java/com/google/devtools/mobileharness/infra/client/api/controller/allocation/allocator:__pkg__",
    "//src/java/com/google/devtools/mobileharness/infra/controller/device:__subpackages__",
    "//src/java/com/google/devtools/mobileharness/infra/controller/scheduler:__subpackages__",
    "//src/java/com/google/devtools/mobileharness/infra/controller/test:__subpackages__",
    "//src/java/com/google/devtools/mobileharness/infra/lab/rpc:__subpackages__",
    "//src/java/com/google/wireless/qa/mobileharness/shared/controller:__subpackages__",
    "//src/java/com/google/wireless/qa/mobileharness/shared/model/allocation:__subpackages__",
    "//src/javatests/com/google/devtools/deviceinfra/infra:__subpackages__",
    "//src/javatests/com/google/devtools/mobileharness:__subpackages__",
]

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = DEFAULT_VISIBILITY,
)

java_library(
    name = "allocation",
    srcs = glob(["*.java"]),
    visibility = DEFAULT_VISIBILITY + [
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/job:locator",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:locator",
        "@maven//:com_google_guava_guava",
    ],
)
