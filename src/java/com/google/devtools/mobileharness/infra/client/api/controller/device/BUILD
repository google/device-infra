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
#   Device controllers for Mobile Harness Client API.

DEFAULT_VISIBILITY = [
    "//javatests/com/google/wireless/qa/mobileharness/client/api:__subpackages__",
    "//src/java/com/google/devtools/deviceinfra/infra/client/api/mode/local:__subpackages__",
    "//src/javatests/com/google/devtools/deviceinfra/infra/client/api/mode/local:__subpackages__",
]

package(
    default_visibility = DEFAULT_VISIBILITY,
)

java_library(
    name = "querier",
    srcs = ["DeviceQuerier.java"],
    visibility =
        DEFAULT_VISIBILITY + [
            "//src/java/com/google/devtools/atsconsole/controller/sessionplugin:__subpackages__",
            "//src/java/com/google/devtools/deviceinfra/infra/client/controller:__subpackages__",
            "//src/java/com/google/devtools/mobileharness/infra/client:__subpackages__",
            "//src/java/com/google/devtools/mobileharness/infra/client/api:__subpackages__",
            "//src/javatests/com/google/devtools/atsconsole/controller/sessionplugin:__subpackages__",
            "//src/javatests/com/google/devtools/deviceinfra/infra/client/controller:__subpackages__",
            "//src/javatests/com/google/devtools/mobileharness/infra/client/longrunningservice:__subpackages__",
        ],
    deps = [
        "//src/devtools/mobileharness/api/query/proto:device_query_java_proto",
        "//src/java/com/google/devtools/deviceinfra/shared/util/auto:auto_value",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dimension",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/lab",
        "//src/testing/mobileharness/shared/proto:device_query_java_proto",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "composite",
    srcs = [
        "CompositeDeviceQuerier.java",
    ],
    visibility =
        DEFAULT_VISIBILITY,
    deps = [
        ":querier",
        "//src/devtools/mobileharness/api/query/proto:device_query_java_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/lab",
        "//src/testing/mobileharness/shared/proto:device_query_java_proto",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)
