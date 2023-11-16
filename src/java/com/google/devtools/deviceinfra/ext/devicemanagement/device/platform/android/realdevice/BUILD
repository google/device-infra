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
    name = "android_real_device_delegate",
    srcs = ["AndroidRealDeviceDelegate.java"],
    visibility = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/api:__subpackages__",
    ],
    deps = [
        ":android_real_device_constants",
        ":android_real_device_delegate_helper",
        "//src/devtools/common/metrics/stability/model/proto:error_java_proto",
        "//src/devtools/mobileharness/api/deviceconfig/proto:basic_java_proto",
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/util:error_id_comparator",
        "//src/java/com/google/devtools/deviceinfra/ext/devicemanagement/device:base_device_helper",
        "//src/java/com/google/devtools/deviceinfra/ext/devicemanagement/device/platform/android:android_device_delegate",
        "//src/java/com/google/devtools/deviceinfra/platform/android/sdk/fastboot",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/testrunner/device/cache:device_cache",
        "//src/java/com/google/devtools/mobileharness/infra/container/sandbox/device:device_sandbox_controller",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device/config",
        "//src/java/com/google/devtools/mobileharness/platform/android/app/devicedaemon:device_daemon_apk_info",
        "//src/java/com/google/devtools/mobileharness/platform/android/app/devicedaemon:device_daemon_helper",
        "//src/java/com/google/devtools/mobileharness/platform/android/connectivity:android_connectivity_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/file:android_file_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/file:autovalues",
        "//src/java/com/google/devtools/mobileharness/platform/android/lightning/apkinstaller",
        "//src/java/com/google/devtools/mobileharness/platform/android/lightning/systemstate",
        "//src/java/com/google/devtools/mobileharness/platform/android/media:android_media_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/packagemanager:android_package_manager_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/packagemanager:enums",
        "//src/java/com/google/devtools/mobileharness/platform/android/process:android_process_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/sdktool/adb:adb_internal_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/sdktool/adb:adb_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/sdktool/adb:enums",
        "//src/java/com/google/devtools/mobileharness/platform/android/sdktool/adb:usb_device_locator",
        "//src/java/com/google/devtools/mobileharness/platform/android/systemsetting:system_setting_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/systemspec",
        "//src/java/com/google/devtools/mobileharness/platform/android/systemstate:system_state_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/user:android_user_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/base",
        "//src/java/com/google/devtools/mobileharness/shared/util/error:more_throwables",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/network",
        "//src/java/com/google/devtools/mobileharness/shared/util/path",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/android:runtime_charging_util",
        "//src/java/com/google/wireless/qa/mobileharness/shared/android:serial_console_util",
        "//src/java/com/google/wireless/qa/mobileharness/shared/android:wifi_util",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:android_device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/spec:android_real_device_spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dimension",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:error_code",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:property",
        "//src/java/com/google/wireless/qa/mobileharness/shared/controller/stat",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:communication_java_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/util:device",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "android_real_device_delegate_helper",
    srcs = ["AndroidRealDeviceDelegateHelper.java"],
    deps = [
        ":android_real_device_constants",
        "//src/java/com/google/devtools/mobileharness/platform/android/sdktool/adb:enums",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:android_device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:base_device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dimension",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "android_real_device_constants",
    srcs = ["AndroidRealDeviceConstants.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/sessionplugin:__subpackages__",
        "//src/javatests/com/google/devtools/deviceinfra/ext/devicemanagement/device/platform/android/realdevice:__subpackages__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/platform/android/sdktool/adb:enums",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/annotation",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "android_real_device_delegate_impl",
    srcs = ["AndroidRealDeviceDelegateImpl.java"],
    visibility = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/api:__subpackages__",
        "//src/javatests/com/google/devtools/deviceinfra/ext/devicemanagement/device/platform/android/realdevice:__subpackages__",
    ],
    deps = [
        ":android_real_device_delegate",
        "//src/java/com/google/devtools/deviceinfra/ext/devicemanagement/device/platform/android:android_device_delegate",
        "//src/java/com/google/devtools/deviceinfra/ext/devicemanagement/device/platform/android:android_device_delegate_impl",
        "//src/java/com/google/devtools/deviceinfra/platform/android/sdk/fastboot",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:locator",
        "//src/java/com/google/devtools/mobileharness/infra/container/sandbox/device:device_sandbox_controller",
        "//src/java/com/google/devtools/mobileharness/infra/container/sandbox/device:no_op_device",
        "//src/java/com/google/devtools/mobileharness/platform/android/app/devicedaemon:device_daemon_helper",
        "//src/java/com/google/devtools/mobileharness/platform/android/connectivity:android_connectivity_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/file:android_file_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/lightning/apkinstaller",
        "//src/java/com/google/devtools/mobileharness/platform/android/lightning/systemstate",
        "//src/java/com/google/devtools/mobileharness/platform/android/media:android_media_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/packagemanager:android_package_manager_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/process:android_process_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/sdktool/adb:adb_internal_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/sdktool/adb:adb_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/sdktool/adb:enums",
        "//src/java/com/google/devtools/mobileharness/platform/android/systemsetting:system_setting_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/systemspec",
        "//src/java/com/google/devtools/mobileharness/platform/android/systemstate:system_state_util",
        "//src/java/com/google/devtools/mobileharness/platform/android/user:android_user_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/network",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/android:runtime_charging_util",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:android_device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dimension",
        "//src/java/com/google/wireless/qa/mobileharness/shared/controller/stat",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)
