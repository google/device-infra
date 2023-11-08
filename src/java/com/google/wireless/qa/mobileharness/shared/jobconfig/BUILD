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
#   MobileHarness JobConfig Utilities.

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//java/com/google/devtools/mobileharness:__subpackages__",
        "//java/com/google/wireless/qa/mobileharness:__subpackages__",
        "//javatests/com/google/devtools/mobileharness:__subpackages__",
        "//javatests/com/google/wireless/qa/mobileharness:__subpackages__",
        "//src/java/com/google/wireless/qa/mobileharness:__subpackages__",
    ],
)

java_library(
    name = "jobconfig",
    srcs = [
        "JobConfigBuilder.java",
        "JobConfigsBuilder.java",
    ],
    deps = [
        ":job_config_gson_holder",
        "//src/java/com/google/devtools/mobileharness/shared/util/base",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:error_code",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_config_java_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_java_proto",
        "@com_google_protobuf//:protobuf_java",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_code_gson_gson",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_http_client_google_http_client",
    ],
)

java_library(
    name = "job_config_gson_holder",
    srcs = ["JobConfigGsonHolder.java"],
    deps = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job/in/json:v2_api",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_config_java_proto",
        "@com_google_protobuf//:protobuf_java",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_code_gson_gson",
    ],
)
