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

package(default_applicable_licenses = ["//:license"])

java_library(
    name = "mobly_aosp_package_test_setup_util",
    srcs = ["MoblyAospPackageTestSetupUtil.java"],
    visibility = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/api:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/platform/testbed/mobly/util:__pkg__",
        "//src/javatests/com/google/wireless/qa/mobileharness/shared/api:__subpackages__",
    ],
    deps = [
        ":install_mobly_test_deps_args",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/common/util:xts_constants",
        "//src/java/com/google/devtools/mobileharness/shared/util/command",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

# TODO - Remove this target once the new target "subdevice_type" is used.
java_library(
    name = "mobly_sub_device_type",
    srcs = [
        "MoblySubDeviceType.java",
    ],
)

java_library(
    name = "subdevice_type",
    srcs = [
        "MoblySubdeviceType.java",
    ],
    visibility = [
    ],
)

java_library(
    name = "mobly_config_creator",
    srcs = [
        "MoblyConfigCreator.java",
    ],
    visibility = ["//src/java/com/google/wireless/qa/mobileharness/shared:__subpackages__"],
    runtime_deps = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:android_device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:misc_testbed_sub_device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:no_op_device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:simple_composite_device",
    ],
    deps = [
        ":mobly_sub_device_type",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/platform/testbed/adhoc",
        "//src/java/com/google/devtools/mobileharness/platform/testbed/config:base",
        "//src/java/com/google/devtools/mobileharness/platform/testbed/mobly:mobly_constant",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:simple_composite_device",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
        "@maven//:org_json_json",
        "@maven//:org_yaml_snakeyaml",
    ],
)

java_library(
    name = "config_generator",
    srcs = [
        "MoblyConfigGenerator.java",
    ],
    visibility = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver:__pkg__",
    ],
    deps = [
        ":subdevice_type",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/platform/testbed/adhoc",
        "//src/java/com/google/devtools/mobileharness/platform/testbed/config:base",
        "//src/java/com/google/devtools/mobileharness/platform/testbed/mobly:mobly_constant",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api:class_util",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:testbed_device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:common_java_proto",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
        "@maven//:org_json_json",
    ],
)

java_library(
    name = "install_mobly_test_deps_args",
    srcs = ["InstallMoblyTestDepsArgs.java"],
    visibility = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/api:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/platform/testbed/mobly/util:__pkg__",
        "//src/javatests/com/google/wireless/qa/mobileharness/shared/api:__subpackages__",
    ],
    deps = ["//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value"],
)

java_library(
    name = "mobly_test_info_map_helper",
    srcs = ["MoblyTestInfoMapHelper.java"],
    visibility = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver:__subpackages__",
        "//src/javatests/com/google/wireless/qa/mobileharness/shared/api/driver:__subpackages__",
    ],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:test_java_proto",
        "//src/devtools/mobileharness/infra/ats/console/result/proto:result_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/mobly:mobly_doc_entry",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/mobly:parser",
        "//src/java/com/google/devtools/mobileharness/platform/testbed/mobly:mobly_constant",
        "//src/java/com/google/devtools/mobileharness/shared/util/error:error_model_converter",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_java_proto",
        "@maven//:com_google_guava_guava",
    ],
)
