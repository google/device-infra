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
    ],
    deps = [
        ":abi",
        ":suite_module_loader",
        ":test_suite_info",
        "//src/devtools/mobileharness/infra/ats/common/proto:xts_common_java_proto",
        "//src/devtools/mobileharness/platform/android/xts/proto:configuration_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/common/util:abi_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/params:module_parameters",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
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
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "abi",
    srcs = ["Abi.java"],
    deps = ["//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value"],
)

java_library(
    name = "test_suite_info",
    srcs = ["TestSuiteInfo.java"],
    deps = [
        "//src/devtools/mobileharness/infra/ats/common/proto:xts_common_java_proto",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/plan:jar_file_util",
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
