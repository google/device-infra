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

# Shared util classes for ats components.

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/retry:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats:__subpackages__",
    ],
)

java_library(
    name = "device_details",
    srcs = ["DeviceDetails.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "device_details_retriever",
    srcs = ["DeviceDetailsRetriever.java"],
    deps = [
        ":device_details",
        ":session_request_info",
        "//src/devtools/mobileharness/api/query/proto:device_query_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/controller/device:querier",
        "//src/java/com/google/devtools/mobileharness/platform/android/sdktool/adb:adb_internal_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/sdktool/adb:adb_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/sdktool/adb:enums",
        "//src/java/com/google/devtools/mobileharness/platform/android/systemsetting:system_setting_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/error:more_throwables",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "device_infra_service_util",
    srcs = ["DeviceInfraServiceUtil.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/shell",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "device_selection",
    srcs = ["DeviceSelection.java"],
    deps = [
        ":device_details",
        ":device_selection_options",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "device_selection_options",
    srcs = ["DeviceSelectionOptions.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "session_handler_helper",
    srcs = ["SessionHandlerHelper.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/common/util:xts_dir_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/subplan:sub_plan",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "session_request_handler_util",
    srcs = ["SessionRequestHandlerUtil.java"],
    resources = [
        "//src/devtools/mobileharness/infra/controller/test/util/xtsdownloader/configs:cts_list",
    ],
    deps = [
        ":device_details",
        ":device_details_retriever",
        ":device_selection",
        ":device_selection_options",
        ":session_handler_helper",
        ":session_request_info",
        ":xts_property_name",
        "//src/devtools/mobileharness/api/query/proto:device_query_java_proto",
        "//src/devtools/mobileharness/platform/android/xts/proto:configuration_java_proto",
        "//src/devtools/mobileharness/platform/android/xts/proto:device_configuration_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/error:exception_factory",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common/plan:test_plan_parser",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/report:certification_suite_info_factory",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/controller/device:querier",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice:annotations",
        "//src/java/com/google/devtools/mobileharness/platform/android/sdktool/adb:adb_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/sdktool/adb:enums",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/common/util:abi_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/common/util:xts_dir_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/config:configuration_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/config:module_configuration_helper",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite:suite_common",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite:suite_test_filter",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite:test_suite_helper",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/retry:previous_result_loader",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/retry:retry_args",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/retry:retry_generator",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/retry:retry_type",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/subplan:sub_plan",
        "//src/java/com/google/devtools/mobileharness/shared/constant:log_record_importance",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local:res_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/jobconfig:job_info_creator",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/path",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_config_java_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_java_proto",
        "@com_google_protobuf//:protobuf_java",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_code_gson_gson",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "session_request_info",
    srcs = ["SessionRequestInfo.java"],
    deps = [
        "//src/devtools/mobileharness/platform/android/xts/proto:configuration_java_proto",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/retry:retry_type",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "session_result_handler_util",
    srcs = ["SessionResultHandlerUtil.java"],
    deps = [
        ":session_handler_helper",
        ":session_request_info",
        ":xts_property_name",
        "//src/devtools/mobileharness/api/model/proto:test_java_proto",
        "//src/devtools/mobileharness/infra/ats/console/result/proto:report_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/report:compatibility_report_creator",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/report:compatibility_report_merger",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/report:mobly_report_parser",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/xml:xml_constants",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/constant:session_properties",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/model:session_info",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/common/util:xts_constants",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/common/util:xts_dir_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite:suite_common",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/retry:previous_result_loader",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/retry:retry_report_merger",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/suite/subplan:sub_plan",
        "//src/java/com/google/devtools/mobileharness/shared/constant:log_record_importance",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:callables",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:mobile_harness_callable",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/path",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "xts_property_name",
    srcs = ["XtsPropertyName.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/plugin:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/console/controller/sessionplugin:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/platform/android/xts/plugin:__pkg__",
    ],
    deps = ["//src/java/com/google/wireless/qa/mobileharness/shared/constant:property"],
)

java_library(
    name = "xts_type_loader",
    srcs = ["XtsTypeLoader.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)
