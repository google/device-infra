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
    name = "compatibility_report_parser",
    srcs = ["CompatibilityReportParser.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/result:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/console/result/report:__pkg__",
    ],
    deps = [
        ":context",
        "//src/devtools/mobileharness/infra/ats/console/result/proto:report_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/xml:xml_constants",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "context",
    srcs = ["Context.java"],
    deps = ["//src/devtools/mobileharness/infra/ats/console/result/proto:report_java_proto"],
)

java_library(
    name = "compatibility_report_merger",
    srcs = ["CompatibilityReportMerger.java"],
    visibility = [
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/console/result/report:__pkg__",
    ],
    deps = [
        ":compatibility_report_parser",
        ":mobly_report_parser",
        "//src/devtools/mobileharness/infra/ats/console/result/proto:report_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/xml:xml_constants",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "compatibility_report_module",
    srcs = ["CompatibilityReportModule.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/console/command:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/console/result/report:__pkg__",
    ],
    deps = ["@maven//:com_google_inject_guice"],
)

java_library(
    name = "compatibility_report_creator",
    srcs = ["CompatibilityReportCreator.java"],
    resources = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/report/res:compatibility_res",
    ],
    visibility = [
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/console/result/report:__pkg__",
    ],
    runtime_deps = [
        "@maven//:net_sf_kxml_kxml2",
    ],
    deps = [
        ":zip_util",
        "//src/devtools/mobileharness/infra/ats/console/result/proto:report_java_proto",
        "//src/devtools/mobileharness/infra/ats/console/tradefed/proto:record_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/checksum:compatibility_report_checksum_helper",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/xml:xml_constants",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/util/tradefed:test_record_writer",
        "//src/java/com/google/devtools/mobileharness/shared/util/error:more_throwables",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
        "@maven//:xmlpull_xmlpull",
    ],
)

java_library(
    name = "zip_util",
    srcs = ["ZipUtil.java"],
)

java_library(
    name = "mobly_report_parser",
    srcs = ["MoblyReportParser.java"],
    visibility = [
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/console/result/report:__pkg__",
    ],
    deps = [
        "//src/devtools/mobileharness/infra/ats/console/result/proto:report_java_proto",
        "//src/devtools/mobileharness/infra/ats/console/result/proto:result_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/mobly:mobly_doc_entry",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/mobly:parser",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/shared/util/error:more_throwables",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@com_google_protobuf//:protobuf_java",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "mobly_report_helper",
    srcs = ["MoblyReportHelper.java"],
    visibility = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/console/result/report:__pkg__",
        "//src/javatests/com/google/wireless/qa/mobileharness/shared/api/driver:__pkg__",
    ],
    deps = [
        ":certification_suite_info",
        "//src/devtools/mobileharness/infra/ats/console/result/proto:report_java_proto",
        "//src/java/com/google/devtools/deviceinfra/platform/android/lightning/internal/sdk/adb",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/xml:xml_constants",
        "//src/java/com/google/devtools/mobileharness/shared/util/error:more_throwables",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@com_google_protobuf//:protobuf_java",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "certification_suite_info",
    srcs = ["CertificationSuiteInfo.java"],
    visibility = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver:__pkg__",
        "//src/javatests/com/google/wireless/qa/mobileharness/shared/api/driver:__pkg__",
    ],
    deps = ["//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value"],
)

java_library(
    name = "certification_suite_info_factory",
    srcs = ["CertificationSuiteInfoFactory.java"],
    visibility = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver:__pkg__",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/console/result/report:__pkg__",
        "//src/javatests/com/google/wireless/qa/mobileharness/shared/api/driver:__pkg__",
    ],
    deps = [":certification_suite_info"],
)
