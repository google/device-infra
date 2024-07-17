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
    name = "test_status",
    srcs = ["TestStatus.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/report:__pkg__",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite:__subpackages__",
    ],
    deps = [
        "//src/devtools/mobileharness/infra/ats/console/result/proto:result_java_proto",
    ],
)

java_library(
    name = "device_build_info",
    srcs = ["DeviceBuildInfo.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/report:__pkg__",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/plugin:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/platform/android/xts/plugin:__pkg__",
    ],
    deps = ["//src/java/com/google/devtools/mobileharness/infra/ats/console/result/xml:xml_constants"],
)
