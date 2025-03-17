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
    name = "base_driver",
    srcs = ["BaseDriver.java"],
    deps = [
        ":driver",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "driver",
    srcs = ["Driver.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
    ],
)

java_library(
    name = "driver_factory",
    srcs = ["DriverFactory.java"],
    deps = [
        ":driver",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api:common_library_module",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/util:reflection",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
    ],
)

################################################################################
# Driver libraries in alphabetical order
################################################################################

java_library(
    name = "android_foreground_service_messenger",
    srcs = ["AndroidForegroundServiceMessenger.java"],
    deps = [
        ":base_driver",
        "//src/java/com/google/devtools/deviceinfra/platform/android/lightning/internal/sdk/adb",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/platform/android/file:android_file_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/sdktool/adb:adb_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/port:portprober",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:sleeper",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/annotation",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/spec:android_foreground_service_messenger_spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/comm/message",
        "//src/java/com/google/wireless/qa/mobileharness/shared/comm/message/event",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_java_proto",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "android_instrumentation",
    srcs = ["AndroidInstrumentation.java"],
    deps = [
        ":base_driver",
        "//src/devtools/mobileharness/api/model/proto:test_java_proto",
        "//src/devtools/mobileharness/platform/android/testing/proto:test_info_pb_java_proto",
        "//src/java/com/google/devtools/deviceinfra/platform/android/lightning/internal/sdk/adb",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/platform/android/event/util",
        "//src/java/com/google/devtools/mobileharness/platform/android/instrumentation:android_instrumentation_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/lightning/apkinstaller",
        "//src/java/com/google/devtools/mobileharness/platform/android/lightning/systemsetting",
        "//src/java/com/google/devtools/mobileharness/platform/android/packagemanager:android_package_manager_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/base",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/path",
        "//src/java/com/google/wireless/qa/mobileharness/shared/android:aapt",
        "//src/java/com/google/wireless/qa/mobileharness/shared/android/parser:android_instrumentation_parser",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/annotation",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/spec:android_instrumentation_driver_spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/spec:entry_delimiter_spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/step/android:install_apk_step",
        "//src/java/com/google/wireless/qa/mobileharness/shared/comm/message",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:property",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job/in/spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_java_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto/spec:android_instrumentation_spec_java_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/util:device",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "android_native_bin",
    srcs = ["AndroidNativeBin.java"],
    deps = [
        ":base_driver",
        "//src/devtools/mobileharness/api/model/proto:test_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/platform/android/file:android_file_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/lightning/systemsetting",
        "//src/java/com/google/devtools/mobileharness/platform/android/nativebin:native_bin_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/sdktool/adb:enums",
        "//src/java/com/google/devtools/mobileharness/platform/android/systemstate:system_state_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/command",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/path",
        "//src/java/com/google/wireless/qa/mobileharness/shared/android:android_build_spec_key",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/annotation",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/spec:android_native_bin_spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:property",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_java_proto",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "mobly_aosp_package_test",
    srcs = ["MoblyAospPackageTest.java"],
    deps = [
        ":mobly_generic_test",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/mobly:parser",
        "//src/java/com/google/devtools/mobileharness/platform/testbed/mobly/util:install_mobly_test_deps_args",
        "//src/java/com/google/devtools/mobileharness/platform/testbed/mobly/util:mobly_aosp_package_test_setup_util",
        "//src/java/com/google/devtools/mobileharness/platform/testbed/mobly/util:mobly_test_info_map_helper",
        "//src/java/com/google/devtools/mobileharness/shared/util/command",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/annotation",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/spec:mobly_aosp_package_test_spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
        "@maven//:org_json_json",
    ],
)

java_library(
    name = "mobly_generic_test",
    srcs = ["MoblyGenericTest.java"],
    deps = [
        ":base_driver",
        "//src/devtools/mobileharness/api/model/proto:test_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/converter",
        "//src/java/com/google/devtools/deviceinfra/platform/android/lightning/internal/sdk/adb",
        "//src/java/com/google/devtools/deviceinfra/platform/android/sdk/fastboot",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/mobly:mobly_doc_entry",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/mobly:parser",
        "//src/java/com/google/devtools/mobileharness/platform/testbed/mobly:mobly_constant",
        "//src/java/com/google/devtools/mobileharness/platform/testbed/mobly/util:mobly_config_creator",
        "//src/java/com/google/devtools/mobileharness/platform/testbed/mobly/util:mobly_test_info_map_helper",
        "//src/java/com/google/devtools/mobileharness/shared/util/command",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/path",
        "//src/java/com/google/devtools/mobileharness/shared/util/system",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api:composite_device_util",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/annotation",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:composite_device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_java_proto",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
        "@maven//:org_json_json",
        "@maven//:org_yaml_snakeyaml",
    ],
)

java_library(
    name = "no_op_driver",
    srcs = ["NoOpDriver.java"],
    deps = [
        ":base_driver",
        "//src/devtools/mobileharness/api/model/proto:test_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:thread_pools",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api:composite_device_util",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/annotation",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/comm/message/event",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job/in/spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_java_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto/spec:no_op_driver_spec_java_proto",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "xts_tradefed_test",
    srcs = ["XtsTradefedTest.java"],
    resources = [
        "//src/devtools/mobileharness/infra/controller/test/util/xtsdownloader/configs:mcts_list",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/agent:tradefed_invocation_agent_deploy.jar",
    ],
    deps = [
        ":base_driver",
        "//src/devtools/mobileharness/api/model/proto:test_java_proto",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:log_java_proto",
        "//src/devtools/mobileharness/platform/android/xts/proto:test_message_java_proto",
        "//src/java/com/google/devtools/deviceinfra/platform/android/lightning/internal/sdk/adb",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/error:exception_factory",
        "//src/java/com/google/devtools/mobileharness/infra/ats/server/sessionplugin:tradefed_config_generator",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/controller:log_recorder",
        "//src/java/com/google/devtools/mobileharness/platform/android/sdktool/adb:adb_internal_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/shared/emulator:android_jit_emulator_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/common/util:xts_command_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/common/util:xts_constants",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/common/util:xts_dir_util",
        "//src/java/com/google/devtools/mobileharness/shared/constant:log_record_importance",
        "//src/java/com/google/devtools/mobileharness/shared/util/base:proto_text_format",
        "//src/java/com/google/devtools/mobileharness/shared/util/command",
        "//src/java/com/google/devtools/mobileharness/shared/util/command/linecallback:command_output_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:callables",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:more_futures",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:thread_pools",
        "//src/java/com/google/devtools/mobileharness/shared/util/error:more_throwables",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local:res_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/path",
        "//src/java/com/google/devtools/mobileharness/shared/util/shell",
        "//src/java/com/google/devtools/mobileharness/shared/util/system",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:sleeper",
        "//src/java/com/google/wireless/qa/mobileharness/shared/android:aapt",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api:composite_device_util",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/annotation",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:composite_device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/comm/message/event",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dimension",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job/in/spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto/spec:xts_tradefed_test_spec_java_proto",
        "@com_google_protobuf//:protobuf_java",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_code_gson_gson",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
        "@maven//:org_apache_commons_commons_text",
    ],
)

################################################################################
# Android Robo Test
################################################################################
java_library(
    name = "android_robo_test",
    srcs = ["AndroidRoboTest.java"],
    tags = ["avoid_dep"],
    deps = [
        ":base_driver",
        "//src/java/com/google/devtools/deviceinfra/platform/android/lightning/internal/sdk/adb",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared/android:aapt",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/annotation",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job/in/spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto/spec:android_robo_test_spec_java_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/util:base",
        "@maven//:com_google_inject_guice",
        "@maven//:javax_inject_jsr330_api",
    ],
)
################################################################################
