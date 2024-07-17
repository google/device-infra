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
    name = "test_suite_helper",
    srcs = ["TestSuiteHelper.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/common:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/sessionplugin:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/common:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/platform/android/xts/suite:__pkg__",
    ],
    deps = [
        ":abi",
        ":suite_module_loader",
        ":test_suite_info",
        "//src/devtools/mobileharness/platform/android/xts/proto:configuration_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/common/util:abi_formatter",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/common/util:abi_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/common/util:xts_dir_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/params:module_parameters",
        "//src/java/com/google/devtools/mobileharness/shared/constant:log_record_importance",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "suite_module_loader",
    srcs = ["SuiteModuleLoader.java"],
    deps = [
        ":abi",
        ":device_foldable_state",
        "//src/devtools/mobileharness/platform/android/xts/proto:configuration_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/common/util:abi_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/config:configuration_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/config:configuration_xml_parser",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/params:foldable_expanding_handler",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/params:module_parameter_handler_interface",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/params:module_parameters",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/params:module_parameters_helper",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/params:negative_handler",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/params:not_multi_abi_handler",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "abi",
    srcs = ["Abi.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/sessionplugin:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/platform/android/xts/suite:__pkg__",
    ],
    deps = ["//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value"],
)

java_library(
    name = "test_suite_info",
    srcs = ["TestSuiteInfo.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/report:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/version:__pkg__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/common/plan:jar_file_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/common/util:xts_dir_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "device_foldable_state",
    srcs = ["DeviceFoldableState.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite:__subpackages__",
    ],
)

java_library(
    name = "suite_common",
    srcs = ["SuiteCommon.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/common:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/report:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/ats/server/sessionplugin:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/console/result/report:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/platform/android/xts/plugin:__subpackages__",
    ],
)

java_library(
    name = "suite_test_filter",
    srcs = ["SuiteTestFilter.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/common:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/retry:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/subplan:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/platform/android/xts/suite:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/platform/android/xts/suite/retry:__subpackages__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/common/util:abi_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "suite_result_reporter",
    srcs = ["SuiteResultReporter.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/sessionplugin:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/console/controller/sessionplugin:__pkg__",
    ],
    deps = [
        "//src/devtools/mobileharness/infra/ats/console/result/proto:report_java_proto",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/xml:xml_constants",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/common:test_status",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:time_utils",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)
