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
# Test sets containing runtime_deps of driver/decorator/device
################################################################################

# Driver/decorator/device for OSS lab server.
java_library(
    name = "oss_lab_test",
    runtime_deps = [
        ":no_op_test",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:android_real_device",
    ],
)

# No-op test for integration test only.
java_library(
    name = "no_op_test",
    runtime_deps = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:no_op_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:no_op_device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver:no_op_driver",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/lister:no_op_driver_lister",
    ],
)

################################################################################
# Common libraries
################################################################################

# Please use the exported ones instead.
java_library(
    name = "api",
    exports = [
        ":class_util",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:composite_device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/job:job_type_util",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/lister",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/lister:base_lister",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator",
    ],
)

# Please use the exported ones instead.
java_library(
    name = "base",
    exports = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:adapter_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:async_timer_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:base_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:base_device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:device_factory",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver:base_driver",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver:driver_factory",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator:adapter_validator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator:base_validator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator:validator_factory",
    ],
)

java_library(
    name = "class_util",
    srcs = ["ClassUtil.java"],
    deps = [
        "//src/java/com/google/devtools/deviceinfra/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/local/annotation",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/annotation",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/lister",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/validator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/util:reflection",
        "@maven//:com_google_code_findbugs_jsr305",
    ],
)

java_library(
    name = "composite_device_util",
    srcs = ["CompositeDeviceUtil.java"],
    deps = [
        ":device_cache",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device:composite_device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
    ],
)

java_library(
    name = "device_cache",
    srcs = ["DeviceCache.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/testrunner/device/cache:device_cache",
    ],
)
