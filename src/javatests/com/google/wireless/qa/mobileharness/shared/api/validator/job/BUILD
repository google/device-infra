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
load("//src/javatests/com/google/devtools/mobileharness/builddefs:junit_test_suites.bzl", "junit_test_suites")

package(
    default_applicable_licenses = ["//:license"],
    default_testonly = 1,
)

java_library(
    name = "job",
    srcs = glob(["*.java"]),
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/spec:mobly_aosp_package_test_spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator/job:android_robo_test_job_validator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator/job:mobly_aosp_package_test_job_validator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator/job:xts_tradefed_test_job_validator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_java_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto/spec:android_robo_test_spec_java_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto/spec:xts_tradefed_test_spec_java_proto",
        "//src/javatests/com/google/devtools/mobileharness/builddefs:truth",
        "@maven//:junit_junit",
        "@maven//:org_mockito_mockito_core",
    ],
)

junit_test_suites(
    name = "gen_tests",
    sizes = [
        "small",
    ],
    deps = [":job"],
)
