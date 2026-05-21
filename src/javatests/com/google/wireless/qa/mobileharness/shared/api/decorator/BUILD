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
load("//src/javatests/com/google/devtools/mobileharness/builddefs:junit_test_suites.bzl", "junit_test_suites")

package(
    default_applicable_licenses = ["//:license"],
    default_testonly = 1,
)

java_library(
    name = "tests",
    srcs = glob(["*.java"]),
    deps = [
        "//src/devtools/mobileharness/api/model/proto:test_java_proto",
        "//src/devtools/mobileharness/platform/android/logcat/proto:logcat_monitoring_report_java_proto",
        "//src/java/com/google/devtools/deviceinfra/platform/android/lightning/internal/sdk/adb",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/job/out",
        "//src/java/com/google/devtools/mobileharness/api/testrunner/device/cache:device_cache",
        "//src/java/com/google/devtools/mobileharness/platform/android/dropbox",
        "//src/java/com/google/devtools/mobileharness/platform/android/file:android_file_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/lightning/apkinstaller",
        "//src/java/com/google/devtools/mobileharness/platform/android/lightning/bundletool",
        "//src/java/com/google/devtools/mobileharness/platform/android/lightning/systemstate",
        "//src/java/com/google/devtools/mobileharness/platform/android/logcat",
        "//src/java/com/google/devtools/mobileharness/platform/android/media:android_media_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/packagemanager:android_package_manager_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/packagemanager:info",
        "//src/java/com/google/devtools/mobileharness/platform/android/sdktool/adb:adb_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/sdktool/adb:enums",
        "//src/java/com/google/devtools/mobileharness/platform/android/systemsetting:system_setting_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/base:proto_extension_registry",
        "//src/java/com/google/devtools/mobileharness/shared/util/command",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local:res_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/path",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:count_down_timer",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:sleeper",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:android_ats_dynamic_config_pusher_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:android_bugreport_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:android_dump_sys_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:android_emulator_video_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:android_install_mainline_modules_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:android_logcat_monitoring_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:android_screenshot_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:cros_base_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:cros_dut_topology_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:cros_ls_nexus_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:cross_over_android_desktop_provision_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:python_version_check_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:report_log_collector_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator/util:step_skippable_lifecycle_decorator_util",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:android_device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/spec:android_dump_sys_spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/spec:cros_decorator_spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/spec:mobly_test_spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/spec:report_log_collector_spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:property",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job/in",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job/in/spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job/out",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto/spec:android_ats_dynamic_config_pusher_decorator_spec_java_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto/spec:android_emulator_video_decorator_spec_java_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto/spec:android_logcat_monitoring_decorator_spec_java_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto/spec:python_version_check_decorator_spec_java_proto",
        "//src/javatests/com/google/devtools/mobileharness/builddefs:truth",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
        "@maven//:junit_junit",
        "@maven//:org_mockito_mockito_core",
    ],
)

junit_test_suites(
    name = "gen_tests",
    sizes = [
        "small",
    ],
    deps = [":tests"],
)
