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

load("@rules_java//java:defs.bzl", "java_library")

package(default_applicable_licenses = ["//:license"])

java_library(
    name = "class_constants",
    srcs = ["ClassConstants.java"],
    visibility = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/api:__pkg__",
    ],
)

java_library(
    name = "client_class_util",
    srcs = ["ClientClassUtil.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/client/api/util/lister:__pkg__",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator:__pkg__",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator/util:__pkg__",
    ],
    deps = [
        ":class_constants",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/lister",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator/job:job_validator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/util:reflection",
        "@maven//:com_google_code_findbugs_jsr305",
    ],
)

java_library(
    name = "reflection_util",
    srcs = ["ReflectionUtil.java"],
    visibility = ["//:deviceinfra_all_pkg"],
    deps = ["//src/java/com/google/devtools/mobileharness/api/model/error"],
)
