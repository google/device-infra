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
#   Utils for Android Instrumentation Test.

load("@rules_java//java:defs.bzl", "java_library")

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = ["//:deviceinfra_all_pkg"],
)

java_library(
    name = "android_instrumentation_util",
    srcs = [
        "AndroidInstrumentationSetting.java",
        "AndroidInstrumentationUtil.java",
        "AndroidTestContent.java",
    ],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:test_java_proto",
        "//src/devtools/mobileharness/platform/android/testing/proto:test_args_pb_java_proto",
        "//src/java/com/google/devtools/deviceinfra/platform/android/lightning/internal/sdk/adb",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/job/out",
        "//src/java/com/google/devtools/mobileharness/platform/android/file:android_file_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/lightning/apkfile:apk_analyzer",
        "//src/java/com/google/devtools/mobileharness/platform/android/lightning/apkinstaller",
        "//src/java/com/google/devtools/mobileharness/platform/android/lightning/systemstate",
        "//src/java/com/google/devtools/mobileharness/platform/android/sdktool/adb:enums",
        "//src/java/com/google/devtools/mobileharness/platform/android/shared/constant:splitters",
        "//src/java/com/google/devtools/mobileharness/platform/android/systemsetting:system_setting_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/user:android_user_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/shared/util/base",
        "//src/java/com/google/devtools/mobileharness/shared/util/command",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent/retry",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local:res_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/path",
        "//src/java/com/google/wireless/qa/mobileharness/shared/android:aapt",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/spec:android_instrumentation_driver_spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/spec:entry_delimiter_spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/spec:split_method_spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dir",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:property",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job/out",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto/spec:android_instrumentation_spec_java_proto",
        "@maven//:androidx_test_services_storage",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)
