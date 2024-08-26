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
#   The utilities to be used for the short term solution to support longevity
#   test in Mobile Harness.

package(
    default_applicable_licenses = ["//:license"],
)

java_library(
    name = "converter",
    srcs = glob(["*Converter.java"]),
    visibility = [
    ],
    deps = [
        "//src/devtools/common/metrics/stability/model/proto:error_java_proto",
        "//src/devtools/mobileharness/api/model/proto:job_java_proto",
        "//src/devtools/mobileharness/api/model/proto:test_java_proto",
        "//src/devtools/mobileharness/service/moss/proto:result_java_proto",
        "//src/devtools/mobileharness/service/moss/proto:slg_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/job:locator",
        "//src/java/com/google/devtools/mobileharness/api/model/job/in:dirs",
        "//src/java/com/google/devtools/mobileharness/api/model/job/in:in_factory",
        "//src/java/com/google/devtools/mobileharness/api/model/job/out",
        "//src/java/com/google/devtools/mobileharness/api/model/job/out:out_factory",
        "//src/java/com/google/devtools/mobileharness/infra/controller/scheduler/model/job/in:device_requirements",
        "//src/java/com/google/devtools/mobileharness/infra/controller/scheduler/model/job/in:in_factory",
        "//src/java/com/google/devtools/mobileharness/service/moss/util/converter:test_status_converter",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/path",
        "//src/java/com/google/wireless/qa/mobileharness/shared/log",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/allocation",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/allocation:allocation_factory",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job:job_factory",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job:job_helper",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job/in",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job/in:in_factory",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job/out:out_factory",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/lab:locator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_java_proto",
        "@maven//:com_google_guava_guava",
    ],
)
