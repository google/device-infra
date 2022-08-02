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

java_library(
    name = "error",
    srcs = glob(
        ["*.java"],
    ),
    tags = [
        "alt_dep=//java/com/google/devtools/mobileharness/api/model/error",
        "avoid_dep",
    ],
    visibility = [
        "//:deviceinfra_api_users",
        "//java/com/google/devtools/mobileharness/api/model/error:error_user",
    ],
    deps = [
        "//devtools/common/metrics/stability/model/proto:error_java_proto",
        "//java/com/google/devtools/common/metrics/stability/model:error_id",
        "//java/com/google/devtools/common/metrics/stability/util:error_id_formatter",
        "//java/com/google/wireless/qa/mobileharness/shared/constant:error_code",
        "//src/java/com/google/devtools/deviceinfra/api/error:exception",
        "//src/java/com/google/devtools/deviceinfra/api/error/id:error_id_interface",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)
