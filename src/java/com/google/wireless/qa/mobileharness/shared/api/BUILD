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

DEFAULT_VISIBILITY = [
    "//:deviceinfra_all_pkg",
]

package(
    default_visibility = DEFAULT_VISIBILITY,
)

java_library(
    name = "job_type_util",
    srcs = ["job/JobTypeUtil.java"],
    deps = [
        "//src/java/com/google/devtools/deviceinfra/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_config_java_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_java_proto",
    ],
)

java_library(
    name = "api",
    srcs = glob([
        "job/*.java",
    ]) + [
        "ClassUtil.java",
        "device/CompositeDevice.java",
        "device/Device.java",
        "decorator/Decorator.java",
        "driver/Driver.java",
        "lister/BaseLister.java",
        "lister/Lister.java",
        "validator/Validator.java",
    ],
    visibility = [
        "//:deviceinfra_pkg",
    ],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/devtools/mobileharness/api/model/proto:job_java_proto",
        "//src/java/com/google/devtools/deviceinfra/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:device_info",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/local/annotation",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/annotation",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dimension",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:error_code",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job:builder_adapter",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job/out:timing",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:common_java_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:communication_java_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_config_java_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_java_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto/spec:job_spec_java_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/util:reflection",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "validator_factory",
    srcs = ["validator/ValidatorFactory.java"],
    visibility = [
    ],
    deps = [
        ":api",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:error_code",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "job_validator",
    srcs = ["validator/JobValidator.java"],
    deps = [
        ":api",
        ":validator_factory",
        "//src/java/com/google/devtools/deviceinfra/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/annotation",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:error_code",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_java_proto",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "base_device",
    srcs = [
        "device/BaseDevice.java",
    ],
    deps = [
        ":api",
        ":validator_factory",
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/java/com/google/devtools/deviceinfra/ext/devicemanagement/device:base_device_helper",
        "//src/java/com/google/devtools/deviceinfra/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/deviceinfra/shared/util/path",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:device_info",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:lite_device_info_factory",
        "//src/java/com/google/devtools/mobileharness/api/testrunner/device/cache:device_cache",
        "//src/java/com/google/devtools/mobileharness/infra/container/annotation",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device:device_info_manager",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device/config",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/annotation",
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
    name = "base",
    srcs = [
        "decorator/AdapterDecorator.java",
        "decorator/AsyncTimerDecorator.java",
        "decorator/BaseDecorator.java",
        "device/DeviceFactory.java",
        "driver/BaseDriver.java",
        "driver/DriverFactory.java",
        "validator/AdapterValidator.java",
        "validator/BaseValidator.java",
    ],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/controller/device:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/local:__pkg__",
        "//src/java/com/google/devtools/mobileharness/platform/testbed/adhoc/controller:__pkg__",
    ],
    exports = [
        ":base_device",
        ":validator_factory",
    ],
    deps = [
        ":api",
        "//src/java/com/google/devtools/deviceinfra/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/container/annotation",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:error_code",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/util:reflection",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_extensions_guice_assistedinject",
        "@maven//:com_google_inject_guice",
    ],
)

java_library(
    name = "simple_composite_device",
    srcs = ["device/SimpleCompositeDevice.java"],
    deps = [
        ":api",
        ":base_device",
        "@maven//:com_google_guava_guava",
    ],
)
