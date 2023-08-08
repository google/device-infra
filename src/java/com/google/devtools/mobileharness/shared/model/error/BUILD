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
    name = "rpc_exception",
    srcs = ["MobileHarnessRpcException.java"],
    visibility = [
        "//:deviceinfra_all_pkg",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "@maven//:com_google_code_findbugs_jsr305",
    ],
)

java_library(
    name = "unknown_error",
    srcs = ["UnknownErrorId.java"],
    visibility = [
        # This lib is for MH Infra internal user only. DO NOT add non-MH code path here.
        "//src/java/com/google/devtools/mobileharness/shared/util/error:__pkg__",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job:__subpackages__",
    ],
    deps = [
        "//src/devtools/common/metrics/stability/model/proto:error_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/util:error_id_formatter",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:error_code",
        "@maven//:com_google_guava_guava",
    ],
)
