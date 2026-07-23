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

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//java/com/google/devtools/mobileharness/service/devicerunclh/shared/protoconverter/v1:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/plugin:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/client/api/controller/job/retry/strategy:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/client/api/plugin:__pkg__",
    ],
)

java_library(
    name = "retry_strategy",
    srcs = [
        "RetryStrategy.java",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
    ],
)

java_library(
    name = "flaky_test_retry_constants",
    srcs = [
        "FlakyTestRetryConstants.java",
    ],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/client/api/controller/job/retry/processor:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/plugin:__pkg__",
        "//src/java/com/google/devtools/mobileharness/shared/util/testresult:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/client/api/controller/job/retry/processor:__pkg__",
    ],
)

java_library(
    name = "flaky_test_retry_strategy",
    srcs = [
        "FlakyTestRetryStrategy.java",
    ],
    deps = [
        ":flaky_test_retry_constants",
        ":retry_strategy",
        "//src/devtools/mobileharness/api/model/proto:test_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/controller/job/retry/processor:test_retry_processor",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "retry",
    srcs = [
        "DefaultRetryStrategy.java",
    ],
    exports = [
        ":flaky_test_retry_strategy",
        ":retry_strategy",
    ],
    deps = [
        ":retry_strategy",
        "//src/devtools/common/metrics/stability/model/proto:error_java_proto",
        "//src/devtools/mobileharness/api/model/proto:job_java_proto",
        "//src/devtools/mobileharness/api/model/proto:test_java_proto",
        "//src/devtools/mobileharness/infra/container/proto:mode_setting_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/converter",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:property",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "@maven//:com_google_guava_guava",
    ],
)
