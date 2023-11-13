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
    name = "base_device",
    srcs = ["BaseDevice.java"],
    deps = [
        ":device",
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/java/com/google/devtools/deviceinfra/ext/devicemanagement/device:base_device_helper",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:device_info",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:lite_device_info_factory",
        "//src/java/com/google/devtools/mobileharness/api/testrunner/device/cache:device_cache",
        "//src/java/com/google/devtools/mobileharness/infra/container/annotation",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device:device_info_manager",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device/config",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/path",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api:class_util",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/annotation",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator:validator_factory",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dimension",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:error_code",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:common_java_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:communication_java_proto",
        "@com_google_protobuf//:protobuf_java",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "composite_device",
    srcs = ["CompositeDevice.java"],
    deps = [
        ":device",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "device",
    srcs = ["Device.java"],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:device_info",
        "//src/java/com/google/devtools/mobileharness/infra/container/sandbox/device:device_sandbox_controller",
        "//src/java/com/google/devtools/mobileharness/infra/container/sandbox/device:unsupported_device",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dimension",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:common_java_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:communication_java_proto",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "device_factory",
    srcs = ["DeviceFactory.java"],
    deps = [
        ":device",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/container/annotation",
    ],
)

java_library(
    name = "simple_composite_device",
    srcs = ["SimpleCompositeDevice.java"],
    deps = [
        ":base_device",
        ":composite_device",
        ":device",
        "@maven//:com_google_guava_guava",
    ],
)

################################################################################
# Device libraries in alphabetical order
################################################################################

java_library(
    name = "android_device",
    srcs = ["AndroidDevice.java"],
    deps = [
        ":base_device",
        "//src/java/com/google/devtools/deviceinfra/ext/devicemanagement/device/platform/android:android_device_delegate",
        "//src/java/com/google/devtools/deviceinfra/ext/devicemanagement/device/platform/android:android_device_delegate_impl",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device/config",
        "//src/java/com/google/devtools/mobileharness/platform/android/sdktool/adb:enums",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator:validator_factory",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dimension",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "android_real_device",
    srcs = ["AndroidRealDevice.java"],
    deps = [
        ":android_device",
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/java/com/google/devtools/deviceinfra/ext/devicemanagement/device/platform/android/realdevice:android_real_device_delegate",
        "//src/java/com/google/devtools/deviceinfra/ext/devicemanagement/device/platform/android/realdevice:android_real_device_delegate_impl",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/container/sandbox/device:device_sandbox_controller",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device/config",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator:validator_factory",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
    ],
)

java_library(
    name = "misc_device",
    srcs = ["MiscDevice.java"],
    deps = [
        ":base_device",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
    ],
)

java_library(
    name = "misc_testbed_sub_device",
    srcs = ["MiscTestbedSubDevice.java"],
    deps = [":misc_device"],
)

java_library(
    name = "no_op_device",
    srcs = ["NoOpDevice.java"],
    deps = [
        ":base_device",
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:device_info",
        "//src/java/com/google/devtools/mobileharness/infra/container/sandbox/device:device_sandbox_controller",
        "//src/java/com/google/devtools/mobileharness/infra/container/sandbox/device:no_op_device",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device/config",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator:validator_factory",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:property",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)
