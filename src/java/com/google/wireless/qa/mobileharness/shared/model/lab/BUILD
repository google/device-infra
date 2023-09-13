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
#   Shared data models for Mobile Harness labs and devices.

package(default_applicable_licenses = ["//:license"])

java_library(
    name = "locator",
    srcs = glob(["*Locator.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:lab_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:locator",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "lab",
    srcs = glob(["*.java"]),
    visibility = ["//:deviceinfra_all_pkg"],
    exports = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/lab/in",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/lab/out",
    ],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/devtools/mobileharness/api/model/proto:lab_java_proto",
        "//src/devtools/mobileharness/api/query/proto:device_query_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:locator",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dimension",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:error_code",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/lab/in",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/lab/out",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:common_java_proto",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)
