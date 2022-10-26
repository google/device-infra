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
#   Shared libs for locating remote/local mobile devices.

package(
    default_visibility = [
        "//src/java/com/google/devtools/deviceinfra/infra/client/api/mode/local:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:__subpackages__",
        "//src/javatests/com/google/devtools/deviceinfra/infra/client/api/mode/local:__subpackages__",
    ],
)

java_library(
    name = "device_info_manager",
    srcs = ["DeviceInfoManager.java"],
    visibility = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/api:__pkg__",
    ],
    deps = [
        "//src/java/com/google/devtools/deviceinfra/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:device_id_descriptor",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:device_info",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:device_info_factory",
        "//src/java/com/google/devtools/mobileharness/infra/shared/api:config",
        "//src/java/com/google/devtools/mobileharness/shared/util/system",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)
