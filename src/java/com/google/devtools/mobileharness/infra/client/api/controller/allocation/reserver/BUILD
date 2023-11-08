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
#   Device reserver interfaces or utility classes, for reserving Mobile
#   Harness local/lab devices for executing Mobile Harness tests.

DEFAULT_VISIBILITY = [
    "//java/com/google/devtools/mobileharness/infra/client:__subpackages__",
    "//java/com/google/wireless/qa/mobileharness/client/api:__subpackages__",
    "//src/java/com/google/devtools/mobileharness/infra/client/api:__subpackages__",
    "//javatests/com/google/devtools/mobileharness/infra/client/api:__subpackages__",
    "//javatests/com/google/devtools/mobileharness/platform/fitbit:__subpackages__",
    "//javatests/com/google/wireless/qa/mobileharness/client/api:__subpackages__",
    "//java/com/google/devtools/mobileharness/platform/fitbit/plugins:__subpackages__",
]

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = DEFAULT_VISIBILITY,
)

java_library(
    name = "reserver",
    srcs = [
        "DeviceReserver.java",
    ],
    visibility = DEFAULT_VISIBILITY + [
        "//java/com/google/devtools/mobileharness/service/moscar:__subpackages__",
        "//javatests/com/google/devtools/mobileharness/service/moscar:__subpackages__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/lab:locator",
    ],
)
