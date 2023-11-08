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
#   Job/Test data models for Mobile Harness scheduler.

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//:deviceinfra_pkg",
    ],
)

java_library(
    name = "job",
    srcs = glob(["*ScheduleUnit.java"]),
    deps = [
        "//src/devtools/mobileharness/api/model/proto:job_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/job:locator",
        "//src/java/com/google/devtools/mobileharness/api/model/job/in",
        "//src/java/com/google/devtools/mobileharness/api/model/job/out:timing",
        "//src/java/com/google/devtools/mobileharness/infra/controller/scheduler/model/job/in:device_requirements",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/util:job_timer",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:count_down_timer",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)
