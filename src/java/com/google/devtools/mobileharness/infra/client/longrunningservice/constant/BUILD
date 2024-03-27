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

package(
    default_applicable_licenses = ["//:license"],
)

java_library(
    name = "dirs",
    srcs = ["OlcServerDirs.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/log:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/ats/local:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice:__pkg__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/path",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dir",
    ],
)

java_library(
    name = "session_properties",
    srcs = ["SessionProperties.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/client/longrunningservice:__subpackages__",
    ],
)
