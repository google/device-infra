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
    default_visibility = [
        "//src/devtools/mobileharness/infra/ats/console/controller/proto:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/ats:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite:__subpackages__",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats:__subpackages__",
        "//src/javatests/com/google/wireless/qa/mobileharness/shared/api:__subpackages__",
    ],
)

proto_library(
    name = "xts_common_proto",
    srcs = ["xts_common.proto"],
)

java_proto_library(
    name = "xts_common_java_proto",
    deps = [":xts_common_proto"],
)
