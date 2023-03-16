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

exports_files(
    [
        "AdapterValidator.java",
        "BaseValidator.java",
        "JobValidator.java",
        "Validator.java",
        "ValidatorFactory.java",
    ],
    visibility = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/api:__pkg__",
    ],
)

java_library(
    name = "xts_tradefed_test_validator",
    srcs = [
        "XtsTradefedTestValidator.java",
    ],
    visibility = [
        "//src/javatests/com/google/wireless/qa/mobileharness/shared/api/validator:__pkg__",
    ],
    deps = [
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api:base",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver:xts_tradefed_test",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job/in/spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto/spec:xts_tradefed_test_spec_java_proto",
        "@maven//:com_google_guava_guava",
    ],
)
