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
#   MobileHarness client entry point binaries.

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = ["//visibility:private"],
)

java_library(
    name = "job_initializer",
    srcs = ["JobInitializer.java"],
    visibility = [
        "//java/com/google/wireless/qa/mobileharness/client/bin:__pkg__",
        "//javatests/com/google/wireless/qa/mobileharness/client/bin:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/client/rbe:__subpackages__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/file/resolver/job:job_file_resolver_constants",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/jobconfig:job_info_creator",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/system",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:exit_code",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:property",
        "//src/java/com/google/wireless/qa/mobileharness/shared/jobconfig",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_config_java_proto",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)
