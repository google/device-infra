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
    name = "previous_result_loader",
    srcs = ["PreviousResultLoader.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/subplan:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/platform/android/xts/suite:__subpackages__",
    ],
    deps = [
        "//src/devtools/mobileharness/infra/ats/console/result/proto:report_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/report:test_result_proto_util",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/result:result_lister_helper",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite:suite_common",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "retry_generator",
    srcs = ["RetryGenerator.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/common:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/common:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/platform/android/xts/suite/retry:__pkg__",
    ],
    deps = [
        ":previous_result_loader",
        ":retry_args",
        ":retry_type",
        "//src/devtools/mobileharness/infra/ats/console/result/proto:report_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite:suite_test_filter",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/subplan:sub_plan",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/subplan:sub_plan_helper",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "retry_type",
    srcs = ["RetryType.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/common:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/command:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/sessionplugin:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/console/controller/sessionplugin:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/platform/android/xts/suite/retry:__pkg__",
    ],
)

java_library(
    name = "retry_args",
    srcs = ["RetryArgs.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/common:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/common:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/platform/android/xts/suite/retry:__pkg__",
    ],
    deps = [
        ":retry_type",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite:suite_test_filter",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "retry_result_helper",
    srcs = ["RetryResultHelper.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/subplan:__pkg__",
    ],
    deps = [
        "//src/devtools/mobileharness/infra/ats/console/result/proto:report_java_proto",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/common:test_status",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite:suite_test_filter",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "retry_report_merger",
    srcs = ["RetryReportMerger.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/common:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/common:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/platform/android/xts/suite/retry:__pkg__",
    ],
    deps = [
        ":previous_result_loader",
        ":retry_args",
        ":retry_generator",
        ":retry_type",
        "//src/devtools/mobileharness/infra/ats/console/result/proto:report_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/xml:xml_constants",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/common:test_status",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/common/util:abi_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/subplan:sub_plan",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)
