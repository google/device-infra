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
load("//devtools/deviceinfra/api/builddefs/test:mobile_test.bzl", "mobile_test")

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/local:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/controller/test/util/xtsdownloader:__subpackages__",
    ],
)

java_library(
    name = "xts_dynamic_download_plugin",
    srcs = ["XtsDynamicDownloadPlugin.java"],
    deps = [
        "//src/devtools/mobileharness/infra/controller/test/util/xtsdownloader/proto:xts_dynamic_download_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/wireless/qa/mobileharness/shared/controller/event:lab_test_events",
    ],
)

java_library(
    name = "mcts_dynamic_download_plugin",
    srcs = ["MctsDynamicDownloadPlugin.java"],
    resources = [
        "//src/devtools/mobileharness/infra/controller/test/util/xtsdownloader/configs:config_proto_files",
    ],
    deps = [
        ":xts_dynamic_download_plugin",
        "//src/devtools/mobileharness/infra/controller/test/util/xtsdownloader/proto:xts_dynamic_download_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/testrunner/plugin:exception",
        "//src/java/com/google/devtools/mobileharness/platform/android/packagemanager:android_package_manager_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/packagemanager:info",
        "//src/java/com/google/devtools/mobileharness/platform/android/sdktool/adb:adb_internal_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/sdktool/adb:adb_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/sdktool/adb:enums",
        "//src/java/com/google/devtools/mobileharness/platform/android/systemspec",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/common/util:xts_constants",
        "//src/java/com/google/devtools/mobileharness/shared/constant:log_record_importance",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local:res_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/path",
        "//src/java/com/google/wireless/qa/mobileharness/shared/controller/event:test",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/lab",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
        "@maven//:org_apache_commons_commons_lang3",
        "@protobuf//:protobuf_java",
    ],
)

# Test target to trigger MCTS dynamic download on a local device/lab server.
mobile_test(
    name = "mcts_dynamic_download_local_test",
    args = [
        "--job_timeout_sec=10800",
        "--test_timeout_sec=7200",
        "--start_timeout_sec=3600",
    ],
    device = "AndroidRealDevice",
    driver = "AndroidXtsTradefedTest",
    files = {
        # IMPORTANT: Do NOT specify 'xts_root_dir' here.
        # Its absence triggers dynamic download mode for MCTS.
    },
    params = {
        "xts_type": "MCTS",
        # This flag explicitly enables dynamic download,
        # which is the default for MCTS when xts_root_dir is absent.
        "use_xts_dynamic_download": "true",
        # Replace with any valid MCTS module name if you want the test to run.
        # If you only care about download, this might not matter,
        # but AdbHostTestCases is small and usually present.
        "run_command": "mcts -m AdbHostTestCases",
    },
)
