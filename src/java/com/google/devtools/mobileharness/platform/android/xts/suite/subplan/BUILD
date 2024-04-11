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
    name = "sub_plan",
    srcs = ["SubPlan.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/common:__pkg__",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/retry:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/common:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/platform/android/xts/suite/retry:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/platform/android/xts/suite/subplan:__pkg__",
    ],
    runtime_deps = [
        "@maven//:net_sf_kxml_kxml2",
    ],
    deps = [
        ":abstract_xml_parser",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite:suite_test_filter",
        "@maven//:com_google_guava_guava",
        "@maven//:xmlpull_xmlpull",
    ],
)

java_library(
    name = "abstract_xml_parser",
    srcs = ["AbstractXmlParser.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
    ],
)

java_library(
    name = "result_type",
    srcs = ["ResultType.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/command:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/platform/android/xts/suite/subplan:__pkg__",
    ],
)

java_library(
    name = "add_sub_plan_args",
    srcs = ["AddSubPlanArgs.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/command:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/platform/android/xts/suite/subplan:__pkg__",
    ],
    deps = [
        ":result_type",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite:suite_test_filter",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "sub_plan_helper",
    srcs = ["SubPlanHelper.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/retry:__pkg__",
    ],
    deps = [
        ":sub_plan",
        "//src/devtools/mobileharness/infra/ats/console/result/proto:report_java_proto",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/xml:xml_constants",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite:suite_test_filter",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/retry:retry_result_helper",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "sub_plan_creator",
    srcs = ["SubPlanCreator.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/command:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/platform/android/xts/suite/subplan:__pkg__",
    ],
    deps = [
        ":add_sub_plan_args",
        ":result_type",
        ":sub_plan",
        ":sub_plan_helper",
        "//src/devtools/mobileharness/infra/ats/console/result/proto:report_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/xml:xml_constants",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/common/util:xts_dir_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite:suite_test_filter",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/retry:previous_result_loader",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)
