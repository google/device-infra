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
    default_visibility = ["//:deviceinfra_all_pkg"],
)

java_library(
    name = "count_down_timer",
    srcs = ["CountDownTimer.java"],
    visibility = [
        "//:deviceinfra_all_pkg",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
    ],
)

java_library(
    name = "sleeper",
    srcs = [
        "Sleeper.java",
        "Sleepers.java",
    ],
    deps = [
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "time_utils",
    srcs = ["TimeUtils.java"],
    deps = [
        "@com_google_protobuf//:protobuf_java_util",
        "@com_google_protobuf_protobuf_java",
        "@maven//:com_google_guava_guava",
    ],
)
