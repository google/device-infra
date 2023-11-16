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

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//:deviceinfra_all_pkg",
    ],
)

################################################################################
# Common libraries in alphabetical order
################################################################################

java_library(
    name = "adapter_validator",
    srcs = ["AdapterValidator.java"],
    deps = [
        ":base_validator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
    ],
)

java_library(
    name = "base_validator",
    srcs = ["BaseValidator.java"],
    deps = [
        ":validator",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "job_validator",
    srcs = ["JobValidator.java"],
    deps = [
        ":validator",
        ":validator_factory",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api:class_util",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/annotation",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:error_code",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_java_proto",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "validator",
    srcs = ["Validator.java"],
    deps = [
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
    ],
)

java_library(
    name = "validator_factory",
    srcs = ["ValidatorFactory.java"],
    deps = [
        ":validator",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:error_code",
        "@maven//:com_google_guava_guava",
    ],
)

################################################################################
# Validator libraries in alphabetical order
################################################################################

java_library(
    name = "android_adb_shell_decorator_validator",
    srcs = ["AndroidAdbShellDecoratorValidator.java"],
    deps = [
        ":base_validator",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dimension",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job/in/spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto/spec:android_adb_shell_decorator_spec_java_proto",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "android_clean_apps_decorator_validator",
    srcs = ["AndroidCleanAppsDecoratorValidator.java"],
    deps = [
        ":base_validator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/spec:android_clean_apps_spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
    ],
)

java_library(
    name = "android_device_settings_decorator_validator",
    srcs = ["AndroidDeviceSettingsDecoratorValidator.java"],
    deps = [
        ":base_validator",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/platform/android/sdktool/adb:enums",
        "//src/java/com/google/devtools/mobileharness/platform/android/systemsetting:system_setting_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:android_device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:error_code",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job/in/spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto/spec:android_device_setting_decorator_spec_java_proto",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "android_switch_user_decorator_validator",
    srcs = ["AndroidSwitchUserDecoratorValidator.java"],
    deps = [
        ":base_validator",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/platform/android/user:android_user_util",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:android_switch_user_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:android_device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job/in/spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto/spec:android_switch_user_decorator_spec_java_proto",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "mobly_aosp_package_test_validator",
    srcs = ["MoblyAospPackageTestValidator.java"],
    deps = [
        ":base_validator",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/report:certification_suite_info_factory",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver:mobly_aosp_package_test",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "xts_tradefed_test_validator",
    srcs = ["XtsTradefedTestValidator.java"],
    deps = [
        ":base_validator",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver:xts_tradefed_test",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job/in/spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto/spec:xts_tradefed_test_spec_java_proto",
        "@maven//:com_google_guava_guava",
    ],
)
